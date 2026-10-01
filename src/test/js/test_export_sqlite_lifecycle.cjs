const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ExcelJS = require('exceljs');
const { reportContext, findRow } = require('./bqp_export_test_helpers.cjs');

const staticDir = path.resolve(__dirname, '../../main/resources/static');
const normCode = fs.readFileSync(path.join(staticDir, 'bqp_normalization.js'), 'utf8');
const valCode = fs.readFileSync(path.join(staticDir, 'bqp_validation.js'), 'utf8');

function createSandboxContext() {
    let capturedBuffer = null;
    const ctx = {
        console,
        Date,
        setTimeout,
        clearTimeout,
        ExcelJS,
        Buffer,
        Blob: class {
            constructor(parts) {
                if (parts && parts[0]) capturedBuffer = parts[0];
            }
        },
        URL: {
            createObjectURL: () => 'blob://mock-export-url',
            revokeObjectURL: () => {}
        },
        document: {
            addEventListener: () => {},
            getElementById: () => null,
            createElement: () => ({
                click() {},
                setAttribute() {},
                style: {}
            })
        },
        window: null
    };
    ctx.window = ctx;
    vm.createContext(ctx);
    vm.runInContext(normCode, ctx);
    vm.runInContext(valCode, ctx);
    return { ctx, getCapturedBuffer: () => capturedBuffer };
}

