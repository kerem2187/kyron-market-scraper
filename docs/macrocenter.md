# Macrocenter: görsel ve barkod toplama

Macrocenter artık HTTP-only örnek kategori adreslerini kullanmaz. Playwright ile gerçek
menüdeki üst kategorileri keşfeder, kategorilerin sonsuz kaydırmasını takip eder ve aynı
ürünü yalnızca bir kez işler. Her kategori varsayılan olarak en fazla 3 liste sayfasıyla sınırlandırılmıştır.

## Veri çıkarma sırası

1. Açılan ürünün kendi `/rest/products/<sku>` yanıtını yakala. `prettyName` ürün URL'siyle
   eşleşmelidir; önerilen ürünler bu amaçla kullanılmaz.
2. Ürün adı, marka, kategori ve yüksek çözünürlüklü ürün görselinin kaynak URL'sini al.
3. Açıkça verilmiş, sağlama toplamı geçerli GTIN/EAN alanı varsa onu kullan.
4. Barkod eksikse ürünün `productSafetyInfo.productSafetyImageInfo.images` etiketlerini,
   ardından kendi ürün fotoğraflarını ZXing ile tara. Yalnız EAN-13, EAN-8 ve UPC-A okunur;
   QR kodlar veya SKU/ürün kimlikleri barkod kabul edilmez.
5. Okunamayan barkodu boş bırak. Mevcut pipeline barkodsuz ürünü kanonik kataloğa yazmaz.
   Etiket fotoğrafı ile katalog görseli farklıdır: `imageUrl` ürün fotoğrafı olarak kalır.

JSON-LD'nin `WebPage -> mainEntity -> offers -> itemOffered` yapısı da desteklenir.
Görseller yeniden barındırılmaz. Etiket indirmeleri mevcut ortak hız sınırına uyar;
indirme 8 MiB, çözülmüş etiket 20 megapiksel ile sınırlandırılır.

## Çalıştırma

GitHub Actions -> **Kyron Market Scrape** -> **Run workflow**:
- `chains`: `macrocenter`
- `max_writes`: önce küçük bir değer (ör. `20`)

Workflow Macrocenter seçildiğinde Chromium'u kurar. Firestore/Sheets adımları mevcut
akışla aynıdır; bu geliştirme günlük otomatik çalıştırmayı etkinleştirmez.
Kaynak **EXPERIMENTAL** kalır ve `all` seçimine otomatik eklenmez.

Önce veritabanına yazmadan kontrol etmek için (Java 21 + Gradle 8.10):

```sh
gradle installBrowsers
MACROCENTER_SEED_URLS=https://www.macrocenter.com.tr/sut-c-116eb gradle macrocenterSmoke
```

`MACROCENTER_SEED_URLS` isteğe bağlı, virgülle ayrılmış Macrocenter kategori URL'leridir.
Normal taramada boş bırakılırsa güncel kategoriler menüden keşfedilir.
Smoke komutu en fazla üç ürünü yazdırır; Firestore veya Sheets istemcisi oluşturmaz.
`NOT_FOUND` barkodun okunamadığı anlamına gelir, başarıyla bulunduğu anlamına gelmez.

## Doğrulama ve sınırlar

- Gerçek ürün yanıtından küçültülmüş `product-detail.json` fikstürü: Uluova Kefir Chia
  Tohumlu 1 L. Yanıt barkod alanı içermiyor; etiket görselinin ayrı alanda olduğu doğrulandı.
- Gerçek etiket görseli yerel Java okuyucusuyla ayrıca test edildi ve
  `8683229254779` EAN-13 barkodu okundu. Bu, tüm ürünlerin barkodunun okunacağı anlamına gelmez.
- Birim testleri: ürün/URL eşleşmesi, yanlış GTIN, SKU'nun reddi, görsel host kısıtlaması,
  kategori keşfi, link tekilleştirme, iç içe JSON-LD, yatay/dikey etiket barkodu, QR reddi.
- Her ürünün okunabilir etiket görseli yoktur; tam barkod kapsamı garanti edilmez.
- Cloudflare, bölgesel stok, boş kategori veya değişen seçiciler taramayı engelleyebilir.
  Erişim kontrolünü aşan bir mekanizma eklenmemiştir. Site koşullarını/robots kurallarını
  kontrol edin ve istek aralığını düşürmeyin.
- Bu değişiklik tek başına GitHub-hosted runner üzerinde uçtan uca canlı çalışma
  doğrulaması değildir. İlk küçük bütçeli çalışma raporu ayrıca kontrol edilmelidir.

## Canlı çalışma hata düzeltmesi

İlk canlı çalışmada Chromium `Network.getResponseBody` hataları görüldü ve çalışma
iptal edildi. Ürün yanıtı artık `route.fetch` ile bir kez alınıp orijinal tarayıcı
isteğine `fulfill` edilir. Gövde Chromium'un geçici ağ önbelleğinden okunmaz.
Katalog ve ürün sayfaları tek bir tarayıcı oturumunda tutulur; ürünler ilk liste
sayfası yüklendiğinde işlenir, önce bütün kategori taramasının bitmesi beklenmez.

Yeni varsayılan sınırlar:
- En fazla **30 ürün denemesi** (hatalar ve barkodsuz ürünler de sayılır).
- **10 dakika** uygulama süresi; kalan süre her isteğin zaman aşımına uygulanır.
- Kategori başına **3 liste sayfası**.
- **5 ardışık ürün hatasında** erken duruş.
- Macrocenter içeren Actions çalışmasında **20 dakika job zaman aşımı**.
- Her üründen sonra ilerleme ve çıkışta `FINAL` özeti.

`max_writes` hâlâ Firestore yazma bütçesidir; ürün deneme sayısından farklıdır.
Workflow'daki `max_products` ve `max_minutes` alanları bu ek sınırları yönetir.
Macrocenter süre ayarı en fazla 30 dakika kabul etse de Actions job sınırı 20 dakikadır;
kurulum/test süresine pay bırakmak için önerilen değer en fazla 10 dakikadır.
Etiket istekleri tek denemeyle ve en fazla 8 saniyelik zaman aşımıyla çalışır.

**Macrocenter Read-only Smoke** iş akışı Firestore/Sheets anahtarı almadan canlı
ürün sorgular. En fazla 10 deneme, 3 dakika ve ilk üç ürünle sınırlıdır. En az bir
üründe hem barkod hem görsel yoksa başarısız sayılır; boş sonuç başarılı gösterilmez.
Bu iş akışı katalog verisi yazmaz ve Firestore taramasını otomatik başlatmaz.


## İthal ürün odaklı kaynak seçimi

Actions çalıştırma formundaki `seed_urls` alanı kategori (`-c-`) veya seçki (`-l-`)
URL'lerini kabul eder. Örneğin Macrocenter ana sayfasındaki **Macropicks 🌍** listesi:
`https://www.macrocenter.com.tr/macropicks-l-8d03038`. Kaynak URL'leri yalnızca
HTTPS ve `www.macrocenter.com.tr` alan adından kabul edilir. Aynı kaynak seçimi
önce **Macrocenter Read-only Smoke** iş akışıyla yazmadan kontrol edilebilir.

Macropicks seçkisinde bulunmak ürünün ithal olduğunun kesin kanıtı değildir.
Bu tarama ürünleri otomatik "ithal" olarak etiketlemez; menşe/ithalatçı bilgisi ayrıca
doğrulanmalıdır. Barkod veya okunabilir etiket bulunmayan ürünler kanonik kataloğa
yazılmaz; listenin tamamının aktarılacağı garanti edilmez.
