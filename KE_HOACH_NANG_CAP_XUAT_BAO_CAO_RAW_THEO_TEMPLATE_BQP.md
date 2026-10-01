# Kế hoạch nâng cấp xuất báo cáo RAW theo mẫu BQP

**Ngày lập:** 01/10/2026  
**Dự án:** `D:\bqp\bqp-validate-data`  
**Trạng thái:** Kế hoạch triển khai; chưa sửa chức năng xuất trong bước này.

## 1. Mục tiêu và phạm vi

Nâng cấp nút **Xuất Excel chưa thẩm định (RAW)** tại danh sách hồ sơ nhập từ Excel: dùng mẫu BQP, bố cục, tổng hợp và cách phân nhóm đơn vị tương tự báo cáo thẩm định đã nâng cấp. Nội dung chỉ lấy dữ liệu kê khai gốc đã lưu, nguồn `excel`.

Yêu cầu trực tiếp của người dùng là lập kế hoạch nâng cấp RAW, tương tự thẩm định nhưng chỉ có dữ liệu thô. Các quyết định kỹ thuật dưới đây là phương án đề xuất để triển khai yêu cầu đó; không mở rộng kế hoạch cũ sang tính lại hoặc đối chiếu RAW.

Kết quả dự kiến:

- Workbook có sheet tổng hợp **I** và các sheet chi tiết **I.1–I.5**, giữ tên sheet theo mẫu BQP. Giữ các sheet chi tiết rỗng để bộ báo cáo có cấu trúc thống nhất; xóa dữ liệu ví dụ trong mọi sheet được giữ.
- Phân nhóm theo chính sách, đối tượng và cây đơn vị dựa trên thông tin nguồn đã lưu.
- Chọn đơn vị cấp 1 với phạm vi nhánh: tổng hợp theo cấp 2, chi tiết thể hiện quan hệ cấp 2/cấp 3 như luồng thẩm định.
- Tiền và các giá trị cá nhân là giá trị gốc; chỉ cộng số liệu nguồn để lập tổng hợp, không tính lại chế độ.
- Loại bỏ sheet **II/III/IV** và các phụ lục con vì chúng phục vụ phân loại kết quả thẩm định. Không thêm tiền tính lại, chênh lệch, kết luận đạt/thừa/thiếu, gạch ngang giá trị hoặc ghi chú thẩm định.

RAW là dữ liệu gốc đã đọc và lưu trong hệ thống. Phạm vi này không bao gồm khôi phục toàn bộ file upload, công thức hoặc định dạng riêng của file nguồn. Không thay đổi nghiệp vụ thẩm định, quy trình lưu đôi, schema SQLite hoặc tính toán Java.

## 2. Hiện trạng đã kiểm tra

| Thành phần | Hiện trạng | Việc cần làm |
|---|---|---|
| `ExportManager.exportRawExcel()` | Lấy nguồn `excel`, bỏ lọc trạng thái, xuất bằng SheetJS | Đổi sang ExcelJS và loader mẫu BQP; quản lý toàn bộ thao tác bằng `try/catch/finally` |
| `buildRawWorkbook()` | Dựng bảng mới, chỉ tạo sheet có hồ sơ; không có tổng hợp I | Tạo workbook từ mẫu, xóa dữ liệu ví dụ, điền RAW và tổng hợp |
| `_buildRawSheetData()` | Header cứng; I.4 dùng cấu trúc gần I.1, I.5 rút gọn; thêm cột đơn vị/ngày lưu | Thay bằng mapper riêng cho từng phụ lục, đúng schema mẫu |
| `normalizeRawRecord()` | Có thể biến ô trống thành 0; chưa giữ đầy đủ metadata nhóm | Bổ sung adapter RAW có quy tắc ưu tiên nguồn và phân biệt thiếu/trống/0 |
| `exportToExcel()` | Nhánh ngoài `validated` chỉ duyệt I.1/I.2/I.3 | Khi nguồn là `excel`, chuyển tới cùng luồng RAW mới |
| `fetchAllMatchingRecords()` | SQLite dùng export session, kiểm tra số lượng/ID; LocalStorage có lọc cơ bản | Giữ session và lấy toàn bộ hồ sơ phù hợp; kiểm tra tính tương đương bộ lọc giữa hai chế độ |
| `BQPReportTemplate` | Đã có loader, cache, xác minh template và tạo export context | Dùng lại cho RAW, bổ sung kiểm tra schema I.4/I.5 cần thiết |
| Engine thẩm định | Đã giữ style/merge, dịch footer, làm tổng hợp và nhóm đơn vị | Chỉ dùng chung hạ tầng trình bày trung lập; RAW có writer dữ liệu riêng |