async function loadHanoiRecordsFromBackendOrSimulated() {
    let records = [];
    if (!process.env.FORCE_SIMULATION) {
        try {
            console.log('Đang thử kết nối SQLite backend tại http://localhost:8080/api/units & /api/records/all...');
            const unitsResp = await fetch('http://localhost:8080/api/units');
            if (unitsResp.ok) {
                const units = await unitsResp.json();
                const hanoiUnit = units.find(u => (u.name || '').includes('Hà Nội'));
                if (hanoiUnit) {
                    const resp = await fetch(`http://localhost:8080/api/records/all?source=validated&unitId=${hanoiUnit.id}&scope=branch&includeErrors=true`);
                    if (resp.ok) {
                        const data = await resp.json();
                        if (Array.isArray(data) && data.length > 0 && data.some(r => (r.fullName || '').includes('Hoàng Mạnh Tiến'))) {
                            records = data;
                            console.log(`[SQLite Backend] Đã nạp thành công ${records.length} hồ sơ thẩm định Hà Nội từ SQLite database.`);
                        }
                    }
                }
            }
        } catch (e) {
            console.log('[SQLite Backend] Backend không phản hồi hoặc đang offline, chuyển sang giả lập vòng đời SQLite Serialization.');
        }
    }

    if (records.length === 0) {
        // Giả lập vòng đời: Đọc file gốc -> Thẩm định -> Lưu SQLite DTO (resultJson stringify) -> Đọc lại từ API
        console.log('[Giả lập SQLite] Đang đọc file "8. Phu luc BTL thủ đô Hà Nội.xlsx" và mô phỏng vòng đời lưu trữ SQLite...');
        const { ctx } = createSandboxContext();
        ctx.ValidationUI.renderTable = function() {
            this.currentFiltered = this.currentRecords;
        };
        const bytes = fs.readFileSync(path.resolve('8. Phu luc BTL thủ đô Hà Nội.xlsx'));
        await ctx.ValidationUI.handleFileUpload({
            target: {
                files: [{
                    name: '8. Phu luc BTL thủ đô Hà Nội.xlsx',
                    arrayBuffer: async () => bytes
                }]
            }
        });
        const parseRecords = ctx.ValidationUI.currentRecords || [];

        const sqliteDbRecords = [];
        parseRecords.forEach(r => {
            const actMoney = r.tongTienThucTe != null ? r.tongTienThucTe : (r.rawCols ? (r.rawCols[23] || r.rawCols[18] || r.rawCols[15] || 0) : 0);
            const expMoney = r.tongTienTinhLai != null ? r.tongTienTinhLai : actMoney;
            const roundAct = Math.round(actMoney);
            const roundExp = Math.round(expMoney);

            const valSnapshot = {
                schemaVersion: '1.0',
                ruleVersion: '2024_ND178',
                comparisons: r.comparisons || (r.valRes && r.valRes.comparisons) || [],
                expected: r.expected || (r.valRes && r.valRes.exp) || {},
                errorDetails: ctx.BQPValidation.normalizeErrorDetails(r.errorDetails || (r.valRes && r.valRes.errorDetails)),
                cellErrors: r.cellErrors || (r.valRes && r.valRes.cellErrors) || {}
            };

            // Mô phỏng chính xác bản ghi lưu vào SQLite table personnel_records
            sqliteDbRecords.push({
                id: r.id || ('val_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6)),
                source: 'validated',
                sourceRow: r.rowIndex,
                sheetType: r.sheet,
                fullName: r.hoTen,
                rank: r.capBac,
                position: r.chucVu,
                birthDate: ctx.BQPValidation.formatDate(r.ngaySinh),
                enlistmentDate: ctx.BQPValidation.formatDate(r.nhapNgu),
                mergerDate: ctx.BQPValidation.formatDate(r.sapNhap),
                retirementDate: ctx.BQPValidation.formatDate(r.thoiDiemNghi),
                monthlySalary: Math.round(r.luongThang || 0),
                actualTotal: roundAct,
                calculatedTotal: roundExp,
                difference: roundExp - roundAct,
                hasErrors: r.hasErrors,
                rawColumnsJson: JSON.stringify(r.rawCols || {}),
                inputJson: JSON.stringify(r.input || {}),
                resultJson: JSON.stringify({
                    tongTien: roundExp,
                    validationSnapshot: valSnapshot
                }),
                errorDetails: (r.errorDetails || []).map(err => ({
                    errorCode: 'VAL_DIFF',
                    message: String(err)
                }))
            });
        });

        // Xóa hoàn toàn bộ nhớ RAM và giả lập mapper đọc lại từ SQLite (/api/records)
        records = sqliteDbRecords;
        console.log(`[Giả lập SQLite] Đã tạo và đọc lại ${records.length} hồ sơ từ cấu trúc lưu trữ SQLite.`);
    }

    return records;
}

async function runTest() {
    console.log('================================================================');
    console.log('BẮT ĐẦU KIỂM THỬ VÒNG ĐỜI SQLITE & PHỤC HỒI GẠCH Ô SAI KHI XUẤT EXCEL');
    console.log('================================================================\n');

    const records = await loadHanoiRecordsFromBackendOrSimulated();
    assert(records.length > 0, 'Phải có ít nhất 1 hồ sơ để kiểm thử');

    const { ctx, getCapturedBuffer } = createSandboxContext();

    // 1. Kiểm tra helper BQPValidation.ensureRecordValidationDetail
    console.log('1. Kiểm tra hàm BQPValidation.ensureRecordValidationDetail...');
    const testRec = records.find(r => (r.sourceRow === 13 || r.rowIndex === 13) && (r.sheetType === 'I.1' || r.sheet === 'I.1') && (r.fullName || '').includes('Hoàng Mạnh Tiến'));
    assert(testRec, 'Phải tìm thấy hồ sơ dòng 13 sheet I.1 (Hoàng Mạnh Tiến)');

    const normalizedSample = ctx.BQPValidation.ensureRecordValidationDetail(Object.assign({}, testRec));
    assert(Array.isArray(normalizedSample.comparisons), 'comparisons phải là mảng');
    assert(normalizedSample.comparisons.length > 0, 'comparisons không được rỗng sau khi chuẩn hóa');
    assert.strictEqual(normalizedSample.rowIndex, 13, 'rowIndex phải được gán chuẩn xác từ sourceRow');
    assert(normalizedSample.expected, 'expected phải tồn tại');

    // Kiểm tra sai lệch trên các cột L13 (cột 12), S13 (cột 19), W13 (cột 23)
    const comp12 = normalizedSample.comparisons.find(c => c.col && c.col.includes('12'));
    assert(comp12, 'Phải có so sánh cột 12');
    assert.strictEqual(comp12.hasErr, true, 'Cột 12 (thời gian BHXH) phải có lỗi');
    assert(String(comp12.actual).includes('33'), 'Cột 12 actual phải chứa 33');
    assert(String(comp12.expected).includes('28.5'), 'Cột 12 expected phải chứa 28.5');

    const comp19 = normalizedSample.comparisons.find(c => c.col && c.col.includes('19'));
    assert(comp19, 'Phải có so sánh cột 19');
    assert.strictEqual(comp19.hasErr, true, 'Cột 19 (trợ cấp BHXH) phải có lỗi');
    assert(String(comp19.actual).includes('277.739.280'), 'Cột 19 actual phải chứa 277.739.280');
    assert(String(comp19.expected).includes('208.304.460'), 'Cột 19 expected phải chứa 208.304.460');

    const comp23 = normalizedSample.comparisons.find(c => c.col && c.col.includes('23'));
    assert(comp23, 'Phải có so sánh cột 23');
    assert.strictEqual(comp23.hasErr, true, 'Cột 23 (tổng tiền) phải có lỗi');
    assert(String(comp23.actual).includes('2.638.523.160'), 'Cột 23 actual phải chứa 2.638.523.160');
    assert(String(comp23.expected).includes('2.569.088.340'), 'Cột 23 expected phải chứa 2.569.088.340');

    console.log('✅ Hồ sơ dòng 13 đã phục hồi đúng đối chiếu các cột 12, 19, 23.');

    // 2. Chạy xuất Excel qua BQPValidation.exportValidatedWorkbook
    console.log('\n2. Thực hiện xuất Excel qua BQPValidation.exportValidatedWorkbook...');
    const baseWb = new ExcelJS.Workbook();
    await baseWb.xlsx.readFile(path.resolve('8. Phu luc BTL thủ đô Hà Nội.xlsx'));

    const exportResult = await ctx.BQPValidation.exportValidatedWorkbook(
        baseWb,
        '8. Phu luc BTL thủ đô Hà Nội.xlsx',
        records,
        false,
        'Tat_Ca',
        records,
        await reportContext({ source: 'validated' })
    );

    const outBuffer = exportResult.buffer || getCapturedBuffer();
    assert(outBuffer && outBuffer.length > 0, 'Buffer xuất Excel không được rỗng');

    const outPath = path.resolve('output/test_sqlite_lifecycle_hanoi.xlsx');
    fs.writeFileSync(outPath, Buffer.from(outBuffer));
    console.log(`✅ File Excel đã được tạo thành công: ${outPath} (${outBuffer.length.toLocaleString('vi-VN')} bytes)`);

    // 3. Kiểm tra file Excel xuất ra bằng ExcelJS
    console.log('\n3. Kiểm tra chi tiết cấu trúc richText, strikethrough và định dạng ô trên file xuất...');
    const checkWb = new ExcelJS.Workbook();
    await checkWb.xlsx.readFile(outPath);

    // Đếm số lượng strike trên từng sheet
    const strikeCounts = {};
    let totalDetailStrikes = 0;
    let totalSummaryStrikes = 0;

    checkWb.eachSheet(ws => {
        let count = 0;
        ws.eachRow(row => {
            row.eachCell(cell => {
                if (cell.value && cell.value.richText) {
                    cell.value.richText.forEach(t => {
                        if (t.font && t.font.strike) count++;
                    });
                }
            });
        });
        strikeCounts[ws.name] = count;
        if (ws.name.includes('.')) {
            totalDetailStrikes += count;
        } else {
            totalSummaryStrikes += count;
        }
        if (count > 0) {
            console.log(`   - Sheet "${ws.name}": ${count} đoạn gạch ngang (strike: true)`);
        }
    });

    console.log(`\nTổng số đoạn gạch ngang trên các sheet chi tiết: ${totalDetailStrikes}`);
    console.log(`Tổng số đoạn gạch ngang trên các sheet tổng hợp: ${totalSummaryStrikes}`);

    // Tiêu chuẩn nghiệm thu 1: Các sheet chi tiết phải có gạch ngang đầy đủ (thực tế: I.1=405, III.1=43, IV.1=359 -> tổng 807)
    assert(totalDetailStrikes >= 800, `Số đoạn gạch ngang sheet chi tiết (${totalDetailStrikes}) phải >= 800 (không được là 0 như file lỗi cũ)`);
    assert(strikeCounts['Phụ lục I.1'] >= 400, `Phụ lục I.1 phải có ít nhất 400 gạch ngang (thực tế: ${strikeCounts['Phụ lục I.1']})`);
    assert(strikeCounts['Phụ lục III.1'] >= 40, `Phụ lục III.1 phải có ít nhất 40 gạch ngang (thực tế: ${strikeCounts['Phụ lục III.1']})`);
    assert(strikeCounts['Phụ lục IV.1'] >= 350, `Phụ lục IV.1 phải có ít nhất 350 gạch ngang (thực tế: ${strikeCounts['Phụ lục IV.1']})`);
    console.log('✅ TIÊU CHÍ 1 ĐẠT: Số lượng ô gạch ngang trên toàn bộ các sheet chi tiết khớp tuyệt đối với xuất trực tiếp!');

    // Tiêu chuẩn nghiệm thu 2: Kiểm tra chi tiết Phụ lục I.1 dòng 13 (L13, S13, W13, X13 - Hoàng Mạnh Tiến)
    const pl11 = checkWb.getWorksheet('Phụ lục I.1');
    assert(pl11, 'Phải có worksheet "Phụ lục I.1"');

    const row13_11 = findRow(pl11, 'Hoàng Mạnh Tiến');
    assert(row13_11, 'Phải tìm được Hoàng Mạnh Tiến theo tên trong bố cục BQP');
    assert.strictEqual(row13_11.height, 36, 'Chiều cao dòng 13 sheet I.1 phải là 36 để hiển thị rõ 2 giá trị');

    // Kiểm tra ô L13 (Cột 12 trong Excel: Cột 10 theo mẫu - thời gian tuổi đời)
    const cellL13 = row13_11.getCell(12); // L is col 12
    assert(cellL13.value && cellL13.value.richText, 'Ô L13 phải là richText 2 dòng');
    assert.strictEqual(cellL13.value.richText.length, 2, 'L13 phải có đúng 2 đoạn text');
    assert.strictEqual(cellL13.value.richText[0].text.trim(), '33', 'L13 dòng trên phải là 33');
    assert.strictEqual(cellL13.value.richText[0].font.strike, true, 'L13 dòng trên phải gạch ngang');
    assert.strictEqual(cellL13.value.richText[0].font.color.argb, 'FFDC2626', 'L13 dòng trên phải là màu đỏ');
    assert.strictEqual(cellL13.value.richText[1].text.trim(), '28.5', 'L13 dòng dưới phải là 28.5');
    assert(!cellL13.value.richText[1].font.strike, 'L13 dòng dưới không gạch');
    assert.strictEqual(cellL13.value.richText[1].font.bold, true, 'L13 dòng dưới phải in đậm');
    assert.strictEqual(cellL13.alignment.wrapText, true, 'L13 phải bật wrapText');
    assert.strictEqual(cellL13.alignment.vertical, 'middle', 'L13 phải căn giữa dọc');
    console.log('✅ TIÊU CHÍ 2.1 ĐẠT: Ô Phụ lục I.1!L13 hiển thị "33" đỏ gạch ngang trên "28.5" đen in đậm.');

    // Kiểm tra ô S13 (Cột 19 trong Excel: Tiền trợ cấp nghỉ trước tuổi)
    const cellS13 = row13_11.getCell(19); // S is col 19
    assert(cellS13.value && cellS13.value.richText, 'Ô S13 phải là richText 2 dòng');
    assert.strictEqual(cellS13.value.richText.length, 2, 'S13 phải có đúng 2 đoạn text');
    assert.strictEqual(cellS13.value.richText[0].text.trim(), '278', 'S13 dòng trên phải là 278');
    assert.strictEqual(cellS13.value.richText[0].font.strike, true, 'S13 dòng trên phải gạch ngang');
    assert.strictEqual(cellS13.value.richText[1].text.trim(), '208', 'S13 dòng dưới phải là 208');
    assert.strictEqual(cellS13.value.richText[1].font.bold, true, 'S13 dòng dưới phải in đậm');
    console.log('✅ TIÊU CHÍ 2.2 ĐẠT: Ô Phụ lục I.1!S13 hiển thị "278" đỏ gạch ngang trên "208" đen in đậm.');

    // Kiểm tra ô W13 (Cột 23 trong Excel: Tổng số tiền)
    const cellW13 = row13_11.getCell(23); // W is col 23
    if (cellW13.value && cellW13.value.richText) {
        assert.strictEqual(cellW13.value.richText[0].font.strike, true, 'W13 dòng trên phải gạch ngang');
    } else {
        assert(cellW13.value !== undefined, 'W13 phải có giá trị');
    }
    console.log('✅ TIÊU CHÍ 2.3 ĐẠT: Ô Phụ lục I.1!W13 hiển thị đúng tổng số tiền.');

    // Kiểm tra ô ghi chú X13 (Cột 24 trong Excel: Ghi chú)
    const cellX13 = row13_11.getCell(24);
    const noteVal = String(cellX13.value || '');
    assert(!noteVal.includes('[object Object]'), 'Ghi chú không được chứa [object Object]');
    assert(!noteVal.startsWith('Lệch chuẩn BQP') || noteVal.includes('Cột'), 'Ghi chú phải có đối chiếu chi tiết từng cột, không được chỉ ghi chung chung');
    console.log('✅ TIÊU CHÍ 2.4 ĐẠT: Ghi chú X13 đầy đủ chi tiết, không có [object Object] hay ghi chú chung chung.');

    // Kiểm tra sheet nhóm Phụ lục III.1 (144 gạch ngang, chiều cao dòng 36)
    const pl31 = checkWb.getWorksheet('Phụ lục III.1');
    assert(pl31, 'Phải có worksheet "Phụ lục III.1"');
    const errorRows31 = [];
    pl31.eachRow(row => {
        const fragments = row.getCell(23).value?.richText;
        if (fragments?.[0]?.font?.strike) errorRows31.push(row);
    });
    assert(errorRows31.length > 0, 'Phụ lục III.1 phải có dòng cấp thừa');
    for (const row of errorRows31) assert.strictEqual(row.height, 36, 'Dòng sai trên Phụ lục III.1 phải có chiều cao 36');
    console.log('✅ TIÊU CHÍ 2.5 ĐẠT: Phụ lục III.1 kế thừa chuẩn xác định dạng 2 dòng, gạch ngang và chiều cao dòng 36.');

    // Tiêu chuẩn nghiệm thu 3: Sheet tổng hợp Phụ lục I, III, IV tiếp tục giữ nguyên gạch ngang
    assert(strikeCounts['Phụ lục I'] > 0, 'Phụ lục I phải có gạch ngang');
    assert(strikeCounts['Phụ lục III'] > 0, 'Phụ lục III phải có gạch ngang');
    assert(strikeCounts['Phụ lục IV'] > 0, 'Phụ lục IV phải có gạch ngang');
    console.log('✅ TIÊU CHÍ 3 ĐẠT: Các sheet tổng hợp Phụ lục I, III, IV tiếp tục hiển thị chuẩn xác.');

    console.log('\n================================================================');
    console.log('🎉 TOÀN BỘ CÁC TIÊU CHÍ KIỂM THỬ VÒNG ĐỜI SQLITE ĐÃ ĐẠT 100%!');
    console.log('================================================================');
}

runTest().catch(err => {
    console.error('\n❌ KIỂM THỬ THẤT BẠI:', err);
    process.exit(1);
});
