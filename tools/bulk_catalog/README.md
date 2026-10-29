# Migros / Macrocenter — ücretsiz, salt okunur toplu katalog adayı taraması

**Durum:** Birim testleri geçiyor. 2026-10-29 canlı doğrulamasında her iki marketin robots.txt sayfası HTTP 403 döndürdü; güvenli duruş gerçekleşti, yeni tam kayıt üretilmedi. Bu sürümün binlerce ürünlük canlı kabul testi tamamlanmış değildir. Önceki 60 ürünlük örnek tarama farklı, sınırlı bir çalışmadır.

## Amaç ve sınırlar
- Normal Chromium sayfalarında mevcut kategori bağlantıları ve `Sonraki sayfa` kontrolü ile keşif. Gizli/izin gerektiren API, oturum anahtarı, proxy, stealth veya CAPTCHA aşma yok.
- Bir turda market başına 1–1500 **ürün denemesi**, 1–110 dakika; iki market ayrı işler. Keşif kuyruğu parti boyutunun yaklaşık iki katına kadar hazırlanır. Her kategoride sayfa bütçesi vardır; tüm mağaza portföyünü kapsama garantisi değildir.
- Ürün URL'si ile tam eşleşen site yanıtı; GTIN kontrol basamağı; gerçek ambalaj barkodu; indirilebilen/çözümlenen ön görsel.
- SKU/id/dosya adından barkod üretilmez. Sıfır yer tutucular, çoklu paketler, ad/barkod çelişkileri ve şüpheli birimler incelemeye ayrılır. Görsel okuma her ürün için mümkün değildir.
- 403/429/challenge durumunda durur, engeli aşmaz. Platform IP'sini değiştirmek veya tekrar tekrar çalıştırmak çözüm sayılmaz.
- SQLite kuyruk, görüntü özeti/okuma önbelleği ve tamamlanan URL'ler saklanır. Yeniden çalıştırma tamamlanan ürünleri atlar; başarısız kayıtlar incelemede tutulur. Süre dolumu veya SIGTERM'de checkpoint ve mevcut çıktılar korunur.
- Aynı barkod farklı ürün adlarında görüldüğünde önceki aday da sonraki CSV dışa aktarımında incelemeye taşınır. Önceki CSV'ler kanonik katalog değildir.

## Çıktılar
`aday_katalog.csv`, `inceleme.csv`, `ozet.json`, `checkpoint.sqlite`.
UTF-8 CSV'de barkodları metin olarak içeri aktarın (baştaki sıfırları koruyun). Kaynak, ürün URL'si, barkod kanıt görseli, kontrol zamanı ve inceleme gerekçesi vardır. CSV formül enjeksiyonuna karşı metin koruması uygulanır.

**Canlı KATALOG, AppSheet, Google Sheet veya Firestore'a yazılmaz. Hiçbir Google/GCP anahtarı yüklenmez.** İnsan kabulünden sonra ayrı bir aktarım gerekir. Kaynak görseller yeniden barındırılmaz; telif/lisans ve kullanım koşulları geçerlidir. Kaynak erişimi ve bağlantılar değişebilir.

## Yerel/Colab çalıştırma
```sh
pip install -r tools/bulk_catalog/requirements.txt
python -m playwright install --with-deps chromium
cd tools/bulk_catalog
python -m unittest -v
python bulk_catalog.py --source migros --out output/migros --limit 1000 --minutes 110
python bulk_catalog.py --source macrocenter --out output/macrocenter --limit 1000 --minutes 110
```
Ücretsiz Colab CPU oturumu kesilebilir; checkpoint dosyasını oturum dışında saklamadan kalıcılık beklemeyin. Doğrudan HTTP veya normal tarayıcı erişimi engellenirse durun; Colab/IP değiştirme ile aşmayın.

## GitHub Actions
`Bulk Catalog Candidates (Read Only)` iş akışı yalnızca bu klasör değişince birim testi yapar. Manuel `collect=true` ayrıca dış kaynak okumalarını açar; varsayılan kapalıdır. Public repoda standart Ubuntu runner kullanılır; ücretli runner/abonelik açılmaz. GitHub'ın ücretsiz koşulları/kotaları yine geçerlidir. Cache kaybolabilir; artifact checkpoint'i indirmek bağımsız yedektir. Otomatik günlük tetikleyici eklenmemiştir.

**Mevcut engeller çözülmeden toplu üretim taraması veya binlerce doğrulanmış barkod sözü verilmez.** Günlük mevcut Kotlin/Firestore/Sheet hattı bu araçtan bağımsızdır ve değiştirilmez.
