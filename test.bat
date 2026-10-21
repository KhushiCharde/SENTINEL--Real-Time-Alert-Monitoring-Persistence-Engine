@echo off
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
javac --release 17 -cp "build;lib/*" -d build src\test\java\sentinel\*.java
if errorlevel 1 exit /b 1
java -cp "build;lib/*" sentinel.CoreChecks
