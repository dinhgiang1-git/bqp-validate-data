/**
 * test_export_integrity.cjs
 * Kiểm thử tính toàn vẹn, trần bộ nhớ RAM và Snapshot Session của ExportManager
 * TRÍCH XUẤT TRỰC TIẾP TỪ file index.html thật để đảm bảo 100% độ chính xác với production.
 * Đáp ứng yêu cầu rà soát độc lập lần 8 (P1.1, P2.2, P2.4).
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

// 1. Đọc file index.html thật từ thư mục resources
const htmlPath = path.resolve(__dirname, '../../main/resources/static/index.html');
assert(fs.existsSync(htmlPath), `Không tìm thấy file index.html tại: ${htmlPath}`);
const htmlContent = fs.readFileSync(htmlPath, 'utf8');

// 2. Trích xuất block mã nguồn của ExportManager từ index.html
const startMarker = 'const ExportManager = {';
const endMarker = 'function saveRecordDirect(prefix)';
const startIdx = htmlContent.indexOf(startMarker);
const endIdx = htmlContent.indexOf(endMarker);
assert(startIdx !== -1, 'Không tìm thấy startMarker "const ExportManager = {" trong index.html');
assert(endIdx !== -1, 'Không tìm thấy endMarker "function saveRecordDirect(prefix)" trong index.html');
const exportManagerCode = htmlContent.substring(startIdx, endIdx).trim();

/**
 * Hàm khởi tạo môi trường sandbox sạch cho từng ca kiểm thử
 */
function createExportManagerInstance(customContext = {}) {
    const sandbox = {
        console: console,
        URLSearchParams: URLSearchParams,
        Set: Set,
        Map: Map,
        JSON: JSON,
        Math: Math,
        String: String,
        Number: Number,
        Boolean: Boolean,
        Error: Error,
        BqpStorageAdapter: { isSqlite: true },
        StorageManager: {
            records: [
                { id: 'offline_1', fullName: 'Hồ sơ LocalStorage Offline 1' }
            ],
            getRecordsBySource(src) {
                return [...this.records];
            }
        },
        BQPLoader: {
            show(title, msg, status) {},
            hide() {}
        },
        confirm: (msg) => true,
        alert: (msg) => {},
        notifyError: (msg) => { if (typeof sandbox.alert === 'function') sandbox.alert(msg); },
        ConfirmModal: {
            confirm: async (opts) => (typeof sandbox.confirm === 'function') ? sandbox.confirm(opts && opts.message) : true
        },
        fetch: async () => { throw new Error('fetch chưa được mock'); },
        ...customContext
    };

    const context = vm.createContext(sandbox);
    vm.runInContext(exportManagerCode + '\nglobalThis.ExportManager = ExportManager;', context);
    return {
        exportManager: sandbox.ExportManager,
        sandbox: sandbox
    };
}

