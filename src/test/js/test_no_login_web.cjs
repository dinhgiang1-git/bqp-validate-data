#!/usr/bin/env node
/**
 * test_no_login_web.cjs
 * Kiểm tra tĩnh: toàn bộ code đăng nhập web đã bị xóa khỏi index.html và các bản đồng bộ.
 * Tương ứng ma trận: WEB-01, WEB-02 (phần kiểm tra mã nguồn).
 */
'use strict';

const fs   = require('fs');
const path = require('path');
const crypto = require('crypto');

const BQP_ROOT = 'd:\\bqp';
const STATIC   = path.join(BQP_ROOT, 'bqp-validate-data', 'src', 'main', 'resources', 'static');

const FILES_TO_CHECK = [
    { label: 'index.html (nguồn chuẩn)',     file: path.join(STATIC, 'index.html') },
    { label: 'cong_cu_tinh_toan_bqp4.0.html', file: path.join(BQP_ROOT, 'cong_cu_tinh_toan_bqp4.0.html') },
    { label: 'target/classes/static/index.html',
      file: path.join(BQP_ROOT, 'bqp-validate-data', 'target', 'classes', 'static', 'index.html') },
];

// Các chuỗi KHÔNG ĐƯỢC phép tồn tại trong bất kỳ file nào
const FORBIDDEN = [
    { kw: 'const AuthManager',          desc: 'Đối tượng AuthManager JS' },
    { kw: 'id="loginOverlay"',           desc: 'HTML div #loginOverlay' },
    { kw: '"admin": "123456"',           desc: 'Tài khoản mặc định admin/123456' },
    { kw: 'STORAGE_KEY: "bqp_auth_session"', desc: 'Khóa session storage cũ' },
    { kw: 'id="userSessionBar"',         desc: 'Thanh tài khoản #userSessionBar' },
    { kw: 'AuthManager.logout()',        desc: 'Nút đăng xuất gọi AuthManager.logout' },
    { kw: 'AuthManager.init()',          desc: 'Khởi tạo AuthManager' },
    { kw: 'AuthManager.handleSubmit',    desc: 'Form submit AuthManager' },
    { kw: '/* === LOGIN OVERLAY === */', desc: 'CSS block LOGIN OVERLAY' },
    { kw: '.login-overlay {',            desc: 'CSS class .login-overlay' },
    { kw: 'id="loginForm"',             desc: 'Form đăng nhập #loginForm' },
    { kw: 'id="login_username"',         desc: 'Input tên đăng nhập' },
    { kw: 'Ghi nhớ đăng nhập',          desc: 'Checkbox ghi nhớ đăng nhập' },
];

// Chuỗi PHẢI có trong index.html (chứng minh trang vẫn hoạt động)
const REQUIRED_IN_MAIN = [
    { kw: 'bqp_auth_session',
      desc: 'Đoạn dọn localStorage cũ (cleanup)',
      note: 'Đây là đoạn removeItem – hợp lệ' },
];

// ── Runner ───────────────────────────────────────────────────────────────────

let passed = 0;
let failed = 0;
const errors = [];

function PASS(msg) { console.log(`✅ [PASS] ${msg}`); passed++; }
function FAIL(msg) { console.log(`❌ [FAIL] ${msg}`); failed++; errors.push(msg); }

console.log('================================================================');
console.log('BẮT ĐẦU KIỂM THỬ: KHÔNG CÒN CODE ĐĂNG NHẬP WEB (WEB-01/WEB-02)');
console.log('================================================================\n');

// 1. Kiểm tra từng file
for (const { label, file } of FILES_TO_CHECK) {
    if (!fs.existsSync(file)) {
        console.log(`⚠️  Bỏ qua (file không tồn tại): ${label}`);
        continue;
    }
    const content = fs.readFileSync(file, 'utf8');
    for (const { kw, desc } of FORBIDDEN) {
        if (content.includes(kw)) {
            FAIL(`Vẫn còn "${desc}" trong: ${label}`);
        } else {
            PASS(`Đã xóa "${desc}" khỏi: ${label}`);
        }
    }
}

