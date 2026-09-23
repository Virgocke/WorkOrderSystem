# 智能工单调度系统（后端）

基于 Spring Boot、Spring Cloud Alibaba 与 OAuth2/JWT 构建的微服务工单系统后端。系统提供工单流转、智能派单、SLA 监控、通知告警、用户与组织管理，以及报表查询等能力。

## 技术栈

- Java 8、Maven
- Spring Boot 2.3.7.RELEASE、Spring Cloud Hoxton.SR9、Spring Cloud Alibaba 2.2.6.RELEASE
- Nacos（服务注册与发现）、Spring Cloud Gateway、OpenFeign
- MySQL 8、MyBatis-Plus、Redis、MinIO
- OAuth2 Password Grant（仅用于本地联调）与 JWT

## 服务组成

| 模块 | 服务名 | 端口 | 职责 |
| --- | --- | ---: | --- |
| `api-gateway` | `gateway` | 63010 | 统一入口、鉴权与路由转发 |
| `Authentication` | `authentication-service` | 63071 | OAuth2 登录、注册、密码重置与当前用户查询 |
| `User-Service/UserAPI` | `userAndHandler-service` | 63070 | 用户、部门、处理人、技能与技能调整申请 |
| `Ticket-Service/TicketAPI` | `ticket-service` | 64060 | 工单、分类、附件、状态流转与评价 |
| `Assign-Engine` | `Assign-Engine` | 64020 | 候选处理人推荐、派单记录与派单权重配置 |
| `Sla-Monitor` | `Sla-Monitor` | 63050 | SLA 记录与超时监控 |
| `Notification-Service` | `Notification` | 63030 | 站内通知与告警处理 |
| `Search-Service` | `Search` | 63040 | 工作台、看板、评分与报表查询 |
| `Base-Utility` | — | — | 公共模型、异常、Jackson 与 MyBatis 配置 |
| `Security-Common` | — | — | JWT 鉴权公共组件 |

> 业务请求建议统一通过网关 `http://localhost:63010` 访问，不直接依赖各服务端口。

## 快速开始

### 1. 准备运行环境

请先安装并启动以下依赖：

- JDK 8
- Maven 3.6+
- MySQL 8（建议使用 `utf8mb4`）
- Nacos 2.x，地址为 `localhost:8848`
- Redis，默认地址为 `localhost:6379`（认证服务的 Redis 配置已预置）
- MinIO，默认地址为 `http://localhost:9000`（启用附件上传时需要）

在 Nacos 中使用命名空间 `dev`、分组 `WorkOrder-project`，这与各服务的 `bootstrap.yaml` 默认配置一致。

### 2. 初始化数据库

在 MySQL 中按顺序执行：

```powershell
mysql -u root -p < sql/work_order_system_schema.sql
mysql -u root -p WorkOrderSystem < sql/work_order_system_seed.sql
```

已有数据库升级时，不要重复执行建表脚本；请按已启用功能执行对应的增量脚本：

| 脚本 | 用途 |
| --- | --- |
| `sql/work_order_system_add_attachments.sql` | 附件元数据 |
| `sql/work_order_system_migrate_attachment_ids.sql` | 附件 ID 迁移 |
| `sql/work_order_system_add_configuration_version.sql` | 派单配置乐观锁版本与修改日志 |
| `sql/work_order_system_add_remind_count.sql` | 工单催办次数 |
| `sql/work_order_system_add_skill_applications.sql` | 技能调整申请 |
| `sql/work_order_system_add_unique_phone.sql` | 手机号唯一约束 |
| `sql/work_order_system_rbac.sql` | RBAC 角色与权限数据 |

### 3. 配置本地环境

各服务配置位于其 `src/main/resources/bootstrap.yaml`。启动前请根据本机环境调整 MySQL、Redis、MinIO、Nacos 与邮件配置；不要将真实密码、访问密钥或生产 JWT 密钥提交到仓库。

