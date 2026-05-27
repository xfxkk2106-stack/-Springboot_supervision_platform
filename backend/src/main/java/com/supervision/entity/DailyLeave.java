package com.supervision.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("daily_leave")
public class DailyLeave {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private LocalDate leaveDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
