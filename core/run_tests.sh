#!/bin/sh
# Host tests for the app's plain Kotlin core (no Android needed), with the tree's Kotlin compiler and
# JUnit. Soong runs the same tests as PcViewerCoreTests.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
TOP=${ANDROID_BUILD_TOP:-$(cd "$HERE/../../../.." && pwd)}
LIB=$TOP/external/kotlinc/lib
JUNIT=$TOP/prebuilts/devtools/tools/lib/junit-4.12.jar:$TOP/prebuilts/devtools/tools/lib/hamcrest-core-1.3.jar
OUT=$HERE/../build/core-tests
rm -rf "$OUT" && mkdir -p "$OUT"
# Keep the compiler small: the machine may be busy with an Android build.
JAVA_OPTS="-Xmx1g" "$TOP/external/kotlinc/bin/kotlinc" -nowarn -cp "$JUNIT" -d "$OUT" "$HERE/src" "$HERE/tests"
TESTS=$(cd "$OUT" && find . -name '*Test.class' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
java -Xmx256m -cp "$OUT:$JUNIT:$LIB/kotlin-stdlib.jar" org.junit.runner.JUnitCore $TESTS
