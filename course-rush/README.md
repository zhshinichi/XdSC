# 高校抢课系统 (course-rush)

《软件体系结构》课程项目。高并发抢课系统，演示 **缓存原子扣减防超卖 + Kafka 削峰 + 限流** 等架构手法。
架构设计文档见 `../软件体系结构_高校抢课系统_架构设计文档.md`。

## 技术栈
- 后端：Java 17 + Spring Boot 3.5（Spring Web / Data JPA / Data Redis / Kafka）
- 存储/中间件：MySQL 8、Redis 7、Kafka（KRaft）
- 前端：Vue 3 + Vite
- 测试：JUnit5 + H2（切片）+ Testcontainers（真实 Redis）+ @EmbeddedKafka

## 目录
```
course-rush/
├── src/main/java/edu/course/rush/
│   ├── user/         注册/登录/JWT
│   ├── course/       课程
│   ├── batch/        抢课批次(时间窗口)
│   ├── enrollment/   核心抢课链路 + Kafka 落库 + 退课/统计
│   └── common/       security(JWT/Authz) / ratelimit / error / web
├── src/main/resources/lua/   try_enroll.lua(原子防重+扣减) / rollback_enroll.lua / rate_limit.lua
├── frontend/         Vue3 + Vite 前端
├── loadtest/         loadtest.mjs(Node压测) + enroll-loadtest.jmx(JMeter)
└── docker-compose.yml  MySQL/Redis/Kafka
```

## 快速开始

### 1. 启动中间件（Docker）
```bash
docker compose up -d        # 起 MySQL(3306)/Redis(6379)/Kafka(9092)
```

### 2. 启动后端（端口 8088）
```bash
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```
> 默认 `app.enroll.persist-mode=async`（走 Kafka 异步落库）。若暂时不想用 Kafka，
> 可加 `--app.enroll.persist-mode=sync` 降级为同步写库。

### 3. 启动前端（端口 5173）
```bash
cd frontend
npm install
npm run dev
```
浏览器打开 **http://localhost:5173**（/api 已代理到后端 8088）。

### 演示流程
1. 注册一个**管理员**账号登录 → 管理后台：建批次（开放时间设为当前前后各 1 小时）→ 发布课程 → 点"预热"。
2. 注册若干**学生**账号 → 课程列表点"抢课"；满了会提示"已抢完"。
3. 管理后台"查看统计"可见 已选/容量/剩余。

## 运行测试
```bash
./mvnw verify     # 单元(surefire) + 集成(failsafe, 含 Testcontainers/EmbeddedKafka)
```
共 53 个测试。核心 **不超卖** 在 Redis 层与 DB 层各有一个并发测试（容量 20 / 200 并发 → 恰好 20）。
> 集成测试需要 Docker 在运行（Testcontainers 会拉起临时 Redis）。

## 压测（非功能测试）

### 方式一：Node 脚本（推荐，自动校验不超卖）
先确保后端已启动，然后：
```bash
node loadtest/loadtest.mjs 500 50        # 500并发抢50个名额
```
输出：抢中/已满/限流计数、吞吐、P50/P95/P99 延迟、以及"不超卖"校验。

### 方式二：JMeter
1. 用管理员建好开放批次+课程并预热，记下课程ID。
2. 打开 `loadtest/enroll-loadtest.jmx`，把用户变量 `COURSE_ID` 改成该课程ID。
3. 设置线程数后运行，查看"聚合报告"。

## 主要接口
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /api/auth/register | - | 注册(STUDENT/ADMIN) |
| POST | /api/auth/login | - | 登录，返回 JWT |
| GET | /api/courses | - | 课程列表 |
| POST | /api/courses | ADMIN | 发布课程 |
| GET/POST | /api/batches | -/ADMIN | 批次查询/创建 |
| POST | /api/enroll | 登录 | **抢课**(限流) |
| DELETE | /api/enroll/{courseId} | 登录 | 退课 |
| GET | /api/my/enrollments | 登录 | 我的课表 |
| POST | /api/admin/courses/{id}/preheat | ADMIN | 预热库存 |
| GET | /api/admin/courses/{id}/stats | ADMIN | 选课统计 |
