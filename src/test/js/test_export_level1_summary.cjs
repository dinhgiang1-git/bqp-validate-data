/**
 * test_export_level1_summary.cjs
 *
 * Kiểm thử toàn diện chức năng xuất báo cáo cấp 1:
 * Phụ lục I–IV tổng hợp theo đơn vị cấp 2 khi chọn đơn vị C1 (toàn nhánh con).
 *
 * Tuân thủ đầy đủ đặc tả, ma trận và tiêu chí nghiệm thu tại:
 * D:\bqp\KE_HOACH_XUAT_BAO_CAO_CAP_1_TONG_HOP_CAP_2.md
 */

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ExcelJS = require('exceljs');
const { reportContext, totalRow, numeric, cellText } = require('./bqp_export_test_helpers.cjs');

const staticDir = path.resolve(__dirname, '../../main/resources/static');
const htmlPath = path.join(staticDir, 'index.html');
const jsPath = path.join(staticDir, 'bqp_validation.js');
const normJsPath = path.join(staticDir, 'bqp_normalization.js');

let passCount = 0;
function pass(msg) {
    console.log(`  ✅ [PASS] ${msg}`);
    passCount++;
}

/**
 * Tạo mock sandbox môi trường chạy script validation
 */
function createSandbox(useInlineScript = false) {
    let capturedBlobBuffer = null;

    const sandbox = {
        console,
        Date,
        setTimeout,
        clearTimeout,
        ExcelJS,
        Buffer,
        Set,
        Map,
        Array,
        Object,
        String,
        Number,
        Math,
        Boolean,
        RegExp,
        JSON,
        Error,
        Blob: class {
            constructor(parts) {
                if (parts && parts[0]) {
                    capturedBlobBuffer = parts[0];
                }
            }
        },
        URL: {
            createObjectURL() { return 'blob://mock-export-url'; },
            revokeObjectURL() {}
        },
        document: {
            addEventListener() {},
            getElementById(id) {
                return {
                    id,
                    value: '',
                    textContent: '',
                    style: {},
                    classList: { add() {}, remove() {}, toggle() {} }
                };
            },
            querySelector() { return null; },
            querySelectorAll() { return []; },
            createElement() {
                return {
                    click() {},
                    setAttribute() {},
                    style: {},
                    download: '',
                    href: ''
                };
            },
            body: {
                appendChild() {}
            }
        },
        window: null,
        notifySuccess: () => {},
        notifyWarning: () => {},
        notifyError: () => {},
        notifyInfo: () => {},
        BQPLoader: {
            show() {},
            hide() {}
        },
        BqpStorageAdapter: { isSqlite: false },
        StorageManager: {
            getRecordsBySource() { return []; },
            getUnits() { return []; }
        },
        UnitTreeManager: {
            getAllDescendantIds() { return []; }
        },
        RecordsUI: {
            getActiveFilters() { return {}; }
        },
        getCapturedBuffer: () => capturedBlobBuffer
    };
    sandbox.window = sandbox;

    const ctx = vm.createContext(sandbox);

    // Nạp bqp_normalization.js
    vm.runInContext(fs.readFileSync(normJsPath, 'utf8'), ctx);

    if (useInlineScript) {
        // Trích xuất script validation inline từ index.html
        const html = fs.readFileSync(htmlPath, 'utf8');
        const lastScriptIdx = html.lastIndexOf('<script');
        const scriptOpenEnd = html.indexOf('>', lastScriptIdx) + 1;
        const scriptCloseIdx = html.indexOf('</script>', lastScriptIdx);
        const inlineJs = html.substring(scriptOpenEnd, scriptCloseIdx);
        vm.runInContext(inlineJs, ctx);
    } else {
        // Nạp bqp_validation.js trực tiếp
        vm.runInContext(fs.readFileSync(jsPath, 'utf8'), ctx);
    }

    return sandbox;
}

/**
 * Tạo base workbook mẫu chuẩn có sheet Phụ lục I, I.1, I.2, I.3
 */
