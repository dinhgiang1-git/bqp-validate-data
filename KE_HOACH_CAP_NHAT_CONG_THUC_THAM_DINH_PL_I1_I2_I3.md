# Kế hoạch rà soát và cập nhật công thức thẩm định Phụ lục I.1, I.2, I.3

**Ngày lập:** 01/10/2026  
**Phạm vi:** Bộ tính Java, engine JavaScript giao diện, parser Excel, kiểm tra và tổng hợp sai lệch cho ba phụ lục I.1–I.3.

## 1. Mục tiêu

- Đảm bảo công thức thẩm định thời gian, điều kiện hưởng và từng khoản tiền của I.1, I.2, I.3 thống nhất với văn bản áp dụng và cách lập biểu thực tế.
- Đồng bộ kết quả giữa Java backend, `bqp_validation.js` và engine nhúng trong `index.html`.
- Phân biệt giá trị thời gian phần mềm tính lại với giá trị thời gian đơn vị kê khai; khi đã xác định giá trị tính đúng, mọi khoản tiền thẩm định phải dùng giá trị tính đúng đó, không lấy lại số kê khai sai.
- Bổ sung kiểm thử hồi quy cho các mốc biên, dữ liệu Excel dạng số/văn bản/công thức, và đối chiếu các dòng bị ảnh hưởng.

## 2. Phát hiện ban đầu đã xác minh

### 2.1. QK5: phần mềm tính đúng 33,5 năm nhưng công thức tiền lại dùng số kê khai 33,6

Hồ sơ **Phạm Trần Đại** trong `input/13. Phụ lục QK5.xlsx`, sheet **Phụ lục I.1**, có lương tháng 32.535.360 đồng. Cột BHXH kê khai là `33.6`.

**Luồng tính trong `PLI1Calculator.java`:**

1. `calcNamLamTron(monthsC12)` tính từ ngày tháng thực tế và trả về `rawExp12 = 33,5 năm` — đây là con số đúng.
2. Nhưng dòng tiếp theo (dòng 99):
   ```java
   BigDecimal exp12ForMoney = (actualC12 != null && actualC12.compareTo(BigDecimal.ZERO) > 0) ? actualC12 : rawExp12;
   ```
   Vì cột kê khai có giá trị `actualC12 = 33,6 ≠ null` và `> 0`, điều kiện đúng và `exp12ForMoney` bị gán thành `33,6` thay vì `33,5`.
3. Khoản vượt mốc tính trên `exp12ForMoney`:
   `32.535.360 × 0,5 × (33,6 − 15) = 302.578.848 đồng`.
4. Kết quả đúng khi dùng `rawExp12 = 33,5`:
   `32.535.360 × 0,5 × (33,5 − 15) = 300.952.080 đồng`.
5. **Chênh lệch: +1.626.768 đồng** do dùng nhầm số kê khai sai vào công thức tiền.

**Nguồn gốc:** Logic ưu tiên dùng số kê khai của đơn vị — thiết kế ban đầu để chống báo lỗi dây chuyền khi BHXH thực tế khác thời gian tính từ ngày. Nhưng khi số kê khai sai định dạng (`33.6`) hoặc sai nghiệp vụ so với số tính lại (`33,5`), thẩm định viên chỉ thấy báo lỗi cột 12, còn cột tiền 19/22 lại dùng số sai đó để tính — gây ra sai lệch tiền mà không có cảnh báo tương ứng.

**Phạm vi ảnh hưởng:** Bất kỳ hồ sơ nào mà `actualC12` khác `rawExp12` và `actualC12 > 0` đều có thể bị tính sai khoản cột 19/22. Cần thống kê toàn bộ QK5 và các file khác.

**Hướng xử lý cần thảo luận:** Hoặc (a) luôn dùng `rawExp12` từ ngày thực tế cho cột tiền thẩm định, hoặc (b) giữ `actualC12` nhưng bắt buộc cảnh báo “Cột tiền 19/22 tính theo số kê khai — cần xác minh”. Không được âm thầm lấy số kê khai sai vào công thức tiền mà không cảnh báo.

### 2.2. Sai khác làm tròn 6 tháng trong I.3

`PLI3Calculator.java` và các engine I.3 trong `bqp_validation.js` và `index.html` dùng điều kiện `< 6` cho thời gian nghỉ sớm ở cột 11. Vì vậy trường hợp phần dư đúng 6 tháng bị làm tròn lên 1 năm. Trong khi đó, quy tắc đã nêu ở tài liệu nghiệp vụ và hàm làm tròn dùng cho I.1/I.2 là `<= 6` tháng thì cộng 0,5 năm. Sai khác này ảnh hưởng cột 11 và khoản trợ cấp cột 12, tổng cột 15 của I.3.

### 2.3. Chất lượng tài liệu và độ bao phủ kiểm thử

Tài liệu `SO_SANH_QUY_DINH_VOI_PHAN_MEM.md` hiện kết luận các công thức “100% khớp” nhưng không ghi nhận sai khác biên vừa phát hiện ở I.3. Test hiện có đã bao phủ một số phép tính đại diện và I.2 có kiểm thử các mốc 4, 6, 7 tháng; chưa chứng minh đầy đủ tính nhất quán của mọi cột, backend/frontend, parser và các mốc biên I.1/I.3.