Template chính: `src/main/resources/static/PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx`.

Các helper bố cục hiện nằm bên trong `BQPValidation.exportValidatedWorkbook()`. Không gọi hàm xuất thẩm định rồi xóa cột so sánh; cách đó vẫn có thể tính lại, phân loại hoặc dùng nhầm giá trị thẩm định trước khi ghi file.

## 3. Quy tắc dữ liệu RAW

### 3.1. Nguồn và thứ tự ưu tiên

1. Chỉ nhận hồ sơ `source = 'excel'`.
2. Dùng `rawCols` hoặc JSON cột nguồn đã lưu làm giá trị ưu tiên, qua ánh xạ schema của file nhập sang schema mẫu đích.
3. Chỉ dùng trường kê khai trong `input`/`inputJson` và trường canonical RAW khi khóa nguồn thực sự không tồn tại. Khóa có giá trị `0`, `''` hoặc `null` không được tự thay bằng giá trị dự phòng.
4. Không lấy `expected`, `comparisons`, `validationSnapshot`, `calculatedTotal`, `difference` hoặc kết quả tính từ `resultJson` làm dữ liệu xuất. Có thể dùng `actualTotal` đã lưu làm dự phòng cho đúng cột tổng tiền I.1/I.2/I.3 khi nguồn cột đó không tồn tại.
5. Adapter tạo đối tượng mới, giữ `unitId`, `unitName`, `sheetType`, `categoryCode`, `categoryName`, `policyCode`, `importId`, `sourceRow` và thông tin nguồn có sẵn. Không sửa DTO, bản ghi đã lưu hoặc template cache.

Kiểm tra cả các alias LocalStorage và DTO SQLite. Nếu JSON nguồn hỏng hoặc schema không thể xác định, báo lỗi rõ ràng trước khi tải file; không âm thầm coi dữ liệu đó là bảng rỗng hoặc chuyển tất cả sang I.1.

### 3.2. Số, ngày và lỗi nguồn

- Giữ số 0 thật; giữ ô trống thật. Không dùng `value || 0` cho dữ liệu RAW.
- Không làm tròn hệ số, tỷ lệ, năm công tác hoặc giá trị nguồn khi xuất.
- Chỉ chuyển chuỗi sang số khi định dạng nguồn xác định rõ; hỗ trợ định dạng Việt Nam theo quy tắc parser nguồn. Chuỗi mơ hồ giữ nguyên, không xóa dấu phân cách rồi đoán số.
- Giữ độ chính xác ngày gốc, ví dụ `05/1980`; không tự bổ sung ngày hoặc dùng ngày hiện tại cho ô thiếu.
- Giữ chuỗi lỗi nguồn như `#DIV/0!` hoặc `[Lỗi ...]` ở đúng ô, bằng trình bày thông thường. Đây là giá trị nguồn, không phải kết luận thẩm định.
- Nếu nguồn lưu đối tượng công thức có kết quả cache, xuất giá trị cache đã lưu. Nếu không có cache, giữ thông tin không đọc được; không mang công thức tham chiếu workbook cũ sang bố cục mới.

