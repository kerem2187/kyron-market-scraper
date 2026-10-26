# Kaynak doğrulama durumu

Başarı ölçütü aynı ürüne ait ad, checksum-geçerli barkod ve gerçekten indirilebilen/çözümlenebilen görseldir. Ürün SKU/id veya görsel dosya adındaki rakamlar barkod olarak kullanılmaz. İsim benzerliğine göre başka marketin barkodu atanmaz.

## Doğrulanan temel kaynaklar

- Macrocenter: önceki canlı turda üç farklı tam kayıt doğrulandı.
- Open Food Facts: önceki canlı turda üç farklı tam kayıt doğrulandı.

## Güncellenen bağdaştırıcılar — yeni canlı doğrulama gerekli

- A101: gerçek `productDetail.attributes.barcodes`, `_p-` ürün bağlantıları, güncel kategori adresleri; HTTP yerine tarayıcı ile erişim.
- Migros: `/rest/products/screens/{id}` ve `data.storeProductInfoDTO`; ambalaj/etiket barkodu okuma.
- ŞOK: Next Flight içindeki yalnızca eşleşen `productData.product`; orijinal ürün görselleri ve ambalajdan barkod okuma. Her ürünün görselinde barkod bulunması garanti değildir.
- CarrefourSA: güncel kategori adresleri, `-p-` bağlantıları ve yalnızca ilgili SKU’ya ait ambalaj görselleri. Açık GTIN alanı veya okunabilir barkod bulunmayan ürünler tam kayıt sayılmaz.

## Harici veri erişimi gerektiren kaynaklar

- FİLE: gerçek kurumsal alan adı `file.com.tr`. Online alışveriş sayfası mobil uygulamaya yönlendiriyor. Mevcut koşullarda yetkili, barkod içeren ürün akışı/API erişimi bulunamadı; sahte `filemarket.com.tr` kategori adresleri kaldırıldı.
- Hakmar Alışveriş Merkezleri: hedef `hakmar.com.tr`; Hakmar Express değildir. Erişilebilir indirim sayfası bülten görselleri sunuyor; yapılandırılmış GTIN kataloğu bulunamadı. Şirketten sağlanan veya kullanımına izin verilen ürün akışı gerekli.

Bu iki kaynak boş/sahte ürün döndürerek başarılı gösterilmez. Tüm sekiz kaynak bağımsız salt okunur canlı testte tutulur; başarısızlıklar gizlenmez. Tüm marketlerin üretime hazır olduğu henüz iddia edilmez.

## Test ortamı

Tarayıcı iş akışları Playwright 1.47 destekli Ubuntu 22.04 üzerinde çalışır. Her işte tüm OS bağımlılıklarını apt ile yeniden kurmak yerine yalnızca Chromium indirilir. Önceki Migros turu ürün testine ulaşmadan OS kurulumu sırasında iş zaman aşımına uğramıştı.
