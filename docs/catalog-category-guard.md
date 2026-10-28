# AppSheet category suggestion guard

Catalog sync reads the existing category dictionary before any write. `SHEETS_CATEGORY_TAB` defaults to the actual Turkish tab name `KATEGORİLER` (dotted İ), independently of the AppSheet table's internal identifier. Required first three headers: `KategoriID`, `KategoriAdi`, `AltKategoriAdi`; later columns are ignored. Fully blank rows are ignored. Empty dictionaries, incomplete rows and duplicate IDs fail before catalog writes; an unreadable dictionary never falls back to raw labels.

A source category may fill a blank KATALOG category only when it exactly matches a dictionary category (surrounding whitespace trimmed, no translation/fuzzy/name/image inference). If the existing row has a subcategory, the proposed category/subcategory pair must also exist in the dictionary. New complete products with unknown categories are still appended with barcode/name/image, but category remains blank for review. The default plan without a dictionary denies all category suggestions.

No populated manual category/subcategory is overwritten, corrected or erased. No subcategory is automatically generated. Names/images, original row numbers, duplicate-row preservation and existing complete-product checks stay unchanged. `SHEETS_CATEGORY_GUARD` reports the dictionary pair count and the number of category suggestions withheld; no product/user values are logged.

This prevents structurally unsupported automatic suggestions, not wrong-but-valid product classification. A yogurt wrongly assigned to a valid dry-goods pair remains a human-review issue. Taxonomy changes still require review; no source-label translation mapping is bundled. Do not publish private dictionary contents or credential files in fixtures.

Validation: `gradle --no-daemon test build` (unit/build only). Tests cover exact/unknown categories, pair conflicts, header/empty/incomplete/duplicate-ID rejection, manual preservation and repeat sync. Live Google Sheets permissions/read failures and production writes are not validated by unit tests. The existing service account must be able to read the dictionary tab; the extra request uses its existing Sheets scope and adds one GET per sync, not a new paid service.

Release: merging changes under sheets/** to main triggers the existing production sync workflow. Do not merge without the live-write release gate and recoverability checks. PR #16 is separate aggregate audit work; this guard is based on main and does not require it, but both touch the same sync method and must be reviewed together for conflict resolution. No main merge or production sync is part of preparing this change.

Rollback: revert this change to Config, CatalogTaxonomy, CatalogSyncPlan, SheetsCatalogSync, tests and this document. Reverting changes future suggestion policy, not past rows; new blank category cells are not backfilled by this rollback itself. Review the main push trigger before any revert. No full backup/restore guarantee is made.
