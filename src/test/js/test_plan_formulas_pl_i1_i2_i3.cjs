const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ExcelJS = require('exceljs');

const staticDir = path.resolve(__dirname, '../../main/resources/static');
const normCode = fs.readFileSync(path.join(staticDir, 'bqp_normalization.js'), 'utf8');
const valCode = fs.readFileSync(path.join(staticDir, 'bqp_validation.js'), 'utf8');

function createSandbox() {
    const ctx = {
        console,
        Date,
        Math,
        parseInt,
        parseFloat,
        String,
        Number,
        Array,
        Object,
        ExcelJS,
        document: {
            addEventListener: () => {},
            getElementById: () => null,
            querySelector: () => null,
            querySelectorAll: () => []
        },
        window: null
    };
    ctx.window = ctx;
    vm.createContext(ctx);
    vm.runInContext(normCode, ctx);
    vm.runInContext(valCode, ctx);
    return ctx;
}

const ctx = createSandbox();
const { BQPValidation } = ctx;

console.log('================================================================');
console.log('KIỂM THỬ CÔNG THỨC THẨM ĐỊNH PHỤ LỤC I.1, I.2, I.3 THEO KẾ HOẠCH');
console.log('================================================================\n');

// -------------------------------------------------------------
// PHẦN 1: PHỤ LỤC I.1 - HỒ SƠ PHẠM TRẦN ĐẠI (QK5) VÀ CỘT 12 KÊ KHAI 33.6
// -------------------------------------------------------------
console.log('--- 1. Kiểm tra Phụ lục I.1: Hồ sơ Phạm Trần Đại (QK5) ---');
// Phạm Trần Đại: Lương = 32.535.360 đ; Nhập ngũ: 01/03/1990; Nghỉ: 01/09/2023
// Số tháng BHXH = 402 tháng = 33 năm 6 tháng -> Làm tròn chuẩn = 33.5 năm.
// Đơn vị kê khai cột 12 = 33.6.
// Trước đây: phần mềm lấy 33.6 đi tính cột 19 -> 32.535.360 * 0.5 * (33.6 - 15) = 302.578.848 đ (sai +1.626.768 đ).
// Sau khi sửa: dùng exp12 chuẩn = 33.5 năm -> 32.535.360 * 0.5 * (33.5 - 15) = 300.952.080 đ.
const daiData = {
    capBac: 'Thượng tá',
    chucVu: 'Phó trưởng phòng DQTV, BTM',
    ngaySinh: '01/01/1972',
    nhapNgu: '01/03/1992',
    thoiDiemNghi: '01/09/2025',
    sapNhap: null,
    cot10Actual: 29,
    cot11Actual: 2.5,
    cot12Actual: 33.6, // đơn vị kê khai nhầm 33.6
    luongThang: 32535360
};

const daiExp = BQPValidation.PLI1Calculator.calculateExpected(daiData);
console.log('Phạm Trần Đại calculated expected:');
console.log('  - Cột 10 (tháng nghỉ sớm):', daiExp.cot10);
console.log('  - Cột 11 (năm nghỉ sớm):', daiExp.cot11);
console.log('  - Cột 12 (năm BHXH chuẩn tính lại):', daiExp.cot12);
console.log('  - Cột 18 (trợ cấp 4 tháng mốc 15 năm):', daiExp.cot18.toLocaleString('vi-VN'), 'đ');
console.log('  - Cột 19 (vượt mốc 15 năm BHXH):', daiExp.cot19.toLocaleString('vi-VN'), 'đ');

assert.strictEqual(daiExp.cot12, 33.5, 'Cột 12 chuẩn của Phạm Trần Đại phải là 33.5 năm');
assert.strictEqual(daiExp.cot19, 300952080, 'Cột 19 của Phạm Trần Đại phải tính theo 33.5 năm = 300.952.080 đ');
console.log('✅ PASS: Cột 19 của Phạm Trần Đại đã ra đúng 300.952.080 đ, không bị lệch do số kê khai 33.6!\n');

// -------------------------------------------------------------
// PHẦN 2: PHỤ LỤC I.3 - MỐC BIÊN LÀM TRÒN 6 THÁNG
// -------------------------------------------------------------
console.log('--- 2. Kiểm tra Phụ lục I.3: Các mốc biên làm tròn thời gian nghỉ sớm (Cột 11) ---');
// Quy tắc NĐ 177:
// Dư 0 tháng -> 0 năm
// Dư 1..6 tháng -> +0.5 năm
// Dư 7..12 tháng -> +1.0 năm

