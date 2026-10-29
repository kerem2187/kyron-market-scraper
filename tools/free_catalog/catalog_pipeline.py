"""KYRON free, read-only catalog candidate collector. No Google/GCP/LLM credentials.
Crawl4AI HTTP strategy, bounded public-only networking and deterministic extraction.
Never writes to Sheets, Firestore, AppSheet or Telegram.
"""
from __future__ import annotations
import argparse, asyncio, csv, hashlib, io, ipaddress, json, re, socket, time, warnings
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlsplit, urlunsplit
from urllib.robotparser import RobotFileParser
import aiohttp
from aiohttp.abc import AbstractResolver
from bs4 import BeautifulSoup
from PIL import Image
from crawl4ai import CrawlerRunConfig
from crawl4ai.async_crawler_strategy import AsyncHTTPCrawlerStrategy
from crawl4ai.models import AsyncCrawlResponse

UA = 'KyronCatalogResearch/1.0 (+https://github.com/kerem2187/kyron-market-scraper)'
MAX_BYTES = 8 * 1024 * 1024
MAX_PIXELS = 40_000_000
Image.MAX_IMAGE_PIXELS = MAX_PIXELS
FIELDS = ['BARKOD', 'URUNADI', 'UrunGorseli', 'KaynakURL', 'KaynakTuru', 'Lisans', 'KontrolZamani', 'GorselSHA256', 'Durum']

class Rejected(ValueError):
    pass

def public_url(value: str, hosts: set[str] | None = None) -> str:
    if not isinstance(value, str) or any(ord(c) < 32 for c in value):
        raise Rejected('invalid_url')
    p = urlsplit(value)
    if p.scheme != 'https' or not p.hostname or p.username or p.password or p.port not in (None, 443):
        raise Rejected('https_public_only')
    host = p.hostname.lower()
    if hosts is not None and host not in hosts:
        raise Rejected('host_not_approved')
    try:
        if not ipaddress.ip_address(host).is_global:
            raise Rejected('non_public_address')
    except ValueError as e:
        if isinstance(e, Rejected):
            raise
        if host == 'localhost' or host.endswith(('.local', '.internal', '.localhost')):
            raise Rejected('non_public_host')
    return value

def gtin(value) -> str:
    # Never strip letters, infer a SKU, pad a bad code or lose leading zeros.
    if not isinstance(value, str) or not re.fullmatch(r'[0-9]{8}|[0-9]{12,14}', value):
        raise Rejected('missing_or_invalid_gtin')
    if len(set(value)) == 1:
        raise Rejected('placeholder_gtin')
    total = sum(int(d) * (3 if i % 2 == 0 else 1) for i, d in enumerate(reversed(value[:-1])))
    if (10 - total % 10) % 10 != int(value[-1]):
        raise Rejected('gtin_checksum')
    return '0' + value if len(value) == 12 else value

def product_name(value) -> str:
    if not isinstance(value, str):
        raise Rejected('missing_name')
    name = re.sub(r'\s+', ' ', BeautifulSoup(value, 'html.parser').get_text(' ', strip=True)).strip()
    if len(name.split()) < 2 or len(name) < 4 or len(name) > 250 or sum(c.isalpha() for c in name) < 3 or name.casefold() in {'test', 'urun', 'ürün', 'unknown', 'product', 'null', 'none'}:
        raise Rejected('placeholder_or_invalid_name')
    return name

def canonical(url):
    p = urlsplit(url)
    # Keep the query: it may identify a distinct variant.
    return urlunsplit((p.scheme.lower(), p.netloc.lower(), p.path.rstrip('/'), p.query, ''))

def products_jsonld(html: str, url: str) -> dict:
    soup = BeautifulSoup(html, 'html.parser')
    items = []
    def walk(obj):
        if isinstance(obj, list):
            for o in obj: walk(o)
        elif isinstance(obj, dict):
            typ = obj.get('@type', [])
            typ = [typ] if isinstance(typ, str) else typ
            if 'Product' in typ:
                items.append(obj)
                return  # Do not mistake nested recommendations for the primary product.
            for v in obj.values():
                if isinstance(v, (list, dict)): walk(v)
    for tag in soup.select('script[type="application/ld+json"]'):
        try: walk(json.loads(tag.string or tag.get_text()))
        except (ValueError, TypeError): continue
    exact = [o for o in items if isinstance(o.get('url'), str) and canonical(urljoin(url, o['url'])) == canonical(url)]
    # URL-less Product is accepted only if the entire page has exactly one Product.
    candidates = exact if exact else [o for o in items if not o.get('url')] if len(items) == 1 else []
    if len(candidates) != 1:
        raise Rejected('ambiguous_or_missing_primary_product')
    obj = candidates[0]
    codes = {gtin(obj[k]) for k in ('gtin', 'gtin8', 'gtin12', 'gtin13', 'gtin14') if obj.get(k) is not None}
    if len(codes) != 1:
        raise Rejected('missing_or_conflicting_gtin')
    image = obj.get('image')
    if isinstance(image, list): image = image[0] if image else None
    if isinstance(image, dict): image = image.get('contentUrl') or image.get('url')
    if not isinstance(image, str): raise Rejected('missing_image')
    return {'BARKOD': codes.pop(), 'URUNADI': product_name(obj.get('name')), 'UrunGorseli': public_url(urljoin(url, image)), 'KaynakURL': url, 'KaynakTuru': 'product_jsonld', 'Lisans': 'SOURCE_TERMS_REVIEW_REQUIRED'}