// 2. Kiểm tra file nguồn chuẩn có đoạn cleanup localStorage
const mainHtml = path.join(STATIC, 'index.html');
if (fs.existsSync(mainHtml)) {
    const content = fs.readFileSync(mainHtml, 'utf8');
    for (const { kw, desc, note } of REQUIRED_IN_MAIN) {
        if (content.includes(kw)) {
            PASS(`Có đoạn ${desc} (${note}) trong index.html`);
        } else {
            FAIL(`Thiếu đoạn ${desc} trong index.html`);
        }
    }

    // Kiểm tra localStorage.removeItem (đoạn cleanup)
    if (content.includes("localStorage.removeItem('bqp_auth_session')")) {
        PASS('Đoạn dọn localStorage chứa removeItem bqp_auth_session');
    } else {
        FAIL('Thiếu đoạn dọn localStorage bqp_auth_session');
    }
}

// 3. Đồng bộ hash giữa các bản
const existingFiles = FILES_TO_CHECK.filter(f => fs.existsSync(f.file));
if (existingFiles.length >= 2) {
    const hashes = existingFiles.map(f => ({
        label: f.label,
        hash: crypto.createHash('sha256').update(fs.readFileSync(f.file)).digest('hex'),
    }));
    const masterHash = hashes[0].hash;
    let allSync = true;
    for (let i = 1; i < hashes.length; i++) {
        if (hashes[i].hash !== masterHash) {
            FAIL(`File ${hashes[i].label} KHÔNG đồng bộ hash với nguồn chuẩn`);
            allSync = false;
        }
    }
    if (allSync) {
        PASS(`Tất cả ${existingFiles.length} bản HTML đồng bộ 100% hash (SHA-256: ${masterHash.slice(0, 16)}...)`);
    }
}

// 4. Kiểm tra security/LauncherSessionFilter.java tồn tại
const filterPath = path.join(BQP_ROOT, 'bqp-validate-data', 'src', 'main', 'java',
    'com', 'bqpvalidateexcel', 'security', 'LauncherSessionFilter.java');
if (fs.existsSync(filterPath)) {
    const fc = fs.readFileSync(filterPath, 'utf8');
    if (fc.includes('OncePerRequestFilter') && fc.includes('/launcher/')) {
        PASS('LauncherSessionFilter.java tồn tại và có logic bypass /launcher/**');
    } else {
        FAIL('LauncherSessionFilter.java thiếu logic cần thiết');
    }
} else {
    FAIL('LauncherSessionFilter.java chưa được tạo');
}

// 5. Kiểm tra launcher files
const launcherFiles = ['PasswordVerifier.cs', 'PasswordDialog.cs', 'CongCuThamDinhBQP.cs'];
for (const lf of launcherFiles) {
    const lp = path.join(BQP_ROOT, 'launcher', lf);
    if (fs.existsSync(lp)) {
        PASS(`Tệp launcher ${lf} tồn tại`);
    } else {
        FAIL(`Tệp launcher ${lf} chưa được tạo`);
    }
}

// Kiểm tra PasswordVerifier.cs có PBKDF2
const pvPath = path.join(BQP_ROOT, 'launcher', 'PasswordVerifier.cs');
if (fs.existsSync(pvPath)) {
    const pvc = fs.readFileSync(pvPath, 'utf8');
    if (pvc.includes('Rfc2898DeriveBytes') && pvc.includes('ConstantTimeEquals')) {
        PASS('PasswordVerifier.cs dùng PBKDF2 và so sánh thời gian cố định');
    } else {
        FAIL('PasswordVerifier.cs thiếu PBKDF2 hoặc constant-time comparison');
    }
    if (pvc.includes('%%LAUNCHER_SALT%%')) {
        PASS('PasswordVerifier.cs có placeholder salt/hash chờ inject khi build');
    } else {
        FAIL('PasswordVerifier.cs thiếu placeholder %%LAUNCHER_SALT%%');
    }
}

