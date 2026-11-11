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
for f in "$TMP/prelude"/*.yaml; do
  [ -f "$f" ] || continue
  [ "$(basename "$f")" = "default.yaml" ] && continue
  cp -f "$f" "$OUT/"
done
# Keep the bundled 1.0.0 dictionary and simplified schema under our control.
if [ ! -f "$OUT/luna_pinyin.dict.yaml" ]; then cp -f "$TMP/luna/luna_pinyin.dict.yaml" "$OUT/"; fi
if [ ! -f "$OUT/luna_pinyin.schema.yaml" ]; then cp -f "$TMP/luna/luna_pinyin.schema.yaml" "$OUT/"; fi
if [ ! -f "$OUT/luna_pinyin.yaml" ] && [ -f "$TMP/luna/luna_pinyin.yaml" ]; then cp -f "$TMP/luna/luna_pinyin.yaml" "$OUT/"; fi
cp -f "$TMP/luna"/*.txt "$OUT/" 2>/dev/null || true

echo "Fetching OpenCC Traditional-to-Simplified conversion data..."
git clone --depth 1 https://github.com/BYVoid/OpenCC.git "$TMP/opencc"
mkdir -p "$OUT/opencc"
cp "$TMP/opencc/data/config/t2s.json" "$OUT/opencc/t2s.json"
for f in CJK_Compatibility_Ideographs.ocd2 TSPhrases.ocd2 TSCharactersExt.ocd2 TSCharacters.ocd2; do
  test -s "$TMP/opencc/data/dictionary/$f"
  cp "$TMP/opencc/data/dictionary/$f" "$OUT/opencc/$f"
done
cp "$TMP/opencc/LICENSE" "$OUT/opencc/LICENSE" 2>/dev/null || true

echo "Rime support data refreshed; bundled luna_pinyin.dict.yaml and luna_pinyin_simp.schema.yaml were preserved."