## 3. Phạm vi rà soát theo phụ lục

### Phụ lục I.1 — nghỉ hưu trước tuổi theo NĐ 178/NĐ 67 và TT 19

- Cột 10–12: tháng nghỉ sớm, năm nghỉ sớm, thời gian BHXH; kiểm tra tính tháng giữa các mốc, làm tròn, giới hạn 60 tháng và phân biệt thời gian suy ra từ ngày với thời gian BHXH kê khai. Lưu ý: nếu số tính từ ngày ≥ 60 mà đơn vị kê khai = 60 thì xử lý là đúng (vì cột 10 chỉ dùng tối đa 60 tháng để tính tiền) — đây là hành vi cần bảo toàn, không phải lỗi.
- Cột 13–16: nhóm thời điểm nghỉ trong/trên 12 tháng và hệ số áp dụng theo thời gian nghỉ sớm.
- Cột 17–22: điều kiện nhóm 2–5 năm hoặc trên 5–10 năm; mốc BHXH 20/15 năm; khoản cơ bản và khoản vượt mốc.
- Cột tổng: cộng khoản đúng nhóm, xử lý ô gộp cột 18+19 hoặc 21+22, và tính chênh lệch trên đúng tổng.
- Kiểm tra riêng tình huống `33.6` trong QK5, cách đọc số có dấu chấm/phẩy, chuỗi năm-tháng và công thức Excel có giá trị cache.

### Phụ lục I.2 — phục viên/thôi việc

- Cột 10–11: số tháng thôi việc, giới hạn 60 tháng/thời gian BHXH dưới 60 tháng, điều kiện tuổi còn lại đủ 24 tháng và làm tròn thời gian công tác.
- Cột 12–17: tính đúng nhóm trong 12 tháng đầu/từ tháng 13, hệ số trên số tháng, trợ cấp theo số năm BHXH và khoản tìm việc làm.
- Cột 18 và 20: tổng cột, xử lý khoản cột 19 và công thức/giá trị kê khai.
- Kiểm tra biên 0, 1, 6, 7, 11, 12 tháng; biên 23/24, 59/60/61 tháng; mốc chuyển nhóm 12 tháng.

### Phụ lục I.3 — chế độ theo NĐ 177/HD 1787

- Cột 10–11: thời gian BHXH và thời gian nghỉ trước tuổi; xác minh riêng mốc đúng 6 tháng; kiểm tra ngày nhập ngũ/nghỉ, tháng sinh và trần tuổi.
- Cột 12–15: khoản theo số năm nghỉ sớm, khoản cơ bản BHXH, khoản vượt mốc 20/15 năm và tổng.
- Kiểm tra mốc chuyển tiếp 01/07/2025, thời gian BHXH đúng bằng mốc và vừa vượt mốc.
- Đồng bộ cách tính giữa `PLI3Calculator`, `bqp_validation.js` và `index.html`.

## 4. Kế hoạch thực hiện

### Giai đoạn 1 — chốt quy tắc nghiệp vụ

1. Lập ma trận cột: dữ liệu đầu vào, đơn vị đo, công thức, điều kiện áp dụng, giới hạn, mốc chuyển tiếp và nguồn pháp lý tương ứng.
2. Đối chiếu trực tiếp các văn bản hiện hành trong thư mục `D:\bqp\quydinh&thongtu`; ghi rõ điều/khoản/mục cho từng công thức và mọi ngoại lệ.
3. Chốt bằng nghiệp vụ quy tắc đọc giá trị BHXH như `33.6`: định dạng nào được chấp nhận; định dạng nào phải cảnh báo hoặc yêu cầu xác minh. Giữ nguyên dữ liệu nguồn; không tự diễn dịch dữ liệu mơ hồ.

**Đầu ra:** Ma trận công thức được xác nhận, danh sách quy tắc đầu vào có căn cứ.

### Giai đoạn 2 — kiểm kê triển khai hiện hành và xác định sai lệch

1. Đối chiếu từng dòng trong ma trận với ba calculator Java, logic Java validation, parser, engine JS riêng và các hàm tính trong HTML.
2. Ghi nhận khác biệt về mốc biên, cách làm tròn, mốc 01/07/2025, điều kiện nhóm, giới hạn, dữ liệu thiếu và cách tổng hợp/chênh lệch.
3. Chạy kiểm tra workbook QK5 theo hướng chỉ đọc; lập danh sách dòng/cột có thể bị ảnh hưởng, phân loại rõ lỗi công thức, lỗi định dạng đầu vào, sai số nguồn và cảnh báo cần cán bộ xác minh.

**Đầu ra:** Danh sách sai lệch có thể tái hiện và phạm vi hồ sơ bị ảnh hưởng; không sửa file nguồn.

### Giai đoạn 3 — cập nhật công thức và thống nhất luồng tính