const testI3Boundary = (mThang, expNam, desc) => {
    // Giả lập ngày sinh và ngày nghỉ sao cho hiệu số tuổi trần - ngày nghỉ = mThang tháng
    // Ví dụ trần = 58 tuổi. Sinh 01/01/1970 -> trần vào 01/01/2028.
    // Nghỉ hưu: dThoiDiemNghi sao cho (2028*12 + 0) - (retYear*12 + retMonth) = mThang
    const totalTarget = 2028 * 12;
    const retTotal = totalTarget - mThang;
    const retY = Math.floor(retTotal / 12);
    const retM = retTotal % 12 + 1; // 1-indexed month
    const retDateStr = `01/${retM < 10 ? '0' + retM : retM}/${retY}`;

    const data = {
        capBac: 'Đại tá',
        chucVu: '',
        ngaySinh: '01/01/1970',
        nhapNgu: '01/01/1990',
        thoiDiemNghi: retDateStr,
        luongThang: 20000000
    };

    const res = BQPValidation.PLI3Calculator.calculateExpected(data);
    assert.strictEqual(
        res.cot11,
        expNam,
        `Mốc ${mThang} tháng (${desc}): Cột 11 phải là ${expNam} năm (thực tế: ${res.cot11})`
    );
    console.log(`  [PASS] ${desc}: ${mThang} tháng -> Cột 11 = ${res.cot11} năm`);
};

testI3Boundary(0, 0, 'Dư đúng 0 tháng');
testI3Boundary(1, 0.5, 'Dư 1 tháng (biên dưới nửa năm)');
testI3Boundary(5, 0.5, 'Dư 5 tháng');
testI3Boundary(6, 0.5, 'ĐÚNG 6 THÁNG (MỐC BIÊN QUAN TRỌNG - PHẢI LÀ 0.5 NĂM)');
testI3Boundary(7, 1.0, 'Dư 7 tháng (biên trên làm tròn 1 năm)');
testI3Boundary(11, 1.0, 'Dư 11 tháng');
testI3Boundary(12, 1.0, 'Đúng 12 tháng (1.0 năm)');
testI3Boundary(30, 2.5, 'Đúng 2 năm 6 tháng (30 tháng -> 2.5 năm)');
testI3Boundary(31, 3.0, '2 năm 7 tháng (31 tháng -> 3.0 năm)');

console.log('✅ PASS: Toàn bộ 9 mốc biên làm tròn I.3 đã kiểm thử thành công!\n');

// -------------------------------------------------------------
// PHẦN 3: PHỤ LỤC I.2 - KIỂM THỬ CÁC MỐC BIÊN VÀ ĐIỀU KIỆN TUỔI ĐỜI
// -------------------------------------------------------------
console.log('--- 3. Kiểm tra Phụ lục I.2: Mốc biên tuổi đời và thời gian công tác ---');
// Tuổi đời còn lại: < 24 tháng thì không được hưởng trợ cấp thôi việc (các cột 12..17 = 0)
// >= 24 tháng: được hưởng
const testI2Age = (remainMonths, shouldBeEligible) => {
    // Trần = 58 tuổi. Sinh 01/01/1970 -> trần 01/01/2028.
    // Nghỉ thôi việc: dThoiDiem sao cho còn lại remainMonths tháng
    const target = 2028 * 12;
    const cur = target - remainMonths;
    const curY = Math.floor(cur / 12);
    const curM = cur % 12 + 1;
    const curDateStr = `01/${curM < 10 ? '0' + curM : curM}/${curY}`;

    const data = {
        capBac: 'Đại tá',
        chucVu: '',
        ngaySinh: '01/01/1970',
        nhapNgu: '01/01/2000',
        thoiDiemNghi: curDateStr,
        sapNhap: null,
        luongThang: 25000000,
        rawCols: []
    };

    const res = BQPValidation.PLI2Calculator.calculateExpected(data);
    const hasMoney = res.cot18 > 0;
    assert.strictEqual(
        hasMoney,
        shouldBeEligible,
        `Tuổi đời còn lại ${remainMonths} tháng: Hưởng trợ cấp phải là ${shouldBeEligible}`
    );
    console.log(`  [PASS] Tuổi còn lại ${remainMonths} tháng -> Được hưởng = ${hasMoney} (Tổng = ${res.cot18.toLocaleString('vi-VN')} đ)`);
};

