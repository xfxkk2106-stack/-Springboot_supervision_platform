package com.supervision.common;

public class ResultCode {
    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int INTERNAL_ERROR = 500;

    // 业务错误码 1xxx
    public static final int ROOM_NOT_FOUND = 1001;
    public static final int ROOM_PASSWORD_ERROR = 1002;
    public static final int ROOM_FULL = 1003;
    public static final int ROOM_CODE_DUPLICATE = 1004;

    // 任务相关 2xxx
    public static final int TASK_NOT_FOUND = 2001;
    public static final int TASK_NO_EVIDENCE = 2002;
    public static final int TASK_ALREADY_COMPLETED = 2003;
    public static final int TASK_LOCKED = 2004;

    // 计划相关 3xxx
    public static final int PLAN_NOT_FOUND = 3001;
    public static final int CANNOT_CREATE_TOMORROW = 3002;

    // 证据相关 4xxx
    public static final int EVIDENCE_NOT_FOUND = 4001;
    public static final int EVIDENCE_ALREADY_REVIEWED = 4002;
}
