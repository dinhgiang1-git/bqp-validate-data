/* Shared layout adapter. Normalization preserves submitted amounts; it does not
 * replace them with calculated entitlements. No network or server dependency. */
(function (root) {
    'use strict';
    const sizes = { 'I.1': 23, 'I.2': 18, 'I.3': 15, 'I.4': 16, 'I.5': 24 };
    const clean = value => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().replace(/\s+/g, ' ').trim();
    function value(cell) {
        if (!cell) return null;
        const v = cell.value;
        if (v && typeof v === 'object' && !(v instanceof Date)) {
            if ('formula' in v || 'sharedFormula' in v) {
                // ExcelJS's value getter omits cached zero; cell.result preserves it.
                const result = cell.result ?? v.result;
                if (result == null || (typeof result === 'object' && result.error)) {
                    const formulaStr = String(v.formula || cell.formula || '');
                    const isIfEmpty = /(?:,\s*""\s*\)|IFERROR\s*\(.*,\s*""\s*\))/i.test(formulaStr);
                    if (isIfEmpty && (!cell.text || cell.text.trim() === '')) {
                        return '';
                    }
                    const errName = (result && typeof result === 'object' && result.error) ? result.error : (v.error || 'Chưa tính kết quả');
                    return `[Lỗi ô: ${errName}]`;
                }
                return result;
            }
            if (v.richText) return v.richText.map(x => x.text).join('');
            if (v.error) return `[Lỗi ô: ${v.error}]`;
            if (v.text != null) return v.text;
        }
        return v instanceof Date ? new Date(v.getTime()) : v;
    }
    const text = cell => String(value(cell) ?? '').trim();
    function sheetType(sheet) {
        if (!sheet) return null;
        const name = clean(typeof sheet === 'string' ? sheet : sheet.name);
        const m = name.match(/^(?:(?:phu luc|pl)\s*)?i\s*\.?\s*([1-5])$/)
            || name.match(/^pl\s*([1-5])$/);
        return m ? `I.${m[1]}` : null;
    }
    const isTotal = label => /^(?:tong\s+)?cong(?:\s|$|[:(])/.test(clean(label));
    
    const isEllipsis = (stt, name) => {
        const s = String(stt ?? '').trim();
        const n = String(name ?? '').trim();
        return /^[.…]+$/.test(s) || (/^[.…]+$/.test(n) && (!s || /^[.…]+$/.test(s)));
    };

    const isPolicy = label => {
        const c = clean(label);
        return /^(?:nghi\s+theo\s+nghi\s+dinh|theo\s+nghi\s+dinh)\s+(?:so\s+)?(178|177|67)/i.test(c);
    };

    const isCategory = label => {
        const c = clean(label);
        if (!c) return false;
        if (/^(?:nam\s+20\d{2}|nghi\s+theo\s+nghi\s+dinh|lan\s+luot\s+don\s+vi|dot\s+\d+)/.test(c)) return true;
        if (/^(?:si\s*quan|sy\s*quan|qncn|quan\s*nhan\s*cn|quan\s*nhan\s*chuyen\s*nghiep|cn&vcqp|cong\s*nhan|vcqp|ldhd|lao\s*dong\s*hop\s*dong)(?:\s|$|[(:])/i.test(c)) return true;
        if (/^(?:dien\s+)?(?:can\s*bo|quan\s*luc)(?:\s+quan\s*ly)?(?:\s|$|[:(])/i.test(c)) return true;
        if (/^(?:qncn\s+dien\s+)(?:cb|ql)\s+quan\s*ly/i.test(c)) return true;
        return false;
    };

    const isRomanNumeral = str => {
        const s = String(str ?? '').trim();
        return /^[IVXLCDM]+$/i.test(s);
    };

    const isLetterCategory = str => {
        const s = String(str ?? '').trim();
        return /^[A-D]$/i.test(s);
    };

    const KNOWN_PTKV_AREAS = {
        '1': 'Sóc Sơn',
        '2': 'Phúc Thọ',
        '3': 'Hồng Hà',
        '4': 'Gia Lâm',
        '5': 'Thanh Oai'
    };

    function normalizeUnitName(raw) {
        let u = String(raw || '').replace(/^['"]|['"]$/g, '').trim();
        u = u.replace(/\s*\(\s*\d*[\.\d]*\s*(?:đ\/c|Đ\/C|nguoi|can\s+bo)?\s*\)\s*(?:đ\/c\))?/gi, '').trim();
        const c = clean(u);
        let mBch = c.match(/^(?:ban\s+chptkv|ban\s+chi\s+huy\s+ptkv|ban\s+ch\s+ptkv)\s*(\d+)\s*[-–—]?\s*(.*)$/);
        if (mBch) {
            let num = mBch[1];
            if (KNOWN_PTKV_AREAS[num]) {
                return `Ban Chỉ huy PTKV ${num} - ${KNOWN_PTKV_AREAS[num]}`;
            }
            let mOrig = u.match(/[-–—]\s*(.+)$/);
            let area = mOrig ? mOrig[1].trim() : (mBch[2] ? mBch[2].split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ') : '');
            return area ? `Ban Chỉ huy PTKV ${num} - ${area}` : `Ban Chỉ huy PTKV ${num}`;
        }
        if (/^(?:trung\s+doan|e)\s*452$/.test(c)) return 'Trung đoàn 452';
        if (/^(?:phong\s+tai\s+chinh|phong\s+tc|co\s+quan\s+tai\s+chinh)$/.test(c)) return 'Phòng Tài chính';
        if (/^(?:bo\s+t\.?\s*muu|bo\s+tham\s+muu)$/.test(c)) return 'Bộ Tham mưu';
        if (/^(?:cuc\s+chinh\s+tri)$/.test(c)) return 'Cục Chính trị';
        if (/^(?:cuc\s+hc\s*-\s*kt|cuc\s+hau\s+can\s*-\s*ky\s+thuat)$/.test(c)) return 'Cục Hậu cần - Kỹ thuật';
        if (/^(?:thu\s+do\s+ha\s+noi|btl\s+thu\s+do|bo\s+tu\s+lenh\s+thu\s+do\s+ha\s+noi)$/.test(c)) return 'Thủ đô Hà Nội';
        if (/^(?:da\s+nang|bo\s+chqs\s+tpdn|bo\s+chqs\s+tp\s+da\s+nang)$/.test(c)) return 'Bộ CHQS TP Đà Nẵng';
        if (/^(?:quang\s+ngai|bo\s+chqs\s+tinh\s+quang\s+ngai)$/.test(c)) return 'Bộ CHQS tỉnh Quảng Ngãi';
        if (/^(?:gia\s+lai|bo\s+chqs\s+tinh\s+gia\s+lai)$/.test(c)) return 'Bộ CHQS tỉnh Gia Lai';
        if (/^(?:dak\s+lak|bo\s+chqs\s+dak\s+lak|bo\s+chqs\s+tinh\s+dak\s+lak)$/.test(c)) return 'Bộ CHQS tỉnh Đắk Lắk';
        if (/^(?:khanh\s+hoa|bo\s+chqs\s+tinh\s+kh|bo\s+chqs\s+tinh\s+khanh\s+hoa)$/.test(c)) return 'Bộ CHQS tỉnh Khánh Hòa';
        if (/^(?:truong\s+qsqk|truong\s+quan\s+su\s+quan\s+khu|truong\s+qs\s+quan\s+khu)$/.test(c)) return 'Trường Quân sự Quân khu';
        if (/^(?:tt\s+xlbm&mt|trung\s+tam\s+xlbm&mt)$/.test(c)) return 'Trung tâm XLBM&MT';
        return u;
    }

    function canonicalizeUnitKey(name) {
        if (!name) return '';
        let s = String(name).replace(/đ/g, 'd').replace(/Đ/g, 'd');
        s = s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
        s = s.replace(/[–—]/g, '-').replace(/\s*-\s*/g, ' - ');
        s = s.replace(/\s+/g, ' ').trim();
        s = s.replace(/\bban\s+chi\s+huy\b/g, 'ban ch');
        s = s.replace(/\bban\s+ch\s+ptkv\b/g, 'ban chptkv');
        s = s.replace(/\bcuc\s+hc\s*-\s*kt\b/g, 'cuc hau can - ky thuat');
        s = s.replace(/\bco\s+quan\s+tai\s+chinh\b/g, 'phong tai chinh');
        s = s.replace(/\bbo\s+t\.?\s*muu\b/g, 'bo tham muu');
        s = s.replace(/([a-z])\s+([0-9])/g, '$1$2');
        s = s.replace(/\s+/g, ' ').trim();
        return s;
    }

    function isUnitKeyword(label) {
        const c = clean(label);
        if (!c) return false;
        return /^(?:ban\s+chi\s+huy|bo\s+chqs|su\s+doan|lu\s+doan|trung\s+doan|cuc|phong|ban|truong|tieu\s+doan|vien|tong\s+cong\s+ty|cong\s+ty|bo\s+tham\s+muu|bo\s+t\.?\s*muu)/.test(c);
    }

    function isNoteRow(row, map) {
        const name = row.getCell(map[2] || 2), rank = row.getCell(map[4] || 4);
        return /^ghi chu\s*:/.test(clean(text(name))) ||
            (rank.isMerged && rank.master.address === name.master.address);
    }

    function findColMap(sheet) {
        if (!sheet) return { colMap: {}, headerRowIdx: 9 };
        const type = sheetType(sheet);
        if (!sizes[type]) return null;
        let header = 0;
        for (let r = 1; r <= Math.min(sheet.rowCount, 40); r++) {
            let hasName = false, hasSalary = false;
            sheet.getRow(r).eachCell(c => {
                const t = clean(text(c));
                hasName ||= /^ho (?:va )?ten$/.test(t);
                hasSalary ||= /^(?:tien\s+)?luong(?:\s+thang)?/.test(t) || /^he so luong/.test(t) || (type === 'I.4' && /^cap bac/.test(t));
            });
            if (hasName && hasSalary) { header = r; break; }
        }
        if (!header) throw new Error(`${sheet.name}: không nhận diện được hàng tiêu đề họ tên và lương tháng.`);
        const map = {}, headers = [];
        sheet.getRow(header).eachCell(c => {
            if (c.isMerged && c.master.col !== c.col) return;
            const t = clean(text(c));
            headers.push({ col: c.col, t });
            let key = 0;
            if (type === 'I.5') {
                key = /^(?:stt|so tt|tt)$/.test(t) ? 1
                    : /^ho (?:va )?ten$/.test(t) ? 2
                    : /^don vi/.test(t) ? 3
                    : /^chuc vu/.test(t) ? 4
                    : /^cap bac/.test(t) ? 5
                    : /^he so luong/.test(t) ? 6
                    : /^chenh lech/.test(t) ? 7
                    : /^phu cap chuc vu/.test(t) ? 8
                    : /^nhap ngu|thoi gian nhap ngu/.test(t) ? 9
                    : /^thoi diem nghi|thoi diem xuat ngu/.test(t) ? 10
                    : /^(?:tien\s+)?luong(?:\s+thang)?/.test(t) ? 13
                    : /^cong$/.test(t) ? 22
                    : 0;
            } else {
                key = /^(?:stt|so tt|tt)$/.test(t) ? 1
                    : /^ho (?:va )?ten$/.test(t) ? 2
                    : /^(?:thang[, ]*nam sinh|nam sinh|ngay sinh)$/.test(t) ? 3
                    : /^cap bac/.test(t) ? 4
                    : /^chuc vu/.test(t) ? 5
                    : /^nhap ngu/.test(t) ? 6
                    : /^thoi diem nghi/.test(t) ? 8
                    : /^(?:tien\s+)?luong(?:\s+thang)?/.test(t) ? 9
                    : 0;
            }
            if (key && !map[key]) map[key] = c.col;
        });
        let numRowIdx = header + 1;
        for (let r = header + 1; r <= Math.min(sheet.rowCount, header + 4); r++) {
            let cnt = 0;
            sheet.getRow(r).eachCell(c => { if (/^\d+$/.test(text(c))) cnt++; });
            if (cnt >= 5) { numRowIdx = r; break; }
        }
        sheet.getRow(numRowIdx).eachCell(c => {
            const n = parseInt(text(c), 10);
            if (n >= 1 && n <= sizes[type]) {
                if (type === 'I.5' || !map[n]) {
                    if (n === 1 && map[2] === c.col) return;
                    map[n] = c.col;
                }
            }
        });
        const fallbacks = {
            'I.1': [null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23],
            'I.2': [null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
            'I.3': [null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
            'I.4': [null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16],
            'I.5': [null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24]
        }[type];
        for (let k = 1; k <= sizes[type]; k++) {
            if (!map[k]) {
                const headerMatch = headers.find(h => {
                    if (k === 1) return /^stt|so tt|tt$/.test(h.t);
                    if (k === 7) return /^sap nhap|giai the/.test(h.t);
                    if (k === 10 && type === 'I.1') return /tro cap mot lan theo thoi gian/.test(h.t);
                    if (k === 11 && type === 'I.1') return /tien luong binh quan/.test(h.t);
                    if (k === 12 && type === 'I.1') return /tro cap mot lan nghi huu truoc tuoi/.test(h.t);
                    return false;
                });
                map[k] = headerMatch ? headerMatch.col : (fallbacks ? fallbacks[k] : k);
            }
        }
        return { colMap: map, headerRowIdx: numRowIdx };
    }

    function number(v) {
        if (v == null || v === '') return null;
        if (typeof v === 'number') return Number.isFinite(v) ? v : null;
        let t = String(v).trim();
        if (!t) return null;
        if (t.includes('[Lỗi')) return null;
        if (t.includes('/')) return null;

        const cleanT = clean(t);
        const mYearMonth = cleanT.match(/^(?:(\d+)\s*nam)?\s*(?:(\d+)\s*thang)?$/);
        if (mYearMonth && (mYearMonth[1] || mYearMonth[2])) {
            const y = mYearMonth[1] ? parseInt(mYearMonth[1], 10) : 0;
            const m = mYearMonth[2] ? parseInt(mYearMonth[2], 10) : 0;
            if (mYearMonth[1] && mYearMonth[2]) {
                return y + m / 12;
            } else if (mYearMonth[1]) {
                return y;
            } else if (mYearMonth[2]) {
                return m;
            }
        }

        t = t.replace(/\s+/g, '');
        if (/^[+-]?\d{1,3}(?:\.\d{3})+(?:,\d+)?$/.test(t)) t = t.replace(/\./g, '').replace(',', '.');
        else if (/^[+-]?\d+,\d+$/.test(t)) t = t.replace(',', '.');
        else if (/^[+-]?\d{1,3}(?:,\d{3})+\.\d+$/.test(t)) t = t.replace(/,/g, '');
        return /^[+-]?\d+(?:\.\d+)?$/.test(t) ? Number(t) : null;
    }

    async function buildWorkbook(source, templateBytes, ExcelJS) {
        const output = new ExcelJS.Workbook();
        await output.xlsx.load(templateBytes);
        
        // Retain summary sheet (Phụ lục I hoặc Phụ lục II) and detail appendices (I.1 - I.5)
        for (const s of [...output.worksheets]) {
            const t = sheetType(s);
            if (s.name !== 'Phụ lục I' && s.name !== 'Phụ lục II' && !sizes[t]) {
                output.removeWorksheet(s.id);
            }
        }
        const audit = output.addWorksheet('Đối chiếu nguồn');
        audit.addRow(['Sheet nguồn', 'Dòng nguồn', 'Sheet đích', 'Dòng đích', 'Nội dung']);
        const widths = [26, 14, 24, 14, 95];
        if (audit.columns && Array.isArray(audit.columns)) {
            audit.columns.forEach((c, i) => { if (c) c.width = widths[i]; });
        } else {
            widths.forEach((w, i) => { audit.getColumn(i + 1).width = w; });
        }
        audit.getRow(1).font = { bold: true };
        audit.addRow(['', '', '', '', 'Chuẩn hóa Phụ lục theo mẫu Hướng dẫn Cục Tài chính (Cấu trúc 4 cấp: Nghị định -> Nhóm A/B -> Đơn vị I/II -> Quân nhân 1/2). Giữ nguyên số tiền kê khai.']);
        
        const counts = {};
        const unitStats = new Map();

        for (const target of output.worksheets.filter(s => sizes[sheetType(s)])) {
            const type = sheetType(target), layout = findColMap(target), end = sizes[type];
            const styleRow = target.getRow(layout.headerRowIdx + 3);
            const styles = Array.from({ length: end }, (_, i) => JSON.parse(JSON.stringify(styleRow.getCell(i + 1).style)));
            for (const merge of [...target.model.merges]) {
                if (Number(merge.match(/\d+/)[0]) > layout.headerRowIdx) target.unMergeCells(merge);
            }
            if (target._rows && target._rows.length > layout.headerRowIdx) target._rows.length = layout.headerRowIdx;
            else if (target.rowCount > layout.headerRowIdx) target.spliceRows(layout.headerRowIdx + 1, target.rowCount - layout.headerRowIdx);
            for (let k = 1; k <= end; k++) target.getRow(layout.headerRowIdx).getCell(k).value = k;
            
            const src = source.worksheets.find(s => sheetType(s) === type);
            if (!src) {
                audit.addRow(['', '', target.name, '', 'Không có phụ lục này trong file nguồn.']);
                if (type === 'I.1' || type === 'I.2' || type === 'I.3') {
                    counts[type] = 0;
                    continue;
                }
                output.removeWorksheet(target.id);
                continue;
            }
            counts[type] = 0;
            const { colMap, headerRowIdx } = findColMap(src);
            let index = 0;
            
            // Trạng thái kế thừa 4 cấp chuẩn Cục Tài chính
            let currentPolicy = (type === 'I.3' ? 'ND177' : 'ND178');
            let currentCategory = null;
            let currentCategoryName = '';
            let currentCategoryLetter = '';
            let currentUnit = 'Cơ quan đơn vị';
            let currentUnitRoman = 'I';

            for (let r = headerRowIdx + 1; r <= src.rowCount; r++) {
                const row = src.getRow(r);
                let name = text(row.getCell(colMap[2]));
                const sttRaw = text(row.getCell(colMap[1]));
                const sttNum = number(sttRaw);
                const hasStt = sttNum != null && sttNum > 0;
                
                // 1. Dòng dấu chấm lửng minh họa ('…', '...')
                if (isEllipsis(sttRaw, name)) {
                    continue;
                }

                // 2. Dòng ghi chú hoặc dòng tổng cộng
                if (isNoteRow(row, colMap)) { audit.addRow([src.name, r, '', '', `Ghi chú nguồn: ${name}`]); continue; }
                if (isTotal(name)) { audit.addRow([src.name, r, '', '', `Dòng cộng nguồn: ${name}; tổng kê khai: ${text(row.getCell(colMap[end]))}`]); continue; }

                // 3. Dòng khối Nghị định (Policy Level)
                if (isPolicy(name) || isPolicy(sttRaw)) {
                    const policyText = name || sttRaw;
                    const mPol = clean(policyText).match(/(178|177|67)/);
                    if (mPol) currentPolicy = 'ND' + mPol[1];
                    const dest = target.addRow();
                    dest.getCell(2).value = policyText;
                    dest.height = 25;
                    dest.font = { name: 'Times New Roman', size: 12, bold: true };
                    audit.addRow([src.name, r, target.name, dest.number, `Khối chính sách: ${policyText}`]);
                    continue;
                }

                const rank = text(row.getCell(colMap[type === 'I.5' ? 5 : 4]));
                const hasDateOrMoney = type === 'I.5'
                    ? (!!text(row.getCell(colMap[9])) || !!text(row.getCell(colMap[13])) || !!text(row.getCell(colMap[22])))
                    : (!!text(row.getCell(colMap[3])) || !!text(row.getCell(colMap[9])));

                // 4. Dòng Nhóm đối tượng (Category Level: A - Sĩ quan, B - QNCN...)
                const isCatText = isCategory(name);
                const isCatLetter = isLetterCategory(sttRaw);
                if (isCatText || (isCatLetter && !rank && !hasDateOrMoney)) {
                    currentCategoryLetter = isCatLetter ? sttRaw.trim().toUpperCase() : (clean(name).includes('quan nhan') ? 'B' : 'A');
                    currentCategoryName = name || (currentCategoryLetter === 'A' ? 'Sĩ quan' : 'Quân nhân chuyên nghiệp');
                    currentCategory = clean(currentCategoryName).includes('quan nhan') ? 'QNCN' : 'SQ';
                    
                    const dest = target.addRow();
                    dest.getCell(1).value = currentCategoryLetter;
                    dest.getCell(2).value = currentCategoryName;
                    dest.height = 24;
                    dest.eachCell({ includeEmpty: true }, (c, k) => {
                        c.style = JSON.parse(JSON.stringify(styles[k - 1] || {}));
                        c.font = { ...c.font, bold: true };
                    });
                    dest.getCell(1).alignment = { horizontal: 'center', vertical: 'middle' };
                    audit.addRow([src.name, r, target.name, dest.number, `Nhóm đối tượng: [${currentCategoryLetter}] ${currentCategoryName}`]);
                    continue;
                }

                // 5. Dòng Đơn vị (Unit Level: I - Đơn vị 1, II - Đơn vị 2...)
                const isRoman = isRomanNumeral(sttRaw);
                const isUnitMarker = isRoman || /^(?:ĐV|DV)$/i.test(sttRaw.trim());
                if (isUnitMarker && !rank && !hasDateOrMoney && name && !isTotal(name)) {
                    currentUnit = normalizeUnitName(name);
                    currentUnitRoman = isRoman ? sttRaw.trim().toUpperCase() : sttRaw.trim();

                    const dest = target.addRow();
                    dest.getCell(1).value = currentUnitRoman;
                    dest.getCell(2).value = currentUnit;
                    dest.height = 24;
                    dest.eachCell({ includeEmpty: true }, (c, k) => {
                        c.style = JSON.parse(JSON.stringify(styles[k - 1] || {}));
                        c.font = { ...c.font, bold: true };
                    });
                    dest.getCell(1).alignment = { horizontal: 'center', vertical: 'middle' };
                    audit.addRow([src.name, r, target.name, dest.number, `Đơn vị: [${currentUnitRoman}] ${currentUnit}`]);
                    continue;
                }

                // 6. Dòng Quân nhân / Cá nhân (Person Level)
                if (!name && hasStt) {
                    name = '[Lỗi: Thiếu họ tên]';
                }
                if (!name) continue;

                const rankColIdx = type === 'I.5' ? 5 : 4;
                const cRankCell = colMap[rankColIdx] ? row.getCell(colMap[rankColIdx]) : null;
                const isMergedRank = cRankCell ? cRankCell.isMerged : false;
                const personal = !isCategory(name) && !isUnitMarker && !isTotal(name) && (type === 'I.5' ? (!!rank || hasDateOrMoney || hasStt) : !!rank) && !isMergedRank;
                const vals = Array(end).fill(null);

                if (personal) {
                    for (let k = 1; k <= end; k++) {
                        const c = colMap[k] ? row.getCell(colMap[k]) : null;
                        vals[k - 1] = c ? value(c) : null;
                        if (k === rankColIdx && !rank) {
                            vals[k - 1] = '[Lỗi: Thiếu cấp bậc]';
                        }
                        if (((type === 'I.5' && (k < 9 || k >= 11)) || (type !== 'I.5' && k >= 9)) && vals[k - 1] != null) {
                            const parsed = number(vals[k - 1]);
                            if (parsed == null) audit.addRow([src.name, r, target.name, target.rowCount + 1, `Cột ${k}: giữ nguyên giá trị không đọc được thành số: ${vals[k - 1]}`]);
                            else vals[k - 1] = parsed;
                        }
                    }
                    vals[0] = ++index;
                    counts[type]++;

                    // Tích lũy cho bảng tổng hợp (Phụ lục I hoặc Phụ lục II)
                    if (!unitStats.has(currentUnit)) {
                        unitStats.set(currentUnit, {
                            'I.1': { count: 0, money: 0 },
                            'I.2': { count: 0, money: 0 },
                            'I.3': { count: 0, money: 0 },
                            'I.4': { count: 0, money: 0 },
                            'I.5': { count: 0, money: 0 }
                        });
                    }
                    const st = unitStats.get(currentUnit);
                    if (st[type]) {
                        st[type].count++;
                        const rowMoney = typeof vals[end - 1] === 'number' ? vals[end - 1] : (number(vals[end - 1]) || 0);
                        st[type].money += rowMoney;
                    }

                } else {
                    vals[1] = name;
                    if (!isCategory(name) && isNaN(name) && !clean(name).includes('nguyen van')) {
                        currentUnit = normalizeUnitName(name);
                    }
                    const details = [];
                    for (let k = 3; k <= end; k++) {
                        const c = row.getCell(colMap[k]);
                        if (text(c)) details.push(`${c.address}: ${text(c)}`);
                    }
                    if (details.length) audit.addRow([src.name, r, '', '', `Dòng nhóm/ghi chú, không tính là hồ sơ: ${details.join('; ')}`]);
                }

                const dest = target.addRow();
                for (let k = 0; k < vals.length; k++) dest.getCell(k + 1).value = vals[k];
                dest.height = personal ? 32 : 22;
                dest.eachCell({ includeEmpty: true }, (c, k) => {
                    c.style = JSON.parse(JSON.stringify(styles[k - 1] || {}));
                    c.alignment = { ...c.alignment, wrapText: true, vertical: 'middle' };
                    const srcCell = colMap[k] ? row.getCell(colMap[k]) : null;
                    if (srcCell && srcCell.numFmt) {
                        c.numFmt = srcCell.numFmt;
                    } else if (k < 9 && typeof c.value === 'number') {
                        c.numFmt = '#,##0';
                    }
                    if (k >= 9) c.numFmt = '#,##0.########';
                    if (c.value instanceof Date) c.numFmt = 'mm/yyyy';
                    if (!personal) c.font = { ...c.font, bold: true };
                });
                const mapped = new Set(Object.values(colMap));
                const extra = [];
                row.eachCell(c => {
                    if (!mapped.has(c.col) && (!c.isMerged || c.master.address === c.address)) extra.push(`${c.address}: ${text(c)}`);
                });
                audit.addRow([src.name, r, target.name, dest.number, extra.length ? `Cột ngoài mẫu: ${extra.join('; ')}` : 'Đã chuyển']);
            }
            target.views = [{ state: 'frozen', xSplit: 2, ySplit: layout.headerRowIdx }];
            target.pageSetup.printArea = `A1:${target.getColumn(end).letter}${target.rowCount}`;
            target.pageSetup.printTitlesRow = `1:${layout.headerRowIdx}`;
        }

        // =====================================================================
        // ĐIỀN DỮ LIỆU TỔNG HỢP VÀO SHEET PHỤ LỤC I HOẶC PHỤ LỤC II
        // =====================================================================
        const summarySheet = output.getWorksheet('Phụ lục II') || output.getWorksheet('Phụ lục I');
        if (summarySheet && unitStats.size > 0) {
            const startRow = summarySheet.name === 'Phụ lục II' ? 8 : 8;
            for (const m of [...summarySheet.model.merges]) {
                const topRow = parseInt(m.match(/\d+/)[0], 10);
                if (topRow >= startRow) summarySheet.unMergeCells(m);
            }
            if (summarySheet._rows && summarySheet._rows.length >= startRow) summarySheet._rows.length = startRow - 1;
            else if (summarySheet.rowCount >= startRow) summarySheet.spliceRows(startRow, summarySheet.rowCount - startRow + 1);

            const fontData = { name: 'Times New Roman', size: 13, bold: false };
            const fontTotal = { name: 'Times New Roman', size: 13, bold: true };
            const borderCell = {
                top: { style: 'thin' },
                left: { style: 'thin' },
                bottom: { style: 'thin' },
                right: { style: 'thin' }
            };

            let stt = 0;
            for (const [u, st] of unitStats.entries()) {
                stt++;
                const r = startRow + stt - 1;
                const row = summarySheet.getRow(r);
                row.height = 25.2;

                row.getCell(1).value = stt; // TT
                row.getCell(2).value = u;   // Tên đơn vị
                row.getCell(3).value = st['I.1'].count || 0; // Cột 1: Số người I.1
                row.getCell(4).value = st['I.1'].money || 0; // Cột 2: Số tiền I.1
                row.getCell(5).value = st['I.2'].count || 0; // Cột 3: Số người I.2
                row.getCell(6).value = st['I.2'].money || 0; // Cột 4: Số tiền I.2
                row.getCell(7).value = st['I.3'].count || 0; // Cột 5: Số người I.3
                row.getCell(8).value = st['I.3'].money || 0; // Cột 6: Số tiền I.3
                row.getCell(9).value = { formula: `C${r}+E${r}+G${r}` }; // Cột 7: Số người
                row.getCell(10).value = { formula: `D${r}+F${r}+H${r}` }; // Cột 8: Số tiền

                row.getCell(1).alignment = { horizontal: 'center', vertical: 'middle' };
                row.getCell(2).alignment = { horizontal: 'left', vertical: 'middle', wrapText: true };
                for (let c = 3; c <= 10; c++) {
                    const cell = row.getCell(c);
                    cell.alignment = { horizontal: 'right', vertical: 'middle' };
                    cell.numFmt = '#,##0';
                }
                for (let c = 1; c <= 10; c++) {
                    const cell = row.getCell(c);
                    cell.font = fontData;
                    cell.border = borderCell;
                }
            }

            const lastDataRow = startRow + stt - 1;
            const totalRowIdx = lastDataRow + 1;
            const totalRow = summarySheet.getRow(totalRowIdx);
            totalRow.height = 34.5;
            totalRow.getCell(1).value = null;
            totalRow.getCell(2).value = 'TỔNG CỘNG';
            totalRow.getCell(3).value = { formula: `SUM(C${startRow}:C${lastDataRow})` };
            totalRow.getCell(4).value = { formula: `SUM(D${startRow}:D${lastDataRow})` };
            totalRow.getCell(5).value = { formula: `SUM(E${startRow}:E${lastDataRow})` };
            totalRow.getCell(6).value = { formula: `SUM(F${startRow}:F${lastDataRow})` };
            totalRow.getCell(7).value = { formula: `SUM(G${startRow}:G${lastDataRow})` };
            totalRow.getCell(8).value = { formula: `SUM(H${startRow}:H${lastDataRow})` };
            totalRow.getCell(9).value = { formula: `C${totalRowIdx}+E${totalRowIdx}+G${totalRowIdx}` };
            totalRow.getCell(10).value = { formula: `D${totalRowIdx}+F${totalRowIdx}+H${totalRowIdx}` };

            totalRow.getCell(2).alignment = { horizontal: 'center', vertical: 'middle' };
            for (let c = 3; c <= 10; c++) {
                const cell = totalRow.getCell(c);
                cell.alignment = { horizontal: 'right', vertical: 'middle' };
                cell.numFmt = '#,##0';
            }
            for (let c = 1; c <= 10; c++) {
                const cell = totalRow.getCell(c);
                cell.font = fontTotal;
                cell.border = borderCell;
            }

            // Dòng Ghi chú
            const noteRowIdx = totalRowIdx + 1;
            const noteRow = summarySheet.getRow(noteRowIdx);
            noteRow.height = 22.0;
            noteRow.getCell(1).value = 'Ghi chú: Sau khi nhập xong số liệu đơn vị từng cấp rà soát phải khớp đúng với số quyết toán năm 2025.';
            noteRow.getCell(1).font = { name: 'Times New Roman', size: 12, italic: true };
            summarySheet.mergeCells(noteRowIdx, 1, noteRowIdx, 10);
        }

        for (const s of source.worksheets) {
            const t = sheetType(s);
            if (s.name !== 'Phụ lục I' && s.name !== 'Phụ lục II' && !sizes[t]) {
                audit.addRow([s.name, '', '', '', 'Ngoài phạm vi chuẩn hóa các phụ lục chi tiết. Xem sheet này trong file nguồn.']);
            }
        }
        return { workbook: output, counts };
    }

    const EMBEDDED_TEMPLATE_B64 = "${b64Data}";

    async function download(validationUI) {
        const sourceWb = validationUI.rawWb || validationUI.currentWb;
        if (!sourceWb) {
            alert('Chưa có file dữ liệu để chuẩn hóa. Vui lòng import file trước.');
            return;
        }

        const templateBytes = Uint8Array.from(atob(EMBEDDED_TEMPLATE_B64), c => c.charCodeAt(0));
        
        const excelJSRef = typeof ExcelJS !== 'undefined' ? ExcelJS : window.ExcelJS;

        const { workbook: normalizedWb } = await buildWorkbook(sourceWb, templateBytes, excelJSRef);

        const auditSheet = normalizedWb.getWorksheet('Đối chiếu nguồn');
        if (auditSheet) {
            normalizedWb.removeWorksheet(auditSheet.id);
        }

        const buffer = await normalizedWb.xlsx.writeBuffer();
        const blob = new Blob([buffer], { 
            type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' 
        });

        const originalName = validationUI.currentFile?.name || 'du_lieu';
        const baseName = originalName.replace(/\.[^.]+$/, '');
        const fileName = `${baseName}_chuan_hoa_BQP.xlsx`;

        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        console.log('Đã tải file chuẩn hóa:', fileName);
    }

    const api = {
        sheetType,
        findColMap,
        buildWorkbook,
        normalizeUnitName,
        canonicalizeUnitKey,
        isUnitKeyword,
        isCategory,
        isTotal,
        isEllipsis,
        isPolicy,
        isRomanNumeral,
        isLetterCategory,
        isNoteRow,
        number,
        value,
        text,
        clean,
        EMBEDDED_TEMPLATE_B64,
        download
    };

    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    else root.BQPNormalization = api;
})(typeof window !== 'undefined' ? window : this);
