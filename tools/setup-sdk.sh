#!/usr/bin/env bash
# 一次装好构建链：JDK + Android cmdline-tools + platform + build-tools
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="$ROOT/.tools/android-sdk"
DL="$ROOT/.tools/dl"
mkdir -p "$SDK" "$DL"

command -v javac >/dev/null || { echo "缺 javac：apt-get install -y openjdk-17-jdk-headless"; exit 1; }

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[1] cmdline-tools"
  [ -f "$DL/cmdline-tools.zip" ] || python3 - "$DL/cmdline-tools.zip" <<'PY'
import sys, urllib.request
urllib.request.urlretrieve(
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip",
    sys.argv[1])
PY
  rm -rf "$SDK/cmdline-tools"
  mkdir -p "$SDK/cmdline-tools"
  python3 -c "import zipfile,sys;zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" \
    "$DL/cmdline-tools.zip" "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"

echo "[2] licenses"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true

echo "[3] platform + build-tools"
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
  "platforms;android-35" "build-tools;35.0.0"

echo "done -> $SDK"
