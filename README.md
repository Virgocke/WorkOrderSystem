# WorkOrderSystem 后端骨架说明

## 工单催办次数

工单表使用独立的 `remind_count` 字段记录催办次数，默认 0，每张工单最多催办 3 次。
已有数据库须先执行 `sql/work_order_system_add_remind_count.sql`，再启动更新后的工单服务；脚本可重复执行，已有工单从 0 次开始。
新建数据库使用更新后的 `sql/work_order_system_schema.sql`。
工单列表及详情的 `TicketResponse` 返回 `remindCount`。催办仅累计该次数，升级级别 `escalatedLevel` 和 SLA 状态保持原值；催办日志及通知待接入。

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
