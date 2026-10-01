const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ExcelJS = require('exceljs');
const { templatePath } = require('./bqp_export_test_helpers.cjs');

const staticDir = path.resolve(__dirname, '../../main/resources/static');

function createContext() {
    let capturedBuffer = null;

    const c = {
        console,
        Date,
        setTimeout,
        clearTimeout,
        ExcelJS,
        Buffer,
        fetch: async url => {
            assert(decodeURIComponent(String(url)).endsWith('PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx'), `Expected BQP report template request: ${url}`);
            return { ok: true, arrayBuffer: async () => fs.readFileSync(templatePath) };
        },
        Blob: class {
            constructor(parts) {
                if (parts && parts[0]) {
                    capturedBuffer = parts[0];
                }
            }
        },
        URL: {
            createObjectURL() { return 'blob://mock-url'; },
            revokeObjectURL() {}
        },
        document: {
            addEventListener() {},
            getElementById(id) {
                return {
                    id,
                    value: '',
                    textContent: '',
                    style: {},
                    classList: { add() {}, remove() {} }
                };
            },
            querySelector() { return null; },
            querySelectorAll() { return []; },
            createElement() {
                return {
                    click() {},
                    setAttribute() {},
                    style: {},
                    download: '',
                    href: ''
                };
            }
        },
        window: null,
        alert(msg) { console.log('ALERT:', msg); },
        getCapturedBuffer: () => capturedBuffer
    };
    c.window = c;
    vm.createContext(c);
    vm.runInContext(fs.readFileSync(path.join(staticDir, 'bqp_normalization.js'), 'utf8'), c);
    vm.runInContext(fs.readFileSync(path.join(staticDir, 'bqp_validation.js'), 'utf8'), c);
    c.ValidationUI.renderTable = function() {
        this.currentFiltered = this.currentRecords;
    };
    return c;
}

