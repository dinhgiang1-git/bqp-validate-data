package com.bqpvalidateexcel;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import technology.tabula.*;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FullPdfDumpTest {

    @Test
    void dumpAllPdfs() throws Exception {
        dumpFile("src/main/resources/dulieu/3. PHU_LUC I.1.pdf", "target/dump_I1.txt");
        dumpFile("src/main/resources/dulieu/3. PHU_LUC_I.2.pdf", "target/dump_I2.txt");
        dumpFile("src/main/resources/dulieu/3. PHU_LUC_I.3.pdf", "target/dump_I3.txt");
    }

    private void dumpFile(String pdfPath, String outPath) throws Exception {
        File file = new File(pdfPath);
        if (!file.exists()) return;

        List<String> lines = new ArrayList<>();
        lines.add("FILE: " + file.getName());

        try (PDDocument document = PDDocument.load(file)) {
            ObjectExtractor oe = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
            PageIterator pages = oe.extract();
            int pageNum = 0;
            while (pages.hasNext()) {
                pageNum++;
                Page page = pages.next();
                List<Table> tables = sea.extract(page);
                lines.add("--- PAGE " + pageNum + " (Tables: " + tables.size() + ") ---");
                for (int t = 0; t < tables.size(); t++) {
                    Table table = tables.get(t);
                    lines.add("Table " + (t + 1) + " [" + table.getRowCount() + " rows x " + table.getColCount() + " cols]:");
                    for (int r = 0; r < table.getRowCount(); r++) {
                        List<RectangularTextContainer> row = table.getRows().get(r);
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("R%02d: ", r));
                        for (int c = 0; c < row.size(); c++) {
                            String text = row.get(c).getText().replace("\r", " ").replace("\n", " ").trim();
                            sb.append("[").append(c).append("]='").append(text).append("' ");
                        }
                        lines.add(sb.toString());
                    }
                }
            }
        }
        Files.write(Paths.get(outPath), lines, StandardCharsets.UTF_8);
    }
}
