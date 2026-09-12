package com.bqpvalidateexcel.model;

import lombok.Getter;

@Getter
public enum AppendixType {
    PHU_LUC_I1("Phụ lục I.1", "Nghỉ hưu trước tuổi theo Nghị định số 178/2024/NĐ-CP", "NĐ 178 - Nghỉ hưu"),
    PHU_LUC_I2("Phụ lục I.2", "Thôi việc, phục viên theo Nghị định số 178/2024/NĐ-CP", "NĐ 178 - Thôi việc"),
    PHU_LUC_I3("Phụ lục I.3", "Không đủ điều kiện tái cử, tái bổ nhiệm theo Nghị định số 177/2024/NĐ-CP", "NĐ 177 - Không tái cử");

    private final String code;
    private final String title;
    private final String shortName;

    AppendixType(String code, String title, String shortName) {
        this.code = code;
        this.title = title;
        this.shortName = shortName;
    }
}
