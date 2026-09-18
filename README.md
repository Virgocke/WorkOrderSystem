# WorkOrderSystem 后端骨架说明

## 动态分配权重

系统配置接口由 Assign-Engine（63020）提供，网关将 `/api/configurations` 转发至该服务。
管理员通过 `GET /api/configurations` 查询解析后的配置值和版本号，再通过 `PUT /api/configurations`
提交 `{ items: [{ configKey, value, version }] }`，保存成功返回 `true`。
版本号与所有 Long 字段一样按字符串传输，首次尚未保存的默认配置版本为 `"0"`。
版本冲突返回 HTTP 409、业务码 1080；保存和修改记录写入同一事务，整批成功或整批回滚。

`assignWeights` 的 `skill/load/sla/rating` 必须为数字，每项在 0 至 99999999.99 之间，最多两位小数，
总和大于零即可。推荐前读取一次数据库，所有候选人采用同一份不可变快照；
候选人响应新增 `weights`，包含实际权重和字符串版本号。权重保存后供后续推荐使用，无需重启。
只有配置记录不存在时才使用 `assignment.weights` 默认值，数据库故障不静默退回默认值。

已有数据库启动新版本前执行 `sql/work_order_system_add_configuration_version.sql`；
新建数据库使用更新后的 schema。编号、SLA、通知和升级规则可持久化保存，其业务消费尚未接入，
代码已用 `//todo` 标注。配置修改记录目前存入 `configuration_change_logs`，审计查询页面待接入。

## 工单催办次数

工单表使用独立的 `remind_count` 字段记录催办次数，默认 0，每张工单最多催办 3 次。
已有数据库须先执行 `sql/work_order_system_add_remind_count.sql`，再启动更新后的工单服务；脚本可重复执行，已有工单从 0 次开始。
新建数据库使用更新后的 `sql/work_order_system_schema.sql`。
工单列表及详情的 `TicketResponse` 返回 `remindCount`。催办仅累计该次数，升级级别 `escalatedLevel` 和 SLA 状态保持原值；催办日志及通知待接入。

## 通知服务（63030）

`GET /api/notifications?page=1&pageSize=10&status=UNREAD` 查询当前登录用户的通知列表，
接收人从 Bearer JWT 的 `user_id` 读取；`status` 可为 `UNREAD`、`READ` 或空。
分页默认第 1 页、每页 10 条，返回 `Result<PageResult<NotificationRecord>>`，`total` 为筛选后的总条数。
列表按创建时间及 ID 倒序，左关联工单编号，系统通知的 `ticketId`、`ticketNo` 保留为 `null`。
数据库状态 `READ` 返回已读，其他投递状态返回未读；ID 按字符串传输，时间格式为 `yyyy-MM-dd HH:mm:ss`。

`GET /api/notifications/unread-count` 返回当前登录用户的未读通知数，响应 `data` 为 `{ "count": 5 }`。
统计与列表的 `UNREAD` 筛选使用相同规则，数量按 JSON 数字传输，没有未读通知时返回 0。

网关将 `/api/notifications/**` 转发至 `Notification` 服务。标记已读、全部已读及消息消费、渠道投递
尚未接入，已在代码中用 `//todo` 标注；前端通知页调用这些接口时仍需后续实现。

## 用户服务（63070）

当前用户服务使用内存数据，便于前端在数据库接入前联调。所有接口都要求携带 OAuth2 签发的 Bearer JWT；接口统一以 `Result` 返回：

- `POST /api/users`：创建用户
- `GET /api/users/{id}`：查询用户资料
- `GET /api/users/handlers`：查询可派单的处理人
- `PUT /api/users/{id}/handler-profile`：维护处理人容量与技能

用户资料不返回密码。后续将 `InMemoryUserDirectoryService` 替换为 MyBatis 实现，并接入 `users`、`handler_profiles`、`handler_skills` 表即可，控制器和接口模型无需改变。

## 认证服务（63071，OAuth2 + JWT）

- `POST /oauth/token`：标准 OAuth2 令牌端点，签发 JWT 访问令牌
- `GET /api/auth/me`：获取当前登录用户

本地联调以 `password` 授权模式获取令牌：

```bash
curl -X POST http://localhost:63071/oauth/token ^
  -d "grant_type=password" ^
  -d "username=admin" ^
  -d "password=change-me" ^
  -d "client_id=work-order-web" ^
  -d "client_secret=change-me-client-secret"
```

认证服务返回的 `access_token` 是 JWT。访问受保护资源时传入：

```http
Authorization: Bearer <access_token>
```

当前只提供本地演示账户 `admin / change-me`。开发环境使用对称 JWT 签名密钥，配置于 `OAUTH_JWT_SIGNING_KEY`；生产环境必须使用环境变量配置高强度密钥，并建议改为 RSA/ECDSA 非对称签名，使资源服务只保存公钥。对外 OAuth2 客户端应使用 `authorization_code + PKCE`，不要使用当前的内部 password grant。
