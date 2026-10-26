# Kaynak doğrulama durumu

Başarı ölçütü aynı ürüne ait ad, checksum-geçerli barkod ve gerçekten indirilebilen/çözümlenebilen görseldir. Ürün SKU/id veya görsel dosya adındaki rakamlar barkod olarak kullanılmaz. İsim benzerliğine göre başka marketin barkodu atanmaz.

## Doğrulanan temel kaynaklar

- Macrocenter, Migros, ŞOK ve Open Food Facts: `40304520683` numaralı salt okunur canlı turda her biri üç farklı tam kayıt testini geçti. Bu, tüm katalog için eksiksizlik garantisi değildir.

## Güncellenen bağdaştırıcılar

- A101: gerçek `productDetail.attributes.barcodes`, `_p-` ürün bağlantıları, güncel kategori adresleri; HTTP yerine tarayıcı ile erişim.
- Migros: `/rest/products/screens/{id}` ve `data.storeProductInfoDTO`; ambalaj/etiket barkodu okuma.
- ŞOK: Next Flight içindeki yalnızca eşleşen `productData.product`; orijinal ürün görselleri ve ambalajdan barkod okuma. Her ürünün görselinde barkod bulunması garanti değildir.
- CarrefourSA: güncel kategori adresleri, `-p-` bağlantıları ve yalnızca ilgili SKU’ya ait ambalaj görselleri. Açık GTIN alanı veya okunabilir barkod bulunmayan ürünler tam kayıt sayılmaz.

## Kullanıcı isteğiyle kapsam dışında tutulan kaynaklar

- FİLE: gerçek kurumsal alan adı `file.com.tr`. Online alışveriş sayfası mobil uygulamaya yönlendiriyor. Mevcut koşullarda yetkili, barkod içeren ürün akışı/API erişimi bulunamadı; sahte `filemarket.com.tr` kategori adresleri kaldırıldı.
- Hakmar Alışveriş Merkezleri: hedef `hakmar.com.tr`; Hakmar Express değildir. Erişilebilir indirim sayfası bülten görselleri sunuyor; yapılandırılmış GTIN kataloğu bulunamadı. Şirketten sağlanan veya kullanımına izin verilen ürün akışı gerekli.

Hakmar ve FİLE kullanıcı isteğiyle zamanlanmış çalışmadan, manuel varsayılan kaynak listesinden ve canlı test matrisinden çıkarıldı. İki bağdaştırıcı da DISABLED durumunda; açıkça seçilmeleri reddedilir. Mevcut veritabanı kayıtları silinmez.

Aktif kapsam: Macrocenter, Migros, CarrefourSA, ŞOK, A101 ve Open Food Facts. Bu altı kaynak bağımsız salt okunur canlı testte tutulur; başarısızlıklar gizlenmez. Kalan kaynakların üretime hazır olduğu canlı testleri tamamlanmadan iddia edilmez.

## Test ortamı

Tarayıcı iş akışları Ubuntu 22.04 üzerinde çalışır. Playwright 1.47 yerine 1.64 kullanılacak biçimde güncellendi; bu değişikliğin canlı test sonucu ayrıca doğrulanmalıdır. Her işte tüm OS bağımlılıklarını apt ile yeniden kurmak yerine yalnızca Chromium indirilir. Önceki Migros turu ürün testine ulaşmadan OS kurulumu sırasında iş zaman aşımına uğramıştı.

## Açık sorunların tanılanması

Son altı kaynaklı canlı turda A101 ve CarrefourSA ürün keşfinde başarısız oldu. Yeni tanılama HTTP durumunu, sınırlı sayfa başlığını ve bulunan ürün bağlantısı sayısını kaydeder; HTML, çerez veya oturum anahtarları kaydedilmez. HTTP erişim engeli başarılı veya boş katalog gibi gösterilmez. Güvenlik doğrulaması aşılmaya çalışılmaz.
