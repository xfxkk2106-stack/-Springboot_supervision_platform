package com.supervision.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("learning_plan")
public class LearningPlan {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private String planType;

    private String title;

    private LocalDate targetDate;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
