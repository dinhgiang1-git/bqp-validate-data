# Kế hoạch: Phân biệt đơn vị cấp 2 trong báo cáo xuất cấp 1

**Ngày lập:** 01/10/2026  
**Phạm vi:** Sheet chi tiết I.1/I.2/I.3 trong file xuất báo cáo cấp 1

---

## 1. Mục tiêu

Khi xuất báo cáo cấp 1, người đọc nhìn vào sheet chi tiết (I.1, I.2, I.3) phải biết mỗi nhóm đơn vị cấp 3 thuộc đơn vị cấp 2 nào — tương tự như cách file hiện tại đã phân biệt cấp 3 bằng dòng tiêu đề đơn vị.

---

## 2. Phân tích hiện trạng

### 2.1. Luồng tạo nhóm dòng trong sheet chi tiết

**Vị trí:** [bqp_validation.js:3032-3078](src/main/resources/static/bqp_validation.js:3032-3078)

Luồng hiện tại xây dựng các nhóm (`descriptors`) theo thứ tự:

```
1. Dòng phân loại chính sách  (178 / 177)         ← layout.bands
2. Dòng phân loại quân nhân   (Sĩ quan / QNCN)    ← layout.categories
3. Dòng tiêu đề đơn vị cấp 3  (unitStyle)          ← layout.unitStyle
4. Dòng dữ liệu cá nhân                            ← layout.dataStyle
```

Key nhóm tại dòng 3042:
```js
const key = policy + '\u0000' + category + '\u0000' + unit;
```

Trong đó `unit` = tên đơn vị cấp 3 từ `rec.group / rec.donVi / rec.unitName`.

**Nhận xét:** Không có thông tin cấp 2 nào trong key hoặc trong descriptor — cấp 3 từ các đơn vị cấp 2 khác nhau bị trộn lẫn.

### 2.2. `resolveUnit` đã có thông tin cấp 2

**Vị trí:** [bqp_validation.js:2406-2485](src/main/resources/static/bqp_validation.js:2406-2485)

Hàm `resolveUnit(unitId)` trả về:
```js
{
  ok: true,
  l2Unit: { id, name, displayOrder }   // ← đơn vị cấp 2
}
```

Thông tin cấp 2 đã có, nhưng **chỉ được dùng trong sheet tổng hợp** (tổng tiền theo cấp 2), chưa được đưa vào sheet chi tiết.

### 2.3. Kết quả hiện tại

Ví dụ file QK5 có cây:
```
QK5 (cấp 1)
├── Sư đoàn 306 (cấp 2)
│   ├── Trung đoàn 12 (cấp 3)
│   └── Trung đoàn 34 (cấp 3)
└── Lữ đoàn 543 (cấp 2)
    ├── Tiểu đoàn 1 (cấp 3)
    └── Tiểu đoàn 2 (cấp 3)
```

Sheet I.1 hiện tại:
```
[178]
  [Sĩ quan]
    I. Trung đoàn 12
       dữ liệu...
    II. Trung đoàn 34
       dữ liệu...
    III. Tiểu đoàn 1
       dữ liệu...
    IV. Tiểu đoàn 2
       dữ liệu...
```

→ **Không biết Trung đoàn 12, 34 thuộc Sư đoàn 306 và Tiểu đoàn 1, 2 thuộc Lữ đoàn 543.**

---

## 3. Thiết kế giải pháp

### 3.1. Cấu trúc nhóm mới

Thêm một cấp phân nhóm theo đơn vị cấp 2 ngay trước dòng tiêu đề cấp 3:

```
[178]
  [Sĩ quan]
    === Sư đoàn 306 ===          ← THÊM MỚI: dòng tiêu đề đơn vị cấp 2
      I. Trung đoàn 12
         dữ liệu...
      II. Trung đoàn 34
         dữ liệu...
    === Lữ đoàn 543 ===          ← THÊM MỚI: dòng tiêu đề đơn vị cấp 2
      III. Tiểu đoàn 1
         dữ liệu...
      IV. Tiểu đoàn 2
         dữ liệu...
```

