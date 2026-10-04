#!/bin/sh
# One-shot rebuild of the MD3-reworked 聪来提权 APK.
# javac -> d8 -> baksmali -> inject -> apktool b -> zipalign -> sign
AJ=/home/rmg/android-sdk/platforms/android-36/android.jar
D8=/home/rmg/android-sdk/build-tools/34.0.0/d8
AS=/home/rmg/android-sdk/build-tools/34.0.0/apksigner
BAK=/root/rmg2/baksmali.jar
D=/home/rmg/work/dec

cd /home/rmg/ui || exit 1

to() { command -v timeout >/dev/null 2>&1 && timeout "$1" "${@:2}" || "${@:2}"; }

echo "== [1] compile stubs =="
rm -rf cp; mkdir -p cp
javac -cp $AJ -d cp \
  stub/androidx/appcompat/app/AppCompatActivity.java \
  stub/com/google/android/material/navigation/NavigationBarView.java \
  stub/com/google/android/material/bottomnavigation/BottomNavigationView.java \
  src/df/root/IReporter.java src/df/root/ExploitRunner.java src/df/root/BootReceiver.java || exit 1

echo "== [2] compile MainActivity =="
rm -rf out; mkdir -p out
javac -encoding UTF-8 -cp $AJ:cp -d out src/df/root/MainActivity.java || exit 1

echo "== [3] d8 =="
rm -rf dexo; mkdir -p dexo
$D8 --release --lib $AJ --min-api 33 --output dexo $(find out -name 'MainActivity*.class') || exit 1

echo "== [4] baksmali =="
rm -rf dexs
java -jar $BAK disassemble dexo/classes.dex -o dexs || exit 1

echo "== [5] inject into dec =="
rm -f "$D/smali_classes3/df/root/MainActivity.smali"
rm -f "$D/smali_classes3/df/root/"'MainActivity$$ExternalSyntheticLambda'*.smali
rm -f "$D/smali_classes3/df/root/databinding/ActivityMainBinding.smali" 2>/dev/null
rmdir "$D/smali_classes3/df/root/databinding" 2>/dev/null
cp dexs/df/root/MainActivity*.smali "$D/smali_classes3/df/root/" || exit 1

echo "== [5b] inject resources =="
mkdir -p "$D/res/drawable" "$D/res/color" "$D/res/menu" "$D/res/layout" || exit 1
cp res/drawable/*.xml  "$D/res/drawable/" || exit 1
cp res/color/*.xml     "$D/res/color/"    || exit 1
cp res/menu/*.xml      "$D/res/menu/"     || exit 1
cp res/layout/*.xml    "$D/res/layout/"   || exit 1

echo "== [6] apktool b =="
cd /home/rmg/work || exit 1
java -jar apktool-arm64.jar b dec -o /home/rmg/ui/ui-build.apk --use-aapt2 > /tmp/apktool_b.log 2>&1
rc=$?
tail -6 /tmp/apktool_b.log
[ $rc -ne 0 ] && { echo "APKTOOL FAIL"; exit 1; }
cd /home/rmg/ui || exit 1

echo "== [7] zipalign =="
python3 zipalign.py ui-build.apk ui-aligned.apk 4 || exit 1

echo "== [8] sign =="
rm -f conglai-md3-ui-signed.apk
$AS sign --ks /home/rmg/build-libexp/ks.keystore --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias k \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out conglai-md3-ui-signed.apk ui-aligned.apk || exit 1

echo "== BUILD_OK =="
ls -l conglai-md3-ui-signed.apk