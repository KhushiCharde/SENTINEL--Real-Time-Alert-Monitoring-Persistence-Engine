@echo off
cd /d "%~dp0"
java -cp "sentinel.jar;lib/*" sentinel.SampleLogWriter %*
