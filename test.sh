#!/usr/bin/env sh
# Compile everything and run the self-tests (engine + curriculum validation).
set -e
cd "$(dirname "$0")"
command -v javac >/dev/null 2>&1 || { echo "javac not found: install a JDK 11 or newer"; exit 1; }
rm -rf build/test-classes
mkdir -p build/test-classes
find src/main/java src/test/java -name '*.java' > build/test-sources.txt
javac -encoding UTF-8 --release 11 -d build/test-classes @build/test-sources.txt
cp -R src/main/resources/. build/test-classes/
java -Djava.awt.headless=true -cp build/test-classes keyflow.TestRunner
