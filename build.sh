#!/usr/bin/env bash
# 免 Gradle / 免 aapt2 构建（aapt2 只有 x86_64 版，本机 arm64 跑不了）
# 流程：javac -> d8(java) -> 自制 AXML 清单 -> zip -> apksigner(java)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$ROOT/.tools/android-sdk}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
PLATFORM_JAR="$(ls -d "$SDK"/platforms/* 2>/dev/null | sort -V | tail -1)/android.jar"
OUT="$ROOT/build"
APK_NAME="HideRecentsCard-1.0.apk"

[ -n "$BT" ] || { echo "找不到 build-tools，先跑 .tools/setup-sdk.sh"; exit 1; }
[ -f "$PLATFORM_JAR" ] || { echo "找不到 android.jar"; exit 1; }
command -v javac >/dev/null || { echo "PATH 里没有 javac"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/stub" "$OUT/classes" "$OUT/dex"

echo "[1/6] javac xposed stub"
find "$ROOT/stub" -name '*.java' > "$OUT/stub-src.txt"
javac --release 11 -nowarn -encoding UTF-8 -cp "$PLATFORM_JAR" -d "$OUT/stub" @"$OUT/stub-src.txt"

echo "[2/6] javac module"
find "$ROOT/app/src/main/java" -name '*.java' > "$OUT/app-src.txt"
javac --release 11 -nowarn -encoding UTF-8 \
  -cp "$PLATFORM_JAR:$OUT/stub" \
  -d "$OUT/classes" @"$OUT/app-src.txt"

echo "[3/6] d8 dex"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --release --min-api 26 --lib "$PLATFORM_JAR" \
  --output "$OUT/dex" @"$OUT/classes.txt"

echo "[4/6] AndroidManifest -> AXML"
python3 "$ROOT/tools/gen_axml.py" \
  "$ROOT/app/src/main/AndroidManifest.xml" "$OUT/AndroidManifest.xml"

echo "[5/6] 打包"
python3 - "$OUT" "$ROOT/app/src/main/assets/xposed_init" <<'PY'
import os, sys, zipfile
out, xinit = sys.argv[1], sys.argv[2]
apk = os.path.join(out, "unsigned.apk")
with zipfile.ZipFile(apk, "w", zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(out, "AndroidManifest.xml"), "AndroidManifest.xml")
    z.write(os.path.join(out, "dex", "classes.dex"), "classes.dex")
    if os.path.exists(xinit):
        z.write(xinit, "assets/xposed_init")
print("  +", apk, os.path.getsize(apk), "bytes")
PY

echo "[6/6] 签名"
KS="$ROOT/.tools/hidecard.jks"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias hidecard -storepass android -keypass android \
    -dname "CN=YG, OU=local, O=local, L=local, ST=local, C=CN" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
java -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android --ks-key-alias hidecard \
  --out "$OUT/$APK_NAME" "$OUT/unsigned.apk"
java -jar "$BT/lib/apksigner.jar" verify --print-certs "$OUT/$APK_NAME" | head -3

echo
echo "OK -> $OUT/$APK_NAME"
ls -lh "$OUT/$APK_NAME"
