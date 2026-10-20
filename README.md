# Kyron Market Scraper (Free / Ücretsiz)

Zamanlanmış (scheduled) bir barkod → ürün kütüphanesi toplama servisi.
Türkiye zincir marketlerinin online kataloglarından **ürün adı + marka + görsel + barkod (EAN-13)**
toplar, normalize eder ve **Firestore**'a yazar. Kyron uygulaması bu kütüphaneyi okur.

> **Tamamen ücretsiz çalışır:** sunucu (Cloud Run) yok. Zamanlayıcı + çalıştırıcı olarak
> **GitHub Actions** (ücretsiz cron), veritabanı olarak **Firestore Spark (ücretsiz) planı**,
> görseller ise **yeniden barındırılmadan** kaynak URL olarak saklanır (depolama maliyeti = 0).

---

## Neden bu ücretsiz?

| Bileşen | Araç | Ücret |
|--------|------|-------|
| Zamanlayıcı + çalıştırıcı | **GitHub Actions** (cron) | Public repo: sınırsız. Private repo: 2000 dk/ay ücretsiz |
| Veritabanı | **Firestore (Spark)** | Ücretsiz kota: ~1 GiB, 20K yazma/gün, 50K okuma/gün |
| Görsel | Kaynak URL (rehost yok) | 0 |
| Çalıştırma ortamı | GitHub-hosted runner | 0 |

Uygulama artık sürekli açık bir sunucu değil; **tek sefer çalışıp çıkan bir CLI job**. GitHub Actions
bunu her gün cron ile tetikler.

---

## Mimari

```
GitHub Actions (cron: her gün 03:00 Europe/Istanbul)
        │  gradle run --args="all"
        ▼
CLI job (Main.kt)  ── ScraperRegistry ── ScrapeOrchestrator
        │                                    │  extract → EAN normalize → upsert
        ▼                                    ▼
  Scraper'lar                          Firestore (doc id = barkod)
   • off          → Open Food Facts (ODbL, önerilen temel katman)
   • migros       → Playwright (JS-render)
   • carrefoursa  → Playwright (JS-render)
   • macrocenter  → Playwright + ürün API yanıtı + etiket barkodu (ZXing)
   • sok / a101 / file / hakmar → HTTP + Jsoup
```

Ekstraksiyonun kalbi **schema.org JSON-LD** okumasıdır (`gtin13`, `name`, `brand`, `image`).
JSON-LD yoksa OpenGraph/`<h1>` fallback'i devreye girer.

---

## Kurulum (adım adım)

### 1. Repo'yu oluştur
Bu klasörü kendi (tercihen **private**) GitHub repo'na push et.

### 2. Firebase servis hesabı anahtarı al
- Firebase Console → Project Settings → **Service accounts** → *Generate new private key*.
- İnen JSON dosyasının içeriğini kopyala. Bu hesabın **Cloud Datastore User** (`roles/datastore.user`) rolü olmalı.
- ⚠️ Bu dosyayı **asla repo'ya koyma** (`.gitignore`'da zaten engelli).

### 3. GitHub secrets/variables ekle
Repo → Settings → Secrets and variables → Actions:
- **Secret** `GCP_SA_KEY` = servis hesabı JSON'unun tamamı.
- **Secret** `GCP_PROJECT_ID` = Firebase proje kimliğin.
- (Opsiyonel) **Variable** `ENABLED_CHAINS` = ör. `off,sok` (boş bırakırsan `all`).

### 4. Çalıştır
- Otomatik: her gün 03:00 (Europe/Istanbul) cron tetikler.
- Elle: repo → Actions → *Kyron Market Scrape* → **Run workflow** (istersen `chains` gir).

---

## Lokal test (opsiyonel)

```bash
gcloud auth application-default login    # veya GOOGLE_APPLICATION_CREDENTIALS=/path/key.json
export GCP_PROJECT_ID=your-project
export ENABLED_CHAINS=off,sok

gradle installBrowsers    # Playwright Chromium'u indirir (bir kez)
gradle run --args="off,sok"
```

---

## Firestore ücretsiz kota yönetimi (önemli)

