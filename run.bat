@echo off
rem Build (if needed) and start Keyflow on Windows.
rem Double-click this file, or run it from PowerShell with:  .\run.bat
setlocal
cd /d "%~dp0"
where javac >nul 2>nul || goto nojdk
if not exist build\classes mkdir build\classes
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 --release 11 -d build\classes @build\sources.txt || goto failed
xcopy /e /y /q src\main\resources build\classes\ >nul
start "" javaw -cp build\classes keyflow.Main %*
exit /b 0

:nojdk
echo javac was not found. Install a JDK 11 or newer from https://adoptium.net
echo (choose the option that adds Java to PATH), then run this file again.
pause
exit /b 1

:failed
echo Build failed. See the messages above.
pause
exit /b 1
