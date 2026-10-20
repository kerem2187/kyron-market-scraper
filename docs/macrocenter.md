# Macrocenter: görsel ve barkod toplama

Macrocenter artık HTTP-only örnek kategori adreslerini kullanmaz. Playwright ile gerçek
menüdeki üst kategorileri keşfeder, kategorilerin sonsuz kaydırmasını takip eder ve aynı
ürünü yalnızca bir kez işler. Her kategori en fazla 50 kaydırmayla sınırlandırılmıştır.

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
