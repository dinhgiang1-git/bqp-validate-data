package com.bqpvalidateexcel.storage.service;

import com.bqpvalidateexcel.storage.util.UnitNameCanonicalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UnitNameCanonicalizerTest {

    @Test
    public void testAccentsAndNonAccentsProduceSameCanonicalKey() {
        String accented = UnitNameCanonicalizer.canonicalize("Ban Chỉ huy PTKV 1 - Sóc Sơn");
        String unaccented = UnitNameCanonicalizer.canonicalize("Ban Chỉ huy PTKV 1 - Soc Son");
        assertEquals(accented, unaccented);
        assertEquals("ban chptkv1 - soc son", accented);
    }

    @Test
    public void testPtkvAbbreviationsEquivalence() {
        String k1 = UnitNameCanonicalizer.canonicalize("Ban CHPTKV1 - Sóc Sơn");
        String k2 = UnitNameCanonicalizer.canonicalize("Ban CH PTKV 1 - Sóc Sơn");
        String k3 = UnitNameCanonicalizer.canonicalize("Ban Chỉ huy PTKV 1 - Sóc Sơn");
        String k4 = UnitNameCanonicalizer.canonicalize("Ban Chỉ huy PTKV 1 - Soc Son");

        assertEquals(k1, k2);
        assertEquals(k2, k3);
        assertEquals(k3, k4);
        assertEquals("ban chptkv1 - soc son", k1);
    }

    @Test
    public void testVietnameseLetterDHandling() {
        // Chữ 'đ' và 'Đ' phải chuyển thành 'd', có dấu/không dấu đều ra cùng key
        String k1 = UnitNameCanonicalizer.canonicalize("Trung đoàn 452");
        String k2 = UnitNameCanonicalizer.canonicalize("Trung doan 452");
        String k3 = UnitNameCanonicalizer.canonicalize("TRUNG ĐOÀN 452");
        String k4 = UnitNameCanonicalizer.canonicalize("trung doan452");

        assertEquals(k1, k2);
        assertEquals(k2, k3);
        assertEquals(k3, k4);
        assertEquals("trung doan452", k1);
    }

    @Test
    public void testSpacingAndHyphenVariations() {
        String k1 = UnitNameCanonicalizer.canonicalize("Ban CHPTKV 5- Thanh Oai");
        String k2 = UnitNameCanonicalizer.canonicalize("Ban CHPTKV 5 - Thanh Oai");
        String k3 = UnitNameCanonicalizer.canonicalize("Ban Chỉ huy PTKV 5 - Thanh Oai");

        assertEquals(k1, k2);
        assertEquals(k2, k3);
        assertEquals("ban chptkv5 - thanh oai", k1);
    }

    @Test
    public void testDepartmentNamesEquivalence() {
        String k1 = UnitNameCanonicalizer.canonicalize("Cục Hậu cần - Kỹ thuật");
        String k2 = UnitNameCanonicalizer.canonicalize("cuc hau can - ky thuat");
        String k3 = UnitNameCanonicalizer.canonicalize("Cục Hậu Cần - Kỹ Thuật");
        assertEquals(k1, k2);
        assertEquals(k2, k3);
        assertEquals("cuc hau can - ky thuat", k1);

        String p1 = UnitNameCanonicalizer.canonicalize("Phòng Tài Chính");
        String p2 = UnitNameCanonicalizer.canonicalize("Phòng Tài chính");
        String p3 = UnitNameCanonicalizer.canonicalize("phong tai chinh");
        assertEquals(p1, p2);
        assertEquals(p2, p3);
        assertEquals("phong tai chinh", p1);
    }

    @Test
    public void testNullAndBlankInput() {
        assertEquals("", UnitNameCanonicalizer.canonicalize(null));
        assertEquals("", UnitNameCanonicalizer.canonicalize(""));
        assertEquals("", UnitNameCanonicalizer.canonicalize("   "));
    }
}
