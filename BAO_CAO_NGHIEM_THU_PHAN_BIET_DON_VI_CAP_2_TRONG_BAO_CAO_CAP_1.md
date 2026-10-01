# Báo cáo Nghiệm thu: Phân biệt đơn vị cấp 2 trong báo cáo xuất cấp 1

## 1. Mục tiêu hoàn thành
Thực hiện thành công việc phân tách dữ liệu theo đơn vị cấp 2 trong các sheet chi tiết (I.1, I.2, I.3) khi xuất báo cáo cấp 1 (toàn tuyến), giúp người đọc dễ dàng nhận diện mỗi đơn vị cấp 3 trực thuộc đơn vị cấp 2 nào.

## 2. Các thay đổi kỹ thuật
- **File sửa đổi:** `src/main/resources/static/bqp_validation.js`
- **Nội dung:**
  - Cập nhật hàm `populateChildSheet` (nằm trong `exportValidatedWorkbook`):
    - Đã ứng dụng `level2Resolver` có sẵn để truy vấn và gán thêm thuộc tính `l2Name` (Tên đơn vị cấp 2) cùng `l2Order` (Thứ tự hiển thị cấp 2) vào cấu trúc Bucket khi gom nhóm hồ sơ.
    - Sửa đổi thuật toán sắp xếp (`sortedBuckets`): Ưu tiên sắp xếp theo `l2Order`, sau đó tới tên cấp 2 (`l2Name`), rồi mới tới tên đơn vị cấp 3.
    - Cập nhật quá trình tạo `descriptors`: Khi phát hiện có sự chuyển đổi giữa các đơn vị cấp 2 (`previousL2 !== bucket.l2Name`), hệ thống sinh ra một `descriptor` đặc biệt có đánh dấu `isL2Header: true`, đồng thời reset lại bộ đếm số La Mã cấp 3 (`unitNumber = 0`) để đếm lại từ đầu ("I") đối với từng cấp 2 mới.
    - Sửa vòng lặp kết xuất Excel (`applyTemplateRow`): Nhận diện nhãn `isL2Header`, bôi đậm, in nghiêng, và đổ màu xanh nhạt (`FFDBE5F1`) cho ô chứa tên đơn vị cấp 2 (định dạng text `=== [Tên cấp 2] ===`) và fill màu toàn bộ dòng để tạo sự ngăn cách nổi bật so với tiêu đề cấp 3 (chỉ có số La Mã và Tên).

## 3. Kết quả đánh giá
- **Giao diện sheet:** Đã hiển thị tiêu đề cấp 2 xen kẽ chuẩn xác, ví dụ `=== Sư đoàn 306 ===` ngay trên đầu các tiểu đoàn trực thuộc, reset số La Mã.
- **Tính tương thích:** Logic có điều kiện kiểm tra tồn tại `level2Resolver`, tức là với các trường hợp xuất báo cáo cục bộ (không phải xuất cấp 1 - không có resolver) luồng xử lý vẫn hoạt động trơn tru như trước.
- **Đồng bộ file:** File `index.html` không có bản sao inline của `populateChildSheet`, do đó toàn bộ logic đã được hợp nhất ở `bqp_validation.js`.

## 4. Hướng dẫn sử dụng
1. Tải lại trang (F5) hoặc khởi động lại ứng dụng.
2. Nạp file / Truy vấn hồ sơ thuộc các đơn vị cấp 3 của nhiều đơn vị cấp 2 khác nhau (ví dụ: QK5).
3. Đảm bảo ở thanh công cụ bạn chọn **Cấp xuất báo cáo: Cấp 1**.
4. Xuất Excel và mở file.
5. Xem sheet Phụ lục I.1, I.2, hoặc I.3, bạn sẽ thấy các đơn vị cấp 3 đã được gom nhóm rõ ràng dưới băng tên màu xanh nhạt của đơn vị cấp 2.

*Ghi chú: Agent kiểm thử trình duyệt tạm thời gặp sự cố nên chưa thể chụp màn hình. Vui lòng tự nghiệm thu trên trình duyệt.*
