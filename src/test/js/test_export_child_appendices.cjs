/**
 * test_export_child_appendices.cjs
 * Kiem thu dien doi tuong vao phu luc con khi xuat danh sach tham dinh.
 * Dung mau that "BO QUOC PHONG.xlsx" theo ke hoach P0.
 *
 * Tham chieu: D:\bqp\KE_HOACH_DIEN_DOI_TUONG_VAO_PHU_LUC_CON_KHI_XUAT_THAM_DINH.md
 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ExcelJS = require('exceljs');

const staticDir = path.resolve(__dirname, '../../main/resources/static');
const htmlPath = path.join(staticDir, 'index.html');
const jsPath = path.join(staticDir, 'bqp_validation.js');
const normJsPath = path.join(staticDir, 'bqp_normalization.js');
const NEW_TEMPLATE_PATH = path.join(staticDir, 'PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx');

let passCount = 0;
let failCount = 0;

function pass(msg) { console.log('  [PASS] ' + msg); passCount++; }
function fail(msg) { console.error('  [FAIL] ' + msg); failCount++; }

function createSandbox(useInlineScript) {
    let capturedBlobBuffer = null;
    const sandbox = {
        console, Date, setTimeout, clearTimeout, ExcelJS, Buffer,
        Set, Map, Array, Object, String, Number, Math, Boolean, RegExp, JSON, Error, Promise,
        Blob: class { constructor(parts) { if (parts && parts[0]) capturedBlobBuffer = parts[0]; } },
        URL: { createObjectURL() { return 'blob://mock'; }, revokeObjectURL() {} },
        document: {
            addEventListener() {},
            getElementById(id) { return { id, value: '', textContent: '', style: {}, classList: { add() {}, remove() {}, toggle() {} } }; },
            querySelector() { return null; },
            querySelectorAll() { return []; },
            createElement() { return { click() {}, setAttribute() {}, style: {}, download: '', href: '' }; },
            body: { appendChild() {} }
        },
        window: null,
        notifySuccess: () => {}, notifyWarning: () => {},
        notifyError: (msg) => { console.log('[Toast Error] ' + msg); }, notifyInfo: () => {},
        BQPLoader: { show() {}, hide() {} },
        BqpStorageAdapter: { isSqlite: false },
        StorageManager: { getRecordsBySource() { return []; }, getUnits() { return []; } },
        UnitTreeManager: { getAllDescendantIds() { return []; } },
        RecordsUI: { getActiveFilters() { return {}; } },
        getCapturedBuffer: () => capturedBlobBuffer
    };
    sandbox.window = sandbox;
    const ctx = vm.createContext(sandbox);
    vm.runInContext(fs.readFileSync(normJsPath, 'utf8'), ctx);
    if (useInlineScript) {
        const html = fs.readFileSync(htmlPath, 'utf8');
        const lastScriptIdx = html.lastIndexOf('<script');
        const scriptOpenEnd = html.indexOf('>', lastScriptIdx) + 1;
        const scriptCloseIdx = html.indexOf('</script>', lastScriptIdx);
        vm.runInContext(html.substring(scriptOpenEnd, scriptCloseIdx), ctx);
    } else {
        vm.runInContext(fs.readFileSync(jsPath, 'utf8'), ctx);
    }
    return sandbox;
}

// Fixture 6 ho so theo ke hoach muc 6.1:
// rec_a1, rec_b1 -> I.1 (dat chuan -> II.1)
// rec_a2, rec_b2 -> I.2 (cap thua diff<-1000 -> III.2)
// rec_a3, rec_b3 -> I.3 (cap thieu diff>1000 -> IV.3)
function createSixRecordsFixture() {
    return [
        {
            id: 'rec_a1', hoTen: 'Nguyen Thi A1', ngaySinh: '10/1970',
            rowIndex: 12, unitId: 'u1', group: 'Don vi A', donVi: 'Don vi A',
            sheet: 'I.1', sheetSource: 'I.1',
            tongTienThucTe: 50000000, tongTienTinhLai: 50000000, diff: 0,
            hasErrors: false, comparisons: [], input: { hoTen: 'Nguyen Thi A1' }
        },
        {
            id: 'rec_b1', hoTen: 'Tran Van B1', ngaySinh: '5/1968',
            rowIndex: 13, unitId: 'u2', group: 'Don vi B', donVi: 'Don vi B',
            sheet: 'I.1', sheetSource: 'I.1',
            tongTienThucTe: 45000000, tongTienTinhLai: 45000000, diff: 0,
            hasErrors: false, comparisons: [], input: { hoTen: 'Tran Van B1' }
        },
        {
            id: 'rec_a2', hoTen: 'Le Thi A2', ngaySinh: '3/1972',
            rowIndex: 12, unitId: 'u1', group: 'Don vi A', donVi: 'Don vi A',
            sheet: 'I.2', sheetSource: 'I.2',
            tongTienThucTe: 30000000, tongTienTinhLai: 27000000, diff: -3000000,
            hasErrors: true, errorDetails: ['Cap thua'], comparisons: [], input: { hoTen: 'Le Thi A2' }
        },
        {
            id: 'rec_b2', hoTen: 'Pham Van B2', ngaySinh: '7/1965',
            rowIndex: 13, unitId: 'u2', group: 'Don vi B', donVi: 'Don vi B',
            sheet: 'I.2', sheetSource: 'I.2',
            tongTienThucTe: 25000000, tongTienTinhLai: 22000000, diff: -3000000,
            hasErrors: true, errorDetails: ['Cap thua'], comparisons: [], input: { hoTen: 'Pham Van B2' }
        },
        {
            id: 'rec_a3', hoTen: 'Hoang Thi A3', ngaySinh: '2/1975',
            rowIndex: 12, unitId: 'u1', group: 'Don vi A', donVi: 'Don vi A',
            sheet: 'I.3', sheetSource: 'I.3',
            tongTienThucTe: 20000000, tongTienTinhLai: 24000000, diff: 4000000,
            hasErrors: true, errorDetails: ['Cap thieu'], comparisons: [], input: { hoTen: 'Hoang Thi A3' }
        },
        {
            id: 'rec_b3', hoTen: 'Vu Van B3', ngaySinh: '9/1971',
            rowIndex: 13, unitId: 'u2', group: 'Don vi B', donVi: 'Don vi B',
            sheet: 'I.3', sheetSource: 'I.3',
            tongTienThucTe: 35000000, tongTienTinhLai: 40000000, diff: 5000000,
            hasErrors: true, errorDetails: ['Cap thieu'], comparisons: [], input: { hoTen: 'Vu Van B3' }
        }
    ];
}

// Helper lay worksheet khong phan biet dau tieng Viet
function getWs(wb, name) {
    if (!wb) return null;
    const ws = wb.getWorksheet(name);
    if (ws) return ws;
    const norm = s => s ? s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/\s+/g, ' ').trim() : '';
    const target = norm(name);
    return wb.worksheets.find(w => norm(w.name) === target) || null;
}

// Doc ten tu sheet - bo dong nhan/tieu de, chi lay dong co STT so
function readNamesFromSheet(ws, startRow) {
    startRow = startRow || 10;
    const names = [];
    for (let r = startRow; r <= ws.rowCount; r++) {
        const row = ws.getRow(r);
        const nameCell = row.getCell(2);
        let name = nameCell.value;
        if (name && typeof name === 'object' && name.richText) name = name.richText.map(t => t.text).join('');
        if (!name || typeof name !== 'string' || !name.trim()) continue;
        const sttVal = row.getCell(1).value;
        if (typeof sttVal === 'number' && Number.isFinite(sttVal)) {
            names.push(name.trim());
        }
    }
    return names;
}

async function runTestSuite() {
    console.log('================================================================');
    console.log('BAT DAU KIEM THU: DIEN DOI TUONG VAO PHU LUC CON KHI XUAT THAM DINH');
    console.log('================================================================\n');

    if (!fs.existsSync(NEW_TEMPLATE_PATH)) {
        console.error('Khong tim thay mau moi: ' + NEW_TEMPLATE_PATH);
        process.exit(2);
    }

    // TEST 1: Xac nhan mau moi dung 18 sheet va co dung hang header
    console.log('--- TEST 1: Kiem tra mau moi BO QUOC PHONG.xlsx co du 18 sheet ---');
    {
        const wb = new ExcelJS.Workbook();
        await wb.xlsx.readFile(NEW_TEMPLATE_PATH);
        const sheetNames = wb.worksheets.map(ws => ws.name);
        try {
            assert.strictEqual(sheetNames.length, 18, 'Can 18 sheet, hien co ' + sheetNames.length);
            // Kiem phu luc I.1 co dong 9 la so thu tu cot
            const wsI1 = wb.worksheets[1]; // index 1 = I.1
            const headerNumRow = wsI1.getRow(9);
            assert.strictEqual(headerNumRow.getCell(1).value, 1, 'Dong 9 cot 1 phai la so 1');
            // Kiem dong minh hoa: dong 12 la "Nguyen Van A"
            const sampleRow = wsI1.getRow(12);
            const sampleName = sampleRow.getCell(2).value;
            console.log('  I.1 dong 12 (minh hoa): ' + sampleName);
            pass('Mau moi du 18 sheet, I.1 co header so thu tu tai dong 9');
        } catch(e) {
            fail('Mau moi sai cau truc: ' + e.message);
        }
    }

    // TEST 2: Tai hien loi voi mau that - fixture 6 ho so, kiem I.x chua dong minh hoa hay ho so thuc
    // Day la test MONG DOI THAT BAI voi code hien tai!
    console.log('\n--- TEST 2: Tai hien loi - I.x chua dong minh hoa thay vi ho so thuc ---');
    {
        const sb = createSandbox(false);
        const records = createSixRecordsFixture();

        // Doc mau that
        const templateWb = new sb.ExcelJS.Workbook();
        const templateBuf = fs.readFileSync(NEW_TEMPLATE_PATH);
        await templateWb.xlsx.load(templateBuf);
        templateWb.__bqpReportTemplate = true;

        let exportErr = null;
        try {
            await sb.BQPValidation.exportValidatedWorkbook(
                templateWb, 'Test_Tai_Hien_Loi.xlsx',
                records, false, 'Tat_Ca'
            );
        } catch(e) {
            exportErr = e;
            console.log('  Loi xuat: ' + e.message);
        }

        const exportedBuf = sb.getCapturedBuffer();
        if (!exportedBuf) {
            fail('Khong xuat duoc buffer - loi nghiem trong truoc khi kiem phu luc');
            // Van ghi nhan de chay tiep cac test khac
        } else {
            const outWb = new ExcelJS.Workbook();
            await outWb.xlsx.load(exportedBuf);

            // Kiem I.1
            const wsI1 = getWs(outWb, 'Phụ lục I.1');
            if (!wsI1) {
                fail('Sheet Phu luc I.1 khong ton tai trong output');
            } else {
                const namesI1 = readNamesFromSheet(wsI1, 10);
                console.log('  I.1 nguoi hien co:', namesI1);
                const oldInI1 = namesI1.some(n => /nguyen van a/i.test(n) || /nguy.n v.n a/i.test(n));
                const hasA1 = namesI1.some(n => /A1/i.test(n) || /Nguyen Thi A1/i.test(n));
                const hasB1 = namesI1.some(n => /B1/i.test(n) || /Tran Van B1/i.test(n));
                if (oldInI1) fail('I.1 VAN con dong mau cu "Nguyen Van A" - LOI CHUA SUA (tai hien thanh cong!)');
                else if (!hasA1 || !hasB1) fail('I.1 THIEU nguoi: rec_a1=' + hasA1 + ', rec_b1=' + hasB1);
                else pass('I.1 chua dung rec_a1 va rec_b1, khong con dong mau cu');
            }

            // Kiem I.2
            const wsI2 = getWs(outWb, 'Phụ lục I.2');
            if (!wsI2) {
                fail('Sheet Phu luc I.2 khong ton tai');
            } else {
                const namesI2 = readNamesFromSheet(wsI2, 10);
                console.log('  I.2 nguoi hien co:', namesI2);
                const oldI2 = namesI2.some(n => /nguyen van a|tran thi b/i.test(n));
                const hasA2 = namesI2.some(n => /A2/i.test(n));
                const hasB2 = namesI2.some(n => /B2/i.test(n));
                if (oldI2) fail('I.2 VAN con dong mau cu');
                else if (!hasA2 || !hasB2) fail('I.2 THIEU nguoi: rec_a2=' + hasA2 + ', rec_b2=' + hasB2);
                else pass('I.2 chua dung rec_a2 va rec_b2');
            }

            // Kiem I.3
            const wsI3 = getWs(outWb, 'Phụ lục I.3');
            if (!wsI3) {
                fail('Sheet Phu luc I.3 khong ton tai');
            } else {
                const namesI3 = readNamesFromSheet(wsI3, 10);
                console.log('  I.3 nguoi hien co:', namesI3);
                const oldI3 = namesI3.some(n => /nguyen van a|tran thi b/i.test(n));
                const hasA3 = namesI3.some(n => /A3/i.test(n));
                const hasB3 = namesI3.some(n => /B3/i.test(n));
                if (oldI3) fail('I.3 VAN con dong mau cu');
                else if (!hasA3 || !hasB3) fail('I.3 THIEU nguoi: rec_a3=' + hasA3 + ', rec_b3=' + hasB3);
                else pass('I.3 chua dung rec_a3 va rec_b3');
            }
        }
    }

    // TEST 3: Phan loai - II.1/III.2/IV.3
    console.log('\n--- TEST 3: Phan loai dung vao II.x/III.x/IV.x ---');
    {
        const sb = createSandbox(false);
        const records = createSixRecordsFixture();

        const templateWb = new sb.ExcelJS.Workbook();
        const templateBuf = fs.readFileSync(NEW_TEMPLATE_PATH);
        await templateWb.xlsx.load(templateBuf);
        templateWb.__bqpReportTemplate = true;

        let exportOk = false;
        try {
            await sb.BQPValidation.exportValidatedWorkbook(templateWb, 'Test_Phan_Loai.xlsx', records, false, 'Tat_Ca');
            exportOk = true;
        } catch(e) {
            fail('Xuat that bai khi phan loai: ' + e.message);
        }

        if (exportOk) {
            const exportedBuf = sb.getCapturedBuffer();
            const outWb = new ExcelJS.Workbook();
            await outWb.xlsx.load(exportedBuf);

            // II.1: Dat chuan I.1
            const wsII1 = getWs(outWb, 'Phụ lục II.1');
            if (wsII1) {
                const names = readNamesFromSheet(wsII1, 10);
                console.log('  II.1 nguoi:', names);
                const ok = names.some(n => /A1/i.test(n)) && names.some(n => /B1/i.test(n)) && !names.some(n => /nguyen van a|tran thi b/i.test(n));
                if (ok) pass('II.1 (Dat chuan) chua dung rec_a1 va rec_b1');
                else fail('II.1 sai: ' + names);
            } else fail('Sheet Phu luc II.1 khong ton tai');

            // III.2: Cap thua I.2
            const wsIII2 = getWs(outWb, 'Phụ lục III.2');
            if (wsIII2) {
                const names = readNamesFromSheet(wsIII2, 10);
                console.log('  III.2 nguoi:', names);
                const ok = names.some(n => /A2/i.test(n)) && names.some(n => /B2/i.test(n));
                if (ok) pass('III.2 (Cap thua, Thoi viec) chua dung rec_a2 va rec_b2');
                else fail('III.2 sai: ' + names);
            } else fail('Sheet Phu luc III.2 khong ton tai');

            // IV.3: Cap thieu I.3
            const wsIV3 = getWs(outWb, 'Phụ lục IV.3');
            if (wsIV3) {
                const names = readNamesFromSheet(wsIV3, 10);
                console.log('  IV.3 nguoi:', names);
                const ok = names.some(n => /A3/i.test(n)) && names.some(n => /B3/i.test(n));
                if (ok) pass('IV.3 (Cap thieu, ND177) chua dung rec_a3 va rec_b3');
                else fail('IV.3 sai: ' + names);
            } else fail('Sheet Phu luc IV.3 khong ton tai');

            // II.2/III.1 phai rong (khong co dat chuan I.2 hoac cap thua I.1)
            const wsII2 = getWs(outWb, 'Phụ lục II.2');
            if (wsII2) {
                const names = readNamesFromSheet(wsII2, 10);
                if (names.length > 0) fail('II.2 phai rong, nhung co: ' + names);
                else pass('II.2 dung la rong');
            } else pass('II.2 khong ton tai (nhom rong, chap nhan duoc)');

            const wsIII1 = getWs(outWb, 'Phụ lục III.1');
            if (wsIII1) {
                const names = readNamesFromSheet(wsIII1, 10);
                if (names.length > 0) fail('III.1 phai rong, nhung co: ' + names);
                else pass('III.1 dung la rong');
            } else pass('III.1 khong ton tai (nhom rong, chap nhan duoc)');
        }
    }

    // TEST 4: Khong nhan ban khi xuat lai nhieu lan
    console.log('\n--- TEST 4: Khong nhan ban khi xuat lai nhieu lan ---');
    {
        const sb = createSandbox(false);
        const records = createSixRecordsFixture();

        // Phai doc mau 2 lan vi exportValidatedWorkbook co the bien doi baseWb
        const templateBuf = fs.readFileSync(NEW_TEMPLATE_PATH);

        const templateWb1 = new sb.ExcelJS.Workbook();
        await templateWb1.xlsx.load(templateBuf);
        templateWb1.__bqpReportTemplate = true;
        await sb.BQPValidation.exportValidatedWorkbook(templateWb1, 'Lap1.xlsx', records, false, 'Tat_Ca');
        const buf1 = sb.getCapturedBuffer();

        const templateWb2 = new sb.ExcelJS.Workbook();
        await templateWb2.xlsx.load(templateBuf);
        templateWb2.__bqpReportTemplate = true;
        await sb.BQPValidation.exportValidatedWorkbook(templateWb2, 'Lap2.xlsx', records, false, 'Tat_Ca');
        const buf2 = sb.getCapturedBuffer();

        if (buf1 && buf2) {
            const wb1 = new ExcelJS.Workbook(); const wb2 = new ExcelJS.Workbook();
            await wb1.xlsx.load(buf1); await wb2.xlsx.load(buf2);
            const ws1 = getWs(wb1, 'Phụ lục I.1');
            const ws2 = getWs(wb2, 'Phụ lục I.1');
            if (ws1 && ws2) {
                const n1 = readNamesFromSheet(ws1, 10);
                const n2 = readNamesFromSheet(ws2, 10);
                console.log('  Lan 1 I.1:', n1.length, 'nguoi | Lan 2 I.1:', n2.length, 'nguoi');
                if (n1.length === n2.length) pass('Xuat lai lan 2 khong nhan ban: ' + n1.length + ' nguoi moi lan');
                else fail('Nhan ban nguoi khi xuat lai: lan1=' + n1.length + ', lan2=' + n2.length);
            } else fail('Khong the doc sheet I.1');
        } else fail('Khong xuat duoc buffer cho mot trong hai lan');
    }

    // TEST 5: Script inline nhat quan
    console.log('\n--- TEST 5: Script inline index.html nhat quan voi bqp_validation.js ---');
    {
        const sbInline = createSandbox(true);
        const records = createSixRecordsFixture();

        const templateBuf = fs.readFileSync(NEW_TEMPLATE_PATH);
        const templateWb = new sbInline.ExcelJS.Workbook();
        await templateWb.xlsx.load(templateBuf);
        templateWb.__bqpReportTemplate = true;

        try {
            await sbInline.BQPValidation.exportValidatedWorkbook(templateWb, 'Test_Inline.xlsx', records, false, 'Tat_Ca');
            const exportedBuf = sbInline.getCapturedBuffer();
            assert(exportedBuf, 'Ban inline phai xuat thanh cong');
            const outWb = new ExcelJS.Workbook();
            await outWb.xlsx.load(exportedBuf);
            const wsI1 = getWs(outWb, 'Phụ lục I.1');
            if (wsI1) {
                const names = readNamesFromSheet(wsI1, 10);
                const hasNoOld = !names.some(n => /nguyen van a/i.test(n));
                const hasReal = names.some(n => /A1/i.test(n)) || names.some(n => /B1/i.test(n));
                if (hasNoOld && hasReal) pass('Script inline cho ket qua nhat quan: I.1 co nguoi thuc, khong con mau cu');
                else if (!hasNoOld) fail('Script inline van con mau cu trong I.1');
                else fail('Script inline khong dien duoc nguoi vao I.1: ' + names);
            } else fail('Script inline khong tao duoc sheet Phu luc I.1');
        } catch(e) {
            fail('Script inline that bai: ' + e.message);
        }
    }

    // Tong ket
    console.log('\n================================================================');
    if (failCount === 0) {
        console.log('TAT CA ' + passCount + ' KIEM THU DA VUOT QUA!');
    } else {
        console.log('Ket qua: ' + passCount + ' PASS, ' + failCount + ' FAIL');
        if (passCount === 1 && failCount >= 4) {
            console.log('>>> CAC FAIL LA MONG DOI: tac nhan tai hien loi xac nhan.');
            console.log('>>> Sua exporter theo ke hoach P1 de cac test nay PASS.');
        }
    }
    console.log('================================================================\n');

    if (failCount > 0) process.exit(1);
}

runTestSuite().catch(err => {
    console.error('KIEM THU LOI NGOAI Y MUON:', err.message);
    console.error(err.stack);
    process.exit(1);
});
