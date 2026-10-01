# Kế hoạch: Xử lý tam giác đỏ (Note) trong file báo cáo xuất

**Ngày lập:** 01/10/2026  
**Phạm vi:** Luồng xuất báo cáo JavaScript, logic đánh dấu lỗi, xử lý Note Excel

---

## 1. Mục tiêu

Loại bỏ hoặc kiểm soát các tam giác đỏ (Excel Note) xuất hiện trong file báo cáo thẩm định để file xuất sạch sẽ, chỉ có cảnh báo cần thiết với người dùng.

---

## 2. Phân tích hiện trạng

### 2.1. Tam giác đỏ là gì?

Tam giác đỏ ở góc phía trên bên phải ô Excel là dấu hiệu ô có **Note** (ghi chú/comment). Khi hover hoặc click vào ô, Note sẽ hiện ra dưới dạng tooltip vàng.

### 2.2. Ba điểm code tạo Note

#### Điểm 1: Đánh dấu lỗi thẩm định (có chủ ý)

**Vị trí:** [bqp_validation.js:2952-2963](src/main/resources/static/bqp_validation.js:2952-2963)

```js
if (hasCellErr && rec.cellErrors) {
    for (let errColIdx in rec.cellErrors) {
        let targetCol = colMap[errColIdx] || parseInt(errColIdx, 10);
        if (targetCol) {
            try {
                let cellToMark = row.getCell(targetCol);
                cellToMark.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFFFC7CE' } };
                cellToMark.font = Object.assign({}, cellToMark.font || {}, { color: { argb: 'FF9C0006' }, bold: true });
                cellToMark.note = '[Lỗi ô: ' + rec.cellErrors[errColIdx] + ']';  // ← Note tại đây
            } catch(eF) {}
        }
    }
}
```

**Mục đích:** Đánh dấu các ô có lỗi thẩm định (ví dụ: thời gian BHXH sai, công thức tính sai) để cán bộ dễ nhận biết và xác minh.

**Đặc điểm:**
- Fill màu hồng nhạt `FFFFC7CE`
- Font màu đỏ đậm `FF9C0006`
- Note chứa thông tin lỗi cụ thể

**Đánh giá:** Đây là tính năng có ích, **nên giữ lại** hoặc cải tiến cách hiển thị.

#### Điểm 2: Đánh dấu lỗi xuất ô (không mong muốn)

**Vị trí:** [bqp_validation.js:2577-2597](src/main/resources/static/bqp_validation.js:2577-2597)

```js
const markCellExportError = (cell, err, fallbackVal) => {
    if (!cell) return;
    try {
        if (fallbackVal !== undefined) {
            cell.value = fallbackVal;
        } else if (cell.value && typeof cell.value === 'object') {
            cell.value = cell.value.result != null ? cell.value.result : (cell.value.formula || '[Lỗi ô]');
        }
        cell.fill = {
            type: 'pattern',
            pattern: 'solid',
            fgColor: { argb: 'FFFFC7CE' }
        };
        cell.font = Object.assign({}, cell.font || {}, {
            color: { argb: 'FF9C0006' },
            bold: true
        });
        cell.note = `[Lỗi xuất ô: ${err ? (err.message || String(err)) : 'Không xác định'}]`;  // ← Note tại đây
    } catch(e) {}
};
```

**Mục đích:** Khi gặp lỗi trong quá trình xuất (ví dụ: không thể ghi giá trị, lỗi định dạng), thay vì crash toàn bộ quá trình xuất, code đánh dấu ô lỗi và tiếp tục.

**Đặc điểm:**
- Cùng màu với lỗi thẩm định (hồng + đỏ)
- Note chứa thông tin lỗi kỹ thuật

**Đánh giá:** Nếu có nhiều ô bị Note này, nghĩa là **có lỗi trong luồng xuất** cần sửa, không nên để người dùng thấy file có lỗi xuất.

#### Điểm 3: Xóa Note khi dọn template

**Vị trí:** [bqp_validation.js:2782-2787](src/main/resources/static/bqp_validation.js:2782-2787)

