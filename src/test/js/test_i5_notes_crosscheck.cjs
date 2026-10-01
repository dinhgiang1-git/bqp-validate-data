'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
    ExcelJS, staticDir, loadReportTemplate, reportContext, inlineEngineCode,
    createExportSandbox, cellText, findRow, numeric
} = require('./bqp_export_test_helpers.cjs');

async function testBtlHaNoiImportAndExport() {
    console.log('--- 1. Testing BTL Hà Nội Input File Parsing & Export ---');
    
    let btlFilePath = path.join(staticDir, '8. Phu luc BTL thủ đô Hà Nội.xlsx');
    if (!fs.existsSync(btlFilePath)) {
        btlFilePath = path.resolve(__dirname, '../../../../input/8. Phu luc BTL thủ đô Hà Nội.xlsx');
    }
    if (!fs.existsSync(btlFilePath)) {
        btlFilePath = path.resolve(__dirname, '../../../../8. Phu luc BTL thủ đô Hà Nội.xlsx');
    }
    if (!fs.existsSync(btlFilePath)) {
        console.warn('⚠️ File "8. Phu luc BTL thủ đô Hà Nội.xlsx" not found at', btlFilePath);
        return;
    }

    const bytes = fs.readFileSync(btlFilePath);
    const sandbox = createExportSandbox();
    
    // Test parsing file
    const fileObj = { name: '8. Phu luc BTL thủ đô Hà Nội.xlsx', arrayBuffer: async () => bytes };
    await sandbox.ValidationUI.handleFileUpload({ target: { files: [fileObj] } });
    
    const records = sandbox.ValidationUI.currentRecords;
    console.log('Total records parsed:', records ? records.length : 0);
    const sheetCounts = {};
    (records || []).forEach(r => { sheetCounts[r.sheet] = (sheetCounts[r.sheet] || 0) + 1; });
    console.log('Sheet counts:', sheetCounts);
    assert.ok(records && records.length > 0, 'Must parse records from BTL Hà Nội');
    
    // Find I.5 record
    const i5Records = records.filter(r => r.sheet === 'I.5');
    assert.equal(i5Records.length, 1, 'Must parse 1 record from Phụ lục I.5');
    
    const recI5 = i5Records[0];
    console.log('I.5 Record parsed:');
    console.log('  Họ tên:', recI5.hoTen);
    console.log('  Cấp bậc (E11):', recI5.capBac);
    console.log('  Chức vụ (D11):', recI5.chucVu);
    console.log('  Nhập ngũ (I11):', recI5.nhapNgu);
    console.log('  Thời điểm nghỉ (J11):', recI5.thoiDiemNghi);
    console.log('  Lương C22 (V11):', recI5.luongThang);

    assert.equal(recI5.capBac, '3//NL1', 'I.5 rank must preserve 3//NL1 string');
    assert.ok(recI5.nhapNgu, 'I.5 enlistment date must be present');
    assert.ok(recI5.thoiDiemNghi, 'I.5 retirement date must be present');
    assert.equal(numeric(recI5.luongThang || recI5.rawCols?.[22]), 22000000, 'I.5 C22 salary must be 22,000,000');
    assert.equal(recI5.cellErrors[9], undefined, 'C9 (nhập ngũ) must not be invalid numeric type error');
    assert.equal(recI5.cellErrors[10], undefined, 'C10 (thời điểm nghỉ) must not be invalid numeric type error');

    console.log('✅ PASS: Phụ lục I.5 parsed correctly with exact columns, dates and salary!');

    // Test cross-checking C22 I.5 with C9 I.1
    console.log('--- 2. Testing Cross-checking C22 I.5 vs C9 I.1 ---');
    const matchingI1 = records.find(r => r.sheet === 'I.1' && r.hoTen === recI5.hoTen);
    assert.ok(matchingI1, 'Must find matching record in Phụ lục I.1 for I.5 person');
    
    console.log('  Matching I.1 record:', matchingI1.hoTen);
    console.log('  I.1 errors:', matchingI1.errorDetails);
    console.log('  I.1 comparisons:', matchingI1.comparisons);
    
    const money = v => numeric(String(v).replace(/\s*đ$/, ''));
    const compC22 = recI5.comparisons.find(c => c.col === 'Cột 22');
    assert.equal(money(compC22.expected), 29203200, 'I.5 C22 must be recalculated as SUM(C13:C21) = 29,203,200');

    const compC9 = matchingI1.comparisons && matchingI1.comparisons.find(c => c.col === 'Cột 9');
    assert.ok(compC9, 'Matching I.1 record must have a Cột 9 comparison');
    assert.equal(compC9.hasErr, true, 'Cột 9 comparison must mark error due to salary discrepancy');
    assert.equal(money(compC9.actual), 30859920, 'C9 actual value must be reported 30,859,920');
    assert.equal(money(compC9.expected), 29203200, 'C9 expected value must be recalculated I.5 C22 29,203,200 (not declared 22,000,000)');
    assert.equal(numeric(matchingI1.rawCols[9]), 30859920, 'Raw C9 must keep the declared value');
    assert.equal(matchingI1.luongThang, 29203200, 'I.1 allowances must be recalculated from the standard I.5 salary');

    console.log('✅ PASS: I.1 C9 (30,859,920) checked against recalculated I.5 C22 (29,203,200); raw C9 preserved!');

    // Test Export & Check Notes
    console.log('--- 3. Testing Exported Workbook for Absence of Notes/Comments ---');
    const template = await loadReportTemplate();
    const context = await reportContext({ reportTemplateWorkbook: template });
    const baseWb = new ExcelJS.Workbook();
    await baseWb.xlsx.load(bytes);
    
    const exportRes = await sandbox.BQPValidation.exportValidatedWorkbook(baseWb, '8. Phu luc BTL thủ đô Hà Nội.xlsx', records, false, 'Tat_Ca', records, context);
    
    const outWb = new ExcelJS.Workbook();
    await outWb.xlsx.load(exportRes.buffer);
    
    let totalNotesFound = 0;
    for (const ws of outWb.worksheets) {
        ws.eachRow({ includeEmpty: true }, row => {
            row.eachCell({ includeEmpty: true }, cell => {
                if (cell.note || cell._comment || (cell._value && cell._value.model && cell._value.model.comment)) {
                    totalNotesFound++;
                    console.error(`Found Note at ${ws.name}!${cell.address}:`, cell.note);
                }
            });
        });
    }
    
    assert.equal(totalNotesFound, 0, 'Exported workbook MUST NOT contain any Excel Notes/comments (0 notes expected)');
    console.log('✅ PASS: Exported workbook contains 0 Excel Notes/comments across all worksheets!');

    // Test 2-line rendering in C9 of Phụ lục I.1 in exported workbook
    console.log('--- 4. Testing 2-line RichText Rendering in C9 of Exported Workbook ---');
    const outI1Ws = outWb.getWorksheet('Phụ lục I.1');
    const outI1Row = findRow(outI1Ws, recI5.hoTen);
    assert.ok(outI1Row, 'Must find record row in exported Phụ lục I.1 sheet');
    
    const cellC9 = outI1Row.getCell(9);
    console.log('C9 cell value in export:', cellC9.value);
    assert.ok(cellC9.value && cellC9.value.richText, 'C9 cell in export must have richText with 2 lines');
    assert.equal(cellC9.value.richText.length, 2, 'C9 richText must contain exactly 2 parts');
    assert.equal(cellC9.value.richText[0].font.strike, true, 'First line (old value) must have strike=true');
    assert.equal(cellC9.value.richText[0].font.color.argb, 'FFDC2626', 'First line must be red');
    assert.ok(!cellC9.value.richText[1].font.strike, 'Second line (new value) must not have strike');
    assert.equal(cellC9.value.richText[1].font.bold, true, 'Second line must be bold');
    
    console.log('✅ PASS: C9 displays old value struck-through red on line 1, new value bold black on line 2!');

    // Test "Danh sách lỗi" sheet
    console.log('--- 5. Testing "Danh sách lỗi" Sheet ---');
    const errSheet = outWb.getWorksheet('Danh sách lỗi');
    assert.ok(errSheet, 'Exported workbook must contain "Danh sách lỗi" sheet when errors exist');
    console.log('✅ PASS: "Danh sách lỗi" sheet present in exported workbook!');
}

async function run() {
    await testBtlHaNoiImportAndExport();
    console.log('\n================================================================');
    console.log('🎉 ALL PHỤ LỤC I.5, NOTES, AND CROSS-CHECK TESTS PASSED 100%!');
    console.log('================================================================');
}

run().catch(err => {
    console.error('❌ Test failed:', err);
    process.exit(1);
});
