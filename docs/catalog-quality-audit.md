# Catalog structural quality audit

`SHEETS_QUALITY phase=before|after` reports structural counts for all **nonblank-barcode rows read by the existing KATALOG reader**, separately from incoming `skippedIncomplete` products. Rows with an empty barcode are excluded by that reader; this is not a complete physical-sheet audit.

- Missing name/image/category/subcategory counts include whitespace-only values.
- Non-HTTPS image references are a review count, **not automatically invalid**: AppSheet can use relative image paths. No image is downloaded or checked for matching the product.
- Barcode checks use the existing supported ASCII GTIN-8/UPC-A/GTIN-13 format and checksum rules. They do not prove GS1 assignment, reject every placeholder, or validate a physical package.
- Canonical duplicate rows count extra occurrences, grouping UPC-A with its zero-prefixed EAN-13. No rows are merged, deleted, compacted or rewritten.
- A subcategory without a category is reported. Filled-but-wrong classifications cannot be detected without a reviewed taxonomy and mapping. No category inference is introduced.

The audit is pure/read-only, logs aggregate counts only, and makes no network requests. The existing sync still writes its planned enrichments/appends; this change neither adds writes nor changes merge policy. `verified=true verificationScope=plannedWritesOnly catalogQualityGuaranteed=false` means planned writes passed read-back, **not** that the whole catalog is complete or semantically correct. Existing `verified=true` consumers remain compatible.

Tests: `gradle --no-daemon test`. Do not run `syncSheetsCatalog` against production to test this audit. The normal PR CI tests/builds without production credentials; merging sheet-code changes into main triggers the existing production sync workflow. Review that release effect before merge. Rollback: revert the audit class, two log integrations, tests and this document; no data rollback is needed for the audit itself.
