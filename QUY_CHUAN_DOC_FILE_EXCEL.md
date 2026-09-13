# QUY CHUẨN ĐỊNH DẠNG FILE EXCEL ĐỂ ĐỌC VÀ XỬ LÝ CHÍNH XÁC 100%

Tài liệu này quy định **4 yếu tố cốt lõi** mà hệ thống yêu cầu để có thể tự động nhận diện, trích xuất và kiểm tra tính hợp lệ dữ liệu của các sheet phụ lục (Phụ lục I.1, Phụ lục I.2, Phụ lục I.3, Phụ lục II) trong file Excel báo cáo.

---

## 1. Tên tab Sheet (Sheet Tab Name)

Hệ thống nhận diện loại phụ lục **hoàn toàn dựa trên tên tab của Sheet** (không phụ thuộc vào nội dung ô A1 hay số dòng trống phía trên).

* **Phụ lục I.1**: Tên tab sheet phải chứa một trong các ký tự nhận diện sau (không phân biệt chữ hoa/thường):
  * `I.1`, `I1`, `PL I.1`, `PLI1`, `PL1`, `Phụ lục I.1`, `Phu luc I.1`
* **Phụ lục I.2**: Tên tab sheet phải chứa một trong các ký tự:
  * `I.2`, `I2`, `PL I.2`, `PLI2`, `PL2`, `Phụ lục I.2`, `Phu luc I.2`
* **Phụ lục I.3**: Tên tab sheet phải chứa một trong các ký tự:
  * `I.3`, `I3`, `PL I.3`, `PLI3`, `PL3`, `Phụ lục I.3`, `Phu luc I.3`
* **Phụ lục II**: Tên tab sheet phải chứa:
  * `Phụ lục II`, `Phu luc II`, `PL II`, `PLII`

---

## 2. Dòng đánh số thứ tự cột (`1, 2, 3, 4, 5...`)

Đây là **yếu tố quan trọng nhất** trong cấu trúc bảng tính của mỗi sheet.

### Đặc điểm kỹ thuật:
* **Vị trí**: Nằm trong khoảng **15 dòng đầu tiên** của sheet (thường nằm ngay dưới dòng tiêu đề chữ của các cột).
* **Nội dung**: Chứa tối thiểu **7 cột** có số thứ tự tăng dần liên tiếp (`1, 2, 3, 4, 5, 6, 7...`).
* **Vai trò**:
  * **Tự động nhận diện lệch cột (Offset Detection)**: Hệ thống tự động kiểm tra tiêu đề phía trên cột số `1`:
    * *Trường hợp A (Mẫu Quân khu)*: Cột số 1 là **"Họ và tên"** $\rightarrow$ Hệ thống giữ nguyên vị trí chuẩn.
    * *Trường hợp B (Mẫu Binh chủng)*: Cột số 1 là **"Số TT"** (số 2 là "Họ và tên") $\rightarrow$ Hệ thống tự động dịch chuyển toàn bộ các cột sang `+1`, đảm bảo đọc đúng 100% các trường Họ tên, Ngày sinh, Cấp bậc, Lương và bôi đỏ chính xác ô sai trên Excel.
  * **Định vị cột động (Chống lỗi khi gộp ô / Merged Cells)**: Khi các đơn vị gộp các ô (ví dụ: gộp cột B, C, D thành một cột "Họ và tên"), hệ thống sẽ căn cứ vào vị trí ô chứa số `1`, `2`, `3`... để map chính xác từng trường dữ liệu, không bị đọc lệch sang cột rỗng.
  * **Tự thích ứng khi thiếu dòng số**: Trường hợp sheet không có dòng số `1, 2, 3...` (như sheet I.1 của BC Tăng thiết giáp), hệ thống tự động quét dòng tiêu đề chữ để định vị cột và dòng bắt đầu dữ liệu.
  * **Xác định dòng bắt đầu đọc**: Dữ liệu cán bộ/quân nhân sẽ được tự động bắt đầu đọc từ dòng ngay kế tiếp dòng đánh số này.

---

## 3. Quy ước cấu trúc dòng dữ liệu (Data Rows)

* **Nhận diện dòng dữ liệu cá nhân**:
  * Cột **"Họ và tên"** bắt buộc phải có nội dung văn bản.
  * Nếu một dòng có cột Họ và tên rỗng hoàn toàn, hệ thống sẽ tự động bỏ qua (coi như dòng trống).
