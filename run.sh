#!/usr/bin/env sh
# Build (if needed) and start Keyflow on Linux / macOS.
set -e
cd "$(dirname "$0")"
command -v javac >/dev/null 2>&1 || { echo "javac not found: install a JDK 11 or newer (https://adoptium.net)"; exit 1; }
mkdir -p build/classes
find src/main/java -name '*.java' > build/sources.txt
javac -encoding UTF-8 --release 11 -d build/classes @build/sources.txt
cp -R src/main/resources/. build/classes/
exec java -cp build/classes keyflow.Main "$@"
