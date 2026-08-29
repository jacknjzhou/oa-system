package com.oa.dto;

import com.oa.enums.DocType;
import com.oa.enums.SecrecyLevel;
import com.oa.enums.Urgency;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;

    private DocType docType;

    private Urgency urgency;

    private SecrecyLevel secrecyLevel;
}
