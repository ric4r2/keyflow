@echo off
rem Build a single runnable jar: build\keyflow.jar
setlocal
cd /d "%~dp0"
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 --release 11 -d build\classes @build\sources.txt || exit /b 1
xcopy /e /y /q src\main\resources build\classes\ >nul
jar --create --file build\keyflow.jar --main-class keyflow.Main -C build\classes .
echo Built build\keyflow.jar
