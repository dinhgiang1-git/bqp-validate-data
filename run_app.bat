@echo off
chcp 65001 > nul
echo ===============================================================================
echo   KHỞI CHẠY HỆ THỐNG KIỂM TOÁN CHẾ ĐỘ CHÍNH SÁCH BỘ QUỐC PHÒNG
echo   (Tự động mở trình duyệt đến http://localhost:8080)
echo ===============================================================================
echo.
call .\mvnw.cmd spring-boot:run
pause
