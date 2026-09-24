#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/rime"
mkdir -p "$OUT"
# Do not overwrite the dictionary supplied for Drumstick 1.0.0.
if [ ! -f "$OUT/luna_pinyin.dict.yaml" ]; then
  curl -L https://raw.githubusercontent.com/rime/rime-luna-pinyin/master/luna_pinyin.dict.yaml -o "$OUT/luna_pinyin.dict.yaml"
fi
if [ ! -f "$OUT/luna_pinyin.schema.yaml" ]; then
  curl -L https://raw.githubusercontent.com/rime/rime-luna-pinyin/master/luna_pinyin.schema.yaml -o "$OUT/luna_pinyin.schema.yaml"
fi
echo "luna_pinyin support files updated without replacing Drumstick's bundled dictionary/schema"