1. Sửa sai khác I.3 tại mốc 6 tháng để khớp quy tắc đã được nghiệp vụ phê duyệt; cập nhật đồng bộ Java và cả hai engine giao diện.
2. Xử lý đầu vào BHXH mơ hồ theo kết luận của Giai đoạn 1: báo lỗi/cảnh báo có nội dung nêu rõ định dạng kỳ vọng; chỉ chuyển đổi khi định dạng có thể xác định duy nhất.
3. Sửa các sai lệch khác được xác nhận trong kiểm kê; tránh thay đổi hành vi không liên quan.
4. Kiểm tra số tiền được tính từ đúng trường thời gian và đúng đơn vị; không để số kê khai mơ hồ lặng lẽ đi vào công thức tiền.

**Đầu ra:** Mã các bộ tính đồng nhất theo ma trận đã duyệt và có dấu vết giải thích sai lệch.

### Giai đoạn 4 — kiểm thử và đối soát

1. Viết test đơn vị cho từng công thức và điều kiện, đặc biệt các mốc liền kề quanh ranh giới.
2. Bổ sung test parser cho số, text, ngày, công thức Excel có cache/không có cache, chuỗi năm-tháng và số thập phân mơ hồ.
3. Chạy bộ test Java, test JavaScript/normalization và kiểm tra cú pháp toàn bộ script nhúng trong HTML.
4. Chạy đối soát trước/sau trên bản sao của workbook QK5, xác minh độc lập các hồ sơ bị ảnh hưởng và tổng chênh lệch; không ghi đè file gốc.
5. Kiểm tra không phát sinh lỗi công thức Excel, lệch cột, thay đổi dữ liệu kê khai hoặc thay đổi ngoài phạm vi.

**Đầu ra:** Biên bản test, danh sách hồ sơ thay đổi kết quả và đối soát được người phụ trách nghiệp vụ xác nhận.

### Giai đoạn 5 — cập nhật tài liệu và phát hành

1. Cập nhật tài liệu so sánh quy định/phần mềm, bỏ các khẳng định tuyệt đối nếu chưa có bằng chứng test cho toàn bộ ma trận.
2. Ghi chú thay đổi và tác động dữ liệu: loại lỗi đã sửa, nhóm hồ sơ bị ảnh hưởng, cách xử lý giá trị `33.6` theo quy tắc được duyệt.
3. Chỉ phát hành sau khi kiểm thử kỹ thuật và xác nhận nghiệp vụ hoàn tất; giữ bản nguồn và kết quả đối soát để truy xuất.

## 5. Ma trận kiểm thử tối thiểu

| Nhóm | Các mốc cần kiểm tra | Phụ lục |
| --- | --- | --- |
| Làm tròn thời gian | 0, 1, 5, 6, 7, 11, 12, 13 tháng | I.1, I.2, I.3 |
| Thời gian nghỉ sớm | 23/24 tháng, 59/60/61 tháng | I.1, I.2 |
| Điều kiện tuổi I.2 | 23/24/25 tháng còn lại đến trần tuổi | I.2 |
| Mốc chuyển nhóm thời gian | 11/12/13 tháng từ ngày sáp nhập/giải thể | I.1, I.2 |
| Mốc BHXH | đúng 15/20 năm và vượt mốc 0,5/1,0 năm | I.1, I.3 |
| Mốc chính sách | trước ngày 01/07/2025, đúng ngày và sau ngày | I.1, I.3 |
| Kiểu dữ liệu | số nguyên, số thập phân, chuỗi “năm/tháng”, ngày Excel, công thức Excel | I.1, I.2, I.3 |
| Đầu ra | từng khoản, tổng, khoản gộp, số tiền thực tế/tính lại/chênh lệch | I.1, I.2, I.3 |

## 6. Tiêu chí hoàn thành

- Tất cả công thức trong ma trận có căn cứ pháp lý/nghiệp vụ và được phê duyệt.
- Không còn sai khác giữa Java, JavaScript riêng và HTML ở cùng một quy tắc.
- Các mốc biên của ma trận có kiểm thử; lỗi I.3 tại đúng 6 tháng được tái hiện trước sửa và hết sau sửa.
- Giá trị đầu vào mơ hồ như `33.6` được xử lý đúng theo quy tắc đã phê duyệt, không âm thầm coi là 33 năm 6 tháng hoặc 33,6 năm khi chưa có căn cứ.
- Workbook QK5 được đối soát trên bản sao; xác định và giải trình được mọi thay đổi kết quả, giữ nguyên bản nguồn.
- Tài liệu nghiệp vụ và hướng dẫn vận hành phản ánh đúng triển khai, không tuyên bố “100% khớp” khi chưa chứng minh.

## 7. Lưu ý phạm vi

Bản kế hoạch này dựa trên việc đọc mã nguồn/test và xác minh dòng Phạm Trần Đại trong workbook QK5. Chưa chạy toàn bộ bộ test, chưa tính thống kê mọi hồ sơ bị ảnh hưởng, và chưa thẩm định lại nội dung pháp lý từ PDF. Vì vậy, sai khác I.3 và trường hợp `33.6` là phát hiện đã xác minh; các hạng mục còn lại là nội dung cần rà soát, không được hiểu là lỗi đã kết luận.
