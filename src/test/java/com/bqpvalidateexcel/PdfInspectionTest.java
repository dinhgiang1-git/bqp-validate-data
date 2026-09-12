package com.bqpvalidateexcel;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import technology.tabula.*;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public class PdfInspectionTest {

    @Test
    void inspectPdfI1() throws Exception {
        inspectFile("src/main/resources/dulieu/3. PHU_LUC I.1.pdf", "PHU_LUC I.1");
    }

    @Test
    void inspectPdfI2() throws Exception {
        inspectFile("src/main/resources/dulieu/3. PHU_LUC_I.2.pdf", "PHU_LUC I.2");
    }

    @Test
    void inspectPdfI3() throws Exception {
        inspectFile("src/main/resources/dulieu/3. PHU_LUC_I.3.pdf", "PHU_LUC I.3");
    }

    private void inspectFile(String filePath, String label) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File not found: " + filePath);
            return;
        }
        System.out.println("==================================================");
        System.out.println("INSPECTING: " + label + " (" + file.getName() + ")");
        System.out.println("==================================================");

        try (PDDocument document = PDDocument.load(file)) {
            ObjectExtractor oe = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
            PageIterator pages = oe.extract();
            int pageNum = 0;
            while (pages.hasNext()) {
                pageNum++;
                Page page = pages.next();
                List<Table> tables = sea.extract(page);
                System.out.println("--- Page " + pageNum + ": found " + tables.size() + " tables ---");
                for (int t = 0; t < tables.size(); t++) {
                    Table table = tables.get(t);
                    System.out.println("Table " + (t + 1) + " rows: " + table.getRowCount() + ", cols: " + table.getColCount());
                    for (int r = 0; r < Math.min(table.getRowCount(), 15); r++) {
                        List<RectangularTextContainer> row = table.getRows().get(r);
                        StringBuilder sb = new StringBuilder();
                        sb.append("Row ").append(r).append(" [").append(row.size()).append(" cols]: ");
                        for (int c = 0; c < row.size(); c++) {
                            String text = row.get(c).getText().replace("\r", " ").replace("\n", " ").trim();
                            sb.append("[").append(c).append("]: ").append(text).append(" | ");
                        }
                        System.out.println(sb.toString());
                    }
                }
            }
        }
    }
}
