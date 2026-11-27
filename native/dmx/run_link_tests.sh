#!/usr/bin/env bash
# Builds and runs the four link tests directly with g++ (without CMake), for
# quick runs in WSL2. Can be invoked from any working directory.
#
#   ./run_link_tests.sh                 run all four tests
#   ./run_link_tests.sh link_server     run one test (frame_codec | dmx_frame_proto | link_session | link_server)
#
# With Protobuf (for dmx_frame_proto):
#   EXTRA_SRC="build/dmx_link.pb.cc" \
#   EXTRA_FLAGS="-Ibuild $(pkg-config --cflags --libs protobuf)" ./run_link_tests.sh
set -u
cd "$(dirname "$0")"
VEC="$(cd ../../schema/testdata && pwd)/dmx_link_vectors.txt"
OUT="${TMPDIR:-/tmp}/archx_link_tests"
mkdir -p "$OUT"
TESTS=("$@")
[ ${#TESTS[@]} -eq 0 ] && TESTS=(frame_codec dmx_frame_proto link_session link_server)
status=0
for t in "${TESTS[@]}"; do
  echo "=== test_$t"
  # shellcheck disable=SC2086
  if ! g++ -std=c++20 -Wall -Wextra -pedantic -g -O1 -pthread -Iinclude \
        -DARCHX_VECTORS_PATH="\"$VEC\"" \
        "tests/link/test_$t.cpp" dmx/link/*.cpp ${EXTRA_SRC:-} ${EXTRA_FLAGS:-} \
        -o "$OUT/test_$t"; then
    echo "  BUILD-FEHLER"; status=1; continue
  fi
  timeout 120 "$OUT/test_$t" || status=1
done
exit $status