async function createBaseWorkbook() {
    const wb = new ExcelJS.Workbook();

    // Sheet Phụ lục I mẫu
    const wsI = wb.addWorksheet('Phụ lục I');
    for (let c = 1; c <= 11; c++) {
        wsI.getColumn(c).width = (c === 2 ? 30 : 16);
    }
    // Dòng 1-7 tiêu đề
    wsI.getCell('C1').value = 'PHỤ LỤC I';
    wsI.getCell('C2').value = 'TỔNG HỢP KINH PHÍ CHI TRẢ CHẾ ĐỘ';
    wsI.getCell('A5').value = 'STT';
    wsI.getCell('B5').value = 'Đơn vị';
    wsI.getCell('C5').value = 'Chế độ 178';
    wsI.getCell('E5').value = 'Chế độ thôi việc';
    wsI.getCell('G5').value = 'Chế độ 177';
    wsI.getCell('I5').value = 'Tổng số tiền';
    wsI.getCell('K5').value = 'Ghi chú';
    wsI.getCell('C6').value = 'Số người';
    wsI.getCell('D6').value = 'Số tiền';
    wsI.getCell('E6').value = 'Số người';
    wsI.getCell('F6').value = 'Số tiền';
    wsI.getCell('G6').value = 'Số người';
    wsI.getCell('H6').value = 'Số tiền';
    wsI.getCell('I6').value = 'Số người';
    wsI.getCell('J6').value = 'Số tiền';
    for (let r = 1; r <= 7; r++) {
        const row = wsI.getRow(r);
        row.height = 24;
    }
    // Dòng mẫu cũ từ template (cần được làm sạch khi xuất)
    const oldRow = wsI.getRow(8);
    oldRow.getCell(1).value = 99;
    oldRow.getCell(2).value = 'Đơn vị mẫu cũ';
    oldRow.getCell(4).value = 99999999;

    function setupDetailSheet(ws, colCount, donViCol) {
        ws.getCell('A8').value = 'STT';
        ws.getCell('B8').value = 'Họ và tên';
        ws.getCell('C8').value = 'Năm sinh';
        ws.getCell('D8').value = 'Cấp bậc';
        ws.getCell('E8').value = 'Chức vụ';
        ws.getCell('F8').value = 'Nhập ngũ';
        ws.getCell('H8').value = 'Thời điểm nghỉ';
        ws.getCell('I8').value = 'Lương tháng';
        ws.getCell(donViCol + '8').value = 'Đơn vị';

        for (let c = 1; c <= colCount; c++) {
            ws.getRow(9).getCell(c).value = c;
        }

        // Add 1 sample data row in template
        const row10 = ws.getRow(10);
        row10.getCell(1).value = 1;
        row10.getCell(2).value = 'Mẫu cũ';
        row10.getCell(9).value = 10000;
        row10.getCell(colCount).value = 10000;
        row10.getCell(donViCol === 'X' ? 24 : (donViCol === 'S' ? 19 : 16)).value = 'Đơn vị cũ';
    }

    // Sheet chi tiết I.1 (23 cột tiền, đơn vị ở cột 24 'X')
    const wsI1 = wb.addWorksheet('Phụ lục I.1');
    setupDetailSheet(wsI1, 23, 'X');

    // Sheet chi tiết I.2 (18 cột tiền, đơn vị ở cột 19 'S')
    const wsI2 = wb.addWorksheet('Phụ lục I.2');
    setupDetailSheet(wsI2, 18, 'S');

    // Sheet chi tiết I.3 (15 cột tiền, đơn vị ở cột 16 'P')
    const wsI3 = wb.addWorksheet('Phụ lục I.3');
    setupDetailSheet(wsI3, 15, 'P');

    return wb;
}

/**
 * Fixture danh mục cây đơn vị 4 cấp theo đặc tả Section 6
 */
