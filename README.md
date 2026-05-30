# 互相监督平台

一个实时协作学习平台，用户可以创建房间、制定每日计划、互相监督学习进度。

## 功能特性

### 房间管理
- 创建/加入学习房间（7位邀请码）
- 管理员/成员角色
- 实时在线状态显示
- 房间解散/退出

### 任务计划
- 制定每日学习计划（科目 + 内容）
- 任务完成状态追踪（需上传证据）
- 明日计划系统：
  - 全员完成后解锁「明日计划」按钮
  - 0 点前可自由编辑，0 点后自动生效（不可更改）
  - 计划任务带锁定标识，不可删除
- 请假成员自动跳过解锁检查

### 明日计划状态流转

```
可编辑状态（0点前）
  ↓ 全员完成 → 解锁「明日计划」按钮
  ↓ 用户填写 → 可自由修改/删除
  ↓
已生效状态（0点后）
  ↓ 自动转为今日任务
  ↓ 计划任务锁定，不可删除/修改
  ↓
用户未填写 + 0点后
  ↓ 显示「添加任务」按钮
  ↓ 其他用户未完成时按钮禁用
```

### 请假系统
- 成员请假申请
- 请假状态显示
- 自动跳过解锁检查

### 实时同步
- WebSocket 实时通信
- 成员加入/退出通知
- 任务状态实时更新
- 房间状态同步

### 身份认证
- HttpOnly Cookie + Redis 会话管理
- 永久用户标识（UID）
- 授权码跨设备登录
- 自动 Token 刷新

### 证据上传
- MinIO 对象存储
- 文件证据管理
- 审核功能

## 技术栈

### 前端
- **Vue 3** - 渐进式 JavaScript 框架
- **Vite** - 下一代前端构建工具
- **Element Plus** - Vue 3 组件库
- **Tailwind CSS** - 实用优先的 CSS 框架
- **Pinia** - Vue 状态管理

### 后端
- **Spring Boot 3.2** - Java 应用框架
- **MyBatis-Plus** - MyBatis 增强工具
- **Netty** - 异步事件驱动网络框架（WebSocket）
- **Spring Data Redis** - Redis 访问

### 数据库
- **MySQL 8** - 关系型数据库
- **Redis** - 内存缓存/会话存储
- **MinIO** - 对象存储（S3 兼容）

## 项目结构

```
互相监督平台/
├── frontend/                # Vue 3 前端
│   ├── src/
│   │   ├── api/            # API 接口
│   │   ├── composables/    # 组合式函数
│   │   ├── router/         # 路由配置
│   │   ├── stores/         # Pinia 状态
│   │   ├── utils/          # 工具函数
│   │   └── views/          # 页面组件
│   └── package.json
│
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/supervision/
│   │   ├── config/         # 配置类
│   │   ├── controller/     # REST 控制器
│   │   ├── entity/         # 实体类
│   │   ├── mapper/         # MyBatis Mapper
│   │   ├── netty/          # WebSocket 服务
│   │   ├── service/        # 业务逻辑
│   │   └── task/           # 定时任务（用户清理 + 凌晨计划转换）
│   └── pom.xml
│
└── README.md
```

## 快速开始

### 环境要求

- Node.js 18+
- Java 17+
- MySQL 8+
- Redis 6+
- MinIO (可选)

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

### 后端启动

1. 创建数据库并导入表结构：
```bash
mysql -u root -p < backend/src/main/resources/init.sql
```

2. 配置环境变量（参考 `backend/deploy/backend.env`）

3. 启动服务：
```bash
cd backend
mvn clean package
java -jar target/supervision-platform-1.0.0.jar
```

后端 API: http://localhost:8080
WebSocket: ws://localhost:8081

## 部署

### 使用 systemd（Linux）

```bash
# 复制服务文件
sudo cp backend/deploy/supervision-backend.service /etc/systemd/system/

# 配置环境变量
cp backend/deploy/backend.env /home/your-user/

# 启动服务
sudo systemctl enable supervision-backend
sudo systemctl start supervision-backend
```

### Nginx 配置

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /path/to/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # API 代理
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket 代理
    location /ws/ {
        proxy_pass http://localhost:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| DB_HOST | MySQL 主机 | localhost |
| DB_PORT | MySQL 端口 | 3306 |
| DB_NAME | 数据库名 | supervision_platform |
| REDIS_HOST | Redis 主机 | localhost |
| REDIS_PORT | Redis 端口 | 6379 |
| SERVER_PORT | 后端端口 | 8080 |
| NETTY_PORT | WebSocket 端口 | 8081 |
| CORS_ORIGINS | 允许的前端域名 | http://localhost:5173 |

## API 接口

### 任务相关
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/task/create` | 创建今日任务 |
| GET | `/api/task/my-today` | 获取我的今日任务 |
| GET | `/api/task/room-status` | 获取房间完成状态 |
| GET | `/api/task/plan-status` | 获取计划状态（按钮可用性） |
| PUT | `/api/task/{id}/complete` | 完成任务 |
| DELETE | `/api/task/{id}` | 删除任务 |

### 计划相关
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tomorrow/create` | 创建/替换明日计划 |
| GET | `/api/tomorrow/today` | 获取明日计划 |
| PUT | `/api/tomorrow/{id}` | 更新单条明日计划 |
| DELETE | `/api/tomorrow/{id}` | 删除单条明日计划 |

### 完整文档
详见 `frontend/API文档.md`

## 许可证

MIT License
