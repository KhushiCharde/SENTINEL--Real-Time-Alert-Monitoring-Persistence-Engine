@echo off
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
javac --release 17 -cp "build;*" -d build CoreChecks.java
if errorlevel 1 exit /b 1
java -cp "build;*" sentinel.CoreChecks
