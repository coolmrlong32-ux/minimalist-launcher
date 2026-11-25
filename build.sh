#!/bin/bash
set -e
cd /workspace/NiagaraLocal
T=/workspace/tools/android
AJ=$T/android.jar

build_variant() {
    local PKG="$1"
    local OUT_APK="$2"
    rm -rf build && mkdir -p build/gen build/classes build/dex

    echo "[1/5] ($PKG) 正在使用 aapt 编译资源与 AndroidManifest.xml..."
    aapt package -f -m --custom-package com.niagara.local --rename-manifest-package "$PKG" -J build/gen -M AndroidManifest.xml -S res -I "$AJ" -F build/res.apk

    echo "[2/5] ($PKG) 正在使用 JDK 17 编译 Java 源码..."
    javac --release 8 -encoding UTF-8 -nowarn -classpath "$AJ" -d build/classes $(find src build/gen -name "*.java")

    echo "[3/5] ($PKG) 正在使用 D8/R8 转换 DEX 字节码..."
    java -cp "$T/r8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$AJ" --output build/dex $(find build/classes -name '*.class')

    echo "[4/5] ($PKG) 正在组装并执行 4 字节 Zipalign 对齐..."
    cp build/res.apk build/unsigned.apk
    (cd build/dex && jar uf ../unsigned.apk classes.dex)
    zipalign -f -p 4 build/unsigned.apk build/aligned.apk

    echo "[5/5] ($PKG) 正在使用 apksigner 进行 V1/V2/V3 签名校验..."
    [ -f release.keystore ] || keytool -genkeypair -keystore release.keystore -storepass niagaralocal -keypass niagaralocal -alias niagara -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Niagara Local Launcher, OU=Aurora, O=Local, L=SG, ST=SG, C=SG" >/dev/null 2>&1
    apksigner sign --ks release.keystore --ks-pass pass:niagaralocal --key-pass pass:niagaralocal --out "build/$OUT_APK" build/aligned.apk
    apksigner verify --verbose "build/$OUT_APK" | head -n 6
    /home/user/android-sdk/android-14/dexdump -c "build/dex/classes.dex" >/dev/null && echo "DEX checksum & header verified OK"

    cp "build/$OUT_APK" "/workspace/$OUT_APK"
    cp "build/$OUT_APK" "/home/user/Desktop/$OUT_APK"
}

sed -i 's/package="com.niagara.loca[l2]"/package="com.niagara.local"/' AndroidManifest.xml
sed -i 's/android:versionCode="[0-9]*"/android:versionCode="146"/' AndroidManifest.xml
sed -i 's/android:versionName="[^"]*"/android:versionName="1.4.6"/' AndroidManifest.xml
rm -f /workspace/NiagaraLocal-v1.4.3*.apk /home/user/Desktop/NiagaraLocal-v1.4.3*.apk
rm -f /workspace/NiagaraLocal-v1.4.4*.apk /home/user/Desktop/NiagaraLocal-v1.4.4*.apk
rm -f /workspace/NiagaraLocal-v1.4.5*.apk /home/user/Desktop/NiagaraLocal-v1.4.5*.apk
build_variant "com.niagara.loca2" "NiagaraLocal-v1.4.6-direct.apk"
build_variant "com.niagara.local" "NiagaraLocal-v1.4.6.apk"
rm -rf /workspace/NiagaraLocal/build

ls -lh /workspace/NiagaraLocal-v*.apk /home/user/Desktop/NiagaraLocal-v*.apk
