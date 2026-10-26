# Google Sheets / AppSheet catalog sync

Target: the existing spreadsheet's `KATALOG` tab, with headers:

`BARKOD | URUNADI | KategoriAdi | AltKategoriAdi | UrunGorseli`

- Barcode is the key and new rows use `RAW` input (text, retaining leading zeros).
- New rows must have checksum-valid barcode, product name and HTTPS image. Incomplete legacy records are skipped/reported, not exported as new incomplete entries.
- Existing non-empty/manual values are preserved. Only blank name/category/image cells are filled by exact row/cell address; row positions and existing duplicate rows are not compacted or deleted.
- New barcode rows are appended; existing barcodes are not appended again on a repeated run.
- Every append/enrichment is read back and verified. An API/read-back failure fails the sync rather than claiming success.
- `URUN_TAKIP` and columns outside the catalog range are not rewritten.
- Daily post-scrape sync and the 05:30 Istanbul fallback/manual workflow use one shared writer lock.
- No blind retry of ambiguous append requests: a subsequent run re-reads barcode keys to avoid duplicate appends.

The Google Sheets API must be enabled and the spreadsheet shared as Editor with the service account configured in the existing GitHub Actions secrets. Those credentials remain inside Actions; do not paste them into chat.

Updating this sheet does not force immediate refresh of every AppSheet client. The app must use this spreadsheet/tab and have suitable client sync settings. AppSheet URL/access is needed to verify those settings; Quick sync alone does not instantly display external Google Sheets edits.
