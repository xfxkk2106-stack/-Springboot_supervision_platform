package com.supervision.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("daily_task")
public class DailyTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private Long planId;

    private String subject;

    private String taskContent;

    private Integer isCompleted;

    private LocalDateTime completedAt;

    private LocalDate taskDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Integer sortOrder;

    private Integer fromPlan;

    // 非数据库字段
    @TableField(exist = false)
    private String displayName;
}