function createTreeFixture() {
    return [
        { id: 'c1_a', name: 'Bộ Tư lệnh Quân khu A', level: 1, parentId: null, orderIndex: 1 },
        { id: 'c2_a', name: '8. Phụ lục BTL thủ đô Hà Nội', level: 2, parentId: 'c1_a', orderIndex: 8 },
        { id: 'c3_a1', name: 'Ban Chỉ huy PTKV 1 - Sóc Sơn', level: 3, parentId: 'c2_a', orderIndex: 1 },
        { id: 'c4_a1', name: 'Đại đội 1 Sóc Sơn', level: 4, parentId: 'c3_a1', orderIndex: 1 },
        { id: 'c3_a2', name: 'Phòng Tài chính', level: 3, parentId: 'c2_a', orderIndex: 2 },

        { id: 'c2_b', name: '9. Phụ lục Quân Khu I', level: 2, parentId: 'c1_a', orderIndex: 9 },
        { id: 'c3_b1', name: 'Bộ CHQS tỉnh Bắc Ninh', level: 3, parentId: 'c2_b', orderIndex: 1 },
        { id: 'c4_b1', name: 'Trung đội Thông tin', level: 4, parentId: 'c3_b1', orderIndex: 1 },
        { id: 'c3_b2', name: 'Phòng Tài chính', level: 3, parentId: 'c2_b', orderIndex: 2 }, // Trùng tên để kiểm tra grouping bằng ID

        // Nhánh C1 mồi (Decoy)
        { id: 'c1_b', name: 'Quân đoàn B', level: 1, parentId: null, orderIndex: 2 },
        { id: 'c2_c', name: 'Sư đoàn 301', level: 2, parentId: 'c1_b', orderIndex: 1 }
    ];
}

/**
 * Fixture tập hồ sơ theo ma trận Section 6
 */
function createRecordsFixture() {
    return [
        // Nhánh C2-A
        {
            id: 'rec_a1',
            hoTen: 'Nguyễn Văn A1 (C2 trực tiếp)',
            unitId: 'c2_a',
            group: '8. Phụ lục BTL thủ đô Hà Nội',
            donVi: '8. Phụ lục BTL thủ đô Hà Nội',
            sheet: 'I.1',
            sheetSource: 'I.1',
            tongTienThucTe: 10000,
            tongTienTinhLai: 10000,
            diff: 0,
            hasErrors: false
        },
        {
            id: 'rec_a2',
            hoTen: 'Trần Văn A2 (C3 cấp thừa)',
            unitId: 'c3_a2',
            group: 'Phòng Tài chính Hà Nội',
            donVi: 'Phòng Tài chính',
            sheet: 'I.2',
            sheetSource: 'I.2',
            tongTienThucTe: 20000,
            tongTienTinhLai: 15000,
            diff: -5000,
            hasErrors: true
        },
        {
            id: 'rec_a3',
            hoTen: 'Lê Văn A3 (C4 cấp thiếu)',
            unitId: 'c4_a1',
            group: 'Đại đội 1 Sóc Sơn',
            donVi: 'Đại đội 1 Sóc Sơn',
            sheet: 'I.3',
            sheetSource: 'I.3',
            tongTienThucTe: 30000,
            tongTienTinhLai: 35000,
            diff: 5000,
            hasErrors: true
        },

        // Nhánh C2-B
        {
            id: 'rec_b1',
            hoTen: 'Phạm Văn B1 (C2 trực tiếp)',
            unitId: 'c2_b',
            group: '9. Phụ lục Quân Khu I',
            donVi: '9. Phụ lục Quân Khu I',
            sheet: 'I.1',
            sheetSource: 'I.1',
            tongTienThucTe: 40000,
            tongTienTinhLai: 40000,
            diff: 0,
            hasErrors: false
        },
        {
            id: 'rec_b2',
            hoTen: 'Hoàng Văn B2 (C3 cấp thừa)',
            unitId: 'c3_b2',
            group: 'Phòng Tài chính QK1',
            donVi: 'Phòng Tài chính',
            sheet: 'I.2',
            sheetSource: 'I.2',
            tongTienThucTe: 50000,
            tongTienTinhLai: 45000,
            diff: -5000,
            hasErrors: true
        },
        {
            id: 'rec_b3',
            hoTen: 'Vũ Văn B3 (C4 cấp thiếu)',
            unitId: 'c4_b1',
            group: 'Trung đội Thông tin Bắc Ninh',
            donVi: 'Trung đội Thông tin',
            sheet: 'I.3',
            sheetSource: 'I.3',
            tongTienThucTe: 60000,
            tongTienTinhLai: 65000,
            diff: 5000,
            hasErrors: true
        }
    ];
}

