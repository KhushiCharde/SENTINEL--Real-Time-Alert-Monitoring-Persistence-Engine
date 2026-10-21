#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
sh build.sh
javac --release 17 -cp 'build:*' -d build CoreChecks.java
java -cp 'build:*' sentinel.CoreChecks
