package com.supervision.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("tomorrow_plan")
public class TomorrowPlan {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private LocalDate taskDate;

    private String subject;

    private String taskContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Integer sortOrder;
}
