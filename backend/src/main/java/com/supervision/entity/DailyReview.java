package com.supervision.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("daily_review")
public class DailyReview {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private LocalDate reviewDate;

    private String summary;

    private Integer moodRating;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
