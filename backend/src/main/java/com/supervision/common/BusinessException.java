package com.supervision.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(ResultCode.INTERNAL_ERROR, message);
    }

    public static BusinessException roomNotFound() {
        return new BusinessException(ResultCode.ROOM_NOT_FOUND, "房间不存在");
    }

    public static BusinessException roomPasswordError() {
        return new BusinessException(ResultCode.ROOM_PASSWORD_ERROR, "房间密码错误");
    }

    public static BusinessException roomFull() {
        return new BusinessException(ResultCode.ROOM_FULL, "房间已满");
    }

    public static BusinessException taskNoEvidence() {
        return new BusinessException(ResultCode.TASK_NO_EVIDENCE, "请先上传学习证据");
    }

    public static BusinessException cannotCreateTomorrow() {
        return new BusinessException(ResultCode.CANNOT_CREATE_TOMORROW, "请先完成今日任务并审核对方证据");
    }
}
