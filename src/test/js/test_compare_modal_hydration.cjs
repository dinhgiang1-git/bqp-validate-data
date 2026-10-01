/**
 * test_compare_modal_hydration.cjs
 * Kiểm thử tính năng hydrate dữ liệu và hiển thị modal Đối chiếu trong Danh sách đối tượng / Danh sách sau thẩm định.
 * Theo tiêu chuẩn nghiệm thu của KE_HOACH_FIX_DOI_CHIEU_DANH_SACH_KHONG_HIEN_THI_DU_LIEU.md
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const assert = require('assert');
const vm = require('vm');

console.log('================================================================');
console.log('BẮT ĐẦU KIỂM THỬ: HYDRATE DỮ LIỆU ĐỐI CHIẾU TRONG DANH SÁCH');
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
// Khởi tạo môi trường giả lập DOM/JS
// -----------------------------------------------------------------
function createDOMEnvironment() {
    const elementsById = {};

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
            set innerHTML(val) {
                this._innerHTML = String(val);
                this._textContent = this._innerHTML.replace(/<[^>]*>/g, '');
            },
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
            }
        };
        return el;
    }

    const mockDoc = {
        activeElement: null,
        body: createElement('body'),
        createElement: createElement,
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

    // Pre-create modal elements used by openCompareModal
    mockDoc.getElementById('valErrorModal');
    mockDoc.getElementById('valModalBody');
    mockDoc.getElementById('valModalTitle');
    mockDoc.getElementById('valModalNav');

    return { mockDoc, elementsById };
}

const { mockDoc, elementsById } = createDOMEnvironment();

class MockURLSearchParams {
    constructor(init) { this.params = {}; }
    get(k) { return this.params[k] || null; }
    set(k, v) { this.params[k] = String(v); }
    toString() { return ''; }
}

const mockApiDb = {};

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
    fetch: async (url) => {
        const u = String(url);
        if (u.startsWith('/api/records/')) {
            const id = u.replace('/api/records/', '').split('?')[0];
            if (mockApiDb[id]) {
                return {
                    ok: true,
                    json: async () => mockApiDb[id]
                };
            }
            return { ok: false, status: 404 };
        }
        if (u.startsWith('/api/records')) {
            return {
                ok: true,
                json: async () => ({
                    items: Object.values(mockApiDb),
                    totalElements: Object.values(mockApiDb).length,
                    totalPages: 1,
                    page: 0
                })
            };
        }
        return { ok: false, status: 404 };
    },
    XLSX: {
        utils: {
            book_new() { return { SheetNames: [], Sheets: {} }; },
            aoa_to_sheet(rows) { return { rows }; },
            book_append_sheet(wb, ws, name) { wb.SheetNames.push(name); wb.Sheets[name] = ws; }
        },
        writeFile() {}
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
    + 'if (typeof ValidationUI !== "undefined") window.ValidationUI = ValidationUI;\n'
    + 'if (typeof mapStoredRecordFromApi !== "undefined") window.mapStoredRecordFromApi = mapStoredRecordFromApi;\n';

vm.createContext(sandbox);
vm.runInContext(fullScript, sandbox);

const RecordsUI = sandbox.RecordsUI || (sandbox.window && sandbox.window.RecordsUI);
const ValidationUI = sandbox.ValidationUI || (sandbox.window && sandbox.window.ValidationUI);
const BQPValidation = sandbox.BQPValidation || (sandbox.window && sandbox.window.BQPValidation);
const mapStoredRecordFromApi = sandbox.mapStoredRecordFromApi || (sandbox.window && sandbox.window.mapStoredRecordFromApi);

assert(RecordsUI, 'Không tìm thấy RecordsUI trong sandbox');
assert(ValidationUI, 'Không tìm thấy ValidationUI trong sandbox');
assert(BQPValidation, 'Không tìm thấy BQPValidation trong sandbox');

pass('Khởi tạo sandbox và load thành công các module UI');

async function runHydrationTests() {
    // -------------------------------------------------------------
    // CA 1: Bản ghi SQLite có snapshot đối chiếu (như Đinh Xuân Hải trong ảnh)
    // -------------------------------------------------------------
    const sampleSnapshotRecordDto = {
        id: 'val_dinh_xuan_hai',
        unitId: 'u1',
        unitName: 'Bộ Tư lệnh Thủ đô Hà Nội',
        sheetType: 'I.1',
        sourceRow: 14,
        fullName: 'Đinh Xuân Hải',
        rank: 'Thượng tá',
        position: 'Trưởng ban',
        birthDate: '1975-04-12',
        enlistmentDate: '1993-09-01',
        mergerDate: '2020-01-01',
        retirementDate: '2026-10-01',
        monthlySalary: 25000000,
        actualTotal: 2638523160,
        calculatedTotal: 2569088340,
        difference: -69434820,
        hasErrors: 1,
        rawColumnsJson: JSON.stringify({
            9: 25000000,
            12: 33,
            19: 277739280,
            23: 2638523160
        }),
        inputJson: JSON.stringify({
            hoTen: 'Đinh Xuân Hải',
            capBac: 'Thượng tá',
            chucVu: 'Trưởng ban',
            ngaySinh: '1975-04-12',
            nhapNgu: '1993-09-01',
            sapNhap: '2020-01-01',
            thoiDiemNghi: '2026-10-01',
            luongThang: 25000000
        }),
        resultJson: JSON.stringify({
            tongTien: 2569088340,
            validationSnapshot: {
                hasErrors: true,
                errorDetails: ['Cột 12: Sai thâm niên', 'Cột 19: Chênh lệch trợ cấp một lần'],
                comparisons: [
                    { col: 'Cột 12', title: 'Thâm niên', actual: '33', expected: '28.5', diff: '+4.5', hasErr: true, formula: 'Quy định BQP' },
                    { col: 'Cột 19', title: 'Trợ cấp một lần', actual: '277.739.280', expected: '208.304.460', diff: '+69.434.820', hasErr: true, formula: 'Khoản 2 Điều 7' },
                    { col: 'Cột 23', title: 'Tổng kinh phí', actual: '2.638.523.160', expected: '2.569.088.340', diff: '+69.434.820', hasErr: true, formula: 'Tổng các cột' }
                ],
                expected: { cot12: 28.5, cot19: 208304460, cot23: 2569088340, tongTien: 2569088340 },
                calculatedTotal: 2569088340,
                actualTotal: 2638523160,
                diff: -69434820
            }
        })
    };

    mockApiDb['val_dinh_xuan_hai'] = sampleSnapshotRecordDto;

    // Giả lập danh sách RecordsUI đang có đối tượng này trong currentItems
    RecordsUI.currentSource = 'validated';
    if (typeof mapStoredRecordFromApi === 'function') {
        RecordsUI.currentItems = [mapStoredRecordFromApi(sampleSnapshotRecordDto, 'validated')];
    } else {
        // Mô phỏng mapping cũ chưa hydrate
        RecordsUI.currentItems = [{
            id: sampleSnapshotRecordDto.id,
            fullName: sampleSnapshotRecordDto.fullName,
            rank: sampleSnapshotRecordDto.rank,
            rawColumns: JSON.parse(sampleSnapshotRecordDto.rawColumnsJson),
            result: JSON.parse(sampleSnapshotRecordDto.resultJson)
        }];
    }

    // Bấm nút Đối chiếu: RecordsUI.viewDetail
    await RecordsUI.viewDetail('val_dinh_xuan_hai');

    const modalBody = elementsById['valModalBody'];
    assert(modalBody, 'valModalBody không tồn tại');
    const htmlContent = modalBody.innerHTML;

    // Kiểm tra dòng trong Excel không được là 'Dòng -'
    assert(htmlContent.includes('Dòng 14'), 'Phải hiển thị "Dòng 14", không được hiển thị "Dòng -"');
    assert(!htmlContent.includes('Dòng -'), 'Vẫn còn hiển thị "Dòng -" do rowIndex bị thiếu');

    // Kiểm tra các dòng trong bảng đối chiếu comparisons
    assert(htmlContent.includes('Cột 12'), 'Bảng đối chiếu phải có Cột 12');
    assert(htmlContent.includes('Trợ cấp một lần'), 'Bảng đối chiếu phải có Trợ cấp một lần');
    assert(htmlContent.includes('2.638.523.160'), 'Phải có số tiền thực tế Cột 23');
    assert(htmlContent.includes('2.569.088.340'), 'Phải có số tiền thẩm định tính lại');

    // Không được báo thiếu ngày giả
    assert(!htmlContent.includes('Thiếu hoặc lỗi các trường bắt buộc [Ngày sinh'), 'Không được báo thiếu Ngày sinh giả');
    assert(!htmlContent.includes('Hồ sơ chưa đủ điều kiện tiên quyết'), 'Không được báo INCOMPLETE_INPUT giả khi snapshot đã có');

    pass('CA 1: Phục hồi 100% dữ liệu đối chiếu từ snapshot SQLite (Đinh Xuân Hải)');

    // -------------------------------------------------------------
    // CA 2: Bản ghi hồ sơ cũ chưa có snapshot nhưng có rawColumnsJson & Ngày sinh dạng chuỗi ISO
    // -------------------------------------------------------------
    const legacyRecordDto = {
        id: 'val_legacy_001',
        unitId: 'u1',
        unitName: 'Sư đoàn 324',
        sheetType: 'I.1',
        sourceRow: 25,
        fullName: 'Trần Văn Cũ',
        rank: 'Thượng tá',
        position: 'Phó Trung đoàn trưởng',
        birthDate: '1976-02-15',
        enlistmentDate: '1994-03-01',
        mergerDate: '2020-01-01',
        retirementDate: '2026-08-01',
        monthlySalary: 20000000,
        actualTotal: 150000000,
        calculatedTotal: 150000000,
        difference: 0,
        hasErrors: 0,
        rawColumnsJson: JSON.stringify({
            9: 20000000,
            10: 0,
            23: 150000000
        }),
        inputJson: JSON.stringify({
            hoTen: 'Trần Văn Cũ',
            capBac: 'Thượng tá',
            chucVu: 'Phó Trung đoàn trưởng',
            ngaySinh: '1976-02-15',
            nhapNgu: '1994-03-01',
            thoiDiemNghi: '2026-08-01',
            luongThang: 20000000
        }),
        resultJson: JSON.stringify({
            tongTien: 150000000
            // Không có validationSnapshot
        })
    };

    mockApiDb['val_legacy_001'] = legacyRecordDto;
    RecordsUI.currentItems = [legacyRecordDto];

    await RecordsUI.viewDetail('val_legacy_001');
    const legacyHtml = elementsById['valModalBody'].innerHTML;

    assert(legacyHtml.includes('Trần Văn Cũ'), 'Phải nạp hồ sơ Trần Văn Cũ');
    assert(legacyHtml.includes('Dòng 25'), 'Phải hiển thị Dòng 25');
    assert(!legacyHtml.includes('Hồ sơ chưa đủ điều kiện tiên quyết'), 'Không được báo lỗi prerequisite khi đủ ngày tháng chuỗi');

    pass('CA 2: Tái tạo đối chiếu cho hồ sơ cũ không có snapshot từ rawColumnsJson');

    // -------------------------------------------------------------
    // CA 3: Hồ sơ hoàn toàn không thể phục hồi (không snapshot, không rawCols)
    // -------------------------------------------------------------
    const emptyRecordDto = {
        id: 'val_corrupted_999',
        unitId: 'u1',
        sheetType: 'I.1',
        sourceRow: 99,
        fullName: 'Lê Văn Lỗi Dữ Liệu',
        actualTotal: 100000000,
        calculatedTotal: 100000000,
        difference: 0,
        hasErrors: 0,
        rawColumnsJson: '{}',
        inputJson: '{}',
        resultJson: '{}'
    };

    mockApiDb['val_corrupted_999'] = emptyRecordDto;
    RecordsUI.currentItems = [emptyRecordDto];

    await RecordsUI.viewDetail('val_corrupted_999');
    const emptyHtml = elementsById['valModalBody'].innerHTML;

    // Không được để bảng rỗng im lặng hoặc báo "Hồ sơ đạt chuẩn 100%" sai sự thật
    assert(emptyHtml.includes('Không có dữ liệu đối chiếu chi tiết cho hồ sơ này'), 'Phải hiển thị thông báo trạng thái rõ ràng thay vì bảng rỗng');
    assert(!emptyHtml.includes('Hồ sơ đạt chuẩn 100%, khớp hoàn toàn'), 'Tuyệt đối không báo "Hồ sơ đạt chuẩn 100%" khi không có dòng đối chiếu nào');

    pass('CA 3: Hiển thị thông báo trạng thái trung thực khi không thể phục hồi chi tiết');

    // -------------------------------------------------------------
    // CA 4: Bảo toàn giá trị số 0
    // -------------------------------------------------------------
    const zeroRecordDto = {
        id: 'val_zero_test',
        sheetType: 'I.1',
        sourceRow: 5,
        fullName: 'Nguyễn Văn Số Không',
        rank: 'Thiếu tá',
        actualTotal: 0,
        calculatedTotal: 0,
        difference: 0,
        monthlySalary: 0,
        rawColumnsJson: JSON.stringify({ 9: 0, 10: 0, 23: 0 }),
        resultJson: JSON.stringify({
            validationSnapshot: {
                calculatedTotal: 0,
                actualTotal: 0,
                diff: 0,
                comparisons: [{ col: 'Cột 23', title: 'Tổng', actual: '0', expected: '0', diff: '0', hasErr: false }]
            }
        })
    };

    mockApiDb['val_zero_test'] = zeroRecordDto;
    RecordsUI.currentItems = [zeroRecordDto];

    await RecordsUI.viewDetail('val_zero_test');
    const zeroHtml = elementsById['valModalBody'].innerHTML;
    assert(zeroHtml.includes('0 đ'), 'Phải hiển thị 0 đ');
    assert(!zeroHtml.includes('Dòng -'), 'Dòng phải là Dòng 5');

    pass('CA 4: Bảo toàn chính xác các trường số có giá trị bằng 0');

    console.log('\n================================================================');
    console.log(`✅ TẤT CẢ ${passCount} / ${passCount} BÀI KIỂM THỬ HYDRATION & MODAL ĐỐI CHIẾU ĐÃ ĐẠT 100%!`);
    console.log('================================================================\n');
}

runHydrationTests().catch(err => {
    console.error('❌ KIỂM THỬ THẤT BẠI:', err);
    process.exit(1);
});
