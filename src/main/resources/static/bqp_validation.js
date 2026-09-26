/**
 * Công cụ Thẩm định Excel Chế độ Chính sách BQP (Nghị định 178 & 177)
 * Chạy 100% Offline trên trình duyệt qua JavaScript thuần
 * Tuyệt đối không dùng icon/emoji - Phong cách chuẩn mực công vụ quân sự
 */

window.BQPValidation = (function () {
    const LCS = 2340000; // Mức lương cơ sở theo Nghị định

    // Trợ giúp phân loại cấp bậc, chức vụ và tính trần tuổi
    const MilitaryRankHelper = {
        checkIsQNCN(capBac, chucVu) {
            let cb = (capBac || '').trim().toLowerCase().replace(/\s+/g, ' ');
            let cv = (chucVu || '').trim().toLowerCase().replace(/\s+/g, ' ');
            let hasNv = cv === 'nv' || cv.startsWith('nv ') || cv.startsWith('nv.') || cv.startsWith('nv/')
                || cv.includes(' nv ') || cv.includes(' nv.') || cv.includes(' nv/') || cv.endsWith(' nv');
            return cv.includes('nhân viên') || hasNv
                || cv.includes('y sĩ') || cv.includes('y sỹ') || cv.includes('y si') || cv.includes('y sy')
                || cv.includes('thủ kho') || cv.includes('thu kho')
                || cv.includes('bảo quản') || cv.includes('bao quan')
                || cv.includes('thủy thủ') || cv.includes('thuỷ thủ') || cv.includes('thuy thu')
                || cv.includes('lái xe') || cv.includes('thợ')
                || cv.includes('chạm') || cv.includes('trạm') || cb.includes('qncn') || cv.includes('qncn')
                || cb.includes(' cn') || cb.includes('/cn') || cb.endsWith('cn') || cb.includes('cia') || cb.includes('chuyên nghiệp');
        },

        getTran(capBac, chucVu) {
            if (!capBac && !chucVu) return 0;
            let cb = (capBac || '').replace(/\u00A0/g, ' ').trim().toLowerCase().replace(/\s+/g, ' ');
            let cv = (chucVu || '').replace(/\u00A0/g, ' ').trim().toLowerCase().replace(/\s+/g, ' ');
            let isQNCN = this.checkIsQNCN(cb, cv);

            cb = cb.replace(/^[-–—\s]+/, '');

            if (cb.startsWith('24.') || cb === '24' || cb.startsWith('24cn') || cb.startsWith('24 cn')) return 58;
            if (cb.startsWith('23.') || cb === '23' || cb.startsWith('23cn') || cb.startsWith('23 cn')) return 56;
            if (cb.startsWith('22.') || cb === '22' || cb.startsWith('22cn') || cb.startsWith('22 cn')) return 54;
            if (cb.startsWith('21.') || cb === '21' || cb.startsWith('21cn') || cb.startsWith('21 cn')) return isQNCN ? 54 : 52;
            if (cb.startsWith('14.') || cb === '14' || cb.startsWith('14cn') || cb.startsWith('14 cn')) return isQNCN ? 52 : 50;

            if (/(?<!\d)4\s*\/\//.test(cb) || cb.includes('đại tá') || cb.includes('đai tá')) return 58;
            if (/(?<!\d)3\s*\/\//.test(cb) || cb.includes('thượng tá') || cb.includes('thượng tạ') || cb.includes('thuong tá')) return 56;
            if (/(?<!\d)2\s*\/\//.test(cb) || cb.includes('trung tá')) return 54;
            if (/(?<!\d)1\s*\/\//.test(cb) || cb.includes('thiếu tá') || cb.includes('thiéu tá')) return isQNCN ? 54 : 52;

            if (/(?<!\d)4\s*\/(?!\/|\d)/.test(cb) || cb.includes('đại uý') || cb.includes('đại úy')) return isQNCN ? 52 : 50;
            if (/(?<!\d)3\s*\/(?!\/|\d)/.test(cb) || cb.includes('thượng uý') || cb.includes('thượng úy')) return isQNCN ? 52 : 50;
            if (/(?<!\d)2\s*\/(?!\/|\d)/.test(cb) || cb.includes('trung uý') || cb.includes('trung úy')) return isQNCN ? 52 : 50;
            if (/(?<!\d)1\s*\/(?!\/|\d)/.test(cb) || cb.includes('thiếu uý') || cb.includes('thiếu úy')) return isQNCN ? 52 : 50;

            if (cb.includes('uý') || cb.includes('úy')) return isQNCN ? 52 : 50;

            if (cv.includes('đại tá') || cv.includes('đai tá')) return 58;
            if (cv.includes('thượng tá') || cv.includes('thuong tá')) return 56;
            if (cv.includes('trung tá')) return 54;
            if (cv.includes('thiếu tá') || cv.includes('thiéu tá')) return isQNCN ? 54 : 52;

            if (isQNCN || cb.includes('quân nhân chuyên nghiệp')) return 54;
            if (cb.includes('vcqp') || cb.includes('cnqp') || cb.includes('viên chức quốc phòng') || cb.includes('công nhân quốc phòng') || cb.includes('lao động hợp đồng') || cb.includes('ldhd')) return 60;

            return 0; // Trả về 0 chuẩn mực giống Java để báo không nhận diện được
        }
    };

    function cleanString(val) {
        if (val == null) return '';
        return String(val)
            .replace(/[\u00A0\u2007\u202F\u200B\uFEFF]/g, ' ')
            .trim()
            .replace(/\s+/g, ' ');
    }

    function toDateObj(d) {
        if (!d) return null;
        if (d instanceof Date) return isNaN(d.getTime()) ? null : d;
        if (typeof d === 'string') {
            let s = d.trim();
            let mY = s.match(/^(\d{1,2})[\/\-](\d{4})$/);
            if (mY) {
                return new Date(parseInt(mY[2], 10), parseInt(mY[1], 10) - 1, 1);
            }
            let dMY = s.match(/^(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})$/);
            if (dMY) {
                return new Date(parseInt(dMY[3], 10), parseInt(dMY[2], 10) - 1, parseInt(dMY[1], 10));
            }
            let parsed = new Date(s);
            if (!isNaN(parsed.getTime())) return parsed;
        }
        return null;
    }

    function calcThang(d1, d2) {
        let dt1 = toDateObj(d1);
        let dt2 = toDateObj(d2);
        if (!dt1 || !dt2) return 0;
        return (dt1.getFullYear() * 12 + dt1.getMonth()) - (dt2.getFullYear() * 12 + dt2.getMonth());
    }

    function calcNamLamTron(thang) {
        if (thang <= 0) return 0;
        let years = Math.floor(thang / 12);
        let rem = thang % 12;
        if (rem === 0) return years;
        if (rem <= 6) return years + 0.5;
        return years + 1.0;
    }

    function isEqual(a, b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Math.abs(Number(a) - Number(b)) <= 1000;
    }

    function isEqualTime(a, b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Math.abs(Number(a) - Number(b)) < 0.01;
    }

    function fmtMoney(m) {
        if (m == null || isNaN(m)) return '0';
        return Math.round(Number(m)).toLocaleString('vi-VN');
    }

    function fmtNum(n) {
        if (n == null || isNaN(n)) return '0';
        return Number(n).toFixed(1).replace(/\.0$/, '');
    }

    function formatDate(d) {
        if (!d) return '';
        if (d instanceof Date) {
            if (isNaN(d.getTime())) return '';
            let m = d.getMonth() + 1;
            let y = d.getFullYear();
            return (m < 10 ? '0' + m : m) + '/' + y;
        }
        if (typeof d === 'string') {
            let s = d.trim();
            if (/^\d{1,2}[\/\-]\d{4}$/.test(s)) return s;
            if (s.includes('-') || s.includes('T')) {
                let parsed = new Date(s);
                if (!isNaN(parsed.getTime())) {
                    let m = parsed.getMonth() + 1;
                    let y = parsed.getFullYear();
                    return (m < 10 ? '0' + m : m) + '/' + y;
                }
            }
            return s;
        }
        return String(d);
    }

    function readNumberToVietnameseWords(amount) {
        if (!amount || isNaN(amount) || amount === 0) return "Không đồng";
        amount = Math.round(Math.abs(amount));

        const digits = ["không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"];
        const tiers = ["", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ"];

        function readThreeDigits(n, hasHigher) {
            let h = Math.floor(n / 100);
            let rem = n % 100;
            let t = Math.floor(rem / 10);
            let u = rem % 10;
            let s = "";

            if (h > 0 || hasHigher) s += digits[h] + " trăm ";

            if (t > 1) {
                s += digits[t] + " mươi ";
                if (u === 1) s += "mốt";
                else if (u === 5) s += "lăm";
                else if (u > 0) s += digits[u];
            } else if (t === 1) {
                s += "mười ";
                if (u === 5) s += "lăm";
                else if (u > 0) s += digits[u];
            } else {
                if (h > 0 || hasHigher) {
                    if (u > 0) s += "lẻ " + digits[u];
                } else if (u > 0) {
                    s += digits[u];
                }
            }
            return s.trim();
        }

        let val = amount;
        let tierIdx = 0;
        let parts = [];

        while (val > 0) {
            let chunk = val % 1000;
            if (chunk !== 0) {
                let chunkWords = readThreeDigits(chunk, val >= 1000);
                let tier = tiers[tierIdx];
                if (tier) chunkWords += " " + tier;
                parts.unshift(chunkWords);
            }
            val = Math.floor(val / 1000);
            tierIdx++;
        }

        let result = parts.join(" ").replace(/\s+/g, " ").trim();
        return result ? result.charAt(0).toUpperCase() + result.slice(1) + " đồng chẵn" : "Không đồng";
    }

    // ============================================================
    // CÁC BỘ TÍNH TOÁN THEO QUY ĐỊNH BỘ QUỐC PHÒNG
    // ============================================================

    // 1. Phụ lục I.1 (Nghỉ hưu NĐ 178)
    const PLI1Calculator = {
        calculateExpected(data) {
            let tran = MilitaryRankHelper.getTran(data.capBac, data.chucVu);
            let dNgaySinh = toDateObj(data.ngaySinh);
            let dThoiDiemNghi = toDateObj(data.thoiDiemNghi);
            let dNhapNgu = toDateObj(data.nhapNgu);
            let dSapNhap = toDateObj(data.sapNhap);

            let rawCot10 = 0;
            if (tran > 0 && dNgaySinh && dThoiDiemNghi) {
                let expYear = dNgaySinh.getFullYear() + tran;
                let expMonth = dNgaySinh.getMonth();
                let retYear = dThoiDiemNghi.getFullYear();
                let retMonth = dThoiDiemNghi.getMonth();
                let diff = (expYear * 12 + expMonth) - (retYear * 12 + retMonth);
                rawCot10 = diff > 0 ? diff + 1 : 0; // tính cả 2 đầu tháng
            }
            if (rawCot10 < 0) rawCot10 = 0;

            let actualC10 = data.cot10Actual;
            let isCapped60 = (rawCot10 > 60 && actualC10 === 60);
            let cot10 = isCapped60 ? 60 : rawCot10;

            let rawExp11 = calcNamLamTron(rawCot10);
            let actualC11 = data.cot11Actual;
            let c11Valid = actualC11 != null && (isEqualTime(actualC11, rawExp11) || (rawCot10 > 60 && actualC11 === 5));
            let cot11 = c11Valid ? actualC11 : rawExp11;

            let monthsC12 = calcThang(dThoiDiemNghi, dNhapNgu);
            if (monthsC12 < 0) monthsC12 = 0;
            let rawExp12 = calcNamLamTron(monthsC12);
            let actualC12 = data.cot12Actual;
            let c12Valid = actualC12 != null && isEqualTime(actualC12, rawExp12);
            let cot12 = c12Valid ? actualC12 : rawExp12;

            let luong = Number(data.luongThang) || 0;
            let nghiTruoc172025 = true;
            if (dThoiDiemNghi && dThoiDiemNghi >= new Date(2025, 6, 1)) {
                nghiTruoc172025 = false;
            }

            let timeDiff = (dThoiDiemNghi && dSapNhap) ? calcThang(dThoiDiemNghi, dSapNhap) : 0;
            let nhoHon12 = (!dSapNhap || timeDiff <= 12);

            let isOver60 = (rawCot10 > 60 && cot11 > 5.0);
            let cappedCot10 = Math.min(rawCot10, 60);

            let cot13 = 0, cot14 = 0, cot15 = 0, cot16 = 0;
            if (nhoHon12) {
                if (!isOver60) cot13 = cappedCot10 * 1.0 * luong;
                else cot14 = cappedCot10 * 0.9 * luong;
            } else {
                if (!isOver60) cot15 = cappedCot10 * 0.5 * luong;
                else cot16 = cappedCot10 * 0.45 * luong;
            }

            let cot17 = 0, cot18 = 0, cot19 = 0, cot20 = 0, cot21 = 0, cot22 = 0;
            let val18_21 = 0, val19_22 = 0;
            let exp12ForMoney = (actualC12 != null && actualC12 > 0) ? actualC12 : rawExp12;

            let mocBHXH = nghiTruoc172025 ? 20 : 15;
            let hsBHXH = nghiTruoc172025 ? 5 : 4;

            if (exp12ForMoney > mocBHXH) {
                val18_21 = luong * hsBHXH;
                val19_22 = luong * 0.5 * (exp12ForMoney - mocBHXH);
            }

            if (cot10 >= 24 && !isOver60) {
                cot17 = cot11 * 5 * luong;
                cot18 = val18_21;
                cot19 = val19_22;
            } else if (isOver60) {
                cot20 = cot11 * 4 * luong;
                cot21 = val18_21;
                cot22 = val19_22;
            }

            let total = cot13 + cot14 + cot15 + cot16 + cot17 + cot18 + cot19 + cot20 + cot21 + cot22;

            return {
                cot10, cot11, cot12,
                cot13, cot14, cot15, cot16, cot17, cot18, cot19, cot20, cot21, cot22,
                total, tran, rawCot10, isOver60, nghiTruoc172025, mocBHXH, hsBHXH, nhoHon12
            };
        }
    };

    // 2. Phụ lục I.2 (Thôi việc NĐ 178)
    const PLI2Calculator = {
        calculateExpected(data) {
            let tran = MilitaryRankHelper.getTran(data.capBac, data.chucVu) || 54;
            let dNgaySinh = toDateObj(data.ngaySinh);
            let dThoiDiemNghi = toDateObj(data.thoiDiemNghi);
            let dNhapNgu = toDateObj(data.nhapNgu);
            let dSapNhap = toDateObj(data.sapNhap);

            let rawThangConLai = 0;
            if (tran > 0 && dNgaySinh && dThoiDiemNghi) {
                let dobMonth = dNgaySinh.getFullYear() * 12 + dNgaySinh.getMonth();
                let retMonth = dThoiDiemNghi.getFullYear() * 12 + dThoiDiemNghi.getMonth();
                rawThangConLai = (dobMonth + tran * 12) - retMonth;
            }
            if (rawThangConLai < 0) rawThangConLai = 0;

            let rawCot10 = rawThangConLai > 0 ? Math.min(rawThangConLai, 60) : 60;
            let actualC10 = data.cot10Actual;
            let c10Valid = (actualC10 != null && (actualC10 === rawCot10 || Math.abs(actualC10 - rawCot10) === 24 || Math.abs(actualC10 - rawCot10) === 2));
            let cot10 = c10Valid ? actualC10 : rawCot10;
            let thangConLai = c10Valid ? actualC10 : rawThangConLai;

            let isOver2Years = thangConLai > 24;

            let monthsCongTac = (dThoiDiemNghi && dNhapNgu) ? calcThang(dThoiDiemNghi, dNhapNgu) : 0;
            if (monthsCongTac < 0) monthsCongTac = 0;
            let rawCot11 = calcNamLamTron(monthsCongTac);
            let actualC11 = data.cot11Actual;
            let c11Valid = (actualC11 != null && (isEqualTime(actualC11, rawCot11) || Math.abs(actualC11 - rawCot11) === 2.0 || Math.abs(actualC11 - rawCot11) === 24.0));
            let cot11 = c11Valid ? actualC11 : rawCot11;

            let distance8_7 = (dThoiDiemNghi && dSapNhap) ? calcThang(dThoiDiemNghi, dSapNhap) : 0;
            let isWithin12Months = (!dSapNhap || distance8_7 <= 12);

            let luong = Number(data.luongThang) || 0;
            let cot12 = 0, cot13 = 0, cot14 = 0, cot15 = 0, cot16 = 0, cot17 = 0;

            if (isOver2Years) {
                if (isWithin12Months) {
                    cot12 = cot10 * 0.8 * luong;
                    cot13 = cot11 * 1.5 * luong;
                    cot14 = 3 * luong;
                } else {
                    cot15 = cot10 * 0.4 * luong;
                    cot16 = cot11 * 1.5 * luong;
                    cot17 = 3 * luong;
                }
            }

            let cot18 = cot12 + cot13 + cot14 + cot15 + cot16 + cot17;

            return {
                cot10, cot11, cot12, cot13, cot14, cot15, cot16, cot17, cot18,
                total: cot18, tran, rawThangConLai, isOver2Years, isWithin12Months, distance8_7
            };
        }
    };

    // 3. Phụ lục I.3 (Nghỉ hưu NĐ 177)
    const PLI3Calculator = {
        calculateExpected(data) {
            let tran = MilitaryRankHelper.getTran(data.capBac, data.chucVu) || 58;
            let dNgaySinh = toDateObj(data.ngaySinh);
            let dThoiDiemNghi = toDateObj(data.thoiDiemNghi);
            let dNhapNgu = toDateObj(data.nhapNgu);

            let diffMonths10 = (dThoiDiemNghi && dNhapNgu) ? calcThang(dThoiDiemNghi, dNhapNgu) : 0;
            if (diffMonths10 < 0) diffMonths10 = 0;
            let rawCot10 = calcNamLamTron(diffMonths10);
            let actualC10 = data.cot10Actual;
            let c10Valid = (actualC10 != null && (isEqualTime(actualC10, rawCot10) || Math.abs(actualC10 - rawCot10) === 2.0 || Math.abs(actualC10 - rawCot10) === 24.0));
            let cot10 = c10Valid ? actualC10 : rawCot10;

            let rawCot11 = 0;
            if (tran > 0 && dNgaySinh && dThoiDiemNghi) {
                let expYear = dNgaySinh.getFullYear() + tran;
                let expMonth = dNgaySinh.getMonth();
                let retYear = dThoiDiemNghi.getFullYear();
                let retMonth = dThoiDiemNghi.getMonth();
                let diff11 = (expYear * 12 + expMonth) - (retYear * 12 + retMonth);
                if (diff11 > 0) {
                    let y11 = Math.floor(diff11 / 12);
                    let m11 = diff11 % 12;
                    if (m11 === 0) rawCot11 = y11;
                    else if (m11 < 6) rawCot11 = y11 + 0.5;
                    else rawCot11 = y11 + 1.0;
                }
            }
            let actualC11 = data.cot11Actual;
            let c11Valid = (actualC11 != null && (isEqualTime(actualC11, rawCot11) || Math.abs(actualC11 - rawCot11) === 2.0 || Math.abs(actualC11 - rawCot11) === 24.0));
            let cot11 = c11Valid ? actualC11 : rawCot11;

            let luong = Number(data.luongThang) || 0;
            let cot12 = cot11 * 5 * luong;
            let cot13 = 5 * luong;

            let nghiTruoc172025 = true;
            if (data.thoiDiemNghi && data.thoiDiemNghi >= new Date(2025, 6, 1)) {
                nghiTruoc172025 = false;
            }

            let moc14 = nghiTruoc172025 ? 20 : 15;
            let cot14 = 0;
            if (cot10 > moc14) {
                cot14 = (cot10 - moc14) * 0.5 * luong;
            }

            let cot15 = cot12 + cot13 + cot14;

            return {
                cot10, cot11, cot12, cot13, cot14, cot15,
                total: cot15, tran, moc14, nghiTruoc172025
            };
        }
    };

    // 4. Phụ lục I.5 (Lương tháng hiện hưởng làm căn cứ tính hưởng chế độ - 26.9.PHU_LUC_SUA.xlsx)
    const PLI5Calculator = {
        calculateExpected(data) {
            const LCS = 2340000; // 2.340.000 VNĐ

            // Cột 13 = Cột 7 * 2.340.000đ (Chênh lệch bảo lưu)
            let rawC7 = data.heSoChenhLechBaoLuu != null ? data.heSoChenhLechBaoLuu : (data.rawCols && data.rawCols[7]);
            let hsBaoLuu = Number(rawC7) || 0;
            let cot13 = Math.round(hsBaoLuu * LCS);

            // Cột 14 = Cột 6 * 2.340.000đ (Lương ngạch bậc)
            let rawC6 = data.heSoLuong != null ? data.heSoLuong : (data.rawCols && data.rawCols[6]);
            let hsLuong = Number(rawC6) || 0;
            let cot14 = Math.round(hsLuong * LCS);

            // Cột 15 = Cột 8 * 2.340.000đ (Phụ cấp chức vụ)
            let rawC8 = data.heSoChucVu != null ? data.heSoChucVu : (data.rawCols && data.rawCols[8]);
            let hsChucVu = Number(rawC8) || 0;
            let cot15 = Math.round(hsChucVu * LCS);

            // Cột 16 = [((Cột 10 – 1 tháng) – Cột 9) đơn vị tính năm] * (Cột 14 + Cột 15)
            let dThoiDiemNghi = toDateObj(data.thoiDiemNghi || data.cot10);
            let dNhapNgu = toDateObj(data.nhapNgu || data.cot9);
            let diffMonths = 0;
            if (dThoiDiemNghi && dNhapNgu) {
                let dNghiMinus1 = new Date(dThoiDiemNghi.getFullYear(), dThoiDiemNghi.getMonth() - 1, 1);
                diffMonths = calcThang(dNghiMinus1, dNhapNgu);
                if (diffMonths < 0) diffMonths = 0;
            }

            let soNamNguyen = Math.floor(diffMonths / 12);
            let tiLeThamNien = Math.max(0, soNamNguyen / 100); // Mỗi năm thâm niên nghề hưởng 1%
            let base14_15 = cot14 + cot15;
            let cot16 = Math.round(base14_15 * tiLeThamNien);

            // Cột 17 = Phụ cấp thâm niên vượt khung (đọc từ Excel nếu có)
            let rawC17 = data.phuCapThamNienVuotKhung != null ? data.phuCapThamNienVuotKhung : (data.rawCols && data.rawCols[17]);
            let cot17 = Number(rawC17) || 0;

            // Căn cứ tính Cột 18, 19, 20: (Cột 14 + Cột 15 + Cột 16)
            let base14_15_16 = cot14 + cot15 + cot16;

            // Cột 18 = Cột 11 * (Cột 14 + Cột 15 + Cột 16)
            let rawC11 = data.tiLePhuCapTrachNhiem != null ? data.tiLePhuCapTrachNhiem : (data.rawCols && data.rawCols[11]);
            let valC11 = Number(rawC11) || 0;
            let tiLeC11 = (valC11 > 1.0) ? (valC11 / 100) : valC11;
            let cot18 = Math.round(base14_15_16 * tiLeC11);

            // Cột 19 = 25% * (Cột 14 + Cột 15 + Cột 16)
            let cot19 = Math.round(base14_15_16 * 0.25);

            // Cột 20 = Cột 12 * (Cột 14 + Cột 15 + Cột 16)
            let rawC12 = data.tiLePhuCapDacThu != null ? data.tiLePhuCapDacThu : (data.rawCols && data.rawCols[12]);
            let valC12 = Number(rawC12) || 0;
            let tiLeC12 = (valC12 > 1.0) ? (valC12 / 100) : valC12;
            let cot20 = Math.round(base14_15_16 * tiLeC12);

            // Cột 21 = Được nhận khác (đọc từ Excel nếu có)
            let rawC21 = data.duocNhanKhac != null ? data.duocNhanKhac : (data.rawCols && data.rawCols[21]);
            let cot21 = Number(rawC21) || 0;

            // Cột 22 = SUM(Cột 13 : Cột 21)
            let cot22 = cot13 + cot14 + cot15 + cot16 + cot17 + cot18 + cot19 + cot20 + cot21;

            return {
                cot13, cot14, cot15, cot16, cot17, cot18, cot19, cot20, cot21, cot22,
                total: cot22,
                diffMonths,
                soNamThamNien: soNamNguyen,
                tiLeThamNien,
                tiLeC11,
                tiLeC12,
                base14_15,
                base14_15_16
            };
        }
    };

    // ============================================================
    // BỘ PHÂN TÍCH VÀ ĐỐI CHIẾU DỮ LIỆU EXCEL
    // ============================================================
    const ExcelParser = {
        getCellText(cell) {
            if (!cell || cell.value == null) return '';
            if (typeof cell.value === 'object') {
                if (cell.value.richText) return cleanString(cell.value.richText.map(rt => rt.text).join(''));
                if (cell.value.result != null) return cleanString(cell.value.result);
                if (cell.value.text != null) return cleanString(cell.value.text);
            }
            try {
                return cleanString(cell.text || cell.value || '');
            } catch (e) {
                return cleanString(cell.value || '');
            }
        },

        parseNumber(cell) {
            if (!cell || cell.value == null) return 0;
            if (typeof cell.value === 'number') return cell.value;
            if (typeof cell.value === 'object' && cell.value.result != null) {
                let n = Number(cell.value.result);
                if (!isNaN(n)) return n;
            }
            let txt = this.getCellText(cell).replace(/[^\d.-]/g, '');
            let n = parseFloat(txt);
            return isNaN(n) ? 0 : n;
        },

        parseDateCell(cell) {
            if (!cell || cell.value == null) return null;
            let val = cell.value;
            if (val instanceof Date) {
                let y = val.getFullYear();
                if (y > 2040) val.setFullYear(y - 100);
                return val;
            }
            let text = this.getCellText(cell).replace(/[-.]/g, '/').replace(/\s+/g, '');
            let parts = text.split('/');
            if (parts.length === 2) {
                let m = parseInt(parts[0], 10);
                let y = parseInt(parts[1], 10);
                if (y < 100) y = y <= 45 ? 2000 + y : 1900 + y;
                if (y > 2040) y -= 100;
                if (m >= 1 && m <= 12) return new Date(y, m - 1, 1);
            } else if (parts.length === 3) {
                let d = parseInt(parts[0], 10);
                let m = parseInt(parts[1], 10);
                let y = parseInt(parts[2], 10);
                if (parts[0].length === 4) {
                    y = parseInt(parts[0], 10);
                    m = parseInt(parts[1], 10);
                    d = parseInt(parts[2], 10);
                }
                if (y < 100) y = y <= 45 ? 2000 + y : 1900 + y;
                if (y > 2040) y -= 100;
                if (m >= 1 && m <= 12) return new Date(y, m - 1, d);
            } else if (/^\d{4}$/.test(text)) {
                let y = parseInt(text, 10);
                if (y > 2040) y -= 100;
                return new Date(y, 0, 1);
            }
            return null;
        },

        findColMap(worksheet) {
            let colMap = {};
            let headerRowIdx = -1;
            let rowCount = Math.min(15, worksheet.rowCount);

            for (let r = 1; r <= rowCount; r++) {
                let row = worksheet.getRow(r);
                let rowMap = {};
                for (let c = 1; c <= 35; c++) {
                    let txt = this.getCellText(row.getCell(c));
                    if (txt.includes('/') || /^\d+[-–]\d+$/.test(txt.trim())) continue;
                    let m = txt.match(/^[\(\[]?(\d{1,2})/);
                    if (m) {
                        let num = parseInt(m[1], 10);
                        if (num >= 1 && num <= 35 && !rowMap[num]) {
                            rowMap[num] = c;
                        }
                    }
                }
                if (Object.keys(rowMap).length >= 7 && rowMap[1] && rowMap[2] && rowMap[3] && rowMap[4] && rowMap[5]) {
                    headerRowIdx = r;
                    // Nhận diện cột Họ và tên nằm ở số nào (1 hay 2)
                    let colHoTenNum = -1;
                    for (let num in rowMap) {
                        let cIdx = rowMap[num];
                        for (let pr = Math.max(1, r - 5); pr < r; pr++) {
                            let prevRow = worksheet.getRow(pr);
                            let hText = this.getCellText(prevRow.getCell(cIdx)).toLowerCase().replace(/\s+/g, ' ').trim();
                            if (hText.includes('họ và tên') || hText.includes('ho va ten') || hText.includes('họ tên')) {
                                colHoTenNum = parseInt(num, 10);
                                break;
                            }
                        }
                        if (colHoTenNum !== -1) break;
                    }

                    // Nhận diện cột Lương nằm ở số nào (9 hay 10)
                    let colLuongNum = -1;
                    for (let num in rowMap) {
                        let cIdx = rowMap[num];
                        for (let pr = Math.max(1, r - 5); pr < r; pr++) {
                            let prevRow = worksheet.getRow(pr);
                            let hText = this.getCellText(prevRow.getCell(cIdx)).toLowerCase().replace(/\s+/g, ' ').trim();
                            if (hText.includes('lương tháng') || hText.includes('luong thang') || hText.includes('tiền lương')) {
                                colLuongNum = parseInt(num, 10);
                                break;
                            }
                        }
                        if (colLuongNum !== -1) break;
                    }

                    if (colLuongNum === 10 && colHoTenNum === 2) {
                        // Mẫu cũ có thêm cột "Đánh giá xếp loại cán bộ"
                        colMap[1] = rowMap[1]; // STT
                        colMap[2] = rowMap[2]; // Họ tên
                        colMap[3] = rowMap[3]; // Ngày sinh
                        colMap[4] = rowMap[4]; // Cấp bậc
                        colMap[5] = rowMap[5]; // Chức vụ
                        colMap[6] = rowMap[6]; // Nhập ngũ
                        colMap[7] = rowMap[8]; // Sáp nhập
                        colMap[8] = rowMap[9]; // Nghỉ hưu
                        colMap[9] = rowMap[10]; // Lương
                        for (let k = 10; k <= 35; k++) {
                            if (rowMap[k + 1]) colMap[k] = rowMap[k + 1];
                        }
                    } else if (colHoTenNum === 1 && colLuongNum === 8) {
                        // Mẫu không có STT (Họ tên ở cột 1)
                        colMap[2] = rowMap[1]; // Họ tên
                        colMap[3] = rowMap[2]; // Ngày sinh
                        colMap[4] = rowMap[3]; // Cấp bậc
                        colMap[5] = rowMap[4]; // Chức vụ
                        colMap[6] = rowMap[5]; // Nhập ngũ
                        colMap[7] = rowMap[6]; // Sáp nhập
                        colMap[8] = rowMap[7]; // Nghỉ
                        colMap[9] = rowMap[8]; // Lương
                        for (let k = 10; k <= 35; k++) {
                            if (rowMap[k - 1]) colMap[k] = rowMap[k - 1];
                        }
                    } else {
                        // Mẫu chuẩn mới 26.9.PHU_LUC_SUA.xlsx (1: STT, 2: Họ tên, 3: Ngày sinh, 4: Cấp bậc, 5: Chức vụ, 6: Nhập ngũ, 7: Sáp nhập, 8: Nghỉ, 9: Lương, 10..35: Chế độ)
                        for (let k in rowMap) {
                            colMap[k] = rowMap[k];
                        }
                    }
                    break;
                }
            }
            return { colMap, headerRowIdx };
        }
    };

    // ============================================================
    // DỊCH VỤ ĐỐI CHIẾU VÀ PHÁT HIỆN LỖI TỪNG CỘT
    // ============================================================
    const ValidationService = {
        validateRow(sheetType, rowData) {
            let exp = {};
            let comparisons = [];
            let errorDetails = [];

            if (sheetType === 'I.1') {
                exp = PLI1Calculator.calculateExpected(rowData);
                let raw = rowData.rawCols || {};

                // Cột 10: Tháng nghỉ hưu trước tuổi
                let c10Act = raw[10];
                let c10Exp = exp.cot10;
                let c10Err = (c10Act != null && c10Act !== c10Exp);
                comparisons.push({
                    col: 'Cột 10',
                    title: 'Số tháng nghỉ hưu trước tuổi',
                    actual: c10Act != null ? c10Act + ' tháng' : 'Chưa nhập',
                    expected: c10Exp + ' tháng',
                    diff: (c10Act != null && c10Act !== c10Exp) ? `${c10Act - c10Exp > 0 ? '+' : ''}${c10Act - c10Exp} tháng` : '0 tháng',
                    hasErr: c10Err,
                    formula: `(${formatDate(rowData.ngaySinh)} + ${exp.tran} tuổi) - ${formatDate(rowData.thoiDiemNghi)} + 1 tháng (tính cả 2 đầu tháng)`
                });
                if (c10Err) {
                    errorDetails.push(`Cột 10 (Số tháng nghỉ trước tuổi): File ghi ${c10Act} tháng, Chuẩn tính lại ${c10Exp} tháng (lệch ${c10Act - c10Exp} tháng). Căn cứ trần tuổi ${exp.tran} và thời điểm nghỉ ${formatDate(rowData.thoiDiemNghi)}.`);
                }

                // Cột 11: Năm nghỉ hưu trước tuổi
                let c11Act = raw[11];
                let c11Exp = exp.cot11;
                let c11Err = (c11Act != null && !isEqualTime(c11Act, c11Exp));
                comparisons.push({
                    col: 'Cột 11',
                    title: 'Số năm nghỉ hưu trước tuổi',
                    actual: c11Act != null ? fmtNum(c11Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c11Exp) + ' năm',
                    diff: c11Err ? `${c11Act - c11Exp > 0 ? '+' : ''}${fmtNum(c11Act - c11Exp)} năm` : '0 năm',
                    hasErr: c11Err,
                    formula: `${exp.rawCot10} tháng / 12 = ${(exp.rawCot10 / 12).toFixed(2)} năm -> làm tròn theo quy tắc BQP`
                });
                if (c11Err) {
                    errorDetails.push(`Cột 11 (Số năm nghỉ trước tuổi): File ghi ${fmtNum(c11Act)} năm, Chuẩn tính lại ${fmtNum(c11Exp)} năm.`);
                }

                // Cột 12: Thời gian đóng BHXH
                let c12Act = raw[12];
                let c12Exp = exp.cot12;
                let c12Err = (c12Act != null && !isEqualTime(c12Act, c12Exp));
                comparisons.push({
                    col: 'Cột 12',
                    title: 'Thời gian đóng BHXH',
                    actual: c12Act != null ? fmtNum(c12Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c12Exp) + ' năm',
                    diff: c12Err ? `${c12Act - c12Exp > 0 ? '+' : ''}${fmtNum(c12Act - c12Exp)} năm` : '0 năm',
                    hasErr: c12Err,
                    formula: `${formatDate(rowData.thoiDiemNghi)} - ${formatDate(rowData.nhapNgu)} -> làm tròn theo quy tắc`
                });
                if (c12Err) {
                    errorDetails.push(`Cột 12 (Thời gian đóng BHXH): File ghi ${fmtNum(c12Act)} năm, Chuẩn tính lại ${fmtNum(c12Exp)} năm.`);
                }

                // Cột 13..22: Tiền trợ cấp
                const colDefs = [
                    { k: 13, expKey: 'cot13', title: 'Trợ cấp nghỉ sớm (HS 1.0)', f: `Khống chế ${Math.min(exp.rawCot10, 60)} tháng × 1.0 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 14, expKey: 'cot14', title: 'Trợ cấp nghỉ sớm (HS 0.9)', f: `Khống chế ${Math.min(exp.rawCot10, 60)} tháng × 0.9 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 15, expKey: 'cot15', title: 'Trợ cấp nghỉ sớm (HS 0.5)', f: `Khống chế ${Math.min(exp.rawCot10, 60)} tháng × 0.5 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 16, expKey: 'cot16', title: 'Trợ cấp nghỉ sớm (HS 0.45)', f: `Khống chế ${Math.min(exp.rawCot10, 60)} tháng × 0.45 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 17, expKey: 'cot17', title: 'Trợ cấp tuổi đời (Nhóm 2 - 5 năm)', f: `${fmtNum(exp.cot11)} năm × 5 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 18, expKey: 'cot18', title: `Trợ cấp BHXH (${exp.mocBHXH} năm đầu)`, f: `${exp.hsBHXH} tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 19, expKey: 'cot19', title: `Trợ cấp BHXH (vượt ${exp.mocBHXH} năm)`, f: `0.5 × (${fmtNum(exp.cot12)} - ${exp.mocBHXH}) × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 20, expKey: 'cot20', title: 'Trợ cấp tuổi đời (Nhóm 5 - 10 năm)', f: `${fmtNum(exp.cot11)} năm × 4 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 21, expKey: 'cot21', title: `Trợ cấp BHXH (${exp.mocBHXH} năm đầu)`, f: `${exp.hsBHXH} tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 22, expKey: 'cot22', title: `Trợ cấp BHXH (vượt ${exp.mocBHXH} năm)`, f: `0.5 × (${fmtNum(exp.cot12)} - ${exp.mocBHXH}) × ${fmtMoney(rowData.luongThang)} đ` }
                ];

                // Kiểm tra đơn vị cộng gộp Cột 18 + 19 hoặc Cột 21 + 22 theo quy định BQP (chuẩn Java)
                let c18Act = raw[18] || 0;
                let c19Act = raw[19] || 0;
                let exp18 = exp.cot18 || 0;
                let exp19 = exp.cot19 || 0;
                let exp18Plus19 = exp18 + exp19;
                let isMerged18_19 = (c19Act === 0) && c18Act !== 0 && isEqual(c18Act, exp18Plus19);

                let c21Act = raw[21] || 0;
                let c22Act = raw[22] || 0;
                let exp21 = exp.cot21 || 0;
                let exp22 = exp.cot22 || 0;
                let exp21Plus22 = exp21 + exp22;
                let isMerged21_22 = (c22Act === 0) && c21Act !== 0 && isEqual(c21Act, exp21Plus22);

                colDefs.forEach(def => {
                    let actVal = raw[def.k] || 0;
                    let expVal = exp[def.expKey] || 0;

                    if ((def.k === 18 || def.k === 19) && isMerged18_19) {
                        if (def.k === 18) {
                            comparisons.push({
                                col: 'Cột 18',
                                title: def.title + ' (Cộng gộp Cột 18 + 19)',
                                actual: fmtMoney(actVal) + ' đ',
                                expected: fmtMoney(exp18Plus19) + ' đ',
                                diff: '0 đ',
                                hasErr: false,
                                formula: `Đơn vị cộng gộp Cột 18 (${fmtMoney(exp18)} đ) + Cột 19 (${fmtMoney(exp19)} đ) -> HỢP LỆ`
                            });
                        } else {
                            comparisons.push({
                                col: 'Cột 19',
                                title: def.title + ' (Đã cộng gộp vào Cột 18)',
                                actual: '0 đ',
                                expected: 'Đã gộp',
                                diff: '0 đ',
                                hasErr: false,
                                formula: 'Đã cộng gộp vào Cột 18 -> HỢP LỆ'
                            });
                        }
                        return;
                    }

                    if ((def.k === 21 || def.k === 22) && isMerged21_22) {
                        if (def.k === 21) {
                            comparisons.push({
                                col: 'Cột 21',
                                title: def.title + ' (Cộng gộp Cột 21 + 22)',
                                actual: fmtMoney(actVal) + ' đ',
                                expected: fmtMoney(exp21Plus22) + ' đ',
                                diff: '0 đ',
                                hasErr: false,
                                formula: `Đơn vị cộng gộp Cột 21 (${fmtMoney(exp21)} đ) + Cột 22 (${fmtMoney(exp22)} đ) -> HỢP LỆ`
                            });
                        } else {
                            comparisons.push({
                                col: 'Cột 22',
                                title: def.title + ' (Đã cộng gộp vào Cột 21)',
                                actual: '0 đ',
                                expected: 'Đã gộp',
                                diff: '0 đ',
                                hasErr: false,
                                formula: 'Đã cộng gộp vào Cột 21 -> HỢP LỆ'
                            });
                        }
                        return;
                    }

                    let err = !isEqual(actVal, expVal);
                    comparisons.push({
                        col: 'Cột ' + def.k,
                        title: def.title,
                        actual: fmtMoney(actVal) + ' đ',
                        expected: fmtMoney(expVal) + ' đ',
                        diff: err ? (actVal - expVal > 0 ? '+' : '') + fmtMoney(actVal - expVal) + ' đ' : '0 đ',
                        hasErr: err,
                        formula: expVal > 0 ? def.f : 'Không phát sinh chi trả (0 đ)'
                    });
                    if (err) {
                        errorDetails.push(`Cột ${def.k} (${def.title}): File tính ${fmtMoney(actVal)} đ, Chuẩn tính lại ${fmtMoney(expVal)} đ (lệch ${fmtMoney(actVal - expVal)} đ).`);
                    }
                });

                // Cột 23: Tổng cộng số tiền
                let actualTotal = raw[23] != null ? raw[23] : 0;
                let expectedTotal = exp.total;
                let totalErr = !isEqual(actualTotal, expectedTotal);
                comparisons.push({
                    col: 'Cột 23',
                    title: 'TỔNG CỘNG SỐ TIỀN',
                    actual: fmtMoney(actualTotal) + ' đ',
                    expected: fmtMoney(expectedTotal) + ' đ',
                    diff: totalErr ? (expectedTotal - actualTotal > 0 ? '+' : '') + fmtMoney(expectedTotal - actualTotal) + ' đ' : '0 đ',
                    hasErr: totalErr,
                    formula: 'Cột 13 + 14 + 15 + 16 + 17 + 18 + 19 + 20 + 21 + 22'
                });
                if (totalErr) {
                    errorDetails.push(`Cột 23 (Tổng cộng số tiền): File tính ${fmtMoney(actualTotal)} đ, Chuẩn tính lại ${fmtMoney(expectedTotal)} đ (lệch ${fmtMoney(expectedTotal - actualTotal)} đ).`);
                }

                return {
                    exp,
                    comparisons,
                    errorDetails,
                    actualTotal,
                    expectedTotal,
                    diff: expectedTotal - actualTotal,
                    hasErrors: errorDetails.length > 0,
                    hasDiff24Months: false
                };
            } else if (sheetType === 'I.2') {
                exp = PLI2Calculator.calculateExpected(rowData);
                let raw = rowData.rawCols || {};

                // Cột 10: Số tháng thôi việc
                let c10Act = raw[10];
                let c10Exp = exp.cot10;
                let c10Err = (c10Act != null && c10Act !== c10Exp);
                comparisons.push({
                    col: 'Cột 10',
                    title: 'Số tháng thôi việc',
                    actual: c10Act != null ? c10Act + ' tháng' : 'Chưa nhập',
                    expected: c10Exp + ' tháng',
                    diff: c10Err ? `${c10Act - c10Exp > 0 ? '+' : ''}${c10Act - c10Exp} tháng` : '0 tháng',
                    hasErr: c10Err,
                    formula: `min(Tuổi đời trước trần ${exp.rawThangConLai} tháng, 60 tháng)`
                });
                if (c10Err) {
                    errorDetails.push(`Cột 10 (Số tháng thôi việc): File ghi ${c10Act} tháng, Chuẩn tính lại ${c10Exp} tháng.`);
                }

                // Cột 11: Thời gian đóng BHXH
                let c11Act = raw[11];
                let c11Exp = exp.cot11;
                let c11Err = (c11Act != null && !isEqualTime(c11Act, c11Exp));
                comparisons.push({
                    col: 'Cột 11',
                    title: 'Thời gian đóng BHXH',
                    actual: c11Act != null ? fmtNum(c11Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c11Exp) + ' năm',
                    diff: c11Err ? `${c11Act - c11Exp > 0 ? '+' : ''}${fmtNum(c11Act - c11Exp)} năm` : '0 năm',
                    hasErr: c11Err,
                    formula: `${formatDate(rowData.thoiDiemNghi)} - ${formatDate(rowData.nhapNgu)} -> làm tròn`
                });
                if (c11Err) {
                    errorDetails.push(`Cột 11 (Thời gian đóng BHXH): File ghi ${fmtNum(c11Act)} năm, Chuẩn tính lại ${fmtNum(c11Exp)} năm.`);
                }

                const colDefs2 = [
                    { k: 12, expKey: 'cot12', title: 'Trợ cấp thôi việc ngay (HS 0.8)', f: `${exp.cot10} tháng × 0.8 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 13, expKey: 'cot13', title: 'Trợ cấp theo BHXH (1.5 tháng)', f: `${fmtNum(exp.cot11)} năm × 1.5 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 14, expKey: 'cot14', title: 'Trợ cấp tìm việc làm', f: `3 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 15, expKey: 'cot15', title: 'Trợ cấp sau 12 tháng (HS 0.4)', f: `${exp.cot10} tháng × 0.4 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 16, expKey: 'cot16', title: 'Trợ cấp theo BHXH (1.5 tháng)', f: `${fmtNum(exp.cot11)} năm × 1.5 × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 17, expKey: 'cot17', title: 'Trợ cấp tìm việc làm', f: `3 tháng × ${fmtMoney(rowData.luongThang)} đ` }
                ];

                colDefs2.forEach(def => {
                    let actVal = raw[def.k] || 0;
                    let expVal = exp[def.expKey] || 0;
                    let err = !isEqual(actVal, expVal);
                    comparisons.push({
                        col: 'Cột ' + def.k,
                        title: def.title,
                        actual: fmtMoney(actVal) + ' đ',
                        expected: fmtMoney(expVal) + ' đ',
                        diff: err ? (actVal - expVal > 0 ? '+' : '') + fmtMoney(actVal - expVal) + ' đ' : '0 đ',
                        hasErr: err,
                        formula: expVal > 0 ? def.f : 'Không phát sinh (0 đ)'
                    });
                    if (err) {
                        errorDetails.push(`Cột ${def.k} (${def.title}): File tính ${fmtMoney(actVal)} đ, Chuẩn tính lại ${fmtMoney(expVal)} đ (lệch ${fmtMoney(actVal - expVal)} đ).`);
                    }
                });

                let actualTotal = raw[18] != null ? raw[18] : 0;
                let expectedTotal = exp.total;
                let totalErr = !isEqual(actualTotal, expectedTotal);
                comparisons.push({
                    col: 'Cột 18',
                    title: 'TỔNG CỘNG SỐ TIỀN',
                    actual: fmtMoney(actualTotal) + ' đ',
                    expected: fmtMoney(expectedTotal) + ' đ',
                    diff: totalErr ? (expectedTotal - actualTotal > 0 ? '+' : '') + fmtMoney(expectedTotal - actualTotal) + ' đ' : '0 đ',
                    hasErr: totalErr,
                    formula: 'Cột 12 + 13 + 14 + 15 + 16 + 17'
                });
                if (totalErr) {
                    errorDetails.push(`Cột 18 (Tổng cộng số tiền): File tính ${fmtMoney(actualTotal)} đ, Chuẩn tính lại ${fmtMoney(expectedTotal)} đ (lệch ${fmtMoney(expectedTotal - actualTotal)} đ).`);
                }

                // Cột 19 & Cột 20 (Tổng phụ Cột 18 + Cột 19)
                let c19Act = raw[19] || 0;
                let c20Act = raw[20];
                if (c20Act != null && c20Act !== 0) {
                    let exp20 = expectedTotal + c19Act;
                    let c20Err = !isEqual(c20Act, exp20);
                    comparisons.push({
                        col: 'Cột 20',
                        title: 'TỔNG CỘNG CHUNG (CỘT 18 + CỘT 19)',
                        actual: fmtMoney(c20Act) + ' đ',
                        expected: fmtMoney(exp20) + ' đ',
                        diff: c20Err ? (exp20 - c20Act > 0 ? '+' : '') + fmtMoney(exp20 - c20Act) + ' đ' : '0 đ',
                        hasErr: c20Err,
                        formula: `Cột 18 (${fmtMoney(expectedTotal)} đ) + Cột 19 (${fmtMoney(c19Act)} đ)`
                    });
                    if (c20Err) {
                        errorDetails.push(`Cột 20 (Tổng cộng chung): File tính ${fmtMoney(c20Act)} đ, Chuẩn tính lại ${fmtMoney(exp20)} đ (lệch ${fmtMoney(exp20 - c20Act)} đ).`);
                    }
                }

                // Kiểm tra lệch 24 tháng / 2 năm do sai tuổi trần quân hàm (Chuẩn Java)
                let diff24C10 = Math.abs((raw[10] || 0) - exp.cot10) === 24 || Math.abs((raw[10] || 0) - exp.cot10) === 2;
                let diff24C11 = raw[11] != null && (Math.abs(raw[11] - exp.cot11) === 2.0 || Math.abs(raw[11] - exp.cot11) === 24.0);
                let hasDiff24Months = diff24C10 || diff24C11;

                return {
                    exp,
                    comparisons,
                    errorDetails,
                    actualTotal,
                    expectedTotal,
                    diff: expectedTotal - actualTotal,
                    hasErrors: errorDetails.length > 0,
                    hasDiff24Months: !!hasDiff24Months
                };
            } else if (sheetType === 'I.3') {
                // I.3
                exp = PLI3Calculator.calculateExpected(rowData);
                let raw = rowData.rawCols || {};

                // Cột 10: Thời gian đóng BHXH
                let c10Act = raw[10];
                let c10Exp = exp.cot10;
                let c10Err = (c10Act != null && !isEqualTime(c10Act, c10Exp));
                comparisons.push({
                    col: 'Cột 10',
                    title: 'Thời gian đóng BHXH',
                    actual: c10Act != null ? fmtNum(c10Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c10Exp) + ' năm',
                    diff: c10Err ? `${c10Act - c10Exp > 0 ? '+' : ''}${fmtNum(c10Act - c10Exp)} năm` : '0 năm',
                    hasErr: c10Err,
                    formula: `${formatDate(rowData.thoiDiemNghi)} - ${formatDate(rowData.nhapNgu)} -> làm tròn`
                });
                if (c10Err) {
                    errorDetails.push(`Cột 10 (Thời gian đóng BHXH): File ghi ${fmtNum(c10Act)} năm, Chuẩn tính lại ${fmtNum(c10Exp)} năm.`);
                }

                // Cột 11: Thời gian nghỉ hưu trước tuổi
                let c11Act = raw[11];
                let c11Exp = exp.cot11;
                let c11Err = (c11Act != null && !isEqualTime(c11Act, c11Exp));
                comparisons.push({
                    col: 'Cột 11',
                    title: 'Thời gian nghỉ hưu trước tuổi',
                    actual: c11Act != null ? fmtNum(c11Act) + ' năm' : 'Chưa nhập',
                    expected: fmtNum(c11Exp) + ' năm',
                    diff: c11Err ? `${c11Act - c11Exp > 0 ? '+' : ''}${fmtNum(c11Act - c11Exp)} năm` : '0 năm',
                    hasErr: c11Err,
                    formula: `(${formatDate(rowData.ngaySinh)} + ${exp.tran} tuổi) - ${formatDate(rowData.thoiDiemNghi)} -> làm tròn`
                });
                if (c11Err) {
                    errorDetails.push(`Cột 11 (Thời gian nghỉ trước tuổi): File ghi ${fmtNum(c11Act)} năm, Chuẩn tính lại ${fmtNum(c11Exp)} năm.`);
                }

                const colDefs3 = [
                    { k: 12, expKey: 'cot12', title: 'Trợ cấp nghỉ trước tuổi', f: `${fmtNum(exp.cot11)} năm × 5 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 13, expKey: 'cot13', title: `Trợ cấp ${exp.moc14} năm đầu`, f: `5 tháng × ${fmtMoney(rowData.luongThang)} đ` },
                    { k: 14, expKey: 'cot14', title: `Trợ cấp vượt ${exp.moc14} năm BHXH`, f: `(${fmtNum(exp.cot10)} - ${exp.moc14}) × 0.5 × ${fmtMoney(rowData.luongThang)} đ` }
                ];

                colDefs3.forEach(def => {
                    let actVal = raw[def.k] || 0;
                    let expVal = exp[def.expKey] || 0;
                    let err = !isEqual(actVal, expVal);
                    comparisons.push({
                        col: 'Cột ' + def.k,
                        title: def.title,
                        actual: fmtMoney(actVal) + ' đ',
                        expected: fmtMoney(expVal) + ' đ',
                        diff: err ? (actVal - expVal > 0 ? '+' : '') + fmtMoney(actVal - expVal) + ' đ' : '0 đ',
                        hasErr: err,
                        formula: expVal > 0 ? def.f : 'Không phát sinh (0 đ)'
                    });
                    if (err) {
                        errorDetails.push(`Cột ${def.k} (${def.title}): File tính ${fmtMoney(actVal)} đ, Chuẩn tính lại ${fmtMoney(expVal)} đ (lệch ${fmtMoney(actVal - expVal)} đ).`);
                    }
                });

                let actualTotal = raw[15] != null ? raw[15] : 0;
                let expectedTotal = exp.total;
                let totalErr = !isEqual(actualTotal, expectedTotal);
                comparisons.push({
                    col: 'Cột 15',
                    title: 'TỔNG CỘNG SỐ TIỀN',
                    actual: fmtMoney(actualTotal) + ' đ',
                    expected: fmtMoney(expectedTotal) + ' đ',
                    diff: totalErr ? (expectedTotal - actualTotal > 0 ? '+' : '') + fmtMoney(expectedTotal - actualTotal) + ' đ' : '0 đ',
                    hasErr: totalErr,
                    formula: 'Cột 12 + 13 + 14'
                });
                if (totalErr) {
                    errorDetails.push(`Cột 15 (Tổng cộng số tiền): File tính ${fmtMoney(actualTotal)} đ, Chuẩn tính lại ${fmtMoney(expectedTotal)} đ (lệch ${fmtMoney(expectedTotal - actualTotal)} đ).`);
                }

                let diff24C10 = raw[10] != null && (Math.abs(raw[10] - exp.cot10) === 2.0 || Math.abs(raw[10] - exp.cot10) === 24.0);
                let diff24C11 = raw[11] != null && (Math.abs(raw[11] - exp.cot11) === 2.0 || Math.abs(raw[11] - exp.cot11) === 24.0);
                let hasDiff24Months = diff24C10 || diff24C11;

                return {
                    exp,
                    comparisons,
                    errorDetails,
                    actualTotal,
                    expectedTotal,
                    diff: expectedTotal - actualTotal,
                    hasErrors: errorDetails.length > 0,
                    hasDiff24Months: !!hasDiff24Months
                };
            } else if (sheetType === 'I.5') {
                exp = PLI5Calculator.calculateExpected(rowData);
                let raw = rowData.rawCols || {};

                const colDefs5 = [
                    { k: 13, expKey: 'cot13', title: 'Chênh lệch bảo lưu (tiền)', f: `Cột 7 (${fmtNum(rowData.heSoChenhLechBaoLuu || raw[7])}) × 2.340.000 đ` },
                    { k: 14, expKey: 'cot14', title: 'Tiền lương ngạch bậc', f: `Cột 6 (${fmtNum(rowData.heSoLuong || raw[6])}) × 2.340.000 đ` },
                    { k: 15, expKey: 'cot15', title: 'Phụ cấp chức vụ', f: `Cột 8 (${fmtNum(rowData.heSoChucVu || raw[8])}) × 2.340.000 đ` },
                    { k: 16, expKey: 'cot16', title: 'Phụ cấp thâm niên nghề', f: `Thâm niên ${exp.soNamThamNien} năm (${fmtNum(exp.tiLeThamNien * 100)}%) × (${fmtMoney(exp.cot14)} + ${fmtMoney(exp.cot15)}) đ` },
                    { k: 17, expKey: 'cot17', title: 'Phụ cấp thâm niên vượt khung', f: `Đọc từ file Excel` },
                    { k: 18, expKey: 'cot18', title: 'Phụ cấp trách nhiệm nghề', f: `${fmtNum(exp.tiLeC11 * 100)}% × (${fmtMoney(exp.cot14)} + ${fmtMoney(exp.cot15)} + ${fmtMoney(exp.cot16)}) đ` },
                    { k: 19, expKey: 'cot19', title: 'Phụ cấp công vụ', f: `25% × (${fmtMoney(exp.cot14)} + ${fmtMoney(exp.cot15)} + ${fmtMoney(exp.cot16)}) đ` },
                    { k: 20, expKey: 'cot20', title: 'Phụ cấp đặc thù quân sự cơ yếu', f: `${fmtNum(exp.tiLeC12 * 100)}% × (${fmtMoney(exp.cot14)} + ${fmtMoney(exp.cot15)} + ${fmtMoney(exp.cot16)}) đ` },
                    { k: 21, expKey: 'cot21', title: 'Được nhận khác', f: `Đọc từ file Excel` },
                    { k: 22, expKey: 'cot22', title: 'TỔNG CỘNG LƯƠNG & PHỤ CẤP', f: `SUM(Cột 13 : Cột 21)` }
                ];

                colDefs5.forEach(def => {
                    let actVal = raw[def.k] || 0;
                    let expVal = exp[def.expKey] || 0;

                    if ((def.k === 17 || def.k === 21) && expVal === 0 && actVal > 0) {
                        expVal = actVal;
                        exp[def.expKey] = actVal;
                    }

                    let err = !isEqual(actVal, expVal);
                    comparisons.push({
                        col: 'Cột ' + def.k,
                        title: def.title,
                        actual: fmtMoney(actVal) + ' đ',
                        expected: fmtMoney(expVal) + ' đ',
                        diff: err ? (actVal - expVal > 0 ? '+' : '') + fmtMoney(actVal - expVal) + ' đ' : '0 đ',
                        hasErr: err,
                        formula: def.f
                    });
                    if (err) {
                        errorDetails.push(`Cột ${def.k} (${def.title}): File tính ${fmtMoney(actVal)} đ, Chuẩn tính lại ${fmtMoney(expVal)} đ (lệch ${fmtMoney(actVal - expVal)} đ).`);
                    }
                });

                let actualTotal = raw[22] != null ? raw[22] : 0;
                let expectedTotal = exp.total;
                let totalErr = !isEqual(actualTotal, expectedTotal);

                return {
                    exp,
                    comparisons,
                    errorDetails,
                    actualTotal,
                    expectedTotal,
                    diff: expectedTotal - actualTotal,
                    hasErrors: errorDetails.length > 0,
                    hasDiff24Months: false
                };
            }
        }
    };

    return {
        LCS,
        MilitaryRankHelper,
        PLI1Calculator,
        PLI2Calculator,
        PLI3Calculator,
        PLI5Calculator,
        ExcelParser,
        ValidationService,
        fmtMoney,
        fmtNum,
        formatDate,
        calcThang,
        calcNamLamTron,
        isEqual,
        isEqualTime,
        readNumberToVietnameseWords
    };
})();

// ============================================================
// GIAO DIỆN THẨM ĐỊNH VÀ ĐỐI CHIẾU DỮ LIỆU (UI CONTROLLER)
// ============================================================
window.ValidationUI = {
    currentFile: null,
    currentResult: null,
    currentRecords: [],
    currentFiltered: [],
    currentModalIndex: -1,

    async handleFileUpload(event) {
        let file = null;
        if (typeof File !== 'undefined' && event instanceof File) {
            file = event;
        } else if (typeof Blob !== 'undefined' && event instanceof Blob) {
            file = event;
        } else if (event && event.target && event.target.files) {
            file = event.target.files[0];
        } else if (event && event.dataTransfer && event.dataTransfer.files) {
            file = event.dataTransfer.files[0];
        }
        if (!file) return;

        let loadingEl = document.getElementById("validate_loading");
        let dropzone = document.getElementById("validate_dropzone");
        if (loadingEl) loadingEl.style.display = "block";
        if (dropzone) dropzone.style.display = "none";

        try {
            this.currentFile = file;
            const arrayBuffer = await file.arrayBuffer();

            const workbook = new ExcelJS.Workbook();
            await workbook.xlsx.load(arrayBuffer);
            this.currentWb = workbook;

            let allRecords = [];
            let sheetStats = { i1: 0, i2: 0, i3: 0, i5: 0 };

            workbook.eachSheet((worksheet) => {
                let sheetName = worksheet.name.toLowerCase();
                let type = null;
                if (sheetName.includes('i.5') || sheetName.includes('i5') || sheetName.includes('pl5') || sheetName.includes('pl i.5')) type = 'I.5';
                else if (sheetName.includes('i.1') || sheetName.includes('i1') || sheetName.includes('pl1') || sheetName.includes('pl i.1')) type = 'I.1';
                else if (sheetName.includes('i.2') || sheetName.includes('i2') || sheetName.includes('pl2') || sheetName.includes('pl i.2')) type = 'I.2';
                else if (sheetName.includes('i.3') || sheetName.includes('i3') || sheetName.includes('pl3') || sheetName.includes('pl i.3')) type = 'I.3';

                if (!type) return;

                const { colMap, headerRowIdx } = BQPValidation.ExcelParser.findColMap(worksheet);
                let startRow = headerRowIdx !== -1 ? headerRowIdx + 1 : (type === 'I.5' ? 6 : 11);
                let currentGroup = worksheet.name;

                for (let r = startRow; r <= worksheet.rowCount; r++) {
                    let row = worksheet.getRow(r);
                    let col2Idx = colMap[2] || 2;
                    let hoten = BQPValidation.ExcelParser.getCellText(row.getCell(col2Idx));
                    if (!hoten && colMap[1]) {
                        hoten = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[1]));
                    }
                    if (!hoten) continue;

                    let lower = hoten.toLowerCase();
                    if (lower.includes('cộng') || lower.includes('tổng cộng')) break;

                    let rowData = null;

                    if (type === 'I.5') {
                        let donVi = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[3] || 3));
                        let chucVu = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[4] || 4));
                        let capBac = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[5] || 5));
                        let heSoLuong = BQPValidation.ExcelParser.parseNumber(row.getCell(colMap[6] || 6));

                        if (!capBac && !heSoLuong) {
                            if (lower !== 'sĩ quan' && lower !== 'qncn' && !lower.includes('quân nhân') && isNaN(hoten)) {
                                currentGroup = hoten;
                            }
                            continue;
                        }

                        let heSoChenhLechBaoLuu = BQPValidation.ExcelParser.parseNumber(row.getCell(colMap[7] || 7));
                        let heSoChucVu = BQPValidation.ExcelParser.parseNumber(row.getCell(colMap[8] || 8));
                        let nhapNgu = BQPValidation.ExcelParser.parseDateCell(row.getCell(colMap[9] || 9));
                        let thoiDiemNghi = BQPValidation.ExcelParser.parseDateCell(row.getCell(colMap[10] || 10));
                        let tiLePhuCapTrachNhiem = BQPValidation.ExcelParser.parseNumber(row.getCell(colMap[11] || 11));
                        let tiLePhuCapDacThu = BQPValidation.ExcelParser.parseNumber(row.getCell(colMap[12] || 12));

                        let rawCols = {};
                        for (let k = 6; k <= 22; k++) {
                            if (colMap[k]) {
                                rawCols[k] = BQPValidation.ExcelParser.parseNumber(row.getCell(colMap[k]));
                            }
                        }

                        rowData = {
                            id: 'val_' + Date.now() + '_' + Math.floor(Math.random() * 100000),
                            rowIndex: r,
                            group: donVi || currentGroup,
                            sheet: type,
                            fileName: file.name,
                            hoTen: hoten,
                            capBac: capBac,
                            chucVu: chucVu,
                            donVi: donVi,
                            heSoLuong: heSoLuong,
                            heSoChenhLechBaoLuu: heSoChenhLechBaoLuu,
                            heSoChucVu: heSoChucVu,
                            ngaySinh: null,
                            nhapNgu: nhapNgu,
                            sapNhap: null,
                            thoiDiemNghi: thoiDiemNghi,
                            tiLePhuCapTrachNhiem: tiLePhuCapTrachNhiem,
                            tiLePhuCapDacThu: tiLePhuCapDacThu,
                            luongThang: rawCols[22] || (heSoLuong * 2340000),
                            rawCols: rawCols
                        };
                    } else {
                        let capBac = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[4] || 4));
                        if (!capBac || capBac.trim() === '') {
                            if (lower !== 'sĩ quan' && lower !== 'qncn' && !lower.includes('năm 20') && !lower.includes('quân nhân') && isNaN(hoten)) {
                                currentGroup = hoten;
                            }
                            continue;
                        }

                        let ngaySinh = BQPValidation.ExcelParser.parseDateCell(row.getCell(colMap[3] || 3));
                        let chucVu = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[5] || 5));
                        let nhapNgu = BQPValidation.ExcelParser.parseDateCell(row.getCell(colMap[6] || 6));
                        let sapNhap = BQPValidation.ExcelParser.parseDateCell(row.getCell(colMap[7] || 7));
                        let thoiDiemNghi = BQPValidation.ExcelParser.parseDateCell(row.getCell(colMap[8] || 8));
                        let luongThang = BQPValidation.ExcelParser.parseNumber(row.getCell(colMap[9] || 9));

                        let rawCols = {};
                        for (let k = 10; k <= 25; k++) {
                            if (colMap[k]) {
                                rawCols[k] = BQPValidation.ExcelParser.parseNumber(row.getCell(colMap[k]));
                            }
                        }

                        rowData = {
                            id: 'val_' + Date.now() + '_' + Math.floor(Math.random() * 100000),
                            rowIndex: r,
                            group: currentGroup,
                            sheet: type,
                            fileName: file.name,
                            hoTen: hoten,
                            capBac: capBac,
                            chucVu: chucVu,
                            ngaySinh: ngaySinh,
                            nhapNgu: nhapNgu,
                            sapNhap: sapNhap,
                            thoiDiemNghi: thoiDiemNghi,
                            luongThang: luongThang,
                            rawCols: rawCols
                        };
                    }

                    let valRes = BQPValidation.ValidationService.validateRow(type, rowData);

                    rowData.valRes = valRes;
                    rowData.hasErrors = valRes.hasErrors;
                    rowData.hasDiff24Months = valRes.hasDiff24Months || false;
                    rowData.errorDetails = valRes.errorDetails;
                    rowData.comparisons = valRes.comparisons;
                    rowData.expected = valRes.exp;
                    rowData.tongTienThucTe = valRes.actualTotal;
                    rowData.tongTienTinhLai = valRes.expectedTotal;
                    rowData.diff = valRes.diff;

                    allRecords.push(rowData);

                    if (type === 'I.1') sheetStats.i1++;
                    else if (type === 'I.2') sheetStats.i2++;
                    else if (type === 'I.3') sheetStats.i3++;
                    else if (type === 'I.5') sheetStats.i5 = (sheetStats.i5 || 0) + 1;
                }
            });

            this.currentRecords = allRecords;
            this.currentResult = {
                records: allRecords,
                sheetStats: sheetStats
            };

            let wrap = document.getElementById("validate_result_wrap");
            if (wrap) wrap.style.display = "block";

            this.renderTable();

        } catch (err) {
            console.error("Lỗi khi đọc file Excel:", err);
            alert("Có lỗi khi đọc file Excel: " + err.message);
            if (dropzone) dropzone.style.display = "block";
        } finally {
            if (loadingEl) loadingEl.style.display = "none";
            event.target.value = "";
        }
    },

    setFilterStatus(status) {
        let sel = document.getElementById('vfilter_status');
        if (sel) sel.value = status;
        this.renderTable();
    },

    renderTable() {
        let container = document.getElementById("vtable_container");
        if (!container) return;

        let records = this.currentRecords || [];

        let filterSheet = document.getElementById('vfilter_sheet') ? document.getElementById('vfilter_sheet').value : '';
        let filterStatus = document.getElementById('vfilter_status') ? document.getElementById('vfilter_status').value : '';
        let filterSearch = document.getElementById('vfilter_search') ? document.getElementById('vfilter_search').value.toLowerCase().trim() : '';

        // Thống kê tổng số lượng theo từng nhóm trạng thái
        let totalCount = records.length;
        let validCount = 0;
        let errCount = 0;
        let underCount = 0;
        let underMoney = 0;
        let overCount = 0;
        let overMoney = 0;
        let otherCount = 0;

        records.forEach(r => {
            let diff = (r.tongTienTinhLai != null && r.tongTienThucTe != null) ? (r.tongTienTinhLai - r.tongTienThucTe) : (r.diff || 0);
            let isErr = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0);
            if (!isErr) {
                validCount++;
            } else {
                errCount++;
                if (diff > 1000) {
                    underCount++;
                    underMoney += diff;
                } else if (diff < -1000) {
                    overCount++;
                    overMoney += Math.abs(diff);
                } else {
                    otherCount++;
                }
            }
        });

        let filtered = records.filter(r => {
            if (filterSheet && r.sheet !== filterSheet) return false;
            let diff = (r.tongTienTinhLai != null && r.tongTienThucTe != null) ? (r.tongTienTinhLai - r.tongTienThucTe) : (r.diff || 0);
            let isErr = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0);
            let isDiffUnder = isErr && diff > 1000;
            let isDiffOver = isErr && diff < -1000;
            let isDiffOther = isErr && !isDiffUnder && !isDiffOver;

            if ((filterStatus === 'valid' || filterStatus === 'ok') && isErr) return false;
            if ((filterStatus === 'error' || filterStatus === 'err') && !isErr) return false;
            if (filterStatus === 'error_under' && (!isErr || !isDiffUnder)) return false;
            if (filterStatus === 'error_over' && (!isErr || !isDiffOver)) return false;
            if (filterStatus === 'error_other' && (!isErr || !isDiffOther)) return false;

            if (filterSearch) {
                let name = (r.hoTen || '').toLowerCase();
                let rank = (r.capBac || '').toLowerCase();
                let unit = (r.group || '').toLowerCase();
                if (!name.includes(filterSearch) && !rank.includes(filterSearch) && !unit.includes(filterSearch)) return false;
            }
            return true;
        });

        this.currentFiltered = filtered;

        if (filtered.length === 0) {
            container.innerHTML = `
                <div style="padding:40px 20px; text-align:center; color:#64748b; background:#ffffff; border:1px solid #e2e8f0; border-radius:6px;">
                    <div style="font-size:15px; font-weight:600; color:#1e293b; margin-bottom:4px;">Không tìm thấy hồ sơ phù hợp</div>
                    <div style="font-size:13px;">Hãy thử thay đổi điều kiện lọc hoặc từ khóa tìm kiếm.</div>
                </div>`;
            let btnExport = document.querySelector('button[onclick="ValidationUI.exportExcel()"]');
            if (btnExport) btnExport.textContent = 'Xuất file Excel (0 hồ sơ)';
            return;
        }

        // Nhóm theo Đơn vị
        let groups = {};
        filtered.forEach(r => {
            let g = r.group || 'Đơn vị không xác định';
            if (!groups[g]) groups[g] = [];
            groups[g].push(r);
        });

        let html = `
            <div style="margin-bottom:12px; font-size:13px; color:#475569;">
                Hiển thị <strong>${filtered.length}</strong> / ${totalCount} hồ sơ thẩm định (${errCount} hồ sơ có sai sót)
            </div>

            <table class="records-table" style="width:100%; border-collapse:collapse; background:#ffffff; border:1px solid #cbd5e1; border-radius:6px; overflow:hidden;">
                <thead>
                    <tr style="background:#f8fafc; border-bottom:2px solid #cbd5e1; color:#334155; font-size:12.5px; text-transform:uppercase;">
                        <th style="width:45px; text-align:center; padding:10px 8px;">STT</th>
                        <th style="width:75px; text-align:center; padding:10px 8px;">Phụ lục</th>
                        <th style="padding:10px 14px; text-align:left;">Họ và tên quân nhân</th>
                        <th style="width:150px; padding:10px 14px; text-align:left;">Cấp bậc / Chức vụ</th>
                        <th style="width:140px; text-align:center; padding:10px 8px;">Trạng thái thẩm định</th>
                        <th style="min-width:140px; text-align:right; padding:10px 14px; white-space:nowrap;">Tiền trên Excel</th>
                        <th style="min-width:140px; text-align:right; padding:10px 14px; white-space:nowrap;">Chuẩn tính lại</th>
                        <th style="min-width:155px; text-align:right; padding:10px 14px; white-space:nowrap;">Chênh lệch</th>
                        <th style="width:100px; text-align:center; padding:10px 8px;">Thao tác</th>
                    </tr>
                </thead>
                <tbody>`;

        let stt = 1;
        for (let groupName in groups) {
            let groupRecords = groups[groupName];

            html += `
                <tr style="background:#f1f5f9; border-top:2px solid #cbd5e1; border-bottom:1px solid #cbd5e1;">
                    <td colspan="9" style="padding:9px 14px;">
                        <div style="font-weight:700; color:#0f172a; font-size:13.5px;">
                            Đơn vị: ${groupName}
                        </div>
                    </td>
                </tr>`;

            groupRecords.forEach(r => {
                let diff = (r.tongTienTinhLai != null && r.tongTienThucTe != null) ? (r.tongTienTinhLai - r.tongTienThucTe) : (r.diff || 0);
                let diffStr = diff === 0 ? '---' : (diff > 0 ? '+' + BQPValidation.fmtMoney(diff) + '&nbsp;đ' : BQPValidation.fmtMoney(diff) + '&nbsp;đ');
                let diffColor = diff === 0 ? '#64748b' : '#b91c1c';

                let isErr = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0);
                let statusBadge = isErr
                    ? `<span style="display:inline-block; padding:3px 8px; border-radius:4px; font-size:11.5px; font-weight:600; background:#fef2f2; color:#b91c1c; border:1px solid #fecaca;">Lệch chuẩn</span>`
                    : `<span style="display:inline-block; padding:3px 8px; border-radius:4px; font-size:11.5px; font-weight:600; background:#f0fdf4; color:#15803d; border:1px solid #bbf7d0;">Đạt chuẩn</span>`;

                let nsStr = BQPValidation.formatDate(r.ngaySinh) || '---';
                let nnStr = BQPValidation.formatDate(r.nhapNgu) || '---';

                html += `
                    <tr style="border-bottom:1px solid #f1f5f9; transition:background 0.15s;">
                        <td style="text-align:center; color:#64748b; font-size:12.5px; padding:10px 8px;">${stt++}</td>
                        <td style="text-align:center; padding:10px 8px;">
                            <span style="font-size:11.5px; font-weight:700; color:#1e40af; background:#eff6ff; border:1px solid #bfdbfe; padding:2px 6px; border-radius:4px;">${r.sheet}</span>
                        </td>
                        <td style="text-align:left; padding:10px 14px;">
                            <div style="font-weight:700; color:#0f172a; font-size:13.5px; margin-bottom:2px;">${r.hoTen}</div>
                            <div style="font-size:11.5px; color:#64748b;">Sinh: ${nsStr} &nbsp;•&nbsp; Nhập ngũ: ${nnStr}</div>
                        </td>
                        <td style="text-align:left; padding:10px 14px;">
                            <div style="font-weight:600; color:#334155; font-size:12.5px;">${r.capBac || '---'}</div>
                            <div style="font-size:11.5px; color:#64748b;">${r.chucVu || '---'}</div>
                        </td>
                        <td style="text-align:center; padding:10px 8px;">
                            ${statusBadge}
                        </td>
                        <td style="text-align:right; font-weight:600; color:#334155; font-size:13px; padding:10px 14px; white-space:nowrap;">
                            ${r.tongTienThucTe ? BQPValidation.fmtMoney(r.tongTienThucTe) + '&nbsp;đ' : '---'}
                        </td>
                        <td style="text-align:right; font-weight:700; color:#15803d; font-size:13px; padding:10px 14px; white-space:nowrap;">
                            ${BQPValidation.fmtMoney(r.tongTienTinhLai)}&nbsp;đ
                        </td>
                        <td style="text-align:right; font-weight:700; color:${diffColor}; font-size:13px; padding:10px 14px; white-space:nowrap;">
                            ${diffStr}
                        </td>
                        <td style="text-align:center; padding:10px 8px;">
                            <button type="button" onclick="ValidationUI.openCompareModal('${r.id}')" title="Đối chiếu chi tiết từng cột" style="padding:4px 12px; font-size:12px; font-weight:600; background:#f8fafc; color:#1e3a5f; border:1px solid #cbd5e1; border-radius:4px; cursor:pointer;">
                                Đối chiếu
                            </button>
                        </td>
                    </tr>`;
            });
        }

        html += `</tbody></table>`;
        container.innerHTML = html;

        let btnExport = document.querySelector('button[onclick="ValidationUI.exportExcel()"]');
        if (btnExport) {
            if (filtered.length < totalCount) {
                btnExport.textContent = `Xuất file Excel (${filtered.length})`;
                btnExport.title = `Xuất ${filtered.length} hồ sơ đã lọc ra file Excel`;
            } else {
                btnExport.textContent = 'Xuất file Excel báo cáo';
            }
        }
    },

    openCompareModal(recordId, isExcelOnly = false) {
        let r = null;
        let list = this.currentFiltered.length > 0 ? this.currentFiltered : this.currentRecords;

        if (typeof recordId === 'number') {
            this.currentModalIndex = recordId;
            r = list[this.currentModalIndex];
        } else {
            this.currentModalIndex = list.findIndex(x => x.id === recordId);
            r = list[this.currentModalIndex];
        }

        // Nếu không có trong danh sách hiện tại (ví dụ bấm từ tab Danh sách đối tượng)
        if (!r && typeof StorageManager !== 'undefined') {
            let src = isExcelOnly ? 'excel' : 'validated';
            let recs = StorageManager.getRecordsBySource(src);
            r = recs.find(x => x.id === recordId);
            if (r) {
                list = recs;
                this.currentRecords = recs;
                this.currentFiltered = recs;
                this.currentModalIndex = list.findIndex(x => x.id === recordId);
            }
        }

        if (!r) {
            alert('Không tìm thấy thông tin hồ sơ.');
            return;
        }

        let modal = document.getElementById("valErrorModal");
        let bodyEl = document.getElementById("valModalBody");
        let titleEl = document.getElementById("valModalTitle");
        let navEl = document.getElementById("valModalNav");
        if (!modal || !bodyEl) return;

        let totalRecords = list.length;
        let idx = this.currentModalIndex;

        let sheetKey = r.sheet || r.sheetSource || 'I.1';

        if (titleEl) {
            titleEl.textContent = isExcelOnly
                ? `THÔNG TIN CHI TIẾT HỒ SƠ QUÂN NHÂN: ${r.hoTen} (${r.capBac || '---'}) — Phụ lục ${sheetKey}`
                : `BẢNG ĐỐI CHIẾU CHI TIẾT CHỈ TIÊU & SAI SÓT: ${r.hoTen} (${r.capBac || '---'}) — Phụ lục ${sheetKey}`;
        }

        // Tái tạo hoặc lấy bảng so sánh từng cột
        let comparisons = r.comparisons;
        let errorDetails = r.errorDetails || [];

        if (!comparisons || comparisons.length === 0) {
            let valRes = BQPValidation.ValidationService.validateRow(sheetKey, r);
            comparisons = valRes.comparisons;
            errorDetails = valRes.errorDetails;
        }

        let dNgaySinh = BQPValidation.formatDate(r.ngaySinh) || '---';
        let dNhapNgu = BQPValidation.formatDate(r.nhapNgu) || '---';
        let dSapNhap = BQPValidation.formatDate(r.sapNhap) || '---';
        let dNghi = BQPValidation.formatDate(r.thoiDiemNghi) || '---';
        let tranTuoi = r.expected ? r.expected.tran : BQPValidation.MilitaryRankHelper.getTran(r.capBac, r.chucVu);

        // Khung cảnh báo sai lệch (Chỉ hiển thị khi thẩm định / đối chiếu, ẩn khi xem hồ sơ Excel)
        let alertBoxHtml = '';
        if (!isExcelOnly) {
            if (r.hasErrors && errorDetails.length > 0) {
                alertBoxHtml = `
                    <div style="background:#fef2f2; border:1px solid #fecaca; border-left:4px solid #b91c1c; border-radius:4px; padding:12px 16px; margin-bottom:16px;">
                        <div style="font-weight:700; color:#991b1b; font-size:13.5px; margin-bottom:6px;">
                            Phát hiện ${errorDetails.length} điểm sai lệch so với chuẩn của Bộ Quốc Phòng:
                        </div>
                        <ul style="margin:0; padding-left:18px; color:#991b1b; font-size:12.5px; line-height:1.6;">
                            ${errorDetails.map(err => `<li><strong>${err}</strong></li>`).join('')}
                        </ul>
                    </div>`;
            } else {
                alertBoxHtml = `
                    <div style="background:#f0fdf4; border:1px solid #bbf7d0; border-left:4px solid #16a34a; border-radius:4px; padding:12px 16px; margin-bottom:16px;">
                        <div style="font-weight:700; color:#166534; font-size:13.5px;">
                            Hồ sơ đạt chuẩn 100%, khớp hoàn toàn giữa file Excel và quy định của Bộ Quốc Phòng.
                        </div>
                    </div>`;
            }
        }

        let totalExpected = r.tongTienTinhLai || 0;
        let totalActual = r.tongTienThucTe || (r.rawCols ? (r.rawCols[23] || r.rawCols[18] || r.rawCols[15] || 0) : 0);
        let totalDiff = totalExpected - totalActual;

        // Khối tổng kinh phí
        let summaryMoneyHtml = '';
        if (isExcelOnly) {
            summaryMoneyHtml = `
                <div style="background:#f0fdf4; border:1px solid #86efac; border-radius:6px; padding:14px; text-align:center; margin-bottom:12px;">
                    <div style="font-size:12px; color:#15803d; font-weight:700; text-transform:uppercase; letter-spacing:0.5px;">Tổng kinh phí (File Excel)</div>
                    <div style="font-size:22px; font-weight:800; color:#15803d; margin-top:4px;">${BQPValidation.fmtMoney(totalActual)} đ</div>
                </div>
            `;
        } else {
            summaryMoneyHtml = `
                <div style="display:grid; grid-template-columns:repeat(3, 1fr); gap:12px; margin-bottom:16px;">
                    <div style="background:#f8fafc; border:1px solid #cbd5e1; border-radius:6px; padding:12px; text-align:center;">
                        <div style="font-size:12px; color:#64748b; font-weight:600;">TỔNG TIỀN FILE EXCEL</div>
                        <div style="font-size:17px; font-weight:700; color:#334155; margin-top:4px;">${BQPValidation.fmtMoney(totalActual)} đ</div>
                    </div>
                    <div style="background:#f0fdf4; border:1px solid #86efac; border-radius:6px; padding:12px; text-align:center;">
                        <div style="font-size:12px; color:#15803d; font-weight:600;">THẨM ĐỊNH TÍNH LẠI</div>
                        <div style="font-size:17px; font-weight:700; color:#15803d; margin-top:4px;">${BQPValidation.fmtMoney(totalExpected)} đ</div>
                    </div>
                    <div style="background:${totalDiff !== 0 ? '#fef2f2' : '#f8fafc'}; border:1px solid ${totalDiff !== 0 ? '#fca5a5' : '#cbd5e1'}; border-radius:6px; padding:12px; text-align:center;">
                        <div style="font-size:12px; color:${totalDiff !== 0 ? '#b91c1c' : '#64748b'}; font-weight:600;">CHÊNH LỆCH</div>
                        <div style="font-size:17px; font-weight:700; color:${totalDiff !== 0 ? '#b91c1c' : '#64748b'}; margin-top:4px;">
                            ${totalDiff === 0 ? '0 đ' : (totalDiff > 0 ? '+' + BQPValidation.fmtMoney(totalDiff) + ' đ' : BQPValidation.fmtMoney(totalDiff) + ' đ')}
                        </div>
                    </div>
                </div>
            `;
        }

        // Bằng chữ
        let chuTienHtml = '';
        if (isExcelOnly) {
            chuTienHtml = `
                <div style="font-size:13px; color:#475569; margin-bottom:14px;">
                    Số tiền bằng chữ: <strong style="color:#0f172a;">${BQPValidation.readNumberToVietnameseWords(totalActual)}</strong>
                </div>
            `;
        } else {
            chuTienHtml = `
                <div style="font-size:13px; color:#475569; margin-bottom:16px;">
                    Số tiền bằng chữ (Thẩm định): <strong style="color:#0f172a;">${BQPValidation.readNumberToVietnameseWords(totalExpected)}</strong>
                </div>
            `;
        }

        // Bảng dữ liệu: Nếu là Hồ sơ Excel -> chỉ hiển thị các cột nhập từ Excel; Nếu là Thẩm định -> bảng đối chiếu 6 cột
        let tableBlockHtml = '';
        if (isExcelOnly) {
            let tableRowsHtml = comparisons.map(c => {
                let isTotal = c.col === 'Cột 23' || c.col === 'Cột 18' || c.col === 'Cột 15' || c.col === 'Cột 20';
                return `
                    <tr style="${isTotal ? 'background:#f0fdf4; font-weight:700;' : ''} border-bottom:1px solid #f1f5f9;">
                        <td style="text-align:center; font-weight:700; color:#1e3a5f; padding:9px 10px; width:75px;">${c.col}</td>
                        <td style="padding:9px 10px; color:#1e293b; font-weight:${isTotal ? '700' : '500'};">${c.title}</td>
                        <td style="text-align:right; padding:9px 14px; font-weight:${isTotal ? '800' : '600'}; color:${isTotal ? '#15803d' : '#334155'}; font-size:${isTotal ? '13.5' : '13'}px; width:220px;">${c.actual}</td>
                    </tr>`;
            }).join('');

            tableBlockHtml = `
                <div style="border:1px solid #cbd5e1; border-radius:6px; overflow:hidden;">
                    <table style="width:100%; border-collapse:collapse; font-size:12.5px;">
                        <thead>
                            <tr style="background:#f8fafc; border-bottom:2px solid #cbd5e1; color:#334155; font-weight:700; font-size:12px; text-transform:uppercase;">
                                <th style="width:75px; text-align:center; padding:9px 10px;">Cột</th>
                                <th style="padding:9px 10px; text-align:left;">Nội dung chỉ tiêu chế độ</th>
                                <th style="width:220px; text-align:right; padding:9px 14px;">Số tiền / Giá trị (File Excel)</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${tableRowsHtml}
                        </tbody>
                    </table>
                </div>
            `;
        } else {
            let tableRowsHtml = comparisons.map(c => {
                let rowStyle = c.hasErr ? 'background:#fff8f8; border-left:3px solid #ef4444;' : '';
                return `
                    <tr style="${rowStyle} border-bottom:1px solid #e2e8f0;">
                        <td style="text-align:center; font-weight:700; color:#1e3a5f; padding:8px 10px;">${c.col}</td>
                        <td style="padding:8px 10px; font-weight:600; color:#1e293b;">${c.title}</td>
                        <td style="text-align:right; padding:8px 10px; font-weight:600; color:${c.hasErr ? '#b91c1c' : '#334155'}; font-size:13px; white-space:nowrap;">${c.actual}</td>
                        <td style="text-align:right; padding:8px 10px; font-weight:700; color:#15803d; font-size:13px; white-space:nowrap;">${c.expected}</td>
                        <td style="text-align:right; padding:8px 10px; font-weight:700; color:${c.hasErr ? '#b91c1c' : '#64748b'}; font-size:12.5px; white-space:nowrap;">${c.diff}</td>
                        <td style="padding:8px 10px; font-size:12px; color:#475569;">${c.formula}</td>
                    </tr>`;
            }).join('');

            tableBlockHtml = `
                <div style="border:1px solid #cbd5e1; border-radius:6px; overflow:hidden;">
                    <table style="width:100%; border-collapse:collapse; font-size:12.5px;">
                        <thead>
                            <tr style="background:#f8fafc; border-bottom:2px solid #cbd5e1; color:#334155; font-weight:700;">
                                <th style="width:75px; text-align:center; padding:9px 10px;">Cột</th>
                                <th style="padding:9px 10px; text-align:left;">Nội dung chỉ tiêu chế độ</th>
                                <th style="width:145px; text-align:right; padding:9px 10px; white-space:nowrap;">File Excel</th>
                                <th style="width:145px; text-align:right; padding:9px 10px; white-space:nowrap;">Thẩm định</th>
                                <th style="width:130px; text-align:right; padding:9px 10px; white-space:nowrap;">Chênh lệch</th>
                                <th style="padding:9px 10px; text-align:left;">Công thức quy định</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${tableRowsHtml}
                        </tbody>
                    </table>
                </div>
            `;
        }

        bodyEl.innerHTML = `
            ${alertBoxHtml}

            <!-- Thẻ thông tin quân nhân -->
            <div style="background:#f8fafc; border:1px solid #cbd5e1; border-radius:6px; padding:12px 16px; margin-bottom:14px;">
                ${sheetKey === 'I.5' ? `
                <div style="display:grid; grid-template-columns:repeat(4, 1fr); gap:10px; font-size:12.5px;">
                    <div>Họ và tên: <strong style="color:#0f172a;">${r.hoTen}</strong></div>
                    <div>Cấp bậc: <strong>${r.capBac || '---'}</strong></div>
                    <div>Chức vụ: <strong>${r.chucVu || '---'}</strong></div>
                    <div>Đơn vị: <strong>${r.donVi || r.group || '---'}</strong></div>

                    <div>Hệ số lương: <strong style="color:#1e3a5f;">${BQPValidation.fmtNum(r.heSoLuong || (r.rawCols && r.rawCols[6]))}</strong></div>
                    <div>Hệ số chênh lệch: <strong>${BQPValidation.fmtNum(r.heSoChenhLechBaoLuu || (r.rawCols && r.rawCols[7]))}</strong></div>
                    <div>Hệ số chức vụ: <strong>${BQPValidation.fmtNum(r.heSoChucVu || (r.rawCols && r.rawCols[8]))}</strong></div>
                    <div>Mức lương cơ sở: <strong>2.340.000 đ</strong></div>

                    <div>Thời điểm nhập ngũ: <strong>${dNhapNgu}</strong></div>
                    <div>Thời điểm nghỉ hưu: <strong>${dNghi}</strong></div>
                    <div>% PC trách nhiệm: <strong>${BQPValidation.fmtNum(((r.tiLePhuCapTrachNhiem || (r.rawCols && r.rawCols[11])) > 1 ? (r.tiLePhuCapTrachNhiem || (r.rawCols && r.rawCols[11])) : (r.tiLePhuCapTrachNhiem || (r.rawCols && r.rawCols[11])) * 100))}%</strong></div>
                    <div>% PC đặc thù: <strong>${BQPValidation.fmtNum(((r.tiLePhuCapDacThu || (r.rawCols && r.rawCols[12])) > 1 ? (r.tiLePhuCapDacThu || (r.rawCols && r.rawCols[12])) : (r.tiLePhuCapDacThu || (r.rawCols && r.rawCols[12])) * 100))}%</strong></div>
                </div>
                ` : `
                <div style="display:grid; grid-template-columns:repeat(4, 1fr); gap:10px; font-size:12.5px;">
                    <div>Họ và tên: <strong style="color:#0f172a;">${r.hoTen}</strong></div>
                    <div>Cấp bậc: <strong>${r.capBac || '---'}</strong></div>
                    <div>Chức vụ: <strong>${r.chucVu || '---'}</strong></div>
                    <div>Đơn vị: <strong>${r.group || '---'}</strong></div>

                    <div>Tháng năm sinh: <strong>${dNgaySinh}</strong></div>
                    ${!isExcelOnly ? `<div>Trần tuổi áp dụng: <strong style="color:#1e3a5f;">${tranTuoi} tuổi</strong></div>` : `<div>Mức lương tháng: <strong style="color:#15803d;">${BQPValidation.fmtMoney(r.luongThang)} đ</strong></div>`}
                    <div>Thời điểm nhập ngũ: <strong>${dNhapNgu}</strong></div>
                    <div>Thời điểm nghỉ: <strong>${dNghi}</strong></div>

                    <div>Sáp nhập / Giải thể: <strong>${dSapNhap}</strong></div>
                    ${!isExcelOnly ? `<div>Lương tháng hưởng: <strong style="color:#15803d;">${BQPValidation.fmtMoney(r.luongThang)} đ</strong></div>` : `<div>Dòng trong Excel: <strong>Dòng ${r.rowIndex || '-'}</strong></div>`}
                    ${!isExcelOnly ? `<div>Dòng trong Excel: <strong>Dòng ${r.rowIndex || '-'}</strong></div>` : `<div>Biểu mẫu: <strong style="color:#1e3a5f;">Phụ lục ${sheetKey}</strong></div>`}
                    ${!isExcelOnly ? `<div>Loại phụ lục: <strong style="color:#1e3a5f;">Phụ lục ${sheetKey}</strong></div>` : `<div>Trạng thái: <strong style="color:#0369a1;">Hồ sơ gốc từ Excel</strong></div>`}
                </div>
                `}
            </div>

            <!-- Tổng kết tiền tệ -->
            ${summaryMoneyHtml}

            <!-- Số tiền bằng chữ -->
            ${chuTienHtml}

            <!-- Bảng dữ liệu -->
            ${tableBlockHtml}
        `;

        // Thanh điều hướng (Trước / Sau)
        if (navEl && idx !== -1) {
            let prevDisabled = idx <= 0 ? 'disabled' : '';
            let nextDisabled = idx >= totalRecords - 1 ? 'disabled' : '';
            let paramBool = isExcelOnly ? 'true' : 'false';
            navEl.innerHTML = `
                <button type="button" class="btn-top" onclick="ValidationUI.openCompareModal(${idx - 1}, ${paramBool})" ${prevDisabled} style="padding:5px 12px; font-size:12px; font-weight:600; cursor:pointer;">
                    Đối tượng trước
                </button>
                <span style="font-size:12.5px; color:#64748b;">
                    Hồ sơ <strong>${idx + 1}</strong> / ${totalRecords}
                </span>
                <button type="button" class="btn-top" onclick="ValidationUI.openCompareModal(${idx + 1}, ${paramBool})" ${nextDisabled} style="padding:5px 12px; font-size:12px; font-weight:600; cursor:pointer;">
                    Đối tượng tiếp theo
                </button>
            `;
        }

        modal.classList.add('open');
    },

    closeErrorModal() {
        let modal = document.getElementById("valErrorModal");
        if (modal) modal.classList.remove('open');
    },

    openSaveModal() {
        let records = this.currentRecords || [];
        if (records.length === 0) {
            alert('Chưa có dữ liệu thẩm định để lưu. Hãy quét file Excel trước.');
            return;
        }

        let modal = document.getElementById("valSaveModal");
        if (modal) {
            // Nạp danh sách đơn vị Cấp 1 vào dropdown
            if (typeof StorageManager !== 'undefined') {
                let units = StorageManager.getUnits();
                let parentUnits = units.filter(u => !u.parentId);
                let sel = document.getElementById("val_save_parent_select");
                if (sel) {
                    sel.innerHTML = '<option value="">-- Chọn Đơn vị Cấp 1 tiếp nhận --</option>' +
                        parentUnits.map(u => `<option value="${u.id}">${u.name}</option>`).join('');
                }
            }
            // Đặt trạng thái mặc định cho các tùy chọn danh sách
            let cbVal = document.getElementById("val_save_to_validated");
            let cbExc = document.getElementById("val_save_to_excel");
            if (cbVal) cbVal.checked = true;
            if (cbExc) cbExc.checked = false;

            modal.classList.add('open');
        } else {
            // Fallback nếu không có modal
            this.saveRecords('all');
        }
    },

    closeSaveModal() {
        let modal = document.getElementById("valSaveModal");
        if (modal) modal.classList.remove('open');
    },

    onUnitModeRadioChange() {
        // Toggle UI modes if needed
    },

    openQuickAddUnitDialog() {
        let name = prompt("Nhập tên Đơn vị Cấp 1 mới (ví dụ: Bộ Tư lệnh Thủ đô Hà Nội):");
        if (name && name.trim()) {
            if (typeof StorageManager !== 'undefined') {
                let id = StorageManager.addUnit(name.trim(), null);
                if (typeof refreshAllUnitSelects === 'function') refreshAllUnitSelects();
                this.openSaveModal();
                let sel = document.getElementById("val_save_parent_select");
                if (sel) sel.value = id;
            }
        }
    },

    saveRecords(mode = 'all', saveMode = 'append') {
        let records = this.currentRecords || [];
        if (records.length === 0) return;

        let toSave = records;

        if (toSave.length === 0) {
            alert('Không có hồ sơ nào thỏa mãn điều kiện lưu.');
            return;
        }

        if (typeof StorageManager === 'undefined') {
            alert('Hệ thống lưu trữ chưa sẵn sàng.');
            return;
        }

        // Tùy chọn lưu vào danh sách nào
        let cbVal = document.getElementById("val_save_to_validated");
        let cbExc = document.getElementById("val_save_to_excel");
        let saveToValidated = cbVal ? cbVal.checked : true;
        let saveToExcel = cbExc ? cbExc.checked : false;

        if (!saveToValidated && !saveToExcel) {
            alert('Vui lòng chọn ít nhất một danh sách để lưu:\n- "Danh sách sau thẩm định"\n- "Hồ sơ nhập từ Excel"');
            return;
        }

        // Lấy thông tin đơn vị được chọn từ form lưu nếu có
        let parentUnitId = null;
        let parentSel = document.getElementById("val_save_parent_select");
        if (parentSel && parentSel.value) parentUnitId = parentSel.value;

        let units = StorageManager.getUnits();
        let targetParent = parentUnitId ? units.find(u => u.id === parentUnitId) : null;

        // 1. Lưu vào "Danh sách sau thẩm định"
        if (saveToValidated) {
            let recordsToSave = toSave.map(r => {
                let uName = r.group || 'BQP';
                let finalUnitId = null;

                if (targetParent) {
                    finalUnitId = StorageManager.addUnit(uName, targetParent.id);
                } else {
                    finalUnitId = StorageManager.addUnit(uName, null);
                }

                let actMoney = r.tongTienThucTe != null ? r.tongTienThucTe : (r.rawCols ? (r.rawCols[23] || r.rawCols[18] || r.rawCols[15] || 0) : 0);
                let expMoney = r.tongTienTinhLai != null ? r.tongTienTinhLai : actMoney;

                return {
                    id: r.id || ('val_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6)),
                    unitId: finalUnitId,
                    donVi: targetParent ? `${targetParent.name} > ${uName}` : uName,
                    group: uName,
                    sheetSource: r.sheet,
                    sheet: r.sheet,
                    hoTen: r.hoTen,
                    capBac: r.capBac,
                    chucVu: r.chucVu,
                    ngaySinh: BQPValidation.formatDate(r.ngaySinh),
                    nhapNgu: BQPValidation.formatDate(r.nhapNgu),
                    sapNhap: BQPValidation.formatDate(r.sapNhap),
                    thoiDiemNghi: BQPValidation.formatDate(r.thoiDiemNghi),
                    luongThang: r.luongThang,
                    tongTienThucTe: actMoney,
                    tongTienTinhLai: expMoney,
                    tongTien: expMoney,
                    diff: expMoney - actMoney,
                    input: {
                        hoTen: r.hoTen,
                        capBac: r.capBac,
                        chucVu: r.chucVu,
                        ngaySinh: BQPValidation.formatDate(r.ngaySinh),
                        nhapNgu: BQPValidation.formatDate(r.nhapNgu),
                        sapNhap: BQPValidation.formatDate(r.sapNhap),
                        thoiDiemNghi: BQPValidation.formatDate(r.thoiDiemNghi),
                        luongThang: r.luongThang
                    },
                    result: {
                        tongTien: expMoney
                    },
                    expected: r.expected,
                    hasErrors: r.hasErrors,
                    errorDetails: r.errorDetails,
                    comparisons: r.comparisons,
                    rawCols: r.rawCols,
                    createdAt: new Date().toISOString()
                };
            });
            StorageManager.addRecordsBySource('validated', recordsToSave, saveMode);
        }

        // 2. Lưu vào "Hồ sơ nhập từ Excel" (lưu theo số liệu thực tế ban đầu từ file Excel)
        if (saveToExcel) {
            let excelRecordsToSave = toSave.map(r => {
                let uName = r.group || 'BQP';
                let finalUnitId = null;

                if (targetParent) {
                    finalUnitId = StorageManager.addUnit(uName, targetParent.id);
                } else {
                    finalUnitId = StorageManager.addUnit(uName, null);
                }

                let actMoney = r.tongTienThucTe != null ? r.tongTienThucTe : (r.rawCols ? (r.rawCols[23] || r.rawCols[18] || r.rawCols[15] || 0) : 0);

                return {
                    id: 'excel_' + (r.id ? r.id.replace(/^val_/, '') : (Date.now() + '_' + Math.random().toString(36).substr(2, 6))),
                    unitId: finalUnitId,
                    donVi: targetParent ? `${targetParent.name} > ${uName}` : uName,
                    group: uName,
                    sheetSource: r.sheet,
                    sheet: r.sheet,
                    hoTen: r.hoTen,
                    capBac: r.capBac,
                    chucVu: r.chucVu,
                    ngaySinh: BQPValidation.formatDate(r.ngaySinh),
                    nhapNgu: BQPValidation.formatDate(r.nhapNgu),
                    sapNhap: BQPValidation.formatDate(r.sapNhap),
                    thoiDiemNghi: BQPValidation.formatDate(r.thoiDiemNghi),
                    luongThang: r.luongThang,
                    tongTienThucTe: actMoney,
                    rawCols: r.rawCols,
                    input: {
                        hoTen: r.hoTen,
                        capBac: r.capBac,
                        chucVu: r.chucVu,
                        ngaySinh: BQPValidation.formatDate(r.ngaySinh),
                        nhapNgu: BQPValidation.formatDate(r.nhapNgu),
                        sapNhap: BQPValidation.formatDate(r.sapNhap),
                        thoiDiemNghi: BQPValidation.formatDate(r.thoiDiemNghi),
                        luongThang: r.luongThang
                    },
                    result: {
                        tongTien: actMoney
                    },
                    createdAt: new Date().toISOString()
                };
            });
            StorageManager.addRecordsBySource('excel', excelRecordsToSave, saveMode);
        }

        if (typeof updateTabCount === 'function') updateTabCount();
        if (typeof refreshAllUnitSelects === 'function') refreshAllUnitSelects();
        if (typeof RecordsUI !== 'undefined') RecordsUI.render();

        this.closeSaveModal();

        let savedDestinations = [];
        if (saveToValidated) savedDestinations.push('"Danh sách sau thẩm định"');
        if (saveToExcel) savedDestinations.push('"Hồ sơ nhập từ Excel"');

        let ok = confirm(`Đã lưu thành công ${toSave.length} đối tượng vào ${savedDestinations.join(' và ')} (được phân loại theo đơn vị, ban).\n\nBạn có muốn chuyển sang tab "Danh sách đối tượng" để xem ngay bây giờ không?`);
        if (ok) {
            // Chuyển sang tab danh sách
            document.querySelectorAll('.tab-button').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

            let tabListBtn = document.getElementById("tab_list_btn");
            let paneList = document.getElementById("pane_list");
            if (tabListBtn) tabListBtn.classList.add('active');
            if (paneList) paneList.classList.add('active');

            if (typeof RecordsUI !== 'undefined' && RecordsUI.switchSource) {
                RecordsUI.switchSource(saveToValidated ? 'validated' : 'excel');
            }
        }
    },

    executeSaveToRecords() {
        this.saveRecords('all', 'append');
    },

    closeQuickAddUnitDialog() {
        let m = document.getElementById('valQuickAddUnitModal');
        if (m) m.classList.remove('open');
    },

    confirmQuickAddUnit() {
        let nameEl = document.getElementById('val_quick_unit_name');
        let parentEl = document.getElementById('val_quick_unit_parent');
        let name = nameEl ? nameEl.value.trim() : '';
        let parentId = parentEl ? parentEl.value : null;

        if (!name) {
            alert('Vui lòng nhập tên đơn vị.');
            return;
        }

        if (typeof StorageManager !== 'undefined') {
            let newId = StorageManager.addUnit(name, parentId || null);
            if (typeof refreshAllUnitSelects === 'function') refreshAllUnitSelects();
            
            let sel = document.getElementById("val_save_parent_select");
            if (sel) {
                let units = StorageManager.getUnits();
                let parentUnits = units.filter(u => !u.parentId);
                sel.innerHTML = '<option value="">-- Chọn Đơn vị Cấp 1 tiếp nhận --</option>' +
                    parentUnits.map(u => `<option value="${u.id}">${u.name}</option>`).join('');
                sel.value = parentId || newId;
            }
        }

        this.closeQuickAddUnitDialog();
    },

    saveSingleRecord(recordId) {
        let r = this.currentRecords.find(x => x.id === recordId);
        if (!r) return;

        if (typeof StorageManager === 'undefined') return;

        let uName = r.group || 'BQP';
        let unitId = StorageManager.addUnit(uName, null);

        let recToSave = {
            id: r.id,
            unitId: unitId,
            donVi: uName,
            sheetSource: r.sheet,
            sheet: r.sheet,
            hoTen: r.hoTen,
            capBac: r.capBac,
            chucVu: r.chucVu,
            ngaySinh: r.ngaySinh,
            nhapNgu: r.nhapNgu,
            sapNhap: r.sapNhap,
            thoiDiemNghi: r.thoiDiemNghi,
            luongThang: r.luongThang,
            hasErrors: r.hasErrors,
            errorDetails: r.errorDetails,
            comparisons: r.comparisons,
            expected: r.expected,
            tongTienThucTe: r.tongTienThucTe,
            tongTienTinhLai: r.tongTienTinhLai,
            diff: r.diff,
            createdAt: new Date().toISOString()
        };

        StorageManager.addRecordsBySource('validated', [recToSave]);

        if (typeof updateTabCount === 'function') updateTabCount();
        if (typeof RecordsUI !== 'undefined') RecordsUI.render();

        alert(`Đã lưu quân nhân "${r.hoTen}" vào Danh sách đối tượng.`);
    },

    deleteRecord(recordId) {
        if (!confirm('Xóa đối tượng này khỏi danh sách thẩm định hiện tại?')) return;
        this.currentRecords = this.currentRecords.filter(r => r.id !== recordId);
        this.renderTable();
    },

    async exportExcel() {
        if (!this.currentRecords || this.currentRecords.length === 0) {
            alert('Không có dữ liệu thẩm định để xuất file.');
            return;
        }

        let filterStatus = document.getElementById('vfilter_status') ? document.getElementById('vfilter_status').value : '';
        let filterSheet = document.getElementById('vfilter_sheet') ? document.getElementById('vfilter_sheet').value : '';
        let filterSearch = document.getElementById('vfilter_search') ? document.getElementById('vfilter_search').value.trim() : '';

        let isFiltered = Boolean(filterStatus || filterSheet || filterSearch || (this.currentFiltered && this.currentFiltered.length < this.currentRecords.length));
        let recordsToExport = (this.currentFiltered && this.currentFiltered.length > 0) ? this.currentFiltered : this.currentRecords;

        if (recordsToExport.length === 0) {
            alert('Không có hồ sơ nào phù hợp với bộ lọc hiện tại để xuất file.');
            return;
        }

        let baseName = (this.currentFile?.name || 'Bao_cao').replace(/\.[^/.]+$/, '');

        try {
            let statusLabel = 'Toàn bộ danh sách';
            let suffix = 'Tat_Ca';
            if (isFiltered && recordsToExport.length < this.currentRecords.length) {
                if (filterStatus === 'valid') {
                    statusLabel = 'Chỉ quân nhân tính đúng 100%';
                    suffix = 'Tinh_Dung';
                } else if (filterStatus === 'error_under') {
                    statusLabel = 'Sai thiếu (Cấp thiếu tiền)';
                    suffix = 'Sai_Thieu';
                } else if (filterStatus === 'error_over') {
                    statusLabel = 'Sai thừa (Cấp thừa tiền)';
                    suffix = 'Sai_Thua';
                } else if (filterStatus === 'error') {
                    statusLabel = 'Lệch chuẩn BQP';
                    suffix = 'Lech_Chuan';
                } else {
                    suffix = 'Da_Loc';
                }
                if (filterSheet) {
                    suffix += '_' + filterSheet.replace('.', '');
                }
            }

            // Tạo bản sao workbook từ file gốc để giữ nguyên 100% định dạng, màu sắc và công thức
            const buffer = await this.currentWb.xlsx.writeBuffer();
            const exportWb = new ExcelJS.Workbook();
            await exportWb.xlsx.load(buffer);

            // Index danh sách hồ sơ xuất
            const recMapByRow = new Map();
            const recMapByNameDob = new Map();
            const keepKeySet = new Set();

            const formatDobSafe = (val) => {
                if (!val) return '';
                if (val instanceof Date && !isNaN(val)) {
                    let m = val.getMonth() + 1;
                    let y = val.getFullYear();
                    return `${m}/${y}`;
                }
                return String(val).trim().replace(/^[0]+/g, '');
            };

            const formatNameSafe = (val) => {
                if (!val) return '';
                return String(val).trim().toLowerCase().replace(/\s+/g, ' ');
            };

            recordsToExport.forEach(r => {
                let sheet = (r.sheet || r.sheetSource || 'I.1').toUpperCase();
                if (r.rowIndex) {
                    recMapByRow.set(sheet + '_' + r.rowIndex, r);
                    keepKeySet.add(sheet + '_' + r.rowIndex);
                }
                let normName = formatNameSafe(r.hoTen || (r.input && r.input.hoTen));
                let normDob = formatDobSafe(r.ngaySinh != null ? r.ngaySinh : (r.input && r.input.ngaySinh));
                if (normName) {
                    let k = sheet + '_' + normName + '_' + normDob;
                    recMapByNameDob.set(k, r);
                    recMapByNameDob.set(sheet + '_' + normName, r);
                    keepKeySet.add(k);
                    keepKeySet.add(sheet + '_' + normName);
                }
            });

            const allSoldierKeys = new Set();
            this.currentRecords.forEach(r => {
                let sheet = (r.sheet || r.sheetSource || 'I.1').toUpperCase();
                if (r.rowIndex) allSoldierKeys.add(sheet + '_' + r.rowIndex);
                let normName = formatNameSafe(r.hoTen || (r.input && r.input.hoTen));
                let normDob = formatDobSafe(r.ngaySinh != null ? r.ngaySinh : (r.input && r.input.ngaySinh));
                if (normName) {
                    allSoldierKeys.add(sheet + '_' + normName + '_' + normDob);
                    allSoldierKeys.add(sheet + '_' + normName);
                }
            });

            // =====================================================================
            // XỬ LÝ CÁC SHEET GỐC (Phụ lục I.1, I.2, I.3, I.5)
            // =====================================================================
            exportWb.eachSheet((ws) => {
                let sheetName = ws.name.toLowerCase();
                let type = null;
                if (sheetName.includes('i.5') || sheetName.includes('i5') || sheetName.includes('pl5') || sheetName.includes('pl i.5')) type = 'I.5';
                else if (sheetName.includes('i.1') || sheetName.includes('i1') || sheetName.includes('pl1') || sheetName.includes('pl i.1')) type = 'I.1';
                else if (sheetName.includes('i.2') || sheetName.includes('i2') || sheetName.includes('pl2') || sheetName.includes('pl i.2')) type = 'I.2';
                else if (sheetName.includes('i.3') || sheetName.includes('i3') || sheetName.includes('pl3') || sheetName.includes('pl i.3')) type = 'I.3';
                if (!type) return;

                const { colMap, headerRowIdx } = BQPValidation.ExcelParser.findColMap(ws);
                let hRow = headerRowIdx !== -1 ? headerRowIdx : (type === 'I.5' ? 5 : (type === 'I.2' ? 8 : (type === 'I.3' ? 8 : 9)));
                let startRow = hRow + 1;

                let maxColIndex = 0;
                for (let k in colMap) {
                    if (colMap[k] > maxColIndex) maxColIndex = colMap[k];
                }
                if (maxColIndex === 0) {
                    maxColIndex = type === 'I.1' ? 23 : (type === 'I.2' ? 18 : (type === 'I.5' ? 22 : 15));
                }
                let noteCol = maxColIndex + 1;

                // Tiêu đề cột Ghi chú thẩm định BQP
                let headerCell = ws.getRow(hRow).getCell(noteCol);
                headerCell.value = "Ghi chú thẩm định BQP";
                headerCell.style = Object.assign({}, headerCell.style, {
                    font: {
                        name: 'Arial',
                        size: 8.5,
                        bold: true,
                        color: { argb: 'FFFF0000' }
                    },
                    alignment: { vertical: 'middle', horizontal: 'center', wrapText: true },
                    border: {
                        top: { style: 'thin' },
                        left: { style: 'thin' },
                        bottom: { style: 'thin' },
                        right: { style: 'thin' }
                    }
                });
                ws.getColumn(noteCol).width = 46;

                const matchedRowSet = new Set();
                const soldierRowSet = new Set();

                // BƯỚC 1: Ghi chú và tô màu chữ đỏ các ô lỗi cho các dòng quân nhân được giữ lại
                for (let r = startRow; r <= ws.rowCount; r++) {
                    let row = ws.getRow(r);
                    let rowName = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[2] || 2)).trim();
                    let rowDob = BQPValidation.ExcelParser.getCellText(row.getCell(colMap[3] || 3)).trim();

                    let rec = recMapByRow.get(type + '_' + r);
                    if (!rec && rowName) {
                        let normN = formatNameSafe(rowName);
                        let normD = formatDobSafe(rowDob);
                        rec = recMapByNameDob.get(type + '_' + normN + '_' + normD) || recMapByNameDob.get(type + '_' + normN);
                    }

                    let isSoldier = Boolean(rec) || (rowName && (
                        allSoldierKeys.has(type + '_' + r) ||
                        allSoldierKeys.has(type + '_' + formatNameSafe(rowName) + '_' + formatDobSafe(rowDob)) ||
                        allSoldierKeys.has(type + '_' + formatNameSafe(rowName))
                    ));

                    if (isSoldier) {
                        soldierRowSet.add(r);
                    }

                    if (rec) {
                        matchedRowSet.add(r);
                        let noteCell = row.getCell(noteCol);

                        let isErr = rec.hasErrors || (rec.errorDetails && rec.errorDetails.length > 0);
                        let act = rec.tongTienThucTe != null ? rec.tongTienThucTe : 0;
                        let exp = rec.tongTienTinhLai != null ? rec.tongTienTinhLai : act;
                        let diff = (rec.diff != null) ? rec.diff : (exp - act);

                        if (isErr || (act > 0 && Math.abs(diff) > 1000)) {
                            let diffPrefix = '';
                            if (diff > 1000) {
                                diffPrefix = `[Sai thiếu: Cấp thiếu +${BQPValidation.fmtMoney(diff)} đ]\n`;
                            } else if (diff < -1000) {
                                diffPrefix = `[Sai thừa: Cấp thừa -${BQPValidation.fmtMoney(Math.abs(diff))} đ]\n`;
                            }
                            let detailStr = (rec.errorDetails && rec.errorDetails.length > 0) ? rec.errorDetails.join('\n') : 'Lệch chuẩn BQP';
                            noteCell.value = diffPrefix + detailStr;
                            noteCell.style = Object.assign({}, noteCell.style, {
                                font: {
                                    name: 'Arial',
                                    size: 7.5,
                                    color: { argb: 'FFFF0000' }
                                },
                                alignment: { wrapText: true, vertical: 'top' },
                                border: {
                                    top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                    left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                    bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                    right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
                                }
                            });

                            if (rec.comparisons) {
                                rec.comparisons.forEach(comp => {
                                    if (comp.hasErr) {
                                        let cMatch = comp.col ? comp.col.match(/(\d+)/) : null;
                                        if (cMatch) {
                                            let cNum = parseInt(cMatch[1], 10);
                                            let cellCol = colMap[cNum];
                                            if (cellCol) {
                                                let errCell = row.getCell(cellCol);
                                                errCell.style = Object.assign({}, errCell.style, {
                                                    font: Object.assign({}, errCell.font || {}, {
                                                        color: { argb: 'FFFF0000' },
                                                        bold: true
                                                    })
                                                });
                                            }
                                        }
                                    }
                                });
                            }
                        } else {
                            noteCell.value = "Đạt chuẩn";
                            noteCell.style = Object.assign({}, noteCell.style, {
                                font: {
                                    name: 'Arial',
                                    size: 8,
                                    color: { argb: 'FF166534' }
                                },
                                alignment: { wrapText: true, vertical: 'middle', horizontal: 'center' },
                                border: {
                                    top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                    left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                    bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                    right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
                                }
                            });
                        }
                    }
                }

                // BƯỚC 2: Nếu có bộ lọc, xóa các dòng quân nhân không thuộc danh sách lọc (duyệt từ dưới lên trên)
                if (isFiltered) {
                    for (let r = ws.rowCount; r >= startRow; r--) {
                        if (soldierRowSet.has(r) && !matchedRowSet.has(r)) {
                            ws.spliceRows(r, 1);
                        }
                    }

                    // BƯỚC 3: Đánh lại STT (Cột 1) cho các dòng quân nhân còn lại trong sheet: 1, 2, 3...
                    let sttCounter = 1;
                    let sttCol = colMap[1] || 1;
                    for (let r = startRow; r <= ws.rowCount; r++) {
                        let row = ws.getRow(r);
                        let noteVal = row.getCell(noteCol).value;
                        if (noteVal) {
                            let sttCell = row.getCell(sttCol);
                            sttCell.value = sttCounter++;
                            sttCell.alignment = { horizontal: 'center', vertical: 'middle' };
                        }
                    }
                }
            });

            // =====================================================================
            // BƯỚC BỔ SUNG: Tạo các Sheet II.x (Đạt chuẩn), III.x (Cấp thừa), IV.x (Cấp thiếu)
            // =====================================================================
            const classifyRec = (r) => {
                let act = r.tongTienThucTe != null ? r.tongTienThucTe : 0;
                let exp2 = r.tongTienTinhLai != null ? r.tongTienTinhLai : act;
                let diff2 = r.diff != null ? r.diff : (exp2 - act);
                let hasCompErr = r.comparisons && r.comparisons.some(c => c.hasErr);
                let isErr2 = r.hasErrors || (r.errorDetails && r.errorDetails.length > 0) || hasCompErr || (act > 0 && Math.abs(diff2) > 1000);

                // II.1, II.2, II.3 CHỈ lấy ra những người hoàn toàn ĐÚNG ở I.1, I.2, I.3
                if (!isErr2) return 'correct';

                // Đối tượng có sai phạm:
                if (diff2 < -1000) return 'over';  // Phụ lục III.x (Cấp thừa)
                if (diff2 > 1000) return 'under';  // Phụ lục IV.x (Cấp thiếu)
                return 'other_err';               // Sai tiêu chí/số tháng nhưng không lệch tiền: lưu vết ở sheet gốc I.x, không đưa vào II.x
            };

            const correctRecs = recordsToExport.filter(r => classifyRec(r) === 'correct');
            const overRecs    = recordsToExport.filter(r => classifyRec(r) === 'over');
            const underRecs   = recordsToExport.filter(r => classifyRec(r) === 'under');

            const subGroups = [
                { recs: correctRecs, prefix: 'II',  label: 'Đạt chuẩn' },
                { recs: overRecs,    prefix: 'III', label: 'Cấp thừa' },
                { recs: underRecs,   prefix: 'IV',  label: 'Cấp thiếu' }
            ];

            // Với mỗi nhóm (correct/over/under), load workbook mới từ buffer, lọc dòng rồi copy sheets
            for (const grp of subGroups) {
                if (grp.recs.length === 0) continue;

                const grpRowMap = new Map();
                const grpNameMap = new Map();
                grp.recs.forEach(r => {
                    let sh = (r.sheet || r.sheetSource || 'I.1').toUpperCase();
                    if (r.rowIndex) grpRowMap.set(sh + '_' + r.rowIndex, r);
                    let nm = formatNameSafe(r.hoTen || (r.input && r.input.hoTen));
                    let db = formatDobSafe(r.ngaySinh != null ? r.ngaySinh : (r.input && r.input.ngaySinh));
                    if (nm) {
                        grpNameMap.set(sh + '_' + nm + '_' + db, r);
                        grpNameMap.set(sh + '_' + nm, r);
                    }
                });

                const grpWb = new ExcelJS.Workbook();
                await grpWb.xlsx.load(buffer.slice(0));

                const sheetMapping = {
                    'I.1': `Phụ lục ${grp.prefix}.1`,
                    'I.2': `Phụ lục ${grp.prefix}.2`,
                    'I.3': `Phụ lục ${grp.prefix}.3`
                };

                const sheetsToProcess = [];
                grpWb.eachSheet((ws) => sheetsToProcess.push(ws));

                for (const grpWs of sheetsToProcess) {
                    let sn = grpWs.name.toLowerCase();
                    let sType = null;
                    if (sn.includes('i.1') || sn.includes('i1') || sn.includes('pl1') || sn.includes('pl i.1')) sType = 'I.1';
                    else if (sn.includes('i.2') || sn.includes('i2') || sn.includes('pl2') || sn.includes('pl i.2')) sType = 'I.2';
                    else if (sn.includes('i.3') || sn.includes('i3') || sn.includes('pl3') || sn.includes('pl i.3')) sType = 'I.3';
                    if (!sType) continue;

                    const { colMap: grpColMap, headerRowIdx: grpHdrIdx } = BQPValidation.ExcelParser.findColMap(grpWs);
                    let grpHRow = grpHdrIdx !== -1 ? grpHdrIdx : 9;
                    let grpStart = grpHRow + 1;

                    let grpMaxCol = 0;
                    for (let kk in grpColMap) { if (grpColMap[kk] > grpMaxCol) grpMaxCol = grpColMap[kk]; }
                    if (grpMaxCol === 0) grpMaxCol = sType === 'I.1' ? 23 : (sType === 'I.2' ? 18 : 15);
                    let grpNoteCol = grpMaxCol + 1;

                    let grpHeaderCell = grpWs.getRow(grpHRow).getCell(grpNoteCol);
                    grpHeaderCell.value = 'Ghi chú thẩm định BQP';
                    grpHeaderCell.style = Object.assign({}, grpHeaderCell.style, {
                        font: { name: 'Arial', size: 8.5, bold: true, color: { argb: 'FFFF0000' } },
                        alignment: { vertical: 'middle', horizontal: 'center', wrapText: true },
                        border: { top: { style: 'thin' }, left: { style: 'thin' }, bottom: { style: 'thin' }, right: { style: 'thin' } }
                    });
                    grpWs.getColumn(grpNoteCol).width = 46;

                    const grpMatchSet = new Set();
                    const grpSoldierSet = new Set();

                    for (let rr = grpStart; rr <= grpWs.rowCount; rr++) {
                        let grpRow = grpWs.getRow(rr);
                        let rName = BQPValidation.ExcelParser.getCellText(grpRow.getCell(grpColMap[2] || 2));
                        let rDob  = BQPValidation.ExcelParser.getCellText(grpRow.getCell(grpColMap[3] || 3));
                        let rnN = formatNameSafe(rName);
                        let rnD = formatDobSafe(rDob);

                        let matchRec = grpRowMap.get(sType + '_' + rr);
                        if (!matchRec && rnN) {
                            matchRec = grpNameMap.get(sType + '_' + rnN + '_' + rnD) || grpNameMap.get(sType + '_' + rnN);
                        }

                        if (rnN && !isNaN(parseInt(grpRow.getCell(grpColMap[1] || 1).value))) {
                            grpSoldierSet.add(rr);
                        }

                        if (matchRec) {
                            grpMatchSet.add(rr);
                            let grpNoteCell = grpRow.getCell(grpNoteCol);
                            let recIsErr = matchRec.hasErrors || (matchRec.errorDetails && matchRec.errorDetails.length > 0);
                            let recAct = matchRec.tongTienThucTe != null ? matchRec.tongTienThucTe : 0;
                            let recExp = matchRec.tongTienTinhLai != null ? matchRec.tongTienTinhLai : recAct;
                            let recDiff = matchRec.diff != null ? matchRec.diff : (recExp - recAct);
                            let recHasDiff = recAct > 0 && Math.abs(recDiff) > 1000;

                            if (recIsErr || recHasDiff) {
                                let diffPfx = '';
                                if (recDiff > 1000) diffPfx = `[Sai thiếu: Cấp thiếu +${BQPValidation.fmtMoney(recDiff)} đ]\n`;
                                else if (recDiff < -1000) diffPfx = `[Sai thừa: Cấp thừa -${BQPValidation.fmtMoney(Math.abs(recDiff))} đ]\n`;
                                let detail = (matchRec.errorDetails && matchRec.errorDetails.length > 0) ? matchRec.errorDetails.join('\n') : 'Lệch chuẩn BQP';
                                grpNoteCell.value = diffPfx + detail;
                                grpNoteCell.style = Object.assign({}, grpNoteCell.style, {
                                    font: { name: 'Arial', size: 7.5, color: { argb: 'FFFF0000' } },
                                    alignment: { wrapText: true, vertical: 'top' },
                                    border: {
                                        top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                        left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                        bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                        right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
                                    }
                                });
                            } else {
                                grpNoteCell.value = 'Đạt chuẩn';
                                grpNoteCell.style = Object.assign({}, grpNoteCell.style, {
                                    font: { name: 'Arial', size: 8, color: { argb: 'FF166534' } },
                                    alignment: { wrapText: true, vertical: 'middle', horizontal: 'center' },
                                    border: {
                                        top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                        left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                        bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                                        right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
                                    }
                                });
                            }
                        }
                    }

                    // Delete soldier rows not in this group (bottom to top)
                    for (let rr = grpWs.rowCount; rr >= grpStart; rr--) {
                        let grpRow = grpWs.getRow(rr);
                        let rnN2 = formatNameSafe(BQPValidation.ExcelParser.getCellText(grpRow.getCell(grpColMap[2] || 2)));
                        let sttVal = grpRow.getCell(grpColMap[1] || 1).value;
                        let isSoldierRow = rnN2 && !isNaN(parseInt(sttVal));
                        if (isSoldierRow && !grpMatchSet.has(rr)) {
                            grpWs.spliceRows(rr, 1);
                        }
                    }

                    // Renumber STT
                    let grpStt = 1;
                    let grpSttCol = grpColMap[1] || 1;
                    for (let rr = grpStart; rr <= grpWs.rowCount; rr++) {
                        let grpRow = grpWs.getRow(rr);
                        let noteVal = grpRow.getCell(grpNoteCol).value;
                        if (noteVal) {
                            let sttCell = grpRow.getCell(grpSttCol);
                            sttCell.value = grpStt++;
                            sttCell.alignment = { horizontal: 'center', vertical: 'middle' };
                        }
                    }

                    // Copy this sheet to exportWb with the new name
                    const newName = sheetMapping[sType];
                    if (newName) {
                        try {
                            const destWs = exportWb.addWorksheet(newName);
                            grpWs.columns.forEach((col, idx) => {
                                try { if (col && col.width) destWs.getColumn(idx + 1).width = col.width; } catch(e2) {}
                            });
                            grpWs.eachRow({ includeEmpty: true }, (row, rowNum) => {
                                const dRow = destWs.getRow(rowNum);
                                row.eachCell({ includeEmpty: true }, (cell, colN) => {
                                    const dCell = dRow.getCell(colN);
                                    try {
                                        if (cell.value && typeof cell.value === 'object' && cell.value.richText) {
                                            dCell.value = cell.value.richText.map(rt => rt.text).join('');
                                        } else if (cell.value && typeof cell.value === 'object' && cell.value.formula) {
                                            dCell.value = cell.value.result != null ? cell.value.result : '';
                                        } else {
                                            dCell.value = cell.value;
                                        }
                                        if (cell.numFmt)    dCell.numFmt    = cell.numFmt;
                                        if (cell.font)      dCell.font      = JSON.parse(JSON.stringify(cell.font));
                                        if (cell.alignment) dCell.alignment = JSON.parse(JSON.stringify(cell.alignment));
                                        if (cell.fill)      dCell.fill      = JSON.parse(JSON.stringify(cell.fill));
                                        if (cell.border)    dCell.border    = JSON.parse(JSON.stringify(cell.border));
                                    } catch(e2) {}
                                });
                                if (row.height) dRow.height = row.height;
                                dRow.commit();
                            });
                            if (grpWs._merges) {
                                Object.keys(grpWs._merges).forEach(key => {
                                    try { destWs.mergeCells(key); } catch(e2) {}
                                });
                            }
                        } catch(e) {
                            console.warn('Lỗi thêm sheet', newName, e);
                        }
                    }
                }
            }

            const outBuffer = await exportWb.xlsx.writeBuffer();
            const blob = new Blob([outBuffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            let fileName = (isFiltered && recordsToExport.length < this.currentRecords.length)
                ? `${baseName}_Tham_dinh_${suffix}_${recordsToExport.length}dc.xlsx`
                : `${baseName}_tham_dinh_BQP.xlsx`;
            a.download = fileName;
            a.click();
            URL.revokeObjectURL(url);

        } catch (e) {
            console.error("Lỗi xuất Excel:", e);
            alert("Có lỗi khi tạo file Excel: " + e.message);
        }
    },

    reset() {
        this.currentFile = null;
        this.currentWb = null;
        this.currentRecords = [];
        this.currentFiltered = [];
        this.currentResult = null;

        let fileInput = document.getElementById("validate_file_input");
        if (fileInput) fileInput.value = "";

        let wrap = document.getElementById("validate_result_wrap");
        let dropzone = document.getElementById("validate_dropzone");
        if (wrap) wrap.style.display = "none";
        if (dropzone) dropzone.style.display = "block";
    }
};

if (typeof document !== 'undefined' && document.addEventListener) {
    document.addEventListener("DOMContentLoaded", function () {
        const dropzone = document.getElementById("validate_dropzone");
        if (dropzone) {
            dropzone.addEventListener("dragover", function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.add("dragover");
            });
            dropzone.addEventListener("dragleave", function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.remove("dragover");
            });
            dropzone.addEventListener("drop", function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.remove("dragover");
                if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                    ValidationUI.handleFileUpload(e);
                }
            });
        }
    });
}
