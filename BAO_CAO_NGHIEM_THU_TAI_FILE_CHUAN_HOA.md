# Báo cáo Nghiệm thu: Tính năng "Tải file chuẩn hóa"

## 1. Mục tiêu đã hoàn thành
Triển khai thành công tính năng "Tải file chuẩn hóa", cho phép người dùng tải xuống file Excel dữ liệu đã được chuẩn hóa theo format của Bộ Quốc phòng.

## 2. Các yêu cầu đã được xác nhận (Quyết định thiết kế)
Trong quá trình triển khai, đã xác nhận với người dùng các quy tắc cấu hình sau:
1. **Sheet tổng hợp:** **Có giữ lại** ("Phụ lục I" hoặc "Phụ lục II") để đảm bảo file chuẩn hóa có cấu trúc đầy đủ, hoàn chỉnh như template BQP ban hành.
2. **Xử lý công thức:** **Chỉ xuất giá trị (value)** đã được cache, bỏ qua công thức. Việc này đảm bảo file an toàn, tránh lỗi tham chiếu hoặc lỗi công thức khi mở trên các môi trường Excel / LibreOffice khác nhau.
3. **Các dòng ngoài cấu trúc (Ghi chú, nhóm đơn vị):** **Được giữ nguyên** để bảo toàn cách trình bày của file nguồn.

## 3. Các thay đổi kỹ thuật
- **File sửa đổi:** `src/main/resources/static/bqp_normalization.js`
- **Nội dung:**
  - Viết mới hàm `async function download(validationUI)` thực hiện quy trình tải:
    - Nhận diện workbook đang mở (`rawWb` hoặc `currentWb`).
    - Khởi tạo file theo mẫu chuẩn (base64 `EMBEDDED_TEMPLATE_B64`).
    - Nạp dữ liệu vào file thông qua `buildWorkbook`.
    - Lọc bỏ sheet "Đối chiếu nguồn" (vì tính năng chuẩn hóa chỉ nhằm xuất dữ liệu chuẩn, không cần log audit).
    - Xuất dưới dạng file Excel với định dạng tên `{Tên file gốc}_chuan_hoa_BQP.xlsx`.
  - Cập nhật đối tượng `api` để export hàm `download` ra ngoài (gọi qua `BQPNormalization.download`).

## 4. Hướng dẫn kiểm tra
1. Tải lại trang ứng dụng (hoặc restart Spring Boot nếu cần).
2. Import một file dữ liệu (Ví dụ: `13. Phụ lục QK5.xlsx`).
3. Click nút **"Tải file chuẩn hóa"** ở thanh công cụ phía trên.
4. Mở file được tải về (`13. Phụ lục QK5_chuan_hoa_BQP.xlsx`) và kiểm tra:
   - Các sheet Phụ lục I.1 đến I.5 có dữ liệu đầy đủ.
   - Không chứa các sheet "Đạt chuẩn", "Cấp thiếu", v.v (thuộc báo cáo thẩm định).
   - Không chứa sheet "Đối chiếu nguồn".
   - Định dạng giống hệt Template gốc của Bộ Quốc phòng.

*Lưu ý: Agent kiểm thử trình duyệt tạm thời gặp sự cố tải model nên chưa thể chụp màn hình kết quả tự động. Bạn vui lòng thao tác trực tiếp trên trình duyệt nhé.*
