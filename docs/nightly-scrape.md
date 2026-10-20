# Nightly market scrape

`Kyron Market Scrape` runs daily at 00:00 UTC (03:00 Europe/Istanbul).
GitHub Actions schedules are best-effort; runner load can delay actual start.
Manual workflow dispatch remains available and uses the same source runner.

Scheduled sources: `macrocenter,migros,carrefoursa,sok,a101,file,hakmar,off`.
`all` is not used because it selects only VERIFIED sources and excludes Macrocenter.
Market Fiyati stays disabled. FILE/Hakmar are incomplete experimental sources,
not verified integrations; empty results fail rather than imply successful coverage.
Macrocenter starts from Macropicks; list membership does not prove imported origin.

Each source runs in its own process with 10 minutes application budget and 500
Firestore writes by default. These are PER SOURCE, not a shared global budget.
An external timeout allows 60 seconds startup grace plus a 30 second kill grace.
One failed source does not prevent subsequent sources from running. Known partial
source errors are visible in logs; a zero process result does not prove full coverage.
Requests use a 3 second base delay. This does not guarantee avoidance of rate limits.

Per-source Sheets sync is disabled. After all source attempts, the workflow runs
`syncSheetsCatalog` once, also when the source runner failed. Failed sources leave
the overall workflow red even when Sheets sync succeeds. Authentication/build
failures or cancellation can prevent execution; inspect each step separately.
The existing 05:30 Istanbul Sheets-only schedule remains as a fallback.

Sheets sync reads the whole canonical Firestore products catalog, appends missing
barcodes and fills empty fields; manual nonempty sheet values remain unchanged.
This updates AppSheet's backing sheet, not a forced refresh of every mobile client.