Một số trường canonical hiện đã được làm tròn/chuyển ngày lúc lưu. Khi cột nguồn đầy đủ, ưu tiên cột nguồn để bảo toàn dữ liệu. Nếu dữ liệu lịch sử đã mất giá trị gốc, không thể phục hồi bằng tính toán; để trống trường không có nguồn và thông báo giới hạn dữ liệu khi cần.

### 3.3. Ánh xạ từng phụ lục

| Sheet | Số cột nghiệp vụ của mẫu | Cách xử lý |
|---|---:|---|
| I.1 | 23 | Điền dữ liệu nguồn; cột 23 là tổng tiền kê khai |
| I.2 | 18 | Điền dữ liệu nguồn; cột 18 là tổng tiền kê khai |
| I.3 | 15 | Điền dữ liệu nguồn; cột 15 là tổng tiền kê khai |
| I.4 | 16 | Mapper riêng theo tiêu đề mẫu, không sử dụng cấu trúc tiền của I.1 |
| I.5 | 22 | Xuất đầy đủ bảng lương/hệ số/phụ cấp nguồn, không rút thành 8 cột |

Số cột trong bảng là số cột nghiệp vụ, không phải `worksheet.columnCount`, vì mẫu còn có cột ẩn hoặc cột định dạng ngoài bảng. Hàng đánh số cột của mẫu là căn cứ map đích; đặc biệt I.5 không dùng riêng heuristic đọc tiêu đề chữ vốn có thể đảo cột.

Trước khi viết mapper I.4/I.5, đối chiếu parser nhập, fixture nguồn thực và metadata đã lưu. Không mặc định cột thứ N của mọi biến thể file nhập có cùng ý nghĩa với cột N của mẫu đích. Trường hợp nguồn lịch sử không đủ thông tin để map đúng phải báo rõ hồ sơ/phụ lục liên quan, không ghi sai cột hoặc bỏ hồ sơ.

STT được đánh lại cho báo cáo; dòng đơn vị được tạo từ cây đơn vị. Không chèn thêm cột `Ngày lưu`, cột đối chiếu hoặc tiền tính lại vào bảng mẫu.

## 4. Bố cục và tổng hợp

### 4.1. Giữ mẫu BQP

- Clone workbook mẫu đã được xác minh, giữ merge, font, border, alignment, numFmt, độ rộng/cột ẩn, chiều cao và thiết lập in hiện có.
- Xóa tên người, đơn vị và số liệu ví dụ trong vùng thân bảng và các giá trị tổng mẫu. Giữ tiêu đề, nhãn nhóm chính thức và hướng dẫn tĩnh, đặc biệt phần hướng dẫn dưới bảng I.5.
- Dùng hàng mẫu đúng loại để tạo hàng chính sách, đối tượng, cấp 2, cấp 3, cá nhân và tổng cộng.
- Khi tăng số dòng, dịch footer, ghi chú, merge và các style tương ứng. Khi xuất ít dòng hoặc xuất lại, không còn hồ sơ từ lần trước.
- Không thêm màu lỗi, rich text đối chiếu hai giá trị hay cột ghi chú thẩm định. Rich text tĩnh của mẫu vẫn được giữ.
- Nếu template không tải được hoặc thiếu cấu trúc bắt buộc, dừng với thông báo rõ ràng. Chỉ dùng fallback đã được kiểm tra tương thích; không dùng workbook upload làm nền.

### 4.2. Phân nhóm đơn vị và đối tượng

