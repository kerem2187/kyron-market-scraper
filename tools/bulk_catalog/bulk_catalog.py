"""Read-only public catalog collector. No Google/Firestore keys or production writes.
Normal browser pages only; stops on access challenges. SQLite checkpoints and CSV exports.
"""
import argparse, csv, hashlib, io, json, os, re, sqlite3, time, signal
from pathlib import Path
from urllib.parse import urlparse
from urllib.robotparser import RobotFileParser

UA = 'KyronCatalogReview/1.0'
HOSTS = {'migros': 'www.migros.com.tr', 'macrocenter': 'www.macrocenter.com.tr'}
IMAGE_HOSTS = {'images.migrosone.com'}
class AccessBlocked(RuntimeError): pass

def gtin(value):
    s = str(value or '')
    if not s.isascii() or not s.isdigit() or len(s) not in (8,12,13,14) or len(set(s)) == 1:
        return None
    if (sum(int(c)*(3 if i % 2 == 0 else 1) for i,c in enumerate(s[-2::-1])) + int(s[-1])) % 10:
        return None
    return '0'+s if len(s)==12 else s

def allowed(url, hosts):
    p = urlparse(url)
    return p.scheme=='https' and p.hostname in hosts and not p.username and not p.password and p.port in (None,443)

def image_urls(data, legal=False):
    imgs = (data.get('productSafetyInfo',{}).get('productSafetyImageInfo',{}).get('images',[]) if legal else data.get('images',[]))
    return [u for im in imgs for u in [next((im.get('urls',{}).get(k) for k in (['LEGAL'] if legal else ['PRODUCT_HD','PRODUCT_DETAIL']) if im.get('urls',{}).get(k)), None)] if u and allowed(u,IMAGE_HOSTS)]

def reason_for(r, peers):
    if not gtin(r.get('barcode')): return 'Barkod yok, geçersiz veya yer tutucu'
    if re.search(r'\b\d+\s*[xX×]\s*\d+', r.get('name','')): return 'Çoklu paket: dış paket barkodu doğrulanmalı'
    if re.search(r'\b200\s*cl\b',r.get('name',''),re.I): return 'Kaynak hacim birimi şüpheli; ambalaj kontrolü gerekli'
    if len({p.get('name','').casefold().strip() for p in peers})>1: return 'Aynı barkod farklı ürün/paket adlarında bulundu'
    if len(r.get('name','').strip().split())<2: return 'Ürün adı yetersiz'
    if not r.get('front_valid'): return 'Ön görsel indirilemedi veya çözümlenemedi'
    if r.get('ambiguous'): return 'Birden fazla barkod veya çelişkili GTIN'
    return None

def csv_safe(value):
    s=str(value or '')
    return "'"+s if s.lstrip().startswith(('=','+','-','@')) or s.startswith(('\t','\r','\n')) else s

