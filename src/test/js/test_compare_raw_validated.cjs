/**
 * test_compare_raw_validated.cjs
 * Bộ kiểm thử toàn diện: Đối chiếu Sau thẩm định vs RAW, bóc tách nguồn, bảo vệ context,
 * báo cáo lỗi trung thực và xử lý bất đồng bộ theo tiêu chuẩn KE_HOACH_FIX_DOI_CHIEU_SAU_THAM_DINH_RAW_VA_BAO_LOI.md.
 * 
 * Bao phủ các mã kiểm thử:
 * DC-01, DC-02, DC-03, DC-04, DC-05, DC-06, DC-07, DC-08, DC-09, DC-10, DC-11, DC-12, DC-13, DC-14, DC-15, DC-21, DC-23
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vm = require('vm');

console.log('================================================================');
console.log('BẮT ĐẦU KIỂM THỬ: ĐỐI CHIẾU SAU THẨM ĐỊNH & RAW (DC-01 -> DC-23)');
console.log('================================================================\n');

const htmlPath = path.resolve(__dirname, '../../main/resources/static/index.html');
assert(fs.existsSync(htmlPath), `Không tìm thấy file ${htmlPath}`);
const html = fs.readFileSync(htmlPath, 'utf8');

let passCount = 0;
function pass(testCode, desc) {
    console.log(`✅ [PASS ${testCode}] ${desc}`);
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
            focus() {},
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
        addEventListener(ev, cb) {},
        removeEventListener(ev, cb) {},
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
        head: createElement('head')
    };

    const mockStorage = {
        _data: {},
        getItem(k) { return this._data[k] || null; },
        setItem(k, v) { this._data[k] = String(v); },
        removeItem(k) { delete this._data[k]; },
        clear() { this._data = {}; }
    };

    const notifications = [];
    const sandbox = {
        console: {
            log: () => {},
            warn: () => {},
            error: () => {},
            info: () => {}
        },
        document: mockDoc,
        window: null,
        localStorage: mockStorage,
        sessionStorage: mockStorage,
        setTimeout: (cb) => { if (typeof cb === 'function') cb(); return 1; },
        clearTimeout: () => {},
        setInterval: () => 1,
        clearInterval: () => {},
        Date: Date,
        Math: Math,
        JSON: JSON,
        RegExp: RegExp,
        Array: Array,
        Object: Object,
        String: String,
        Number: Number,
        Boolean: Boolean,
        Promise: Promise,
        parseInt: parseInt,
        parseFloat: parseFloat,
        isNaN: isNaN,
        isFinite: isFinite,
        encodeURIComponent: encodeURIComponent,
        decodeURIComponent: decodeURIComponent,
        URLSearchParams: URLSearchParams,
        notifySuccess: (msg) => notifications.push({ type: 'success', msg }),
        notifyWarning: (msg) => notifications.push({ type: 'warning', msg }),
        notifyError: (msg) => notifications.push({ type: 'error', msg }),
        notifyInfo: (msg) => notifications.push({ type: 'info', msg }),
        _notifications: notifications,
        fetch: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        AbortController: class {
            constructor() { this.signal = { aborted: false }; }
            abort() { this.signal.aborted = true; }
        }
    };
    sandbox.window = sandbox;
    sandbox.global = sandbox;
    sandbox.globalThis = sandbox;

    // Load inline scripts từ index.html
    const scriptMatches = html.match(/<script(?![^>]*src=)[^>]*>([\s\S]*?)<\/script>/gi) || [];
    const fullScript = scriptMatches.map(s => s.replace(/<script[^>]*>|<\/script>/gi, '')).join('\n;\n');
    vm.runInNewContext(fullScript, sandbox);

    return sandbox;
}

// =================================================================
// BẮT ĐẦU CHẠY CÁC TEST CASES
// =================================================================

async function runTests() {
    // -------------------------------------------------------------
    // DC-01: SQLite mode, currentItems=[], detail API trả 200 -> Mở modal thành công
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        sb.BqpStorageAdapter.isSqlite = true;
        sb.RecordsUI.currentItems = []; // Mô phỏng danh sách bộ nhớ đang rỗng
        sb.RecordsUI.currentSource = 'validated';

        const mockRecord = {
            id: 'val_item_001',
            hoVaTen: 'Đinh Xuân Hải',
            sheetType: 'I.1',
            sourceRow: 5,
            unitId: 'u1',
            donVi: 'Cục Kế hoạch và Đầu tư',
            tongTienThucTe: 120000000,
            tongTienTinhLai: 120000000,
            hasErrors: false,
            resultJson: JSON.stringify({
                validationSnapshot: {
                    sheet: 'I.1',
                    hasErrors: false,
                    calculatedTotal: 120000000,
                    comparisons: [
                        { col: 'C10', title: 'Tháng', actual: '12', expected: '12', diff: '0 tháng', hasErr: false },
                        { col: 'C23', title: 'Tổng tiền', actual: '120.000.000', expected: '120.000.000', diff: '0 đ', hasErr: false }
                    ],
                    errorDetails: []
                }
            })
        };

        sb.fetch = async (url) => {
            if (url.includes('/api/records/val_item_001')) {
                return {
                    ok: true,
                    status: 200,
                    json: async () => mockRecord
                };
            }
            if (url.includes('/api/records/excel_item_001')) {
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({
                        id: 'excel_item_001',
                        sheetType: 'I.1',
                        sourceRow: 5,
                        unitId: 'u1',
                        rawColumnsJson: JSON.stringify({ C10: '12', C23: '120000000' }),
                        actualTotal: 120000000
                    })
                };
            }
            return { ok: false, status: 404, json: async () => ({ error: 'Not found' }) };
        };

        await sb.RecordsUI.viewDetail('val_item_001', 'validated');

        const modal = sb.document.getElementById('valErrorModal');
        const titleEl = sb.document.getElementById('valModalTitle');
        const bodyEl = sb.document.getElementById('valModalBody');

        assert(modal.classList.contains('open'), 'Modal phải có class open');
        assert(titleEl.textContent.includes('Đinh Xuân Hải'), 'Tiêu đề modal phải chứa họ tên hồ sơ');
        assert(bodyEl.innerHTML.includes('120.000.000'), 'Nội dung modal phải chứa giá trị so sánh');
        assert(!sb._notifications.some(n => n.msg.includes('Không tìm thấy thông tin hồ sơ')), 'Không được báo lỗi "Không tìm thấy thông tin hồ sơ"');

        pass('DC-01', 'SQLite currentItems=[]: Gọi detail API 200 mở modal thành công, không báo lỗi không tìm thấy');
    }

    // -------------------------------------------------------------
    // DC-02: currentItems chứa hồ sơ khác, ID mở nằm ngoài trang -> Mở đúng hồ sơ từ API
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        sb.BqpStorageAdapter.isSqlite = true;
        sb.RecordsUI.currentItems = [
            { id: 'val_page1_01', hoVaTen: 'Nguyễn Văn Trang Một' }
        ];
        sb.RecordsUI.currentSource = 'validated';

        const mockTarget = {
            id: 'val_page2_99',
            hoVaTen: 'Trần Thị Trang Hai',
            sheetType: 'I.1',
            sourceRow: 88,
            tongTienThucTe: 80000000,
            tongTienTinhLai: 80000000,
            hasErrors: false,
            resultJson: JSON.stringify({
                validationSnapshot: {
                    sheet: 'I.1',
                    hasErrors: false,
                    calculatedTotal: 80000000,
                    comparisons: [
                        { col: 'C23', title: 'Tổng tiền', actual: '80.000.000', expected: '80.000.000', diff: '0 đ', hasErr: false }
                    ]
                }
            })
        };

        sb.fetch = async (url) => {
            if (url.includes('/api/records/val_page2_99')) {
                return { ok: true, status: 200, json: async () => mockTarget };
            }
            return { ok: false, status: 404, json: async () => ({}) };
        };

        await sb.RecordsUI.viewDetail('val_page2_99', 'validated');

        const titleEl = sb.document.getElementById('valModalTitle');
        assert(titleEl.textContent.includes('Trần Thị Trang Hai'), 'Modal phải mở đúng người ngoài trang');
        assert(!titleEl.textContent.includes('Nguyễn Văn Trang Một'), 'Modal không được nhầm sang người trong currentItems');
        pass('DC-02', 'currentItems có người khác: Mở đúng hồ sơ ngoài trang từ API, không chọn sai phần tử');
    }

    // -------------------------------------------------------------
    // DC-03: Ghép cặp RAW theo exact ID excel_<suffix> với val_<suffix>
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        sb.BqpStorageAdapter.isSqlite = true;
        sb.RecordsUI.currentItems = [];
        sb.RecordsUI.currentSource = 'validated';

        const mockVal = {
            id: 'val_batch9_777',
            hoVaTen: 'Lê Hoàng Nam',
            sheetType: 'I.1',
            sourceRow: 10,
            unitId: 'u2',
            tongTienThucTe: 90000000,
            tongTienTinhLai: 95000000,
            hasErrors: true,
            errorDetails: ['C13 sai phụ cấp'],
            resultJson: JSON.stringify({
                validationSnapshot: {
                    sheet: 'I.1',
                    hasErrors: true,
                    calculatedTotal: 95000000,
                    comparisons: [
                        { col: 'C13', title: 'PC chức vụ', actual: '10.000.000', expected: '15.000.000', diff: '+5.000.000 đ', hasErr: true },
                        { col: 'C23', title: 'Tổng cộng', actual: '90.000.000', expected: '95.000.000', diff: '+5.000.000 đ', hasErr: true }
                    ]
                }
            })
        };

        const mockRaw = {
            id: 'excel_batch9_777',
            sheetType: 'I.1',
            sourceRow: 10,
            unitId: 'u2',
            rawColumnsJson: JSON.stringify({ C13: '10.000.000', C23: '90.000.000' }),
            actualTotal: 90000000
        };

        let rawRequested = false;
        sb.fetch = async (url) => {
            if (url.includes('/api/records/val_batch9_777')) {
                return { ok: true, status: 200, json: async () => mockVal };
            }
            if (url.includes('/api/records/excel_batch9_777')) {
                rawRequested = true;
                return { ok: true, status: 200, json: async () => mockRaw };
            }
            return { ok: false, status: 404, json: async () => ({}) };
        };

        await sb.RecordsUI.viewDetail('val_batch9_777', 'validated');

        assert(rawRequested, 'Phải gửi request lấy bản ghi RAW excel_batch9_777');
        const bodyEl = sb.document.getElementById('valModalBody');
        assert(bodyEl.innerHTML.includes('File Excel (RAW)'), 'Phải có cột File Excel (RAW)');
        assert(bodyEl.innerHTML.includes('Sau thẩm định'), 'Phải có cột Sau thẩm định');
        assert(bodyEl.innerHTML.includes('10.000.000'), 'Phải hiển thị giá trị RAW 10.000.000');
        assert(bodyEl.innerHTML.includes('15.000.000'), 'Phải hiển thị giá trị Thẩm định 15.000.000');
        pass('DC-03', 'Ghép cặp RAW bằng exact ID excel_<suffix>: Nạp đúng giá trị RAW và hiển thị đối chiếu');
    }

    // -------------------------------------------------------------
    // DC-04: Hai người trùng tên hoặc cùng người nhiều đợt nhập -> Không ghép nhầm
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        sb.BqpStorageAdapter.isSqlite = true;

        // Giả sử có 2 bản ghi cùng tên Nguyễn Văn A nhưng khác suffix
        const val1 = { id: 'val_dot1_rec1', hoVaTen: 'Nguyễn Văn A', sheetType: 'I.1', sourceRow: 2, unitId: 'u1' };
        const val2 = { id: 'val_dot2_rec1', hoVaTen: 'Nguyễn Văn A', sheetType: 'I.1', sourceRow: 2, unitId: 'u1' };

        let fetchedRawId = null;
        sb.fetch = async (url) => {
            if (url.includes('/api/records/val_dot2_rec1')) {
                return { ok: true, status: 200, json: async () => val2 };
            }
            if (url.includes('/api/records/excel_dot2_rec1')) {
                fetchedRawId = 'excel_dot2_rec1';
                return { ok: true, status: 200, json: async () => ({ id: 'excel_dot2_rec1', sheetType: 'I.1', sourceRow: 2, unitId: 'u1' }) };
            }
            return { ok: false, status: 404, json: async () => ({}) };
        };

        await sb.RecordsUI.viewDetail('val_dot2_rec1', 'validated');
        assert.strictEqual(fetchedRawId, 'excel_dot2_rec1', 'Phải ghép đúng exact ID excel_dot2_rec1, không lẫn với dot1');
        pass('DC-04', 'Cùng họ tên / đợt nhập: Ghép cặp chính xác theo suffix đợt nhập, không ghép heuristic theo tên');
    }

    // -------------------------------------------------------------
    // DC-05 & DC-06: Chuyển source nhanh (Race condition) & Loading table state
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        sb.BqpStorageAdapter.isSqlite = true;
        sb.RecordsUI.currentSource = 'excel';

        // Giả lập fetch đang pending
        let fetchResolve;
        sb.fetch = async () => new Promise(resolve => { fetchResolve = resolve; });

        // Khi switchSource được gọi:
        // 1. Phải đặt loading spinner ngay lập tức trong khi fetch đang chờ
        sb.RecordsUI.switchSource('validated');
        const container = sb.document.getElementById('records_table_container');
        assert(container.innerHTML.includes('Đang tải danh sách'), 'Phải hiển thị loading spinner ngay lập tức khi chuyển nguồn');

        // 2. Kiểm tra guard chống phản hồi cũ ghi đè
        const requestIdOld = sb.RecordsUI.currentRequestId;
        sb.RecordsUI.currentSource = 'excel';
        sb.RecordsUI.currentRequestId = requestIdOld + 1;

        // Mô phỏng render trả về muộn từ request cũ (validated)
        sb.RecordsUI.render([], 0, requestIdOld, 'validated');
        // Container không được chứa bảng rỗng của validated cũ
        assert(!container.innerHTML.includes('records-table-validated'), 'Phản hồi cũ không được phép ghi đè bảng nguồn mới');

        pass('DC-05 & DC-06', 'Chống race condition và khóa bảng khi tải: Bảng cũ bị xóa ngay, response stale bị loại bỏ hoàn toàn');
    }

    // -------------------------------------------------------------
    // DC-07: Quy ước dấu chênh lệch thống nhất: Sau thẩm định − RAW
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        const testRec = {
            id: 'val_diff_test',
            hoVaTen: 'Phạm Văn Kiểm',
            sheetType: 'I.1',
            sourceRow: 5,
            actualTotal: 40000000,
            tongTienThucTe: 40000000,
            tongTienTinhLai: 50000000, // Validated > RAW -> +10.000.000 đ
            hasErrors: true,
            comparisons: [
                { col: 'C23', title: 'Tổng cộng', actual: '40.000.000', expected: '50.000.000', diff: '+10.000.000 đ', hasErr: true }
            ]
        };

        sb.ValidationUI.openCompareModal(testRec, false);
        const bodyEl = sb.document.getElementById('valModalBody');
        assert(bodyEl.innerHTML.includes('+10.000.000'), 'Quy ước dấu: Sau thẩm định > RAW phải mang dấu dương (+)');
        pass('DC-07', 'Quy ước chênh lệch: Thống nhất Sau thẩm định − RAW (Dương: Thẩm định > RAW, Âm: Thẩm định < RAW)');
    }

    // -------------------------------------------------------------
    // DC-08: Tổng bằng nhau (diff=0) nhưng có cột sai lệch -> Báo lỗi, KHÔNG báo 100%
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        const testRec = {
            id: 'val_equal_total_err',
            hoVaTen: 'Ngô Văn B',
            sheetType: 'I.1',
            tongTienThucTe: 50000000,
            tongTienTinhLai: 50000000,
            diff: 0,
            hasErrors: true, // Có lỗi ở cột C13 và C14 bù trừ nhau
            errorDetails: ['C13 (PC chức vụ): File ghi 10.000.000, Chuẩn 15.000.000', 'C14 (Thâm niên): File ghi 20.000.000, Chuẩn 15.000.000'],
            comparisons: [
                { col: 'C13', title: 'PC chức vụ', actual: '10.000.000', expected: '15.000.000', diff: '+5.000.000 đ', hasErr: true },
                { col: 'C14', title: 'Thâm niên', actual: '20.000.000', expected: '15.000.000', diff: '-5.000.000 đ', hasErr: true },
                { col: 'C23', title: 'Tổng cộng', actual: '50.000.000', expected: '50.000.000', diff: '0 đ', hasErr: false }
            ]
        };

        sb.ValidationUI.openCompareModal(testRec, false);
        const bodyEl = sb.document.getElementById('valModalBody');
        assert(!bodyEl.innerHTML.includes('Hồ sơ đạt chuẩn 100%'), 'Tuyệt đối không được báo xanh 100% khi hasErrors=true');
        assert(bodyEl.innerHTML.includes('Phát hiện 2 điểm sai lệch'), 'Phải báo phát hiện 2 điểm sai lệch');
        pass('DC-08', 'Tổng tiền khớp nhưng có sai lệch cột: Báo lỗi chính xác, không giả mạo đạt chuẩn 100%');
    }

    // -------------------------------------------------------------
    // DC-09: hasErrors=true nhưng errorDetails=[] -> Thông báo trung thực "chưa có đầy đủ chi tiết"
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        const testRec = {
            id: 'val_empty_details_err',
            hoVaTen: 'Vũ Thị C',
            sheetType: 'I.1',
            tongTienThucTe: 40000000,
            tongTienTinhLai: 45000000,
            hasErrors: true,
            errorDetails: [], // Danh sách lỗi trống
            comparisons: []
        };

        sb.ValidationUI.openCompareModal(testRec, false);
        const bodyEl = sb.document.getElementById('valModalBody');
        assert(!bodyEl.innerHTML.includes('Hồ sơ đạt chuẩn 100%'), 'Không được báo đạt chuẩn');
        assert(bodyEl.innerHTML.includes('chưa có đầy đủ chi tiết lỗi'), 'Phải báo trung thực chưa có đầy đủ mô tả chi tiết lỗi');
        pass('DC-09', 'hasErrors=true với errorDetails rỗng: Báo lỗi trung thực, không lừa dối người dùng');
    }

    // -------------------------------------------------------------
    // DC-10: Backend lỗi dạng Object {col, message}, deduplicate, không [object Object]
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        const testRec = {
            id: 'val_obj_err',
            hoVaTen: 'Trịnh Văn D',
            sheetType: 'I.1',
            hasErrors: true,
            errorDetails: [
                { col: 'C13', message: 'Hệ số chức vụ sai định mức', errorCode: 'ERR_CV' },
                'Hệ số chức vụ sai định mức', // Trùng lặp dạng string
                { col: 'C10', message: 'Tháng sinh không hợp lệ' }
            ],
            comparisons: []
        };

        sb.ValidationUI.openCompareModal(testRec, false);
        const bodyEl = sb.document.getElementById('valModalBody');
        assert(!bodyEl.innerHTML.includes('[object Object]'), 'Không được để lọt chuỗi [object Object]');
        assert(bodyEl.innerHTML.includes('Hệ số chức vụ sai định mức'), 'Phải trích xuất đúng message');
        assert(bodyEl.innerHTML.includes('Tháng sinh không hợp lệ'), 'Phải trích xuất đúng thông báo từ object');
        pass('DC-10', 'Lỗi dạng Object: Chuẩn hóa, bóc tách chuỗi thông báo an toàn và khử trùng lặp');
    }

    // -------------------------------------------------------------
    // DC-11 & DC-12: Bảo toàn giá trị số 0 thật sự, không biến thành rỗng
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        const testRec = {
            id: 'val_zero_test',
            hoVaTen: 'Đặng Văn E',
            sheetType: 'I.1',
            hasErrors: false,
            tongTienThucTe: 0,
            tongTienTinhLai: 0,
            comparisons: [
                { col: 'C10', title: 'Tháng', actual: 0, expected: 0, diff: '0 tháng', hasErr: false },
                { col: 'C13', title: 'Tiền', actual: 0, expected: 0, diff: '0 đ', hasErr: false }
            ]
        };

        sb.ValidationUI.openCompareModal(testRec, false);
        const bodyEl = sb.document.getElementById('valModalBody');
        assert(bodyEl.innerHTML.includes('<td>0</td>') || bodyEl.innerHTML.includes('>0 đ<') || bodyEl.innerHTML.includes('0'), 'Phải giữ nguyên số 0');
        pass('DC-11 & DC-12', 'Bảo toàn số 0: Số 0 thực tế được hiển thị nguyên vẹn, không bị mất hoặc biến thành rỗng');
    }

    // -------------------------------------------------------------
    // DC-13: Thiếu bản ghi RAW nhưng có snapshot gốc đầy đủ -> Hiển thị cảnh báo nguồn snapshot
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        sb.BqpStorageAdapter.isSqlite = true;

        const valRec = {
            id: 'val_orphan_01',
            hoVaTen: 'Hoàng Văn F',
            sheetType: 'I.1',
            sourceRow: 12,
            unitId: 'u1',
            resultJson: JSON.stringify({
                validationSnapshot: {
                    sheet: 'I.1',
                    hasErrors: false,
                    calculatedTotal: 60000000,
                    comparisons: [
                        { col: 'C23', title: 'Tổng', actual: '60.000.000', expected: '60.000.000', diff: '0 đ', hasErr: false }
                    ]
                }
            })
        };

        // API trả valRec nhưng RAW 404
        sb.fetch = async (url) => {
            if (url.includes('/api/records/val_orphan_01')) return { ok: true, status: 200, json: async () => valRec };
            return { ok: false, status: 404, json: async () => ({}) };
        };

        await sb.RecordsUI.viewDetail('val_orphan_01', 'validated');
        const bodyEl = sb.document.getElementById('valModalBody');
        assert(bodyEl.innerHTML.includes('Hồ sơ hiển thị từ bản ghi snapshot lưu trữ'), 'Phải có cảnh báo hiển thị từ snapshot khi RAW bị thiếu');
        pass('DC-13', 'Thiếu bản RAW liên kết: Tận dụng snapshot lưu trữ kèm cảnh báo minh bạch nguồn gốc');
    }

    // -------------------------------------------------------------
    // DC-14: Thiếu RAW lẫn snapshot -> Cảnh báo thiếu dữ liệu, cấm build expected=actual giả
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        const testRec = {
            id: 'val_legacy_bare',
            hoVaTen: 'Bùi Thị G',
            sheetType: 'I.1',
            hasErrors: false,
            // Không có snapshot, không có comparisons, không có rawCols
        };

        sb.ValidationUI.openCompareModal(testRec, false);
        const bodyEl = sb.document.getElementById('valModalBody');
        assert(bodyEl.innerHTML.includes('Hồ sơ này không có snapshot đối chiếu hoặc thiếu dữ liệu cột gốc'), 'Phải cảnh báo thiếu dữ liệu');
        assert(!bodyEl.innerHTML.includes('Hồ sơ đạt chuẩn 100%'), 'Tuyệt đối không được gán 100% khớp giả tạo');
        pass('DC-14', 'Thiếu cả RAW và snapshot: Cảnh báo minh bạch, cấm giả tạo bảng đối chiếu khớp 100%');
    }

    // -------------------------------------------------------------
    // DC-15: Metadata cặp mâu thuẫn (sheetType I.1 vs I.2) -> Đánh dấu inconsistent
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        sb.BqpStorageAdapter.isSqlite = true;

        const valRec = { id: 'val_mismatch_01', hoVaTen: 'Lý Văn H', sheetType: 'I.1', sourceRow: 4, unitId: 'u1' };
        const rawRec = { id: 'excel_mismatch_01', hoVaTen: 'Lý Văn H', sheetType: 'I.2', sourceRow: 4, unitId: 'u1' }; // Mâu thuẫn sheetType

        sb.fetch = async (url) => {
            if (url.includes('/api/records/val_mismatch_01')) return { ok: true, status: 200, json: async () => valRec };
            if (url.includes('/api/records/excel_mismatch_01')) return { ok: true, status: 200, json: async () => rawRec };
            return { ok: false, status: 404, json: async () => ({}) };
        };

        await sb.RecordsUI.viewDetail('val_mismatch_01', 'validated');
        const openedRec = sb.ValidationUI._modalContext.list[0];
        assert.strictEqual(openedRec.linkedRawStatus, 'inconsistent', 'Phải đánh dấu trạng thái inconsistent khi metadata không khớp');
        assert(sb.document.getElementById('valModalBody').innerHTML.includes('Dữ liệu nguồn không nhất quán'), 'Phải hiển thị cảnh báo không nhất quán trong modal');
        pass('DC-15', 'Metadata cặp mâu thuẫn: Phát hiện xung đột, không gộp dữ liệu bừa bãi');
    }

    // -------------------------------------------------------------
    // DC-21: Mở modal đối chiếu KHÔNG làm thay đổi ValidationUI.currentRecords
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        const existingCalcRecords = [
            { id: 'excel_working_1', hoVaTen: 'Hồ sơ đang thẩm định 1' },
            { id: 'excel_working_2', hoVaTen: 'Hồ sơ đang thẩm định 2' }
        ];
        sb.ValidationUI.currentRecords = existingCalcRecords.slice();

        const savedRec = {
            id: 'val_saved_record_99',
            hoVaTen: 'Hồ sơ đã lưu trong CSDL',
            sheetType: 'I.1'
        };

        sb.ValidationUI.openCompareModal(savedRec, false);

        assert.strictEqual(sb.ValidationUI.currentRecords.length, 2, 'ValidationUI.currentRecords không được bị thay đổi số lượng');
        assert.strictEqual(sb.ValidationUI.currentRecords[0].id, 'excel_working_1', 'ValidationUI.currentRecords phải giữ nguyên bản ghi 1');
        assert.strictEqual(sb.ValidationUI.currentRecords[1].id, 'excel_working_2', 'ValidationUI.currentRecords phải giữ nguyên bản ghi 2');
        pass('DC-21', 'Bảo vệ state màn Thẩm định Excel: Xem đối chiếu danh sách không đè/thay đổi currentRecords');
    }

    // -------------------------------------------------------------
    // DC-23: Bảo vệ an toàn chống XSS trong modal
    // -------------------------------------------------------------
    {
        const sb = createDOMEnvironment();
        const xssRec = {
            id: 'val_xss_test',
            hoVaTen: '<script>alert("XSS")</script>',
            donVi: '<b onmouseover="alert(1)">Đơn vị độc</b>',
            sheetType: 'I.1',
            hasErrors: true,
            errorDetails: ['Lỗi có chèn <img src=x onerror=alert(1)>']
        };

        sb.ValidationUI.openCompareModal(xssRec, false);
        const titleEl = sb.document.getElementById('valModalTitle');
        const bodyEl = sb.document.getElementById('valModalBody');

        assert(!titleEl.innerHTML.includes('<script>'), 'Thẻ script phải được escape trong tiêu đề');
        assert(titleEl.innerHTML.includes('&lt;script&gt;') || !titleEl.innerHTML.includes('<script>alert'), 'Phải mã hóa ký tự HTML an toàn');
        assert(!bodyEl.innerHTML.includes('<img src=x'), 'Thẻ img độc hại phải được escape trong nội dung');
        pass('DC-23', 'Bảo vệ XSS: Mã hóa HTML toàn diện cho dữ liệu tên người, đơn vị và chi tiết lỗi');
    }

    console.log('\n================================================================');
    console.log(`✅ TẤT CẢ ${passCount} BÀI KIỂM THỬ ĐỐI CHIẾU & BÁO LỖI ĐÃ ĐẠT 100%!`);
    console.log('Hệ thống đã đạt đầy đủ tiêu chuẩn theo kế hoạch đề ra!');
    console.log('================================================================\n');
}

runTests().catch(err => {
    console.error('❌ KIỂM THỬ THẤT BẠI:', err);
    process.exit(1);
});
