'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
    ExcelJS, staticDir, loadReportTemplate, reportContext, inlineEngineCode,
    createExportSandbox, cellText, findRow, totalRow, numeric
} = require('./bqp_export_test_helpers.cjs');

const plain = value => value === undefined ? undefined : JSON.parse(JSON.stringify(value));
// ExcelJS assigns shared-string ids while serializing; those are ZIP bookkeeping, not workbook content.
const snapshot = workbook => JSON.stringify(workbook.model, (key, value) => key === 'ssId' ? undefined : value);
const headerEnd = ws => ws.name === 'Phụ lục I.4' ? 8 : ws.name === 'Phụ lục I.5' ? 7 : /\.\d$/.test(ws.name) ? 9 : 7;

function fixture() {
    const units = [
        { id: 'c1', name: 'Bộ Tư lệnh thử nghiệm', level: 1, parentId: null },
        { id: 'c2a', name: 'Sư đoàn Alpha', level: 2, parentId: 'c1', orderIndex: 1 },
        { id: 'c2b', name: 'Sư đoàn Beta', level: 2, parentId: 'c1', orderIndex: 2 },
        { id: 'c3a', name: 'Tiểu đoàn Alpha', level: 3, parentId: 'c2a' }
    ];
    const records = [];
    for (let type = 1; type <= 3; type++) {
        for (let status = 0; status <= 2; status++) {
            const actual = 100000 * type + 10000 * status;
            const diff = status === 0 ? 0 : status === 1 ? -5000 : 5000;
            const totalCol = [0, 23, 18, 15][type];
            records.push({
                id: `r${type}${status}`, hoTen: `Hồ sơ thật ${type}-${status}`, ngaySinh: '02/1970',
                capBac: 'Đại tá', chucVu: 'Trợ lý', nhapNgu: '03/1988',
                group: status === 2 ? 'Tiểu đoàn Alpha' : 'Sư đoàn Beta',
                donVi: status === 2 ? 'Tiểu đoàn Alpha' : 'Sư đoàn Beta',
                unitId: status === 2 ? 'c3a' : 'c2b', sheet: `I.${type}`, sheetSource: `I.${type}`,
                rowIndex: 12, tongTienThucTe: actual, tongTienTinhLai: actual + diff, diff,
                hasErrors: status !== 0, rawCols: { 9: 9000000, [totalCol]: actual },
                comparisons: status === 0 ? [] : [{ col: `Cột ${totalCol}`, actual: String(actual), expected: String(actual + diff), hasErr: true }]
            });
        }
    }
    return { units, records };
}

function uploadedWorkbook() {
    const workbook = new ExcelJS.Workbook();
    const ws = workbook.addWorksheet('UPLOAD_LAYOUT_SENTINEL');
    ws.getCell('A1').value = 'Workbook input must not become report layout';
    ws.getCell('B12').value = 'Source workbook record';
    ws.getCell('W12').value = 987654321;
    ws.getColumn(1).width = 99;
    return workbook;
}

async function exportAndRead(sandbox, base, records, context, filtered = false, allRecords = records) {
    const result = await sandbox.BQPValidation.exportValidatedWorkbook(base, 'Ho_so_upload.xlsx', records, filtered, 'Da_Loc', allRecords, context);
    const captured = sandbox.capture();
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(result && result.buffer ? result.buffer : captured.buffer);
    return { workbook, filename: (result && result.fileName) || captured.filename };
}

