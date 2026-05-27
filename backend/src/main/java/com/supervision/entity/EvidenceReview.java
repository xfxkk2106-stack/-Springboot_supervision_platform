package com.supervision.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("evidence_review")
public class EvidenceReview {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long evidenceId;

    private Long reviewerId;

    private Integer result;

    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime reviewedAt;
}