* **Dòng phân tách cơ quan, đơn vị**:
  * Các dòng tiêu đề nhóm đơn vị (ví dụ: *I. Cơ quan Bộ chỉ huy*, *II. Các đơn vị trực thuộc*, *1. Ban Tham mưu*...) được tự động nhận diện để phân loại và gom nhóm danh sách người có sai sót khi xuất sang Phụ lục II.
* **Dòng kết thúc đọc**:
  * Hệ thống tự động dừng đọc sheet khi gặp dòng có chữ **`Cộng`**, **`Tổng cộng`** hoặc khi đọc hết toàn bộ các dòng có dữ liệu.

---

## 4. Định dạng dữ liệu ở các cột trọng yếu

### a. Cột Ngày / Tháng / Năm *(Tháng năm sinh, Nhập ngũ, Sáp nhập/giải thể, Thời điểm nghỉ hưu)*:
* Hệ thống hỗ trợ linh hoạt cả 2 định dạng:
  * **Định dạng Date/Time chuẩn trong Excel**.
  * **Chuỗi văn bản (Text)** theo các định dạng phổ biến:
    * `MM/yyyy`, `M/yyyy` (ví dụ: `05/1985`, `7/1990`)
    * `dd/MM/yyyy`, `d/M/yyyy` (ví dụ: `15/04/1980`)
    * `yyyy` (ví dụ: `1978`)
    * `MM-yyyy`, `M-yyyy` (ví dụ: `09-1988`)
    * **Năm 2 chữ số (2-digit years)**: `MM/yy`, `M/yy`, `dd/MM/yy` (ví dụ: `02/69` $\rightarrow$ `02/1969`, `3/25` $\rightarrow$ `03/2025`, `8/86` $\rightarrow$ `08/1986`, `02/25` $\rightarrow$ `02/2025`). Tự động quy đổi năm $\le 45$ thành `20xx`, $> 45$ thành `19xx`.
    * **Tự động sửa lỗi gõ phím**:
      * Chữ `O` hoặc `o` gõ nhầm thay số `0` (ví dụ: `O2/1974` $\rightarrow$ `02/1974`).
      * Excel tự động chuyển năm `69` thành `2069` $\rightarrow$ tự động nhận diện và chuyển về `1969`.
      * Nhiều mốc ngày phân tách bằng dấu `;` (ví dụ: `12/88; 7/96`) $\rightarrow$ tự động lấy mốc nhập ngũ ban đầu.
    * Dạng viết tắt tháng: `Thg7-25`, `Thg10/1985`...

### b. Cột Cấp bậc / Chức vụ / Trần quân hàm:
* Hỗ trợ đọc tên cấp bậc bằng văn bản đầy đủ và biến thể:
  * `Đại tá`, `Thượng tá`, `Trung tá`, `Thiếu tá`, `Đại úy`, `Thượng úy`, `Trung úy`, `Thiếu úy`, `QNCN`, `VCQP`, `CNQP`...
  * Tự động sửa lỗi chính tả gõ nhầm dấu hoặc xuống dòng: `Đai tá`, `Thượng tạ`, `Thuong tá`, `Thiéu tá`, `Thượng\ntá`, `Thượng\núy`...
  * Hỗ trợ các ký hiệu nâng lương: `NL1`, `NL2`, `NLL1`, `NLL2`, `NLLI`, `NLLI1`, `NNL1`, `4//L2`...
* Hỗ trợ nhận diện các **ký hiệu trần quân hàm và nhóm chức vụ lương**:
  * `4//`: Đại tá (Trần tuổi: 58)
  * `3//`, `3//CN`, `3// CN`: Thượng tá (Trần tuổi: 56)
  * `2//`, `2//CN`: Trung tá (Trần tuổi: 54)
  * `1//`, `1//CN`, `1// CN`: Thiếu tá (Trần tuổi: Sĩ quan 52, QNCN 54)
  * `4/`, `4/ CN`: Đại úy (Trần tuổi: Sĩ quan 50, QNCN 52)
  * `3/`, `3/ CN`: Thượng úy (Trần tuổi: Sĩ quan 50, QNCN 52)
  * `2/`, `2/ CN`: Trung úy (Trần tuổi: Sĩ quan 50, QNCN 52)
  * `1/`, `1/ CN`: Thiếu úy (Trần tuổi: Sĩ quan 50, QNCN 52)
  * **Nhóm chức vụ / thang lương**:
    * `24.0`, `24.1`, `24.2`: Nhóm chức vụ tương đương Đại tá (Trần tuổi: 58)
    * `23.1`, `23.2`: Nhóm chức vụ tương đương Thượng tá (Trần tuổi: 56)
    * `22.1`, `22.2`: Nhóm chức vụ tương đương Trung tá (Trần tuổi: 54)
    * `21.1`, `21.2`: Nhóm chức vụ tương đương Thiếu tá (Trần tuổi: 52)
  * **Tự động nhận diện Quân nhân chuyên nghiệp (QNCN)** qua chức vụ để áp dụng đúng mức trần tuổi (Ví dụ: Thiếu tá QNCN trần 54 tuổi; Thiếu tá Sĩ quan trần 52 tuổi):
    * Các từ khóa chức vụ QNCN được hỗ trợ: `NV`, `nhân viên`, `y sĩ`, `y sỹ`, `thủ kho`, `bảo quản kho`, `thuỷ thủ`, `thủy thủ`, `lái xe`, `thợ`, `trạm`, `chạm`, `qncn`, `cn`...

