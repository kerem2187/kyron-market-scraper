# KYRON — Ücretsiz katalog araştırma aracı (U41)

**Mevcut Kotlin günlük toplama hattının yerine geçmez.** Ayrı, kimlik bilgisi istemeyen bir Colab/Python yardımcı aracıdır. Canlı Google Sheets, Firestore, AppSheet ve Telegram'a **hiçbir yazma işlemi içermez**.

## Seçim ve maliyet

- Crawl4AI 0.9.5 (Apache-2.0): yerel Python kütüphanesi, ücretli bulut API'si/LLM anahtarı kullanılmaz. Bu proje Crawl4AI (https://github.com/unclecode/crawl4ai) kullanır.
- Firecrawl: açık kaynak AGPL sürümü Docker ile birden fazla servis işletmeyi gerektirir; yönetilen bulut sürümü kredilidir. Ücretsiz Colab yardımcı işi için tercih edilmedi.
- Scrapy (BSD-3-Clause): güçlü bir alternatif; mevcut Kotlin zamanlanmış hattını bir ikinci tam crawler ile değiştirmek yerine dar bir yardımcı araç tercih edildi.
- Colab ücretsiz kaynakları süreklilik/7x24 çalışma garantisi vermez. Bu defter CPU kullanır; GPU/ücretli plan gerekmez.
- Python bağımlılığı kurulu olması LLM kullanılacağı anlamına gelmez: kod LLM/stealth/proxy API'si çağırmaz, API anahtarı veya servis hesabı istemez.

