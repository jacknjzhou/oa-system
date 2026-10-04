package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.entity.CompanyInfo;
import com.oa.repository.CompanyInfoRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 企业信息（SY-04，单行）。 */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyInfoRepository companyInfoRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> get() {
        CompanyInfo info = companyInfoRepository.findById(1L)
                .orElseGet(CompanyInfo::new);
        return ApiResponse.success(view(info));
    }

    @PutMapping
    @Transactional
    public ApiResponse<Map<String, Object>> save(@RequestBody CompanyRequest req) {
        CompanyInfo info = companyInfoRepository.findById(1L).orElseGet(() -> {
            CompanyInfo c = new CompanyInfo();
            c.setId(1L);
            return c;
        });
        if (req.getName() != null) info.setName(req.getName());
        if (req.getShortName() != null) info.setShortName(req.getShortName());
        if (req.getLogoUrl() != null) info.setLogoUrl(req.getLogoUrl());
        if (req.getAddress() != null) info.setAddress(req.getAddress());
        if (req.getPhone() != null) info.setPhone(req.getPhone());
        if (req.getEmail() != null) info.setEmail(req.getEmail());
        if (req.getCreditCode() != null) info.setCreditCode(req.getCreditCode());
        if (req.getDescription() != null) info.setDescription(req.getDescription());
        return ApiResponse.success(view(companyInfoRepository.save(info)));
    }

    private Map<String, Object> view(CompanyInfo info) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", info.getName());
        m.put("shortName", info.getShortName());
        m.put("logoUrl", info.getLogoUrl());
        m.put("address", info.getAddress());
        m.put("phone", info.getPhone());
        m.put("email", info.getEmail());
        m.put("creditCode", info.getCreditCode());
        m.put("description", info.getDescription());
        return m;
    }

    @Data
    public static class CompanyRequest {
        private String name;
        private String shortName;
        private String logoUrl;
        private String address;
        private String phone;
        private String email;
        private String creditCode;
        private String description;
    }
}
