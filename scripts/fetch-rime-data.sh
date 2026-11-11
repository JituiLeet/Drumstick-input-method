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
git clone --depth 1 --recurse-submodules https://github.com/BYVoid/OpenCC.git "$TMP/opencc"
OPENCC_BUILD="$TMP/opencc-build"
cmake -S "$TMP/opencc" -B "$OPENCC_BUILD" \
  -DBUILD_SHARED_LIBS=OFF \
  -DOPENCC_BUILD_TOOLS=ON \
  -DOPENCC_BUILD_DATA=OFF \
  -DOPENCC_ENABLE_INSTALL=OFF \
  -DENABLE_GTEST=OFF \
  -DENABLE_BENCHMARK=OFF
cmake --build "$OPENCC_BUILD" --target opencc_dict --parallel
OPENCC_DICT="$(find "$OPENCC_BUILD" -type f -name opencc_dict -perm -111 -print -quit)"
test -n "$OPENCC_DICT"
mkdir -p "$OUT/opencc"
cp "$TMP/opencc/data/config/t2s.json" "$OUT/opencc/t2s.json"
python3 "$TMP/opencc/data/scripts/extract_tofu_risk.py" \
  "$TMP/opencc/data/dictionary/TSCharacters.txt" "$OPENCC_BUILD/TSCharactersExt.txt"
for name in CJK_Compatibility_Ideographs TSCharacters TSCharactersExt TSPhrases; do
  input="$TMP/opencc/data/dictionary/$name.txt"
  [ "$name" != "TSCharactersExt" ] || input="$OPENCC_BUILD/TSCharactersExt.txt"
  test -s "$input"
  "$OPENCC_DICT" --input "$input" --output "$OUT/opencc/$name.ocd2" --from text --to ocd2
done
cp "$TMP/opencc/LICENSE" "$OUT/opencc/LICENSE" 2>/dev/null || true

for f in t2s.json CJK_Compatibility_Ideographs.ocd2 TSCharacters.ocd2 TSCharactersExt.ocd2 TSPhrases.ocd2; do
  test -s "$OUT/opencc/$f"
done
grep -q 'opencc_config: t2s.json' "$OUT/luna_pinyin_simp.schema.yaml"


echo "Rime support data refreshed; bundled luna_pinyin.dict.yaml and luna_pinyin_simp.schema.yaml were preserved."