## Kurulum ve kullanım

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r tools/free_catalog/requirements.txt
cd tools/free_catalog
python -m unittest -v
# UTF-8 metin dosyası: satır başına bir barkod (metin olarak, baştaki sıfırları koruyun)
python catalog_pipeline.py --barcodes barkodlar.txt --out catalog_output
# Kullanımına izin verilen, doğrudan ürün sayfaları; her host açıkça onaylanır:
python catalog_pipeline.py --urls urller.txt --allow-host www.example.com --out catalog_output
```

Colab: `KYRON_Ucretsiz_Katalog.ipynb` dosyasını açın, kurulum ve test hücrelerini çalıştırın, Barkodlar/Ürün_URLleri alanlarını düzenleyin. Varsayılan üç barkod yalnızca küçük bir canlı denemedir. Liste bir çalıştırmada en çok 100, varsayılan 30 kayıttır. Otomatik ürün/kategori arama veya tüm interneti tarama özelliği değildir.

## Toplama ve kalite kapıları

1. Kamuya açık HTTPS, onaylı ürün alan adı, DNS'te yalnızca global IP; her yönlendirme yeniden kontrol edilir. Çerez/oturum/özel ağ erişimi yok.
2. HTML ve görsel için robots.txt okunur; okunamıyorsa kapalı davranılır. 403/429 engelleri aşılmaz.
3. Yalnızca ana ürüne bağlı `Product` JSON-LD; öneri ürünleri, belirsiz varyantlar ve SKU/görsel dosya adından türetilen barkodlar reddedilir.
4. GTIN-8/12/13/14 checksum. UPC-A, mevcut Kotlin hattıyla uyumlu 13 haneye dönüştürülür; diğer kodların baştaki sıfırları korunur. Kötü kontrol basamağı "düzeltilmez".
5. `a`, `.`, `test` gibi anlamsız adlar reddedilir. İsim üretilmez, çeviri/LLM ile tahmin edilmez.
6. Görsel aynı ürün kaydındaki URL'den gelir. HTTPS/MIME/boyut/piksel ve gerçek resim çözümleme kontrolleri; SHA-256 kaydı.
7. Aynı barkodda çelişen adlar incelemeye ayrılır; rastgele bir kaynağa üstünlük verilmez.
8. Kaynak URL, zaman ve lisans bilgisi korunur. Eşleşme **kaynağın iddiasıdır**; checksum ve geçerli JPEG, semantik doğruluk garantisi değildir. İnsan onayı olmadan kanonik kataloğa aktarılmaz.

JavaScript gerektiren veya açık GTIN sunmayan siteler burada boş/belirsiz sonucu **başarı gibi göstermez**. Bunlar için mevcut siteye özel Kotlin adaptörleri korunur. Hakmar/FİLE/A101/CarrefourSA yeniden etkinleştirilmez.

### Open Food Facts

Yalnızca resmi belgede kullanımına izin verilen, tam barkodla ürün API sorgusu yapılır. HTML robots kuralları bu API izni yerine sayılmaz. En çok 12 istek/dakika hedeflenir (bu araç seri ve 5 sn aralıklı); resmi sınırlar değişebilir, IP'yi paylaşan diğer işler toplam kotayı etkileyebilir. Ürün bulunmazsa başka barkod tahmin edilmez. OFF topluluk verisidir, üretici doğrulaması değildir.

Veritabanı ODbL, içerikler DbCL, görseller CC BY-SA kapsamındadır. Türev veri tabanı paylaşımı ve atıf yükümlülükleri değerlendirilmeden özel kanonik veri tabanına otomatik birleştirme yapılmaz. Kaynak URL ve lisans sütunu silinmez. Ticari site görsellerinin erişilebilir olması yeniden kullanım izni değildir.

## Çıktılar

- `aday_katalog.csv`: yalnızca teknik kontrolleri geçen, **insan onayı gereken** barkod/ad/görsel adayları. CSV içe aktarırken BARKOD'u metin türünde tutun.
- `inceleme.jsonl`: bulunamayan, çelişen, engellenen veya görseli doğrulanamayan kayıtlar ve nedenleri.
- `ozet.json`: aday/inceleme sayıları, üretime yazma sayısı (daima 0), tarih.

Kategori ve altkategori bu araçta tahmin edilmez. Mevcut KATEGORİLER/LOKASYONLAR ve mağaza portföyüne dokunulmaz. Katalogdaki mevcut ad, görsel, eşik veya personel kaydı değişmez. Canlı aktarım, ayrı mevcut Kotlin kalite kapıları ve insan onayı gerektirir; bu çalışma otomatik yayın yetkisi vermez.

## Test ve sınırlar

Birim testleri ağsızdır, sahte verilerle doğrulama sınırlarını sınar. Canlı üç barkodluk deneme tüm katalog kapsamı, tüm marketler veya sürekli erişim garantisi değildir. Yeni bağımlılık/sürüm yükseltmesi yeniden test edilmelidir.

Geri alma: bu yardımcı klasörü/defteri kullanmayı bırakmak yeterlidir; üretim sistemi değiştirilmez. Orijinal Colab TimesFM/TradingAgents defteri korunur.

## Resmi kaynaklar

- https://github.com/unclecode/crawl4ai
- https://github.com/scrapy/scrapy
- https://docs.firecrawl.dev/contributing/self-host
- https://www.firecrawl.dev/pricing
- https://research.google.com/colaboratory/faq.html
- https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/
- https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side

## U41 doğrulama kaydı

- İzole Python 3.13 ortamında Crawl4AI 0.9.5 kurulumu ve import kontrolü geçti.
- 28 ağsız test geçti: gerçek Crawl4AI HTTP stratejisi, ana/öneri ürün ayrımı, varyant sorgusu, checksum, eksik ad/SKU, yinelenen/çelişen kayıt, özel DNS/IP engeli ve görsel bayt doğrulaması dahil.
- Canlı üç barkod denemesinde `8690632992846` için kaynak adı ve çözümlenen görsel adayı alındı. `5000112664782` OFF'ta HTTP 404 döndü; isim/görsel tahmin edilmedi. `8690632049618` yalnızca `Nesquik` adı verdiğinden yeterince ayırt edici bulunmadı ve incelemeye ayrıldı.
- Barkod örnekleri aracı sınamak içindir; kaynakta mevcut olmaları veya verilerin kusursuzluğu garanti edilmez. Damak adayı bile insan incelemesi olmadan canlı kataloğa aktarılmadı.
- Orijinal Colab defterindeki TimesFM/TradingAgents hücreleri ve canlı mağaza verileri değiştirilmedi.
