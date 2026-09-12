package com.bqpvalidateexcel.service;

import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.ParsedRecord;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public interface RecordParserService {

    /**
     * DTO chứa kết quả trích xuất dữ liệu từ tệp đầu vào
     */
    class ExtractedData {
        public AppendixType appendixType;
        public String inputFormat; // "PDF" hoặc "EXCEL"
        public List<ParsedRecord> records = new ArrayList<>();
        public List<List<String>> headerRows = new ArrayList<>();
        public List<List<String>> allRawRows = new ArrayList<>();
    }

    /**
     * Bóc tách dữ liệu từ File
     */
    ExtractedData parse(File file, AppendixType forcedType) throws Exception;

    /**
     * Bóc tách dữ liệu từ InputStream
     */
    ExtractedData parse(InputStream inputStream, String fileName, AppendixType forcedType) throws Exception;

    /**
     * Kiểm tra định dạng tệp có được hỗ trợ bởi parser này hay không
     */
    boolean supports(String fileName);
}
