const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ExcelJS = require('exceljs');

const staticDir = path.resolve(__dirname, '../../main/resources/static');
const html = fs.readFileSync(path.join(staticDir, 'index.html'), 'utf8');
const js = fs.readFileSync(path.join(staticDir, 'bqp_validation.js'), 'utf8');
const templateName = 'PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx';
const templateUrl = '/' + encodeURIComponent(templateName);
const templateBytes = fs.readFileSync(path.join(staticDir, templateName));
const managerCode = html.slice(html.indexOf('const ExportManager = {'), html.indexOf('function saveRecordDirect(prefix)'));
const helperCode = code => code.slice(code.indexOf('// A report template is loaded separately'), code.indexOf('window.CellStatus = BQPValidation.CellStatus;'));

function sandbox(fetchImpl) {
    const errors = [];
    const context = {
        console, ExcelJS, Buffer, setTimeout, clearTimeout, Date,
        fetch: fetchImpl,
        document: {
            addEventListener() {}, getElementById() { return null; },
            createElement() { throw new Error('An export must not open an input workbook picker.'); }
        },
        BqpStorageAdapter: { isSqlite: false },
        StorageManager: { getRecordsBySource() { return []; }, getUnits() { return []; } },
        ToastManager: { error(message) { errors.push(message); } },
        window: null
    };
    context.window = context;
    vm.createContext(context);
    vm.runInContext(fs.readFileSync(path.join(staticDir, 'bqp_normalization.js'), 'utf8'), context);
    vm.runInContext(js, context);
    vm.runInContext(managerCode + '\nglobalThis.ExportManager = ExportManager;', context);
    return { context, errors };
}

const response = bytes => ({ ok: true, status: 200, arrayBuffer: async () => bytes });

async function run() {
    assert.equal(helperCode(html).replace(/\r\n/g, '\n'), helperCode(js).replace(/\r\n/g, '\n'), 'Inline and standalone template loaders must be identical.');
    assert(!html.includes('PHU LUC KEM THEO HUONG DAN CUA CUC TAI CHINH.xlsx'));
    assert.equal((html.match(/download="PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx"/g) || []).length, 2);

    let requests = [];
    const { context } = sandbox(async url => { requests.push(url); return response(templateBytes); });
    const firstPromise = context.BQPReportTemplate.load();
    assert.equal(context.BQPReportTemplate.load(), firstPromise, 'Concurrent exports share the same load promise.');
    const template = await firstPromise;
    assert.equal(await context.BQPReportTemplate.load(), template);
    assert.deepEqual(requests, [templateUrl], 'A valid primary template is fetched exactly once.');
    assert.equal(template.workbook.__bqpReportTemplate, true);
    assert.equal(template.workbook.__bqpReportTemplateSource, templateUrl);

    const upload = new ExcelJS.Workbook();
    upload.addWorksheet('Input layout');
    context.ValidationUI.currentWb = upload;
    context.ValidationUI.currentFile = { name: 'Don_vi_input.xlsx' };
    const records = [{ hoTen: 'Hồ sơ thật', sheet: 'I.1' }];
    context.ValidationUI.currentRecords = records;
    context.ValidationUI.currentFiltered = records;
    context.StorageManager.getRecordsBySource = () => records;
    const calls = [];
    context.BQPValidation.exportValidatedWorkbook = async (...args) => { calls.push(args); return { success: true }; };
    const unitContext = {
        selectedUnitId: 'cap1', summaryLevel: 2, scope: 'branch', source: 'validated',
        filters: { unitId: 'cap1', scope: 'branch', status: 'valid' },
        units: [{ id: 'cap1', name: 'Quân khu I' }]
    };
    await Promise.all([
        context.ExportManager.exportValidatedExcel(records, unitContext),
        context.ValidationUI.exportExcel()
    ]);
    assert.equal(requests.length, 1, 'Both UI export routes reuse the same verified template.');
    assert.equal(calls.length, 2);
    for (const call of calls) {
        assert.equal(call[0], upload, 'Input workbook remains available only as a data source.');
        assert.equal(call[1], 'Don_vi_input.xlsx', 'Output naming preserves input filename.');
        assert.equal(call[6].reportTemplateWorkbook, template.workbook);
        assert.equal(call[6].reportTemplateSource, templateUrl);
        assert.equal(call[6].reportTemplateVerified, true);
    }
    const managerContext = calls.find(call => call[6].selectedUnitId === 'cap1')[6];
    for (const key of Object.keys(unitContext)) assert.equal(managerContext[key], unitContext[key]);
    assert.equal(unitContext.reportTemplateWorkbook, undefined, 'Caller context is not mutated.');
    assert.equal(upload.__bqpReportTemplate, undefined, 'Uploaded input is never marked as a template.');

    context.ValidationUI.currentWb = null;
    context.ValidationUI.currentFile = null;
    await context.ExportManager.exportValidatedExcel(records, unitContext);
    assert.equal(calls[2][0], template.workbook);
    assert.equal(calls[2][1], templateName, 'The actual template filename is used only when there is no input filename.');

    let offline = true;
    const retryRequests = [];
    const failed = sandbox(async url => {
        retryRequests.push(url);
        return offline ? { ok: false, status: 404 } : response(templateBytes);
    });
    let exportCount = 0;
    failed.context.ValidationUI.currentWb = upload;
    failed.context.ValidationUI.currentRecords = records;
    failed.context.BQPValidation.exportValidatedWorkbook = async () => { exportCount++; };
    const failedResult = await failed.context.ValidationUI.exportExcel();
    assert.equal(failedResult.success, false);
    assert.equal(exportCount, 0, 'Template load failures must stop export even when an input workbook exists.');
    assert(failed.errors.some(message => /Không thể tải template BQP tương thích/.test(message)));
    assert.equal(failed.context.BQPReportTemplate._loadPromise, null, 'A failed promise is cleared for retry.');
    offline = false;
    await failed.context.BQPReportTemplate.load();
    assert.equal(retryRequests.length, 5, 'Retry fetches the primary template after all four candidates failed.');

    const badSheet = new ExcelJS.Workbook();
    await badSheet.xlsx.load(templateBytes);
    badSheet.removeWorksheet(badSheet.getWorksheet('Phụ lục IV').id);
    assert.throws(() => context.BQPReportTemplate.validate(badSheet), /thiếu sheet tổng hợp IV/);
    const badColumn = new ExcelJS.Workbook();
    await badColumn.xlsx.load(templateBytes);
    badColumn.getWorksheet('Phụ lục I.1').getCell('W9').value = null;
    assert.throws(() => context.BQPReportTemplate.validate(badColumn), /thiếu cột 23/);
    const invalidBytes = await badColumn.xlsx.writeBuffer();
    const fallbackRequests = [];
    const fallback = sandbox(async url => {
        fallbackRequests.push(url);
        return response(url === templateUrl ? invalidBytes : templateBytes);
    });
    const fallbackTemplate = await fallback.context.BQPReportTemplate.load();
    assert.deepEqual(fallbackRequests, [templateUrl, '/26.9.PHU_LUC_SUA.xlsx']);
    assert.equal(fallbackTemplate.source, '/26.9.PHU_LUC_SUA.xlsx');
    assert.equal(fallbackTemplate.fileName, '26.9.PHU_LUC_SUA.xlsx');
    assert.equal(fallbackTemplate.workbook.__bqpReportTemplate, true, 'Only a compatible fallback may be flagged.');
    console.log('PASS: report template caching, fallback validation, failure retry, download links, and both UI export routes.');
}

run().catch(error => { console.error(error); process.exitCode = 1; });