Spark planı günlük **~20.000 yazma** ile sınırlıdır. Tüm zincirleri tek günde tam çekmek bu sınırı aşabilir.
Kota içinde kalmanın yolları:

1. **Zincir rotasyonu:** Her gün farklı bir alt küme çek. `ENABLED_CHAINS` variable'ını günlere böl,
   ya da farklı cron'larla ayrı job'lar tanımla. Örnek (haftalık dağıtım):
   ```yaml
   # .github/workflows/scrape.yml içine ek cron'lar
   on:
     schedule:
       - cron: "0 0 * * 1"   # Pazartesi
       - cron: "0 0 * * 3"   # Çarşamba
   ```
   ve manuel çalıştırmada `chains` gir/repo variable ile hangi zincir olacağını belirle.
2. **Yazılmadan önce okuma yok:** Kod bilerek read-before-write yapmaz (okuma kotasını korur);
   aynı çalışma içinde tekrarlayan barkodlar da atlanır.
3. **`off` için bulk dump:** Yüz binlerce satırlık ilk yüklemeyi API yerine resmi Open Food Facts
   **data dump**'ıyla yap: https://world.openfoodfacts.org/data (ülke: Turkey). Tek seferlik
   toplu import, günlük yazma kotanı sabaha karşı parçalı çalıştırarak ya da yerel batch ile yapılabilir.

---

## Veri modeli (Firestore, doc id = barkod)

| Alan | Tip | Not |
|------|-----|-----|
| `barcode` | string | EAN-13, birincil anahtar |
| `name` | string | Kanonik ad |
| `brand` | string? | Marka |
| `imageUrl` | string? | Kaynak görsel URL'si (rehost edilmez) |
| `category` | string? | Kategori |
| `unit` | string? | ör. "500 g" |
| `source` | string | `off` / zincir slug'ı / `user` |
| `sourceUrl` | string? | Kaynak sayfa |
| `confidence` | number | Kaynak güven skoru |
| `updatedAt` | number | epoch ms |

---

## Dürüst uyarılar

1. **"Tam ve eksiksiz" kalıcı değildir.** Site HTML'i değişince ilgili scraper'ın selector'larını
   güncellemen gerekir — bu bir bakım işi. `chains/` altındaki `// TODO`'ları tamamla.
2. **Kapsam kaynağa göre değişir.** Migros, CarrefourSA, Macrocenter, ŞOK, A101 barkod açısından
   zengin; File/Hakmar zayıf; **BİM** online barkodlu katalog vermez (dahil edilmedi).
3. **Temel katman `off` ile başla.** Tek başına yüz binlerce yerli ürünü barkod+görselle getirir.
4. **Görseller hotlink'tir.** Ücretsiz kalmak için kaynak URL saklanır; kaynak silerse görsel kırılabilir.
   İleride ücretsiz **Cloudflare R2** (10 GB) veya Firebase Storage'a taşıyabilirsin.
5. **Yasal/etik:** `robots.txt` ve kullanım şartlarına uy; `REQUEST_DELAY_MS` ile nazik davran;
   mümkünse açık lisanslı (OFF) görseli tercih et.

---

## Ücretsizi aşarsan (büyüme yolu)

- **Görsel barındırma:** Cloudflare R2 (10 GB ücretsiz) veya Firebase Storage.
- **Daha yüksek yazma limiti:** Firestore Blaze (kullandıkça-öde) ya da Supabase/Neon (Postgres, ücretsiz katman).
- **Daha uzun/sık job:** Kendi VPS'inde cron, ya da Cloud Run + Cloud Scheduler (bu repo'nun önceki sürümü).


## Macrocenter görsel ve etiket barkodu desteği

Güncel kategori keşfi, sonsuz kaydırma, ürün görsel URL'leri ve etiket görsellerinden
barkod okuma desteği için [Macrocenter kullanım ve doğrulama notlarına](docs/macrocenter.md) bakın.
Önce `gradle macrocenterSmoke` ile veritabanına yazmadan deneyin. Kaynak deneysel kalır;
Actions üzerinde çalıştırırken `chains=macrocenter` seçilmelidir.
