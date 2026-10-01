# Kế hoạch: Dùng template BQP cho mọi lần xuất báo cáo thẩm định

## Bối cảnh

Người dùng yêu cầu:
1. **Mọi lần xuất báo cáo** (không chỉ cấp 1) phải dùng file mẫu `PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx` làm template chính.
2. **Xóa toàn bộ dữ liệu mẫu** (ví dụ "Nguyễn Văn A", "Đơn vị 1"...) trong template trước khi điền dữ liệu thẩm định thực.
3. **Giữ nguyên layout chuẩn BQP**: merge cells, border, font, alignment, column width của template.
4. Điền dữ liệu thẩm định đúng vào các sheet tương ứng (I.1, I.2, I.3, II, III, IV) theo cấu trúc template.

Hiện tại luồng xuất **clone file đầu vào** (file người dùng upload) làm nền và tạo lại các sheet tổng hợp bằng hardcode. Cần đổi thành: **clone template BQP** làm workbook đầu ra, xóa dữ liệu mẫu, giữ format và điền dữ liệu mới.

Toàn bộ tính năng xuất là **JavaScript thuần** (ExcelJS), không qua Java.

---

## Phân tích hiện trạng

### Luồng xuất
```
ExportManager.exportToExcel() [index.html:8594]
  → exportValidatedExcel() [index.html:8674]
    → xây exportContext { summaryLevel: 2 } khi chọn Cấp 1 + scope='branch'
    → BQPValidation.exportValidatedWorkbook(baseWb, baseFileName, records, isFiltered, suffix, allRecords, exportContext)
      [bqp_validation.js:2496, index.html:12649 - bản sao inline]
```

### Tên file hiện tại (`bqp_validation.js:3701-3704`)
```js
let fileName = (isFiltered && recordsToExport.length < allRecs.length)
    ? `${baseName}_Tham_dinh_${suffix}_${recordsToExport.length}dc.xlsx`
    : `${baseName}_tham_dinh_BQP.xlsx`;
```
- `baseName` = tên file input bỏ extension.
- Không phân biệt xuất cấp 1 / cấp 2 / toàn bộ.

### Border hàng dữ liệu (`bqp_validation.js:2978-2988`)
```js
if (!cell.border) {   // Chỉ gán khi cell chưa có border
    cell.border = {
        top: { style: 'thin', color: { argb: 'FFE2E8F0' } }, ...
    };
}
```
- Nếu dòng đến từ template đã có `border` object (kể cả `{}`), điều kiện `false` → border không được đặt lại.
- Sheet tổng hợp `buildSummarySheet:3416` gán **vô điều kiện** → không bị lỗi.
- Sheet chi tiết I.x, II.x dùng `if (!cell.border)` → **tiềm ẩn thiếu border** khi template có border cũ.

### File mẫu được yêu cầu sử dụng

Dùng làm template chính file nằm sẵn trong dự án:
`src/main/resources/static/PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx`

Danh sách candidate hiện tại trong `bqp_validation.js` và bản inline `index.html` **không có đúng tên file này**: candidate đầu tiên ghi `...CUC TAI CHINH.xlsx`, nên không khớp file BQP; loader hiện có thể rơi xuống `26.9.PHU_LUC_SUA.xlsx`. Cập nhật loader để URL-encode và tải đúng file BQP này làm lựa chọn ưu tiên đầu tiên. Giữ các template fallback hiện có nếu file BQP không tải được.

Trước khi thay đổi, kiểm tra sheet names, cột và định dạng của file BQP để xác nhận nó tương thích với `exportValidatedWorkbook` và parser. Không suy ra cấu trúc chỉ từ tên file. Đồng bộ candidate và `baseFileName` trong cả `bqp_validation.js` và `index.html`.

---

## Thay đổi đề xuất

### Phạm vi đã xác nhận

- Template BQP áp dụng cho **tất cả** lần xuất báo cáo.
- Xóa mọi dữ liệu mẫu, giữ cấu trúc/định dạng; điền dữ liệu thật theo sheet/cột hiện có.
- Nếu template không tải được hoặc thiếu sheet/cột cần thiết, dừng export với thông báo lỗi rõ ràng; không âm thầm dùng workbook đầu vào làm template.

### Luồng xử lý cần triển khai

1. Tải/giữ workbook BQP đúng một lần qua loader trong `index.html`; sửa mọi liên kết tải template đang còn trỏ nhầm tên Cục Tài chính. Gắn cờ template rõ ràng để export không nhầm workbook upload của người dùng với template.
2. Trong `exportValidatedWorkbook`, khởi tạo workbook đầu ra từ template BQP, không clone `baseWb` làm layout. Giữ `baseWb` chỉ làm nguồn dữ liệu record/đối chiếu khi cần.
3. Xóa dữ liệu ví dụ trong vùng dữ liệu của các sheet chi tiết và sheet tổng hợp, nhưng giữ header, merge, style, hàng nhóm đơn vị, công thức/định dạng của template. Xác định chính xác ranh giới header và data row bằng `findColMap`/sheet type; không xóa cứng từ một số hàng chung cho mọi sheet.
4. Điền hồ sơ thật vào I.1/I.2/I.3 (và I.5 nếu hiện hỗ trợ), sử dụng `populateChildSheet`/`applyRecordToRow` cùng `findColMap`; các hàng mới phải được tạo từ style của hàng mẫu trước khi ghi. Gỡ hoàn toàn tên/giá trị mẫu.
5. Với các sheet tổng hợp I/II/III/IV, dùng sheet mẫu tương ứng nếu có; cập nhật `buildSummarySheet` để ghi vào sheet template đã tồn tại, không xóa sheet rồi dựng header/merge bằng hardcode. Giữ cách phân nhóm đạt chuẩn/cấp thừa/cấp thiếu hiện hành và chỉ thay vùng dữ liệu.
6. Với II.x/III.x/IV.x, giữ sheet template sẵn có, xóa data mẫu rồi điền nhóm record tương ứng; chỉ tạo sheet nếu template thực sự không có, sao chép đầy đủ cấu trúc/style từ sheet detail tương ứng.
7. Đồng bộ thay đổi ở `bqp_validation.js` và bản inline trong `index.html`, tránh hai engine xuất khác nhau.

