# Google Sheets / AppSheet catalog sync

The scraper can synchronize canonical Firestore `products` into the existing `KATALOG` tab.

Required headers, in order:

`BARKOD | URUNADI | KategoriAdi | AltKategoriAdi | UrunGorseli`

Behavior:
- `BARKOD` is the key and is written as text with `RAW` input mode.
- Existing non-empty manual values are preserved.
- Blank name/category/image fields are enriched from Firestore.
- New barcodes are appended.
- Duplicate barcode rows are collapsed to the first row.
- `URUN_TAKIP` is never modified.

Setup:
1. Enable Google Sheets API in project `kyron-21`.
2. Share the spreadsheet as Editor with `firebase-adminsdk-fbsvc@kyron-21.iam.gserviceaccount.com`.
3. Keep `GCP_SA_KEY` and `GCP_PROJECT_ID` in GitHub Actions secrets.
4. Run `Kyron Market Scrape`; successful scraping is followed by catalog sync.