```js
for (let r = layout.dataStart; r < layout.footerRow; r++) {
    ws.getRow(r).eachCell({ includeEmpty: true }, cell => {
        if (!cell.isMerged || cell.master.address === cell.address) cell.value = null;
        cell.note = undefined;  // ← Xóa Note tại đây
    });
}
```

**Mục đích:** Khi clone template có sẵn Note (ví dụ ghi chú hướng dẫn trong file mẫu), xóa sạch trước khi điền dữ liệu mới.

**Đánh giá:** Logic đúng, **cần giữ**.

### 2.3. Phân loại nguồn gốc Note trong file người dùng

Dựa vào ảnh người dùng cung cấp, các tam giác đỏ xuất hiện trên nhiều ô. Cần xác định:

1. **Note từ lỗi thẩm định** (`[Lỗi ô: ...]`):
   - Có chủ ý
   - Số lượng phụ thuộc vào dữ liệu đầu vào
   - Người dùng cần biết để xác minh

2. **Note từ lỗi xuất** (`[Lỗi xuất ô: ...]`):
   - Không mong muốn
   - Nếu xuất hiện nhiều, cần điều tra và sửa nguyên nhân
   - Không nên để người dùng thấy

3. **Note từ template không được xóa**:
   - Có thể do logic xóa chưa bao phủ hết các vùng
   - Hoặc template có Note ở vùng ngoài `dataStart` đến `footerRow`

---

## 3. Câu hỏi cần làm rõ với người dùng

Trước khi sửa, cần xác nhận:

1. **Người dùng có muốn giữ cảnh báo lỗi thẩm định không?**
   - Nếu có: giữ màu fill/font nhưng loại bỏ Note (để không có tam giác đỏ)
   - Nếu không: loại bỏ cả màu và Note

2. **File xuất hiện tại có nhiều Note `[Lỗi xuất ô: ...]` không?**
   - Nếu có: cần điều tra và sửa lỗi xuất
   - Nếu không: chủ yếu là Note lỗi thẩm định

3. **Có Note nào khác ngoài 2 loại trên không?**
   - Ví dụ: Note từ template, Note người dùng thêm thủ công
   - Nếu có: cần xác định có nên giữ lại không

---

## 4. Các phương án giải quyết

### Phương án A: Loại bỏ hoàn toàn Note, chỉ giữ màu sắc

**Ưu điểm:**
- File xuất không có tam giác đỏ
- Vẫn có cảnh báo trực quan (màu hồng + chữ đỏ đậm)
- Đơn giản, ít thay đổi code

**Nhược điểm:**
- Mất thông tin chi tiết về lỗi (ví dụ: "Thời gian BHXH sai: kỳ vọng 33,5 năm, thực tế 33,6 năm")
- Cán bộ phải nhìn vào màu và đoán lỗi là gì

**Triển khai:**
```js
// Dòng 2960: Xóa dòng gán Note
// cellToMark.note = '[Lỗi ô: ' + rec.cellErrors[errColIdx] + ']';  ← Xóa dòng này

// Dòng 2595: Xóa dòng gán Note
// cell.note = `[Lỗi xuất ô: ${err ? (err.message || String(err)) : 'Không xác định'}]`;  ← Xóa dòng này
```

### Phương án B: Giữ Note cho lỗi thẩm định, loại bỏ Note lỗi xuất

**Ưu điểm:**
- Giữ thông tin chi tiết cho lỗi thẩm định
- Loại bỏ Note không mong muốn (lỗi xuất)

**Nhược điểm:**
- Vẫn có tam giác đỏ ở các ô lỗi thẩm định
- Nếu có nhiều lỗi thẩm định, file vẫn có nhiều tam giác đỏ

**Triển khai:**
```js
// Dòng 2595: Xóa dòng gán Note
// cell.note = `[Lỗi xuất ô: ${err ? (err.message || String(err)) : 'Không xác định'}]`;  ← Xóa dòng này

// Dòng 2960: Giữ nguyên
cellToMark.note = '[Lỗi ô: ' + rec.cellErrors[errColIdx] + ']';  // ← Giữ lại
```

**Bổ sung:** Điều tra và sửa nguyên nhân gây lỗi xuất để `markCellExportError` không bao giờ được gọi.

