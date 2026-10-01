/**
 * Comprehensive Automated Test Suite for BQP ToastManager, ConfirmModal and Zero-Native-Dialog
 * 
 * 1. Static Code Analysis (Zero-Native-Dialog Strict Enforcement):
 *    - 0 native alert() calls across all 8 source and target files (zero fallback exceptions)
 *    - 0 native confirm() calls across all 8 source and target files
 *    - 0 native prompt() calls across all 8 source and target files
 * 2. SHA-256 Hash Synchronization across all 4 HTML files and all 4 JS files
 * 3. ToastManager Unit & Behavior Tests (ARIA, types, auto-container, max-3 queue, action callbacks)
 * 4. ConfirmModal Unit & Behavior Tests (confirm resolve, cancel resolve, danger tone autofocus, escape, choose options)
 * 5. Quick Add Unit Modal (#valQuickAddUnitModal) & ValidationUI Behavior Tests:
 *    - Inline validation on empty name (no native prompt/alert)
 *    - Inline duplicate name validation in same parent
 *    - Anti-double-click protection (submit button disabled during async save)
 *    - Success flow: adds unit, updates dropdown, closes modal, triggers ToastManager.success
 *    - Failure flow: catches error, shows inline message, triggers ToastManager.error, restores submit button
 *    - Escape & Enter keyboard navigation with stopPropagation() protection
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const vm = require('vm');
const assert = require('assert');

console.log('================================================================');
console.log('BẮT ĐẦU KIỂM THỬ HỆ THỐNG TOAST, CONFIRM MODAL & ZERO-NATIVE-DIALOG');
console.log('================================================================\n');

let passCount = 0;
let failCount = 0;

function runTest(name, fn) {
    try {
        fn();
        console.log(`✅ [PASS] ${name}`);
        passCount++;
    } catch (err) {
        console.error(`❌ [FAIL] ${name}:`, err.message);
        failCount++;
    }
}

async function runAsyncTest(name, fn) {
    try {
        await fn();
        console.log(`✅ [PASS] ${name}`);
        passCount++;
    } catch (err) {
        console.error(`❌ [FAIL] ${name}:`, err.message);
        failCount++;
    }
}

// ---------------------------------------------------------------------
// Minimal DOM Mock for Node environment
// ---------------------------------------------------------------------
function createDOMEnvironment() {
    const listeners = {};

    function createElement(tag, id = '') {
        const el = {
            tagName: tag.toUpperCase(),
            id: id || '',
            _className: '',
            get className() { return this._className; },
            set className(val) {
                this._className = String(val);
                this._classes = new Set(this._className.trim().split(/\s+/).filter(Boolean));
            },
            _classes: new Set(),
            classList: {
                add(c) { el._classes.add(c); el._className = Array.from(el._classes).join(' '); },
                remove(c) { el._classes.delete(c); el._className = Array.from(el._classes).join(' '); },
                contains(c) { return el._classes.has(c); }
            },
            style: {},
            children: [],
            parentNode: null,
            _textContent: '',
            get textContent() { return this._textContent; },
            set textContent(val) {
                this._textContent = String(val);
                this.children = [];
            },
            _innerHTML: '',
            get innerHTML() { return this._innerHTML; },
            set innerHTML(val) {
                this._innerHTML = String(val);
                if (!val) {
                    this.children = [];
                }
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
                if (idx !== -1) {
                    this.children.splice(idx, 1);
                    child.parentNode = null;
                }
                return child;
            },
            get firstElementChild() { return this.children[0] || null; },
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
            dispatch(ev, data = {}) {
                if (this._listeners[ev]) {
                    const eventObj = {
                        preventDefault: () => { eventObj.defaultPrevented = true; },
                        stopPropagation: () => { eventObj.propagationStopped = true; },
                        defaultPrevented: false,
                        propagationStopped: false,
                        ...data
                    };
                    this._listeners[ev].forEach(cb => cb(eventObj));
                    return eventObj;
                }
                return { defaultPrevented: false, propagationStopped: false };
            },
            querySelector(sel) {
                if (sel.startsWith('#')) return findChild(this, c => c.id === sel.substring(1));
                if (sel.startsWith('.')) return findChild(this, c => c.classList.contains(sel.substring(1)));
                return findChild(this, c => c.tagName.toLowerCase() === sel.toLowerCase());
            },
            querySelectorAll(sel) {
                const results = [];
                collectChildren(this, c => {
                    if (sel.startsWith('.')) return c.classList.contains(sel.substring(1));
                    if (sel.includes(':not([disabled])')) {
                        const tag = sel.split(':')[0];
                        return c.tagName.toLowerCase() === tag.toLowerCase() && !c.disabled;
                    }
                    return c.tagName.toLowerCase() === sel.toLowerCase();
                }, results);
                return results;
            }
        };
        return el;
    }

    function findChild(parent, predicate) {
        for (const c of parent.children) {
            if (predicate(c)) return c;
            const sub = findChild(c, predicate);
            if (sub) return sub;
        }
        return null;
    }

    function collectChildren(parent, predicate, results) {
        for (const c of parent.children) {
            if (predicate(c)) results.push(c);
            collectChildren(c, predicate, results);
        }
    }

    const mockBody = createElement('body');

    // Pre-create modal overlay as it exists in index.html markup
    const overlay = createElement('div', 'bqp_confirm_modal_overlay');
    const card = createElement('div');
    card.className = 'bqp-confirm-card';
    const header = createElement('div');
    header.className = 'bqp-confirm-header';
    const title = createElement('h3', 'bqp_confirm_title');
    const closeBtn = createElement('button', 'bqp_confirm_close_btn');
    header.appendChild(title);
    header.appendChild(closeBtn);
    const body = createElement('div');
    body.className = 'bqp-confirm-body';
    const msg = createElement('div', 'bqp_confirm_message');
    const details = createElement('div', 'bqp_confirm_details');
    body.appendChild(msg);
    body.appendChild(details);
    const footer = createElement('div', 'bqp_confirm_footer');
    footer.className = 'bqp-confirm-footer';
    card.appendChild(header);
    card.appendChild(body);
    card.appendChild(footer);
    overlay.appendChild(card);
    mockBody.appendChild(overlay);

    const mockDoc = {
        body: mockBody,
        activeElement: null,
        createElement,
        getElementById(id) {
            return findChild(mockBody, c => c.id === id);
        },
        querySelector(sel) {
            return mockBody.querySelector(sel);
        },
        querySelectorAll(sel) {
            return mockBody.querySelectorAll(sel);
        },
        addEventListener(ev, cb) {
            if (!listeners[ev]) listeners[ev] = [];
            listeners[ev].push(cb);
        },
        removeEventListener(ev, cb) {
            if (listeners[ev]) {
                listeners[ev] = listeners[ev].filter(f => f !== cb);
            }
        },
        _triggerKey(key, shiftKey = false) {
            if (listeners['keydown']) {
                listeners['keydown'].forEach(cb => cb({
                    key,
                    shiftKey,
                    preventDefault() {},
                    stopPropagation() {}
                }));
            }
        }
    };

    return { mockDoc, mockBody, createElement };
}

(async function () {
    // -------------------------------------------------------------
    // Test 1: Static Code Analysis - Zero native alert/confirm/prompt
    // -------------------------------------------------------------
    let currentDir = __dirname;
    while (currentDir && !fs.existsSync(path.join(currentDir, 'package.json'))) {
        const parent = path.dirname(currentDir);
        if (parent === currentDir) break;
        currentDir = parent;
    }
    const basePath = currentDir;
    const filesToCheck = [
        'bqp-validate-data/src/main/resources/static/index.html',
        'bqp-validate-data/src/main/resources/static/bqp_validation.js',
        'cong_cu_tinh_toan_bqp4.0.html',
        'BQP_Application_Release/index.html',
        'bqp_validation.js',
        'BQP_Application_Release/bqp_validation.js',
        'bqp-validate-data/target/classes/static/index.html',
        'bqp-validate-data/target/classes/static/bqp_validation.js'
    ];

    function checkDirectCalls(filePath) {
        if (!fs.existsSync(filePath)) return null;
        const content = fs.readFileSync(filePath, 'utf8');
        const lines = content.split('\n');
        const alerts = [];
        const confirms = [];
        const prompts = [];
        lines.forEach((line, idx) => {
            const l = line.trim();
            if (l.startsWith('//') || l.startsWith('*') || l.startsWith('/*')) return;
            
            // STRICT: Zero alert() calls allowed anywhere, zero fallback exceptions
            if (/\balert\s*\(/.test(l)) {
                alerts.push({ line: idx + 1, text: l });
            }
            // STRICT: Zero prompt() calls allowed anywhere
            if (/\bprompt\s*\(/.test(l)) {
                prompts.push({ line: idx + 1, text: l });
            }
            // STRICT: Zero native confirm() calls allowed anywhere
            if (/\bconfirm\s*\(/.test(l)) {
                const isCustomMethod = /ConfirmModal\.confirm|SafeConfirmModal\.confirm|confirm\s*\(\s*options\s*\)|confirm\s*\(\s*opts\s*\)|confirm:\s*function|async\s+confirm\s*\(/.test(l);
                if (!isCustomMethod) {
                    confirms.push({ line: idx + 1, text: l });
                }
            }
        });
        return { alerts, confirms, prompts };
    }

    filesToCheck.forEach(rel => {
        const p = path.resolve(basePath, rel);
        if (!fs.existsSync(p)) return;

        runTest(`Kiểm tra không còn alert() (kể cả fallback) trong: ${rel}`, () => {
            const res = checkDirectCalls(p);
            assert.strictEqual(res.alerts.length, 0, `Còn ${res.alerts.length} alert() tại: ${JSON.stringify(res.alerts)}`);
        });

        runTest(`Kiểm tra không còn confirm() trực tiếp trong: ${rel}`, () => {
            const res = checkDirectCalls(p);
            assert.strictEqual(res.confirms.length, 0, `Còn ${res.confirms.length} confirm() tại: ${JSON.stringify(res.confirms)}`);
        });

        runTest(`Kiểm tra không còn prompt() trực tiếp trong: ${rel}`, () => {
            const res = checkDirectCalls(p);
            assert.strictEqual(res.prompts.length, 0, `Còn ${res.prompts.length} prompt() tại: ${JSON.stringify(res.prompts)}`);
        });
    });

    // -------------------------------------------------------------
    // Test 2: Synchronized Hashes across all HTML and JS copies
    // -------------------------------------------------------------
    function hashFile(rel) {
        const p = path.resolve(basePath, rel);
        return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
    }

    runTest('Kiểm tra các bản HTML đồng bộ 100% hash', () => {
        const h1 = hashFile('bqp-validate-data/src/main/resources/static/index.html');
        const h2 = hashFile('cong_cu_tinh_toan_bqp4.0.html');
        assert.strictEqual(h1, h2, 'static/index.html khác cong_cu_tinh_toan_bqp4.0.html');
        const releaseHtml = path.resolve(basePath, 'BQP_Application_Release/index.html');
        if (fs.existsSync(releaseHtml)) {
            const h3 = hashFile('BQP_Application_Release/index.html');
            assert.strictEqual(h2, h3, 'cong_cu_tinh_toan_bqp4.0.html khác release/index.html');
        }
        const targetHtml = path.resolve(basePath, 'bqp-validate-data/target/classes/static/index.html');
        if (fs.existsSync(targetHtml)) {
            const ht = hashFile('bqp-validate-data/target/classes/static/index.html');
            assert.strictEqual(h1, ht, 'static/index.html khác target/classes/static/index.html');
        }
    });

    runTest('Kiểm tra các bản JS đồng bộ 100% hash', () => {
        const h1 = hashFile('bqp-validate-data/src/main/resources/static/bqp_validation.js');
        const h2 = hashFile('bqp_validation.js');
        assert.strictEqual(h1, h2, 'static/bqp_validation.js khác root bqp_validation.js');
        const releaseJs = path.resolve(basePath, 'BQP_Application_Release/bqp_validation.js');
        if (fs.existsSync(releaseJs)) {
            const h3 = hashFile('BQP_Application_Release/bqp_validation.js');
            assert.strictEqual(h2, h3, 'root bqp_validation.js khác release/bqp_validation.js');
        }
        const targetJs = path.resolve(basePath, 'bqp-validate-data/target/classes/static/bqp_validation.js');
        if (fs.existsSync(targetJs)) {
            const ht = hashFile('bqp-validate-data/target/classes/static/bqp_validation.js');
            assert.strictEqual(h1, ht, 'static/bqp_validation.js khác target/classes/static/bqp_validation.js');
        }
    });

    // -------------------------------------------------------------
    // Test 3: ToastManager Unit & Behavior Tests
    // -------------------------------------------------------------
    const { mockDoc, mockBody, createElement } = createDOMEnvironment();
    globalThis.document = mockDoc;
    globalThis.window = {
        document: mockDoc,
        requestAnimationFrame(cb) { cb(); }
    };

    // Load ToastManager & ConfirmModal code from index.html
    const indexContent = fs.readFileSync(path.resolve(basePath, 'bqp-validate-data/src/main/resources/static/index.html'), 'utf8');
    const startToast = indexContent.indexOf('window.ToastManager = {');
    const startConfirm = indexContent.indexOf('window.ConfirmModal = {');
    const endConfirm = indexContent.indexOf('// ============================================================', startConfirm);
    assert.ok(startToast !== -1 && startConfirm !== -1 && endConfirm !== -1, 'Phải tìm thấy khai báo ToastManager và ConfirmModal');
    const block = indexContent.substring(startToast, endConfirm);
    new Function('window', 'document', block)(window, mockDoc);

    runTest('ToastManager tạo container tự động và có aria-live', () => {
        window.ToastManager.info('Test info');
        const container = mockDoc.getElementById('bqp_toast_container');
        assert.ok(container, 'Container bqp_toast_container phải được tạo');
        assert.strictEqual(container.getAttribute('aria-live'), 'polite');
    });

    runTest('ToastManager hiển thị đúng loại và class CSS (success, error, warning, info)', () => {
        window.ToastManager.success('Thao tác thành công');
        const container = mockDoc.getElementById('bqp_toast_container');
        const latest = container.children[container.children.length - 1];
        assert.ok(latest.classList.contains('bqp-toast-success'), 'Phải có class bqp-toast-success');
        assert.strictEqual(latest.getAttribute('role'), 'status');

        window.ToastManager.error('Có lỗi xảy ra');
        const errToast = container.children[container.children.length - 1];
        assert.ok(errToast.classList.contains('bqp-toast-error'), 'Phải có class bqp-toast-error');
        assert.strictEqual(errToast.getAttribute('role'), 'alert');
    });

    runTest('ToastManager giới hạn tối đa 3 toast đồng thời (tự xóa toast cũ nhất)', () => {
        const container = mockDoc.getElementById('bqp_toast_container');
        container.children = [];
        window.ToastManager.info('Toast 1');
        window.ToastManager.info('Toast 2');
        window.ToastManager.info('Toast 3');
        const countBefore = Array.from(container.children).filter(t => !t._removing).length;
        assert.strictEqual(countBefore, 3, 'Phải có đúng 3 toast');

        window.ToastManager.info('Toast 4');
        const countAfter = Array.from(container.children).filter(t => !t._removing).length;
        assert.strictEqual(countAfter, 3, 'Khi thêm toast thứ 4, số toast hoạt động vẫn là 3 (toast cũ nhất đang gỡ bỏ)');
    });

    runTest('ToastManager hỗ trợ nút hành động action button và kích hoạt callback', () => {
        let actionTriggered = false;
        window.ToastManager.success('Lưu thành công', {
            actionLabel: 'Xem danh sách',
            onAction: () => { actionTriggered = true; }
        });
        const container = mockDoc.getElementById('bqp_toast_container');
        const latest = container.children[container.children.length - 1];
        const btn = latest.querySelector('.bqp-toast-action-btn');
        assert.ok(btn, 'Toast phải có nút action');
        assert.strictEqual(btn.textContent, 'Xem danh sách');
        btn.onclick({ stopPropagation() {} });
        assert.strictEqual(actionTriggered, true, 'onAction callback phải được gọi khi click');
    });

    // -------------------------------------------------------------
    // Test 4: ConfirmModal Unit & Behavior Tests
    // -------------------------------------------------------------

    await runAsyncTest('ConfirmModal.confirm() trả về Promise<boolean> = true khi bấm Xác nhận', async () => {
        const promise = window.ConfirmModal.confirm({
            title: 'Xóa đối tượng?',
            message: 'Bạn có chắc chắn?',
            tone: 'primary',
            confirmLabel: 'Xác nhận'
        });

        const footer = mockDoc.getElementById('bqp_confirm_footer');
        const buttons = footer.querySelectorAll('button');
        const confirmBtn = buttons.find(b => b.classList.contains('bqp-confirm-btn-primary'));
        assert.ok(confirmBtn, 'Phải có nút confirm');
        confirmBtn.onclick();

        const result = await promise;
        assert.strictEqual(result, true, 'Kết quả xác nhận phải là true');
    });

    await runAsyncTest('ConfirmModal.confirm() trả về Promise<boolean> = false khi bấm Hủy', async () => {
        const promise = window.ConfirmModal.confirm({
            title: 'Xóa đối tượng?',
            message: 'Bạn có chắc chắn?',
            tone: 'danger',
            cancelLabel: 'Giữ lại'
        });

        const footer = mockDoc.getElementById('bqp_confirm_footer');
        const buttons = footer.querySelectorAll('button');
        const cancelBtn = buttons.find(b => b.classList.contains('bqp-confirm-btn-secondary'));
        assert.ok(cancelBtn, 'Phải có nút cancel');
        cancelBtn.onclick();

        const result = await promise;
        assert.strictEqual(result, false, 'Kết quả hủy phải là false');
    });

    await runAsyncTest('ConfirmModal.confirm() tone "danger" tự động focus vào nút Cancel để chống bấm nhầm', async () => {
        window.ConfirmModal.confirm({
            title: 'Xóa dữ liệu vĩnh viễn?',
            message: 'Hành động nguy hiểm',
            tone: 'danger'
        });

        const footer = mockDoc.getElementById('bqp_confirm_footer');
        const cancelBtn = footer.querySelectorAll('button').find(b => b.classList.contains('bqp-confirm-btn-secondary'));
        assert.strictEqual(mockDoc.activeElement, cancelBtn, 'Nút Cancel phải được focus mặc định khi tone = danger');
        cancelBtn.onclick();
    });

    await runAsyncTest('ConfirmModal.confirm() xử lý phím Escape để hủy', async () => {
        const promise = window.ConfirmModal.confirm({
            title: 'Thử nghiệm phím Escape',
            message: 'Nhấn Escape'
        });

        mockDoc._triggerKey('Escape');
        const result = await promise;
        assert.strictEqual(result, false, 'Escape phải resolve về false');
    });

    await runAsyncTest('ConfirmModal.choose() trả về đúng giá trị option được chọn', async () => {
        const promise = window.ConfirmModal.choose({
            title: 'Chọn phạm vi',
            message: 'Kích hoạt đơn vị:',
            options: [
                { value: 'single', label: 'Chỉ đơn vị này', variant: 'primary' },
                { value: 'branch', label: 'Cả nhánh con', variant: 'primary' },
                { value: 'cancel', label: 'Hủy bỏ', variant: 'secondary' }
            ],
            dismissValue: 'cancel'
        });

        const footer = mockDoc.getElementById('bqp_confirm_footer');
        const branchBtn = footer.querySelectorAll('button').find(b => b.textContent === 'Cả nhánh con');
        assert.ok(branchBtn, 'Phải có nút Cả nhánh con');
        branchBtn.onclick();

        const result = await promise;
        assert.strictEqual(result, 'branch', 'Kết quả lựa chọn phải là "branch"');
    });

    // -------------------------------------------------------------
    // Test 5: Quick Add Unit Modal (#valQuickAddUnitModal) & ValidationUI Behavior Tests
    // -------------------------------------------------------------
    const jsContent = fs.readFileSync(path.resolve(basePath, 'bqp-validate-data/src/main/resources/static/bqp_validation.js'), 'utf8');

    // Setup elements for Quick Add Unit modal
    const modalElements = {
        valQuickAddUnitModal: createElement('div', 'valQuickAddUnitModal'),
        val_quick_unit_name: createElement('input', 'val_quick_unit_name'),
        val_quick_unit_error: createElement('div', 'val_quick_unit_error'),
        btn_confirm_quick_unit: createElement('button', 'btn_confirm_quick_unit'),
        val_quick_unit_parent: createElement('select', 'val_quick_unit_parent'),
        val_save_parent_select: createElement('select', 'val_save_parent_select'),
        btn_val_quick_add_unit: createElement('button', 'btn_val_quick_add_unit')
    };

    Object.values(modalElements).forEach(el => mockBody.appendChild(el));

    let mockUnits = [
        { id: 'u1', name: 'Bộ Tư lệnh Thông tin', parentId: null, isActive: true, level: 1 },
        { id: 'u2', name: 'Lữ đoàn 139', parentId: 'u1', isActive: true, level: 2 }
    ];

    let capturedToasts = [];
    let addUnitPromiseHook = null;
    let addUnitShouldFail = false;

    const testSandbox = {
        console,
        setTimeout: (cb) => { cb(); },
        clearTimeout: () => {},
        escapeHtml: (s) => String(s),
        document: mockDoc,
        ToastManager: {
            success: (msg) => capturedToasts.push({ type: 'success', msg }),
            error: (msg) => capturedToasts.push({ type: 'error', msg }),
            warning: (msg) => capturedToasts.push({ type: 'warning', msg }),
            info: (msg) => capturedToasts.push({ type: 'info', msg })
        },
        StorageManager: {
            getUnits: () => mockUnits,
            addUnit: (name, parentId) => {
                if (addUnitPromiseHook) {
                    return addUnitPromiseHook(name, parentId);
                }
                if (addUnitShouldFail) {
                    return Promise.reject(new Error('Lỗi CSDL mô phỏng'));
                }
                const newId = 'unit_' + Date.now();
                mockUnits.push({ id: newId, name, parentId, isActive: true });
                return Promise.resolve(newId);
            }
        },
        refreshAllUnitSelects: () => {},
        // STRICT: If ANY code calls native alert/confirm/prompt, throw immediately to fail the test!
        alert: () => { throw new Error('NATIVE_ALERT_CALLED: alert() is strictly forbidden'); },
        confirm: () => { throw new Error('NATIVE_CONFIRM_CALLED: confirm() is strictly forbidden'); },
        prompt: () => { throw new Error('NATIVE_PROMPT_CALLED: prompt() is strictly forbidden'); }
    };
    testSandbox.window = testSandbox;
    testSandbox.globalThis = testSandbox;

    vm.runInNewContext(jsContent, testSandbox);
    const ValidationUI = testSandbox.ValidationUI;

    runTest('ValidationUI.openQuickAddUnitDialog() mở modal, reset form và focus input', () => {
        modalElements.val_quick_unit_name.value = 'Giá trị cũ';
        modalElements.val_quick_unit_error.textContent = 'Lỗi cũ';
        modalElements.val_quick_unit_error.style.display = 'block';

        ValidationUI.openQuickAddUnitDialog();

        assert.ok(modalElements.valQuickAddUnitModal.classList.contains('open'), 'Modal phải có class open');
        assert.strictEqual(modalElements.val_quick_unit_name.value, '', 'Tên phải được reset');
        assert.strictEqual(modalElements.val_quick_unit_error.style.display, 'none', 'Lỗi phải được ẩn');
        assert.strictEqual(mockDoc.activeElement, modalElements.val_quick_unit_name, 'Ô tên phải được focus');
    });

    await runAsyncTest('ValidationUI.confirmQuickAddUnit() kiểm tra rỗng hiển thị lỗi nội tuyến (không dùng prompt/alert)', async () => {
        modalElements.val_quick_unit_name.value = '   ';
        await ValidationUI.confirmQuickAddUnit();

        assert.strictEqual(modalElements.val_quick_unit_error.style.display, 'block', 'Thông báo lỗi phải hiển thị');
        assert.strictEqual(modalElements.val_quick_unit_error.textContent, 'Vui lòng nhập tên đơn vị.');
        assert.strictEqual(mockDoc.activeElement, modalElements.val_quick_unit_name, 'Input phải được focus');
        assert.strictEqual(modalElements.valQuickAddUnitModal.classList.contains('open'), true, 'Modal vẫn phải mở');
    });

    await runAsyncTest('ValidationUI.confirmQuickAddUnit() phát hiện trùng tên cùng cấp trực thuộc', async () => {
        modalElements.val_quick_unit_name.value = 'Bộ Tư lệnh Thông tin';
        modalElements.val_quick_unit_parent.value = ''; // Cấp 1
        await ValidationUI.confirmQuickAddUnit();

        assert.strictEqual(modalElements.val_quick_unit_error.style.display, 'block');
        assert.strictEqual(modalElements.val_quick_unit_error.textContent, 'Đơn vị với tên này đã tồn tại trong cùng cấp trực thuộc.');
        assert.strictEqual(mockDoc.activeElement, modalElements.val_quick_unit_name);
        assert.strictEqual(modalElements.valQuickAddUnitModal.classList.contains('open'), true);
    });

    await runAsyncTest('ValidationUI.confirmQuickAddUnit() chống double-click bằng cách disable nút submit khi đang lưu', async () => {
        modalElements.val_quick_unit_name.value = 'Trung tâm Tác chiến Điện tử';
        modalElements.val_quick_unit_parent.value = 'u1';

        let resolver;
        addUnitPromiseHook = () => new Promise(res => { resolver = res; });

        const savePromise = ValidationUI.confirmQuickAddUnit();
        assert.strictEqual(modalElements.btn_confirm_quick_unit.disabled, true, 'Nút submit phải bị disable trong lúc async save');

        resolver('new_unit_123');
        await savePromise;
        addUnitPromiseHook = null;

        assert.strictEqual(modalElements.btn_confirm_quick_unit.disabled, false, 'Nút submit phải được re-enable sau khi lưu');
        assert.strictEqual(modalElements.valQuickAddUnitModal.classList.contains('open'), false, 'Modal phải đóng sau khi lưu thành công');
        const lastToast = capturedToasts[capturedToasts.length - 1];
        assert.strictEqual(lastToast.type, 'success', 'Phải thông báo Toast success');
        assert.ok(lastToast.msg.includes('Trung tâm Tác chiến Điện tử'), 'Nội dung Toast phải chứa tên đơn vị mới');
    });

    await runAsyncTest('ValidationUI.confirmQuickAddUnit() xử lý lỗi lưu trữ an toàn (re-enable button, giữ modal mở, hiện toast error)', async () => {
        addUnitShouldFail = true;
        ValidationUI.openQuickAddUnitDialog();
        modalElements.val_quick_unit_name.value = 'Đơn vị lỗi';
        modalElements.val_quick_unit_parent.value = '';

        await ValidationUI.confirmQuickAddUnit();
        addUnitShouldFail = false;

        assert.strictEqual(modalElements.btn_confirm_quick_unit.disabled, false, 'Nút submit phải được mở lại');
        assert.strictEqual(modalElements.valQuickAddUnitModal.classList.contains('open'), true, 'Modal vẫn phải mở khi lỗi');
        assert.strictEqual(modalElements.val_quick_unit_error.style.display, 'block');
        assert.ok(modalElements.val_quick_unit_error.textContent.includes('Lỗi CSDL mô phỏng'));
        const lastToast = capturedToasts[capturedToasts.length - 1];
        assert.strictEqual(lastToast.type, 'error', 'Phải kích hoạt Toast error');
    });

    runTest('ValidationUI.closeQuickAddUnitDialog() đóng modal và khôi phục focus về nút mở', () => {
        ValidationUI.openQuickAddUnitDialog();
        assert.ok(modalElements.valQuickAddUnitModal.classList.contains('open'));

        ValidationUI.closeQuickAddUnitDialog();
        assert.strictEqual(modalElements.valQuickAddUnitModal.classList.contains('open'), false, 'Modal phải gỡ class open');
        assert.strictEqual(mockDoc.activeElement, modalElements.btn_val_quick_add_unit, 'Focus phải khôi phục về nút mở');
    });

    runTest('Phím Escape đóng modal tạo nhanh và gọi stopPropagation() để không đóng modal lưu phía dưới', () => {
        ValidationUI.openQuickAddUnitDialog();
        assert.ok(modalElements.valQuickAddUnitModal.classList.contains('open'));

        const eventResult = modalElements.valQuickAddUnitModal.dispatch('keydown', { key: 'Escape' });
        assert.strictEqual(modalElements.valQuickAddUnitModal.classList.contains('open'), false, 'Escape phải đóng modal tạo nhanh');
        assert.strictEqual(eventResult.propagationStopped, true, 'Escape phải gọi stopPropagation()');
        assert.strictEqual(mockDoc.activeElement, modalElements.btn_val_quick_add_unit, 'Phải khôi phục focus');
    });

    await runAsyncTest('Phím Enter kích hoạt xác nhận tạo đơn vị nhanh', async () => {
        ValidationUI.openQuickAddUnitDialog();
        modalElements.val_quick_unit_name.value = 'Tiểu đoàn 50';
        modalElements.val_quick_unit_parent.value = 'u1';

        const eventResult = modalElements.valQuickAddUnitModal.dispatch('keydown', { key: 'Enter' });
        assert.strictEqual(eventResult.defaultPrevented, true, 'Enter phải preventDefault');
        
        // Wait microtasks for async confirm
        await new Promise(r => setTimeout(r, 10));
        assert.strictEqual(modalElements.valQuickAddUnitModal.classList.contains('open'), false, 'Modal phải đóng sau Enter');
    });

    console.log('\n================================================================');
    if (failCount > 0) {
        console.error(`❌ CÓ ${failCount} BÀI KIỂM THỬ THẤT BẠI.`);
        process.exit(1);
    } else {
        console.log(`✅ TẤT CẢ ${passCount} BÀI KIỂM THỬ ĐÃ VƯỢT QUA 100%!`);
        console.log('Hệ thống Toast, Confirm Modal và Zero-Native-Dialog hoạt động chính xác tuyệt đối!');
        console.log('================================================================');
        process.exit(0);
    }
})();
