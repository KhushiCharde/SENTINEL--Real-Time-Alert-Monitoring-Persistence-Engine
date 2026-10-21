#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p build
javac --release 17 -d build AlertEngine.java Dashboard.java FileTailer.java HistoryStore.java LogEvent.java Main.java SampleLogWriter.java Severity.java WebServer.java
jar --create --file sentinel.jar --main-class sentinel.Main -C build . -C . index.html
