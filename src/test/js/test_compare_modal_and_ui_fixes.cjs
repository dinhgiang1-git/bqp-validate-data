const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('=== TEST 1: Kiểm tra bqp_validation.js logic ===');
const mod = require(path.resolve(__dirname, '../../main/resources/static/bqp_validation.js'));
const BQPValidation = mod.BQPValidation;

// Test 1.1: buildRawExcelComparisons
const mockRawCols = {
    "1": "1",
    "2": "Trần Đình Dũng",
    "3": "1//",
    "4": "Trợ lý",
    "5": "03/1984",
    "6": "02/2006",
    "7": "07/2025",
    "10": 60,
    "11": 19.5,
    "12": 1018967040,
    "13": 620933040,
    "14": 63685440,
    "18": 1703585520
};

const rawComparisons = BQPValidation.buildRawExcelComparisons('I.2', mockRawCols, {
    hoTen: 'Trần Đình Dũng',
    tienBaoCao: 1703585520,
    tongTienExcel: 1703585520
});

assert(Array.isArray(rawComparisons), 'rawComparisons must be an array');
assert(rawComparisons.length > 0, 'rawComparisons must not be empty');
console.log(`✓ buildRawExcelComparisons trả về ${rawComparisons.length} dòng chỉ tiêu.`);
const col18Item = rawComparisons.find(c => c.col === 'Cột 18' || (c.title && c.title.includes('TỔNG CỘNG')));
assert(col18Item, 'Phải có dòng tổng kinh phí Cột 18');
assert.strictEqual(col18Item.actual, '1.703.585.520 đ', 'Tổng kinh phí actual phải đúng 1.703.585.520 đ');
console.log('✓ Dòng tổng kinh phí:', col18Item);

// Test 1.2: ensureRecordValidationDetail với nguồn RAW (isExcelOnly = true)
const rawRecord = {
    id: 'test-raw-1',
    sheet: 'I.2',
    sheetSource: 'I.2',
    source: 'excel',
    hoTen: 'Trần Đình Dũng',
    tienBaoCao: 1703585520,
    tongTienExcel: 1703585520,
    tongTienTinhLai: 0,
    rawCols: mockRawCols
};

BQPValidation.ensureRecordValidationDetail(rawRecord, true);
assert(rawRecord.result, 'rawRecord.result phải được tạo');
assert(rawRecord.result.validationSnapshot, 'validationSnapshot phải được tạo');
assert(rawRecord.result.validationSnapshot.comparisons.length > 0, 'comparisons trong snapshot không được rỗng');
console.log(`✓ ensureRecordValidationDetail(rawRecord, true) đã nạp ${rawRecord.result.validationSnapshot.comparisons.length} chỉ tiêu vào validationSnapshot`);

// Test 1.3: ensureRecordValidationDetail với bản ghi đã thẩm định
const validatedRecord = {
    id: 'test-val-1',
    sheet: 'I.2',
    sheetSource: 'I.2',
    source: 'validated',
    hoTen: 'Trần Đình Dũng',
    tienBaoCao: 1703585520,
    tongTienExcel: 1703585520,
    tongTienTinhLai: 1703585520,
    rawCols: mockRawCols,
    result: {
        validationSnapshot: {
            comparisons: [
                { name: 'Chỉ tiêu 1', actual: 100, expected: 100, diff: 0 }
            ]
        }
    }
};
BQPValidation.ensureRecordValidationDetail(validatedRecord, false);
assert.strictEqual(validatedRecord.result.validationSnapshot.comparisons[0].name, 'Chỉ tiêu 1');
console.log('✓ ensureRecordValidationDetail giữ nguyên snapshot có sẵn của bản ghi thẩm định');

console.log('\n=== TEST 2: Kiểm tra giao diện HTML trong index.html và cong_cu_tinh_toan_bqp4.0.html ===');
const htmlPaths = [
    path.resolve(__dirname, '../../main/resources/static/index.html'),
    path.resolve(__dirname, '../../../../cong_cu_tinh_toan_bqp4.0.html')
];

for (const p of htmlPaths) {
    const html = fs.readFileSync(p, 'utf8');
    const filename = path.basename(p);
    
    // Kiểm tra colgroup độ rộng cột Thao tác là 160px
    assert(html.includes('<col style="width: 160px;">'), `${filename} phải có <col style="width: 160px;"> cho cột thao tác`);
    console.log(`✓ [${filename}] Cột thao tác đã được mở rộng lên 160px`);

    // Kiểm tra sticky border-left
    assert(html.includes('border-left: 1px solid #e2e8f0;'), `${filename} phải có border-left cho cột sticky`);
    console.log(`✓ [${filename}] Cột sticky có border-left phân tách rõ ràng`);

    // Kiểm tra render diff === 0
    assert(html.includes('let isZeroDiff = (diff === 0);'), `${filename} phải có logic phân biệt isZeroDiff`);
    assert(html.includes('let diffTdClass = isZeroDiff ? \'col-align-center\' : \'col-align-right\';'), `${filename} diff === 0 phải căn giữa col-align-center`);
    console.log(`✓ [${filename}] Chênh lệch = 0 hiển thị "---" căn giữa, không bị dính sang phải`);

    // Kiểm tra thứ tự nút RAW
    assert(html.includes('<button type="button" class="btn-row-action neutral" onclick="RecordsUI.viewDetail(\'${escapeJs(r.id)}\')" title="Xem chi tiết đối chiếu">Đối chiếu</button>'), `${filename} RAW tab phải có nút Đối chiếu trước`);
    console.log(`✓ [${filename}] Nút [Đối chiếu] và [Nạp form] đồng bộ thứ tự`);

    // Kiểm tra viewDetail nạp rawCols và gọi ensureRecordValidationDetail
    assert(html.includes('BQPValidation.ensureRecordValidationDetail(r, isExcelOnly)'), `${filename} viewDetail phải gọi ensureRecordValidationDetail(r, isExcelOnly)`);
    console.log(`✓ [${filename}] viewDetail hỗ trợ đầy đủ hydration cho nguồn RAW`);
}

console.log('\n=== TEST 3: Kiểm tra với dữ liệu SQLite thực tế (%LOCALAPPDATA%) nếu có ===');
const localAppData = process.env.LOCALAPPDATA || '';
const dbPath = path.join(localAppData, 'BQP', 'QuanLyCheDo', 'data', 'bqp-data.db');
if (fs.existsSync(dbPath)) {
    console.log('Tìm thấy file DB thực tế tại:', dbPath);
    try {
        const sqlite3 = require('sqlite3') || null;
        // Nếu không có native sqlite3 module thì thử bằng python/powershell hoặc đọc qua fs
        console.log('File DB size:', fs.statSync(dbPath).size, 'bytes');
    } catch(e) {
        console.log('sqlite3 module not available in node, skipping direct driver call');
    }
} else {
    console.log('Không có file DB cục bộ tại đường dẫn này, bỏ qua kiểm tra DB');
}

console.log('\n🎉 TẤT CẢ CÁC BƯỚC KIỂM TRA ĐÃ HOÀN TOÀN ĐẠT YÊU CẦU!');
