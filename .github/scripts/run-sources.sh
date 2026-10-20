#!/usr/bin/env bash
# Each source gets its own process, Firestore write budget and wall-clock limit.
# Keep processing after errors; workflow syncs the resulting catalog even on failure.
set -uo pipefail
: "${ENABLED_CHAINS:?ENABLED_CHAINS is required}"
: "${MAX_RUN_MINUTES:?MAX_RUN_MINUTES is required}"
: "${MAX_WRITES_PER_RUN:?MAX_WRITES_PER_RUN is required}"
[[ "$MAX_RUN_MINUTES" =~ ^([1-9]|10)$ ]] || { echo "Minutes must be 1-10"; exit 2; }
[[ "$MAX_WRITES_PER_RUN" =~ ^[1-9][0-9]*$ ]] || { echo "Write budget must be positive"; exit 2; }
IFS=',' read -ra sources <<< "$ENABLED_CHAINS"
failed=0
printf '| Source | Process result |\n|---|---|\n' >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
for source in "${sources[@]}"; do
  source="${source//[[:space:]]/}"
  case "$source" in
    macrocenter|migros|carrefoursa|sok|a101|file|hakmar|off|all) ;;
    *) echo "::error::Unknown or disabled source: $source"; failed=1; continue ;;
  esac
  echo "::group::Source $source"
  rc=0
  timeout --signal=TERM --kill-after=30s "$((MAX_RUN_MINUTES * 60 + 60))s" \
    gradle --no-daemon run --args="$source" || rc=$?
  echo "SOURCE_RESULT source=$source exit=$rc"
  printf '| %s | %s |\n' "$source" "$rc" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  echo "::endgroup::"
  if [[ "$rc" -ne 0 ]]; then
    echo "::warning::Source $source did not finish successfully (exit=$rc); continuing remaining sources."
    failed=1
  fi
done
exit "$failed"
