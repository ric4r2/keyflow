#!/usr/bin/env sh
# Build a single runnable jar: build/keyflow.jar  (run with: java -jar build/keyflow.jar)
set -e
cd "$(dirname "$0")"
rm -rf build/classes
mkdir -p build/classes
find src/main/java -name '*.java' > build/sources.txt
javac -encoding UTF-8 --release 11 -d build/classes @build/sources.txt
cp -R src/main/resources/. build/classes/
jar --create --file build/keyflow.jar --main-class keyflow.Main -C build/classes .
echo "Built build/keyflow.jar"
