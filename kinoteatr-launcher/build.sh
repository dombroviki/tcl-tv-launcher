#!/bin/sh
# Builds kinoteatr.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Usage: ./build.sh            build only
#        TV=192.168.1.50:5555 ./build.sh install    build and install on the TV
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
JAVA_HOME="${JAVA_HOME:-$(ls -d "/c/Program Files/Microsoft/jdk-17"* | head -1)}"
PATH="$JAVA_HOME/bin:$PATH"
ADB="${ADB:-adb}"
TV="${TV:-}"

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml build/res.zip \
  -A assets --min-sdk-version 30 --target-sdk-version 30 --version-code 2 --version-name 1.0.1

javac -nowarn -source 11 -target 11 -encoding UTF-8 -classpath "$AJ" -d build/classes \
  $(find src -name '*.java')

"$BT/d8.bat" --release --min-api 30 --lib "$AJ" --output build/dex $(find build/classes -name '*.class')

python -I -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a'); z.write(sys.argv[2],'classes.dex'); z.close()" \
  build/base.apk build/dex/classes.dex

[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias kino -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Kinoteatr" >/dev/null

"$BT/zipalign" -p -f 4 build/base.apk build/aligned.apk
"$BT/apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out kinoteatr.apk build/aligned.apk

ls -l kinoteatr.apk

if [ "${1:-}" = "install" ]; then
  [ -n "$TV" ] || { echo "Укажи телевизор: TV=192.168.1.50:5555 sh build.sh install"; exit 1; }
  "$ADB" connect "$TV" >/dev/null
  "$ADB" -s "$TV" install -r kinoteatr.apk
fi