async function testExport(filePath) {
    console.log(`\n======================================================`);
    console.log(`Testing export for: ${path.basename(filePath)}`);
    console.log(`======================================================`);
    const ctx = createContext();

    const bytes = fs.readFileSync(filePath);

    // Run upload via ValidationUI
    await ctx.ValidationUI.handleFileUpload({
        target: {
            files: [{
                name: path.basename(filePath),
                arrayBuffer: async () => bytes
            }]
        }
    });

    const records = ctx.ValidationUI.currentRecords;
    console.log(`Loaded ${records.length} records from ${path.basename(filePath)}`);

    const discrepancyRecords = records.filter(r => r.hasErrors || Math.abs(r.diff || 0) > 1000);
    console.log(`Records with discrepancies/errors: ${discrepancyRecords.length}`);

    // Call exportExcel
    await ctx.ValidationUI.exportExcel();

    const buffer = ctx.getCapturedBuffer();
    assert.ok(buffer, 'Exported buffer should not be null');

    const exportedWb = new ExcelJS.Workbook();
    await exportedWb.xlsx.load(Buffer.from(buffer));

    console.log('Exported sheets:', exportedWb.worksheets.map(s => s.name));

    // Verify detailed sheets (Phụ lục I.1, I.2, I.3)
    let detailedDualCount = 0;
    ['Phụ lục I.1', 'Phụ lục I.2', 'Phụ lục I.3'].forEach(sName => {
        const ws = exportedWb.getWorksheet(sName);
        if (!ws) return;
        ws.eachRow((row, rNum) => {
            if (rNum <= 9) return; // Bỏ qua hàng header/tiêu đề của template, chỉ quét các hàng dữ liệu
            if (/^Ghi chú:/iu.test(ctx.BQPValidation.ExcelParser.getCellText(row.getCell(2)).trim())) return;
            let rowHasDual = false;
            for (let c = 10; c <= ws.columnCount; c++) {
                const cell = row.getCell(c);
                if (cell.value && typeof cell.value === 'object' && Array.isArray(cell.value.richText) && cell.value.richText.length >= 2) {
                    detailedDualCount++;
                    rowHasDual = true;
                    const rt = cell.value.richText;
                    assert.equal(rt.length, 2, `Cell at ${cell.address} should have exactly 2 richText fragments`);
                    assert.ok(rt[0].font.strike, `Top fragment at ${cell.address} must have strike: true`);
                    assert.equal(rt[0].font.color.argb, 'FFDC2626', `Top fragment at ${cell.address} must be red`);
                    assert.ok(!rt[1].font.strike, `Bottom fragment at ${cell.address} must not have strike`);
                    assert.equal(rt[1].font.bold, true, `Bottom fragment at ${cell.address} must be bold`);
                    assert.equal(cell.alignment?.wrapText, true, `Cell at ${cell.address} must have wrapText: true`);
                    assert.equal(cell.alignment?.vertical, 'middle', `Cell at ${cell.address} must have vertical: middle`);
                    if (detailedDualCount <= 10) {
                        console.log(`[PASS Detail] ${sName} Row ${rNum} Col ${c} (${cell.address}): Top="${rt[0].text.trim()}" | Bottom="${rt[1].text.trim()}"`);
                    }
                }
            }
            if (rowHasDual) {
                assert.equal(row.height, 36, `Row ${rNum} with dual value must have height 36`);
            }
        });
    });

    console.log(`Total dual-value cells verified in detailed sheets: ${detailedDualCount}`);

    // Verify summary sheets (Phụ lục I, II, III, IV)
    let summaryDualCount = 0;
    ['Phụ lục I', 'Phụ lục II', 'Phụ lục III', 'Phụ lục IV'].forEach(sName => {
        const ws = exportedWb.getWorksheet(sName);
        if (!ws) return;
        ws.eachRow((row, rNum) => {
            if (rNum <= 8) return; // Bỏ qua hàng header của sheet tổng hợp
            let rowHasDual = false;
            for (let c = 4; c <= 10; c += 2) {
                const cell = row.getCell(c);
                if (cell.value && typeof cell.value === 'object' && Array.isArray(cell.value.richText) && cell.value.richText.length >= 2) {
                    summaryDualCount++;
                    rowHasDual = true;
                    const rt = cell.value.richText;
                    assert.equal(rt.length, 2, `Summary cell at ${cell.address} should have exactly 2 richText fragments`);
                    assert.ok(rt[0].font.strike, `Top fragment at ${cell.address} must have strike: true`);
                    assert.equal(rt[0].font.color.argb, 'FFDC2626', `Top fragment at ${cell.address} must be red`);
                    assert.ok(!rt[1].font.strike, `Bottom fragment at ${cell.address} must not have strike`);
                    assert.equal(rt[1].font.bold, true, `Bottom fragment at ${cell.address} must be bold`);
                    assert.equal(cell.alignment.wrapText, true, `Cell at ${cell.address} must have wrapText: true`);
                    assert.equal(cell.alignment.vertical, 'middle', `Cell at ${cell.address} must have vertical: middle`);
                    console.log(`[PASS Summary] ${sName} Row ${rNum} Col ${c} (${cell.address}): Top="${rt[0].text.trim()}" | Bottom="${rt[1].text.trim()}" | Height=${row.height}`);
                }
            }
            if (rowHasDual) {
                assert.equal(row.height, 36, `Summary row ${rNum} with dual value must have height 36`);
            }
        });
    });

    console.log(`Total dual-value cells verified in summary sheets: ${summaryDualCount}`);

    if (discrepancyRecords.length > 0) {
        assert.ok(detailedDualCount > 0, 'Should have created dual-value cells in detailed sheets for error records');
        assert.ok(summaryDualCount > 0, 'Should have created dual-value cells in summary sheets for error units');
    }

    const outPath = path.resolve(`D:/bqp/output/verified_${path.basename(filePath)}`);
    await exportedWb.xlsx.writeFile(outPath);
    console.log(`Export file saved and verified: ${outPath}`);
}

async function main() {
    await testExport('D:/bqp/input/8. Phu luc BTL thủ đô Hà Nội.xlsx');
    await testExport('D:/bqp/input/15. Phụ lục QK9.xlsx');
    console.log('\n======================================================');
    console.log('ALL DUAL-VALUE (2 GIÁ TRỊ CÙNG Ô) TESTS PASSED 100%!');
    console.log('======================================================');
}

main().catch(err => {
    console.error('Test error:', err);
    process.exit(1);
});
