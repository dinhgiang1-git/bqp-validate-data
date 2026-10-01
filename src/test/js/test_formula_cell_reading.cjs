/**
 * Bộ kiểm thử toàn diện: Đọc giá trị ô công thức Excel an toàn
 * Phân biệt rõ số 0, ô trống, công thức trả rỗng, lỗi công thức và công thức chưa có kết quả.
 * Nghiệm thu nghiêm ngặt theo Mục 13 của: KE_HOACH_DOC_GIA_TRI_O_CONG_THUC_EXCEL.md
 */

const assert = require('assert');
const path = require('path');
const fs = require('fs');
const ExcelJS = require('exceljs');
const { BQPValidation, ExcelParser, CellStatus } = require('../../../../bqp_validation.js');

async function runTests() {
    console.log('====================================================');
    console.log('BẮT ĐẦU KIỂM THỬ: ĐỌC GIÁ TRỊ Ô CÔNG THỨC EXCEL');
    console.log('====================================================\n');

    // ----------------------------------------------------
    // TEST 1: Kiểm tra Định nghĩa CellStatus
    // ----------------------------------------------------
    console.log('TEST 1: Kiểm tra hằng số CellStatus');
    assert.strictEqual(CellStatus.VALUE, 'VALUE');
    assert.strictEqual(CellStatus.FORMULA_CACHED, 'FORMULA_CACHED');
    assert.strictEqual(CellStatus.FORMULA_EVALUATED, 'FORMULA_EVALUATED');
    assert.strictEqual(CellStatus.FORMULA_EMPTY, 'FORMULA_EMPTY');
    assert.strictEqual(CellStatus.FORMULA_NO_RESULT, 'FORMULA_NO_RESULT');
    assert.strictEqual(CellStatus.FORMULA_ERROR, 'FORMULA_ERROR');
    assert.strictEqual(CellStatus.BLANK, 'BLANK');
    assert.strictEqual(CellStatus.INVALID_TYPE, 'INVALID_TYPE');
    console.log('-> PASS: CellStatus định nghĩa đủ 8 trạng thái chuẩn mực.\n');

    // ----------------------------------------------------
    // TEST 2: Ô công thức có kết quả cache (Số, Text, Ngày, Số 0)
    // ----------------------------------------------------
    console.log('TEST 2: Ô công thức có kết quả cache');
    
    // 2.1. Công thức trả về số tiền dương
    const cellFormulaNumber = {
        value: { formula: 'SUM(A1:A5)', result: 15500000 },
        type: 6,
        address: 'B1'
    };
    const resNum = ExcelParser.readCellValue(cellFormulaNumber);
    assert.strictEqual(resNum.status, CellStatus.FORMULA_CACHED);
    assert.strictEqual(resNum.value, 15500000);
    assert.strictEqual(resNum.valueType, 'number');
    assert.strictEqual(resNum.formula, 'SUM(A1:A5)');
    assert.strictEqual(ExcelParser.parseNumber(cellFormulaNumber), 15500000);

    // 2.2. Công thức trả về số 0 thực sự
    const cellFormulaZero = {
        value: { formula: 'A1-A1', result: 0 },
        type: 6,
        address: 'B2'
    };
    const resZero = ExcelParser.readCellValue(cellFormulaZero);
    assert.strictEqual(resZero.status, CellStatus.FORMULA_CACHED);
    assert.strictEqual(resZero.value, 0);
    assert.strictEqual(resZero.valueType, 'number');
    assert.strictEqual(ExcelParser.parseNumber(cellFormulaZero), 0, 'Số 0 từ công thức phải giữ nguyên là 0');

    // 2.3. Công thức trả về chuỗi text
    const cellFormulaText = {
        value: { formula: 'CONCATENATE("BQP-", "2025")', result: 'BQP-2025' },
        type: 6,
        address: 'B3'
    };
    const resText = ExcelParser.readCellValue(cellFormulaText);
    assert.strictEqual(resText.status, CellStatus.FORMULA_CACHED);
    assert.strictEqual(resText.value, 'BQP-2025');
    assert.strictEqual(resText.valueType, 'string');
    assert.strictEqual(ExcelParser.getCellText(cellFormulaText), 'BQP-2025');

    // 2.4. Công thức trả về ngày tháng
    const testDate = new Date(Date.UTC(2025, 6, 1));
    const cellFormulaDate = {
        value: { formula: 'DATE(2025,7,1)', result: testDate },
        type: 6,
        address: 'B4'
    };
    const resDate = ExcelParser.readCellValue(cellFormulaDate);
    assert.strictEqual(resDate.status, CellStatus.FORMULA_CACHED);
    assert.strictEqual(resDate.valueType, 'date');
    assert.strictEqual(ExcelParser.parseDateCell(cellFormulaDate).getTime(), testDate.getTime());
    console.log('-> PASS: Nhận diện chính xác các loại dữ liệu cached từ công thức (kể cả số 0).\n');

    // ----------------------------------------------------
    // TEST 3: Công thức trả về chuỗi rỗng "" và xử lý XML Index (Không dùng Heuristic Regex)
    // ----------------------------------------------------
    console.log('TEST 3: Công thức trả về chuỗi rỗng & Tra cứu XML Index');

    // 3.1. Có result: "" rõ ràng
    const cellEmptyWithResult = {
        value: { formula: 'IF(L13>5, K13*0.9, "")', result: '' },
        type: 6,
        address: 'O13'
    };
    const resEmpty1 = ExcelParser.readCellValue(cellEmptyWithResult);
    assert.strictEqual(resEmpty1.status, CellStatus.FORMULA_EMPTY);
    assert.strictEqual(resEmpty1.value, '');
    assert.strictEqual(ExcelParser.parseNumber(cellEmptyWithResult), null, 'Ô rỗng parseNumber phải trả về defaultValue null');
    assert.strictEqual(ExcelParser.getCellText(cellEmptyWithResult), '');

    // 3.2. Không có result trong ExcelJS NHƯNG có XML Index xác nhận tồn tại <v/> rỗng
    const cellWithXmlEmptyV = {
        value: { formula: 'IF(L13>5, $K13*J13*0.9,"")' },
        type: 6,
        address: 'O14',
        worksheet: { name: 'Phụ lục I.1' }
    };
    const mockXmlIndex = {
        'Phụ lục I.1': {
            'O14': { hasFormula: true, hasV: true, isEmptyV: true }
        }
    };
    const resEmptyXml = ExcelParser.readCellValue(cellWithXmlEmptyV, null, { xmlIndex: mockXmlIndex });
    assert.strictEqual(resEmptyXml.status, CellStatus.FORMULA_EMPTY, 'Phải nhận diện FORMULA_EMPTY khi XML có <v/> rỗng');
    assert.strictEqual(resEmptyXml.value, '');

    // 3.3. Công thức có "" trong cú pháp NHƯNG XML hoàn toàn không có thẻ <v> -> Phải là FORMULA_NO_RESULT (Không được suy đoán thành FORMULA_EMPTY)
    const cellMissingVWithIfEmpty = {
        value: { formula: 'IF(L1052>5, $K1052*J1052*0.9, "")' },
        type: 6,
        address: 'O1052',
        worksheet: { name: 'Phụ lục I.1' }
    };
    const mockXmlIndexNoV = {
        'Phụ lục I.1': {
            'O1052': { hasFormula: true, hasV: false, isEmptyV: false }
        }
    };
    const resNoResultXml = ExcelParser.readCellValue(cellMissingVWithIfEmpty, null, { xmlIndex: mockXmlIndexNoV });
    assert.strictEqual(resNoResultXml.status, CellStatus.FORMULA_NO_RESULT, 'Công thức thiếu hoàn toàn <v> trong XML BẮT BUỘC là FORMULA_NO_RESULT dù cú pháp có chuỗi rỗng ""');
    assert.strictEqual(resNoResultXml.value, null);
    console.log('-> PASS: Phân biệt chính xác giữa <v/> rỗng hợp lệ và thiếu hoàn toàn <v> qua XML index, đã loại bỏ 100% regex heuristic.\n');

    // ----------------------------------------------------
    // TEST 4: Công thức thiếu kết quả (FORMULA_NO_RESULT)
    // ----------------------------------------------------
    console.log('TEST 4: Công thức thiếu kết quả cache (chưa được tính toán)');
    const cellNoResult = {
        value: { formula: 'A1*B1+C1' },
        type: 6,
        text: '',
        address: 'C1'
    };
    const resNoResult = ExcelParser.readCellValue(cellNoResult);
    assert.strictEqual(resNoResult.status, CellStatus.FORMULA_NO_RESULT);
    assert.strictEqual(resNoResult.value, null);
    assert.strictEqual(resNoResult.valueSource, 'none');
    assert.strictEqual(resNoResult.rawText, '[Lỗi ô: Chưa tính kết quả]');
    assert.strictEqual(ExcelParser.parseNumber(cellNoResult), null, 'Tuyệt đối không âm thầm gán 0 cho ô chưa tính');
    assert.strictEqual(ExcelParser.getCellText(cellNoResult), '[Lỗi ô: Chưa tính kết quả]');
    console.log('-> PASS: Báo đúng lỗi Chưa tính kết quả khi thiếu cache, không âm thầm gán 0.\n');

    // ----------------------------------------------------
    // TEST 5: Công thức lỗi Excel (#DIV/0!, #VALUE!, #REF!...)
    // ----------------------------------------------------
    console.log('TEST 5: Công thức lỗi Excel');
    const errorCases = [
        { err: '#DIV/0!', formula: '1/0' },
        { err: '#VALUE!', formula: '1+"ABC"' },
        { err: '#REF!', formula: '#REF!+5' },
        { err: '#NAME?', formula: 'UNKNOWNFUNC(1)' }
    ];

    for (const ec of errorCases) {
        const cellErrStr = {
            value: { formula: ec.formula, result: ec.err },
            type: 6,
            address: 'E1'
        };
        const resErrStr = ExcelParser.readCellValue(cellErrStr);
        assert.strictEqual(resErrStr.status, CellStatus.FORMULA_ERROR);
        assert.strictEqual(resErrStr.errorCode, ec.err);
        assert.strictEqual(ExcelParser.parseNumber(cellErrStr), null);
        assert.strictEqual(ExcelParser.getCellText(cellErrStr), `[Lỗi ô: ${ec.err}]`);

        const cellErrObj = {
            value: { formula: ec.formula, error: ec.err },
            type: 6,
            address: 'E2'
        };
        const resErrObj = ExcelParser.readCellValue(cellErrObj);
        assert.strictEqual(resErrObj.status, CellStatus.FORMULA_ERROR);
        assert.strictEqual(resErrObj.errorCode, ec.err);
    }
    console.log('-> PASS: Nhận diện chính xác và bảo toàn mã lỗi Excel chuẩn.\n');

    // ----------------------------------------------------
    // TEST 6: Giá trị thường (Literal Number 0, Text, Blank)
    // ----------------------------------------------------
    console.log('TEST 6: Ô giá trị thường (Literal)');
    const cellLiteralZero = { value: 0, address: 'F1' };
    const resLitZero = ExcelParser.readCellValue(cellLiteralZero);
    assert.strictEqual(resLitZero.status, CellStatus.VALUE);
    assert.strictEqual(resLitZero.value, 0);
    assert.strictEqual(ExcelParser.parseNumber(cellLiteralZero), 0);

    const cellBlank = { value: null, address: 'F2' };
    const resBlank = ExcelParser.readCellValue(cellBlank);
    assert.strictEqual(resBlank.status, CellStatus.BLANK);
    assert.strictEqual(resBlank.value, null);
    assert.strictEqual(ExcelParser.parseNumber(cellBlank), null);
    assert.strictEqual(ExcelParser.getCellText(cellBlank), '');

    const cellWhitespace = { value: '   ', address: 'F3' };
    const resWs = ExcelParser.readCellValue(cellWhitespace);
    assert.strictEqual(resWs.status, CellStatus.BLANK);
    console.log('-> PASS: Phân biệt rõ số 0 thực sự với ô trống hoặc chuỗi trắng.\n');

    // ----------------------------------------------------
    // TEST 7: Kiểm tra trực tiếp trên file INPUT QK4 (input/12. Phu lục QK4.xlsx)
    // ----------------------------------------------------
    console.log('TEST 7: Kiểm tra trên file thực tế INPUT QK4');
    const inputPath = path.resolve(__dirname, '../../../../input/12. Phu lục QK4.xlsx');
    assert.ok(fs.existsSync(inputPath), 'Bắt buộc phải tồn tại file input/12. Phu lục QK4.xlsx');
    
    const inputBuf = fs.readFileSync(inputPath);
    const inputXmlIndex = ExcelParser.buildXmlFormulaIndex(inputBuf);
    assert.ok(Object.keys(inputXmlIndex).length > 0, 'Phải lập được XML index từ file input QK4');

    const wbInput = new ExcelJS.Workbook();
    await wbInput.xlsx.load(inputBuf);
    wbInput._xmlIndex = inputXmlIndex;

    const wsI1Input = wbInput.getWorksheet('Phụ lục I.1');
    assert.ok(wsI1Input, 'Phải có worksheet Phụ lục I.1');

    // 7.1. Kiểm tra ô S15
    const cellS15 = wsI1Input.getCell('S15');
    const resS15 = ExcelParser.readCellValue(cellS15);
    console.log('  Ô S15 QK4 formula:', resS15.formula);
    console.log('  Ô S15 QK4 status:', resS15.status, 'value:', resS15.value);
    assert.strictEqual(resS15.status, CellStatus.FORMULA_CACHED, 'S15 phải đọc được trạng thái FORMULA_CACHED');
    assert.strictEqual(resS15.value, 160828200, 'S15 phải đọc đúng giá trị cache 160828200');
    assert.strictEqual(ExcelParser.parseNumber(cellS15), 160828200);

    // 7.2. Kiểm tra ô O12 (Công thức IF rỗng có thẻ <v/> trong XML)
    const cellO12 = wsI1Input.getCell('O12');
    const resO12 = ExcelParser.readCellValue(cellO12);
    console.log('  Ô O12 QK4 status:', resO12.status, 'value:', JSON.stringify(resO12.value));
    assert.strictEqual(resO12.status, CellStatus.FORMULA_EMPTY, 'O12 có <v/> rỗng trong XML phải có status FORMULA_EMPTY');
    assert.strictEqual(resO12.value, '');

    // 7.3. Tạo báo cáo công thức toàn workbook input
    const repInput = ExcelParser.generateFormulaReport(wbInput);
    console.log('  Thống kê workbook INPUT QK4:');
    console.log('    - Tổng số công thức:', repInput.totalFormulas);
    console.log('    - Công thức có cache:', repInput.cachedFormulas);
    console.log('    - Công thức rỗng "":', repInput.emptyFormulas);
    console.log('    - Công thức chưa tính:', repInput.noResultFormulas);
    console.log('    - Công thức lỗi:', repInput.errorFormulas);
    assert.strictEqual(repInput.noResultFormulas, 0, 'File input QK4 chuẩn phải có đúng 0 ô công thức thiếu kết quả (không bị báo nhầm)');
    assert.ok(repInput.emptyFormulas >= 6000, 'Số ô công thức rỗng phải được nhận diện chính xác');
    console.log('-> PASS: Kiểm tra file INPUT QK4 đạt 100% tiêu chí đề ra.\n');

    // ----------------------------------------------------
    // TEST 8: Kiểm tra trực tiếp trên file OUTPUT QK4 (output/12. Phu lục QK4_tham_dinh_BQP (1).xlsx)
    // ----------------------------------------------------
    console.log('TEST 8: Kiểm tra trên file thực tế OUTPUT QK4 (Chứa 8 ô công thức thiếu cache)');
    const outPath = path.resolve(__dirname, '../../../../output/12. Phu lục QK4_tham_dinh_BQP (1).xlsx');
    assert.ok(fs.existsSync(outPath), 'Bắt buộc phải tồn tại file output QK4');

    const outBuf = fs.readFileSync(outPath);
    const outXmlIndex = ExcelParser.buildXmlFormulaIndex(outBuf);

    const wbOut = new ExcelJS.Workbook();
    await wbOut.xlsx.load(outBuf);
    wbOut._xmlIndex = outXmlIndex;

    const repOut = ExcelParser.generateFormulaReport(wbOut);
    console.log('  Thống kê workbook OUTPUT QK4:');
    console.log('    - Tổng số công thức:', repOut.totalFormulas);
    console.log('    - Công thức có cache:', repOut.cachedFormulas);
    console.log('    - Công thức rỗng "":', repOut.emptyFormulas);
    console.log('    - Công thức chưa tính:', repOut.noResultFormulas);
    console.log('    - Công thức lỗi:', repOut.errorFormulas);
    assert.strictEqual(repOut.noResultFormulas, 8, 'File output QK4 phải xác định đúng chính xác 8 công thức thiếu cache!');

    const wsI1Out = wbOut.getWorksheet('Phụ lục I.1');
    const missingCacheAddrs = ['O12', 'O1052', 'R1052', 'S1052', 'T1052', 'U1052', 'V1052', 'W1052'];
    for (const addr of missingCacheAddrs) {
        const c = wsI1Out.getCell(addr);
        const r = ExcelParser.readCellValue(c);
        assert.strictEqual(r.status, CellStatus.FORMULA_NO_RESULT, `Ô ${addr} thiếu cache trong XML phải có status FORMULA_NO_RESULT, không được coi là FORMULA_EMPTY!`);
        assert.strictEqual(r.value, null);
    }
    console.log('-> PASS: Toàn bộ 8 ô thiếu cache của file output QK4 được nhận diện đúng là FORMULA_NO_RESULT.\n');

    // ----------------------------------------------------
    // TEST 9: Kiểm tra hồ sơ thiếu prerequisite (INCOMPLETE_INPUT)
    // ----------------------------------------------------
    console.log('TEST 9: Kiểm tra hồ sơ thiếu điều kiện tiên quyết (Prerequisite)');
    
    // 9.1. Thiếu tiền lương tháng
    const fakeRowMissingSalary = {
        sheet: 'I.1',
        hoTen: 'Nguyễn Văn A',
        capBac: 'Thượng tá',
        ngaySinh: new Date(1975, 4, 1),
        nhapNgu: new Date(1993, 2, 1),
        thoiDiemNghi: new Date(2025, 6, 1),
        luongThang: null,
        rawCols: { 9: null, 23: 150000000 },
        cellErrors: { 9: '[Lỗi: Chưa tính kết quả]' }
    };
    const valResSalary = BQPValidation.ValidationService.validateRow('I.1', fakeRowMissingSalary);
    assert.strictEqual(valResSalary.status, 'INCOMPLETE_INPUT');
    assert.strictEqual(valResSalary.actualTotal, null, 'Hồ sơ thiếu lương không được đưa ra actualTotal');
    assert.strictEqual(valResSalary.expectedTotal, null, 'Hồ sơ thiếu lương không được tính expectedTotal');
    assert.strictEqual(valResSalary.diff, null, 'Hồ sơ thiếu lương không được tính chênh lệch thừa/thiếu');
    assert.strictEqual(valResSalary.hasErrors, true);
    assert.ok(valResSalary.errorDetails[0].includes('Tiền lương tháng bình quân'));

    // 9.2. Thiếu thời điểm nghỉ
    const fakeRowMissingDate = {
        sheet: 'I.1',
        hoTen: 'Trần Văn B',
        capBac: 'Đại tá',
        ngaySinh: new Date(1973, 1, 1),
        nhapNgu: new Date(1991, 8, 1),
        thoiDiemNghi: null,
        luongThang: 30000000,
        rawCols: { 9: 30000000, 23: 200000000 },
        cellErrors: { 8: '[Lỗi ô: #VALUE!]' }
    };
    const valResDate = BQPValidation.ValidationService.validateRow('I.1', fakeRowMissingDate);
    assert.strictEqual(valResDate.status, 'INCOMPLETE_INPUT');
    assert.strictEqual(valResDate.actualTotal, null);
    assert.strictEqual(valResDate.expectedTotal, null);
    assert.strictEqual(valResDate.diff, null);

    // 9.3. Phụ lục I.5 thiếu hệ số lương hoặc lương cột 22
    const fakeRowI5 = {
        sheet: 'I.5',
        hoTen: 'Lê Văn C',
        capBac: 'Thiếu tá',
        heSoLuong: null,
        nhapNgu: new Date(2005, 1, 1),
        thoiDiemNghi: new Date(2025, 1, 1),
        luongThang: null,
        rawCols: { 6: null, 22: null },
        cellErrors: { 6: '[Lỗi: Chưa tính kết quả]' }
    };
    const valResI5 = BQPValidation.ValidationService.validateRow('I.5', fakeRowI5);
    assert.strictEqual(valResI5.status, 'INCOMPLETE_INPUT');
    assert.strictEqual(valResI5.actualTotal, null);
    assert.strictEqual(valResI5.expectedTotal, null);
    assert.strictEqual(valResI5.diff, null);
    console.log('-> PASS: Kiểm tra Prerequisite hồ sơ đạt chuẩn mực: Tuyệt đối không tính toán kết luận giả tạo trên ô lỗi.\n');

    console.log('====================================================');
    console.log('TẤT CẢ 9/9 BÀI KIỂM THỬ ĐỌC CÔNG THỨC EXCEL ĐÃ PASS 100%!');
    console.log('====================================================');
}

runTests().catch(err => {
    console.error('TEST THẤT BẠI:', err);
    process.exit(1);
});