testI2Age(23, false); // 23 tháng: dưới 24 tháng -> 0 đồng
testI2Age(24, true);  // đúng 24 tháng: đủ điều kiện -> được hưởng
testI2Age(25, true);  // 25 tháng -> được hưởng
testI2Age(60, true);  // 60 tháng -> được hưởng tối đa 60 tháng
testI2Age(72, true);  // 72 tháng -> khống chế tối đa 60 tháng

console.log('✅ PASS: Mốc biên tuổi đời I.2 đã kiểm thử thành công!\n');

// -------------------------------------------------------------
// PHẦN 4: ĐỐI SOÁT VỚI BẢN SAO WORKBOOK QK5
// -------------------------------------------------------------
console.log('--- 4. Đối soát trực tiếp hồ sơ Phạm Trần Đại trong file QK5 ---');
async function testQk5File() {
    const qk5Path = path.resolve('input/13. Phụ lục QK5.xlsx');
    if (!fs.existsSync(qk5Path)) {
        console.log('Bỏ qua: Không tìm thấy input/13. Phụ lục QK5.xlsx');
        return;
    }

    const wb = new ExcelJS.Workbook();
    await wb.xlsx.readFile(qk5Path);
    const ws = wb.getWorksheet('Phụ lục I.1');
    assert(ws, 'File QK5 phải có sheet Phụ lục I.1');

    // Tìm dòng của Phạm Trần Đại
    let daiRowNumber = -1;
    ws.eachRow((row, rNum) => {
        row.eachCell(cell => {
            const val = String(cell.value || '');
            if (val.includes('Phạm Trần Đại')) {
                daiRowNumber = rNum;
            }
        });
    });

    assert(daiRowNumber > 0, 'Phải tìm thấy dòng của Phạm Trần Đại trong QK5');
    console.log(`Tìm thấy Phạm Trần Đại tại dòng ${daiRowNumber} sheet Phụ lục I.1`);

    const row = ws.getRow(daiRowNumber);
    // Trong sheet Phụ lục I.1 của QK5:
    // Cột 13 trong Excel = Cột 12 theo mẫu (Thời gian đóng BHXH)
    // Cột 20 trong Excel = Cột 19 theo mẫu (Trợ cấp vượt mốc BHXH)
    const c12Val = row.getCell(13).value;
    const c19Val = row.getCell(20).value;
    console.log(`  - Giá trị kê khai cột 12 (Col 13): ${c12Val}`);
    console.log(`  - Giá trị kê khai cột 19 (Col 20): ${typeof c19Val === 'object' && c19Val.result ? c19Val.result : c19Val}`);

    const parsedData = {
        capBac: 'Thượng tá',
        chucVu: 'Phó trưởng phòng DQTV, BTM',
        ngaySinh: '01/01/1972',
        nhapNgu: '01/03/1992',
        thoiDiemNghi: '01/09/2025',
        sapNhap: null,
        cot10Actual: 29,
        cot11Actual: 2.5,
        cot12Actual: c12Val,
        luongThang: 32535360
    };

    const valResult = BQPValidation.PLI1Calculator.calculateExpected(parsedData);
    assert.strictEqual(valResult.cot12, 33.5, 'Cột 12 chuẩn tính lại phải là 33.5 năm');
    assert.strictEqual(valResult.cot19, 300952080, 'Cột 19 chuẩn thẩm định phải là 300.952.080 đ');
    console.log(`  - Thẩm định cột 12 tính lại: ${valResult.cot12} năm`);
    console.log(`  - Thẩm định cột 19 tiền tính lại: ${valResult.cot19.toLocaleString('vi-VN')} đ`);
    console.log('✅ PASS: Hồ sơ QK5 Phạm Trần Đại đã được thẩm định đúng 100% theo chuẩn BQP!');
}

testQk5File().then(() => {
    console.log('\n================================================================');
    console.log('🎉 TẤT CẢ KIỂM THỬ THEO KẾ HOẠCH ĐÃ ĐẠT 100%!');
    console.log('================================================================');
}).catch(err => {
    console.error('❌ Lỗi:', err);
    process.exit(1);
});
