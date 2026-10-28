# Kyron Market Scraper

Ürün adı, doğrulanmış barkod ve kaynak görseli toplayan Kotlin/JVM katalog hattı.
Firestore'daki kanonik ürünler ayrı bir iş akışıyla Google Sheets/AppSheet `KATALOG` tablosuna aktarılır. Katalog, mağazanın gerçek stok veya satış defteri değildir.

## Kapsam ve dürüst durum

- Günlük iş akışının açık kaynak seçimi: `macrocenter,migros,sok,off`.
- Hakmar ve FİLE kapsam dışıdır. Kodda başka adaptör bulunması, günlük akışta etkin veya doğrulanmış olduğu anlamına gelmez.
- `all`, registry'nin VERIFIED kapsamıdır; açık dört kaynak listesiyle eş anlamlı değildir. Kaynak seçimini iş akışında açık belirtin.
- Dış kaynak erişimi ve görseller değişebilir. Bir smoke testi tüm ürünlerin günlük eksiksiz toplandığını kanıtlamaz. Kaynak bazında hata ve miktarları Actions'tan inceleyin.
- Yeni ücretli servis veya abonelik gerektirmeyen yapı hedeflenir. GitHub, Firestore ve Google kotaları/koşulları geçerlidir; sınırsız kullanım veya sıfır fatura garantisi yoktur. AppSheet lisans uygunluğu ayrı konudur.

## Mimari ve zamanlama

```text
GitHub Actions: Kyron Market Scrape (03:00 Europe/Istanbul, best-effort)
  → kaynak başına izole scraper ve bütçe
  → kanonik Firestore ürünleri + gözlemler

GitHub Actions: Sync AppSheet Product Catalog (05:30 Europe/Istanbul, best-effort)
  → Firestore okuma
  → KATALOG güvenli ekleme / eksik alan doldurma
  → AppSheet'in normal senkronizasyonunda görünürlük
```

Zamanlanmış işler arasında tamamlanma bağımlılığı yoktur; geciken toplama aynı günkü Sheet aktarımına yetişmeyebilir. GitHub cron kesin saat SLA'sı değildir. Kaynak görseller yeniden barındırılmaz; kaynağın görseli kaldırması halinde bağlantı bozulabilir.

## Güvenli geliştirme

Java 21 ve Gradle 8.10 ile, üretim kimlik bilgisi vermeden:

```bash
gradle --no-daemon test build
```

Sınırlı dış kaynak okuma testi, veritabanına yazmadan:

```bash
gradle installBrowsers  # Chromium kullanan kaynaklar için
gradle sourceSmoke --args="migros"
```

Bunlar tam katalog veya fiziksel AppSheet kamera/offline kabul testleri değildir.

## Üretim çalıştırması — veri yazar

Mevcut yetkili kurulum `GCP_PROJECT_ID` ve `GCP_SA_KEY` GitHub secrets kullanır. Anahtarları, kullanıcı verisini ve özel yedekleri repoya veya hata raporuna eklemeyin. Var olan secret'ı görmek için çıktı/log üretmeyin. Gerekli servis hesabı erişimi en az ayrıcalıkla yönetilmelidir.

`Kyron Market Scrape` elle çalıştırıldığında Firestore'a; `Sync AppSheet Product Catalog` elle çalıştırıldığında kaynak Sheet'e yazabilir. Bunlar “zararsız test” değildir. Aynı şekilde sheets kodu, build veya sync workflow değişikliklerinin main'e alınması path filtresine göre canlı eşitlemeyi tetikleyebilir.

Kota bütçeleri ürün sayısının tamamlanacağına dair vaat değildir. Bütçe/süre dolumu, başarısız istekler, görsel doğrulaması ve kaynak hataları günlük çıktılarla birlikte değerlendirilmelidir. AppSheet senkronizasyon gecikmesi de ayrıca izlenmelidir.

## Operasyon belgeleri

- [CI, güvenlik sınırları ve yayın kapıları](docs/CI_AND_RELEASE.md)
- [Günlük kaynak toplama](docs/nightly-scrape.md)
- [Google Sheets aktarımı](docs/GOOGLE_SHEETS_SYNC.md)
- [Kaynak durumları](docs/source-status.md)
- [Macrocenter doğrulaması](docs/macrocenter.md)

Kaynakların kullanım şartlarına, robots kurallarına, lisans/atıf yükümlülüklerine ve hız sınırlarına uyun. Daha agresif istek veya erişim engeli aşma yöntemleri çözüm sayılmaz. Bu belgeler güvenlik sertifikası, üretim hazır beyanı veya tüm entegrasyonların kabulü değildir.
