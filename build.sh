#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p build
javac --release 17 -d build src/main/java/sentinel/*.java
jar --create --file sentinel.jar --main-class sentinel.Main -C build .
