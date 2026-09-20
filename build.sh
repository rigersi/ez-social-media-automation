#!/usr/bin/env bash
# Zero-dependency build: plain javac, no Maven/Gradle needed.
set -e
cd "$(dirname "$0")"
mkdir -p target/classes
find src/main/java -name '*.java' > target/sources.txt
javac -encoding UTF-8 -nowarn -d target/classes @target/sources.txt
echo "BUILD OK -> target/classes"
