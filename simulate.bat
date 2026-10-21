@echo off
cd /d "%~dp0"
java -cp "sentinel.jar;*" sentinel.SampleLogWriter %*
