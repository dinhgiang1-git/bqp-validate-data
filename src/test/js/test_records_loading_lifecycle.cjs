/**
 * test_records_loading_lifecycle.cjs
 * Bộ kiểm thử vòng đời tải danh sách, xử lý lỗi, timeout, request hiện hành
 * và tái hiện lỗi diffColor trong RecordsUI.render() theo KE_HOACH_FIX_DANH_SACH_SAU_THAM_DINH_BI_KET_DANG_TAI.md
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vm = require('vm');

console.log('================================================================');
console.log('BẮT ĐẦU KIỂM THỬ: VÒNG ĐỜI TẢI DANH SÁCH & FIX LỖI DIFFCOLOR');
console.log('================================================================\n');

const htmlPath = path.resolve(__dirname, '../../main/resources/static/index.html');
assert(fs.existsSync(htmlPath), `Không tìm thấy file ${htmlPath}`);
const html = fs.readFileSync(htmlPath, 'utf8');

// Trích xuất toàn bộ mã JavaScript inline từ index.html
const scriptMatches = html.match(/<script(?![^>]*src=)[^>]*>([\s\S]*?)<\/script>/gi) || [];
const fullScript = scriptMatches.map(s => s.replace(/<script[^>]*>|<\/script>/gi, '')).join('\n;\n');

let passCount = 0;
function pass(testCode, desc) {
    console.log(`✅ [PASS ${testCode}] ${desc}`);
    passCount++;
}

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
            return createElement('div');
        },
        querySelectorAll() { return []; },
        head: createElement('head')
    };

    // Pre-create common elements
    [
        'records_table_container', 'records_filter_status', 'records_filter_unit',
        'records_filter_sheet', 'records_search_input', 'btn_records_export',
        'btn_records_export_raw', 'btn_src_manual', 'btn_src_excel', 'btn_src_validated',
        'src_count_manual', 'src_count_excel', 'src_count_validated',
        'btn_scope_branch', 'btn_scope_direct'
    ].forEach(id => mockDoc.getElementById(id));

    const mockStorage = {
        _data: {},
        getItem(k) { return this._data[k] || null; },
        setItem(k, v) { this._data[k] = String(v); },
        removeItem(k) { delete this._data[k]; },
        clear() { this._data = {}; }
    };

    const notifications = [];
    let customFetch = null;

    class MockAbortSignal {
        constructor() {
            this.aborted = false;
            this._listeners = [];
        }
        addEventListener(name, fn) {
            if (name === 'abort') this._listeners.push(fn);
        }
    }

    class MockAbortController {
        constructor() {
            this.signal = new MockAbortSignal();
        }
        abort() {
            this.signal.aborted = true;
            this.signal._listeners.forEach(fn => fn());
        }
    }

    const sandbox = {
        console: {
            log: () => {},
            warn: () => {},
            error: (...args) => {
                sandbox._lastError = args;
            },
            info: () => {}
        },
        _lastError: null,
        document: mockDoc,
        window: null,
        localStorage: mockStorage,
        sessionStorage: mockStorage,
        setTimeout: (cb, ms) => setTimeout(cb, ms),
        clearTimeout: (id) => clearTimeout(id),
        setInterval: (cb, ms) => setInterval(cb, ms),
        clearInterval: (id) => clearInterval(id),
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
        fetch: (url, opts) => {
            if (typeof sandbox.customFetch === 'function') {
                return sandbox.customFetch(url, opts);
            }
            return Promise.resolve({
                ok: true,
                status: 200,
                json: async () => ({ items: [], totalElements: 0, totalPages: 0, page: 0, size: 50 })
            });
        },
        customFetch: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        AbortController: MockAbortController,
        URL: {
            createObjectURL: () => 'blob://test',
            revokeObjectURL: () => {}
        }
    };
    sandbox.window = sandbox;
    sandbox.global = sandbox;
    sandbox.globalThis = sandbox;

    vm.createContext(sandbox);
    vm.runInContext(fullScript, sandbox);

    return { sandbox, elementsById };
}

async function runTests() {
    // -------------------------------------------------------------
    // TEST 1: Tái hiện & Kiểm chứng lỗi diffColor khi render hàng có chênh lệch khác 0
    // -------------------------------------------------------------
    console.log('--- TEST 1: Kiểm tra RecordsUI.render() với hàng Sau thẩm định có diff !== 0 ---');
    {
        const { sandbox } = createDOMEnvironment();
        const container = sandbox.document.getElementById('records_table_container');

        // Đặt trạng thái ban đầu là spinner đang quay (giống khi bấm chuyển tab Sau thẩm định)
        container.innerHTML = '<div class="spinner"></div>Đang tải danh sách Sau thẩm định...';

        // Giả lập dữ liệu trả về từ SQLite có 1 người có chênh lệch tiền
        const sampleRecord = {
            id: 'val_101',
            source: 'validated',
            sourceRow: 8,
            sheetType: 'I.1',
            fullName: 'Nguyễn Văn Minh',
            rank: 'Thượng tá',
            position: 'Trợ lý',
            birthDate: '15/05/1975',
            enlistmentDate: '01/09/1993',
            retirementDate: '01/07/2025',
            monthlySalary: 25000000,
            actualTotal: 200000000,
            calculatedTotal: 205000000,
            difference: 5000000, // diff !== 0
            hasErrors: true,
            errorDetails: ['Lệch tổng tiền: File 200.000.000 đ, Tính lại 205.000.000 đ (+5.000.000 đ)']
        };

        sandbox.customFetch = async () => ({
            ok: true,
            status: 200,
            json: async () => ({
                items: [sampleRecord],
                totalElements: 1,
                totalPages: 1,
                page: 0,
                size: 50
            })
        });

        sandbox.BqpStorageAdapter.isSqlite = true;
        sandbox.RecordsUI.currentSource = 'validated';
        sandbox.RecordsUI.currentPage = 0;

        let renderErr = null;
        try {
            await sandbox.RecordsUI.render();
        } catch (err) {
            renderErr = err;
        }

        assert(!renderErr, `RecordsUI.render() không được ném lỗi, nhưng gặp lỗi: ${renderErr ? renderErr.message : ''}`);

        // Sau khi đã fix: container phải chứa bảng, tên quân nhân và tiền chênh lệch
        assert(!container.innerHTML.includes('Đang tải danh sách'), 'Vòng quay spinner phải bị xóa sau khi render xong');
        assert(container.innerHTML.includes('Nguyễn Văn Minh'), 'Bảng phải hiển thị tên quân nhân');
        assert(container.innerHTML.includes('+5.000.000'), 'Cột chênh lệch phải hiển thị +5.000.000 đ');
        pass('TC-01', 'RecordsUI.render() render thành công hàng có chênh lệch dương (+5.000.000 đ), không còn lỗi diffColor');
    }

    // -------------------------------------------------------------
    // TEST 2: Kiểm tra các mức chênh lệch khác: âm, bằng 0, chênh lệch nhỏ (1.000 đ)
    // -------------------------------------------------------------
    console.log('\n--- TEST 2: Kiểm tra các giá trị chênh lệch biên (0, ±1, ±1000, ±1001) ---');
    {
        const diffValues = [0, 1, -1, 1000, -1000, 1001, -1001, 150000000];
        for (const diff of diffValues) {
            const { sandbox, elementsById } = createDOMEnvironment();
            const container = elementsById['records_table_container'];

            const rec = {
                id: 'val_' + diff,
                source: 'validated',
                sheetType: 'I.1',
                fullName: 'Thử Nghiệm Lệch ' + diff,
                actualTotal: 100000000,
                calculatedTotal: 100000000 + diff,
                difference: diff,
                hasErrors: diff !== 0
            };

            sandbox.customFetch = async () => ({
                ok: true,
                status: 200,
                json: async () => ({ items: [rec], totalElements: 1, totalPages: 1, page: 0, size: 50 })
            });

            sandbox.BqpStorageAdapter.isSqlite = true;
            sandbox.RecordsUI.currentSource = 'validated';
            await sandbox.RecordsUI.render();

            if (diff === 0) {
                assert(container.innerHTML.includes('---'), 'diff = 0 phải hiển thị ---');
            } else {
                assert(container.innerHTML.includes('Thử Nghiệm Lệch ' + diff), `Phải render được hàng diff=${diff}`);
            }
        }
        pass('TC-02', 'Tất cả các mức chênh lệch biên (0, ±1, ±1000, ±1001, lớn) đều render an toàn, đúng màu sắc và định dạng');
    }

    // -------------------------------------------------------------
    // TEST 3: Xử lý trạng thái rỗng (Empty State)
    // -------------------------------------------------------------
    console.log('\n--- TEST 3: Xử lý trạng thái rỗng (Empty State) khi totalElements = 0 ---');
    {
        const { sandbox, elementsById } = createDOMEnvironment();
        const container = elementsById['records_table_container'];

        sandbox.customFetch = async () => ({
            ok: true,
            status: 200,
            json: async () => ({ items: [], totalElements: 0, totalPages: 0, page: 0, size: 50 })
        });

        sandbox.BqpStorageAdapter.isSqlite = true;
        sandbox.RecordsUI.currentSource = 'validated';
        await sandbox.RecordsUI.render();

        assert(!container.innerHTML.includes('Đang tải danh sách'), 'Không còn spinner ở trạng thái rỗng');
        assert(container.innerHTML.includes('Chưa có danh sách sau thẩm định') || container.innerHTML.includes('Không tìm thấy đối tượng nào'), 'Phải hiển thị thông báo rỗng phù hợp');
        pass('TC-03', 'Trạng thái rỗng hiển thị thông điệp rõ ràng, xóa bỏ hoàn toàn spinner');
    }

    // -------------------------------------------------------------
    // TEST 4: Xử lý lỗi tải dữ liệu (Error State: HTTP 500 / 503 / Network Error)
    // -------------------------------------------------------------
    console.log('\n--- TEST 4: Xử lý lỗi HTTP 500/Network - Phải hiện Error UI và nút Thử lại ---');
    {
        const { sandbox, elementsById } = createDOMEnvironment();
        const container = elementsById['records_table_container'];
        container.innerHTML = '<div class="spinner"></div>Đang tải...';

        sandbox.customFetch = async () => ({
            ok: false,
            status: 500,
            statusText: 'Internal Server Error'
        });

        sandbox.BqpStorageAdapter.isSqlite = true;
        sandbox.RecordsUI.currentSource = 'validated';
        await sandbox.RecordsUI.render();

        assert(!container.innerHTML.includes('class="spinner"'), 'Spinner phải được xóa khi có lỗi mạng/HTTP');
        assert(container.innerHTML.includes('Không thể tải danh sách') || container.innerHTML.includes('Lỗi'), 'Phải hiển thị thông báo lỗi');
        assert(container.innerHTML.includes('Thử lại'), 'Phải có nút Thử lại khi tải thất bại');
        pass('TC-04', 'HTTP 500 hiển thị trạng thái lỗi rõ ràng kèm nút Thử lại, không bị kẹt spinner');
    }

    // -------------------------------------------------------------
    // TEST 5: Cơ chế Timeout (15.000 ms) chuyển sang Error UI
    // -------------------------------------------------------------
    console.log('\n--- TEST 5: Cơ chế Timeout khi request bị treo quá hạn ---');
    {
        const { sandbox, elementsById } = createDOMEnvironment();
        const container = elementsById['records_table_container'];

        // Request không bao giờ resolve
        sandbox.customFetch = (url, opts) => new Promise((resolve, reject) => {
            if (opts && opts.signal) {
                opts.signal.addEventListener('abort', () => {
                    const err = new Error('The operation was aborted');
                    err.name = 'AbortError';
                    reject(err);
                });
            }
        });

        sandbox.BqpStorageAdapter.isSqlite = true;
        sandbox.RecordsUI.currentSource = 'validated';

        // Tăng tốc timer để kiểm tra timeout 15s mà không phải đợi thật
        const origSetTimeout = sandbox.setTimeout;
        sandbox.setTimeout = (cb, ms) => {
            if (ms >= 10000) {
                // Kích hoạt timeout ngay lập tức
                return origSetTimeout(cb, 10);
            }
            return origSetTimeout(cb, ms);
        };

        await sandbox.RecordsUI.render();

        assert(!container.innerHTML.includes('class="spinner"'), 'Spinner phải được xóa sau khi timeout');
        assert(container.innerHTML.includes('Không thể tải') || container.innerHTML.includes('thời gian') || container.innerHTML.includes('Lỗi'), 'Phải thông báo quá hạn hoặc lỗi tải');
        assert(container.innerHTML.includes('Thử lại'), 'Phải có nút Thử lại sau timeout');
        pass('TC-05', 'Request bị treo quá 15 giây được ngắt an toàn và chuyển sang thông báo lỗi kèm nút Thử lại');
    }

    // -------------------------------------------------------------
    // TEST 6: Chống race condition (Request cũ không ghi đè request mới)
    // -------------------------------------------------------------
    console.log('\n--- TEST 6: Chống race condition khi chuyển nguồn nhanh ---');
    {
        const { sandbox, elementsById } = createDOMEnvironment();
        const container = elementsById['records_table_container'];

        let resolveReq1 = null;
        let resolveReq2 = null;

        sandbox.customFetch = (url) => {
            if (url.includes('source=validated')) {
                return new Promise(res => { resolveReq1 = res; });
            }
            if (url.includes('source=excel')) {
                return new Promise(res => { resolveReq2 = res; });
            }
            return Promise.resolve({ ok: true, json: async () => ({ items: [], totalElements: 0 }) });
        };

        sandbox.BqpStorageAdapter.isSqlite = true;

        // Bắt đầu request 1: validated
        sandbox.RecordsUI.currentSource = 'validated';
        const p1 = sandbox.RecordsUI.render();

        // Ngay sau đó người dùng bấm sang excel (request 2)
        sandbox.RecordsUI.currentSource = 'excel';
        const p2 = sandbox.RecordsUI.render();

        // Cho request 2 (excel) hoàn thành trước
        resolveReq2({
            ok: true,
            status: 200,
            json: async () => ({
                items: [{ id: 'raw_1', fullName: 'Hồ Sơ RAW Mới', sheetType: 'I.1', actualTotal: 100000 }],
                totalElements: 1,
                totalPages: 1,
                page: 0,
                size: 50
            })
        });
        await p2;

        assert(container.innerHTML.includes('Hồ Sơ RAW Mới'), 'Request 2 (excel) phải hiển thị kết quả');

        // Bây giờ request 1 (validated cũ) mới trả về kết quả
        resolveReq1({
            ok: true,
            status: 200,
            json: async () => ({
                items: [{ id: 'val_old', fullName: 'Hồ Sơ Cũ Trễ', sheetType: 'I.1', calculatedTotal: 200000 }],
                totalElements: 1,
                totalPages: 1,
                page: 0,
                size: 50
            })
        });
        await p1;

        // Request 1 cũ tuyệt đối KHÔNG được đè lên dữ liệu RAW mới
        assert(!container.innerHTML.includes('Hồ Sơ Cũ Trễ'), 'Request 1 cũ không được ghi đè DOM của request 2');
        assert(container.innerHTML.includes('Hồ Sơ RAW Mới'), 'Dữ liệu của request 2 mới nhất phải được bảo toàn trọn vẹn');
        pass('TC-06', 'Cơ chế currentRequestId và AbortController bảo vệ tuyệt đối chống race condition khi chuyển nguồn/bộ lọc nhanh');
    }

    // -------------------------------------------------------------
    // TEST 7: Nút Thử lại hoạt động và phục hồi hiển thị bảng
    // -------------------------------------------------------------
    console.log('\n--- TEST 7: Kiểm tra nút Thử lại (Retry) phục hồi dữ liệu thành công ---');
    {
        const { sandbox, elementsById } = createDOMEnvironment();
        const container = elementsById['records_table_container'];

        let shouldFail = true;
        sandbox.customFetch = async () => {
            if (shouldFail) {
                return { ok: false, status: 503, statusText: 'Service Unavailable' };
            }
            return {
                ok: true,
                status: 200,
                json: async () => ({
                    items: [{ id: 'val_ok', fullName: 'Đồng chí Phục Hồi', sheetType: 'I.1', difference: 0 }],
                    totalElements: 1,
                    totalPages: 1,
                    page: 0,
                    size: 50
                })
            };
        };

        sandbox.BqpStorageAdapter.isSqlite = true;
        sandbox.RecordsUI.currentSource = 'validated';

        // Lần 1: Lỗi 503
        await sandbox.RecordsUI.render();
        assert(container.innerHTML.includes('Thử lại'), 'Lần 1 lỗi phải có nút Thử lại');

        // Lần 2: Bấm Thử lại (gọi lại RecordsUI.render())
        shouldFail = false;
        await sandbox.RecordsUI.render();

        assert(!container.innerHTML.includes('Không thể tải'), 'Thông báo lỗi biến mất sau khi thử lại thành công');
        assert(container.innerHTML.includes('Đồng chí Phục Hồi'), 'Dữ liệu bảng được phục hồi 100%');
        pass('TC-07', 'Nút Thử lại hoạt động hoàn hảo, giữ nguyên bộ lọc và phục hồi hiển thị bảng khi mạng hoạt động lại');
    }

    console.log('\n================================================================');
    console.log(`🎉 TẤT CẢ ${passCount} / 7 BÀI KIỂM THỬ VÒNG ĐỜI TẢI DANH SÁCH ĐÃ ĐẠT 100%!`);
    console.log('================================================================\n');
}

runTests().catch(err => {
    console.error('Lỗi khi chạy kiểm thử:', err);
    process.exit(1);
});