function assertHeadersAndWidths(template, output) {
    for (const source of template.worksheets) {
        const target = output.getWorksheet(source.name);
        assert(target, `Output retains ${source.name}`);
        for (let r = 1; r <= headerEnd(source); r++) {
            assert.equal(target.getRow(r).height, source.getRow(r).height, `${source.name} row ${r} height`);
            for (let c = 1; c <= source.columnCount; c++) {
                const before = source.getRow(r).getCell(c), after = target.getRow(r).getCell(c);
                for (const attribute of ['value', 'font', 'alignment', 'border', 'numFmt']) {
                    assert.deepEqual(plain(after[attribute]), plain(before[attribute]), `${source.name}!${before.address} ${attribute}`);
                }
            }
        }
        for (let c = 1; c <= Math.max(source.columnCount, source.columns.length); c++) {
            const before = source.getColumn(c), after = target.getColumn(c);
            // ExcelJS omits width="0" when writing hidden columns; its reader supplies the default width9.
            if (before.hidden && before.width === 0) assert([0, 9].includes(after.width), `${source.name} hidden column ${c} width`);
            else assert.equal(after.width, before.width, `${source.name} column ${c} width`);
            assert.equal(after.hidden, before.hidden, `${source.name} column ${c} hidden state`);
        }
        const headerMerges = source.model.merges.filter(range => Number(range.match(/\d+/)[0]) <= headerEnd(source));
        for (const range of headerMerges) assert(target.model.merges.includes(range), `${source.name} retains merge ${range}`);
    }
}

function assertNoSamples(workbook) {
    for (const ws of workbook.worksheets) {
        ws.eachRow(row => row.eachCell(cell => {
            assert(!/(?:Nguyễn Văn [AB]|Nguyen Van [AB]|Đơn v[iị]\s*\d+)/iu.test(cellText(cell)), `${ws.name}!${cell.address} still contains sample ${cellText(cell)}`);
            assert(![30500000, 1898625000, 976000000, 457500000, 343125000].includes(cell.value), `${ws.name}!${cell.address} retains example salary/money`);
        }));
    }
}

function assertDecreeBands(template, workbook) {
    for (const name of ['Phụ lục I.4', 'Phụ lục I.5']) {
        const source = template.getWorksheet(name), target = workbook.getWorksheet(name);
        const labels = new Set();
        source.eachRow(row => {
            const text = cellText(row.getCell(2));
            if (/^Nghỉ theo Nghị định số \d+/u.test(text)) labels.add(text);
        });
        for (const label of labels) assert(findRow(target, label), `${name} retains decree section ${label}`);
    }
}

function assertDetailRecords(sandbox, workbook, sheetName, records) {
    const ws = workbook.getWorksheet(sheetName);
    const colMap = {};
    ws.getRow(headerEnd(ws)).eachCell(cell => {
        if (typeof cell.value === 'number') colMap[cell.value] = cell.col;
    });
    const names = [];
    ws.eachRow(row => {
        if (typeof row.getCell(colMap[1] || 1).value === 'number' && row.number > headerEnd(ws)) {
            const name = cellText(row.getCell(colMap[2] || 2)).trim();
            if (name) names.push(name);
        }
    });
    assert.deepEqual(names.slice().sort(), records.map(r => r.hoTen).sort(), `${sheetName} membership`);
    for (const rec of records) {
        const row = findRow(ws, rec.hoTen, colMap[2] || 2);
        const logicalTotal = sheetName.endsWith('.1') ? 23 : sheetName.endsWith('.2') ? 18 : 15;
        assert.equal(numeric(row.getCell(colMap[logicalTotal] || logicalTotal).value), rec.tongTienTinhLai, `${sheetName} ${rec.hoTen} recalculated money`);
    }
    const logicalTotal = sheetName.endsWith('.1') ? 23 : sheetName.endsWith('.2') ? 18 : 15;
    assert.equal(numeric(totalRow(ws).getCell(colMap[logicalTotal] || logicalTotal).value), records.reduce((sum, rec) => sum + rec.tongTienTinhLai, 0), `${sheetName} detail total matches recalculated records`);
}

function assertSummary(workbook, sheetName, records) {
    const ws = workbook.getWorksheet(sheetName);
    const total = totalRow(ws);
    assert(total, `${sheetName} total row`);
    assert.equal(numeric(total.getCell(9).value), records.length, `${sheetName} total people`);
    assert.equal(numeric(total.getCell(10).value), records.reduce((sum, rec) => sum + rec.tongTienTinhLai, 0), `${sheetName} recalculated total`);
    for (let type = 1; type <= 3; type++) {
        const subset = records.filter(rec => rec.sheet === `I.${type}`);
        assert.equal(numeric(total.getCell(type * 2 + 1).value), subset.length, `${sheetName} I.${type} count`);
        assert.equal(numeric(total.getCell(type * 2 + 2).value), subset.reduce((sum, rec) => sum + rec.tongTienTinhLai, 0), `${sheetName} I.${type} money`);
    }
}

