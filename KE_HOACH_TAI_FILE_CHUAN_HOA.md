# Kế hoạch: Chức năng "Tải file chuẩn hóa"

**Ngày lập:** 01/10/2026  
**Phạm vi:** Giao diện JavaScript, module chuẩn hóa, xuất workbook theo template BQP

---

## 1. Mục tiêu

Cho phép người dùng tải xuống file Excel đã được chuẩn hóa theo định dạng BQP, lấy dữ liệu từ lần import file gần nhất, **không phải báo cáo thẩm định**.

**Khác biệt với xuất báo cáo thẩm định:**
- Báo cáo thẩm định: có các sheet đạt/thừa/thiếu chuẩn, giá trị tính lại, chênh lệch
- File chuẩn hóa: chỉ dữ liệu kê khai theo đúng cấu trúc/format template BQP, không có kết quả thẩm định

---

## 2. Hiện trạng (dựa vào kết quả khảo sát)

### 2.1. Nút đã có markup nhưng chưa có handler

**Vị trí:** [index.html:3305](src/main/resources/static/index.html:3305)
```html
<button onclick="BQPNormalization.download(ValidationUI)" ...>
    <i class="fas fa-download"></i> Tải file chuẩn hóa
</button>
```

**Vấn đề:** `BQPNormalization.download` không tồn tại trong [bqp_normalization.js](src/main/resources/static/bqp_normalization.js). Module này chỉ export:
- `sheetType` (line 547)
- `findColMap` (line 548)
- `buildWorkbook` (line 549)
- Các helper khác

**Không export:** `download`, `normalizeWorkbook`

### 2.2. Workbook nguồn được giữ trong bộ nhớ

**Vị trí:** [bqp_validation.js:13913-13918](src/main/resources/static/bqp_validation.js:13913-13918)
```js
ValidationUI.currentFile = null;     // tên file
ValidationUI.currentWb = null;       // workbook đang dùng để thẩm định
ValidationUI.rawWb = null;           // workbook gốc người dùng upload
ValidationUI.currentRecords = null;  // kết quả parse
```

Khi upload, `rawWb` giữ workbook gốc, `currentWb` giữ workbook đang phân tích ([13920-13997](src/main/resources/static/bqp_validation.js:13920-13997)).

### 2.3. Có sẵn engine chuẩn hóa nhưng chưa nối với nút

**Module:** [bqp_normalization.js](src/main/resources/static/bqp_normalization.js)

**Các thành phần:**
1. **`sheetType(sheetName)`** (line 33-39): Nhận diện loại sheet (I.1, I.2, I.3, I.4, I.5)
2. **`findColMap(sheet, type)`** (line 139-203): Tìm mapping cột trong header
3. **`buildWorkbook(sourceWb, templateBytes)`** (line 235-278):
   - Đọc template bytes
   - Dọn workbook mẫu
   - Ghi các phụ lục chi tiết từ nguồn
   - Phân loại dòng cá nhân (line 293-446)
   - Tạo sheet tổng hợp (line 452-539)
   - **Thêm sheet "Đối chiếu nguồn"** (line 246-247) — không phù hợp file chuẩn hóa thuần

**Template:** Embedded base64 tại `EMBEDDED_TEMPLATE_B64` (line 550), tương ứng file `PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx`

### 2.4. Export thẩm định không tái sử dụng được

**Vị trí:** [bqp_validation.js:5708-5763](src/main/resources/static/bqp_validation.js:5708-5763)

Handler `ValidationUI.exportExcel()` → `BQPValidation.exportValidatedWorkbook(...)` xuất báo cáo có:
- Sheet đạt/thừa/thiếu chuẩn
- Kết quả thẩm định
- Chênh lệch

→ **Không phù hợp** cho file chuẩn hóa thuần.

---

## 3. Thiết kế giải pháp

### 3.1. Luồng xử lý đề xuất

```
Người dùng bấm "Tải file chuẩn hóa"
  ↓
BQPNormalization.download(ValidationUI)
  ↓
Lấy workbook nguồn từ ValidationUI.rawWb hoặc currentWb
  ↓
Gọi buildWorkbook(sourceWb, templateBytes)
  ↓
Điều chỉnh: bỏ sheet "Đối chiếu nguồn" và các sheet thẩm định
  ↓
Tạo Blob từ workbook đầu ra
  ↓
Trigger download với tên file: {tên_gốc}_chuan_hoa_BQP.xlsx
```

### 3.2. Implementation chi tiết

#### Bước 1: Export hàm `download` trong bqp_normalization.js

**Vị trí cần sửa:** [bqp_normalization.js:547-575](src/main/resources/static/bqp_normalization.js:547-575)

