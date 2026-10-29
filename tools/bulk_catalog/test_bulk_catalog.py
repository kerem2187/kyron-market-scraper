import unittest, tempfile,sqlite3,json
from pathlib import Path
from bulk_catalog import gtin,allowed,reason_for,export,csv_safe
class Tests(unittest.TestCase):
 def test_csv_formula(self):self.assertEqual(csv_safe('=IMPORTXML(1)'),"'=IMPORTXML(1)")
 def test_csv_plain(self):self.assertEqual(csv_safe('İçim Süt'),'İçim Süt')
 def test_valid(self):self.assertEqual(gtin('8690504410911'),'8690504410911')
 def test_checksum(self):self.assertIsNone(gtin('8690504410912'))
 def test_placeholder(self):self.assertIsNone(gtin('0000000000000'))
 def test_sku(self):self.assertIsNone(gtin('11010066'))
 def test_non_digits(self):self.assertIsNone(gtin('EAN 8690504410911'))
 def test_unicode(self):self.assertIsNone(gtin('８６９０５０４４１０９１１'))
 def test_upc(self):self.assertEqual(gtin('036000291452'),'0036000291452')
 def test_ean8(self):self.assertEqual(gtin('96385074'),'96385074')
 def test_url(self):self.assertTrue(allowed('https://images.migrosone.com/a.jpg',{'images.migrosone.com'}))
 def test_ssrf(self):self.assertFalse(allowed('https://127.0.0.1/a',{'images.migrosone.com'}))
 def test_credentials(self):self.assertFalse(allowed('https://user@images.migrosone.com/a',{'images.migrosone.com'}))
 def test_http(self):self.assertFalse(allowed('http://images.migrosone.com/a',{'images.migrosone.com'}))
 def test_port(self):self.assertFalse(allowed('https://images.migrosone.com:8080/a',{'images.migrosone.com'}))
 def row(self):return {'barcode':'8690504410911','name':'İçim Süt 1 L','front_valid':True,'url':'https://www.migros.com.tr/product-p-abc'}
 def test_accept(self):r=self.row();self.assertIsNone(reason_for(r,[r]))
 def test_conflict(self):r=self.row();other=dict(r,name='İçim Süt 200 Ml');self.assertIsNotNone(reason_for(r,[r,other]))
 def test_pack(self):r=dict(self.row(),name='İçim Süt 6x200 Ml');self.assertIsNotNone(reason_for(r,[r]))
 def test_no_image(self):r=dict(self.row(),front_valid=False);self.assertIsNotNone(reason_for(r,[r]))
 def test_ambiguous(self):r=dict(self.row(),ambiguous=True);self.assertIsNotNone(reason_for(r,[r]))
 def test_export(self):
  with tempfile.TemporaryDirectory() as td:
   db=sqlite3.connect(':memory:');db.executescript('create table results(url text,record text);create table queue(url text,done int);')
   r=self.row();db.execute('insert into results values(?,?)',(r['url'],json.dumps(r)));db.execute('insert into queue values(?,1)',(r['url'],));summary=export(db,Path(td),'migros');self.assertEqual(summary['production_writes'],0);self.assertEqual(summary['candidates'],1);self.assertTrue((Path(td)/'aday_katalog.csv').exists())
if __name__=='__main__':unittest.main()
