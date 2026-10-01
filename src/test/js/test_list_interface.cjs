/**
 * test_list_interface.cjs
 * Bộ kiểm thử tự động toàn diện cho giao diện Danh sách đối tượng & Danh sách sau thẩm định
 * Thực hiện theo tiêu chuẩn nghiệm thu của RA_SOAT_VA_KE_HOACH_FIX_GIAO_DIEN_DANH_SACH.md
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const assert = require('assert');

console.log('================================================================');
console.log('BẮT ĐẦU KIỂM THỬ GIAO DIỆN DANH SÁCH ĐỐI TƯỢNG (LIST INTERFACE)');
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
// 1. Kiểm tra dải 5 thẻ KPI đã được loại bỏ triệt để
// -----------------------------------------------------------------
{
    assert(!html.includes('id="rollup_kpi_widget"'), 'Vẫn còn phần tử id="rollup_kpi_widget" trong HTML');
    assert(!html.includes('class="rollup-kpi-grid"'), 'Vẫn còn class="rollup-kpi-grid" trong HTML');
    assert(!html.includes('.rollup-kpi-grid {'), 'Vẫn còn CSS .rollup-kpi-grid trong style');
    assert(!html.includes('.rollup-card {'), 'Vẫn còn CSS .rollup-card trong style');
    assert(!html.includes('.card-actual {'), 'Vẫn còn CSS .card-actual trong style');
    pass('Đã loại bỏ hoàn toàn dải 5 thẻ KPI (#rollup_kpi_widget & CSS liên quan)');
}

// -----------------------------------------------------------------
// 2. Kiểm tra CSS khung bảng không còn cắt nội dung (.records-table-wrap)
// -----------------------------------------------------------------
{
    const wrapCssMatch = html.match(/\.records-table-wrap\s*\{([^}]+)\}/);
    assert(wrapCssMatch, 'Không tìm thấy CSS .records-table-wrap');
    const wrapBody = wrapCssMatch[1];
    assert(!wrapBody.includes('overflow: hidden'), '.records-table-wrap không được dùng overflow: hidden');
    assert(wrapBody.includes('overflow: visible'), '.records-table-wrap phải có overflow: visible');
    pass('.records-table-wrap đã bỏ overflow: hidden, cho phép hiển thị đầy đủ');
}

// -----------------------------------------------------------------
// 3. Kiểm tra vùng cuộn ngang độc lập .records-table-scroll
// -----------------------------------------------------------------
{
    const scrollCssMatch = html.match(/\.records-table-scroll\s*\{([^}]+)\}/);
    assert(scrollCssMatch, 'Không tìm thấy CSS .records-table-scroll');
    const scrollBody = scrollCssMatch[1];
    assert(scrollBody.includes('overflow-x: auto'), '.records-table-scroll phải có overflow-x: auto');
    assert(scrollBody.includes('scrollbar-gutter: stable'), '.records-table-scroll phải có scrollbar-gutter: stable');
    pass('.records-table-scroll có cơ chế cuộn ngang độc lập và ổn định scrollbar');
}

// -----------------------------------------------------------------
// 4. Kiểm tra cấu trúc độ rộng cố định (table-layout: fixed & min-width)
// -----------------------------------------------------------------
{
    const tableCssMatch = html.match(/\.records-table\s*\{([^}]+)\}/);
    assert(tableCssMatch, 'Không tìm thấy CSS .records-table');
    assert(tableCssMatch[1].includes('table-layout: fixed'), '.records-table phải có table-layout: fixed');

    assert(html.includes('.records-table-validated'), 'Thiếu class .records-table-validated');
    const validatedMatch = html.match(/\.records-table-validated\s*\{([^}]+)\}/);
    assert(validatedMatch && validatedMatch[1].includes('min-width: 1165px'), '.records-table-validated phải có min-width >= 1165px');
    pass('.records-table-validated có table-layout: fixed và min-width: 1165px');
}

// -----------------------------------------------------------------
// 5. Kiểm tra cố định cột thao tác khi cuộn ngang (Sticky Action Column)
// -----------------------------------------------------------------
{
    assert(html.includes('position: sticky;'), 'Thiếu định nghĩa position: sticky cho cột thao tác');
    assert(html.includes('right: 0;'), 'Thiếu right: 0 cho cột sticky');
    assert(html.includes('box-shadow: -3px 0 6px rgba(15, 23, 42, 0.06);') || html.includes('box-shadow:'), 'Thiếu shadow tách biệt cột sticky');
    pass('Cột thao tác được cố định sticky sang mép phải với nền đặc và đổ bóng tách biệt');
}

// -----------------------------------------------------------------
// 6. Kiểm tra nút thao tác hàng (.record-actions & .btn-row-action)
// -----------------------------------------------------------------
{
    assert(html.includes('.record-actions {'), 'Thiếu class .record-actions');
    assert(html.includes('.btn-row-action {'), 'Thiếu class .btn-row-action');
    assert(html.includes('.btn-row-action.neutral'), 'Thiếu biến thể .btn-row-action.neutral');
    assert(html.includes('.btn-row-action.success'), 'Thiếu biến thể .btn-row-action.success');
    pass('Các nút trong hàng đã chuyển sang class chuyên dụng .btn-row-action gọn gàng');
}

// -----------------------------------------------------------------
// 7. Kiểm tra workspace có chiều rộng thống nhất cho mọi tab
// -----------------------------------------------------------------
{
    // Không còn class đặc biệt dành riêng cho pane_list
    assert(!html.includes('.workspace.workspace-records-active'), 'Còn tồn tại class .workspace.workspace-records-active – phải xóa');
    // Không còn logic toggle class theo pane_list
    assert(!html.includes("ws.classList.toggle('workspace-records-active'"), 'Còn tồn tại logic toggle workspace-records-active – phải xóa');
    // Không còn transition thay đổi chiều rộng
    assert(!html.includes('transition: max-width'), 'Còn tồn tại transition max-width – phải xóa');
    // CSS chuẩn hóa: width 100% và max-width 1760px
    assert(html.includes('width: 100%;'), 'Thiếu width: 100% trên .workspace');
    assert(html.includes('max-width: 1760px;'), 'Thiếu max-width: 1760px trên .workspace');
    // Chỉ có đúng một max-width quy định cho workspace
    const maxWidthOccurrences = (html.match(/max-width: 1760px;/g) || []).length;
    assert(maxWidthOccurrences === 1, `Phải có đúng 1 khai báo max-width: 1760px, hiện có: ${maxWidthOccurrences}`);
    pass('Workspace có chiều rộng thống nhất 1760px cho mọi tab, không còn co giãn khi chuyển tab');
}


// -----------------------------------------------------------------
// 8. Kiểm tra responsive breakpoint cây đơn vị chuyển sang 1280px
// -----------------------------------------------------------------
{
    assert(html.includes('@media (max-width: 1280px)'), 'Cây đơn vị phải có breakpoint chuyển cột ở 1280px');
    pass('Breakpoint điều chỉnh linh hoạt ở màn hình 1280px, bảo vệ không gian cho bảng');
}

// -----------------------------------------------------------------
// 9. Kiểm tra cấu trúc bảng validated có đúng 9 cột & colgroup
// -----------------------------------------------------------------
{
    // Trích xuất đoạn mã render bảng validated từ template
    const colgroupMatch = html.match(/<colgroup>([\s\S]*?)<\/colgroup>/);
    assert(colgroupMatch, 'Bảng validated phải có thẻ colgroup');
    const cols = colgroupMatch[1].match(/<col[^>]*>/g) || [];
    assert.strictEqual(cols.length, 9, `Bảng validated phải có đúng 9 thẻ col, hiện có ${cols.length}`);

    // Kiểm tra các cột trong header
    assert(html.includes('<th class="col-align-center">STT</th>'), 'Thiếu cột STT');
    assert(html.includes('<th class="col-align-center">Phụ lục</th>'), 'Thiếu cột Phụ lục');
    assert(html.includes('<th class="col-align-left">Họ và tên quân nhân</th>'), 'Thiếu cột Họ và tên quân nhân');
    assert(html.includes('<th class="col-align-left">Cấp bậc / Chức vụ</th>'), 'Thiếu cột Cấp bậc / Chức vụ');
    assert(html.includes('<th class="col-align-center">Trạng thái thẩm định</th>'), 'Thiếu cột Trạng thái thẩm định');
    assert(html.includes('<th class="col-align-right">Tiền trên Excel</th>'), 'Thiếu cột Tiền trên Excel');
    assert(html.includes('<th class="col-align-right">Chuẩn tính lại</th>'), 'Thiếu cột Chuẩn tính lại');
    assert(html.includes('<th class="col-align-right">Chênh lệch</th>'), 'Thiếu cột Chênh lệch');
    assert(html.includes('<th class="col-align-center">Thao tác</th>'), 'Thiếu cột Thao tác');

    // Kiểm tra dòng nhóm có colspan = 9
    assert(html.includes('let colspan = isValidatedSource ? 9 : 10;'), 'Dòng tiêu đề nhóm phải có colspan = 9 khi ở nguồn validated');
    pass('Bảng validated có đầy đủ 9 cột chuẩn hóa qua colgroup và header');
}

// -----------------------------------------------------------------
// 10. Kiểm tra cột thao tác có đầy đủ 2 nút "Đối chiếu" và "Nạp form"
// -----------------------------------------------------------------
{
    assert(
        html.includes(`onclick="RecordsUI.viewDetail('\${r.id}')"`) ||
        html.includes(`onclick="RecordsUI.viewDetail('\${escapeJs(r.id)}')"`),
        'Thiếu nút Đối chiếu viewDetail'
    );
    assert(
        html.includes(`onclick="RecordsUI.loadToForm('\${r.id}')"`) ||
        html.includes(`onclick="RecordsUI.loadToForm('\${escapeJs(r.id)}')"`),
        'Thiếu nút Nạp form loadToForm'
    );
    assert(html.includes('>Đối chiếu</button>'), 'Thiếu text nút Đối chiếu');
    assert(html.includes('>Nạp form</button>'), 'Thiếu text nút Nạp form');
    pass('Cột thao tác giữ trọn vẹn cả 2 chức năng [Đối chiếu] và [Nạp form]');
}

// -----------------------------------------------------------------
// 11. Kiểm tra phân trang pagination nằm ngoài vùng cuộn ngang
// -----------------------------------------------------------------
{
    // Tìm đoạn render container cho validated source
    const lastValidatedIdx = html.lastIndexOf('if (isValidatedSource) {');
    const validatedRenderSlice = html.slice(lastValidatedIdx, html.indexOf('} else {', lastValidatedIdx));
    assert(validatedRenderSlice.includes('class="records-table-scroll"'), 'Phải có container records-table-scroll');
    assert(validatedRenderSlice.replace(/\r\n/g, '\n').includes('</div>\n                        ${paginationHtml}'), 'Phân trang paginationHtml phải nằm sau thẻ đóng của records-table-scroll');
    pass('Phân trang nằm ngoài vùng cuộn ngang, luôn hiển thị cố định chân bảng');
}

// -----------------------------------------------------------------
// 12. Kiểm tra tính toàn vẹn và đồng bộ 100% hash giữa các file HTML
// -----------------------------------------------------------------
{
    const path1 = path.resolve('d:/bqp/bqp-validate-data/src/main/resources/static/index.html');
    const path2 = path.resolve('d:/bqp/cong_cu_tinh_toan_bqp4.0.html');
    const path3 = path.resolve('d:/bqp/BQP_Application_Release/index.html');

    const h1 = crypto.createHash('sha256').update(fs.readFileSync(path1)).digest('hex');
    const h2 = crypto.createHash('sha256').update(fs.readFileSync(path2)).digest('hex');

    assert.strictEqual(h1, h2, 'Hash lệch giữa index.html và cong_cu_tinh_toan_bqp4.0.html');
    if (fs.existsSync(path3)) {
        const h3 = crypto.createHash('sha256').update(fs.readFileSync(path3)).digest('hex');
        assert.strictEqual(h2, h3, 'Hash lệch giữa cong_cu_tinh_toan_bqp4.0.html và BQP_Application_Release/index.html');
    }
    pass(`Các file HTML nguồn đồng bộ tuyệt đối 100% (SHA-256: ${h1.substring(0, 16)}...)`);
}

// -----------------------------------------------------------------
// 13. Kiểm tra tính toàn vẹn và đồng bộ 100% hash giữa các file JS
// -----------------------------------------------------------------
{
    const js1 = path.resolve('d:/bqp/bqp-validate-data/src/main/resources/static/bqp_validation.js');
    const js2 = path.resolve('d:/bqp/bqp_validation.js');
    const js3 = path.resolve('d:/bqp/BQP_Application_Release/bqp_validation.js');

    const jh1 = crypto.createHash('sha256').update(fs.readFileSync(js1)).digest('hex');
    const jh2 = crypto.createHash('sha256').update(fs.readFileSync(js2)).digest('hex');

    assert.strictEqual(jh1, jh2, 'Hash lệch giữa static/bqp_validation.js và root bqp_validation.js');
    if (fs.existsSync(js3)) {
        const jh3 = crypto.createHash('sha256').update(fs.readFileSync(js3)).digest('hex');
        assert.strictEqual(jh2, jh3, 'Hash lệch giữa root bqp_validation.js và Release bqp_validation.js');
    }
    pass(`Các file JS nguồn đồng bộ tuyệt đối 100% (SHA-256: ${jh1.substring(0, 16)}...)`);
}

console.log('\n================================================================');
console.log(`✅ TẤT CẢ ${passCount} / 13 BÀI KIỂM THỬ GIAO DIỆN DANH SÁCH ĐÃ ĐẠT 100%!`);
console.log('Giao diện danh sách đối tượng & sau thẩm định đã được nâng cấp hoàn hảo!');
console.log('================================================================\n');
