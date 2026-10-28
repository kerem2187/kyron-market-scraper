# CI ve kontrollü yayın

## İş akışlarının sınırları

| İş akışı | Amaç | Üretime yazma / kimlik bilgisi |
| --- | --- | --- |
| `ci.yml` | PR, main, hardening ve CI düzeltme dalında test/build | Yok; yalnız contents:read |
| `pr-validation.yml` | Eski giriş noktasından elle test/build | Yok; yalnız workflow_dispatch, contents:read |
| `source-smoke.yml` | Sınırlı ürün/görsel ve keşif doğrulaması | Veritabanı kimliği yok; dış sitelere okuma istekleri var |
| `macrocenter-smoke.yml` | Sınırlı kaynak doğrulaması | Akışın kendi salt okunur kapsamına bakın |
| `scrape.yml` | Zamanlı/elle gerçek katalog toplama | Firestore yazabilir; onaylı üretim işlemi |
| `sheets-sync.yml` | Firestore → AppSheet KATALOG | Gerçek Google Sheets yazmaları; zamanlı/elle/path filtreli main push |

## Bu pakette değişenler

- Eski satır 33 YAML hatası, eski akışın credential-free elle doğrulama olarak yeniden düzenlenmesiyle giderildi.
- Eski akıştaki üretim servis hesabı, gerçek OFF/Firestore smoke işi ve sabit PR #1 yorumları kaldırıldı.
- Otomatik doğrulama tek CI akışında; test iptali yalnız aynı ref'in eski CI işini etkiler, üretim writer kilitlerini etkilemez.
- Checkout'tan sonra Git kimliği kalıcı tutulmaz. Test raporları başarısızlıkta da 7 gün saklanır.
- Test komutları `test build`; `run` ve `syncSheetsCatalog` çağrılmaz. SHEETS_SYNC_ENABLED=false tek başına güvenlik kontrolü değildir: asıl sınır üretim kimliklerinin bu işlere verilmemesidir.
- Kaynak toplayıcılar, zamanlamalar, veri şeması, üretim kimlikleri ve OFF kapsamı değişmez. Hakmar/FİLE eklenmez.

## Yerel doğrulama

Java 21 ve Gradle 8.10:

```bash
gradle --no-daemon test build
actionlint .github/workflows/*.yml
```

Bunlar canlı veri testi değildir. Kaynak smoke başarısı tüm günlük katalogların eksiksizliğini kanıtlamaz; bir kaynak başarısızsa başarılı raporlanmaz.

## Yayın kapıları

1. Değişiklikleri ayrı PR'da incele; diff kapsamını ve gerçek HEAD'i doğrula.
2. CI/test ve workflow sözdizimi geçmeli. Kaynak smoke sonuçlarını ayrıca değerlendir.
3. Gerçek yazan değişiklikler için güncel özel yedek, onaylı geri dönüş ve açık üretim etkisi onayı gerekir.
4. Yalnız CI/belge paketi `sheets-sync.yml` push path'leriyle eşleşmez. Ürün/sheets kodu veya build dosyası içeren diğer PR'lar aynı kabul edilmez.
5. Birleştirilmiş gerçek ağaçta yeniden CI; ardından yalnız onaylı kapsamda yayın ve log geri okuma.

## Geri alma ve açık sınırlar

Bu paketin revert'i CI davranışını geri çevirir; ürün verisini geri yüklemez. Eski geçersiz/yazan workflow'u geri getirmek güvenli varsayılan değildir: gerekirse yalnız yeni workflow değişikliğini düzeltin. PR #16/#17 bu pakete dahil değildir.

Dal koruması, zorunlu inceleme, MFA, secret rotasyonu ve en az ayrıcalıklı servis hesabı ayrıca değerlendirilmelidir; bu paket bunları etkinleştirmiş sayılmaz. Kotlin/Java/Gradle bağımlılıkları topluca yükseltilmedi. CI eklentileri resmi v7/v6/v6 commit SHA'larına sabitlendi; artifact yükleyici Node 24 kullanan v7 commit'ine taşındı. Üretim iş akışlarının eklentileri bu pakette değiştirilmedi. Ücretsiz hizmetler kota ve sağlayıcı şartlarına tabidir; sıfır fatura veya üretim lisansı garantisi verilmez.
