#!/bin/bash
# Copies the old-style TypeScript ERP into the empty eval workspace.
set -e
here="$(cd "$(dirname "$0")" && pwd)"
cp -R "$here/../fixtures/legacy-ts/." .
