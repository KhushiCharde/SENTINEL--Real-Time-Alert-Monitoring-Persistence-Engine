@echo off
cd /d "%~dp0"
if not exist build mkdir build
javac --release 17 -d build AlertEngine.java Dashboard.java FileTailer.java HistoryStore.java LogEvent.java Main.java SampleLogWriter.java Severity.java WebServer.java
if errorlevel 1 exit /b 1
jar --create --file sentinel.jar --main-class sentinel.Main -C build . -C . index.html
