package com.legalrag.controller;

import com.legalrag.service.data.DataLoaderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DataLoaderService dataLoaderService;

    @PostMapping("/load-data")
    public ResponseEntity<?> loadData() {
        dataLoaderService.loadDataset();
        return ResponseEntity.ok("Dataset loaded successfully");
    }
}