- Key nhóm đơn vị dùng ID, không chỉ dùng tên. Hai đơn vị trùng tên thuộc hai nhánh phải được tách đúng.
- Với cấp 1 + `scope = 'branch'`: sheet I tổng hợp theo đơn vị cấp 2; chi tiết có dòng cấp 2 trước nhóm đơn vị trực thuộc. Giữ thứ tự cây đơn vị và quy ước đánh số tương tự xuất thẩm định.
- Với `scope = 'direct'`: chỉ hồ sơ trực thuộc đơn vị được chọn. Với đơn vị khác hoặc toàn bộ dữ liệu: nhóm theo cây và phạm vi đã chọn, không tự mở rộng tập hồ sơ.
- Chính sách và đối tượng lấy từ metadata nguồn; không suy lại từ kết luận thẩm định hoặc tự phân loại theo cấp bậc. Thiếu metadata nhóm thì đưa vào nhóm trung lập `Chưa có thông tin nhóm nguồn`, không mặc định thành SQ/ND178 trong adapter xuất.
- Nếu xuất tổng hợp cấp 1 mà không xác định được nhánh cấp 2 của hồ sơ, báo lỗi thiếu ánh xạ và dừng; không gộp nhầm. Ở chế độ không đòi hỏi nhánh cấp 2, giữ hồ sơ chưa có đơn vị trong nhóm `Chưa có thông tin đơn vị`.

### 4.3. Sheet tổng hợp I

Tổng hợp số hồ sơ và tổng tiền kê khai của I.1/I.2/I.3 theo đúng các cột mẫu I. Tổng tiền cá nhân lấy từ cột cuối đã kê khai, không cộng lại các thành phần để suy ra mức hưởng. I.4/I.5 được xuất đầy đủ nhưng không cộng thêm vào tổng tiền chế độ của I, tránh đếm trùng.

Quy tắc cộng:

- Đếm hồ sơ cá nhân, không đếm dòng chính sách/đơn vị hoặc cộng lại các dòng subtotal.
- Tổng cộng bằng tổng của các hồ sơ nguồn thuộc đúng phạm vi; dùng phép cộng thập phân phù hợp độ chính xác dữ liệu, không làm tròn từng người trước khi cộng.
- Nhóm không có hồ sơ có số lượng và tổng tiền bằng 0.
- Có hồ sơ nhưng giá trị tiền thiếu, lỗi hoặc không chuyển số chắc chắn: để trống ô tổng bị ảnh hưởng; ghi chú trung lập tại vùng ghi chú về số ô nguồn chưa đủ dữ liệu. Không xuất tổng một phần như tổng đầy đủ, không mặc định ô thiếu là 0.
- Footer chi tiết chỉ cộng các cột có ý nghĩa cộng theo schema; không tự cộng mọi hệ số, tỷ lệ hoặc thời gian. Tổng nguồn thiếu được xử lý như trên.
- Giữ đơn vị tiền đúng tiêu đề mẫu. Nếu cần đổi đơn vị trình bày, chỉ đổi sau khi cộng và kiểm thử hệ số đổi; không đổi giá trị gốc chi tiết.

## 5. Thiết kế triển khai

### 5.1. Hạ tầng dùng chung, writer RAW riêng

Trong `bqp_validation.js`, tách các helper trung lập phục vụ clone/style, nhận diện vùng bảng, xóa thân mẫu, giãn dòng, dịch merge/footer và phân giải cây đơn vị thành module dùng chung, dự kiến `BQPReportWorkbook`. Helper không gọi calculator, hydrate thẩm định hoặc phân loại đạt/thừa/thiếu.

Thêm API dự kiến:

```js
BQPValidation.exportRawWorkbook(templateWorkbook, rawRecords, exportContext)
```

API này dựng workbook RAW và trả workbook/kết quả cho lớp giao diện tải xuống; lớp giao diện chịu trách nhiệm thông báo. Engine thẩm định tiếp tục giữ contract hiện tại, có thể gọi helper trung lập sau khi kiểm thử hồi quy. Không refactor các quy tắc thẩm định ngoài phần cần dùng chung.

Luồng mới:

```text
Nút RAW / exportToExcel khi nguồn excel
  → chụp bộ lọc, luôn status = null
  → fetchAllMatchingRecords('excel', filters)
  → adapter RAW + kiểm tra schema nguồn
  → BQPReportTemplate.createExportContext(context)
  → exportRawWorkbook(template, records, context)
  → ExcelJS writeBuffer → tải file
```