async function runTests() {
    console.log('================================================================');
    console.log('BẮT ĐẦU KIỂM THỬ EXPORT INTEGRITY TỪ index.html (RÀ SOÁT LẦN 8)');
    console.log('================================================================\n');

    let passedTests = 0;

    // -----------------------------------------------------------------
    // TEST 1: Dữ liệu rỗng trả về mảng rỗng an toàn
    // -----------------------------------------------------------------
    {
        let sessionClosed = false;
        const { exportManager } = createExportManagerInstance({
            fetch: async (url, opts) => {
                if (url === '/api/records/export/session') {
                    return {
                        ok: true,
                        json: async () => ({
                            sessionId: 'sess_empty',
                            totalElements: 0,
                            totalPages: 0,
                            pageSize: 1000
                        })
                    };
                }
                if (url.startsWith('/api/records/export/session/sess_empty')) {
                    sessionClosed = true;
                    return { ok: true, json: async () => ({}) };
                }
                throw new Error('Unexpected URL: ' + url);
            }
        });

        const res = await exportManager.fetchAllMatchingRecords('validated');
        assert.strictEqual(res.length, 0, 'Dữ liệu rỗng phải trả về mảng có length = 0');
        assert.strictEqual(exportManager.pendingExportRecords.length, 0, 'pendingExportRecords phải rỗng');
        assert.strictEqual(sessionClosed, true, 'Phiên session phải được dọn dẹp qua DELETE');
        console.log('✅ TEST 1: Dữ liệu rỗng xử lý hoàn hảo & giải phóng session.');
        passedTests++;
    }

    // -----------------------------------------------------------------
    // TEST 2: Lỗi HTTP 500 khi đang tải trang -> fail-fast, xóa pending, gọi DELETE session
    // -----------------------------------------------------------------
    {
        let sessionDeleted = false;
        const { exportManager } = createExportManagerInstance({
            fetch: async (url, opts) => {
                if (url === '/api/records/export/session') {
                    return {
                        ok: true,
                        json: async () => ({
                            sessionId: 'sess_err',
                            totalElements: 2000,
                            totalPages: 2,
                            pageSize: 1000
                        })
                    };
                }
                if (url.includes('/page?page=0')) {
                    return {
                        ok: true,
                        json: async () => ({
                            items: Array.from({ length: 1000 }, (_, i) => ({ id: 'r_' + i }))
                        })
                    };
                }
                if (url.includes('/page?page=1')) {
                    return {
                        ok: false,
                        status: 500
                    };
                }
                if (opts && opts.method === 'DELETE') {
                    sessionDeleted = true;
                    return { ok: true };
                }
                throw new Error('Unexpected URL: ' + url);
            }
        });

        let thrown = null;
        try {
            await exportManager.fetchAllMatchingRecords('validated');
        } catch (e) {
            thrown = e;
        }

        assert(thrown, 'Phải ném lỗi khi HTTP 500 xảy ra');
        assert(thrown.message.includes('Lỗi máy chủ'), 'Nội dung lỗi phải chỉ rõ lỗi máy chủ: ' + thrown.message);
        assert.strictEqual(exportManager.pendingExportRecords.length, 0, 'pendingExportRecords phải được dọn dẹp sạch');
        assert.strictEqual(sessionDeleted, true, 'Phiên session phải được xóa ngay cả khi có lỗi');
        console.log('✅ TEST 2: Cơ chế fail-fast khi gặp lỗi HTTP 500 hoạt động chuẩn xác.');
        passedTests++;
    }

    // -----------------------------------------------------------------
    // TEST 3: Số lượng tải về không khớp totalElements (thiếu bản ghi)
    // -----------------------------------------------------------------
    {
        const { exportManager } = createExportManagerInstance({
            fetch: async (url, opts) => {
                if (url === '/api/records/export/session') {
                    return {
                        ok: true,
                        json: async () => ({
                            sessionId: 'sess_mismatch',
                            totalElements: 100,
                            totalPages: 1,
                            pageSize: 1000
                        })
                    };
                }
                if (url.includes('/page')) {
                    return {
                        ok: true,
                        json: async () => ({
                            items: Array.from({ length: 95 }, (_, i) => ({ id: 'r_' + i })) // Thiếu 5 bản ghi
                        })
                    };
                }
                if (opts && opts.method === 'DELETE') return { ok: true };
                throw new Error('Unexpected URL: ' + url);
            }
        });

        let thrown = null;
        try {
            await exportManager.fetchAllMatchingRecords('validated');
        } catch (e) {
            thrown = e;
        }

        assert(thrown, 'Phải ném lỗi nếu thiếu bản ghi so với totalElements');
        assert(thrown.message.includes('không toàn vẹn'), 'Lỗi phải nêu rõ dữ liệu xuất không toàn vẹn');
        assert.strictEqual(exportManager.pendingExportRecords.length, 0);
        console.log('✅ TEST 3: Phát hiện thiếu hụt số lượng bản ghi và chặn xuất file thành công.');
        passedTests++;
    }

    // -----------------------------------------------------------------
    // TEST 4: Phát hiện ID trùng lặp giữa các trang (seenIds)
    // -----------------------------------------------------------------
    {
        const { exportManager } = createExportManagerInstance({
            fetch: async (url, opts) => {
                if (url === '/api/records/export/session') {
                    return {
                        ok: true,
                        json: async () => ({
                            sessionId: 'sess_dup',
                            totalElements: 20,
                            totalPages: 2,
                            pageSize: 10
                        })
                    };
                }
                if (url.includes('/page?page=0')) {
                    return {
                        ok: true,
                        json: async () => ({
                            items: Array.from({ length: 10 }, (_, i) => ({ id: 'rec_' + i }))
                        })
                    };
                }
                if (url.includes('/page?page=1')) {
                    return {
                        ok: true,
                        json: async () => ({
                            // Cố tình trùng ID rec_5 từ trang trước
                            items: [
                                { id: 'rec_5' },
                                ...Array.from({ length: 9 }, (_, i) => ({ id: 'rec_page2_' + i }))
                            ]
                        })
                    };
                }
                if (opts && opts.method === 'DELETE') return { ok: true };
                throw new Error('Unexpected URL: ' + url);
            }
        });

        let thrown = null;
        try {
            await exportManager.fetchAllMatchingRecords('validated');
        } catch (e) {
            thrown = e;
        }

        assert(thrown, 'Phải ném lỗi khi có ID trùng lặp');
        assert(thrown.message.includes('trùng lặp'), 'Thông báo phải chỉ rõ trùng lặp ID: ' + thrown.message);
        assert.strictEqual(exportManager.pendingExportRecords.length, 0);
        console.log('✅ TEST 4: Phát hiện ID trùng lặp bằng seenIds Set thành công.');
        passedTests++;
    }

    // -----------------------------------------------------------------
    // TEST 5: Vượt trần cứng bộ nhớ RAM (> 50.000 hồ sơ)
    // -----------------------------------------------------------------
    {
        let alertMessage = null;
        const { exportManager } = createExportManagerInstance({
            alert: (msg) => { alertMessage = msg; },
            fetch: async (url, opts) => {
                if (url === '/api/records/export/session') {
                    return {
                        ok: true,
                        json: async () => ({
                            sessionId: 'sess_huge',
                            totalElements: 50001,
                            totalPages: 51,
                            pageSize: 1000
                        })
                    };
                }
                if (opts && opts.method === 'DELETE') return { ok: true };
                throw new Error('Không được gọi thêm endpoint nào khi đã vượt quá 50.000');
            }
        });

        let thrown = null;
        try {
            await exportManager.fetchAllMatchingRecords('validated');
        } catch (e) {
            thrown = e;
        }

        assert(thrown, 'Phải ném ngoại lệ khi hồ sơ > 50.000');
        assert(thrown.message.includes('vượt quá giới hạn an toàn'), 'Phải thông báo vượt giới hạn: ' + thrown.message);
        assert(alertMessage && alertMessage.includes('50.000'), 'Phải hiển thị cảnh báo alert cho người dùng');
        assert.strictEqual(exportManager.pendingExportRecords.length, 0);
        console.log('✅ TEST 5: Chặn xuất và cảnh báo khi vượt trần cứng 50.000 hồ sơ.');
        passedTests++;
    }

    // -----------------------------------------------------------------
    // TEST 6: Cảnh báo RAM khi hồ sơ từ 20.000 đến 50.000
    // -----------------------------------------------------------------
    {
        // 6a: Người dùng bấm CANCEL
        {
            const { exportManager } = createExportManagerInstance({
                confirm: (msg) => false, // Từ chối
                fetch: async (url, opts) => {
                    if (url === '/api/records/export/session') {
                        return {
                            ok: true,
                            json: async () => ({
                                sessionId: 'sess_warn_cancel',
                                totalElements: 25000,
                                totalPages: 25,
                                pageSize: 1000
                            })
                        };
                    }
                    if (opts && opts.method === 'DELETE') return { ok: true };
                    throw new Error('Không được tiếp tục khi confirm bị từ chối');
                }
            });

            let thrown = null;
            try {
                await exportManager.fetchAllMatchingRecords('validated');
            } catch (e) {
                thrown = e;
            }

            assert(thrown, 'Phải hủy khi người dùng chọn Cancel');
            assert(thrown.message.includes('Đã hủy xuất file'), 'Thông báo hủy đúng');
            assert.strictEqual(exportManager.pendingExportRecords.length, 0);
        }

        // 6b: Người dùng bấm TIẾP TỤC
        {
            let pagesRequested = 0;
            const { exportManager } = createExportManagerInstance({
                confirm: (msg) => true, // Đồng ý
                fetch: async (url, opts) => {
                    if (url === '/api/records/export/session') {
                        return {
                            ok: true,
                            json: async () => ({
                                sessionId: 'sess_warn_ok',
                                totalElements: 21000,
                                totalPages: 21,
                                pageSize: 1000
                            })
                        };
                    }
                    if (url.includes('/page?page=')) {
                        const pageNum = parseInt(url.match(/page=(\d+)/)[1]);
                        pagesRequested++;
                        return {
                            ok: true,
                            json: async () => ({
                                items: Array.from({ length: 1000 }, (_, i) => ({
                                    id: `r_p${pageNum}_${i}`,
                                    fullName: `Quân nhân ${pageNum}_${i}`
                                }))
                            })
                        };
                    }
                    if (opts && opts.method === 'DELETE') return { ok: true };
                    throw new Error('Unexpected URL: ' + url);
                }
            });

            const res = await exportManager.fetchAllMatchingRecords('validated');
            assert.strictEqual(res.length, 21000, 'Tải đủ 21.000 hồ sơ');
            assert.strictEqual(pagesRequested, 21, 'Đã gửi đúng 21 requests');
        }
        console.log('✅ TEST 6: Cơ chế cảnh báo RAM (20.000 - 50.000 hồ sơ) hoạt động chính xác.');
        passedTests++;
    }

    // -----------------------------------------------------------------
    // TEST 7: Snapshot Session phân trang thành công & đối chiếu trường
    // -----------------------------------------------------------------
    {
        let deleteCalled = false;
        const { exportManager } = createExportManagerInstance({
            fetch: async (url, opts) => {
                if (url === '/api/records/export/session') {
                    assert.strictEqual(opts.method, 'POST', 'Phải dùng HTTP POST');
                    assert.strictEqual(opts.headers['Content-Type'], 'application/json', 'Content-Type phải là application/json');
                    const parsedBody = JSON.parse(opts.body);
                    assert.strictEqual(parsedBody.source, 'validated', 'Trường source trong JSON body phải là validated');
                    return {
                        ok: true,
                        json: async () => ({
                            sessionId: 'sess_success_123',
                            totalElements: 3,
                            totalPages: 1,
                            pageSize: 1000
                        })
                    };
                }
                if (url.includes('/session/sess_success_123/page')) {
                    return {
                        ok: true,
                        json: async () => ({
                            items: [
                                {
                                    id: 'rec_s1',
                                    fullName: 'Nguyễn Văn A',
                                    unitName: 'Sư đoàn 312',
                                    sheetType: 'I.1',
                                    monthlySalary: 12000000,
                                    actualTotal: 50000000,
                                    calculatedTotal: 50000000,
                                    difference: 0,
                                    hasErrors: 0,
                                    inputJson: '{"rawName":"A"}',
                                    resultJson: '{"ok":true}'
                                },
                                {
                                    id: 'rec_s2',
                                    fullName: 'Trần Văn B',
                                    unitName: 'Sư đoàn 312',
                                    sheetType: 'I.2',
                                    monthlySalary: 15000000,
                                    actualTotal: 60000000,
                                    calculatedTotal: 62000000,
                                    difference: 2000000,
                                    hasErrors: 1,
                                    errorDetails: [{ columnNumber: 5, message: 'Lỗi năm' }]
                                },
                                {
                                    id: 'rec_s3',
                                    fullName: 'Lê Văn C',
                                    unitName: 'Lữ đoàn 139',
                                    sheetType: 'I.3',
                                    monthlySalary: 9000000,
                                    actualTotal: 30000000,
                                    calculatedTotal: 30000000,
                                    difference: 0,
                                    hasErrors: 0
                                }
                            ]
                        })
                    };
                }
                if (opts && opts.method === 'DELETE' && url === '/api/records/export/session/sess_success_123') {
                    deleteCalled = true;
                    return { ok: true };
                }
                throw new Error('Unexpected URL: ' + url);
            }
        });

        const res = await exportManager.fetchAllMatchingRecords('validated');
        assert.strictEqual(res.length, 3);
        assert.strictEqual(res[0].id, 'rec_s1');
        assert.strictEqual(res[0].hoTen, 'Nguyễn Văn A');
        assert.strictEqual(res[0].donVi, 'Sư đoàn 312');
        assert.strictEqual(res[0].luongThang, 12000000);
        assert.strictEqual(res[0].input.rawName, 'A');
        assert.strictEqual(res[1].diff, 2000000);
        assert.strictEqual(res[1].hasErrors, true);
        assert.strictEqual(res[1].errorDetails.length, 1);
        assert.strictEqual(deleteCalled, true, 'DELETE session phải được gọi sau khi hoàn thành');
        console.log('✅ TEST 7: Snapshot session tải đầy đủ, ánh xạ trường chuẩn xác và thu hồi session.');
        passedTests++;
    }

    // -----------------------------------------------------------------
    // TEST 8: Cơ chế Fail-Fast khi Snapshot Session lỗi (Không fallback ngầm)
    // -----------------------------------------------------------------
    {
        let fallbackRequests = 0;
        const { exportManager } = createExportManagerInstance({
            fetch: async (url, opts) => {
                if (url === '/api/records/export/session') {
                    // Endpoint gặp sự cố (ví dụ HTTP 503 do vượt trần 20 phiên, hoặc HTTP 400 do tham số lỗi)
                    return {
                        ok: false,
                        status: 503,
                        json: async () => ({ message: 'Hệ thống đang phục vụ số lượng phiên xuất tối đa (20 phiên)' })
                    };
                }
                if (url.startsWith('/api/records?')) {
                    fallbackRequests++;
                    return {
                        ok: true,
                        json: async () => ({
                            totalElements: 2,
                            totalPages: 1,
                            items: [
                                { id: 'fb_1', fullName: 'Hồ sơ Fallback 1' },
                                { id: 'fb_2', fullName: 'Hồ sơ Fallback 2' }
                            ]
                        })
                    };
                }
                throw new Error('Unexpected URL: ' + url);
            }
        });

        let thrownError = null;
        try {
            await exportManager.fetchAllMatchingRecords('validated');
        } catch (e) {
            thrownError = e;
        }

        assert(thrownError !== null, 'Phải ném lỗi khi khởi tạo phiên xuất thất bại');
        assert(thrownError.message.includes('HTTP 503'), 'Thông báo lỗi phải chứa mã HTTP 503');
        assert(thrownError.message.includes('Hệ thống đang phục vụ số lượng phiên xuất tối đa'), 'Thông báo lỗi phải chứa thông điệp từ server');
        assert.strictEqual(fallbackRequests, 0, 'TUYỆT ĐỐI KHÔNG fallback sang phân trang thường khi session thất bại');
        assert.strictEqual(exportManager.pendingExportRecords.length, 0, 'pendingExportRecords phải được dọn sạch');
        console.log('✅ TEST 8: Cơ chế fail-fast khi session lỗi (ngăn chặn hoàn toàn fallback ngầm).');
        passedTests++;
    }

    // -----------------------------------------------------------------
    // TEST 9: Chế độ Offline (Không dùng SQLite)
    // -----------------------------------------------------------------
    {
        const { exportManager } = createExportManagerInstance({
            BqpStorageAdapter: { isSqlite: false }
        });

        const res = await exportManager.fetchAllMatchingRecords('manual');
        assert.strictEqual(res.length, 1);
        assert.strictEqual(res[0].id, 'offline_1');
        console.log('✅ TEST 9: Chế độ Offline tương thích ngược hoàn toàn.');
        passedTests++;
    }

    console.log('\n================================================================');
    console.log(`HOÀN TẤT: ĐÃ VƯỢT QUA ${passedTests} / 9 BÀI KIỂM THỬ TÍNH TOÀN VẸN EXPORT`);
    console.log('Code kiểm thử được trích xuất 100% từ file index.html thật!');
    console.log('================================================================');
}

runTests().catch(err => {
    console.error('❌ KIỂM THỬ THẤT BẠI:', err);
    process.exit(1);
});
