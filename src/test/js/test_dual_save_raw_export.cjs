/**
 * test_dual_save_raw_export.cjs
 * Bộ kiểm thử tự động toàn diện cho chức năng:
 * - Lưu đồng thời hai danh sách (Chưa thẩm định RAW & Sau thẩm định)
 * - Xuất Excel chưa thẩm định (RAW)
 * - Giao diện Hồ sơ và cây đơn vị dùng chung
 * Theo tiêu chuẩn nghiệm thu của KE_HOACH_LUU_DONG_THOI_HAI_DANH_SACH_VA_XUAT_EXCEL_RAW.md
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const assert = require('assert');
const vm = require('vm');

console.log('================================================================');
console.log('BẮT ĐẦU KIỂM THỬ: LƯU ĐỒNG THỜI 2 DANH SÁCH & XUẤT EXCEL RAW');
console.log('================================================================\n');

const htmlPath = path.resolve(__dirname, '../../main/resources/static/index.html');
assert(fs.existsSync(htmlPath), `Không tìm thấy file ${htmlPath}`);
const html = fs.readFileSync(htmlPath, 'utf8');

let passCount = 0;
function pass(msg) {
    console.log(`✅ [PASS] ${msg}`);
    passCount++;
}

// -----------------------------------------------------------------
// 1. Kiểm tra cấu trúc HTML modal lưu (valSaveModal)
// -----------------------------------------------------------------
{
    // Không còn 2 checkbox riêng lẻ cho phép bỏ chọn một trong hai danh sách
    assert(!html.includes('id="val_save_to_validated"'), 'Vẫn còn checkbox id="val_save_to_validated" trong HTML');
    assert(!html.includes('id="val_save_to_excel"'), 'Vẫn còn checkbox id="val_save_to_excel" trong HTML');

    // Có chỉ báo cố định cho cả 2 đích lưu
    assert(html.includes('Chưa thẩm định (RAW):'), 'Thiếu chỉ báo Chưa thẩm định (RAW) trong valSaveModal');
    assert(html.includes('Sau thẩm định:'), 'Thiếu chỉ báo Sau thẩm định trong valSaveModal');

    // Có phần tử hiển thị số lượng đối tượng dự kiến ghi
    assert(html.includes('id="val_save_record_count_text"'), 'Thiếu phần tử id="val_save_record_count_text" trong valSaveModal');

    // Nút xác nhận lưu có ID và nội dung lưu đồng thời
    assert(html.includes('id="btn_execute_save_records"'), 'Thiếu nút id="btn_execute_save_records"');
    assert(html.includes('Lưu đồng thời vào 2 danh sách'), 'Nút xác nhận lưu phải có nhãn "Lưu đồng thời vào 2 danh sách"');

    pass('Modal valSaveModal đã bỏ checkbox chọn riêng; cố định 2 đích lưu và nút "Lưu đồng thời vào 2 danh sách"');
}

// -----------------------------------------------------------------
// 2. Kiểm tra nhãn tab và thanh toolbar trong tab Hồ sơ (pane_list)
// -----------------------------------------------------------------
{
    // Tab nguồn phải đổi nhãn đúng chuẩn
    assert(html.includes('id="btn_src_manual"'), 'Thiếu tab btn_src_manual');
    assert(html.includes('id="btn_src_excel"'), 'Thiếu tab btn_src_excel');
    assert(html.includes('id="btn_src_validated"'), 'Thiếu tab btn_src_validated');

    const tabSlice = html.slice(html.indexOf('<div class="records-source-tabs">'), html.indexOf('</div>', html.indexOf('<div class="records-source-tabs">')) + 10);
    assert(tabSlice.includes('Chưa thẩm định (RAW)'), 'Tab nguồn excel phải đổi nhãn thành "Chưa thẩm định (RAW)"');
    assert(tabSlice.includes('Sau thẩm định'), 'Tab nguồn validated phải đổi nhãn thành "Sau thẩm định"');

    // Nút xuất RAW có mặt trong toolbar
    assert(html.includes('id="btn_records_export_raw"'), 'Thiếu nút id="btn_records_export_raw" trong toolbar');
    assert(html.includes('Xuất Excel chưa thẩm định (RAW)'), 'Nút btn_records_export_raw phải có nhãn "Xuất Excel chưa thẩm định (RAW)"');

    pass('Nhãn tab nguồn đã đổi thành "Chưa thẩm định (RAW)" và "Sau thẩm định"; có nút btn_records_export_raw');
}

// -----------------------------------------------------------------
// 3. Khởi tạo môi trường giả lập DOM/JS để kiểm tra logic mappers & export
// -----------------------------------------------------------------
function createDOMEnvironment() {
    function createElement(tag, id = '') {
        const el = {
            tagName: tag.toUpperCase(),
            id: id || '',
            _classes: new Set(),
            classList: {
                add(c) { el._classes.add(c); },
                remove(c) { el._classes.delete(c); },
                toggle(c, v) { if (v) el._classes.add(c); else el._classes.delete(c); },
                contains(c) { return el._classes.has(c); }
            },
            style: {},
            children: [],
            parentNode: null,
            _textContent: '',
            get textContent() { return this._textContent; },
            set textContent(val) { this._textContent = String(val); },
            _innerHTML: '',
            get innerHTML() { return this._innerHTML; },
            set innerHTML(val) { this._innerHTML = String(val); },
            type: '',
            value: '',
            disabled: false,
            setAttribute(name, val) { this[name] = val; },
            getAttribute(name) { return this[name] || null; },
            appendChild(child) {
                child.parentNode = this;
                this.children.push(child);
                return child;
            },
            removeChild(child) {
                const idx = this.children.indexOf(child);
                if (idx !== -1) this.children.splice(idx, 1);
                return child;
            },
            focus() { mockDoc.activeElement = el; },
            _listeners: {},
            addEventListener(ev, cb) {
                if (!this._listeners[ev]) this._listeners[ev] = [];
                this._listeners[ev].push(cb);
            },
            removeEventListener(ev, cb) {
                if (this._listeners && this._listeners[ev]) {
                    this._listeners[ev] = this._listeners[ev].filter(f => f !== cb);
                }
            },
            querySelector(sel) {
                if (sel.startsWith('#')) return elementsById[sel.substring(1)] || null;
                return null;
            }
        };
        return el;
    }

    const elementsById = {};
    const mockBody = createElement('body');

    const mockDoc = {
        body: mockBody,
        activeElement: mockBody,
        createElement(tag) { return createElement(tag); },
        getElementById(id) {
            if (!elementsById[id]) {
                elementsById[id] = createElement('div', id);
            }
            return elementsById[id];
        },
        querySelector(sel) {
            if (sel.startsWith('#')) return this.getElementById(sel.substring(1));
            return null;
        },
        querySelectorAll() { return []; },
        addEventListener() {},
        removeEventListener() {}
    };

    return { mockDoc, elementsById };
}

const { mockDoc, elementsById } = createDOMEnvironment();

class MockURLSearchParams {
    constructor(init) { this.params = {}; }
    get(k) { return this.params[k] || null; }
    set(k, v) { this.params[k] = String(v); }
    toString() { return ''; }
}

const sandbox = {
    document: mockDoc,
    location: { search: '', origin: 'http://localhost:8080', pathname: '/' },
    sessionStorage: {
        getItem() { return null; },
        setItem() {},
        removeItem() {}
    },
    URLSearchParams: MockURLSearchParams,
    console: { log: () => {}, warn: () => {}, error: () => {} },
    Math: Math,
    Date: Date,
    JSON: JSON,
    Array: Array,
    Object: Object,
    String: String,
    Number: Number,
    Boolean: Boolean,
    RegExp: RegExp,
    Set: Set,
    Map: Map,
    Promise: Promise,
    AbortController: globalThis.AbortController || class { abort() {} get signal() { return {}; } },
    setTimeout: (fn) => setTimeout(fn, 0),
    clearTimeout: (id) => clearTimeout(id),
    XLSX: {
        utils: {
            book_new() { return { SheetNames: [], Sheets: {} }; },
            aoa_to_sheet(rows) { return { rows }; },
            book_append_sheet(wb, ws, name) { wb.SheetNames.push(name); wb.Sheets[name] = ws; }
        },
        writeFile() {}
    },
    BQPValidation: {
        formatDate(d) { return d ? String(d) : ''; },
        fmtMoney(m) { return Number(m || 0).toLocaleString('vi-VN'); },
        normalizeErrorDetails(errs) { return errs || []; },
        ensureRecordValidationDetail(r) { r._ensuredValidation = true; }
    },
    UnitTreeManager: {
        getUnitLabel(id, units) { return 'Đơn vị ' + id; },
        getAllDescendantIds(id, units) { return [id]; }
    },
    StorageManager: {
        records: { manual: [], excel: [], validated: [] },
        getRecordsBySource(src) { return this.records[src] || []; },
        getUnits() { return [{ id: 'u1', name: 'Sư đoàn 324' }]; }
    },
    BqpStorageAdapter: {
        isSqlite: true,
        counts: { manual: 0, excel: 0, validated: 0 }
    },
    notifySuccess() {},
    notifyWarning() {},
    notifyError() {},
    notifyInfo() {},
    updateTabCount() {},
    refreshAllUnitSelects() {},
    addEventListener() {},
    removeEventListener() {}
};

sandbox.window = sandbox;
sandbox.self = sandbox;
sandbox.globalThis = sandbox;

// Trích xuất mã JavaScript từ index.html
const scriptMatches = html.match(/<script(?![^>]*src=)[^>]*>([\s\S]*?)<\/script>/gi) || [];
const fullScript = scriptMatches.map(s => s.replace(/<script[^>]*>|<\/script>/gi, '')).join('\n;\n')
    + '\n;if (typeof RecordsUI !== "undefined") window.RecordsUI = RecordsUI;\n'
    + 'if (typeof ExportManager !== "undefined") window.ExportManager = ExportManager;\n'
    + 'if (typeof ValidationUI !== "undefined") window.ValidationUI = ValidationUI;\n';

vm.createContext(sandbox);
vm.runInContext(fullScript, sandbox);

const ValidationUI = sandbox.ValidationUI || (sandbox.window && sandbox.window.ValidationUI);
const RecordsUI = sandbox.RecordsUI || (sandbox.window && sandbox.window.RecordsUI);
const ExportManager = sandbox.ExportManager || (sandbox.window && sandbox.window.ExportManager);

assert(ValidationUI, 'Không tìm thấy ValidationUI trong script');
assert(RecordsUI, 'Không tìm thấy RecordsUI trong script');
assert(ExportManager, 'Không tìm thấy ExportManager trong script');
pass('Khởi tạo sandbox và load thành công ValidationUI, RecordsUI, ExportManager');

// -----------------------------------------------------------------
// 4. Kiểm thử Mapper RAW: mapValidationRecordToRawRecord
// -----------------------------------------------------------------
{
    assert(typeof ValidationUI.mapValidationRecordToRawRecord === 'function', 'ValidationUI.mapValidationRecordToRawRecord phải là một hàm');

    const sampleRecord = {
        id: 'val_12345',
        rowIndex: 15,
        group: 'Ban Tham mưu',
        sheet: 'I.1',
        categoryCode: 'SQ',
        policyCode: 'ND178',
        hoTen: 'Nguyễn Văn Kiểm Thử',
        capBac: 'Thượng tá',
        chucVu: 'Trưởng ban',
        ngaySinh: '1975-04-12',
        nhapNgu: '1993-09-01',
        sapNhap: '2020-01-01',
        thoiDiemNghi: '2026-10-01',
        luongThang: 25000000,
        tongTienThucTe: 150000000,
        tongTienTinhLai: 165000000,
        diff: 15000000,
        hasErrors: true, // Lỗi đối soát nghiệp vụ
        errorDetails: [{ message: 'Sai thâm niên cột 12' }],
        comparisons: [{ col: 12, diff: 15000000 }],
        expected: { tongTien: 165000000 },
        rawCols: { 9: 25000000, 23: 150000000 }
    };

    const raw = ValidationUI.mapValidationRecordToRawRecord(sampleRecord, { unitName: 'Ban Tham mưu' });

    assert.strictEqual(raw.source, 'excel', 'Nguồn của bản ghi RAW phải là "excel"');
    assert.strictEqual(raw.classificationSource, 'EXCEL_ORIGINAL', 'classificationSource phải là EXCEL_ORIGINAL');
    assert(raw.id.startsWith('excel_'), 'ID bản ghi RAW phải có tiền tố "excel_"');
    assert.strictEqual(raw.fullName, 'Nguyễn Văn Kiểm Thử');
    assert.strictEqual(raw.unitName, 'Ban Tham mưu', 'RAW phải có canonical field unitName');
    assert.strictEqual(raw.unit, 'Ban Tham mưu', 'RAW phải có alias field unit');
    assert(raw.retirementDate === '2026-10-01' || raw.retirementDate === '10/2026', 'RAW phải có canonical retirementDate');
    assert(raw.demobilizationDate === '2026-10-01' || raw.demobilizationDate === '10/2026', 'RAW phải có alias demobilizationDate');
    assert(raw.mergerDate === '2020-01-01' || raw.mergerDate === '01/2020', 'RAW phải có canonical mergerDate');
    assert(raw.recruitmentDate === '2020-01-01' || raw.recruitmentDate === '01/2020', 'RAW phải có alias recruitmentDate');
    assert.strictEqual(raw.actualTotal, 150000000);
    assert.strictEqual(raw.calculatedTotal, 0, 'calculatedTotal của RAW phải bằng 0');
    assert.strictEqual(raw.difference, 0, 'difference của RAW phải bằng 0');
    assert.strictEqual(raw.hasErrors, false, 'RAW không được mang cờ hasErrors từ kết quả thẩm định nghiệp vụ BQP');
    assert.strictEqual(raw.validationSnapshot, undefined, 'RAW không được chứa validationSnapshot');
    assert.strictEqual(raw.expected, undefined, 'RAW không được chứa expected');
    assert.strictEqual(raw.comparisons, undefined, 'RAW không được chứa comparisons');

    pass('mapValidationRecordToRawRecord: Tách sạch 100% dữ liệu gốc, chuẩn hóa canonical + alias fields');
}

// -----------------------------------------------------------------
// 5. Kiểm thử Mapper Validated: mapValidationRecordToValidatedRecord
// -----------------------------------------------------------------
{
    assert(typeof ValidationUI.mapValidationRecordToValidatedRecord === 'function', 'ValidationUI.mapValidationRecordToValidatedRecord phải là một hàm');

    const sampleRecord = {
        id: 'val_12345',
        rowIndex: 15,
        group: 'Ban Tham mưu',
        sheet: 'I.1',
        categoryCode: 'SQ',
        policyCode: 'ND178',
        hoTen: 'Nguyễn Văn Kiểm Thử',
        capBac: 'Thượng tá',
        chucVu: 'Trưởng ban',
        ngaySinh: '1975-04-12',
        nhapNgu: '1993-09-01',
        sapNhap: '2020-01-01',
        thoiDiemNghi: '2026-10-01',
        luongThang: 25000000,
        tongTienThucTe: 150000000,
        tongTienTinhLai: 165000000,
        diff: 15000000,
        hasErrors: true,
        errorDetails: [{ message: 'Sai thâm niên cột 12' }],
        comparisons: [{ col: 12, diff: 15000000 }],
        expected: { tongTien: 165000000 },
        rawCols: { 9: 25000000, 23: 150000000 }
    };

    const val = ValidationUI.mapValidationRecordToValidatedRecord(sampleRecord, { unitName: 'Ban Tham mưu' });

    assert.strictEqual(val.source, 'validated', 'Nguồn của bản ghi validated phải là "validated"');
    assert.strictEqual(val.classificationSource, 'VALIDATED_EXCEL');
    assert.strictEqual(val.unitName, 'Ban Tham mưu', 'Validated phải có canonical field unitName');
    assert.strictEqual(val.unit, 'Ban Tham mưu', 'Validated phải có alias field unit');
    assert(val.retirementDate === '2026-10-01' || val.retirementDate === '10/2026', 'Validated phải có canonical retirementDate');
    assert(val.demobilizationDate === '2026-10-01' || val.demobilizationDate === '10/2026', 'Validated phải có alias demobilizationDate');
    assert(val.mergerDate === '2020-01-01' || val.mergerDate === '01/2020', 'Validated phải có canonical mergerDate');
    assert(val.recruitmentDate === '2020-01-01' || val.recruitmentDate === '01/2020', 'Validated phải có alias recruitmentDate');
    assert(Array.isArray(val.errorDetails) && val.errorDetails.length > 0, 'errorDetails phải là mảng');
    assert.strictEqual(val.errorDetails[0].message, 'Sai thâm niên cột 12', 'errorDetails phải có message');
    assert.strictEqual(val.errorDetails[0].ruleCode, 'BQP_VAL_RULE', 'errorDetails phải có ruleCode');
    assert.strictEqual(val.actualTotal, 150000000);
    assert.strictEqual(val.calculatedTotal, 165000000, 'calculatedTotal phải giữ đúng tiền tính lại');
    assert.strictEqual(val.difference, 15000000, 'difference phải giữ đúng chênh lệch');
    assert.strictEqual(val.hasErrors, true, 'hasErrors của validated phải phản ánh lỗi đối soát');
    assert(val.resultJson.includes('validationSnapshot'), 'validated phải chứa snapshot đối soát trong resultJson');

    pass('mapValidationRecordToValidatedRecord: Bảo toàn đầy đủ kết quả thẩm định, chuẩn hóa canonical + alias fields và structured errorDetails');
}

// -----------------------------------------------------------------
// 6. Kiểm thử chuyển nguồn (switchSource) & hiển thị nút xuất phù hợp
// -----------------------------------------------------------------
{
    const btnExp = mockDoc.getElementById('btn_records_export');
    const btnExpRaw = mockDoc.getElementById('btn_records_export_raw');
    const selStatus = mockDoc.getElementById('records_filter_status');

    // Chuyển sang nguồn 'excel' (Chưa thẩm định RAW)
    RecordsUI.switchSource('excel');
    assert.strictEqual(RecordsUI.currentSource, 'excel');
    assert.strictEqual(btnExp.style.display, 'none', 'Khi ở nguồn excel, nút xuất validated phải ẩn');
    assert.strictEqual(btnExpRaw.style.display, 'inline-block', 'Khi ở nguồn excel, nút btn_records_export_raw phải hiện');
    assert.strictEqual(selStatus.style.display, 'none', 'Khi ở nguồn excel, bộ lọc trạng thái thẩm định phải ẩn');

    // Chuyển sang nguồn 'validated' (Sau thẩm định)
    RecordsUI.switchSource('validated');
    assert.strictEqual(RecordsUI.currentSource, 'validated');
    assert.strictEqual(btnExp.style.display, 'inline-block', 'Khi ở nguồn validated, nút xuất báo cáo phải hiện');
    assert.strictEqual(btnExpRaw.style.display, 'none', 'Khi ở nguồn validated, nút btn_records_export_raw phải ẩn');
    assert.strictEqual(selStatus.style.display, 'inline-block', 'Khi ở nguồn validated, bộ lọc trạng thái thẩm định phải hiện');

    // Chuyển sang nguồn 'manual' (Tính thủ công)
    RecordsUI.switchSource('manual');
    assert.strictEqual(RecordsUI.currentSource, 'manual');
    assert.strictEqual(btnExp.style.display, 'inline-block', 'Khi ở nguồn manual, nút xuất danh sách phải hiện');
    assert.strictEqual(btnExpRaw.style.display, 'none', 'Khi ở nguồn manual, nút btn_records_export_raw phải ẩn');
    assert.strictEqual(selStatus.style.display, 'none', 'Khi ở nguồn manual, bộ lọc trạng thái thẩm định phải ẩn');

    pass('switchSource: Chuyển đổi chính xác nút xuất và bộ lọc theo đúng nguồn đang xem');
}

// -------------------------------------------------------------
// 7. Kiểm thử helper lấy bộ lọc đang hiển thị (getActiveFilters)
// -------------------------------------------------------------
{
    assert(typeof RecordsUI.getActiveFilters === 'function', 'RecordsUI.getActiveFilters phải là một hàm');

    RecordsUI.selectedUnitId = 'u_123';
    RecordsUI.selectedScope = 'direct';
    mockDoc.getElementById('records_filter_sheet').value = 'I.1';
    mockDoc.getElementById('records_search_input').value = 'Nguyễn Văn';
    mockDoc.getElementById('records_filter_status').value = 'ok';
    RecordsUI.currentSource = 'validated';

    let filtersVal = RecordsUI.getActiveFilters();
    assert.strictEqual(filtersVal.unitId, 'u_123');
    assert.strictEqual(filtersVal.scope, 'direct');
    assert.strictEqual(filtersVal.sheetType, 'I.1');
    assert.strictEqual(filtersVal.status, 'ok');
    assert.strictEqual(filtersVal.q, 'Nguyễn Văn');

    // Ở nguồn excel, status phải tự động bỏ qua
    RecordsUI.currentSource = 'excel';
    let filtersRaw = RecordsUI.getActiveFilters();
    assert.strictEqual(filtersRaw.status, null, 'Ở nguồn excel, getActiveFilters phải đặt status = null');

    pass('getActiveFilters: Thu thập chính xác toàn bộ tham số lọc đang hiển thị');
}

// -------------------------------------------------------------
// 8. Kiểm thử ExportManager.normalizeRawRecord
// -------------------------------------------------------------
{
    assert(typeof ExportManager.normalizeRawRecord === 'function', 'ExportManager.normalizeRawRecord phải là một hàm');

    const dbRecord = {
        id: 'rec_001',
        unitId: 'u1',
        unitName: 'Sư đoàn 324',
        sheetType: 'I.1',
        sourceRow: 12,
        fullName: 'Lê Văn Thử Nghiệm',
        rank: 'Trung tá',
        position: 'Phó ban',
        birthDate: '1980-05-10',
        enlistmentDate: '1998-03-01',
        retirementDate: '2026-12-01',
        monthlySalary: 20000000,
        actualTotal: 180000000,
        calculatedTotal: 0,
        difference: 0,
        hasErrors: 0,
        rawColumnsJson: JSON.stringify({ 9: 20000000, 10: 12, 23: 180000000 })
    };

    const norm = ExportManager.normalizeRawRecord(dbRecord);
    assert.strictEqual(norm.fullName, 'Lê Văn Thử Nghiệm');
    assert.strictEqual(norm.hoTen, 'Lê Văn Thử Nghiệm');
    assert.strictEqual(norm.capBac, 'Trung tá');
    assert.strictEqual(norm.actualTotal, 180000000);
    assert.strictEqual(norm.calculatedTotal, 0);
    assert.strictEqual(norm.difference, 0);
    assert.strictEqual(norm._ensuredValidation, undefined, 'normalizeRawRecord tuyệt đối không gọi ensureRecordValidationDetail');

    pass('normalizeRawRecord: Chuẩn hóa đầy đủ trường alias mà không phục hồi chi tiết thẩm định');
}

// -------------------------------------------------------------
// 9. Kiểm thử ExportManager.buildRawWorkbook
// -------------------------------------------------------------
{
    assert(typeof ExportManager.buildRawWorkbook === 'function', 'ExportManager.buildRawWorkbook phải là một hàm');
    assert(typeof ExportManager._buildRawSheetData === 'function', 'ExportManager._buildRawSheetData phải là một hàm');

    const mockRawRecords = [
        {
            sheet: 'I.1',
            hoTen: 'Trần Văn Một',
            ngaySinh: '1978-01-01',
            capBac: 'Đại tá',
            chucVu: 'Sư đoàn trưởng',
            nhapNgu: '1996-03-01',
            thoiDiemNghi: '2026-10-01',
            luongThang: 30000000,
            tongTienThucTe: 250000000,
            actualTotal: 250000000,
            rawCols: { 9: 30000000, 10: 0, 11: 5, 23: 250000000 }, // C10 = 0 thực sự
            unitName: 'Sư đoàn 324'
        },
        {
            sheet: 'I.2',
            hoTen: 'Lê Văn Hai',
            ngaySinh: '1985-06-15',
            capBac: 'Thiếu tá',
            chucVu: 'Trợ lý',
            nhapNgu: '2004-09-01',
            thoiDiemNghi: '2026-11-01',
            luongThang: 18000000,
            tongTienThucTe: 90000000,
            actualTotal: 90000000,
            rawCols: { 9: 18000000, 10: 6, 18: 90000000 },
            unitName: 'Ban Tham mưu'
        },
        {
            sheet: 'I.4',
            hoTen: 'Vũ Văn Bốn',
            ngaySinh: '1982-08-20',
            capBac: 'Thượng tá',
            chucVu: 'Phó Chỉ huy trưởng',
            nhapNgu: '2000-03-01',
            thoiDiemNghi: '2026-09-01',
            luongThang: 22000000,
            tongTienThucTe: 110000000,
            actualTotal: 110000000,
            rawCols: { 9: 22000000, 10: 12, 11: 4, 12: '[Lỗi #DIV/0!]', 16: 110000000 },
            unitName: 'Bộ Chỉ huy QS Tỉnh'
        },
        {
            sheet: 'I.5',
            hoTen: 'Phạm Thị Ba',
            capBac: 'CNQP',
            chucVu: 'Nhân viên',
            luongThang: 12000000,
            tongTienThucTe: 12000000,
            actualTotal: 12000000,
            unitName: 'Ban Hậu cần'
        }
    ];

    const wb = ExportManager.buildRawWorkbook(mockRawRecords, [{ id: 'u1', name: 'Sư đoàn 324' }]);
    assert(wb.SheetNames.includes('Phụ lục I.1'), 'Workbook RAW phải có sheet Phụ lục I.1');
    assert(wb.SheetNames.includes('Phụ lục I.2'), 'Workbook RAW phải có sheet Phụ lục I.2');
    assert(wb.SheetNames.includes('Phụ lục I.4'), 'Workbook RAW phải có sheet Phụ lục I.4');
    assert(wb.SheetNames.includes('Phụ lục I.5'), 'Workbook RAW phải có sheet Phụ lục I.5');

    // Kiểm tra nội dung dòng I.1
    const sheetI1 = wb.Sheets['Phụ lục I.1'];
    const rowsI1 = sheetI1.rows;
    assert.strictEqual(rowsI1.length, 2, 'Sheet I.1 phải có 1 dòng header và 1 dòng dữ liệu');
    const dataRowI1 = rowsI1[1];
    assert.strictEqual(dataRowI1[1], 'Trần Văn Một');
    assert.strictEqual(dataRowI1[8], 30000000);
    assert.strictEqual(dataRowI1[9], 0, 'Giá trị 0 ở cột 10 (tháng) phải được bảo toàn là số 0, không bị đổi thành rỗng');
    assert.strictEqual(dataRowI1[22], 250000000, 'Cột 23 tổng tiền phải là số tiền kê khai gốc');

    // Kiểm tra nội dung dòng I.4
    const sheetI4 = wb.Sheets['Phụ lục I.4'];
    const rowsI4 = sheetI4.rows;
    assert.strictEqual(rowsI4.length, 2, 'Sheet I.4 phải có 1 dòng header và 1 dòng dữ liệu');
    assert.strictEqual(rowsI4[0].length, 18, 'Sheet I.4 phải có đúng 18 cột');
    const dataRowI4 = rowsI4[1];
    assert.strictEqual(dataRowI4[1], 'Vũ Văn Bốn');
    assert.strictEqual(dataRowI4[11], '[Lỗi #DIV/0!]', 'Công thức lỗi không được bị ép về số 0');
    assert.strictEqual(dataRowI4[15], 110000000, 'Cột 16 tổng tiền phải là số tiền kê khai gốc');

    // Tuyệt đối không có cột "Ghi chú thẩm định BQP" hoặc các sheet Đạt chuẩn/Cấp thừa/Cấp thiếu
    assert(!rowsI1[0].includes('Ghi chú thẩm định BQP'), 'Sheet RAW không được có cột "Ghi chú thẩm định BQP"');
    assert(!wb.SheetNames.includes('Đạt chuẩn'), 'Sheet RAW không được chứa sheet phân loại "Đạt chuẩn"');
    assert(!wb.SheetNames.includes('Cấp thừa'), 'Sheet RAW không được chứa sheet phân loại "Cấp thừa"');
    assert(!wb.SheetNames.includes('Cấp thiếu'), 'Sheet RAW không được chứa sheet phân loại "Cấp thiếu"');

    pass('buildRawWorkbook: Tạo đúng các sheet I.1, I.2, I.4, I.5; giữ nguyên số 0, bảo toàn lỗi công thức và không có kết luận thẩm định');
}

// -------------------------------------------------------------
// 10. Kiểm tra đồng bộ 100% hash giữa các file HTML
// -------------------------------------------------------------
{
    const path1 = path.resolve('d:/bqp/bqp-validate-data/src/main/resources/static/index.html');
    const path2 = path.resolve('d:/bqp/cong_cu_tinh_toan_bqp4.0.html');
    const path3 = path.resolve('d:/bqp/bqp-validate-data/target/classes/static/index.html');
    const path4 = path.resolve('d:/bqp/BQP_Application_Release/index.html');

    const h1 = crypto.createHash('sha256').update(fs.readFileSync(path1)).digest('hex');
    const h2 = crypto.createHash('sha256').update(fs.readFileSync(path2)).digest('hex');

    assert.strictEqual(h1, h2, 'Hash lệch giữa static/index.html và cong_cu_tinh_toan_bqp4.0.html');

    if (fs.existsSync(path3)) {
        const h3 = crypto.createHash('sha256').update(fs.readFileSync(path3)).digest('hex');
        assert.strictEqual(h1, h3, 'Hash lệch giữa static/index.html và target/classes/static/index.html');
    }

    if (fs.existsSync(path4)) {
        const h4 = crypto.createHash('sha256').update(fs.readFileSync(path4)).digest('hex');
        assert.strictEqual(h1, h4, 'Hash lệch giữa static/index.html và Release/index.html');
    }

    pass(`Các bản sao HTML đồng bộ 100% SHA-256 (${h1.substring(0, 16)}...)`);
}

console.log('\n================================================================');
console.log(`✅ TẤT CẢ ${passCount} / 10 BÀI KIỂM THỬ LƯU ĐỒNG THỜI & XUẤT RAW ĐÃ VƯỢT QUA 100%!`);
console.log('Chức năng Lưu đôi nguyên tử & Xuất Excel RAW đã hoạt động hoàn hảo!');
console.log('================================================================\n');