```js
// Thêm vào phần export
async function download(validationUI) {
    // 1. Kiểm tra có workbook nguồn không
    const sourceWb = validationUI.rawWb || validationUI.currentWb;
    if (!sourceWb) {
        alert('Chưa có file dữ liệu để chuẩn hóa. Vui lòng import file trước.');
        return;
    }

    // 2. Lấy template bytes
    const templateBytes = Uint8Array.from(atob(EMBEDDED_TEMPLATE_B64), c => c.charCodeAt(0));

    // 3. Build workbook chuẩn hóa
    const normalizedWb = await buildWorkbook(sourceWb, templateBytes);

    // 4. Bỏ sheet "Đối chiếu nguồn" nếu có
    const auditSheet = normalizedWb.getWorksheet('Đối chiếu nguồn');
    if (auditSheet) {
        normalizedWb.removeWorksheet(auditSheet.id);
    }

    // 5. Xuất file
    const buffer = await normalizedWb.xlsx.writeBuffer();
    const blob = new Blob([buffer], { 
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' 
    });

    // 6. Tên file xuất
    const originalName = validationUI.currentFile?.name || 'du_lieu';
    const baseName = originalName.replace(/\.[^.]+$/, ''); // bỏ extension
    const fileName = `${baseName}_chuan_hoa_BQP.xlsx`;

    // 7. Trigger download
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);

    console.log('Đã tải file chuẩn hóa:', fileName);
}

// Export
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { sheetType, findColMap, buildWorkbook, download };
} else {
    window.BQPNormalization = { sheetType, findColMap, buildWorkbook, download };
}
```

#### Bước 2: Xử lý sheet "Đối chiếu nguồn" trong buildWorkbook

**Tùy chọn A:** Không tạo sheet này khi build workbook cho chức năng chuẩn hóa
- Thêm tham số `options = { includeAuditSheet: false }` vào `buildWorkbook`
- Kiểm tra flag trước khi tạo sheet audit (line 246-247)

**Tùy chọn B:** Tạo rồi xóa sau (như đã làm ở Bước 1)
- Đơn giản hơn, không cần sửa signature của `buildWorkbook`
- Phù hợp nếu sau này có trường hợp khác cần sheet audit

→ **Khuyến nghị:** Dùng tùy chọn B (xóa sau) để không làm phức tạp `buildWorkbook`.

#### Bước 3: Kiểm tra và điều chỉnh các sheet được giữ lại

**Hiện tại `buildWorkbook` tạo:**
- Các sheet chi tiết: I.1, I.2, I.3, I.4, I.5 (line 271)
- Sheet tổng hợp: "Phụ lục I" (line 452-539)
- Sheet "Đối chiếu nguồn" (line 246-247)

**File chuẩn hóa cần:**
- Các sheet chi tiết: ✓
- Sheet tổng hợp: **cần xác nhận** — có công thức tổng hay chỉ là dữ liệu kê khai?
- Sheet "Đối chiếu nguồn": ✗ (xóa)

### 3.3. Xử lý tên file

**Quy tắc đề xuất:**
```
{tên_file_gốc_bỏ_extension}_chuan_hoa_BQP.xlsx
```

**Ví dụ:**
- Input: `13. Phụ lục QK5.xlsx`
- Output: `13. Phụ lục QK5_chuan_hoa_BQP.xlsx`

---

## 4. Các điểm cần quyết định

### 4.1. Formula vs Value

**Vấn đề:** Ô công thức trong file nguồn xử lý như thế nào?

**Tùy chọn:**
1. Giữ công thức và cached result (nếu có)
2. Xuất giá trị hiện có, bỏ công thức
3. Xuất công thức nhưng cần recalculate bằng LibreOffice

**Hành vi hiện tại:** Hàm `value()` (line 8-30) trả marker lỗi khi thiếu kết quả cached.

**Khuyến nghị:** Xuất giá trị (tùy chọn 2) — đơn giản nhất, tránh lỗi công thức khi mở file.

### 4.2. Merged cells

**Vấn đề:** Xử lý merge ở hàng tiêu đề, ghi chú, dòng nhóm như thế nào?

**Hành vi hiện tại:**
- `findColMap` bỏ các ô merge không phải ô master (line 154-169)
- `isNoteRow` dùng trạng thái merge (line 133-137)

**Khuyến nghị:** Giữ nguyên merge từ template, chỉ ghi dữ liệu vào ô master của các merged range.

### 4.3. Sheet tổng hợp

**Vấn đề:** Sheet "Phụ lục I" (tổng hợp) có cần không?

**Trong file chuẩn hóa:**
- Nếu chỉ cần dữ liệu kê khai → có thể bỏ sheet tổng hợp
- Nếu cần cấu trúc hoàn chỉnh theo template BQP → giữ sheet tổng hợp với công thức tổng

**Khuyến nghị:** **Giữ sheet tổng hợp** để file chuẩn hóa đầy đủ như template BQP, nhưng cần xác minh với người dùng.

### 4.4. Multiple sheets cùng loại

**Vấn đề:** Nếu file nguồn có nhiều sheet I.1 hoặc tên trùng loại?

