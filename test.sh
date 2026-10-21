#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
sh build.sh
javac --release 17 -cp 'build:lib/*' -d build src/test/java/sentinel/*.java
java -cp 'build:lib/*' sentinel.CoreChecks
