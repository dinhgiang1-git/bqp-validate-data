const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const staticDir = path.resolve(__dirname, '../../main/resources/static');
const ExcelJS = require(path.join(staticDir, 'exceljs.min.js'));
const normalizer = require(path.join(staticDir, 'bqp_normalization.js'));
const XLSX = require(path.join(staticDir, 'xlsx.full.min.js'));
const defaultInputDir = fs.existsSync('D:\\bqp\\input') ? 'D:\\bqp\\input' : (fs.existsSync(path.resolve(__dirname, '../../../../input')) ? path.resolve(__dirname, '../../../../input') : null);
const inputDir = process.argv[2] || defaultInputDir;
const templateFile = fs.existsSync(path.join(staticDir, 'PHU LUC KEM THEO HUONG DAN CUA CUC TAI CHINH.xlsx'))
    ? path.join(staticDir, 'PHU LUC KEM THEO HUONG DAN CUA CUC TAI CHINH.xlsx')
    : path.join(staticDir, '26.9.PHU_LUC_SUA.xlsx');
const template = fs.readFileSync(templateFile);
const snapshot = w => JSON.stringify(w.worksheets.map(s => {
    const cells = [];
    s.eachRow(row => row.eachCell(c => cells.push([c.address, c.value, c.style])));
    return [s.name, cells];
}));

function context(engine) {
    const c = { console, Date, setTimeout, clearTimeout, ExcelJS, XLSX, Blob, Buffer,
        document: { addEventListener() {}, getElementById() { return null; } },
        alert(message) { throw new Error(message); } };
    c.window = c;
    vm.createContext(c);
    vm.runInContext(fs.readFileSync(path.join(staticDir, 'bqp_normalization.js'), 'utf8'), c);
    vm.runInContext(engine, c);
    c.ValidationUI.renderTable = () => {};
    return c;
}

