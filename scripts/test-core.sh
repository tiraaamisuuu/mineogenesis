#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p core/build/manual-tests
find core/src/main/java core/src/test/java -name '*.java' > core/build/manual-tests/sources.txt
javac --release 21 -d core/build/manual-tests @core/build/manual-tests/sources.txt
java -cp core/build/manual-tests io.github.tiraaamisuuu.mineogenesis.core.FoundationTests
java -cp core/build/manual-tests io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionTests
java -cp core/build/manual-tests io.github.tiraaamisuuu.mineogenesis.core.acquisition.AcquisitionTests
