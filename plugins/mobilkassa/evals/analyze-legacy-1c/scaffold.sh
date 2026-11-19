#!/bin/bash
# Copies the old-style 1C configuration fragment into the empty eval workspace.
set -e
here="$(cd "$(dirname "$0")" && pwd)"
cp -R "$here/../fixtures/legacy-1c/." .
