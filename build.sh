#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ANDROID_SDK="${ANDROID_HOME:-/data/android-sdk}"
BT="$ANDROID_SDK/build-tools/36.0.0"
if [ ! -d "$BT" ]; then
    BT=$(ls -d "$ANDROID_SDK"/build-tools/* 2>/dev/null | sort -V | tail -n 1)
fi
AJ="$ANDROID_SDK/platforms/android-34/android.jar"
if [ ! -f "$AJ" ]; then
    AJ=$(ls "$ANDROID_SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -n 1)
fi

VERSION_NAME=$(grep -oP 'android:versionName="\K[^"]+' AndroidManifest.xml | head -n 1)
VERSION_NAME="${VERSION_NAME:-2.7.0}"

build_variant() {
    local PKG="$1"
    local OUT_APK="$2"
    rm -rf build && mkdir -p build/gen build/classes build/dex dist

    echo "[1/5] ($PKG) 正在使用 aapt 编译资源与 AndroidManifest.xml..."
    "$BT/aapt" package -f -m --custom-package com.niagara.local --rename-manifest-package "$PKG" -J build/gen -M AndroidManifest.xml -S res -I "$AJ" -F build/res.apk

    echo "[2/5] ($PKG) 正在使用 javac --release 8 编译 Java 源码..."
    javac --release 8 -encoding UTF-8 -nowarn -classpath "$AJ" -d build/classes $(find src build/gen -name "*.java")

    echo "[3/5] ($PKG) 正在使用 D8 转换 DEX 字节码..."
    java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$AJ" --output build/dex $(find build/classes -name '*.class')

    echo "[4/5] ($PKG) 正在组装并执行 4 字节 Zipalign 对齐..."
    cp build/res.apk build/unsigned.apk
    (cd build/dex && jar uf ../unsigned.apk classes.dex)
    "$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

    echo "[5/5] ($PKG) 正在使用 apksigner 进行 V1/V2/V3 签名校验..."
    [ -f release.keystore ] || keytool -genkeypair -keystore release.keystore -storepass niagaralocal -keypass niagaralocal -alias niagara -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Minimalist Launcher, OU=Aurora, O=Local, L=SG, ST=SG, C=SG" >/dev/null 2>&1
    "$BT/apksigner" sign --ks release.keystore --ks-pass pass:niagaralocal --key-pass pass:niagaralocal --out "build/$OUT_APK" build/aligned.apk
    "$BT/apksigner" verify --verbose "build/$OUT_APK" | head -n 6
    if [ -x "$BT/dexdump" ]; then
        "$BT/dexdump" -c "build/dex/classes.dex" >/dev/null && echo "DEX checksum & header verified OK"
    fi

    cp "build/$OUT_APK" "dist/$OUT_APK"
}

build_variant "com.niagara.loca2" "极简桌面-v${VERSION_NAME}-direct.apk"
build_variant "com.niagara.local" "极简桌面-v${VERSION_NAME}.apk"
rm -rf build

ls -lh dist/*.apk
