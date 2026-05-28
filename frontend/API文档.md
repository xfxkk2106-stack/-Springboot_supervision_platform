# 互相监督平台 - 前端 API 文档

## 目录

- [1. 基础配置](#1-基础配置)
- [2. 认证机制](#2-认证机制)
- [3. API 接口](#3-api-接口)
  - [3.1 认证模块 Auth](#31-认证模块-auth)
  - [3.2 房间模块 Room](#32-房间模块-room)
  - [3.3 计划模块 Plan](#33-计划模块-plan)
  - [3.4 任务模块 Task](#34-任务模块-task)
  - [3.5 证据模块 Evidence](#35-证据模块-evidence)
  - [3.6 审核模块 Review](#36-审核模块-review)
- [4. WebSocket 实时通信 (Netty)](#4-websocket-实时通信-netty)
  - [4.1 连接信息](#41-连接信息)
  - [4.2 消息格式](#42-消息格式)
  - [4.3 服务端推送事件](#43-服务端推送事件)
  - [4.4 客户端发送事件](#44-客户端发送事件)

---

## 1. 基础配置

| 配置项 | 值 |
|--------|-----|
| HTTP Base URL | `/api` |
| HTTP 超时时间 | 30000ms |
| WebSocket 端口 | `8081` (Netty 服务) |
| 开发代理 | Vite 将 `/api` 代理到 `http://localhost:8080` |

### 统一响应格式

所有 API 返回以下结构：

```json
{
  "code": 200,
  "message": "success",
  "data": <T>
}
```

错误码：`401` 未登录、`403` 无权限、`404` 资源不存在、`500` 服务器错误

---

## 2. 认证机制

### JWT Token

- 登录/加入房间后获取 JWT，存储于 `localStorage.token`
- 每次请求自动附加 `Authorization: Bearer <token>` 请求头
- Token 载荷包含：`memberId`、`roomId`、`displayName`

### 无需认证的接口

| 接口 | 说明 |
|------|------|
| `POST /api/room/create` | 创建房间 |
| `POST /api/room/join` | 加入房间 |
| `GET /api/auth/verify` | 验证 Token |

其余接口均需携带有效 JWT。

---

## 3. API 接口

### 3.1 认证模块 Auth

**前端文件：** `src/api/auth.js`

#### 验证 Token

```
GET /api/auth/verify
```

**响应 data：**

```json
{
  "memberId": 1,
  "roomId": 1,
  "displayName": "张三",
  "isAdmin": true
}
```

---

### 3.2 房间模块 Room

**前端文件：** `src/api/room.js`

#### 创建房间

```
POST /api/room/create
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| displayName | string | 是 | 昵称，最长 20 字符 |

**响应 data：**

```json
{
  "token": "eyJhbG...",
  "memberId": 1,
  "roomId": 1,
  "roomCode": "ABC1234",
  "displayName": "张三",
  "isAdmin": true
}
```

#### 加入房间

```
POST /api/room/join
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| roomCode | string | 是 | 房间邀请码，7 位 |
| displayName | string | 是 | 昵称，最长 20 字符 |

**响应 data：**

```json
{
  "token": "eyJhbG...",
  "memberId": 2,
  "roomId": 1,
  "roomCode": "ABC1234",
  "displayName": "李四",
  "isAdmin": false
}
```

#### 获取房间信息

```
GET /api/room/{roomCode}/info
```

**响应 data：**

```json
{
  "id": 1,
  "roomCode": "ABC1234",
  "creatorId": 1,
  "creatorName": "张三",
  "status": 1,
  "createdAt": "2024-01-01T00:00:00"
}
```

#### 获取房间成员列表

```
GET /api/room/{roomCode}/members
```

**响应 data：**

```json
[
  {
    "id": 1,
    "roomId": 1,
    "displayName": "张三",
    "isAdmin": 1,
    "isOnline": 1
  }
]
```

#### 检查是否为管理员

```
GET /api/room/check-admin
```

**响应 data：** `true` / `false`

#### 退出房间

```
POST /api/room/leave
```

**响应 data：** `"已退出房间"`

#### 注销房间（管理员）

```
POST /api/room/dissolve
```

**响应 data：** `"房间已注销"`

---

### 3.3 计划模块 Plan

**前端文件：** `src/api/plan.js`

#### 创建学习计划

```
POST /api/plan/create
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| planType | string | 是 | 计划类型（year/quarter/month/week） |
| title | string | 是 | 计划标题 |
| targetDate | string | 是 | 目标日期，格式 `YYYY-MM-DD` |

**响应 data：** `LearningPlan` 对象

#### 获取计划列表

```
GET /api/plan/list?type={planType}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 否 | 筛选计划类型 |

**响应 data：** `LearningPlan[]`

#### 获取成员计划列表

```
GET /api/plan/member/{memberId}?type={planType}
```

**响应 data：** `LearningPlan[]`（需同一房间）

#### 更新计划状态

```
PUT /api/plan/{id}/update
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | integer | 是 | 计划状态 |

**响应 data：** 更新后的 `LearningPlan`

#### 删除计划

```
DELETE /api/plan/{id}
```

**响应：** 成功无 data

---

### 3.4 任务模块 Task

**前端文件：** `src/api/task.js`

#### 创建每日任务

```
POST /api/task/create
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| subject | string | 是 | 科目名称 |
| taskContent | string | 是 | 任务内容 |

**响应 data：** `DailyTask` 对象

#### 获取今日所有成员任务

```
GET /api/task/today
```

**响应 data：** `DailyTask[]`

#### 获取我的今日任务

```
GET /api/task/my-today
```

**响应 data：** `DailyTask[]`

#### 获取指定成员今日任务

```
GET /api/task/member/{memberId}
```

**响应 data：** `DailyTask[]`

#### 获取成员历史任务

```
GET /api/task/member/{memberId}/history?days=30
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| days | integer | 否 | 查询天数，默认 30 |

**响应 data：** `DailyTask[]`（不含今日）

#### 完成任务

```
PUT /api/task/{id}/complete
```

**说明：** 需先上传证据，否则返回错误。完成后自动广播 WebSocket `task_completed` 事件。

**响应 data：** 更新后的 `DailyTask`

#### 删除任务

```
DELETE /api/task/{id}
```

**说明：** 仅未完成的任务可删除。

#### 请假

```
POST /api/task/leave
```

**说明：** 请假会删除当日未完成的任务，并广播 `member_leave_changed` 事件。

**响应 data：** `"已请假"`

#### 取消请假

```
DELETE /api/task/leave
```

**响应 data：** `"已取消请假"`

#### 获取房间完成状态

```
GET /api/task/room-status
```

**响应 data：**

```json
{
  "allCompleted": true,
  "hasTomorrowPlans": false,
  "members": [
    {
      "memberId": 1,
      "displayName": "张三",
      "totalTasks": 3,
      "completedTasks": 3,
      "isOnLeave": false,
      "allDone": true
    }
  ]
}
```

---

### 3.5 证据模块 Evidence

**前端文件：** `src/api/evidence.js`

#### 上传证据图片

```
POST /api/evidence/upload
Content-Type: multipart/form-data
```

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| taskId | Long | 是 | 关联的任务 ID |
| files | File[] | 是 | 证据图片文件（支持多张） |

**响应 data：** `TaskEvidence[]`

#### 获取任务证据列表

```
GET /api/evidence/task/{taskId}
```

**响应 data：**

```json
[
  {
    "id": 1,
    "taskId": 1,
    "imageUrl": "http://minio:9000/bucket/xxx.png",
    "status": 0,
    "uploadedAt": "2024-01-01T00:00:00"
  }
]
```

| status 值 | 含义 |
|-----------|------|
| 0 | 待审核 |
| 1 | 已通过 |
| 2 | 已驳回 |

#### 审核证据

```
POST /api/evidence/{id}/review
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| result | integer | 是 | 1=通过，2=驳回 |
| comment | string | 否 | 审核备注 |

**说明：**
- 不能审核自己的证据
- 审核通过后自动完成对应任务
- 广播 `evidence_reviewed` 事件，携带最新房间状态

**响应 data：** `EvidenceReview` 对象

#### 删除证据

```
DELETE /api/evidence/{id}
```

**说明：** 仅待审核(status=0)的自己的证据可删除。

---

### 3.6 审核模块 Review

**前端文件：** `src/api/review.js`

#### 创建/更新每日总结

```
POST /api/review/create
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| summary | string | 是 | 今日总结 |
| moodRating | integer | 是 | 心情评分 |

**响应 data：** `DailyReview` 对象

#### 获取今日总结

```
GET /api/review/today
```

**响应 data：** `DailyReview` 对象或 `null`

#### 创建明日计划

```
POST /api/tomorrow/create
```

**请求体：**

```json
{
  "plans": [
    { "subject": "数学", "taskContent": "复习高数第三章" },
    { "subject": "英语", "taskContent": "背单词50个" }
  ]
}
```

**说明：** 需所有今日任务已完成且证据已审核通过。

**响应 data：** `TomorrowPlan[]`

#### 获取明日计划

```
GET /api/tomorrow/today
```

**响应 data：** `TomorrowPlan[]`

---

## 4. WebSocket 实时通信 (Netty)

> **重要：** 本项目 WebSocket 服务端使用 **Netty** 框架实现（非 Spring WebSocket），运行在独立端口 `8081`。前端使用浏览器原生 `WebSocket` API 连接。

### 4.1 连接信息

| 配置项 | 值 |
|--------|-----|
| 协议 | `ws://`（WebSocket） |
| 地址 | `{hostname}:8081` |
| 路径 | `/ws/room/{roomCode}` |
| 认证 | Query 参数 `?token={JWT}` |
| 服务端实现 | Netty 4.1.x (`NettyWebSocketServer` + `NettyChannelHandler`) |
| 客户端实现 | 浏览器原生 `WebSocket` API (`useWebSocket.js`) |

**连接 URL 示例：**

```
ws://localhost:8081/ws/room/ABC1234?token=eyJhbG...
```

**重连机制：** 连接断开后自动 3 秒重连（非主动关闭时）。

### 4.2 消息格式

所有消息均为 JSON 格式：

```json
{
  "type": "事件类型",
  "data": <事件数据>
}
```

### 4.3 服务端推送事件

以下事件由后端通过 Netty 推送给前端：

| 事件类型 | 触发时机 | data 结构 | 接收方 |
|----------|----------|-----------|--------|
| `member_online` | 成员 WebSocket 连接建立 | `{ memberId, displayName, roomId, isAdmin }` | 房间内其他成员 |
| `member_offline` | 成员 WebSocket 断开（最后一个会话） | `{ memberId, displayName }` | 房间内其他成员 |
| `room_online_members` | 新成员连接时，发送当前在线成员列表 | `[{ memberId, displayName }]` | 仅新连接的成员 |
| `task_created` | 成员创建任务 | `taskId` (Long) | 房间内所有成员 |
| `task_completed` | 成员完成任务 | `{ taskId, roomStatus: { allCompleted, hasTomorrowPlans, members: [...] } }` | 房间内所有成员 |
| `member_leave_changed` | 成员请假或取消请假 | `{ memberId }` | 房间内所有成员 |
| `evidence_reviewed` | 证据被审核（通过/驳回） | `{ evidenceId, result, taskId, memberId, roomStatus }` | 房间内所有成员 |
| `tomorrow_converted` | 明日计划自动转为今日任务（每日零点） | `null` | 房间内所有成员 |
| `room_dissolved` | 管理员注销房间 | `null` | 房间内所有成员 |
| `member_left` | 成员主动退出房间 | `{ memberId, displayName }` | 房间内所有成员 |

#### roomStatus 结构说明

`task_completed` 和 `evidence_reviewed` 事件携带 `roomStatus`，用于前端即时更新房间状态：

```json
{
  "allCompleted": true,
  "hasTomorrowPlans": false,
  "members": [
    {
      "memberId": 1,
      "displayName": "张三",
      "totalTasks": 3,
      "completedTasks": 3,
      "isOnLeave": false,
      "allDone": true
    },
    {
      "memberId": 2,
      "displayName": "李四",
      "totalTasks": 2,
      "completedTasks": 2,
      "isOnLeave": false,
      "allDone": true
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| allCompleted | boolean | 房间内所有成员是否都已完成今日任务 |
| hasTomorrowPlans | boolean | 是否已有明日计划 |
| members[].memberId | Long | 成员 ID |
| members[].displayName | string | 成员昵称 |
| members[].totalTasks | integer | 今日任务总数 |
| members[].completedTasks | integer | 已完成任务数 |
| members[].isOnLeave | boolean | 是否请假 |
| members[].allDone | boolean | 该成员是否全部完成（或请假） |

### 4.4 客户端发送事件

前端通过 `send(type, data)` 发送消息，Netty 服务端会将消息**转发给房间内其他成员**（不包括发送者）：

| 事件类型 | data 结构 | 发送时机 |
|----------|-----------|----------|
| `task_completed` | `{ taskId }` | 前端完成任务 API 调用成功后 |

> **注意：** 前端发送的 `task_completed` 会被 Netty 透传给其他客户端，但服务端也会独立广播带 `roomStatus` 的 `task_completed`。前端处理器会优先使用服务端广播中携带的 `roomStatus`。

---

## 附录：实体结构参考

### DailyTask

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 任务 ID |
| memberId | Long | 所属成员 ID |
| subject | string | 科目 |
| taskContent | string | 任务内容 |
| isCompleted | integer | 0=未完成，1=已完成 |
| completedAt | LocalDateTime | 完成时间 |
| taskDate | LocalDate | 任务日期 |
| createdAt | LocalDateTime | 创建时间 |

### TaskEvidence

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 证据 ID |
| taskId | Long | 关联任务 ID |
| imageUrl | string | 图片 URL（MinIO） |
| status | integer | 0=待审核，1=通过，2=驳回 |
| uploadedAt | LocalDateTime | 上传时间 |

### LearningPlan

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 计划 ID |
| memberId | Long | 所属成员 ID |
| planType | string | 类型（year/quarter/month/week） |
| title | string | 标题 |
| targetDate | LocalDate | 目标日期 |
| status | integer | 状态 |
| createdAt | LocalDateTime | 创建时间 |

### TomorrowPlan

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 计划 ID |
| memberId | Long | 所属成员 ID |
| subject | string | 科目 |
| taskContent | string | 任务内容 |
| taskDate | LocalDate | 计划日期 |
| createdAt | LocalDateTime | 创建时间 |
