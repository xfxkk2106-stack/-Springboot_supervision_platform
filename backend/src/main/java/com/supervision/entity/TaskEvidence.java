package com.supervision.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task_evidence")
public class TaskEvidence {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String imageUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadedAt;

    private Integer status;
}