### 3.2. Sửa key nhóm

**Hiện tại:**
```js
const key = policy + '\u0000' + category + '\u0000' + unit;
```

**Đề xuất:**
```js
// Khi xuất cấp 1 (resolver có sẵn), lấy thêm l2Name
let l2Name = '';
if (resolver) {
    const res = resolver.resolveUnit(rec.unitId);
    if (res.ok && res.l2Unit) l2Name = res.l2Unit.name;
}
const key = policy + '\u0000' + category + '\u0000' + l2Name + '\u0000' + unit;
```

Gắn thêm `l2Name` vào bucket để dùng khi tạo descriptor:
```js
if (!buckets.has(key)) {
    buckets.set(key, { policy, category, l2Name, l2Order: l2.displayOrder, unit, records: [] });
}
```

### 3.3. Thêm descriptor cho dòng cấp 2

Trong vòng lặp xây `descriptors`, theo dõi `previousL2` tương tự cách theo dõi `previousCategory`:

```js
let previousCategory = null, previousPolicy = null, previousL2 = null, unitNumber = 0;

// Sắp xếp theo l2Order rồi mới đến tên đơn vị cấp 3
const sortedBuckets = [...buckets.values()].sort((a, b) =>
    b.policy.localeCompare(a.policy) ||
    (a.category === 'SQ' ? 0 : 1) - (b.category === 'SQ' ? 0 : 1) ||
    (a.l2Order - b.l2Order) ||
    String(a.l2Name || '').localeCompare(String(b.l2Name || ''), 'vi')
);

for (const bucket of sortedBuckets) {
    // ... xử lý bands, categories như hiện tại ...

    // THÊM: Dòng tiêu đề cấp 2 khi đổi sang cấp 2 mới
    if (resolver && bucket.l2Name && previousL2 !== bucket.l2Name) {
        descriptors.push({
            prototype: layout.l2Style || layout.unitStyle,   // dùng style riêng hoặc fallback
            l2: bucket.l2Name,
            isL2Header: true
        });
        previousL2 = bucket.l2Name;
        unitNumber = 0;   // reset đánh số đơn vị cấp 3
    }

    // Dòng tiêu đề cấp 3 (đã có)
    if (layout.unitStyle) {
        descriptors.push({ prototype: layout.unitStyle, unit: bucket.unit, number: roman(++unitNumber) });
    }
    bucket.records.forEach(rec => descriptors.push({ prototype: layout.dataStyle, rec }));
}
```

### 3.4. Render dòng tiêu đề cấp 2

Trong đoạn `descriptors.forEach(...)` (dòng 3098), thêm nhánh xử lý `isL2Header`:

```js
descriptors.forEach((descriptor, i) => {
    const row = ws.getRow(layout.dataStart + i);
    applyTemplateRow(row, descriptor.prototype, maxCol);

    if (descriptor.isL2Header) {
        // Ghi tên đơn vị cấp 2 vào cột đầu (merge hoặc cột 1)
        row.getCell(1).value = descriptor.l2;
        // Style: bold, fill xanh nhạt để phân biệt với cấp 3
        row.getCell(1).font = { name: 'Times New Roman', size: 12, bold: true, italic: true };
        row.getCell(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFDBE5F1' } };
        // Merge từ cột 1 đến cột cuối nếu cần
        if (maxCol > 1) {
            row.mergeCells(layout.dataStart + i, 1, layout.dataStart + i, maxCol);
        }
        row.alignment = { horizontal: 'center', vertical: 'middle' };
        return; // bỏ qua xử lý dữ liệu thông thường
    }

    // ... logic hiện tại cho category, band, unit, rec
});
```

### 3.5. Trường hợp không có resolver (xuất không phải cấp 1)

