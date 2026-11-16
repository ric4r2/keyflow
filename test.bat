@echo off
rem Compile everything and run the self-tests on Windows.
setlocal
cd /d "%~dp0"
if exist build\test-classes rmdir /s /q build\test-classes
mkdir build\test-classes
dir /s /b src\main\java\*.java src\test\java\*.java > build\test-sources.txt
javac -encoding UTF-8 --release 11 -d build\test-classes @build\test-sources.txt || exit /b 1
xcopy /e /y /q src\main\resources build\test-classes\ >nul
java -Djava.awt.headless=true -cp build\test-classes keyflow.TestRunner