def off_product(data: dict, expected: str) -> dict:
    if data.get('status') != 1 or not isinstance(data.get('product'), dict):
        raise Rejected('off_not_found')
    p = data['product']
    code = gtin(p.get('code'))
    if code != gtin(expected): raise Rejected('off_barcode_mismatch')
    return {'BARKOD': code, 'URUNADI': product_name(p.get('product_name_tr') or p.get('product_name')), 'UrunGorseli': public_url(p.get('image_front_url')), 'KaynakURL': f'https://world.openfoodfacts.org/product/{expected}', 'KaynakTuru': 'openfoodfacts_community', 'Lisans': 'Open Food Facts: ODbL database / DbCL contents / CC BY-SA images; see source product page'}

class PublicResolver(AbstractResolver):
    async def resolve(self, host, port=0, family=socket.AF_UNSPEC):
        answers = await asyncio.get_running_loop().getaddrinfo(host, port, family=family, type=socket.SOCK_STREAM)
        if not answers or any(not ipaddress.ip_address(a[4][0]).is_global for a in answers):
            raise Rejected('dns_non_public_address')
        return [{'hostname': host, 'host': a[4][0], 'port': port, 'family': a[0], 'proto': a[2], 'flags': socket.AI_NUMERICHOST} for a in answers]
    async def close(self): pass

class Fetcher:
    def __init__(self):
        self.robots = {}
        self.last = defaultdict(float)
        self.delays = defaultdict(lambda: 2.0)
        self.session = None
    async def __aenter__(self):
        self.session = aiohttp.ClientSession(connector=aiohttp.TCPConnector(resolver=PublicResolver(), limit=2), timeout=aiohttp.ClientTimeout(total=25), headers={'User-Agent': UA}, trust_env=False)
        return self
    async def __aexit__(self, *args): await self.session.close()
    async def get(self, url, *, hosts=None, robots=True, limit=MAX_BYTES, redirects=0):
        public_url(url, hosts)
        p = urlsplit(url)
        origin = f'https://{p.netloc}'
        if robots:
            if origin not in self.robots:
                status, _, body, _ = await self.get(origin + '/robots.txt', hosts={p.hostname}, robots=False, limit=512_000)
                if status == 404:
                    text = 'User-agent: *\nAllow: /'
                elif status == 200:
                    text = body.decode('utf-8', errors='replace')
                else:
                    raise Rejected(f'robots_unavailable_http_{status}')
                parser = RobotFileParser(); parser.parse(text.splitlines())
                self.robots[origin] = parser
                self.delays[origin] = max(2, parser.crawl_delay(UA) or parser.crawl_delay('*') or 0)
            if not self.robots[origin].can_fetch(UA, url): raise Rejected('robots_disallowed')
        await asyncio.sleep(max(0, self.last[origin] + self.delays[origin] - time.monotonic()))
        self.last[origin] = time.monotonic()
        async with self.session.get(url, allow_redirects=False) as r:
            if r.status in (301, 302, 303, 307, 308):
                if redirects >= 3: raise Rejected('redirect_limit')
                target = urljoin(url, r.headers.get('Location', ''))
                public_url(target, hosts)
                # Each redirected page is rechecked, including robots and public DNS.
                return await self.get(target, hosts=hosts, robots=robots, limit=limit, redirects=redirects + 1)
            if int(r.headers.get('Content-Length', '0')) > limit: raise Rejected('response_too_large')
            chunks = []; size = 0
            async for chunk in r.content.iter_chunked(65536):
                size += len(chunk)
                if size > limit: raise Rejected('response_too_large')
                chunks.append(chunk)
            return r.status, dict(r.headers), b''.join(chunks), str(r.url)

class CatalogHTTPStrategy(AsyncHTTPCrawlerStrategy):
    """Crawl4AI strategy with pinned-public DNS, response limits and robots checks.
    No browser, JavaScript, proxy, paid API, LLM or stealth plugin is used.
    """
    def __init__(self, fetcher: Fetcher, hosts: set[str]):
        super().__init__()
        self.fetcher, self.hosts = fetcher, hosts
    async def _handle_http(self, url, config):
        status, headers, body, final = await self.fetcher.get(url, hosts=self.hosts)
        if status != 200: raise Rejected(f'product_http_{status}')
        if 'text/html' not in headers.get('Content-Type', '').lower(): raise Rejected('not_html')
        return AsyncCrawlResponse(html=body.decode('utf-8', errors='replace'), response_headers=headers, status_code=status, redirected_url=final)

