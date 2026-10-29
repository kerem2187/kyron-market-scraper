import asyncio, io, json, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
import catalog_pipeline as c

URL='https://shop.example/product/a'
def html(obj): return '<html><script type="application/ld+json">'+json.dumps(obj)+'</script></html>'
def product(**kwargs):
    return dict({'@type':'Product','url':URL,'name':'Örnek Süt 1 L','gtin13':'8690632992846','image':'https://cdn.example/a.jpg'}, **kwargs)

class QualityTests(unittest.TestCase):
    def test_valid_ean13(self): self.assertEqual(c.gtin('8690632992846'),'8690632992846')
    def test_valid_ean8(self): self.assertEqual(c.gtin('96385074'),'96385074')
    def test_upc(self): self.assertEqual(c.gtin('036000291452'),'0036000291452')
    def test_gtin14(self): self.assertEqual(c.gtin('10012345000017'),'10012345000017')
    def test_bad_codes(self):
        for value in ['a8690632992846','8690632992847','0000000000000','123','SKU123',8690632992846]:
            with self.subTest(value=value),self.assertRaises(c.Rejected): c.gtin(value)
    def test_bad_names(self):
        for value in ['a','.','Test','Ürün','Nesquik','12','   ',None]:
            with self.subTest(value=value),self.assertRaises(c.Rejected): c.product_name(value)
    def test_clean_name(self): self.assertEqual(c.product_name('<b>Süt</b>  1 L'),'Süt 1 L')
    def test_unsafe_urls(self):
        for value in ['http://example.com','https://127.0.0.1/a','https://[::1]/a','https://169.254.169.254/a','https://user:pass@example.com','https://example.com:444/a','https://test.local/a','file:///etc/passwd']:
            with self.subTest(value=value),self.assertRaises(c.Rejected): c.public_url(value)
    def test_allowlist(self):
        with self.assertRaises(c.Rejected): c.public_url('https://evil.example',{'shop.example'})
    def test_jsonld_primary(self):
        recommendation=product(url='https://shop.example/product/b',gtin13='5000112664782',name='Başka Ürün')
        row=c.products_jsonld(html({'@graph':[recommendation,product()]}),URL)
        self.assertEqual(row['BARKOD'],'8690632992846')
    def test_recommendation_only_rejected(self):
        with self.assertRaises(c.Rejected): c.products_jsonld(html(product(url=URL+'/other')),URL)
    def test_ambiguous(self):
        a=product();a.pop('url')
        with self.assertRaises(c.Rejected): c.products_jsonld(html([a,a]),URL)
    def test_query_variant_preserved(self):
        with self.assertRaises(c.Rejected): c.products_jsonld(html(product(url=URL+'?variant=2')),URL+'?variant=1')
    def test_one_urlless(self):
        a=product();a.pop('url');self.assertEqual(c.products_jsonld(html(a),URL)['URUNADI'],'Örnek Süt 1 L')
    def test_conflicting_gtins(self):
        with self.assertRaises(c.Rejected): c.products_jsonld(html(product(gtin='5000112664782')),URL)
    def test_no_sku_barcode_fallback(self):
        p=product();p.pop('gtin13');p['sku']='8690632992846'
        with self.assertRaises(c.Rejected): c.products_jsonld(html(p),URL)
    def test_image_object(self): self.assertEqual(c.products_jsonld(html(product(image={'url':'/a.png'})),URL)['UrunGorseli'],'https://shop.example/a.png')
    def test_off_mismatch(self):
        with self.assertRaises(c.Rejected): c.off_product({'status':1,'product':{'code':'5000112664782'}},'8690632992846')
    def test_conflicts_quarantined(self):
        rows=[{'BARKOD':'1','URUNADI':'Süt 1 L'},{'BARKOD':'1','URUNADI':'Ayran 1 L'}]
        accepted,review=c.resolve_conflicts(rows);self.assertEqual(len(accepted),0);self.assertEqual(len(review),1)
    def test_duplicate(self): self.assertEqual(len(c.resolve_conflicts([{'BARKOD':'1','URUNADI':'Süt 1 L'}]*2)[0]),1)
    def test_formula_injection(self): self.assertEqual(c.csv_cell('=HYPERLINK("bad")'),'\'=HYPERLINK("bad")')
    def test_outputs(self):
        with tempfile.TemporaryDirectory() as d:
            summary=c.write_outputs(Path(d),[],[{'reason':'test'}]); self.assertEqual(summary['production_writes'],0)
            self.assertEqual(len(list(Path(d).iterdir())),3)
    def test_max_items(self):
        with self.assertRaises(c.Rejected): asyncio.run(c.run(barcodes=['1','2'],max_items=1))
    def test_dns_private(self):
        async def check():
            with patch.object(asyncio.get_running_loop(),'getaddrinfo',return_value=[(socket.AF_INET,socket.SOCK_STREAM,6,'',('127.0.0.1',443))]):
                with self.assertRaises(c.Rejected): await c.PublicResolver().resolve('public.example',443)
        import socket
        asyncio.run(check())

class ImageTests(unittest.IsolatedAsyncioTestCase):
    async def test_crawl4ai_http_strategy(self):
        class Fake:
            async def get(self,*a,**k): return 200,{'Content-Type':'text/html'},html(product()).encode(),URL
        strategy=c.CatalogHTTPStrategy(Fake(),{'shop.example'})
        response=await strategy.crawl(URL,c.CrawlerRunConfig(page_timeout=1000))
        self.assertEqual(c.products_jsonld(response.html,URL)['BARKOD'],'8690632992846')

    async def test_real_image_bytes(self):
        b=io.BytesIO();Image.new('RGB',(100,100),'white').save(b,format='PNG')
        class Fake:
            async def get(self,*a,**k): return 200,{'Content-Type':'image/png'},b.getvalue(),'https://cdn.example/a.png'
        self.assertEqual(len(await c.check_image(Fake(),'https://cdn.example/a.png')),64)
    async def test_html_is_not_image(self):
        class Fake:
            async def get(self,*a,**k): return 200,{'Content-Type':'text/html'},b'<html>blocked</html>','https://cdn.example/a.png'
        with self.assertRaises(c.Rejected): await c.check_image(Fake(),'https://cdn.example/a.png')
    async def test_bad_image_payload(self):
        class Fake:
            async def get(self,*a,**k): return 200,{'Content-Type':'image/png'},b'NOTPNG','https://cdn.example/a.png'
        with self.assertRaises(Exception): await c.check_image(Fake(),'https://cdn.example/a.png')

if __name__=='__main__': unittest.main()