def export(db, out, source, stop_reason='running'):
    rows=[json.loads(x[0]) for x in db.execute('select record from results order by url')]
    peers={}
    for r in rows:
        if r.get('barcode'): peers.setdefault(r['barcode'],[]).append(r)
    header=['BARKOD','URUNADI','UrunGorseli','Kaynak','KaynakURL','BarkodKanitURL','KontrolUTC','Durum','Neden','KaynakKategori']
    accepted=[];review=[];seen=set()
    for r in rows:
        reason=r.get('error') or reason_for(r,peers.get(r.get('barcode'),[]))
        values=[r.get('barcode',''),r.get('name',''),r.get('image',''),source,r['url'],r.get('evidence',''),r.get('checked',''),'İnceleme' if reason else 'İnsan onayı bekleyen aday',reason or '',r.get('category','')]
        if reason: review.append(values)
        elif r['barcode'] not in seen: accepted.append(values);seen.add(r['barcode'])
    for name, items in [('aday_katalog.csv',accepted),('inceleme.csv',review)]:
        tmp=out/(name+'.tmp')
        with tmp.open('w',encoding='utf-8-sig',newline='') as f:
            w=csv.writer(f);w.writerow(header);w.writerows([csv_safe(v) for v in row] for row in items)
        tmp.replace(out/name)
    summary={'source':source,'discovered':db.execute('select count(*) from queue').fetchone()[0], 'processed':len(rows),'candidates':len(accepted),'review':len(review),'pending':db.execute('select count(*) from queue where done=0').fetchone()[0],'stop_reason':stop_reason,'production_writes':0,'checked_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
    (out/'ozet.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2));return summary

class Collector:
    def __init__(self, context, source, out, limit, minutes, page_limit):
        self.c=context;self.source=source;self.host=HOSTS[source];self.base='https://'+self.host
        self.out=Path(out);self.out.mkdir(parents=True,exist_ok=True)
        self.db=sqlite3.connect(self.out/'checkpoint.sqlite')
        self.db.executescript('''create table if not exists queue(url text primary key,done integer default 0);create table if not exists results(url text primary key,record text);create table if not exists pages(url text primary key);create table if not exists image_cache(url text primary key,info text);''')
        self.limit=limit;self.deadline=time.monotonic()+minutes*60;self.page_limit=page_limit;self.robots={};self.last=0;self.detail=self.c.new_page();self.detail.set_default_timeout(18000);self.asset=self.c.new_page()
    def check_time(self):
        if time.monotonic()>self.deadline:raise TimeoutError('run_time_budget')
    def pause(self,seconds=3):
        self.check_time();time.sleep(max(0,seconds-(time.monotonic()-self.last)));self.last=time.monotonic()
    def can_fetch(self,url):
        host=urlparse(url).hostname
        if host not in self.robots:
            self.pause();r=self.asset.goto('https://'+host+'/robots.txt',wait_until='domcontentloaded',timeout=18000)
            if not r:raise AccessBlocked('robots navigation failed')
            if r.status in (401,403,429):raise AccessBlocked('robots access denied '+str(r.status))
            if r.status>=500:raise AccessBlocked('robots unavailable')
            rp=RobotFileParser();rp.parse(r.text().splitlines() if r.ok else [])
            self.robots[host]=rp
        return self.robots[host].can_fetch(UA,url)
    def nav(self,p,url):
        if not allowed(url,{self.host}) or not self.can_fetch(url):raise AccessBlocked('URL/robots denied')
        self.pause();r=p.goto(url,wait_until='domcontentloaded',timeout=30000)
        title=p.title()
        if not r or r.status in (401,403,429) or re.search('just a moment|attention required|access denied',title,re.I):raise AccessBlocked('source access challenge; no bypass')
        if not r.ok:raise RuntimeError('page HTTP '+str(r.status))
    def add_links(self,p):
        links=p.locator('a[href*="-p-"]').evaluate_all('(es)=>[...new Set(es.map(e=>e.href))]')
        for u in links:
            if allowed(u,{self.host}):self.db.execute('insert or ignore into queue(url) values(?)',(u.split('?')[0],))
        self.db.commit()
        return links
    def discover(self):
        p=self.c.new_page()
        try:
            self.nav(p,self.base);p.wait_for_timeout(2000)
            categories=p.locator('a[href*="-c-"]').evaluate_all('(es)=>[...new Set(es.map(e=>e.href))]')
            categories=[u for u in categories if allowed(u,{self.host})]
            # Favor packaged retail goods; never synthesize category addresses.
            categories.sort(key=lambda u:(0 if re.search('sut|cikolata|atistirmalik|icecek|temel-gida|deterjan|kahve|biskuvi',u) else 1,u))
            if not categories:raise RuntimeError('No public categories discovered')
            for cat in categories:
                if self.db.execute('select count(*) from queue where done=0').fetchone()[0]>=self.limit*2:break
                self.check_time();self.nav(p,cat)
                try:p.locator('a[href*="-p-"]').first.wait_for(timeout=18000)
                except Exception:continue
                previous=None
                for n in range(self.page_limit):
                    current=p.url();links=self.add_links(p)
                    if not links or tuple(links)==previous:break
                    previous=tuple(links);self.db.execute('insert or ignore into pages values(?)',(current,));self.db.commit()
                    if self.db.execute('select count(*) from queue where done=0').fetchone()[0]>=self.limit*2:break
                    nxt=p.get_by_role('button',name='Sonraki sayfa',exact=True)
                    if not nxt.count() or not nxt.is_enabled():break
                    self.pause();nxt.click();p.wait_for_timeout(1800)
                    title=p.title()
                    if re.search('just a moment|attention required|access denied',title,re.I):raise AccessBlocked('pagination access challenge')
                print(json.dumps({'stage':'discovery','source':self.source,'urls':self.db.execute('select count(*) from queue').fetchone()[0]},ensure_ascii=False),flush=True)
            if not self.db.execute('select count(*) from queue').fetchone()[0]:raise RuntimeError('No product links discovered')
        finally:p.close()
    def image(self,url):
        if not allowed(url,IMAGE_HOSTS) or not self.can_fetch(url):return {'valid':False,'codes':[]}
        row=self.db.execute('select info from image_cache where url=?',(url,)).fetchone()
        if row:return json.loads(row[0])
        self.pause(1.5);r=self.asset.goto(url,wait_until='domcontentloaded',timeout=18000)
        if not r:return {'valid':False,'codes':[]}
        if r.status in (401,403,429):raise AccessBlocked('image source rate/access limit '+str(r.status))
        info={'valid':False,'codes':[]}
        if r.ok:
            length=r.headers.get('content-length')
            if length and int(length)>8*1024*1024:return info
            raw=r.body()
            if len(raw)<=8*1024*1024:
                from PIL import Image
                import zxingcpp
                try:
                    im=Image.open(io.BytesIO(raw))
                    if im.width*im.height<=40000000:
                        im.load();codes=set()
                        for v in zxingcpp.read_barcodes(im):
                            if str(v.format) in ('EAN-13','EAN-8','UPC-A','ITF','ITF-14'):
                                code=gtin(v.text)
                                if code:codes.add(code)
                        info={'valid':True,'codes':sorted(codes),'sha256':hashlib.sha256(raw).hexdigest()}
                except Exception:pass
        self.db.execute('insert or replace into image_cache values(?,?)',(url,json.dumps(info)));self.db.commit();return info
    def product(self,url):
        r={'url':url,'checked':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
        endpoint=re.compile(r'https://'+re.escape(self.host)+r'/rest/products/(screens/)?[0-9]+(?:\?.*)?$')
        with self.detail.expect_response(lambda x:endpoint.match(x.url) is not None,timeout=20000) as response:
            self.nav(self.detail,url)
        res=response.value
        if res.status in (401,403,429):raise AccessBlocked('product response access denied')
        root=res.json();data=root.get('data',{});data=data.get('storeProductInfoDTO',data)
        if root.get('successful') is not True or data.get('prettyName')!=urlparse(url).path.strip('/'):
            raise RuntimeError('Product identity mismatch')
        r['name']=data.get('name','');r['category']=data.get('category',{}).get('name','')
        images=image_urls(data);legal=image_urls(data,True)
        r['image']=images[0] if images else '';r['front_valid']=bool(images and self.image(images[0])['valid'])
        explicit=set()
        for key in ('barcode','barcodes','gtin','gtin13','ean'):
            values=data.get(key,[]);values=values if isinstance(values,list) else [values]
            explicit.update(c for v in values if (c:=gtin(v)))
        codes=set(explicit)
        for im in list(dict.fromkeys(legal+images))[:4]:
            info=self.image(im)
            if info['codes']:
                codes.update(info['codes']);r['evidence']=im;r['evidence_sha256']=info.get('sha256');break
        r['ambiguous']=len(codes)>1
        if len(codes)==1:r['barcode']=next(iter(codes))
        return r
    def run(self):
        stop='completed';count=0;failures=0
        try:
            # A restored queue is drained before new discovery; no already-completed product is refetched.
            pending=self.db.execute('select count(*) from queue where done=0').fetchone()[0]
            if pending<self.limit:self.discover()
            urls=[x[0] for x in self.db.execute('select url from queue where done=0 order by rowid limit ?',(self.limit,))]
            for u in urls:
                self.check_time()
                try:r=self.product(u);failures=0
                except AccessBlocked:raise
                except Exception as e:
                    failures+=1;r={'url':u,'error':type(e).__name__+': '+str(e)[:180]}
                    if failures>=5:raise RuntimeError('five_consecutive_product_errors')
                self.db.execute('insert or replace into results values(?,?)',(u,json.dumps(r,ensure_ascii=False)))
                self.db.execute('update queue set done=1 where url=?',(u,));self.db.commit();count+=1
                if count%10==0:print(json.dumps(export(self.db,self.out,self.source),ensure_ascii=False),flush=True)
            stop='batch_limit' if self.db.execute('select count(*) from queue where done=0').fetchone()[0] else 'discovered_queue_complete'
        except (AccessBlocked,TimeoutError) as e:stop=str(e)
        except Exception as e:stop=type(e).__name__+': '+str(e)[:200]
        finally:
            summary=export(self.db,self.out,self.source,stop);print(json.dumps(summary,ensure_ascii=False),flush=True)
            self.detail.close();self.asset.close();self.db.close()
        return summary

def main():
    a=argparse.ArgumentParser();a.add_argument('--source',choices=HOSTS,required=True);a.add_argument('--out',required=True);a.add_argument('--limit',type=int,default=500);a.add_argument('--minutes',type=int,default=80);a.add_argument('--pages-per-category',type=int,default=15);args=a.parse_args()
    if not 1<=args.limit<=1500 or not 1<=args.minutes<=110 or not 1<=args.pages_per_category<=30:a.error('Limits: products 1..1500, minutes 1..110, pages 1..30')
    from playwright.sync_api import sync_playwright
    def terminate(signum,frame):raise TimeoutError('terminated_with_checkpoint')
    signal.signal(signal.SIGTERM,terminate)
    with sync_playwright() as pw:
        remote=os.getenv('AGENT_BROWSER_CDP')
        browser=pw.chromium.connect_over_cdp(remote) if remote else pw.chromium.launch(headless=True)
        context=browser.contexts[0] if remote else browser.new_context()
        try:summary=Collector(context,args.source,args.out,args.limit,args.minutes,args.pages_per_category).run()
        finally:
            if not remote:context.close();browser.close()
    if not summary['processed'] or summary['stop_reason'] not in ('batch_limit','discovered_queue_complete'):raise SystemExit(2)
if __name__=='__main__':main()