// Kiểm tra CongCuThamDinhBQP.cs có STAThread và PasswordDialog
const mainCs = path.join(BQP_ROOT, 'launcher', 'CongCuThamDinhBQP.cs');
if (fs.existsSync(mainCs)) {
    const mcc = fs.readFileSync(mainCs, 'utf8');
    if (mcc.includes('[STAThread]') && mcc.includes('PasswordDialog')) {
        PASS('CongCuThamDinhBQP.cs có STAThread và gọi PasswordDialog trước main flow');
    } else {
        FAIL('CongCuThamDinhBQP.cs thiếu [STAThread] hoặc PasswordDialog call');
    }
    if (mcc.includes('LAUNCHER_SESSION_TOKEN') && mcc.includes('GenerateSecureToken')) {
        PASS('CongCuThamDinhBQP.cs tạo token phiên và truyền qua biến môi trường');
    } else {
        FAIL('CongCuThamDinhBQP.cs thiếu session token generation');
    }
    if (mcc.includes('/launcher/bootstrap') && mcc.includes('/launcher/health')) {
        PASS('CongCuThamDinhBQP.cs dùng /launcher/health và /launcher/bootstrap');
    } else {
        FAIL('CongCuThamDinhBQP.cs thiếu endpoint bootstrap/health');
    }
    if (!mcc.includes('http://localhost:8080\\"') && mcc.includes('bootstrapUrl')) {
        PASS('CongCuThamDinhBQP.cs mở URL bootstrap thay vì trực tiếp localhost:8080');
    }
    if (mcc.includes('X-BQP-Launcher-Internal')) {
        PASS('CongCuThamDinhBQP.cs gửi header nội bộ khi shutdown');
    } else {
        FAIL('CongCuThamDinhBQP.cs thiếu header X-BQP-Launcher-Internal');
    }
}

// Kiểm tra BqpValidateExcelApplication.java không còn tự mở browser
const appJavaPath = path.join(BQP_ROOT, 'bqp-validate-data', 'src', 'main', 'java',
    'com', 'bqpvalidateexcel', 'BqpValidateExcelApplication.java');
if (fs.existsSync(appJavaPath)) {
    const ajc = fs.readFileSync(appJavaPath, 'utf8');
    if (!ajc.includes('ApplicationReadyEvent') && !ajc.includes('autoOpenBrowser')) {
        PASS('BqpValidateExcelApplication.java không còn tự mở trình duyệt');
    } else {
        FAIL('BqpValidateExcelApplication.java vẫn còn code tự mở trình duyệt');
    }
}

// Kiểm tra SystemController.java bảo vệ shutdown
const scPath = path.join(BQP_ROOT, 'bqp-validate-data', 'src', 'main', 'java',
    'com', 'bqpvalidateexcel', 'storage', 'controller', 'SystemController.java');
if (fs.existsSync(scPath)) {
    const scc = fs.readFileSync(scPath, 'utf8');
    if (scc.includes('INTERNAL_HEADER') && scc.includes('FORBIDDEN')) {
        PASS('SystemController.java bảo vệ API shutdown bằng phiên/header nội bộ');
    } else {
        FAIL('SystemController.java chưa bảo vệ API shutdown');
    }
}

// ── Kết quả ─────────────────────────────────────────────────────────────────
const total = passed + failed;
console.log('\n================================================================');
if (failed === 0) {
    console.log(`✅ TẤT CẢ ${total} / ${total} BÀI KIỂM THỬ ĐÃ VƯỢT QUA 100%!`);
    console.log('Hệ thống xác thực launcher/backend đã triển khai đúng chuẩn!');
} else {
    console.log(`❌ ${failed} / ${total} BÀI KIỂM THỬ THẤT BẠI:`);
    for (const e of errors) console.log(`   - ${e}`);
}
console.log('================================================================');
if (failed > 0) process.exit(1);
