# Daily market ingestion and catalog delivery

- Daily start: 00:00 UTC / 03:00 Europe/Istanbul. GitHub schedules are best-effort, not an exact-time SLA.
- Active sources: `macrocenter,migros,sok,off`. OFF remains enabled. A101, CarrefourSA, FİLE and Hakmar remain disabled.
- Each source runs independently in a non-fail-fast matrix. Defaults are **30 minutes and 500 Firestore writes per source** (at most 2,000 counted writes across four sources). Canonical + per-source observation can consume two writes per product; the canary also counts.
- Application/OS timeouts remain bounded. A failed source leaves the overall workflow red; it cannot prevent the other sources or the final catalog sync from running unless the workflow is cancelled/setup fails.
- Manual runs can use smaller budgets. Macrocenter defaults to public category discovery instead of only Macropicks, with up to 1,000 product attempts and 10 listing passes inside the time/write caps. Migros and ŞOK also discover public category links; Migros attempts lazy-loaded listing links. Unsupported pagination, missing public GTINs and budgets still constrain coverage.
- A valid record requires a same-product name, checksum-valid barcode and HTTPS image whose actual bytes decode. No SKU/image filename is substituted for a GTIN. Unreadable/missing-image or missing-GTIN products are skipped, not invented.
- OFF search attempts are separated by at least seven seconds. HTTP 429 uses at least 60 seconds (and honors longer Retry-After); transient server/transport errors have bounded retries. HTTP errors remain failures, including an error on page 2 after a good page 1.
- CI includes a dedicated two-page OFF test. The ordinary three-product source smoke alone cannot validate pagination.

## Catalog and AppSheet

After all four source attempts, the `catalog-sync` job reads canonical Firestore products and updates the existing `KATALOG` tab. It runs even if a source fails. The independent 05:30 Istanbul Sheets-only fallback remains enabled. Both writers share the same concurrency lock.

The sync only appends missing complete barcodes and fills blank B/C/E cells. It does not rewrite the table, compact/delete duplicate rows, change manual non-empty values, touch URUN_TAKIP, or delete old products. Incomplete legacy Firestore records are reported and skipped. Every requested change is read back before sync success is reported. Ambiguous append failures are not blindly retried; the next sync re-reads barcode keys.

This delivers data into the AppSheet **backing sheet**. It does not force a refresh on all mobile clients. The target AppSheet app URL/access and its Sync on start/Automatic updates/data-source settings must be checked separately. External Sheets changes appear at the client's next sync.

## What “complete” does not mean

A successful bounded process is not proof that every product in every store has been enumerated. Some sites do not publish barcodes or complete image sets. Budget stops, discovered category/listing counts, rejected images, skipped records and source errors are logged; no full-catalog completeness guarantee is made. No price/stock or branch-specific availability guarantee is made either.

Source and catalog logs are retained as GitHub artifacts for seven days and key results are added to the run summary. A green code/live smoke check is necessary but not sufficient: the production Firestore/Sheets run must be checked too.
