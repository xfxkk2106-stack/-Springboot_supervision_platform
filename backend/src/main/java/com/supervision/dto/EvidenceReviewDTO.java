package com.supervision.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EvidenceReviewDTO {
    @NotNull(message = "审核结果不能为空")
    private Integer result;

    private String comment;
}