### Phương án C: Thay Note bằng Data Validation hoặc Conditional Formatting

**Ưu điểm:**
- Không có tam giác đỏ
- Vẫn có cảnh báo trực quan
- Thông tin lỗi có thể lưu ở nơi khác (ví dụ: sheet riêng "Danh sách lỗi")

**Nhược điểm:**
- Phức tạp hơn, cần thay đổi nhiều code
- Data Validation có giới hạn độ dài message
- Conditional Formatting không lưu thông tin lỗi

**Triển khai:** Cần thiết kế lại cách hiển thị lỗi.

### Phương án D: Thêm sheet "Danh sách lỗi" riêng

**Ưu điểm:**
- File chính sạch sẽ, không có tam giác đỏ
- Thông tin lỗi đầy đủ, chi tiết
- Dễ tổng hợp và review

**Nhược điểm:**
- Cán bộ phải chuyển qua sheet khác để xem lỗi
- Mất liên kết trực tiếp giữa ô và thông tin lỗi

**Triển khai:**
```js
// Tạo sheet "Danh sách lỗi" với các cột:
// - STT
// - Sheet
// - Dòng
// - Cột
// - Họ tên
// - Loại lỗi
// - Mô tả chi tiết

// Khi có lỗi, thêm vào sheet này thay vì gán Note
```

---

## 5. Kế hoạch triển khai (Khuyến nghị: Phương án B + D)

### Giai đoạn 1 — Xác nhận yêu cầu với người dùng

**Câu hỏi:**
1. Bạn có muốn file xuất hoàn toàn không có tam giác đỏ không?
2. Nếu có lỗi thẩm định, bạn muốn cảnh báo như thế nào? (màu sắc, sheet riêng, hoặc không cần cảnh báo trực quan)
3. Hiện tại file xuất có bao nhiêu tam giác đỏ? Nội dung Note là gì?

**Đầu ra:** Quyết định phương án triển khai.

### Giai đoạn 2 — Điều tra lỗi xuất (nếu có)

**Mục tiêu:** Xác định có bao nhiêu Note `[Lỗi xuất ô: ...]` trong file thực tế.

**Cách thực hiện:**
1. Mở file báo cáo người dùng gửi
2. Duyệt qua các ô có tam giác đỏ
3. Ghi lại nội dung Note và vị trí ô

**Nếu phát hiện lỗi xuất:**
- Xem log console trình duyệt khi xuất file
- Kiểm tra điều kiện gọi `markCellExportError`
- Sửa nguyên nhân gây lỗi xuất

**Đầu ra:** Danh sách lỗi xuất (nếu có) và nguyên nhân.

### Giai đoạn 3 — Loại bỏ Note không mong muốn

**File cần sửa:**
- [bqp_validation.js](src/main/resources/static/bqp_validation.js)
- [index.html](src/main/resources/static/index.html) (bản inline)

**Thay đổi:**

#### 3.1. Loại bỏ Note lỗi xuất

```js
// bqp_validation.js dòng 2595
// Xóa hoặc comment dòng này:
// cell.note = `[Lỗi xuất ô: ${err ? (err.message || String(err)) : 'Không xác định'}]`;
```

#### 3.2. Tùy chọn: Loại bỏ Note lỗi thẩm định

Nếu người dùng muốn không có tam giác đỏ:

```js
// bqp_validation.js dòng 2960
// Xóa hoặc comment dòng này:
// cellToMark.note = '[Lỗi ô: ' + rec.cellErrors[errColIdx] + ']';
```

#### 3.3. Đồng bộ với index.html

Tìm đoạn tương ứng trong `index.html` và áp dụng thay đổi tương tự.

### Giai đoạn 4 — Thêm sheet "Danh sách lỗi" (nếu chọn Phương án D)

**Vị trí:** Sau khi tạo các sheet chi tiết, trước khi xuất file.

**Cấu trúc sheet:**

| STT | Sheet | Dòng | Cột | Họ tên | Loại lỗi | Mô tả chi tiết |
|-----|-------|------|-----|--------|----------|----------------|
| 1 | Phụ lục I.1 | 10 | C12 | Nguyễn Văn A | Thời gian BHXH | Kỳ vọng 33,5 năm, kê khai 33,6 năm |
| 2 | Phụ lục I.2 | 15 | C10 | Trần Thị B | Điều kiện tuổi | Còn lại < 24 tháng |

