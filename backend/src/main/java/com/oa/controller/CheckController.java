package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.CheckRecordDTO;
import com.oa.entity.User;
import com.oa.service.AuthService;
import com.oa.service.CheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 打卡（AT-01）。 */
@RestController
@RequestMapping("/api/check")
@RequiredArgsConstructor
public class CheckController {

    private final CheckService checkService;
    private final AuthService authService;

    public record ClockRequest(String type, String source, String note) {}

    /** 打卡：type = in / out。 */
    @PostMapping("/{type}")
    public ApiResponse<CheckRecordDTO> clock(@PathVariable String type,
                                             @RequestBody(required = false) ClockRequest req) {
        User current = authService.getCurrentUser();
        String source = req == null ? null : req.source();
        String note = req == null ? null : req.note();
        return ApiResponse.success(checkService.clock(current.getId(), type, source, note));
    }

    /** 今日打卡汇总。 */
    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> today() {
        User current = authService.getCurrentUser();
        return ApiResponse.success(checkService.today(current.getId()));
    }

    /** 月度统计（按月聚合）。 */
    @GetMapping("/month")
    public ApiResponse<List<Map<String, Object>>> month(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        User current = authService.getCurrentUser();
        LocalDate d = date == null ? LocalDate.now() : date;
        return ApiResponse.success(checkService.month(current.getId(), d.getYear(), d.getMonthValue()));
    }
}
