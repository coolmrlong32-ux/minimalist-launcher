# PowerShell build script for 极简桌面 (Minimalist Launcher)
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$SdkRoot = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
$BuildTools = Join-Path $SdkRoot 'build-tools\34.0.0'
$JdkBin = 'D:\android-cli-workspace\openjdk\jdk-17.0.13+11\bin'

if (Test-Path $JdkBin) { $env:Path = "$JdkBin;$env:Path" }

Remove-Item -Recurse -Force "$ProjectRoot\build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$ProjectRoot\build\gen","$ProjectRoot\build\classes","$ProjectRoot\build\dex" | Out-Null

[xml]$manifest = Get-Content "$ProjectRoot\AndroidManifest.xml"
$manifest.manifest.package = 'com.niagara.local'
$manifest.manifest.SetAttribute('android:versionCode','290')
$manifest.manifest.SetAttribute('android:versionName','2.9.0')
$manifest.Save("$ProjectRoot\AndroidManifest.xml")

function Build-Variant($Pkg, $OutApk) {
    Write-Host "[1/5] $Pkg aapt"
    $aapt = Join-Path $BuildTools 'aapt.exe'
    & $aapt package -f -m --custom-package $Pkg --rename-manifest-package $Pkg -J "$ProjectRoot\build\gen" -M "$ProjectRoot\AndroidManifest.xml" -S "$ProjectRoot\res" -I "$SdkRoot\platforms\android-34\android.jar" -F "$ProjectRoot\build\res.apk"
    if ($LASTEXITCODE) { throw "aapt failed" }

    Write-Host "[2/5] $Pkg javac"
    $javaFiles = Get-ChildItem -Recurse -Path "$ProjectRoot\src","$ProjectRoot\build\gen" -Filter *.java | Select-Object -ExpandProperty FullName
    & javac --release 8 -encoding UTF-8 -nowarn -classpath "$SdkRoot\platforms\android-34\android.jar" -d "$ProjectRoot\build\classes" $javaFiles
    if ($LASTEXITCODE) { throw "javac failed" }

    Write-Host "[3/5] $Pkg D8"
    $r8jar = Join-Path $BuildTools 'lib\d8.jar'
    & java -cp $r8jar com.android.tools.r8.D8 --release --min-api 26 --lib "$SdkRoot\platforms\android-34\android.jar" --output "$ProjectRoot\build\dex" $(Get-ChildItem "$ProjectRoot\build\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
    if ($LASTEXITCODE) { throw "D8 failed" }

    Write-Host "[4/5] $Pkg zipalign"
    Copy-Item "$ProjectRoot\build\res.apk" "$ProjectRoot\build\unsigned.apk" -Force
    Set-Location "$ProjectRoot\build\dex"
    jar uf ..\unsigned.apk classes.dex
    Set-Location $ProjectRoot
    $zipalign = Join-Path $BuildTools 'zipalign.exe'
    & $zipalign -f -p 4 "$ProjectRoot\build\unsigned.apk" "$ProjectRoot\build\aligned.apk"
    if ($LASTEXITCODE) { throw "zipalign failed" }

    Write-Host "[5/5] $Pkg apksigner"
    $keystore = "$ProjectRoot\release.keystore"
    if (-not (Test-Path $keystore)) {
        & keytool -genkeypair -keystore $keystore -storepass niagaralocal -keypass niagaralocal -alias niagara -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Minimalist Launcher, OU=Aurora, O=Local, L=SG, ST=SG, C=SG" > $null 2>&1
    }
    $apksigner = Join-Path $BuildTools 'apksigner.bat'
    & $apksigner sign --ks $keystore --ks-pass pass:niagaralocal --key-pass pass:niagaralocal --out "$ProjectRoot\build\$OutApk" "$ProjectRoot\build\aligned.apk"
    if ($LASTEXITCODE) { throw "apksigner sign failed" }

    $outDir = 'D:\NiagaraProject'
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    Copy-Item "$ProjectRoot\build\$OutApk" "$outDir\$OutApk" -Force
}

Build-Variant 'com.niagara.loca2' '极简桌面-v2.9.0-direct.apk'
Build-Variant 'com.niagara.local' '极简桌面-v2.9.0.apk'
Copy-Item "$ProjectRoot\build\极简桌面-v2.9.0.apk" "D:\NiagaraProject\极简桌面.apk" -Force
Remove-Item -Recurse -Force "$ProjectRoot\build" -ErrorAction SilentlyContinue
Write-Host "Build finished."
