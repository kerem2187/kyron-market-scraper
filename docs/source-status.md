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

Aktif kapsam: Macrocenter, Migros, ŞOK ve Open Food Facts. Yalnızca bu dört kaynak zamanlanmış çalışmada, manuel varsayılanlarda ve salt okunur canlı test matrisinde yer alır. A101 ve CarrefourSA da kullanıcı isteğiyle DISABLED durumundadır; manuel seçimleri reddedilir. Eski kayıtlar silinmez.

## Test ortamı

Tarayıcı iş akışları Ubuntu 22.04 üzerinde çalışır. Playwright 1.47 yerine 1.64 kullanılacak biçimde güncellendi; `40395328348` canlı turunda Macrocenter, Migros, ŞOK ve Open Food Facts yeniden geçti. Her işte tüm OS bağımlılıklarını apt ile yeniden kurmak yerine yalnızca Chromium indirilir. Önceki Migros turu ürün testine ulaşmadan OS kurulumu sırasında iş zaman aşımına uğramıştı.

## Açık sorunların tanılanması

Son altı kaynaklı canlı turda A101 ve CarrefourSA ürün keşfinde başarısız oldu. Yeni tanılama HTTP durumunu, sınırlı sayfa başlığını ve bulunan ürün bağlantısı sayısını kaydeder; HTML, çerez veya oturum anahtarları kaydedilmez. HTTP erişim engeli başarılı veya boş katalog gibi gösterilmez. Güvenlik doğrulaması aşılmaya çalışılmaz.

## Dört kaynakla devam kararı

Kullanıcı yalnızca çalışan kaynaklarla devam edilmesini istedi; A101/CarrefourSA için izinli API bulunmuyor. Son canlı turda A101 üç kategoride HTTP 403 “Just a moment...”, CarrefourSA üç kategoride HTTP 403 “Attention Required! | Cloudflare” döndürdü. İkisi aktif kapsamdan çıkarıldı; erişim engeli aşılmaya çalışılmaz. Dört kaynaklı CI ve canlı testler yeşil olmadan ana dala birleştirilmez.

## Daily reliability update

OFF remains enabled at the user's request. The first production run on merge 3540668 passed Macrocenter/Migros/ŞOK and Sheets delivery but failed on OFF page 2 after 97 unchanged complete records from page 1. The follow-up adds conservative OFF rate limiting, Retry-After-aware retries and a dedicated page-2 live test. Failures are still fatal, not relabeled success.

Daily ingestion now attempts discovered public categories instead of restricting Macrocenter to Macropicks. Four source jobs are independent; the final verified KATALOG sync runs after all attempts, including on partial source failure. Budgets remain explicit. This widens coverage but does not prove a complete market inventory.