所有需要验签的服务必须使用同一 JWT 签名密钥。开发环境可在 PowerShell 中设置：

```powershell
$env:OAUTH_JWT_SIGNING_KEY = "replace-with-a-local-development-secret"
```

生产环境应通过安全的配置管理系统注入高强度密钥，并改用非对称签名方案，使资源服务仅持有公钥。

### 4. 构建与启动

在项目根目录执行：

```powershell
mvn clean package -DskipTests
```

启动顺序建议为认证服务、用户服务、工单服务、其余业务服务，最后启动网关。每个命令应在独立终端中运行：

```powershell
mvn -pl Authentication -am spring-boot:run
mvn -pl User-Service/UserAPI -am spring-boot:run
mvn -pl Ticket-Service/TicketAPI -am spring-boot:run
mvn -pl Assign-Engine -am spring-boot:run
mvn -pl Sla-Monitor -am spring-boot:run
mvn -pl Notification-Service -am spring-boot:run
mvn -pl Search-Service -am spring-boot:run
mvn -pl api-gateway -am spring-boot:run
```

运行测试：

```powershell
mvn test
```

## 认证与调用示例

初始化脚本提供本地演示用户：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `Admin@123` | 管理员 |
| `handler` | `Handler@123` | 处理人 |
| `user` | `User@123` | 普通用户 |

使用认证服务获取令牌（Password Grant 仅限内部本地联调）：

```powershell
curl.exe -X POST http://localhost:63071/oauth/token `
  -d "grant_type=password" `
  -d "username=admin" `
  -d "password=Admin@123" `
  -d "client_id=WorkOrderSystem" `
  -d "client_secret=WorkOrderSystemSecret"
```

返回结果中的 `access_token` 为 JWT；调用受保护接口时带上：

```http
Authorization: Bearer <access_token>
```

网关会将 `/api/oauth/**` 转发到认证服务的 `/oauth/**`，并将以下路径路由到对应服务：

| 网关路径 | 服务 |
| --- | --- |
| `/api/auth/**`、`/api/oauth/**` | 认证服务 |
| `/api/users/**`、`/api/handlers/**`、`/api/handler-skills/**`、`/api/departments/**`、`/api/skills/**` | 用户与处理人服务 |
| `/api/tickets/**`、`/api/ticket-categories/**`、`/api/files/**` | 工单服务 |
| `/api/assign-engine/**`、`/api/configurations/**`、`/api/assignment-records/**` | 智能派单服务 |
| `/api/sla/**` | SLA 监控服务 |
| `/api/notifications/**`、`/api/alerts/**` | 通知服务 |
| `/api/dashboard/**`、`/api/handler/**`、`/api/reports/**`、`/api/ratings/**` | 查询与报表服务 |

## 已实现的业务说明

- 工单包含分类、优先级、处理人、状态、SLA 时限、操作记录、评分与附件元数据；每张工单最多催办 3 次，`remindCount` 与升级级别独立。
- 智能派单基于技能、当前负载、SLA 与评分进行候选人推荐。`GET /api/configurations` 可读取权重配置，管理员通过 `PUT /api/configurations` 以版本号进行并发安全更新；权重保存后无需重启即可生效。
- 通知列表通过 `GET /api/notifications` 查询，未读数通过 `GET /api/notifications/unread-count` 查询；告警可经 `POST /api/alerts/{id}/handle` 标记为已处理。
- 用户、部门、处理人、技能标签与技能调整申请均已提供服务端接口；权限模型包含普通用户、处理人和管理员三类角色，并提供 RBAC 基础数据。

## 当前限制与后续工作

- 部分通知投递、全部标记已读、催办日志、SLA 升级规则消费与配置审计查询仍待接入。
- OAuth2 Password Grant 只应作为本地兼容联调方案；面向浏览器或第三方客户端时应采用 Authorization Code + PKCE。
- 部署到生产前，请将配置中的本地连接信息、邮件凭据、MinIO 密钥和 JWT 签名密钥迁移到受控的外部配置系统。
