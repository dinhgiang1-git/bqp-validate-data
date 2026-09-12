@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

echo ===============================================================================
echo   HỆ THỐNG ĐÓNG GÓI ỨNG DỤNG KIỂM TOÁN CHẾ ĐỘ CHÍNH SÁCH THÀNH FILE .EXE
echo   (Tự động tạo JRE nhúng tối giản + Đóng gói jpackage độc lập Windows)
echo ===============================================================================
echo.

:: 1. Xác định đường dẫn JDK
set "JDK_BIN="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\jlink.exe" (
        set "JDK_BIN=%JAVA_HOME%\bin"
    )
)

if "!JDK_BIN!"=="" (
    for /f "tokens=*" %%i in ('where jlink 2^>nul') do (
        set "JLINK_EXE=%%i"
        for %%d in ("!JLINK_EXE!\..") do set "JDK_BIN=%%~fd"
    )
)

if "!JDK_BIN!"=="" (
    for /d %%d in ("C:\Program Files\Java\jdk-*") do (
        if exist "%%d\bin\jlink.exe" (
            set "JDK_BIN=%%d\bin"
        )
    )
)

if "!JDK_BIN!"=="" (
    echo [LỖI] Không tìm thấy JDK (jlink/jpackage). Vui lòng cài đặt JDK 17 hoặc 21 trở lên.
    pause
    exit /b 1
)

echo [BƯỚC 1/4] Tìm thấy JDK tại: !JDK_BIN!
"!JDK_BIN!\java.exe" -version
echo.

:: 2. Build Maven Fat JAR
echo [BƯỚC 2/4] Đang biên dịch mã nguồn và đóng gói Spring Boot JAR bằng Maven...
call .\mvnw.cmd clean package -DskipTests
if errorlevel 1 (
    echo [LỖI] Quá trình biên dịch Maven thất bại.
    pause
    exit /b 1
)
echo -> Đã tạo file JAR thành công tại target\bqp-validate-excel-0.0.1-SNAPSHOT.jar
echo.

:: 3. Tạo Custom JRE tối giản bằng jlink
echo [BƯỚC 3/4] Đang tạo JRE nhúng tối giản bằng jlink...
if exist "target\custom-jre" (
    rmdir /s /q "target\custom-jre"
)

"!JDK_BIN!\jlink.exe" --no-header-files --no-man-pages --compress=2 --strip-debug ^
      --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.security.jgss,java.instrument,jdk.unsupported,java.xml,java.net.http,java.logging,jdk.crypto.ec ^
      --output target\custom-jre

if errorlevel 1 (
    echo [CẢNH BÁO] jlink gặp lỗi, sẽ sử dụng toàn bộ JDK runtime để đóng gói...
    set "RUNTIME_ARG="
) else (
    echo -> Đã tạo runtime JRE nhúng thành công tại target\custom-jre
    set "RUNTIME_ARG=--runtime-image target\custom-jre"
)
echo.

:: 4. Đóng gói ứng dụng thành file EXE độc lập bằng jpackage
echo [BƯỚC 4/4] Đang đóng gói ứng dụng bằng jpackage...
if exist "dist" (
    rmdir /s /q "dist"
)
mkdir dist\input
copy target\bqp-validate-excel-0.0.1-SNAPSHOT.jar dist\input\app.jar > nul

"!JDK_BIN!\jpackage.exe" --type app-image ^
         --name "KiemToanCheDoBQP" ^
         --app-version "1.0.0" ^
         --vendor "BoQuocPhong" ^
         --description "Phan mem kiem toan che do chinh sach ND 178 va ND 177" ^
         --input dist\input ^
         --main-jar app.jar ^
         !RUNTIME_ARG! ^
         --dest dist\portable ^
         --java-options "-Xms256m -Xmx2048m -Dfile.encoding=UTF-8"

if errorlevel 1 (
    echo [LỖI] jpackage thất bại.
) else (
    echo.
    echo ===============================================================================
    echo   ĐÓNG GÓI THÀNH CÔNG!
    echo   Thư mục ứng dụng độc lập: dist\portable\KiemToanCheDoBQP\
    echo   File thực thi Windows:    dist\portable\KiemToanCheDoBQP\KiemToanCheDoBQP.exe
    echo   (Bạn có thể copy toàn bộ thư mục KiemToanCheDoBQP sang máy tính khác
    echo    và chạy ngay mà không cần cài đặt Java)
    echo ===============================================================================
)

echo.
pause
