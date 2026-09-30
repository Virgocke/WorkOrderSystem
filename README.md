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
| `Messaging-Common` | — | — | 统一事件信封、Outbox 与幂等消费底座 |
| `Ticket-Event-Contract` | — | — | 工单事件 V1 业务快照与共享校验 |

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
mysql -u root -p WorkOrderSystem < sql/work_order_system_add_messaging.sql
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
| `sql/work_order_system_add_messaging.sql` | RocketMQ Outbox、消费幂等日志与通知来源事件唯一约束 |
| `sql/work_order_system_add_ticket_numbering.sql` | 工单编号事务计数器 |
| `sql/work_order_system_add_sla_defaults.sql` | 分类 SLA 可空并继承系统默认值，保留现有时限 |

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
- 分类的响应和解决 SLA 可分别设为 `null` 以使用 `slaDefaults`；创建工单时读取系统默认值（配置不存在时为 30/240 分钟），生成并固定截止时间。配置与分类更新只影响新工单，已有分类数值在迁移时保留。
- 创建工单时按 `ticketNoRule` 生成编号；同一前缀和日期范围使用 MySQL 事务计数器连续取号，序号达到配置位数上限时返回冲突错误，不截断或自动扩位。
- 处理人逐级升级待响应或处理中工单时，升级事实进入事务性 Outbox；通知服务为启用管理员生成站内通知，SLA 服务在独立消费组内同步升级级别。
- `escalationRules` 已接通自动升级：SLA 服务默认每 60 秒扫描一页（最多 200 单），工单服务锁定源记录并重新读取当前规则，升至最高匹配级别。响应/解决超时分钟数从对应截止时间起算，已响应不再检查响应超时，已解决/关闭/撤销不升级；空规则关闭自动升级，调整规则不回退已有级别。系统操作以 `SYSTEM` 留痕，通知与升级告警由同一个幂等消费事务创建。
- 升级通知的 `MANAGER` 表示当前处理人的部门负责人，未派单时为提交人部门负责人；不可用时回退到启用管理员，`ADMIN` 表示全部启用管理员。完全没有接收人时升级回滚并重试。自动升级沿用既有表，无新增数据库迁移。
- 自动升级需要 SLA、工单和通知服务开启 `WORK_ORDER_MESSAGING_ENABLED=true` 并运行 RocketMQ；SLA 已配置 `sla-monitor` Outbox 生产端。首次部署需一起更新 Ticket-Service、Sla-Monitor、Notification-Service 的公共事件契约。扫描周期可通过 `work-order.sla.escalation.scan-interval-ms` 调整，规则保存后由后续扫描读取，无需重启。
- 工单创建时发布 `TICKET_CREATED`；SLA 服务幂等初始化记录，派单引擎按分类技能、实时在办负载和历史表现生成 `ASSIGNMENT_PROPOSED`，工单服务在仍待分配且处理人未满额时条件确认并发送处理人通知。
- 智能派单基于技能、当前负载、SLA 与评分进行候选人推荐。`GET /api/configurations` 可读取权重配置，管理员通过 `PUT /api/configurations` 以版本号进行并发安全更新；权重保存后无需重启即可生效。
- 部署新版评分逻辑前，先执行 `sql/work_order_system_add_assignment_scoring.sql`。分类可配置所需技能，子分类无配置时继承最近上级；在办负载实时从工单状态计算。工单解决和评价分别保存归属处理人快照，转派后的业绩归最终提交解决的接手人；SLA 与评价按快照按需聚合，无定时重算。手动派单及转派复用引擎评分。
- 通知列表通过 `GET /api/notifications` 查询，未读数通过 `GET /api/notifications/unread-count` 查询；告警可经 `POST /api/alerts/{id}/handle` 标记为已处理。
- 用户、部门、处理人、技能标签与技能调整申请均已提供服务端接口；权限模型包含普通用户、处理人和管理员三类角色，并提供 RBAC 基础数据。