**Hành vi hiện tại:** Tìm sheet nguồn đầu tiên có cùng loại (line 271).

**Khuyến nghị:** Cảnh báo nếu phát hiện nhiều sheet cùng loại; xử lý sheet đầu tiên hoặc gộp nếu cần.

### 4.5. Dòng ngoài mẫu

**Vấn đề:** Giữ hay bỏ dòng ghi chú, tổng cộng, nhóm đơn vị/chính sách?

**Hành vi hiện tại:** Các dòng này được ghi sang audit hoặc bỏ (line 304-307, 541-545).

**Khuyến nghị:** 
- Giữ dòng ghi chú và nhóm đơn vị (để giữ cấu trúc nguồn)
- Bỏ dòng tổng cộng (sẽ được tính lại bằng công thức nếu cần)

---

## 5. Các bước triển khai

### Giai đoạn 1 — Xác nhận yêu cầu với người dùng

**Cần xác nhận:**
1. Sheet tổng hợp có cần không?
2. Giữ công thức hay xuất giá trị?
3. Xử lý dòng ghi chú/nhóm đơn vị như thế nào?

**Đầu ra:** Quy tắc rõ ràng về nội dung file chuẩn hóa.

### Giai đoạn 2 — Implement hàm download

1. Thêm hàm `download(validationUI)` vào `bqp_normalization.js`
2. Export hàm này trong phần export cuối file (line 574)
3. Test handler được gọi đúng khi bấm nút

**File cần sửa:**
- [bqp_normalization.js](src/main/resources/static/bqp_normalization.js) (thêm hàm + export)

### Giai đoạn 3 — Điều chỉnh buildWorkbook

1. Xóa sheet "Đối chiếu nguồn" sau khi build
2. Kiểm tra sheet tổng hợp có đúng dữ liệu không
3. Xử lý công thức theo quyết định Giai đoạn 1

**File cần sửa:**
- [bqp_normalization.js](src/main/resources/static/bqp_normalization.js) (logic trong `download`)

### Giai đoạn 4 — Test và xác minh

1. Import file phụ lục mẫu (ví dụ: `13. Phụ lục QK5.xlsx`)
2. Bấm nút "Tải file chuẩn hóa"
3. Mở file tải về, kiểm tra:
   - Có đúng các sheet chi tiết I.1, I.2, I.3... không
   - Có sheet tổng hợp không (nếu cần giữ)
   - Không có sheet "Đối chiếu nguồn"
   - Không có sheet thẩm định (đạt/thiếu/thừa)
   - Dữ liệu kê khai đúng với file nguồn
   - Layout/format theo template BQP
4. So sánh với template gốc `PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx`

### Giai đoạn 5 — Đồng bộ với bản inline trong index.html

**Lưu ý:** Nếu có bản sao logic trong `index.html` (tương tự `exportValidatedWorkbook`), cần đồng bộ.

**Kiểm tra:**
- Tìm trong `index.html` xem có bản inline của `BQPNormalization` không
- Nếu có, áp dụng thay đổi tương tự

---

## 6. Xác minh hoàn thành

- [ ] Nút "Tải file chuẩn hóa" gọi được handler mà không lỗi
- [ ] File tải về có đúng tên `{tên_gốc}_chuan_hoa_BQP.xlsx`
- [ ] Mở được bằng Excel/LibreOffice, không lỗi format
- [ ] Có đúng các sheet theo template BQP
- [ ] Không có sheet "Đối chiếu nguồn", sheet thẩm định
- [ ] Dữ liệu kê khai khớp với file nguồn
- [ ] Layout/format giống template BQP (merge, border, font)
- [ ] Test với nhiều file nguồn khác nhau (QK5, BTL Hà Nội...)
- [ ] Xử lý đúng trường hợp chưa import file (hiện alert)

---

## 7. Rủi ro và giảm thiểu

| Rủi ro | Khả năng | Tác động | Giảm thiểu |
|--------|----------|----------|------------|
| Template không khớp cấu trúc file nguồn | Trung bình | Cao | Test với nhiều file nguồn khác nhau |
| Mất dữ liệu khi chuyển đổi công thức | Thấp | Cao | Xuất giá trị thay vì công thức |
| Sheet tổng hợp có công thức sai | Trung bình | Trung bình | Xác minh công thức với nghiệp vụ |
| Bản inline trong index.html bị lệch | Cao | Cao | Kiểm tra và đồng bộ ngay |

---

## 8. Ghi chú

- Chức năng này **độc lập** với xuất báo cáo thẩm định (task #6)
- Có thể triển khai song song với task #6
- Template BQP được dùng cho cả hai chức năng, nhưng xử lý khác nhau:
  - **Báo cáo thẩm định:** Có sheet đạt/thiếu/thừa, giá trị tính lại, chênh lệch
  - **File chuẩn hóa:** Chỉ dữ liệu kê khai theo format template