### c. Các cột Tiền tệ / Số tháng / Số năm:
* Hỗ trợ giá trị dạng **Số (Numeric)**, **Công thức (Formula)** hoặc **Chuỗi (Text)**.
* **Tự động quy đổi chuỗi thời gian**:
  * Dạng `"YY-MM"` (ví dụ: `"34-06"`, `"05-05"`, `"02-04"`, `"30-09"`, `"03-11"`): tự động làm tròn theo quy tắc (tháng $\le 6 \rightarrow +0.5$ năm, tháng $> 6 \rightarrow +1.0$ năm).
  * Dạng chữ: `"26 tháng"` $\rightarrow 26$, `"36 năm"` $\rightarrow 36$, `"2 năm 2 tháng"` $\rightarrow 2.5$ năm, `"33 năm 07 tháng"` $\rightarrow 34$ năm.
* Hệ thống tự động làm sạch các dấu phân cách hàng nghìn (dấu chấm `.`, dấu phẩy `,` hoặc khoảng trắng) trước khi đưa vào các thuật toán tính toán và đối soát.
* **Chống báo lỗi dây chuyền (Cascade Error Prevention)**: Khi đơn vị kê khai thời gian đóng BHXH (Cột 12) từ Sổ BHXH thực tế (ví dụ có thời gian công tác khác hoặc làm tròn thành 34 năm thay vì 33.5 năm), hệ thống ghi chú để thẩm định viên kiểm tra Cột 12 nhưng các cột tính tiền (Cột 18, 19, 21, 22) vẫn đối soát chính xác theo số năm BHXH thực tế đó, không sinh lỗi tính tiền oan.

---

## 5. Quy cách hiển thị khi báo lỗi (Error Formatting)

Khi phát hiện dữ liệu không hợp lệ hoặc sai công thức tính toán, hệ thống áp dụng quy chuẩn hiển thị sau:
* **Font chữ**: `Arial`
* **Cỡ chữ (Font size)**: `7`
* **Màu chữ**: Màu đỏ (`IndexedColors.RED`)
* **Phạm vi áp dụng**:
  * **Các ô dữ liệu sai trong bảng**: Đổi chữ sang màu đỏ, font Arial 7 (giữ nguyên viền và căn lề gốc).
  * **Cột "Ghi chú"**: Tiêu đề cột in đậm (Bold) màu đỏ, nội dung giải thích chi tiết lỗi và công thức tính lại sử dụng font Arial 7 màu đỏ, tự động xuống dòng (Wrap Text).

---

## Tóm tắt nguyên lý hoạt động

```
[File Excel Upload]
       │
       ▼
 1. Quét tên tab Sheet (I.1, I.2, I.3, II)
       │
       ▼
 2. Tìm dòng đánh số cột (1, 2, 3...) trong 15 dòng đầu
    └── Tự động ánh xạ vị trí các cột (bất chấp ô Merge)
       │
       ▼
 3. Đọc dữ liệu từ dòng kế tiếp:
    ├── Cột Họ tên có dữ liệu -> Đọc & Validate quy tắc
    ├── Dòng tiêu đề La Mã -> Cập nhật tên đơn vị
    └── Gặp dòng 'Cộng' / 'Tổng cộng' -> Dừng đọc sheet
       │
       ▼
 4. Báo đỏ ô sai trong sheet (Arial size 7) & Tự động sinh danh sách sai vào Phụ lục II
```

