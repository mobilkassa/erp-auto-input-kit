#!/bin/bash
# A TypeScript ERP that does not talk to the kassa yet: a new connection.
set -e
here="$(cd "$(dirname "$0")" && pwd)"
cp -R "$here/../fixtures/new-ts/." .