Context chứa `source: 'excel'`, `reportMode: 'raw'`, `selectedUnitId`, `scope`, bản sao `filters`, bản sao `units` và `summaryLevel: 2` khi chọn cấp 1/phạm vi nhánh. Không đọc thêm trạng thái UI giữa chừng làm thay đổi phạm vi file đang xuất.

### 5.2. Tích hợp giao diện và truy xuất dữ liệu

- Cả hai điểm vào xuất nguồn `excel` gọi chung một luồng; không đi qua `_buildSheetData()` dành cho xuất chung hiện tại. Nguồn `manual` giữ hành vi hiện có.
- Kiểm tra thư viện theo nhánh: RAW/thẩm định cần ExcelJS; không chặn RAW chỉ vì thiếu SheetJS.
- Giữ bộ lọc đơn vị/phạm vi, phụ lục, tìm kiếm và đối tượng nếu UI hỗ trợ. SQLite và LocalStorage phải chọn cùng tập hồ sơ. Bỏ trạng thái thẩm định ở cả phía client và payload session RAW.
- Xuất toàn bộ hồ sơ phù hợp, không chỉ trang đang hiển thị. Giữ giới hạn 50.000 hồ sơ, cảnh báo bộ nhớ từ 20.000, kiểm tra số lượng/ID và đóng session trong `finally`.
- Không fallback sang LocalStorage khi SQLite lỗi; không tải file thiếu trang hoặc thiếu hồ sơ. Tập rỗng chỉ thông báo không có dữ liệu.
- Loader bao phủ nạp hồ sơ, tải mẫu, dựng workbook và serialize. Khóa nút khi đang xuất, mở lại khi hoàn tất hoặc lỗi; chỉ báo thành công sau khi tạo được file.
- Tên file đề xuất: `BQP_Chua_tham_dinh_RAW_<Don_vi>_<YYYY-MM-DD>_<N>hs.xlsx`; thêm nhãn `cap1` cho báo cáo cấp 1 theo nhánh. Chuẩn hóa ký tự tên file, dùng ngày địa phương thay cho cắt ngày UTC bằng `toISOString()`.

## 6. Các bước thực hiện

1. **Chốt fixture và contract:** đọc mẫu BQP thật, dữ liệu RAW SQLite/LocalStorage, schema I.4/I.5 và các biến thể nhập; thêm kiểm thử bảo toàn nguồn trước khi thay writer.
2. **Tách helper trình bày:** đưa phần clone/style/layout/giãn dòng trung lập ra module dùng chung. Chạy các kiểm thử xuất thẩm định hiện có để xác nhận kết quả không đổi.
3. **Adapter và mapper RAW:** bảo toàn metadata, blank/0/lỗi/độ chính xác; triển khai từng mapper I.1–I.5, kiểm tra thiếu schema và tính bất biến đầu vào.
4. **Writer chi tiết:** clone mẫu, giữ các sheet RAW, dọn ví dụ, tạo nhóm chính sách/đối tượng/đơn vị, ghi hồ sơ và footer nguồn.
5. **Tổng hợp I và cấp đơn vị:** cộng giá trị kê khai, xử lý tổng chưa đủ dữ liệu, tách đơn vị trùng tên và tổng hợp cấp 1 theo cấp 2.
6. **Nối UI:** thống nhất hai điểm vào, ExcelJS, context, tên file, bộ lọc/session và lifecycle loader/nút xuất.
7. **Hồi quy và nghiệm thu:** chạy suite JavaScript liên quan, mở file thực trong Excel, kiểm tra bố cục/in và đối chiếu dữ liệu từng phụ lục. Đồng bộ các bản phân phối từ nguồn chuẩn.

## 7. File dự kiến thay đổi khi triển khai