async function run() {
    const html = fs.readFileSync(path.join(staticDir, 'index.html'), 'utf8');
    const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(m => m[1]);
    scripts.forEach(s => new vm.Script(s)); // Check all inline scripts, not only the engine.
    const engines = [context(scripts.find(s => s.includes('window.BQPValidation ='))),
        context(fs.readFileSync(path.join(staticDir, 'bqp_validation.js'), 'utf8'))];
    assert.equal(normalizer.sheetType('Phụ lục II.1'), null);
    assert.equal(normalizer.sheetType('Phụ lục III.1'), null);
    assert.equal(normalizer.sheetType('Phụ lục I.1 (2)'), null);
    assert.equal(normalizer.sheetType('PHỤ LỤC I.1'), 'I.1');
    assert.equal(normalizer.number('37 năm 09 tháng'), 37.75);
    assert.equal(normalizer.number('43 tháng'), 43);
    assert.equal(normalizer.number('27/10'), null);
    assert.equal(normalizer.number('2,5'), 2.5);
    assert.equal(normalizer.number('1.234.567,50'), 1234567.5);
    assert.equal(normalizer.number(''), null);
    assert.equal(normalizer.number(0), 0);
    assert.equal(normalizer.isTotal('Nguyễn Văn Cộng'), false);
    assert.equal(normalizer.isTotal('Tổng cộng'), true);
    const t = new ExcelJS.Workbook(); await t.xlsx.load(template);
    assert.equal(normalizer.findColMap(t.getWorksheet('Phụ lục I.3')).colMap[6], 6);
    const broken = t.getWorksheet('Phụ lục I.1');
    broken.getCell('I6').value = 'Không rõ';
    assert.throws(() => normalizer.findColMap(broken), /không nhận diện/);
    broken.getCell('A40').value = { formula: '1+1' };
    assert.match(String(normalizer.value(broken.getCell('A40'))), /\[Lỗi ô:/);
    broken.getCell('A40').value = { formula: '1-1', result: 0 };
    assert.equal(normalizer.value(broken.getCell('A40')), 0);

    // Unit test: Display name preserves Vietnamese accents
    assert.equal(normalizer.normalizeUnitName('Ban CHPTKV1 - Sóc Sơn'), 'Ban Chỉ huy PTKV 1 - Sóc Sơn');
    assert.equal(normalizer.normalizeUnitName('Ban Chỉ huy PTKV 1 - Soc Son'), 'Ban Chỉ huy PTKV 1 - Sóc Sơn');
    assert.equal(normalizer.normalizeUnitName('Ban CHPTKV 2 - Phúc Thọ'), 'Ban Chỉ huy PTKV 2 - Phúc Thọ');
    assert.equal(normalizer.normalizeUnitName('Ban CHPTKV 3 - Hồng Hà'), 'Ban Chỉ huy PTKV 3 - Hồng Hà');
    assert.equal(normalizer.normalizeUnitName('Ban CHPTKV 4 - Gia Lâm'), 'Ban Chỉ huy PTKV 4 - Gia Lâm');
    assert.equal(normalizer.normalizeUnitName('Ban CHPTKV 5 - Thanh Oai'), 'Ban Chỉ huy PTKV 5 - Thanh Oai');
    assert.equal(normalizer.normalizeUnitName('Trung đoàn 452'), 'Trung đoàn 452');
    assert.equal(normalizer.normalizeUnitName('Cục Hậu cần - Kỹ thuật'), 'Cục Hậu cần - Kỹ thuật');
    assert.equal(normalizer.normalizeUnitName('Phòng Tài Chính'), 'Phòng Tài chính');

    // Unit test: canonicalizeUnitKey matches Java UnitNameCanonicalizer
    assert.equal(normalizer.canonicalizeUnitKey('Ban CHPTKV1 - Sóc Sơn'), 'ban chptkv1 - soc son');
    assert.equal(normalizer.canonicalizeUnitKey('Ban Chỉ huy PTKV 1 - Soc Son'), 'ban chptkv1 - soc son');
    assert.equal(normalizer.canonicalizeUnitKey('Ban CH PTKV 1 - Sóc Sơn'), 'ban chptkv1 - soc son');
    assert.equal(normalizer.canonicalizeUnitKey('Trung đoàn 452'), 'trung doan452');
    assert.equal(normalizer.canonicalizeUnitKey('Trung doan 452'), 'trung doan452');
    assert.equal(normalizer.canonicalizeUnitKey('Cục Hậu cần - Kỹ thuật'), 'cuc hau can - ky thuat');
    assert.equal(normalizer.canonicalizeUnitKey('cuc hc - kt'), 'cuc hau can - ky thuat');

    if (!inputDir || !fs.existsSync(inputDir)) {
        console.log('Skipping file integration checks: input directory not found.');
        return;
    }

    for (const f of fs.readdirSync(inputDir).filter(f => f.endsWith('.xlsx') && !f.startsWith('~$'))) {
        const bytes = fs.readFileSync(path.join(inputDir, f));
        const source = new ExcelJS.Workbook(); await source.xlsx.load(bytes);
        const before = snapshot(source);
        // Independent fixture oracle: physical name columns observed in the files.
        const btl = f.includes('BTL') || f.includes('thủ đô');
        const qk9 = f.includes('QK9');
        const expectedCounts = {}, totals = {}, expectedRows = {};
        const typesToTest = btl ? ['I.1', 'I.2', 'I.3', 'I.5'] : ['I.1', 'I.2', 'I.3'];
        for (const type of typesToTest) {
            const s = source.getWorksheet('Phụ lục ' + type);
            if (!s) {
                expectedCounts[type] = 0;
                totals[type] = 0;
                expectedRows[type] = [];
                continue;
            }
            const nameCol = qk9 && type !== 'I.3' ? 3 : 2;
            const mapInfo = normalizer.findColMap(s);
            const m = mapInfo.colMap;
            const end = ({ 'I.1': 23, 'I.2': 18, 'I.3': 15, 'I.5': 22 })[type];
            const lastCol = m[end];
            const rankCol = type === 'I.5' ? 5 : 4;
            expectedRows[type] = [];
            for (let r = mapInfo.headerRowIdx + 1; r <= s.rowCount; r++) {
                const row = s.getRow(r);
                const name = normalizer.text(row.getCell(nameCol));
                const rank = normalizer.text(row.getCell(m[rankCol]));
                if (name && rank && !row.getCell(m[rankCol]).isMerged && !normalizer.isTotal(name) && !normalizer.isCategory(name)) {
                    expectedRows[type].push({
                        row: r,
                        name,
                        salary: row.getCell(m[type === 'I.5' ? 13 : 9]).value,
                        total: normalizer.number(normalizer.value(row.getCell(lastCol))) || 0
                    });
                }
            }
            expectedCounts[type] = expectedRows[type].length;
            totals[type] = expectedRows[type].reduce((sum, r) => sum + r.total, 0);
            assert.equal(m[2], nameCol);
            assert.ok(m[rankCol] >= nameCol);
        }
        for (const engine of engines) {
            await engine.ValidationUI.handleFileUpload({ target: { files: [{ name: f, arrayBuffer: async () => bytes }] } });
            for (const type of Object.keys(expectedCounts)) {
                const records = engine.ValidationUI.currentRecords.filter(r => r.sheet === type);
                assert.equal(records.length, expectedCounts[type], `${f} ${type}: upload count`);
                assert.ok(Math.abs(records.reduce((sum, r) => sum + r.tongTienThucTe, 0) - totals[type]) < 0.1, `${f} ${type}: source money`);
                records.forEach((r, i) => { assert.equal(r.hoTen, expectedRows[type][i].name.replace(/\s+/g, ' ')); });
            }
        }
        const result = await normalizer.buildWorkbook(source, template, ExcelJS);
        assert.deepEqual(result.counts, expectedCounts);
        const knownCounts = f.includes('QK5') ? [2660, 128, 0]
            : f.includes('QK9') ? [2109, 140, 9]
            : f.includes('QK4') ? [1681, 14, 5]
            : f.includes('Tài chính') ? [853, 6, 12]
            : btl ? [482, 10, 0, 1]
            : [837, 8, 1];
        assert.deepEqual(Object.values(result.counts), knownCounts);
        assert.ok(snapshot(source) === before, 'Source cell values and styles must not change');
        const roundtrip = new ExcelJS.Workbook(); await roundtrip.xlsx.load(await result.workbook.xlsx.writeBuffer());
        for (const type of Object.keys(expectedCounts)) {
            const s = roundtrip.getWorksheet('Phụ lục ' + type);
            const { colMap, headerRowIdx } = normalizer.findColMap(s);
            assert.equal(headerRowIdx, type === 'I.5' ? 5 : 9);
            let count = 0, total = 0;
            if (expectedCounts[type] === 0) continue;
            const rankCol = type === 'I.5' ? 5 : 4;
            s.eachRow((row, r) => {
                if (r <= headerRowIdx || !row.getCell(rankCol).value) return;
                assert.equal(normalizer.text(row.getCell(2)), expectedRows[type][count].name);
                const src = source.getWorksheet('Phụ lục ' + type).getRow(expectedRows[type][count].row);
                const end = ({ 'I.1': 23, 'I.2': 18, 'I.3': 15, 'I.5': 22 })[type];
                const srcMap = normalizer.findColMap(source.getWorksheet('Phụ lục ' + type)).colMap;
                for (let k = 2; k <= end; k++) {
                    const original = normalizer.value(src.getCell(srcMap[k]));
                    const isNumCol = type === 'I.5' ? ((k >= 6 && k <= 8) || k >= 11) : k >= 9;
                    const expected = isNumCol ? normalizer.number(original) ?? original : original;
                    assert.deepEqual(normalizer.value(row.getCell(k)) ?? null, expected ?? null, `${f} ${type} row ${row.number} column ${k}`);
                }
                total += normalizer.number(row.getCell(end).value) || 0;
                count++;
            });
            assert.equal(count, expectedCounts[type]);
            assert.ok(Math.abs(total - totals[type]) < 0.1);
            assert.equal(colMap[6], 6);
            assert.ok(s.model.merges.length > 0, 'Template headers preserved');
        }
        console.log(JSON.stringify({ file: f, counts: expectedCounts, totals, result: 'PASS' }));
    }
}
run().catch(e => { console.error(e); process.exitCode = 1; });

