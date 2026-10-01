# Chuẩn hóa Excel theo mẫu 26.9

Trong màn hình thẩm định, nạp file như bình thường. Nút **Xuất theo mẫu
26.9 (I.1–I.3)** xuất toàn bộ ba phụ lục chi tiết của file đang nạp, không áp
dụng bộ lọc danh sách. Nút **Xuất file Excel báo cáo** tiếp tục dùng cho báo
cáo thẩm định trên bố cục nguồn.

Chức năng mới chạy trong giao diện `src/main/resources/static/index.html`.
Khởi động lại ứng dụng từ source/build mới và tải lại trang để sử dụng.
Bản phát hành EXE/JAR đã đóng gói trước đó không tự cập nhật. Các API Java
`/api/excel/validate` và `/api/excel/summary` chưa sử dụng bộ chuyển đổi này.

## Phạm vi và dữ liệu

- Dùng đúng mẫu `src/main/resources/static/26.9.PHU_LUC_SUA.xlsx`.
- Nhận diện tiêu đề nhiều dòng, cột đánh giá thêm, một/hai cột STT,
  số cột in sai hoặc lặp. Nếu không nhận diện được cấu trúc, báo lỗi.
- Không dừng khi gặp tổng cộng ở đầu hoặc giữa bảng. Không nhập bản sao
  I.1 (2), báo cáo II.1/III.1 hoặc ghi chú gộp ô thành hồ sơ mới.
- Giữ số tiền kê khai, không thay bằng số tiền phần mềm tính lại. Tổng cộng
  nguồn nằm trong bảng đối chiếu, tránh cộng trùng với các dòng chi tiết.
- File xuất gồm I.1, I.2, I.3 và **Đối chiếu nguồn**. I, I.4, I.5, II và
  các sheet khác chưa được chuyển đổi; giữ file nguồn để đối chiếu chúng.
- Bảng đối chiếu ghi sheet/dòng nguồn, dòng đích, cột ngoài mẫu và giá trị
  chưa chuyển được thành số. Ô trống được giữ trống. Không suy diễn ngày
  sáp nhập hoặc tự sửa nghiệp vụ theo tiêu đề/công thức có sẵn trong file.
- Công thức nguồn được xuất bằng kết quả đã lưu, bao gồm kết quả bằng 0.
  Công thức không có kết quả hoặc có lỗi Excel làm dừng xuất và báo ô cần
  kiểm tra. Không sao chép công thức sang vị trí mới với tham chiếu sai.
- Chuỗi như `37 năm 09 tháng`, `43 tháng`, `2,5` được đọc thành số;
  chuỗi mơ hồ như `27/10` giữ nguyên và ghi cảnh báo trong bảng đối chiếu.
  Chuẩn hóa bố cục không chứng minh tính đúng đắn của số liệu hay quy tắc
  tính quyền lợi. Phần thẩm định cũ vẫn cần đối chiếu nghiệp vụ riêng.

## Kiểm thử

Chạy từ thư mục project (không cần cài thêm thư viện Node):

```powershell
node src/test/js/normalization.test.cjs D:/bqp/input
```

Kiểm thử chạy cả engine đang nhúng trong HTML và file JavaScript riêng;
kiểm tra số hồ sơ, tên, dòng nguồn, tổng tiền, cột nhập ngũ/sáp nhập/lương;
xuất rồi đọc lại workbook trong bộ nhớ, kiểm tra dữ liệu và tiêu đề gộp ô;
kiểm tra dữ liệu/style nguồn không bị sửa. File thực tế không được ghi đè
và dữ liệu cá nhân không được thêm vào repository làm fixture.

Kết quả trên bộ input hiện tại:

| File | I.1 | I.2 | I.3 | Tổng hồ sơ |
| --- | ---: | ---: | ---: | ---: |
| QK5 | 2660 | 128 | 0 | 2788 |
| QK9 | 2109 | 140 | 9 | 2258 |
| BTL Thủ đô | 482 | 10 | 0 | 492 |
| Quân khu I | 837 | 8 | 1 | 846 |

Các số lượng này đếm dòng chi tiết có họ tên và cấp bậc, loại dòng ghi chú
gộp ô. Số tổng hợp do đơn vị ghi có thể khác tổng dòng chi tiết; không tự
điều chỉnh để ép khớp. Chưa kiểm tra thao tác in/xuất bằng Microsoft Excel.

Nếu dùng riêng `bqp_validation.js`, nạp `bqp_normalization.js` trước nó.
