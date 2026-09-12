package com.bqpvalidateexcel;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class RawPdfTextTest {
    @Test
    void printPdfText() throws Exception {
        printText("src/main/resources/dulieu/3. PHU_LUC_I.2.pdf");
    }

    private void printText(String path) throws Exception {
        try (PDDocument doc = PDDocument.load(new File(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            System.out.println("=== RAW TEXT OF " + path + " ===");
            System.out.println(stripper.getText(doc));
        }
    }
}