### Kiểm tra tương thích trước khi ghi mã

Workbook BQP đã xác minh có các sheet I.1/I.2/I.3 với header hàng 6–9 và data mẫu từ hàng 10; sheet tổng hợp II có header hàng 1–7 và data mẫu từ hàng 8. Bảng chi tiết có 23/18/15 cột tương ứng; vẫn cần xác minh đầy đủ template có đủ các sheet I/II/III/IV và II.x/III.x/IV.x, header recognition, các vùng merge và tổng dòng trước khi triển khai logic xóa/điền.



### Thay đổi 1 — Sửa tên file xuất

**Vấn đề:** Tên không phân biệt xuất theo cấp; người dùng khó nhận ra file thuộc đơn vị nào.

**Đề xuất:** Khi `exportContext.selectedUnitId` tồn tại và `summaryLevel === 2` (xuất cấp 1), thêm nhãn đơn vị vào tên file. Ví dụ: `QuanKhu_I_tham_dinh_cap1.xlsx`.

```js
// Thay đoạn dòng 3701-3704:
let unitLabel = '';
if (exportContext?.summaryLevel === 2 && exportContext?.selectedUnitId) {
    // lấy tên đơn vị cấp 1 từ exportContext.units hoặc baseName
    unitLabel = '_cap1';
}
let fileName = (isFiltered && recordsToExport.length < allRecs.length)
    ? `${baseName}_Tham_dinh_${suffix}${unitLabel}_${recordsToExport.length}dc.xlsx`
    : `${baseName}_tham_dinh_BQP${unitLabel}.xlsx`;
```

Có thể bàn thêm quy ước tên cụ thể với người dùng.

### Thay đổi 2 — Sửa border hàng dữ liệu detail sheets

**Vấn đề:** `if (!cell.border)` bỏ qua border cũ từ template, có thể gây thiếu khung.

**Đề xuất:** Bỏ điều kiện `if`, luôn gán border:

```js
// bqp_validation.js dòng 2978-2988
for (let c = 1; c <= maxCol; c++) {
    const cell = row.getCell(c);
    cell.border = {           // Xóa `if (!cell.border)`
        top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
        right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
    };
}
```

### Thay đổi 3 — Dùng file mẫu BQP làm template chính

**Vấn đề:** File `PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx` có trong `static` nhưng **không được load** vì tên trong candidate list ghi sai (`CUC TAI CHINH` thay vì `BO QUOC PHONG`). Hiện tại `26.9.PHU_LUC_SUA.xlsx` mới được dùng thực tế.

**Đề xuất:** Đặt file BQP làm ưu tiên đầu tiên bằng cách thay candidate đầu tiên:

```js
// bqp_validation.js dòng ~8768 (index.html tương tự)
const candidates = [
    '/PHU%20LUC%20KEM%20THEO%20HUONG%20DAN%20CUA%20BO%20QUOC%20PHONG.xlsx',  // ← ưu tiên 1
    '/26.9.PHU_LUC_SUA.xlsx',
    '/8_phu_luc_btl_thu_do_ha_noi.xlsx',
    '/8.%20Phu%20luc%20BTL%20...xlsx'
];
```

Cập nhật đồng thời điều kiện đặt `baseFileName` để nhận ra tên file mới:
```js
baseFileName = url.includes('BO%20QUOC%20PHONG') || url.includes('PHU%20LUC')
    ? 'PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx'
    : '8. Phu luc BTL thủ đô Hà Nội.xlsx';
```

**Lưu ý trước khi thực hiện:** Đọc sheet names và cấu trúc của file BQP bằng `openpyxl` để xác nhận tương thích với parser và `exportValidatedWorkbook`. Nếu cấu trúc sheet/cột khác `26.9.PHU_LUC_SUA.xlsx`, cần kiểm tra lại logic parser nhận diện mẫu (`bqp_validation.js:1368`).

---

## Files cần sửa

| File | Vị trí | Nội dung thay đổi |
|---|---|---|
| `src/main/resources/static/bqp_validation.js` | Dòng 3701-3704 | Tên file xuất — thêm nhãn cấp |
| `src/main/resources/static/bqp_validation.js` | Dòng 2978-2988 | Border hàng data — xóa `if (!cell.border)` |
| `src/main/resources/static/bqp_validation.js` | Dòng 3703 + candidates | Thêm tên file mẫu BQP vào danh sách |
| `src/main/resources/static/index.html` | Tương đương với 3 dòng trên (bản inline) | **Đồng bộ y hệt**, cần tìm đúng dòng tương ứng |

> **Quan trọng:** `bqp_validation.js` và `index.html` có bản sao logic xuất gần như giống hệt nhau. Mọi thay đổi phải được áp dụng cho **cả hai** để không bị lệch.

---


---

## Lưu ý

- Không sửa logic thẩm định hoặc các calculator Java. Phạm vi chỉ là phần tạo/format file Excel xuất.
- Số `(10)` trong tên file hiện tại là suffix Windows browser, **không phải** do code — không cần xử lý ở phía code.
- Cần xác nhận với người dùng quy ước tên file cụ thể mong muốn (ví dụ có muốn tên đơn vị đầy đủ hay rút gọn).