async function run() {
    const external = fs.readFileSync(path.join(staticDir, 'bqp_validation.js'), 'utf8').replace(/\r\n/g, '\n').trim();
    assert(inlineEngineCode().replace(/\r\n/g, '\n').trim() === external, 'Standalone and inline engine code must be identical');
    const sandbox = createExportSandbox();
    const { units, records } = fixture();
    const template = await loadReportTemplate();
    const base = uploadedWorkbook();
    const beforeInput = snapshot(base);
    const beforeTemplate = snapshot(template);
    const context = await reportContext({ reportTemplateWorkbook: template });
    const unverified = await loadReportTemplate();
    delete unverified.__bqpReportTemplate;
    for (const invalidContext of [null, {}, { ...context, reportTemplateWorkbook: unverified, reportTemplateVerified: false }, { ...context, reportTemplateWorkbook: base }]) {
        await assert.rejects(() => sandbox.BQPValidation.exportValidatedWorkbook(base, 'Test.xlsx', records, false, 'Tat_Ca', records, invalidContext), /(?:mẫu|template|BQP)/iu);
    }
    const malformed = await loadReportTemplate();
    malformed.removeWorksheet(malformed.getWorksheet('Phụ lục I.2').id);
    await assert.rejects(() => sandbox.BQPValidation.exportValidatedWorkbook(base, 'Test.xlsx', records, false, 'Tat_Ca', records, { ...context, reportTemplateWorkbook: malformed }), /(?:mẫu|template|I\.2)/iu);
    const missingColumn = await loadReportTemplate();
    missingColumn.getWorksheet('Phụ lục I.1').getCell('W9').value = null;
    await assert.rejects(() => sandbox.BQPValidation.exportValidatedWorkbook(base, 'Test.xlsx', records, false, 'Tat_Ca', records, { ...context, reportTemplateWorkbook: missingColumn }), /(?:mẫu|cột|I\.1)/iu);

    const ordinary = await exportAndRead(sandbox, base, records, context);
    assert.equal(ordinary.filename, 'Ho_so_upload_tham_dinh_BQP.xlsx');
    assert(snapshot(base) === beforeInput, 'Source upload workbook is unchanged');
    assert(snapshot(template) === beforeTemplate, 'Report template is unchanged');
    assert.equal(ordinary.workbook.getWorksheet('UPLOAD_LAYOUT_SENTINEL'), undefined, 'Output layout comes from BQP workbook');
    assertHeadersAndWidths(template, ordinary.workbook);
    assertNoSamples(ordinary.workbook);
    assertDecreeBands(template, ordinary.workbook);
    for (const source of template.worksheets) {
        const ws = ordinary.workbook.getWorksheet(source.name);
        const notes = [];
        ws.getRow(headerEnd(ws)).eachCell(cell => { if (cellText(cell) === 'Ghi chú thẩm định BQP') notes.push(cell.col); });
        assert.equal(notes.length, 1, `${ws.name} has one validation-note column`);
        assert.equal(notes[0], Math.max(source.columnCount, source.columns.length) + 1, `${ws.name} notes appear immediately after original column definitions`);
        assert.equal(ws.getColumn(notes[0]).hidden, false, `${ws.name} validation notes are visible`);
    }
    for (const ws of template.worksheets) {
        if (ws.name === 'Phụ lục III') continue; // Original template has no total; exporter inserts one before its note.
        assert.deepEqual(ordinary.workbook.getWorksheet(ws.name).model.merges.slice().sort(), ws.model.merges.slice().sort(), `${ws.name} preserves all merges without overflow`);
    }
    for (let type = 1; type <= 3; type++) {
        const subset = records.filter(r => r.sheet === `I.${type}`);
        assertDetailRecords(sandbox, ordinary.workbook, `Phụ lục I.${type}`, subset);
        assertDetailRecords(sandbox, ordinary.workbook, `Phụ lục II.${type}`, subset.filter(r => r.diff === 0));
        assertDetailRecords(sandbox, ordinary.workbook, `Phụ lục III.${type}`, subset.filter(r => r.diff < 0));
        assertDetailRecords(sandbox, ordinary.workbook, `Phụ lục IV.${type}`, subset.filter(r => r.diff > 0));
    }
    assertSummary(ordinary.workbook, 'Phụ lục I', records);
    assertSummary(ordinary.workbook, 'Phụ lục II', records.filter(r => r.diff === 0));
    assertSummary(ordinary.workbook, 'Phụ lục III', records.filter(r => r.diff < 0));
    assertSummary(ordinary.workbook, 'Phụ lục IV', records.filter(r => r.diff > 0));
    console.log('PASS: all real BQP sheets, headers, styles, merges, sample cleaning, classification and money');

    const selected = records.filter(r => r.diff < 0);
    const filtered = await exportAndRead(sandbox, base, selected, context, true, records);
    assert.equal(filtered.filename, 'Ho_so_upload_Tham_dinh_Da_Loc_3dc.xlsx');
    assertSummary(filtered.workbook, 'Phụ lục I', selected);
    assertSummary(filtered.workbook, 'Phụ lục II', []);
    assertSummary(filtered.workbook, 'Phụ lục IV', []);
    assertNoSamples(filtered.workbook);

    const c1 = await exportAndRead(sandbox, base, records, { ...context, selectedUnitId: 'c1', summaryLevel: 2, scope: 'branch', units });
    assert(c1.filename.includes('_cap1'), 'Level1 filename is distinguished');
    assertSummary(c1.workbook, 'Phụ lục I', records);
    assert(findRow(c1.workbook.getWorksheet('Phụ lục I'), 'Sư đoàn Alpha'), 'C3 records resolve to C2 ancestor');
    assert(findRow(c1.workbook.getWorksheet('Phụ lục I'), 'Sư đoàn Beta'), 'C2 records retain their C2 group');
    assert(!findRow(c1.workbook.getWorksheet('Phụ lục I'), 'Tiểu đoàn Alpha'), 'Level1 summary groups descendants by C2');

    const savedRecords = [{
        id: 'saved-1', source: 'validated', sheetType: 'I.1', fullName: 'Hồ sơ từ cơ sở dữ liệu',
        birthDate: '02/1970', rank: 'Đại tá', position: 'Trợ lý', unitName: 'Đơn vị lưu trữ',
        enlistmentDate: '03/1988', mergerDate: '03/2025', retirementDate: '07/2025', monthlySalary: 9500000,
        actualTotal: 250000, calculatedTotal: 245000, difference: -5000, hasErrors: true,
        rawColumnsJson: JSON.stringify({ 2: 'Hồ sơ từ cơ sở dữ liệu', 23: 250000 }),
        inputJson: JSON.stringify({ hoTen: 'Hồ sơ từ cơ sở dữ liệu' }),
        resultJson: JSON.stringify({ tongTien: 245000, validationSnapshot: { comparisons: [{ col: 'Cột 23', actual: '250000', expected: '245000', hasErr: true }] } })
    }];
    const saved = await exportAndRead(sandbox, base, savedRecords, { ...context, source: 'validated' });
    const savedRow = findRow(saved.workbook.getWorksheet('Phụ lục I.1'), 'Hồ sơ từ cơ sở dữ liệu');
    assert(savedRow, 'Saved DTO is hydrated into BQP detail');
    assert.equal(cellText(savedRow.getCell(6)), '03/1988', 'Saved enlistment date survives missing raw columns');
    assert.equal(cellText(savedRow.getCell(7)), '03/2025', 'Saved merger date survives missing raw columns');
    assert.equal(cellText(savedRow.getCell(8)), '07/2025', 'Saved retirement date survives missing raw columns');
    assert.equal(numeric(savedRow.getCell(9).value), 9500000, 'Saved monthly salary survives missing raw columns');
    assert(findRow(saved.workbook.getWorksheet('Phụ lục III.1'), 'Hồ sơ từ cơ sở dữ liệu'), 'Saved DTO classification survives hydration');
    assert.equal(numeric(totalRow(saved.workbook.getWorksheet('Phụ lục I')).getCell(10).value), 245000);
    console.log('PASS: filtered export, level1 grouping and filenames, saved-record hydration');

    const supplementalRecords = [
        { id: 'appendix4', hoTen: 'Hồ sơ phụ lục tác động', sheet: 'I.4', group: 'Đơn vị thật', hasErrors: false,
            rawCols: { 2: 'Hồ sơ phụ lục tác động', 3: '02/1970', 4: 'Đại tá', 5: 'Cơ quan thật', 6: 'Trợ lý tác động', 9: 120, 10: 115, 15: 'T', 16: 'Nội dung thực' } },
        { id: 'appendix5', hoTen: 'Hồ sơ phụ lục tiền lương', sheet: 'I.5', group: 'Đơn vị thật', donVi: 'Cơ quan lương', chucVu: 'Trợ lý tiền lương', capBac: 'Đại tá', policyCode: 'ND177', categoryCode: 'QNCN',
            tongTienThucTe: 9500000, tongTienTinhLai: 9500000, diff: 0, hasErrors: false,
            comparisons: [{ col: 'Cột 22', actual: '9500000', expected: '9500000', hasErr: false }],
            rawCols: { 2: 'Hồ sơ phụ lục tiền lương', 3: 'Cơ quan lương', 4: 'Trợ lý tiền lương', 5: 'Đại tá', 6: 6.6, 9: '03/1988', 13: 500000, 22: 9500000 } }
    ];
    const supplemental = await exportAndRead(sandbox, base, supplementalRecords, context);
    assertNoSamples(supplemental.workbook);
    assertDecreeBands(template, supplemental.workbook);
    const i4Record = findRow(supplemental.workbook.getWorksheet('Phụ lục I.4'), supplementalRecords[0].hoTen);
    assert(i4Record, 'I.4 passes through personnel-impact records');
    assert.equal(i4Record.getCell(6).value, 'Trợ lý tác động');
    assert.equal(i4Record.getCell(9).value, 120);
    const i5Record = findRow(supplemental.workbook.getWorksheet('Phụ lục I.5'), supplementalRecords[1].hoTen);
    assert(i5Record, 'I.5 receives salary records');
    let precedingBand = '', precedingCategory = '';
    for (let r = 8; r < i5Record.number; r++) {
        const row = supplemental.workbook.getWorksheet('Phụ lục I.5').getRow(r);
        if (/Nghị định/u.test(cellText(row.getCell(2)))) precedingBand = cellText(row.getCell(2));
        if (/^[AB]$/u.test(cellText(row.getCell(1)))) precedingCategory = cellText(row.getCell(1));
    }
    assert(precedingBand.includes('177'), 'Saved policyCode places salary record in NĐ177 band');
    assert.equal(precedingCategory, 'B', 'Saved categoryCode places QNCN in the template B group');
    assert.equal(i5Record.getCell(4).value, 'Trợ lý tiền lương', 'I.5 numerical column header governs position');
    assert.equal(i5Record.getCell(5).value, 'Đại tá', 'I.5 numerical column header governs rank');
    assert.equal(i5Record.getCell(9).value, '03/1988', 'I.5 keeps enlistment date in column9');
    assert.equal(i5Record.getCell(13).value, 500000, 'I.5 keeps reserved salary in column13');
    assert.equal(numeric(i5Record.getCell(22).value), 9500000);
    assert.equal(numeric(totalRow(supplemental.workbook.getWorksheet('Phụ lục I.5')).getCell(22).value), 9500000, 'I.5 salary total matches its populated rows');
    for (let r = 49; r <= 80; r++) {
        for (let c = 1; c <= 24; c++) {
            assert.deepEqual(plain(supplemental.workbook.getWorksheet('Phụ lục I.5').getRow(r).getCell(c).value), plain(template.getWorksheet('Phụ lục I.5').getRow(r).getCell(c).value), `I.5 preserves salary guidance ${r}:${c}`);
        }
    }
    console.log('PASS: I.4 pass-through, I.5 authoritative column mapping, salary guidance preservation');

    const overflowRecords = Array.from({ length: 50 }, (_, i) => ({
        ...records[0], id: `large-${i}`, hoTen: `Hồ sơ bổ sung ${String(i).padStart(2, '0')}`,
        group: `Đơn vị thực ${i % 14}`, donVi: `Đơn vị thực ${i % 14}`, unitId: `real-${i % 14}`
    }));
    const large = await exportAndRead(sandbox, base, overflowRecords, context);
    assertHeadersAndWidths(template, large.workbook);
    assertNoSamples(large.workbook);
    assertDetailRecords(sandbox, large.workbook, 'Phụ lục I.1', overflowRecords);
    assertSummary(large.workbook, 'Phụ lục I', overflowRecords);
    const largeDetail = large.workbook.getWorksheet('Phụ lục I.1');
    const lastRecord = findRow(largeDetail, overflowRecords[49].hoTen);
    assert(lastRecord.number > 30, 'Records exceed original body capacity');
    const sourceBorder = template.getWorksheet('Phụ lục I.1').getCell('W12').border;
    assert.deepEqual(lastRecord.getCell(23).border, sourceBorder, 'New rows retain BQP table borders');
    const note = [];
    largeDetail.eachRow(row => { if (cellText(row.getCell(2)).startsWith('Ghi chú:')) note.push(row.number); });
    assert.equal(note.length, 1, 'Overflow preserves one original note');
    assert(note[0] > lastRecord.number, 'Footer is moved after additional records');
    assert(largeDetail.model.merges.includes(`B${note[0]}:W${note[0]}`), 'Footer merge moves with inserted rows');
    console.log('PASS: inserting records and summary units beyond sample capacity preserves footer and data-row styles');

    const missingChild = await loadReportTemplate();
    missingChild.removeWorksheet(missingChild.getWorksheet('Phụ lục II.2').id);
    const rebuilt = await exportAndRead(sandbox, base, records, { ...context, reportTemplateWorkbook: missingChild });
    const rebuiltChild = rebuilt.workbook.getWorksheet('Phụ lục II.2');
    const childSource = template.getWorksheet('Phụ lục I.2');
    assertDetailRecords(sandbox, rebuilt.workbook, 'Phụ lục II.2', records.filter(record => record.sheet === 'I.2' && record.diff === 0));
    assert.deepEqual(rebuiltChild.model.merges.slice().sort(), childSource.model.merges.slice().sort(), 'Missing child is cloned with complete template merges');
    assert.deepEqual(rebuiltChild.getCell('A4').font, childSource.getCell('A4').font, 'Missing child retains blank header row styles');
    assertNoSamples(rebuilt.workbook);
    console.log('PASS: missing optional child sheet clones complete template layout and receives real records');

    // Compare two fresh exports before inspection materializes empty cells in ExcelJS.
    const standalone = await exportAndRead(createExportSandbox(), uploadedWorkbook(), fixture().records, await reportContext());
    const inline = await exportAndRead(createExportSandbox(true), uploadedWorkbook(), fixture().records, await reportContext());
    for (const ws of standalone.workbook.worksheets) {
        const target = inline.workbook.getWorksheet(ws.name);
        assert.deepEqual(target.model.rows, ws.model.rows, `${ws.name} inline export equals standalone export`);
        assert.deepEqual(target.model.merges, ws.model.merges, `${ws.name} inline merge parity`);
        assert.deepEqual(target.model.cols, ws.model.cols, `${ws.name} inline column parity`);
    }
    assertHeadersAndWidths(template, inline.workbook);
    assertNoSamples(inline.workbook);
    const marked = await loadReportTemplate();
    const direct = await exportAndRead(sandbox, marked, fixture().records, null);
    assertSummary(direct.workbook, 'Phụ lục I', fixture().records);
    console.log('PASS: inline execution matches standalone, explicitly marked BQP workbook supports direct callers');
}

run().catch(error => { console.error(error); process.exitCode = 1; });
