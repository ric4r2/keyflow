@echo off
rem Build (if needed) and start Keyflow on Windows.
setlocal
cd /d "%~dp0"
where javac >nul 2>nul || (echo javac not found: install a JDK 11 or newer from https://adoptium.net & exit /b 1)
if not exist build\classes mkdir build\classes
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 --release 11 -d build\classes @build\sources.txt || exit /b 1
xcopy /e /y /q src\main\resources build\classes\ >nul
java -cp build\classes keyflow.Main %*
