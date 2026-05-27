package com.supervision.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("room_member")
public class RoomMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private String displayName;

    private Integer isAdmin;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;

    private Integer isOnline;
}