async def check_image(fetcher: Fetcher, url: str) -> str:
    status, headers, body, _ = await fetcher.get(url)
    if status != 200: raise Rejected(f'image_http_{status}')
    if not headers.get('Content-Type', '').lower().startswith('image/'):
        raise Rejected('image_mime')
    with warnings.catch_warnings():
        warnings.simplefilter('error', Image.DecompressionBombWarning)
        with Image.open(io.BytesIO(body)) as image:
            if min(image.size) < 80 or image.width * image.height > MAX_PIXELS:
                raise Rejected('image_dimensions')
            image.verify()
        with Image.open(io.BytesIO(body)) as image: image.load()
    return hashlib.sha256(body).hexdigest()

def resolve_conflicts(records: list[dict]):
    groups = defaultdict(list)
    for row in records: groups[row['BARKOD']].append(row)
    accepted, review = [], []
    for code, rows in groups.items():
        # Conservative: name disagreement is review, never overwrite/majority-vote.
        if len({r['URUNADI'].casefold() for r in rows}) > 1:
            review.append({'BARKOD': code, 'reason': 'conflicting_names', 'records': rows})
        else: accepted.append(rows[0])
    return accepted, review

def csv_cell(value):
    value = str(value)
    return "'" + value if value.lstrip().startswith(('=', '+', '-', '@')) else value

def write_outputs(folder: Path, records, review):
    folder.mkdir(parents=True, exist_ok=True)
    accepted, conflicts = resolve_conflicts(records)
    review = review + conflicts
    with (folder / 'aday_katalog.csv').open('w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, quoting=csv.QUOTE_ALL); w.writeheader()
        for row in accepted: w.writerow({k: csv_cell(row.get(k, '')) for k in FIELDS})
    with (folder / 'inceleme.jsonl').open('w', encoding='utf-8') as f:
        for row in review: f.write(json.dumps(row, ensure_ascii=False) + '\n')
    summary = {'candidate_count': len(accepted), 'review_count': len(review), 'production_writes': 0, 'semantic_matching': 'source-asserted, not guaranteed; human review required', 'created_utc': datetime.now(timezone.utc).isoformat()}
    (folder / 'ozet.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    return summary

async def run(*, urls=None, barcodes=None, approved_hosts=None, out='catalog_output', max_items=30):
    urls = list(dict.fromkeys(urls or [])); barcodes = list(dict.fromkeys(barcodes or []))
    hosts = {h.strip().lower() for h in (approved_hosts or []) if h.strip()}
    if not 1 <= max_items <= 100 or len(urls) + len(barcodes) > max_items:
        raise Rejected('max_items_exceeded_1_to_100')
    records, review = [], []
    async with Fetcher() as fetcher:
        strategy = CatalogHTTPStrategy(fetcher, hosts)
        for kind, value in [('url', v) for v in urls] + [('barcode', b) for b in barcodes]:
            try:
                if kind == 'url':
                    public_url(value, hosts)
                    response = await strategy.crawl(value, CrawlerRunConfig(page_timeout=25000))
                    row = products_jsonld(response.html, response.redirected_url or value)
                else:
                    gtin(value)
                    endpoint = f'https://world.openfoodfacts.org/api/v2/product/{value}.json?fields=code,product_name,product_name_tr,image_front_url,quantity,brands'
                    # OFF explicitly documents this public API. Website robots is not used as an API license.
                    # At most 12 requests/minute here, below the documented 15/minute.
                    fetcher.delays['https://world.openfoodfacts.org'] = 5.0
                    status, _, body, _ = await fetcher.get(endpoint, hosts={'world.openfoodfacts.org'}, robots=False)
                    if status != 200: raise Rejected(f'off_http_{status}')
                    row = off_product(json.loads(body), value)
                row['GorselSHA256'] = await check_image(fetcher, row['UrunGorseli'])
                row['KontrolZamani'] = datetime.now(timezone.utc).isoformat()
                row['Durum'] = 'KAYNAK_ESLESMESI_ADAYI_INSAN_ONAYI_GEREKLI'
                records.append(row)
            except Exception as exc:
                # Do not log headers, page bodies, environment variables or credentials.
                reason = str(exc) if isinstance(exc, Rejected) else type(exc).__name__
                review.append({'input': value, 'reason': reason})
    return write_outputs(Path(out), records, review)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--urls', type=Path, help='One public product URL per line; no search/category URLs')
    parser.add_argument('--barcodes', type=Path, help='One GTIN per line, exact Open Food Facts lookup')
    parser.add_argument('--allow-host', action='append', default=[])
    parser.add_argument('--out', default='catalog_output')
    parser.add_argument('--max-items', type=int, default=30)
    args = parser.parse_args()
    def lines(path): return [s.strip() for s in path.read_text().splitlines() if s.strip() and not s.startswith('#')] if path else []
    print(json.dumps(asyncio.run(run(urls=lines(args.urls), barcodes=lines(args.barcodes), approved_hosts=args.allow_host, out=args.out, max_items=args.max_items)), ensure_ascii=False, indent=2))
if __name__ == '__main__': main()
