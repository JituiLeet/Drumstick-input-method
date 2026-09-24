#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TRIME_DIR="${TRIME_DIR:-$ROOT/.rime-build/trime}"
OUT="$ROOT/app/src/main/jniLibs"
TRIME_REF="${TRIME_REF:-develop}"
DRUM_CPP="$ROOT/app/src/main/cpp/drumstick_rime.cpp"

mkdir -p "$ROOT/.rime-build"
if [ ! -d "$TRIME_DIR/.git" ]; then
  git clone --depth 1 --recurse-submodules --shallow-submodules --branch "$TRIME_REF" https://github.com/osfans/trime.git "$TRIME_DIR"
else
  git -C "$TRIME_DIR" fetch --depth 1 origin "$TRIME_REF"
  git -C "$TRIME_DIR" checkout -q FETCH_HEAD
  git -C "$TRIME_DIR" submodule update --init --recursive --depth 1
fi

rm -rf "$OUT" "$ROOT/.rime-build/native-out"
mkdir -p "$OUT/armeabi-v7a" "$OUT/arm64-v8a" "$ROOT/.rime-build/native-out"

TRIME_CMAKE_FILE="$TRIME_DIR/app/src/main/jni/CMakeLists.txt"

# Rime is BSD-3-Clause. We use Trime only as an Android dependency build harness.
# Disable Trime's GPL JNI target and optional plugins; the final shared library is
# our own JNI bridge plus the BSD librime static target.
python3 - "$TRIME_DIR/app/src/main/jni/cmake/Rime.cmake" <<'PY'
from pathlib import Path
import re, sys
p=Path(sys.argv[1]); s=p.read_text()
s=re.sub(r"set\(RIME_PLUGINS\s+[^)]*\)", "set(RIME_PLUGINS)", s, count=1)
s=re.sub(r"(?ms)^\s*target_compile_options\(\s*rime-lua-objs\s+PRIVATE\s+\"-ffile-prefix-map=\$\{CMAKE_CURRENT_SOURCE_DIR\}=\.\"\s*\)\s*", "if(TARGET rime-lua-objs)\n  target_compile_options(rime-lua-objs PRIVATE \"-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.\")\nendif()\n", s, count=1)
s=re.sub(r"(?ms)^\s*target_compile_options\(\s*rime-octagram-objs\s+PRIVATE\s+\"-ffile-prefix-map=\$\{CMAKE_CURRENT_SOURCE_DIR\}=\.\"\s*\)\s*", "if(TARGET rime-octagram-objs)\n  target_compile_options(rime-octagram-objs PRIVATE \"-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.\")\nendif()\n", s, count=1)
p.write_text(s)
PY

python3 - "$TRIME_CMAKE_FILE" "$DRUM_CPP" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
cpp = Path(sys.argv[2])
s = p.read_text()

# Remove previous Drumstick injection if the build directory is reused.
marker = "# === DRUMSTICK JNI TARGET BEGIN ==="
if marker in s:
    s = s.split(marker)[0].rstrip()

# Disable Trime JNI target.
s = s.replace(
    "add_subdirectory(librime_jni)",
    "# Drumstick uses its own JNI bridge."
)

# Ensure PIC for static librime.
if "set(CMAKE_POSITION_INDEPENDENT_CODE ON)" not in s:
    s = s.replace(
        "cmake_minimum_required(VERSION 3.18.0)",
        "cmake_minimum_required(VERSION 3.18.0)\nset(CMAKE_POSITION_INDEPENDENT_CODE ON)"
    )

# Create target first, then link it. The old script could leave a
# target_link_libraries() without a matching add_library().
s += f"""

{marker}
add_library(librime SHARED
    "{cpp.as_posix()}"
)

target_compile_features(librime PRIVATE cxx_std_17)

target_include_directories(librime PRIVATE
    "${{CMAKE_CURRENT_SOURCE_DIR}}/librime/src"
)

target_link_libraries(librime PRIVATE
    "-Wl,--whole-archive"
    rime-static
    "-Wl,--no-whole-archive"
    log
)

set_target_properties(librime PROPERTIES
    OUTPUT_NAME "librime"
    LIBRARY_OUTPUT_DIRECTORY "${{CMAKE_BINARY_DIR}}/drumstick-out"
)
"""

p.write_text(s)
PY

CMAKE_BIN="${CMAKE_BIN:-$ANDROID_SDK_ROOT/cmake/3.22.1/bin/cmake}"
[ -x "$CMAKE_BIN" ] || CMAKE_BIN="$(command -v cmake)"

echo "Using CMake: $CMAKE_BIN"

echo "Trime build harness: $TRIME_DIR"

build_one() {
  local abi="$1" api="$2"
  local b="$ROOT/.rime-build/cmake-$abi"
  rm -rf "$b"
  RIME_PLUGINS="" "$CMAKE_BIN" -S "$TRIME_DIR/app/src/main/jni" -B "$b" \
    -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-$api" \
    -DANDROID_STL=c++_static \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_TESTING=OFF \
    -DBUILD_TEST=OFF \
    -DBUILD_SAMPLE=OFF \
    -DENABLE_LOGGING=ON \
    -DENABLE_TIMESTAMP=OFF
  "$CMAKE_BIN" --build "$b" --target librime --parallel
  local so
  so="$(find "$b/drumstick-out" -type f -name 'liblibrime.so' -print -quit)"
  test -n "$so"
  cp "$so" "$OUT/$abi/liblibrime.so"
  echo "== $abi: linked symbols =="
  "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -d "$OUT/$abi/liblibrime.so" | grep -E 'NEEDED|SONAME' || true
  echo "Checking rime_get_api..."
  "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -Ws "$OUT/$abi/liblibrime.so" | grep -q "rime_get_api"     && echo "rime_get_api found"     || echo "rime_get_api is statically linked"

  ! "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -d "$OUT/$abi/liblibrime.so" | grep -Eq 'Shared library: \[librime\.so\]'
}

build_one armeabi-v7a 19
build_one arm64-v8a 21

find "$OUT" -type f -name 'liblibrime.so' -print
