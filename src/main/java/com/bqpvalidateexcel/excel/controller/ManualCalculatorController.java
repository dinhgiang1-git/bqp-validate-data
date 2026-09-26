package com.bqpvalidateexcel.excel.controller;

import com.bqpvalidateexcel.excel.model.dto.ManualCalculateRequestDto;
import com.bqpvalidateexcel.excel.model.dto.ManualCalculateResponseDto;
import com.bqpvalidateexcel.excel.service.ManualCalculatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/calculator")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ManualCalculatorController {

    private final ManualCalculatorService calculatorService;

    @PostMapping("/calculate")
    public ResponseEntity<ManualCalculateResponseDto> calculate(@RequestBody ManualCalculateRequestDto request) {
        ManualCalculateResponseDto res = calculatorService.calculate(request);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/pli1")
    public ResponseEntity<ManualCalculateResponseDto> calculatePLI1(@RequestBody ManualCalculateRequestDto request) {
        request.setSheetType("I.1");
        ManualCalculateResponseDto res = calculatorService.calculatePLI1(request);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/pli2")
    public ResponseEntity<ManualCalculateResponseDto> calculatePLI2(@RequestBody ManualCalculateRequestDto request) {
        request.setSheetType("I.2");
        ManualCalculateResponseDto res = calculatorService.calculatePLI2(request);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/pli3")
    public ResponseEntity<ManualCalculateResponseDto> calculatePLI3(@RequestBody ManualCalculateRequestDto request) {
        request.setSheetType("I.3");
        ManualCalculateResponseDto res = calculatorService.calculatePLI3(request);
        return ResponseEntity.ok(res);
    }
}
