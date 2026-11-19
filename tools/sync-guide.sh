#!/bin/sh
# Copies the guide into the plugin's auto-input skill; tools/check-docs.sh fails while the copies differ.
cd "$(dirname "$0")/.." || exit 2
REF=plugins/mobilkassa/skills/auto-input/references
mkdir -p "$REF"
cp guide/auto-input.md guide/error-codes.json "$REF/"
echo "synced guide -> $REF"
