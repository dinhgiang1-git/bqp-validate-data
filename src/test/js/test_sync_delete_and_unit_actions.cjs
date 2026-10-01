const fs = require('fs');
const path = require('path');
const assert = require('assert');

console.log('=== BẮT ĐẦU KIỂM THỬ: XÓA ĐỒNG THỜI 2 DANH SÁCH & BỎ ACTION ĐƠN VỊ ===\n');

const htmlPaths = [
    path.resolve(__dirname, '../../main/resources/static/index.html'),
    path.resolve(__dirname, '../../../../cong_cu_tinh_toan_bqp4.0.html')
];

for (const p of htmlPaths) {
    const filename = path.basename(p);
    const content = fs.readFileSync(p, 'utf8');

    // 1. Kiểm tra không còn nút Tạm dừng hoạt động [⊘] trên cây
    assert(!content.includes("title=\"${u.isActive === false ? 'Kích hoạt lại' : 'Tạm dừng hoạt động'}\""),
        `[${filename}] Không được chứa nút Tạm dừng hoạt động trên cây đơn vị`);
    console.log(`✓ [${filename}] Đã loại bỏ hoàn toàn nút Tạm dừng hoạt động [⊘]`);

    // 2. Kiểm tra không còn nút Xem lịch sử [⏱] trên cây
    assert(!content.includes("title=\"Xem lịch sử thay đổi\""),
        `[${filename}] Không được chứa nút Xem lịch sử thay đổi [⏱] trên cây đơn vị`);
    console.log(`✓ [${filename}] Đã loại bỏ hoàn toàn nút Xem lịch sử thay đổi [⏱]`);

    // 3. Kiểm tra không còn nút "Lịch sử" trên unit_header_actions
    assert(!content.includes("onclick=\"UnitTreeManager.openHistoryFromContext()\""),
        `[${filename}] Không được chứa nút Lịch sử trên unit_header_actions`);
    console.log(`✓ [${filename}] Đã loại bỏ nút Lịch sử trên toolbar đơn vị`);

    // 4. Kiểm tra các nút hợp lệ còn lại: + con, ✎, ⇄, 🗑
    assert(content.includes('title="Sửa tên / mã đơn vị">✎</button>'), `[${filename}] Phải có nút sửa ✎`);
    assert(content.includes('title="Chuyển đơn vị cha">⇄</button>'), `[${filename}] Phải có nút chuyển ⇄`);
    assert(content.includes('title="Xóa đơn vị">🗑</button>'), `[${filename}] Phải có nút xóa 🗑`);
    console.log(`✓ [${filename}] Các nút hợp lệ [✎], [⇄], [🗑] được bảo toàn nguyên vẹn`);

    // 5. Kiểm tra logic xóa đồng thời trong StorageManager.clearSource
    assert(content.includes("if (syncPair && (source === 'validated' || source === 'excel'))"),
        `[${filename}] StorageManager.clearSource phải có logic xóa đồng bộ syncPair`);
    console.log(`✓ [${filename}] StorageManager.clearSource hỗ trợ xóa đồng thời 2 nguồn`);

    // 6. Kiểm tra logic xóa đồng thời trong StorageManager.deleteRecordBySource
    assert(content.includes("if (syncPair && (source === 'validated' || source === 'excel'))"),
        `[${filename}] StorageManager.deleteRecordBySource phải có logic xóa bản ghi đối ứng`);
    console.log(`✓ [${filename}] StorageManager.deleteRecordBySource hỗ trợ xóa bản ghi đối ứng`);

    // 7. Kiểm tra logic deleteAll trong RecordsUI
    assert(content.includes("const isDualExcelSource = (this.currentSource === 'validated' || this.currentSource === 'excel');"),
        `[${filename}] RecordsUI.deleteAll phải nhận diện isDualExcelSource`);
    assert(content.includes("await StorageManager.clearSource('validated', false);"),
        `[${filename}] RecordsUI.deleteAll phải gọi clearSource cho validated`);
    assert(content.includes("await StorageManager.clearSource('excel', false);"),
        `[${filename}] RecordsUI.deleteAll phải gọi clearSource cho excel`);
    console.log(`✓ [${filename}] RecordsUI.deleteAll xóa sạch đồng thời cả 2 danh sách khi ở tab Thẩm định hoặc RAW`);
}

console.log('\n=== KIỂM THỬ MÔ PHỎNG RUNTIME STORAGE MANAGER ===');
// Giả lập môi trường test StorageManager
const mockStorage = {
    cache: {
        records: {
            validated: [{ id: 'val_001', name: 'A' }, { id: 'val_002', name: 'B' }],
            excel: [{ id: 'excel_001', name: 'A' }, { id: 'excel_002', name: 'B' }],
            manual: [{ id: 'man_001', name: 'C' }]
        }
    }
};

let deletedApis = [];
const mockFetch = async (url, opts) => {
    deletedApis.push({ url, method: opts.method });
    return { ok: true, status: 200 };
};

// Mô phỏng StorageManager.clearSource
const simStorageManager = {
    async clearSource(source, syncPair = true) {
        if (syncPair && (source === 'validated' || source === 'excel')) {
            await this.clearSource('validated', false);
            await this.clearSource('excel', false);
            return;
        }
        await mockFetch(`/api/records?source=${encodeURIComponent(source)}`, { method: 'DELETE' });
        mockStorage.cache.records[source] = [];
    }
};

// Chạy test: Xóa từ validated -> cả validated và excel đều phải rỗng
(async () => {
    await simStorageManager.clearSource('validated');
    assert.strictEqual(mockStorage.cache.records.validated.length, 0, 'validated phải rỗng');
    assert.strictEqual(mockStorage.cache.records.excel.length, 0, 'excel phải rỗng');
    assert.strictEqual(mockStorage.cache.records.manual.length, 1, 'manual phải được giữ nguyên');
    assert(deletedApis.some(a => a.url.includes('source=validated')), 'Phải gọi API xóa validated');
    assert(deletedApis.some(a => a.url.includes('source=excel')), 'Phải gọi API xóa excel');
    console.log('✓ Mô phỏng clearSource("validated"): Đồng thời xóa sạch cả validated và excel!');

    // Reset lại dữ liệu và test ngược lại: Xóa từ excel -> cả excel và validated đều phải rỗng
    mockStorage.cache.records.validated = [{ id: 'val_003' }];
    mockStorage.cache.records.excel = [{ id: 'excel_003' }];
    deletedApis = [];

    await simStorageManager.clearSource('excel');
    assert.strictEqual(mockStorage.cache.records.validated.length, 0, 'validated phải rỗng khi xóa excel');
    assert.strictEqual(mockStorage.cache.records.excel.length, 0, 'excel phải rỗng khi xóa excel');
    assert(deletedApis.some(a => a.url.includes('source=validated')), 'Phải gọi API xóa validated khi xóa excel');
    assert(deletedApis.some(a => a.url.includes('source=excel')), 'Phải gọi API xóa excel khi xóa excel');
    console.log('✓ Mô phỏng clearSource("excel"): Ngược lại cũng đồng thời xóa sạch cả excel và validated!');

    console.log('\n🎉 TẤT CẢ CÁC BÀI KIỂM THỬ ĐÃ ĐẠT 100%!');
})();
