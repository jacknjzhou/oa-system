package com.oa.service;

import com.oa.dto.CheckRecordDTO;
import com.oa.entity.CheckRecord;
import com.oa.repository.CheckRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 打卡服务（AT-01）：上班/下班卡 + 今日汇总 + 月度统计。 */
@Service
@RequiredArgsConstructor
public class CheckService {

    private final CheckRecordRepository checkRecordRepository;

    @Transactional
    public CheckRecordDTO clock(Long userId, String type, String source, String note) {
        if (!"in".equals(type) && !"out".equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "打卡类型只能是 in/out");
        }
        String src = source == null || source.isBlank() ? "manual" : source;
        if (!"manual".equals(src) && !"scan".equals(src)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "打卡来源只能是 manual/scan");
        }
        CheckRecord record = new CheckRecord();
        record.setUserId(userId);
        record.setCheckTime(LocalDateTime.now());
        record.setType(type);
        record.setSource(src);
        record.setNote(note == null || note.length() > 128 ? null : note);
        return CheckRecordDTO.from(checkRecordRepository.save(record));
    }

    /** 今日打卡汇总：firstIn / lastOut / 工作分钟数 / 全部记录。 */
    @Transactional(readOnly = true)
    public Map<String, Object> today(Long userId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<CheckRecord> records = checkRecordRepository
                .findByUserIdAndCheckTimeBetweenOrderByCheckTimeAsc(userId, start, LocalDateTime.now());
        CheckRecord firstIn = records.stream().filter(r -> "in".equals(r.getType())).findFirst().orElse(null);
        CheckRecord lastOut = records.stream().filter(r -> "out".equals(r.getType())).reduce(
                (a, b) -> b.getCheckTime().isAfter(a.getCheckTime()) ? b : a).orElse(null);
        long minutes = 0;
        if (firstIn != null && lastOut != null && lastOut.getCheckTime().isAfter(firstIn.getCheckTime())) {
            minutes = ChronoUnit.MINUTES.between(firstIn.getCheckTime(), lastOut.getCheckTime());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", LocalDate.now().toString());
        result.put("firstIn", firstIn == null ? null : firstIn.getCheckTime());
        result.put("lastOut", lastOut == null ? null : lastOut.getCheckTime());
        result.put("workMinutes", minutes);
        result.put("records", records.stream().map(CheckRecordDTO::from).toList());
        return result;
    }

    /** 月度统计：按天聚合 [{date, firstIn, lastOut, minutes, count}]，升序。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> month(Long userId, int year, int month) {
        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        List<CheckRecord> records = checkRecordRepository
                .findByUserIdAndCheckTimeBetweenOrderByCheckTimeAsc(userId, start, end);
        Map<String, List<CheckRecord>> byDay = new LinkedHashMap<>();
        for (CheckRecord r : records) {
            byDay.computeIfAbsent(r.getCheckTime().toLocalDate().toString(), k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> days = new ArrayList<>();
        byDay.forEach((day, list) -> {
            list.sort(Comparator.comparing(CheckRecord::getCheckTime));
            CheckRecord firstIn = list.stream().filter(r -> "in".equals(r.getType())).findFirst().orElse(null);
            CheckRecord lastOut = list.stream().filter(r -> "out".equals(r.getType())).reduce(
                    (a, b) -> b.getCheckTime().isAfter(a.getCheckTime()) ? b : a).orElse(null);
            long minutes = 0;
            if (firstIn != null && lastOut != null && lastOut.getCheckTime().isAfter(firstIn.getCheckTime())) {
                minutes = ChronoUnit.MINUTES.between(firstIn.getCheckTime(), lastOut.getCheckTime());
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day);
            row.put("firstIn", firstIn == null ? null : firstIn.getCheckTime());
            row.put("lastOut", lastOut == null ? null : lastOut.getCheckTime());
            row.put("minutes", minutes);
            row.put("count", list.size());
            days.add(row);
        });
        return days;
    }
}