**Logic:**
```js
// Sau khi tổng hợp recordsToExport
const errors = [];
recordsToExport.forEach((rec, idx) => {
    if (rec.cellErrors) {
        for (let colIdx in rec.cellErrors) {
            errors.push({
                stt: errors.length + 1,
                sheet: rec.sheetName || 'N/A',
                row: rec.rowIndex || idx + 10,
                col: colIdx,
                name: rec.name || rec.C2 || '',
                errorType: 'Lỗi ô',
                description: rec.cellErrors[colIdx]
            });
        }
    }
});

// Tạo sheet "Danh sách lỗi"
if (errors.length > 0) {
    const errSheet = outWb.addWorksheet('Danh sách lỗi');
    // Header
    errSheet.getRow(1).values = ['STT', 'Sheet', 'Dòng', 'Cột', 'Họ tên', 'Loại lỗi', 'Mô tả chi tiết'];
    // Data
    errors.forEach((err, idx) => {
        errSheet.getRow(idx + 2).values = [
            err.stt,
            err.sheet,
            err.row,
            err.col,
            err.name,
            err.errorType,
            err.description
        ];
    });
    // Style header
    errSheet.getRow(1).font = { bold: true };
    errSheet.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFD9E1F2' } };
}
```

### Giai đoạn 5 — Test và xác minh

1. Import file phụ lục có lỗi thẩm định
2. Xuất báo cáo
3. Mở file Excel xuất, kiểm tra:
   - Không có tam giác đỏ ở các ô dữ liệu (hoặc chỉ có ở ô lỗi thẩm định nếu giữ Note)
   - Sheet "Danh sách lỗi" (nếu có) hiển thị đầy đủ thông tin
   - Màu fill/font vẫn đánh dấu ô lỗi
4. Test với nhiều file khác nhau (QK5, BTL Hà Nội...)

---

## 6. Tiêu chí hoàn thành

- [ ] Không còn Note `[Lỗi xuất ô: ...]` trong file xuất (lỗi xuất đã được sửa)
- [ ] Note `[Lỗi ô: ...]` được xử lý theo quyết định người dùng (giữ/loại bỏ/chuyển sang sheet riêng)
- [ ] File xuất không có tam giác đỏ không mong muốn
- [ ] Vẫn có cảnh báo trực quan cho lỗi thẩm định (màu sắc hoặc sheet riêng)
- [ ] Logic xử lý Note đồng bộ giữa `bqp_validation.js` và `index.html`
- [ ] Test với nhiều file nguồn khác nhau

---

## 7. Rủi ro

| Rủi ro | Khả năng | Tác động | Giảm thiểu |
|--------|----------|----------|------------|
| Loại bỏ Note làm mất thông tin lỗi quan trọng | Trung bình | Cao | Thêm sheet "Danh sách lỗi" để giữ thông tin chi tiết |
| Vẫn có Note từ nguồn khác chưa phát hiện | Thấp | Trung bình | Kiểm tra kỹ file mẫu và logic xóa Note |
| Lỗi xuất vẫn xảy ra sau khi loại bỏ Note | Trung bình | Cao | Điều tra và sửa nguyên nhân lỗi xuất trước khi loại bỏ Note |
| Bản inline trong index.html bị lệch | Cao | Cao | Đồng bộ mọi thay đổi ngay lập tức |

---

## 8. Lưu ý

- Note Excel (`cell.note`) khác với Comment (`cell.comment`). ExcelJS dùng thuật ngữ `note` cho tính năng Comment của Excel.
- Việc loại bỏ Note không ảnh hưởng đến màu fill và font, vẫn có cảnh báo trực quan.
- Nếu file có quá nhiều lỗi thẩm định, cân nhắc cải thiện chất lượng dữ liệu đầu vào hoặc tăng cường validation trước khi xuất.
- Khi dùng sheet "Danh sách lỗi", cần thêm hyperlink từ ô lỗi đến dòng tương ứng trong sheet lỗi (nếu muốn).