Giữ nguyên hành vi hiện tại — không thêm dòng cấp 2 khi `resolver === null`.

### 3.6. Fallback khi không tìm được cấp 2

Nếu `resolver.resolveUnit` trả về `ok: false` (hồ sơ không gắn đơn vị, hoặc unitId không hợp lệ), bỏ qua dòng tiêu đề cấp 2 cho bucket đó và tiếp tục bình thường.

---

## 4. Ảnh hưởng đến sheet tổng hợp (Phụ lục II/III/IV)

Sheet tổng hợp **đã phân nhóm theo cấp 2** (dòng 3183-3254 trong `buildSummarySheet`) nên không cần thay đổi thêm.

---

## 5. Files cần sửa

| File | Vị trí | Nội dung thay đổi |
|------|--------|------------------|
| `bqp_validation.js` | dòng 3032-3078 | Thêm `l2Name` vào key, thêm `previousL2`, thêm descriptor `isL2Header` |
| `bqp_validation.js` | dòng 3098-3200 | Xử lý render descriptor `isL2Header` |
| `index.html` | tương đương trên | Đồng bộ y hệt |

---

## 6. Kế hoạch triển khai

### Giai đoạn 1 — Sửa `bqp_validation.js`

1. Sửa đoạn tạo `buckets` để thêm `l2Name` và `l2Order` khi `resolver` có sẵn.
2. Sửa vòng lặp `sortedBuckets` để sắp xếp thêm theo `l2Order`.
3. Thêm xử lý `previousL2` trong vòng lặp xây `descriptors`.
4. Thêm nhánh `isL2Header` trong vòng lặp render `descriptors`.

### Giai đoạn 2 — Đồng bộ `index.html`

Tìm đoạn tương đương trong `index.html` và áp dụng thay đổi y hệt.

### Giai đoạn 3 — Test

1. Import file phụ lục QK5 (có cây 3 cấp rõ ràng).
2. Chọn xuất báo cáo cấp 1, chọn QK5.
3. Mở file xuất, kiểm tra sheet I.1, I.2, I.3:
   - Có dòng tiêu đề cấp 2 trước mỗi nhóm cấp 3 không?
   - Đánh số cấp 3 reset về I sau mỗi cấp 2 không?
   - Không bị lỗi merge hoặc dòng trống thừa không?
4. Test với file không có cây cấp 2 (xuất thường) — đảm bảo không thay đổi hành vi.
5. Test trường hợp hồ sơ thiếu unitId — không crash.

---

## 7. Tiêu chí hoàn thành

- [ ] Sheet I.1/I.2/I.3 có dòng tiêu đề đơn vị cấp 2 trước mỗi nhóm cấp 3.
- [ ] Dòng tiêu đề cấp 2 có format phân biệt rõ (bold, fill xanh nhạt, căn giữa).
- [ ] Thứ tự: cấp 2 theo `displayOrder`, rồi cấp 3 theo tên.
- [ ] Đánh số cấp 3 reset theo từng cấp 2.
- [ ] Xuất báo cáo thường (không phải cấp 1) không bị ảnh hưởng.
- [ ] Không crash khi hồ sơ thiếu unitId hoặc không resolve được cấp 2.
- [ ] Logic đồng bộ giữa `bqp_validation.js` và `index.html`.

---

## 8. Lưu ý

- Nếu template BQP có sẵn kiểu dòng cấp 2 trong layout (`layout.l2Style`), dùng style đó thay vì hardcode — cần kiểm tra khi đọc template.
- Nếu không có `layout.l2Style`, dùng `layout.unitStyle` làm base rồi override màu/font để phân biệt.
- Sau khi thêm dòng cấp 2, tổng số dòng trong sheet tăng — đảm bảo `prepareTemplateBody` được gọi với số đúng (descriptors.length đã bao gồm dòng l2 rồi).