async function runTestSuite() {
    console.log('================================================================');
    console.log('BẮT ĐẦU KIỂM THỬ: XUẤT BÁO CÁO CẤP 1 TỔNG HỢP ĐƠN VỊ CẤP 2');
    console.log('================================================================\n');

    // -------------------------------------------------------------
    // Test 1: Đơn vị Resolver logic & Edge Cases (P0)
    // -------------------------------------------------------------
    console.log('--- TEST 1: Resolver ánh xạ đơn vị sang C2 theo ID & Edge Cases ---');
    {
        const sb = createSandbox(false);
        const units = createTreeFixture();
        const resolver = sb.BQPValidation.buildLevel2Resolver(units, 'c1_a');

        // Ánh xạ C2 trực tiếp -> chính C2 đó
        const resC2 = resolver.resolveUnit('c2_a');
        assert.strictEqual(resC2.ok, true);
        assert.strictEqual(resC2.l2Unit.id, 'c2_a');
        assert.strictEqual(resC2.l2Unit.name, '8. Phụ lục BTL thủ đô Hà Nội');
        assert.strictEqual(resC2.l2Unit.displayOrder, 8);

        // Ánh xạ C3 -> C2 cha
        const resC3 = resolver.resolveUnit('c3_a2');
        assert.strictEqual(resC3.ok, true);
        assert.strictEqual(resC3.l2Unit.id, 'c2_a');

        // Ánh xạ C4 -> C2 tổ tiên
        const resC4 = resolver.resolveUnit('c4_a1');
        assert.strictEqual(resC4.ok, true);
        assert.strictEqual(resC4.l2Unit.id, 'c2_a');

        // Hai đơn vị C3 cùng tên "Phòng Tài chính" thuộc 2 C2 khác nhau được phân biệt bằng ID
        const resC3_A = resolver.resolveUnit('c3_a2');
        const resC3_B = resolver.resolveUnit('c3_b2');
        assert.strictEqual(resC3_A.l2Unit.id, 'c2_a');
        assert.strictEqual(resC3_B.l2Unit.id, 'c2_b');

        // Ca lỗi: Đơn vị gắn trực tiếp C1 (không có C2)
        const resDirectC1 = resolver.resolveUnit('c1_a');
        assert.strictEqual(resDirectC1.ok, false);
        assert(resDirectC1.error.includes('trực tiếp'), 'Phải báo lỗi gắn trực tiếp C1');

        // Ca lỗi: Đơn vị thuộc C1 khác (c1_b)
        const resOtherC1 = resolver.resolveUnit('c2_c');
        assert.strictEqual(resOtherC1.ok, false);
        assert(resOtherC1.error.includes('không thuộc nhánh'), 'Phải báo lỗi không thuộc nhánh C1 đã chọn');

        // Ca lỗi: unitId rỗng hoặc không tồn tại
        assert.strictEqual(resolver.resolveUnit('').ok, false);
        assert.strictEqual(resolver.resolveUnit('non_existent_id').ok, false);

        // Ca lỗi: Vòng lặp cây đơn vị
        const cyclicUnits = [
            { id: 'c1', name: 'C1', parentId: null },
            { id: 'loop1', name: 'Loop 1', parentId: 'loop2' },
            { id: 'loop2', name: 'Loop 2', parentId: 'loop1' }
        ];
        const cyclicResolver = sb.BQPValidation.buildLevel2Resolver(cyclicUnits, 'c1');
        const resLoop = cyclicResolver.resolveUnit('loop1');
        assert.strictEqual(resLoop.ok, false);
        assert(resLoop.error.includes('vòng lặp'), 'Phải phát hiện vòng lặp cây đơn vị');

        pass('Resolver ánh xạ C2/C3/C4 chính xác theo ID, chặn vòng lặp, chặn đơn vị C1 khác và phát hiện lỗi gắn trực tiếp C1');
    }

    // -------------------------------------------------------------
    // Test 2: Preflight Validation khi có hồ sơ không ánh xạ được (P0)
    // -------------------------------------------------------------
    console.log('\n--- TEST 2: Preflight Validation chặn xuất và báo lỗi rõ ràng ---');
    {
        const sb = createSandbox(false);
        const units = createTreeFixture();
        const baseWb = await createBaseWorkbook();
        const badRecords = [
            { id: 'bad1', hoTen: 'Chiến sĩ lỗi', unitId: 'c1_a', sheet: 'I.1', tongTienThucTe: 1000 }
        ];
        const exportContext = {
            ...await reportContext(),
            selectedUnitId: 'c1_a',
            scope: 'branch',
            units,
            summaryLevel: 2
        };

        let threw = false;
        try {
            await sb.BQPValidation.exportValidatedWorkbook(baseWb, 'Test.xlsx', badRecords, true, 'Tat_Ca', null, exportContext);
        } catch (e) {
            threw = true;
            assert(e.message.includes('Không thể xuất báo cáo cấp 1'), 'Thông báo lỗi phải rõ ràng');
            assert(e.message.includes('Chiến sĩ lỗi'), 'Phải nêu tên hồ sơ bị lỗi');
        }
        assert.strictEqual(threw, true, 'Bắt buộc phải dừng và ném lỗi khi có hồ sơ gắn trực tiếp C1');

        pass('Preflight Validation phát hiện chính xác hồ sơ không thể ánh xạ và dừng xuất file an toàn');
    }

    // -------------------------------------------------------------
    // Test 3: Xuất báo cáo cấp 1 với dữ liệu chuẩn theo ma trận Section 6 (P0)
    // -------------------------------------------------------------
    console.log('\n--- TEST 3: Kiểm tra cấu trúc 4 Phụ lục I, II, III, IV trên Workbook thật ---');
    {
        const sb = createSandbox(false);
        const units = createTreeFixture();
        const baseWb = await createBaseWorkbook();
        const records = createRecordsFixture();

        const exportContext = {
            ...await reportContext(),
            source: 'validated',
            selectedUnitId: 'c1_a',
            scope: 'branch',
            filters: { unitId: 'c1_a', scope: 'branch' },
            units,
            summaryLevel: 2
        };

        await sb.BQPValidation.exportValidatedWorkbook(
            baseWb,
            '8. Phu luc BTL thủ đô Hà Nội.xlsx',
            records,
            true,
            'Da_Loc',
            records,
            exportContext
        );

        const exportedBuf = sb.getCapturedBuffer();
        assert(exportedBuf, 'Phải tạo và bắt được file Excel xuất');

        const outWb = new ExcelJS.Workbook();
        await outWb.xlsx.load(exportedBuf);

        // 3.1. Kiểm tra sheet "Phụ lục I"
        const wsI = outWb.getWorksheet('Phụ lục I');
        assert(wsI, 'Workbook xuất phải có sheet Phụ lục I');

        // Hai dòng C2 từ hàng 8; tổng/ghi chú giữ vị trí của template BQP thật.
        const row8 = wsI.getRow(8);
        const row9 = wsI.getRow(9);
        const row10 = totalRow(wsI);
        const row11 = wsI.getRow(row10.number + 1);

        assert.strictEqual(row8.getCell(1).value, 1);
        assert.strictEqual(row8.getCell(2).value, '8. Phụ lục BTL thủ đô Hà Nội');
        assert.strictEqual(row8.getCell(3).value, 1, 'C2-A số người I.1 = 1');
        assert.strictEqual(row8.getCell(4).value, 10000, 'C2-A tiền I.1 = 10.000');
        assert.strictEqual(row8.getCell(5).value, 1, 'C2-A số người I.2 = 1');
        assert.strictEqual(row8.getCell(7).value, 1, 'C2-A số người I.3 = 1');
        assert.strictEqual(row8.getCell(9).value, 3, 'C2-A tổng số người = 3');
        assert.strictEqual(row8.getCell(10).value, 60000, 'C2-A tổng tiền = 60.000');

        assert.strictEqual(row9.getCell(1).value, 2);
        assert.strictEqual(row9.getCell(2).value, '9. Phụ lục Quân Khu I');
        assert.strictEqual(row9.getCell(3).value, 1, 'C2-B số người I.1 = 1');
        assert.strictEqual(row9.getCell(4).value, 40000, 'C2-B tiền I.1 = 40.000');
        assert.strictEqual(row9.getCell(5).value, 1, 'C2-B số người I.2 = 1');
        assert.strictEqual(row9.getCell(7).value, 1, 'C2-B số người I.3 = 1');
        assert.strictEqual(row9.getCell(9).value, 3, 'C2-B tổng số người = 3');
        assert.strictEqual(row9.getCell(10).value, 150000, 'C2-B tổng tiền = 150.000');

        // Dòng TỔNG CỘNG
        assert(row10, 'Phải giữ hàng tổng của template');
        assert.strictEqual(row10.getCell(3).value, 2, 'Tổng số người I.1 = 2');
        assert.strictEqual(row10.getCell(4).value, 50000, 'Tổng tiền I.1 = 50.000');
        assert.strictEqual(row10.getCell(5).value, 2, 'Tổng số người I.2 = 2');
        assert.strictEqual(row10.getCell(7).value, 2, 'Tổng số người I.3 = 2');
        assert.strictEqual(row10.getCell(9).value, 6, 'Tổng toàn bộ số người = 6');
        assert.strictEqual(row10.getCell(10).value, 210000, 'Tổng toàn bộ số tiền = 210.000');

        // Dòng ghi chú
        assert(cellText(row11.getCell(1)).includes('Ghi chú'), 'Giữ ghi chú ngay sau hàng tổng');

        // Tuyệt đối không có dòng thứ 12 trở đi
        const row12 = wsI.getRow(12);
        assert(!row12.getCell(2).value, 'Không được còn dòng thừa nào sau dòng ghi chú');

        // 3.2. Kiểm tra Phụ lục II (Đạt chuẩn)
        const wsII = outWb.getWorksheet('Phụ lục II');
        assert(wsII, 'Phải tạo sheet Phụ lục II');
        assert.strictEqual(wsII.getRow(8).getCell(2).value, '8. Phụ lục BTL thủ đô Hà Nội');
        assert.strictEqual(wsII.getRow(8).getCell(3).value, 1);
        assert.strictEqual(wsII.getRow(8).getCell(4).value, 10000);
        assert.strictEqual(wsII.getRow(9).getCell(2).value, '9. Phụ lục Quân Khu I');
        assert.strictEqual(wsII.getRow(9).getCell(3).value, 1);
        assert.strictEqual(wsII.getRow(9).getCell(4).value, 40000);
        assert.strictEqual(totalRow(wsII).getCell(9).value, 2, 'Phụ lục II tổng số người = 2');
        assert.strictEqual(numeric(totalRow(wsII).getCell(10).value), 50000, 'Phụ lục II tổng tiền = 50.000');

        // 3.3. Kiểm tra Phụ lục III (Cấp thừa)
        const wsIII = outWb.getWorksheet('Phụ lục III');
        assert(wsIII, 'Phải tạo sheet Phụ lục III');
        assert.strictEqual(wsIII.getRow(8).getCell(2).value, '8. Phụ lục BTL thủ đô Hà Nội');
        assert.strictEqual(wsIII.getRow(8).getCell(5).value, 1);
        assert.strictEqual(wsIII.getRow(9).getCell(2).value, '9. Phụ lục Quân Khu I');
        assert.strictEqual(wsIII.getRow(9).getCell(5).value, 1);
        assert.strictEqual(totalRow(wsIII).getCell(9).value, 2, 'Phụ lục III tổng số người = 2');
        // Tiền thẩm định: 15.000 + 45.000 = 60.000, Tiền gốc: 20.000 + 50.000 = 70.000, lệch -10.000
        assert.strictEqual(numeric(totalRow(wsIII).getCell(10).value), 60000, 'Phụ lục III tiền thẩm định = 60.000');
        assert(totalRow(wsIII).getCell(10).value.richText, 'Phụ lục III giữ cả tiền gốc và tiền thẩm định');

        // 3.4. Kiểm tra Phụ lục IV (Cấp thiếu)
        const wsIV = outWb.getWorksheet('Phụ lục IV');
        assert(wsIV, 'Phải tạo sheet Phụ lục IV');
        assert.strictEqual(wsIV.getRow(8).getCell(2).value, '8. Phụ lục BTL thủ đô Hà Nội');
        assert.strictEqual(wsIV.getRow(8).getCell(7).value, 1);
        assert.strictEqual(wsIV.getRow(9).getCell(2).value, '9. Phụ lục Quân Khu I');
        assert.strictEqual(wsIV.getRow(9).getCell(7).value, 1);
        assert.strictEqual(totalRow(wsIV).getCell(9).value, 2, 'Phụ lục IV tổng số người = 2');
        // Tiền thẩm định: 35.000 + 65.000 = 100.000, Tiền gốc: 30.000 + 60.000 = 90.000, lệch +10.000
        assert.strictEqual(numeric(totalRow(wsIV).getCell(10).value), 100000, 'Phụ lục IV tiền thẩm định = 100.000');

        pass('Cả 4 Phụ lục I, II, III, IV hiển thị chuẩn xác 2 dòng C2, khớp 100% số người, số tiền, chênh lệch và dòng TỔNG CỘNG');
    }

    // -------------------------------------------------------------
    // Test 4: Làm sạch nhóm rỗng trong chế độ C1 (không có dòng giả "Toàn đơn vị")
    // -------------------------------------------------------------
    console.log('\n--- TEST 4: Nhóm rỗng trong chế độ C1 làm sạch triệt để không có dòng giả ---');
    {
        const sb = createSandbox(false);
        const units = createTreeFixture();
        const baseWb = await createBaseWorkbook();

        // Chỉ có hồ sơ Đạt chuẩn (I.1), không có hồ sơ cấp thừa (III) hoặc cấp thiếu (IV)
        const cleanRecords = [
            {
                id: 'rec_clean_1',
                hoTen: 'Người chuẩn A',
                unitId: 'c2_a',
                group: 'BTL Thủ Đô',
                sheet: 'I.1',
                tongTienThucTe: 10000,
                tongTienTinhLai: 10000,
                diff: 0,
                hasErrors: false
            }
        ];

        const exportContext = {
            ...await reportContext(),
            source: 'validated',
            selectedUnitId: 'c1_a',
            scope: 'branch',
            units,
            summaryLevel: 2
        };

        await sb.BQPValidation.exportValidatedWorkbook(
            baseWb,
            'TestClean.xlsx',
            cleanRecords,
            true,
            'Da_Loc',
            cleanRecords,
            exportContext
        );

        const exportedBuf = sb.getCapturedBuffer();
        const outWb = new ExcelJS.Workbook();
        await outWb.xlsx.load(exportedBuf);

        // Sheet III (Cấp thừa) rỗng:
        const wsIII = outWb.getWorksheet('Phụ lục III');
        assert(wsIII, 'Phải khởi tạo sheet Phụ lục III dù rỗng');

        // Tổng và ghi chú giữ layout BQP, vùng dữ liệu rỗng không có đơn vị giả.
        const r8 = totalRow(wsIII);
        assert(r8, 'Nhóm rỗng vẫn có hàng tổng');
        assert.strictEqual(r8.getCell(9).value, 0, 'Tổng số người = 0');
        assert.strictEqual(r8.getCell(10).value, 0, 'Tổng tiền = 0');

        const r9 = wsIII.getRow(r8.number + 1);
        assert(cellText(r9.getCell(1)).includes('Ghi chú'), 'Giữ dòng ghi chú của template');

        // Không còn dòng thừa từ template
        for (let r = 8; r < r8.number; r++) assert(!wsIII.getRow(r).getCell(2).value, 'Không được còn dòng mẫu hoặc đơn vị giả');

        pass('Nhóm rỗng trong C1 giữ hàng tổng bằng 0 và ghi chú của mẫu BQP, loại bỏ dòng giả "Toàn đơn vị" và rác mẫu');
    }

    // -------------------------------------------------------------
    // Test 5: Không hồi quy — Non-C1 (Chọn C2, Toàn quân, hoặc không truyền context)
    // -------------------------------------------------------------
    console.log('\n--- TEST 5: Tương thích ngược tuyệt đối khi không chọn C1 ---');
    {
        const sb = createSandbox(false);
        const baseWb = await createBaseWorkbook();
        const records = [
            {
                id: 'r_legacy_1',
                hoTen: 'Người Sóc Sơn',
                group: 'Ban Chỉ huy PTKV 1 - Sóc Sơn',
                donVi: 'Ban Chỉ huy PTKV 1 - Sóc Sơn',
                sheet: 'I.1',
                tongTienThucTe: 5000,
                tongTienTinhLai: 5000,
                diff: 0,
                hasErrors: false
            }
        ];

        // Context chỉ xác minh template, không yêu cầu nhóm theo cấp 1.
        await sb.BQPValidation.exportValidatedWorkbook(
            baseWb,
            'Legacy.xlsx',
            records,
            false,
            'Tat_Ca',
            records,
            await reportContext()
        );

        const exportedBuf = sb.getCapturedBuffer();
        const outWb = new ExcelJS.Workbook();
        await outWb.xlsx.load(exportedBuf);

        const wsI = outWb.getWorksheet('Phụ lục I');
        // Phải giữ cách nhóm theo group/donVi cũ
        assert.strictEqual(wsI.getRow(8).getCell(2).value, 'Ban Chỉ huy PTKV 1 - Sóc Sơn');
        assert.strictEqual(wsI.getRow(8).getCell(4).value, 5000);

        pass('Không có exportContext giữ nguyên 100% cách nhóm hiển thị theo đơn vị chi tiết cũ');
    }

    // -------------------------------------------------------------
    // Test 6: Kiểm tra phiên bản inline trong index.html
    // -------------------------------------------------------------
    console.log('\n--- TEST 6: Kiểm tra script validation inline trong index.html ---');
    {
        const sbInline = createSandbox(true);
        const units = createTreeFixture();
        const baseWb = await createBaseWorkbook();
        const records = createRecordsFixture();

        const exportContext = {
            ...await reportContext(),
            source: 'validated',
            selectedUnitId: 'c1_a',
            scope: 'branch',
            units,
            summaryLevel: 2
        };

        await sbInline.BQPValidation.exportValidatedWorkbook(
            baseWb,
            'InlineTest.xlsx',
            records,
            true,
            'Da_Loc',
            records,
            exportContext
        );

        const exportedBuf = sbInline.getCapturedBuffer();
        assert(exportedBuf, 'Bản inline trong HTML phải xuất thành công');

        const outWb = new ExcelJS.Workbook();
        await outWb.xlsx.load(exportedBuf);

        const wsI = outWb.getWorksheet('Phụ lục I');
        assert.strictEqual(wsI.getRow(8).getCell(2).value, '8. Phụ lục BTL thủ đô Hà Nội');
        assert.strictEqual(wsI.getRow(9).getCell(2).value, '9. Phụ lục Quân Khu I');
        assert.strictEqual(totalRow(wsI).getCell(9).value, 6);

        pass('Script inline trong index.html chạy đồng nhất 100% với bqp_validation.js ngoài');
    }

    // -------------------------------------------------------------
    // Test 7: Kiểm tra ExportManager._calculateEffectiveLevel & context
    // -------------------------------------------------------------
    console.log('\n--- TEST 7: Kiểm tra ExportManager._calculateEffectiveLevel & context ---');
    {
        const html = fs.readFileSync(htmlPath, 'utf8');
        const startMarker = 'const ExportManager = {';
        const endMarker = 'function saveRecordDirect(prefix)';
        const startIdx = html.indexOf(startMarker);
        const endIdx = html.indexOf(endMarker);
        const emCode = html.substring(startIdx, endIdx).trim();

        const sb = {
            console,
            Set,
            Map,
            Array,
            Object,
            String,
            Number,
            Boolean,
            JSON,
            BqpStorageAdapter: { isSqlite: false },
            StorageManager: { getUnits() { return createTreeFixture(); }, getRecordsBySource() { return []; } },
            RecordsUI: { getActiveFilters() { return { unitId: 'c1_a', scope: 'branch' }; } }
        };
        const ctx = vm.createContext(sb);
        vm.runInContext(emCode + '\nglobalThis.ExportManager = ExportManager;', ctx);

        const units = createTreeFixture();
        // C1-A: Level 1
        assert.strictEqual(sb.ExportManager._calculateEffectiveLevel('c1_a', units), 1);
        // C2-A: Level 2
        assert.strictEqual(sb.ExportManager._calculateEffectiveLevel('c2_a', units), 2);
        // C3-A1: Level 3
        assert.strictEqual(sb.ExportManager._calculateEffectiveLevel('c3_a1', units), 3);
        // C4-A1: Level 4
        assert.strictEqual(sb.ExportManager._calculateEffectiveLevel('c4_a1', units), 4);
        // Null/empty unitId: null
        assert.strictEqual(sb.ExportManager._calculateEffectiveLevel('', units), null);
        assert.strictEqual(sb.ExportManager._calculateEffectiveLevel(null, units), null);

        pass('ExportManager._calculateEffectiveLevel tính chính xác cấp bậc của từng đơn vị');
    }

    console.log('\n================================================================');
    console.log(`🎉 TẤT CẢ ${passCount} / 7 NHÓM KIỂM THỬ XUẤT CẤP 1 ĐÃ VƯỢT QUA 100%!`);
    console.log('================================================================\n');
}

runTestSuite().catch(err => {
    console.error('\n❌ KIỂM THỬ THẤT BÀI:', err);
    process.exit(1);
});