| File | Nội dung |
|---|---|
| `src/main/resources/static/bqp_validation.js` | Helper workbook trung lập, adapter/mapper và writer RAW |
| `src/main/resources/static/index.html` | ExportManager, tích hợp UI; đồng bộ nguyên bản engine inline |
| `src/test/js/test_export_raw_bqp_template.cjs` | Bộ kiểm thử RAW theo template thật, ghi/đọc lại XLSX |
| `src/test/js/test_dual_save_raw_export.cjs` | Thay kỳ vọng bảng RAW cũ; giữ kiểm thử lưu đôi và tách nguồn |
| `src/test/js/test_export_sqlite_lifecycle.cjs` | Bổ sung RAW session/filter/lifecycle khi cần |
| `src/test/js/bqp_export_test_helpers.cjs` | Fixture/helper dùng chung nếu cần |
| `D:\bqp\package.json` | Script `test:js:export:raw-bqp-template` và đưa vào suite |
| Bản JS/HTML phân phối và `target/classes/static` | Đồng bộ theo quy trình hiện có; kiểm tra parity |

Không sửa file mẫu để che lỗi mapper. Không coi việc đồng bộ file tĩnh là đã cập nhật JAR/EXE; nếu cần bản đóng gói mới thì thực hiện bước build riêng sau khi nghiệm thu mã nguồn.

## 8. Kiểm thử và tiêu chí nghiệm thu

### 8.1. Kiểm thử tự động

- Đủ I/I.1–I.5; không có II/III/IV hoặc sheet đối chiếu. Không còn tên/số liệu ví dụ trong thân và tổng bảng.
- Fixture thật cho cả năm phụ lục; xác nhận toàn bộ cột I.4/I.5 đúng nghĩa, không chỉ số lượng cột.
- Dữ liệu có tiền tính lại khác tiền gốc và snapshot/so sánh sẵn: file vẫn chỉ dùng tiền gốc. Spy xác nhận không gọi calculator, `ensureRecordValidationDetail()` hoặc hàm dựng comparisons.
- Phân biệt 0, ô trống, trường không tồn tại, chuỗi lỗi, số thập phân, định dạng Việt Nam và ngày tháng/năm. RawCols có blank/0 phải thắng canonical khác giá trị.
- Tổng nguồn đúng khi đầy đủ; tổng thiếu/lỗi không bị thành 0 hoặc tổng một phần. I.4/I.5 không làm tăng số hồ sơ/tổng tiền I.
- Cấp 1/cấp 2/cấp 3, direct/branch, đơn vị trùng tên khác ID, thiếu metadata nhóm và thiếu ánh xạ cấp 2.
- Ít dòng, vượt số hàng mẫu, xuất nhiều lần: merge/style/cột ẩn/footer/hướng dẫn I.5 giữ đúng sau ghi và đọc lại XLSX. Input và template cache không bị thay đổi.
- SQLite nhiều trang: đủ số lượng, không trùng ID, xóa session cả khi lỗi; LocalStorage có tập ID tương đương cùng bộ lọc. Status thẩm định không tác động RAW.
- Template lỗi, JSON hỏng, sheet nguồn không nhận diện, tải trang lỗi: báo lỗi và không download file giả thành công.
- Hai điểm vào UI xuất cùng nội dung RAW; loader/nút được phục hồi. JS riêng, engine inline và bản phân phối tương đương.
- Các kiểm thử xuất thẩm định, lưu đôi, danh sách và đối chiếu hiện có tiếp tục đạt.

### 8.2. Nghiệm thu file thực

Xuất ít nhất một bộ hồ sơ có đủ các phụ lục đang được lưu, một báo cáo cấp 1 nhiều nhánh và một bộ vượt số hàng mẫu. Mở bằng Excel để kiểm tra tên sheet, tiêu đề, nhóm đơn vị, border, merge, footer và vùng in; đối chiếu hồ sơ nguồn theo ID/phụ lục và tổng tiền kê khai.

Hoàn thành khi mọi hồ sơ RAW phù hợp bộ lọc được xuất đúng một lần vào đúng phụ lục, giá trị gốc được bảo toàn, bố cục BQP rõ ràng và không xuất hiện nội dung so sánh/kết luận thẩm định.
