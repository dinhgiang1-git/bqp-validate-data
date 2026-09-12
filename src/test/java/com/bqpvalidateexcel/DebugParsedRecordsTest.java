package com.bqpvalidateexcel;

import com.bqpvalidateexcel.model.AppendixAuditResult;
import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.AuditRecord;
import com.bqpvalidateexcel.service.AuditService;
import com.bqpvalidateexcel.service.CalculationService;
import com.bqpvalidateexcel.service.PdfParserService;
import org.junit.jupiter.api.Test;

import java.io.File;

public class DebugParsedRecordsTest {

    private final PdfParserService pdfParserService = new PdfParserService();
    private final CalculationService calculationService = new CalculationService();
    private final AuditService auditService = new AuditService(pdfParserService, calculationService);

    @Test
    void debugI1() throws Exception {
        File file = new File("src/main/resources/dulieu/3. PHU_LUC I.1.pdf");
        AppendixAuditResult result = auditService.auditSinglePdf(file, AppendixType.PHU_LUC_I1);
        System.out.println("DEBUG I.1 first 5 records:");
        for (int i = 0; i < Math.min(5, result.getRecords().size()); i++) {
            AuditRecord ar = result.getRecords().get(i);
            System.out.println("Rec " + i + ": " + ar.getParsed().getFullName()
                    + " | Rank: " + ar.getParsed().getRank()
                    + " | Birth: " + ar.getParsed().getBirthDateStr() + " -> " + ar.getParsed().getBirthDate()
                    + " | Enlist: " + ar.getParsed().getEnlistDateStr() + " -> " + ar.getParsed().getEnlistDate()
                    + " | Retire: " + ar.getParsed().getRetirementDateStr() + " -> " + ar.getParsed().getRetirementDate()
                    + " | Salary: " + ar.getParsed().getSalary()
                    + " | DeclTotal: " + ar.getParsed().getDeclaredTotal()
                    + " | CalcTotal: " + ar.getCalculated().getTotalAmount()
                    + " | DiffReview: " + ar.getDifference().getReviewNote());
        }
    }

    @Test
    void debugI3() throws Exception {
        File file = new File("src/main/resources/dulieu/3. PHU_LUC_I.3.pdf");
        AppendixAuditResult result = auditService.auditSinglePdf(file, AppendixType.PHU_LUC_I3);
        System.out.println("DEBUG I.3 records:");
        for (int i = 0; i < result.getRecords().size(); i++) {
            AuditRecord ar = result.getRecords().get(i);
            System.out.println("Rec " + i + ": " + ar.getParsed().getFullName()
                    + " | Rank: " + ar.getParsed().getRank()
                    + " | Birth: " + ar.getParsed().getBirthDateStr() + " -> " + ar.getParsed().getBirthDate()
                    + " | Enlist: " + ar.getParsed().getEnlistDateStr() + " -> " + ar.getParsed().getEnlistDate()
                    + " | Retire: " + ar.getParsed().getRetirementDateStr() + " -> " + ar.getParsed().getRetirementDate()
                    + " | Salary: " + ar.getParsed().getSalary()
                    + " | DeclTotal: " + ar.getParsed().getDeclaredTotal()
                    + " | CalcTotal: " + ar.getCalculated().getTotalAmount()
                    + " | DiffReview: " + ar.getDifference().getReviewNote());
        }
    }
}
