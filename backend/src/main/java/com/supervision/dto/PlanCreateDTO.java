package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class PlanCreateDTO {
    @NotBlank(message = "计划类型不能为空")
    private String planType;

    @NotBlank(message = "计划标题不能为空")
    private String title;

    @NotNull(message = "目标日期不能为空")
    private LocalDate targetDate;
}
