# PowerShell Script Build & Package Windows Standalone App
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "  HE THONG DONG GOI UNG DUNG KIEM TOAN CHE DO CHINH SACH (.EXE)" -ForegroundColor Green
Write-Host "  (Spring Boot 3 + Embedded Minimal JRE + Native Windows Executable)" -ForegroundColor Yellow
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Tim JDK Bin
$jdkBin = $null
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jlink.exe")) {
    $jdkBin = "$env:JAVA_HOME\bin"
} elseif (Get-Command jlink -ErrorAction SilentlyContinue) {
    $jdkBin = Split-Path (Get-Command jlink).Source
} else {
    $jdkPaths = Get-ChildItem "C:\Program Files\Java\jdk-*" -ErrorAction SilentlyContinue
    foreach ($p in $jdkPaths) {
        if (Test-Path "$($p.FullName)\bin\jlink.exe") {
            $jdkBin = "$($p.FullName)\bin"
            break
        }
    }
}

if (-not $jdkBin) {
    Write-Host "[LOI] Khong tim thay JDK (jlink/jpackage). Vui long cai dat JDK 17 hoac 21+." -ForegroundColor Red
    exit 1
}

Write-Host "[BUOC 1/4] Su dung JDK tai: $jdkBin" -ForegroundColor Cyan
& "$jdkBin\java.exe" -version

# 2. Build Maven Jar
Write-Host "`n[BUOC 2/4] Dang bien dich ma nguon va dong goi JAR bang Maven..." -ForegroundColor Cyan
& .\mvnw.cmd clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "[LOI] Qua trinh bien dich Maven that bai." -ForegroundColor Red
    exit 1
}

$jarPath = "target\bqp-validate-excel-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "[LOI] Khong tim thay file $jarPath sau khi build." -ForegroundColor Red
    exit 1
}
Write-Host "-> Da tao file JAR thanh cong: $jarPath" -ForegroundColor Green

# 3. Tao custom runtime JRE voi jlink
Write-Host "`n[BUOC 3/4] Dang tao Custom JRE nhung toi gian bang jlink..." -ForegroundColor Cyan
$jrePath = "target\custom-jre"
if (Test-Path $jrePath) {
    Remove-Item -Recurse -Force $jrePath
}

$modules = "java.base,java.desktop,java.sql,java.naming,java.management,java.security.jgss,java.instrument,jdk.unsupported,java.xml,java.net.http,java.logging,jdk.crypto.ec"
& "$jdkBin\jlink.exe" --no-header-files --no-man-pages --compress=2 --strip-debug --add-modules $modules --output $jrePath

# 4. Dong goi EXE bang jpackage
Write-Host "`n[BUOC 4/4] Dang dong goi native Windows executable bang jpackage..." -ForegroundColor Cyan
$distDir = "dist"
if (Test-Path $distDir) {
    Remove-Item -Recurse -Force $distDir
}
New-Item -ItemType Directory -Path "dist\input" -Force | Out-Null
Copy-Item $jarPath -Destination "dist\input\app.jar"

$jpackArgs = @(
    "--type", "app-image",
    "--name", "KiemToanCheDoBQP",
    "--app-version", "1.0.0",
    "--vendor", "BoQuocPhong",
    "--description", "Kiem toan che do chinh sach ND 178 va ND 177",
    "--input", "dist\input",
    "--main-jar", "app.jar"
)

if (Test-Path $jrePath) {
    Write-Host "-> Da tao Custom JRE thanh cong tai $jrePath" -ForegroundColor Green
    $jpackArgs += @("--runtime-image", $jrePath)
}

$jpackArgs += @(
    "--dest", "dist\portable",
    "--java-options", "-Xms256m -Xmx2048m -Dfile.encoding=UTF-8"
)

& "$jdkBin\jpackage.exe" @jpackArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n===============================================================================" -ForegroundColor Green
    Write-Host "  DONG GOI THANH CONG!" -ForegroundColor Green
    Write-Host "  Thu muc ung dung doc lap: dist\portable\KiemToanCheDoBQP\" -ForegroundColor White
    Write-Host "  File chay Windows:        dist\portable\KiemToanCheDoBQP\KiemToanCheDoBQP.exe" -ForegroundColor Yellow
    Write-Host "  Ung dung co the copy sang may khac chay ngay ma khong can cai dat Java!" -ForegroundColor White
    Write-Host "===============================================================================" -ForegroundColor Green
} else {
    Write-Host "[LOI] jpackage khong hoan tat thanh cong." -ForegroundColor Red
}
