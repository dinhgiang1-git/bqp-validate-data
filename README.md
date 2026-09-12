# HỆ THỐNG KIỂM TOÁN TÍNH CHẾ ĐỘ CHÍNH SÁCH BỘ QUỐC PHÒNG
*(Nghị định số 178/2024/NĐ-CP & Nghị định số 177/2024/NĐ-CP)*

---

## 1. Giới Thiệu
Hệ thống là một ứng dụng Java Spring Boot độc lập chạy cục bộ (Local Web UI tại `http://localhost:8080`), thực hiện nhiệm vụ:
1. **Bóc tách dữ liệu PDF bảng biểu đa trang**: Sử dụng công nghệ Tabula Lattice Mode (`SpreadsheetExtractionAlgorithm`) kết hợp PDFBox.
2. **Tính toán độc lập với độ chính xác cao**: Sử dụng `BigDecimal` cài đặt chuẩn hóa toàn bộ công thức theo:
   - **Phụ lục I.1**: Nghỉ hưu trước tuổi (NĐ 178/2024).
   - **Phụ lục I.2**: Thôi việc / Phục viên (NĐ 178/2024).
   - **Phụ lục I.3**: Không đủ điều kiện tái cử, tái bổ nhiệm (NĐ 177/2024).
3. **Đối soát & Báo cáo sai lệch**:
   - Tự động so khớp dữ liệu gốc và dữ liệu tính toán.
   - Phân loại 6 nhóm tiêu chí lỗi theo đúng mẫu biểu.
   - Xuất file **Excel danh sách gốc** có bổ sung cột *"Ghi chú rà soát"* ngoài cùng và highlight dòng sai.
   - Xuất file **Excel đối soát `Sua.xlsx`** (18 cột chuẩn của Bộ).
   - Tự động chuyển đổi sang **File PDF báo cáo `Sua.pdf`** in ấn chuẩn Unicode tiếng Việt.
   - Hỗ trợ tải trọn gói toàn bộ kết quả trong 1 file `.ZIP`.
4. **Hỗ trợ nộp đa phụ lục linh hoạt**: Cho phép người dùng nộp **1 trong 3**, **2 trong 3** hoặc **cả 3** phụ lục trong một lần rà soát.
5. **Tự động mở trình duyệt**: Tự động mở Google Chrome/Microsoft Edge khi ứng dụng khởi chạy.

---

## 2. Quy Tắc Nghiệp Vụ Cài Đặt

### 2.1. Bảng trần tuổi quân hàm (`tranQuanHam`):
- **Đại tá** (`4//`): 58 tuổi
- **Thượng tá** (`3//`): 56 tuổi
- **Trung tá** (`2//`): 54 tuổi
- **Thiếu tá** (`1//`): 52 tuổi
- **Cấp úy** (`Đại úy`, `Thượng úy`, `Trung úy`, `Thiếu úy`, `4/`, `3/`, `2/`, `1/`): 50 tuổi
- **Quy đổi tháng lẻ sang năm**:
  - `0 tháng` $\rightarrow$ `+0.0 năm`
  - `Từ 1 đến 6 tháng lẻ` $\rightarrow$ `+0.5 năm`
  - `Từ 7 đến 12 tháng lẻ` $\rightarrow$ `+1.0 năm`

### 2.2. Chi tiết công thức tính:
- **Phụ lục I.1**:
  - Cột 10 = Tuổi trần quân hàm - Thời điểm nghỉ hưu.
  - Cột 11 = Cột 10 quy đổi làm tròn tháng lẻ.
  - Cột 12 = Thời điểm nghỉ - Ngày nhập ngũ (quy đổi ra năm).
  - Phân nhóm: `DT1` (Cột 10 $\le$ 60 tháng) / `DT2` (Cột 10 > 60 tháng).
  - Mốc sáp nhập: `TH1` ($\le$ 12 tháng) / `TH2` (> 12 tháng).
  - `C1`: DT1, TH1 = Lương $\times$ 1.0 $\times$ Cột 10; DT1, TH2 = Lương $\times$ 0.5 $\times$ Cột 10; DT2, TH1 = Lương $\times$ 0.9 $\times$ 60; DT2, TH2 = Lương $\times$ 0.45 $\times$ 60.
  - `C2`: DT1 = Cột 11 $\times$ 5 $\times$ Lương; DT2 = Cột 11 $\times$ 4 $\times$ Lương.
  - `C3`: Nghỉ trước 01/07/2025 (BHXH > 20): $5 \times$ Lương $+ (\text{BHXH} - 20) \times 0.5 \times$ Lương; Nghỉ từ 01/07/2025 (BHXH > 15): $4 \times$ Lương $+ (\text{BHXH} - 15) \times 0.5 \times$ Lương.
- **Phụ lục I.2**:
  - Cột 10 = $\min(\text{Tháng BHXH}, 60)$; Cột 11 = Làm tròn Cột 10.
  - TH1: Trợ cấp tháng = Cột 10 $\times 0.8 \times$ Lương; Trợ cấp năm = Cột 11 $\times 1.5 \times$ Lương; Trợ cấp việc làm = $3 \times$ Lương.
  - TH2: Trợ cấp tháng = Cột 10 $\times 0.4 \times$ Lương; Trợ cấp năm = Cột 11 $\times 1.5 \times$ Lương; Trợ cấp việc làm = $3 \times$ Lương.
- **Phụ lục I.3**:
  - C12 = Cột 11 $\times 5 \times$ Lương.
  - C13 & C14: Tính theo số năm BHXH thực tế vượt mốc 20 năm (trước 01/07/2025) hoặc 15 năm (từ 01/07/2025).

---

## 3. Hướng Dẫn Sử Dụng Nhanh

### Chạy trực tiếp qua script:
- Nhấp đúp file `run_app.bat` để khởi động ứng dụng và tự động mở trình duyệt đến `http://localhost:8080`.
- Hoặc dùng lệnh:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```

### Đóng gói thành file `.EXE` độc lập:
- Nhấp đúp file `build_exe.bat` (hoặc chạy `powershell -ExecutionPolicy Bypass -File build_exe.ps1`).
- Quá trình tự động thực hiện:
  1. Build Spring Boot Fat JAR.
  2. Dùng `jlink` tạo JRE nhúng tối giản (kèm các module cần thiết: `java.base`, `java.desktop`, `java.sql`, `java.naming`, `jdk.unsupported`...).
  3. Dùng `jpackage` tạo file `KiemToanCheDoBQP.exe` trong thư mục `dist\portable\KiemToanCheDoBQP\`.
- **Triển khai**: Copy toàn bộ thư mục `KiemToanCheDoBQP\` sang bất kỳ máy tính Windows nào để chạy ngay lập tức mà không cần cài đặt Java.
