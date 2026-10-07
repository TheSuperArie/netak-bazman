#!/bin/bash
# Builds NetakBazman.apk without Gradle: aapt2 -> javac -> dx -> align + v2 sign
set -e
cd "$(dirname "$0")"
T=/home/claude/tools
AAPT2=$T/apktool/brut.apktool/apktool-lib/src/main/resources/prebuilt/linux/aapt2
ANDROID_JAR=$T/platforms/android-30/android.jar
DX=$T/dx.jar

rm -rf build && mkdir -p build/gen build/classes

$AAPT2 compile --dir res -o build/res.zip
$AAPT2 link -o build/base.apk -I $ANDROID_JAR --manifest AndroidManifest.xml \
    --java build/gen --min-sdk-version 30 --target-sdk-version 30 \
    --version-code 2 --version-name 1.1 build/res.zip

javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 -bootclasspath $ANDROID_JAR \
    -d build/classes $(find build/gen src -name "*.java")

java -cp $DX com.android.dx.command.Main --dex --min-sdk-version=26 \
    --output=build/classes.dex build/classes

if [ ! -f keys/key.pem ]; then
  mkdir -p keys
  python3 tools/make_key.py keys/key.pem keys/cert.der
fi
python3 tools/apk_pack_sign.py build/base.apk build/classes.dex keys/key.pem keys/cert.der build/NetakBazman.apk
ls -la build/NetakBazman.apk
