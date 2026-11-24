#!/bin/bash
# Compiles the app (core + Compose UI) on the host with the tree's Kotlin compiler and Compose plugin,
# against the prebuilt androidx jars, the public SDK jar and the coroutines jar of a previous build
# (out/soong/.intermediates). A quick check before a full build; it builds no APK.
#   tools/typecheck.sh [WORK_DIR]
set -e
HERE=$(cd "$(dirname "$0")/.." && pwd)
TOP=${ANDROID_BUILD_TOP:-$(cd "$HERE/../../.." && pwd)}
WORK=${1:-$HERE/build/typecheck}
M=$TOP/prebuilts/sdk/current/androidx/m2repository/androidx
INTER=$TOP/out/soong/.intermediates
rm -rf "$WORK" && mkdir -p "$WORK/libs" "$WORK/gen"

for a in $(cd "$M" && ls -d compose/*/* lifecycle/* activity/* savedstate/* navigationevent/* core/core \
           collection/collection-jvm annotation/annotation-jvm annotation/annotation-experimental); do
  v=$(ls "$M/$a" | sort -V | tail -1)
  for f in "$M/$a/$v"/*; do
    case "$f" in
      *sources*) ;;
      *.aar) unzip -p "$f" classes.jar > "$WORK/libs/$(echo "$a" | tr / _).jar" 2>/dev/null || true ;;
      *.jar) cp "$f" "$WORK/libs/" ;;
    esac
  done
done

# R with the string ids the code uses.
python3 - "$HERE/res/values/strings.xml" "$WORK/gen/R.kt" <<'PY'
import sys, xml.etree.ElementTree as ET
names = [e.get("name") for e in ET.parse(sys.argv[1]).getroot() if e.tag == "string"]
ids = "\n".join(f"        const val {n} = {i + 1}" for i, n in enumerate(names))
open(sys.argv[2], "w").write(f"package org.lineageos.pcviewer\n\nobject R {{\n    object string {{\n{ids}\n    }}\n}}\n")
PY

CP=$(ls "$WORK"/libs/*.jar | tr '\n' ':')
CP=$CP$INTER/external/kotlinx.coroutines/kotlinx_coroutines/linux_glibc_common/combined/kotlinx_coroutines.jar
# The app builds with sdk_version "current": compile against the public SDK, as Soong does.
CP=$CP:$TOP/prebuilts/sdk/current/public/android.jar
JAVA_OPTS="-Xmx1536m" nice -n 19 "$TOP/external/kotlinc/bin/kotlinc" -no-reflect -jvm-target 21 \
  -Xplugin="$TOP/external/kotlinc/lib/compose-compiler-plugin.jar" \
  -cp "$CP" -d "$WORK/out" "$HERE/core/src" "$TOP/packages/apps/PcTextEditor/core/src" "$HERE/src" "$WORK/gen"
echo "typecheck: ok"
