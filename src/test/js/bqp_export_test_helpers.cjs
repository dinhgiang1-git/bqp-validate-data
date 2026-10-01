'use strict';

const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ExcelJS = require('exceljs');

const staticDir = path.resolve(__dirname, '../../main/resources/static');
const templateName = 'PHU LUC KEM THEO HUONG DAN CUA BO QUOC PHONG.xlsx';
const templatePath = path.join(staticDir, templateName);

async function loadReportTemplate() {
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.readFile(templatePath);
    workbook.__bqpReportTemplate = true;
    return workbook;
}

async function reportContext(extra = {}) {
    return {
        reportTemplateWorkbook: await loadReportTemplate(),
        reportTemplateSource: templateName,
        reportTemplateVerified: true,
        ...extra
    };
}

function inlineEngineCode() {
    const html = fs.readFileSync(path.join(staticDir, 'index.html'), 'utf8');
    const start = html.lastIndexOf('<script');
    return html.slice(html.indexOf('>', start) + 1, html.indexOf('</script>', start));
}

function createExportSandbox(inline = false) {
    let buffer = null;
    let filename = null;
    const sandbox = {
        console, Date, setTimeout, clearTimeout, ExcelJS, Buffer,
        Blob: class { constructor(parts) { buffer = parts[0]; } },
        URL: { createObjectURL() { return 'blob://test'; }, revokeObjectURL() {} },
        document: {
            addEventListener() {},
            getElementById(id) { return { id, value: '', style: {}, classList: { add() {}, remove() {}, toggle() {} } }; },
            querySelector() { return null; }, querySelectorAll() { return []; },
            createElement() { return { style: {}, setAttribute() {}, click() { filename = this.download; } }; },
            body: { appendChild() {} }
        },
        BQPLoader: { show() {}, hide() {} },
        StorageManager: { getUnits() { return []; }, getRecordsBySource() { return []; } },
        BqpStorageAdapter: { isSqlite: false },
        RecordsUI: { getActiveFilters() { return {}; } },
        notifySuccess() {}, notifyWarning() {}, notifyInfo() {}, notifyError() {},
        capture() { return { buffer, filename }; },
        window: null
    };
    sandbox.window = sandbox;
    const context = vm.createContext(sandbox);
    vm.runInContext(fs.readFileSync(path.join(staticDir, 'bqp_normalization.js'), 'utf8'), context);
    vm.runInContext(inline ? inlineEngineCode() : fs.readFileSync(path.join(staticDir, 'bqp_validation.js'), 'utf8'), context);
    return sandbox;
}

function cellText(cell) {
    const value = cell && cell.value;
    if (value == null) return '';
    if (value.richText) return value.richText.map(part => part.text).join('');
    if (value.formula) return String(value.result == null ? '' : value.result);
    return String(value);
}

function findRow(worksheet, label, column = 2) {
    for (let n = 1; n <= worksheet.rowCount; n++) {
        if (cellText(worksheet.getRow(n).getCell(column)).trim() === label) return worksheet.getRow(n);
    }
    return null;
}

function totalRow(worksheet) {
    for (let n = 8; n <= worksheet.rowCount; n++) {
        const row = worksheet.getRow(n);
        if ([1, 2].some(c => /^(?:tổng cộng|cộng)$/iu.test(cellText(row.getCell(c)).trim()))) return row;
    }
    return null;
}

function numeric(value) {
    if (value && value.richText) value = value.richText[value.richText.length - 1].text;
    if (value && value.formula) value = value.result;
    return typeof value === 'number' ? value : Number(String(value || '').trim().replace(/[.,\s]/g, ''));
}

module.exports = { ExcelJS, staticDir, templateName, templatePath, loadReportTemplate, reportContext, inlineEngineCode, createExportSandbox, cellText, findRow, totalRow, numeric };
