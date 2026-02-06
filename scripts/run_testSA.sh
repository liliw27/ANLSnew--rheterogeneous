#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Build if needed (or always, to keep it simple and reproducible for students).
"$SCRIPT_DIR/build.sh"

echo "[run] Running src/test/testSA ..."
java -cp build/classes test.testSA "$@"


