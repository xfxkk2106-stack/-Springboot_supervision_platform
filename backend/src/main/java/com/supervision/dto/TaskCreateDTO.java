package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskCreateDTO {
    @NotBlank(message = "科目不能为空")
    private String subject;

    @NotBlank(message = "学习计划内容不能为空")
    private String taskContent;
}
