#!/bin/bash
# An ERP that only takes the X report: nothing to move.
set -e
here="$(cd "$(dirname "$0")" && pwd)"
mkdir -p src
cp "$here/../fixtures/legacy-ts/package.json" .
cp "$here/../fixtures/legacy-ts/src/report.ts" src/
