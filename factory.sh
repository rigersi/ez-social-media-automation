#!/usr/bin/env bash
cd "$(dirname "$0")"
[ -d target/classes ] || ./build.sh
java -cp target/classes ez.factory.Main "$@"
