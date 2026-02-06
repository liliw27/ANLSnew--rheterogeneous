#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p build/classes

echo "[build] Compiling Java sources into build/classes ..."
find src -name "*.java" > build/sources.txt
javac -encoding UTF-8 -d build/classes @build/sources.txt

echo "[build] Done."


