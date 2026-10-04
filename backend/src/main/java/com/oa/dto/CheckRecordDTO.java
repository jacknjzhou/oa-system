package com.oa.dto;

import com.oa.entity.CheckRecord;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CheckRecordDTO {
    private Long id;
    private Long userId;
    private LocalDateTime checkTime;
    private String type;
    private String source;
    private String note;

    public static CheckRecordDTO from(CheckRecord r) {
        return CheckRecordDTO.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .checkTime(r.getCheckTime())
                .type(r.getType())
                .source(r.getSource())
                .note(r.getNote())
                .build();
    }
}