## 当前限制与后续工作

- 部分通知投递、全部标记已读、催办日志与配置审计查询仍待接入。
- OAuth2 Password Grant 只应作为本地兼容联调方案；面向浏览器或第三方客户端时应采用 Authorization Code + PKCE。
- 部署到生产前，请将配置中的本地连接信息、邮件凭据、MinIO 密钥和 JWT 签名密钥迁移到受控的外部配置系统。


### 工单通知渠道与邮件部署

`notificationChannels` 已接入派单、回复、催办、转派、升级、解决、关闭、撤销通知。配置为 `{"internal":true,"email":true}`：站内信始终开启，邮件可关闭，不提供短信。旧配置中的布尔 `sms` 字段读取时忽略，新保存只接受 `internal/email` 两项。认证服务的验证码邮件保持独立。

1. 先运行 `sql/work_order_system_add_notification_email.sql`，创建邮件投递队列；全量建库脚本也已包含该表。已有 `notification_records` 和消息幂等表继续复用。
2. 更新 Assign-Engine、Notification-Service 及前端，工单事件需开启 `WORK_ORDER_MESSAGING_ENABLED=true` 并接通 RocketMQ。
3. 当前本机已从 Authentication 提取相同 SMTP 账号至通知服务的 `application-mail-local.yaml`，由 `mail-local` profile 自动加载；文件已加入 Git 忽略。它是本机快照，认证服务邮箱配置变更后需同步更新。新环境可配置下表中的环境变量；这些变量也可以覆盖本地快照。默认配置位于 `Notification-Service/src/main/resources/application.yaml`。

本机已于 2026-09-30 执行邮件队列迁移并验证重复执行成功；没有发送真实邮件或重启业务服务。

| 环境变量 | 用途及默认值 |
| --- | --- |
| `NOTIFICATION_MAIL_HOST` | SMTP 服务器，需填写 |
| `NOTIFICATION_MAIL_PORT` | 默认 587 |
| `NOTIFICATION_MAIL_USERNAME` | SMTP 登录账号，需填写 |
| `NOTIFICATION_MAIL_PASSWORD` | SMTP 密码或服务商授权码，需填写 |
| `NOTIFICATION_MAIL_FROM` | 发件地址，默认使用登录账号 |
| `NOTIFICATION_MAIL_AUTH` | 默认 true |
| `NOTIFICATION_MAIL_STARTTLS` | 默认 true，启用且要求 STARTTLS |
| `NOTIFICATION_MAIL_SSL` | 默认 false；465 端口通常设 true，同时关闭 STARTTLS |
| `NOTIFICATION_MAIL_WORKER_ENABLED` | 默认 true；false 暂停工作者，已生成任务保留 |

邮件开关从后续通知消费起生效，已生成任务继续处理，不为历史已消费事件补发邮件。通知、邮件任务、消费日志在同一事务落库；工作者在事务外发送 SMTP，每 10 秒最多 20 封，租约 5 分钟，失败按 30/60/120/240 秒退避，最多尝试 5 次。`notification_email_deliveries` 保存邮箱/内容快照、重试状态及脱敏错误，关联的 EMAIL 通知同步 PENDING/SENT/FAILED。缺失或非法邮箱直接失败，站内信仍创建。SMTP 未配置或认证失败也记录失败并有限重试，不会假记成功；修正配置后，已达 FAILED 的任务不会自动重发，当前没有管理员重发入口。

站内信列表、未读数量及已读操作仅包含 INTERNAL。自动升级告警仍记录站内信的送达状态，邮件的投递结果查看独立队列，不用邮件失败覆盖已经成功的站内告警。

邮件投递采用可恢复任务队列：普通 SMTP 不支持端到端幂等键，服务器接受邮件后、成功状态提交前退出，恢复时可能重复发送。SENT 表示 SMTP 接受，不保证最终送达或阅读。本地临时 SMTP 测试只验证发送协议和内容，不等于真实邮箱验收。
