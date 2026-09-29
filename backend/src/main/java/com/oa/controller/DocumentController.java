package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.DocumentDTO;
import com.oa.dto.DocumentRequest;
import com.oa.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ApiResponse<DocumentDTO> create(@Valid @RequestBody DocumentRequest request) {
        return ApiResponse.success(documentService.create(request));
    }

    @GetMapping
    public ApiResponse<List<DocumentDTO>> list(@RequestParam(required = false) String title) {
        return ApiResponse.success(documentService.list(title));
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentDTO> detail(@PathVariable Long id) {
        return ApiResponse.success(documentService.getDetail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DocumentDTO> update(@PathVariable Long id, @Valid @RequestBody DocumentRequest request) {
        return ApiResponse.success(documentService.update(id, request));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<DocumentDTO> archive(@PathVariable Long id) {
        return ApiResponse.success(documentService.archive(id));
    }
}
