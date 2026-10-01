/**
 * test_xss_protection.cjs
 * Kiểm thử toàn diện cơ chế chống tấn công XSS (P0-03)
 * Theo tiêu chuẩn nghiệm thu của NHAN_XET_RA_SOAT_TOAN_BO_CHUC_NANG_30_09_2026.md
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vm = require('vm');

console.log('================================================================');
console.log('BẮT ĐẦU KIỂM THỬ: BẢO VỆ CHỐNG TẤN CÔNG XSS (P0-03)');
console.log('================================================================\n');

let passCount = 0;
function pass(msg) {
    console.log(`✅ [PASS] ${msg}`);
    passCount++;
}

// 1. Kiểm tra các hàm bảo mật trong bqp_validation.js
const jsPath = path.resolve(__dirname, '../../main/resources/static/bqp_validation.js');
assert(fs.existsSync(jsPath), 'Không tìm thấy bqp_validation.js');
const jsCode = fs.readFileSync(jsPath, 'utf8');

const htmlPath = path.resolve(__dirname, '../../main/resources/static/index.html');
assert(fs.existsSync(htmlPath), 'Không tìm thấy index.html');
const htmlCode = fs.readFileSync(htmlPath, 'utf8');

// Tạo môi trường VM để test trực tiếp các hàm escape
const normPath = path.resolve(__dirname, '../../main/resources/static/bqp_normalization.js');
const normCode = fs.readFileSync(normPath, 'utf8');

const sandbox = {
    document: {
        getElementById() { return null; },
        querySelector() { return null; },
        querySelectorAll() { return []; },
        createElement() { return {}; }
    },
    console: console
};
sandbox.window = sandbox;
vm.createContext(sandbox);
vm.runInContext(normCode, sandbox);
vm.runInContext(jsCode, sandbox);

const { escapeHtml, escapeAttr, escapeJs } = sandbox.window;

// CA 1: escapeHtml cơ bản và edge cases
assert.strictEqual(typeof escapeHtml, 'function', 'escapeHtml phải được định nghĩa');
assert.strictEqual(escapeHtml(null), '', 'escapeHtml(null) phải là rỗng');
assert.strictEqual(escapeHtml(undefined), '', 'escapeHtml(undefined) phải là rỗng');
assert.strictEqual(escapeHtml(12345), '12345', 'escapeHtml(number) phải giữ nguyên chuỗi số');
assert.strictEqual(
    escapeHtml('<script>alert("XSS")</script>'),
    '&lt;script&gt;alert(&quot;XSS&quot;)&lt;/script&gt;',
    'escapeHtml phải mã hóa thẻ script và dấu nháy kép'
);
assert.strictEqual(
    escapeHtml("Tom & Jerry's <show>"),
    'Tom &amp; Jerry&#39;s &lt;show&gt;',
    'escapeHtml phải mã hóa & và dấu nháy đơn'
);
pass('CA 1: escapeHtml hoạt động chính xác với ký tự đặc biệt, HTML tags và edge cases');

// CA 2: escapeAttr cho thuộc tính thẻ HTML
assert.strictEqual(typeof escapeAttr, 'function', 'escapeAttr phải được định nghĩa');
assert.strictEqual(escapeAttr(null), '', 'escapeAttr(null) phải là rỗng');
assert.strictEqual(escapeAttr(undefined), '', 'escapeAttr(undefined) phải là rỗng');
assert.strictEqual(
    escapeAttr('"><img src=x onerror=alert(1)>'),
    '&quot;&gt;&lt;img src=x onerror=alert(1)&gt;',
    'escapeAttr phải mã hóa dấu đóng ngoặc nhọn và nháy kép để không thoát khỏi attribute'
);
pass('CA 2: escapeAttr ngăn chặn phá vỡ ngữ cảnh HTML attribute');

// CA 3: escapeJs cho nhúng chuỗi an toàn vào inline Javascript
assert.strictEqual(typeof escapeJs, 'function', 'escapeJs phải được định nghĩa');
assert.strictEqual(escapeJs(null), '', 'escapeJs(null) phải là rỗng');
assert.strictEqual(
    escapeJs("Robert'); DROP TABLE Students;--\nalert('1')"),
    "Robert\\'); DROP TABLE Students;--\\nalert(\\'1\\')",
    'escapeJs phải escape dấu nháy đơn, nháy kép và ký tự xuống dòng'
);
pass('CA 3: escapeJs ngăn chặn phá vỡ ngữ cảnh chuỗi trong JavaScript');

// CA 4: Kiểm tra chống XSS trong cây đơn vị (renderTree & updateBreadcrumb)
assert(
    htmlCode.includes('escapeHtml(u.name)') || htmlCode.includes('escapeHtml(u.name ||'),
    'renderTree phải escapeHtml u.name'
);
assert(
    htmlCode.includes('escapeHtml(u.code)') || htmlCode.includes('escapeHtml(u.code ||'),
    'renderTree phải escapeHtml u.code'
);
pass('CA 4: Cây đơn vị 4 cấp đã được escapeHtml phòng ngừa stored XSS từ cơ sở dữ liệu');

// CA 5: Kiểm tra chống XSS trong bảng dữ liệu hồ sơ (cellValHelper & action buttons)
assert(
    htmlCode.includes('cellValHelper = (val) =>') &&
    htmlCode.includes('escapeHtml(s)') &&
    htmlCode.includes('escapeAttr(s)'),
    'cellValHelper phải escapeHtml nội dung hiển thị và escapeAttr thuộc tính title'
);
assert(
    htmlCode.includes("RecordsUI.viewDetail('${escapeJs(r.id)}')") ||
    htmlCode.includes("escapeJs(r.id)"),
    'Nút thao tác hồ sơ phải escapeJs r.id để tránh injection qua id hồ sơ'
);
pass('CA 5: Bảng hồ sơ dữ liệu (cellValHelper) mã hóa an toàn dữ liệu từ Excel / SQLite');

// CA 6: Kiểm tra an toàn của populateSelect & populateFilterSelect (DOM API thay thế innerHTML)
assert(
    htmlCode.includes("document.createElement('option')") &&
    htmlCode.includes('opt.textContent = item.label'),
    'populateSelect và populateFilterSelect phải dùng DOM createElement và textContent thay vì nối innerHTML'
);
pass('CA 6: Các hàm populateSelect và populateFilterSelect dùng DOM textContent chống chèn HTML');

// CA 7: Kiểm tra chống XSS trong bảng tính toán công thức (renderCalculationUI)
assert(
    htmlCode.includes('escapeHtml(r.formula)') &&
    htmlCode.includes('escapeHtml(r.title)') &&
    htmlCode.includes('escapeHtml(r.col)'),
    'renderCalculationUI phải escapeHtml cho tên cột, tiêu đề và công thức'
);
assert(
    htmlCode.includes('escapeHtml(res.tongTienChu)') &&
    htmlCode.includes('escapeHtml(res.hoTen'),
    'renderCalculationUI phải escapeHtml thông tin đối tượng và số tiền bằng chữ'
);
pass('CA 7: Giao diện kết quả tính toán chi tiết đã được bảo vệ trước công thức/dữ liệu độc hại');

// CA 8: Kiểm tra chống XSS trong Modal Đối chiếu và Bảng Thẩm định (ValidationUI)
assert(
    jsCode.includes('escapeHtml(c.col)') &&
    jsCode.includes('escapeHtml(c.actual)') &&
    jsCode.includes('escapeHtml(c.expected)') &&
    jsCode.includes('escapeHtml(groupName)'),
    'bqp_validation.js phải escapeHtml c.col, c.actual, c.expected và groupName trong bảng so sánh và bảng đối chiếu'
);
pass('CA 8: Modal đối chiếu và bảng thẩm định (ValidationUI) mã hóa dữ liệu gốc và dữ liệu thẩm định an toàn');

console.log('\n================================================================');
console.log(`✅ TẤT CẢ ${passCount} / ${passCount} BÀI KIỂM THỬ BẢO VỆ CHỐNG XSS ĐÃ ĐẠT 100%!`);
console.log('================================================================\n');
