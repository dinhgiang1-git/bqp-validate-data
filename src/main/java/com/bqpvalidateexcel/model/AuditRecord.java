package com.bqpvalidateexcel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecord {
    private AppendixType appendixType;
    private ParsedRecord parsed;
    private CalculatedRecord calculated;
    private AuditDifference difference;

    public boolean isError() {
        return difference != null && difference.isHasError();
    }
}
