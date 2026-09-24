#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/rime"
mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Fetching open-source Rime prelude/luna-pinyin support files without replacing Drumstick's bundled dictionary/schema..."
git clone --depth 1 https://github.com/rime/rime-prelude.git "$TMP/prelude"
git clone --depth 1 https://github.com/rime/rime-luna-pinyin.git "$TMP/luna"
cp -f "$TMP/prelude"/*.yaml "$OUT/" 2>/dev/null || true
# Keep the bundled 1.0.0 dictionary and simplified schema under our control.
if [ ! -f "$OUT/luna_pinyin.dict.yaml" ]; then cp -f "$TMP/luna/luna_pinyin.dict.yaml" "$OUT/"; fi
if [ ! -f "$OUT/luna_pinyin.schema.yaml" ]; then cp -f "$TMP/luna/luna_pinyin.schema.yaml" "$OUT/"; fi
if [ ! -f "$OUT/luna_pinyin.yaml" ] && [ -f "$TMP/luna/luna_pinyin.yaml" ]; then cp -f "$TMP/luna/luna_pinyin.yaml" "$OUT/"; fi
cp -f "$TMP/luna"/*.txt "$OUT/" 2>/dev/null || true

echo "Rime support data refreshed; bundled luna_pinyin.dict.yaml and luna_pinyin_simp.schema.yaml were preserved."
