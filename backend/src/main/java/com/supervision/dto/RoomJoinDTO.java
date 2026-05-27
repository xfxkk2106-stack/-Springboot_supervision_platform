package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoomJoinDTO {
    @NotBlank(message = "邀请码不能为空")
    @Size(min = 7, max = 7, message = "邀请码为7位")
    private String roomCode;

    @NotBlank(message = "名称不能为空")
    @Size(max = 20, message = "名称最多20个字符")
    private String displayName;
}
