# 智能工单调度系统（后端）

基于 Spring Boot、Spring Cloud Alibaba 与 OAuth2/JWT 的微服务工单系统后端，提供工单流转、智能派单、SLA 监控、通知告警、组织与技能管理、审计和报表查询。

MySQL 保存业务数据；RocketMQ 配合事务性 Outbox 驱动派单、SLA 和通知；Elasticsearch 提供关键词检索，业务响应仍从 MySQL 读取。本文按当前代码说明部署与功能边界。

## 技术栈

| 组件 | 当前项目版本或用途 |
| --- | --- |
| Java / Maven | Java 8，多模块 Maven 工程 |
| Spring Boot | `2.3.7.RELEASE` |
| Spring Cloud / Alibaba | `Hoxton.SR9` / `2.2.6.RELEASE` |
| Nacos / Gateway / OpenFeign | 服务注册发现、网关路由、服务间调用 |
| MySQL / MyBatis-Plus | MySQL 8，MyBatis-Plus `3.4.1` |
| Redis / MinIO | 认证相关缓存、附件对象存储 |
| RocketMQ | Spring 集成 `2.2.3`；本地 Docker 配置使用 Broker `4.9.6` |
| Elasticsearch / IK / Kibana | 本地配置均为 `7.12.1`，IK 提供中文分词，Kibana 用于查询调试 |
| Lombok | `1.18.32` |
| OAuth2 / JWT | 当前保留 Password Grant 供内部本地联调 |

## 服务组成

| 模块 | 服务名 | 默认端口 | 职责 |
| --- | --- | ---: | --- |
| `api-gateway` | `gateway` | 63010 | 统一入口、鉴权与路由转发 |
| `Authentication` | `authentication-service` | 63071 | 登录、注册、密码重置与当前用户查询 |
| `User-Service/UserAPI` | `userAndHandler-service` | 63070 | 用户、部门、处理人、技能与技能调整申请 |
| `Ticket-Service/TicketAPI` | `ticket-service` | 64060 | 工单、分类、附件、状态流转与评价 |
| `Assign-Engine` | `Assign-Engine` | 65020 | 派单评分、候选推荐、派单记录与系统配置 |
| `Sla-Monitor` | `Sla-Monitor` | 63050 | SLA 记录、超时状态与自动升级 |
| `Notification-Service` | `Notification` | 63030 | 站内通知、邮件任务与升级告警 |
| `Search-Service` | `Search` | 63040 | 关键词搜索、工作台、看板、审计与报表 |
| `Base-Utility` | — | — | 公共模型、异常、Jackson 与 MyBatis 配置 |
| `Security-Common` | — | — | JWT 鉴权与当前用户解析 |
| `Messaging-Common` | — | — | 事件信封、Outbox、重试恢复与幂等消费 |
| `Ticket-Event-Contract` | — | — | 工单事件 V1 快照与共享校验 |
| `Skill-Event-Contract` | — | — | 技能审核事件快照与共享校验 |

`UserModel` / `UserService`、`TicketModel` / `TicketService` 是各业务服务内部的模型和实现模块，不独立启动。业务请求统一通过网关 `http://localhost:63010` 访问；上表端口以各模块的 `bootstrap.yaml` 为准。

## 已实现的功能

- **工单生命周期**：提交、自动/手动派单、响应、回复、催办、转派、升级、提交解决、确认、关闭、撤销和评价，包含状态历史、操作记录、内部备注与附件。每张工单最多催办 3 次，催办次数与升级级别独立。
- **编号与 SLA 默认值**：按 `ticketNoRule` 和 MySQL 事务计数器生成编号，序号达到配置位数上限时返回冲突。分类响应/解决 SLA 可分别设为 `null`，创建时读取 `slaDefaults`；配置不存在时使用 30/240 分钟。截止时间在创建时固定，后续配置更新不追溯修改旧工单。
- **统一派单评分**：自动派单、手动派单和转派复用技能匹配、实时在办负载及历史 SLA/评价评分。子分类未配置技能时继承最近上级；负载由待响应和处理中工单实时计算。解决与评价保存处理人归属快照，转派后的业绩归实际提交解决的接手人。管理员修改 `assignWeights` 后无需重启生效，配置保存使用版本号控制并发。
- **SLA 与升级**：工单创建、解决、关闭、撤销和升级事件更新 SLA 记录；`escalationRules` 驱动自动升级。默认每 60 秒扫描一页、最多 200 单，工单服务锁定源记录并复核规则后升至最高匹配级别；空规则关闭自动升级，规则调整不回退已有级别，终态工单不再升级。
- **通知与告警**：派单、回复、催办、转派、升级、解决、关闭、撤销及技能审核结果生成通知。支持站内信、未读数、批量已读和全部已读；邮件通过独立任务队列发送。升级告警保存事件发生时的工单编号/标题快照，历史展示不再同步请求工单详情。
- **组织、技能和查询**：用户、部门树、处理人档案、技能标签、技能申请与审核；普通用户、处理人、管理员三类权限；管理员看板、处理人工作台、报表、评价明细、工单及配置审计。关键词查询的具体路径见下文。

## 本地启动

以下命令在包含 `pom.xml` 的 `WorkOrderSystem` 目录执行，示例终端为 PowerShell。

### 1. 准备依赖

| 依赖 | 默认地址 | 使用范围 |
| --- | --- | --- |
| JDK / Maven | JDK 8、Maven 3.6+ | 编译与运行后端 |
| MySQL | `localhost:3306`，数据库 `WorkOrderSystem` | 业务与消息持久化，字符集 `utf8mb4` |
| Nacos | `localhost:8848` | 服务注册与发现 |
| Redis | `localhost:6379` | 认证服务 |
| MinIO | `http://localhost:9000` | 附件上传与预览 |
| RocketMQ NameServer / Broker | `localhost:9876` / `localhost:10911` | 异步派单、SLA 同步及事件通知 |
| Elasticsearch / Kibana | `http://127.0.0.1:9200` / `http://127.0.0.1:5601` | 关键词检索及搜索调试 |

各服务默认使用 Nacos 命名空间 ID `dev`、分组 `WorkOrder-project`，需与 Nacos 中实际配置一致。

消息和 Elasticsearch 开关默认关闭。关闭消息后，自动派单、事件驱动的 SLA 同步和通知链路不会运行；关闭 ES 后，已接入 ES 的关键词查询会失败，无关键词列表仍可查询 MySQL。

### 2. 初始化数据库

**空库首次部署**按顺序执行：

```powershell
mysql --default-character-set=utf8mb4 -u root -p --execute="SOURCE sql/work_order_system_schema.sql"
mysql --default-character-set=utf8mb4 -u root -p WorkOrderSystem --execute="SOURCE sql/work_order_system_add_messaging.sql"
mysql --default-character-set=utf8mb4 -u root -p WorkOrderSystem --execute="SOURCE sql/work_order_system_seed.sql"
```

全量建表脚本已包含当前业务表、邮件任务、技能审核关联、告警快照、评分归属与 SLA 终态字段；消息 Outbox 和消费日志由第二步补充。第三步初始化演示账号和权限。PowerShell 不支持 Bash 风格的 `<` 输入重定向，示例使用 MySQL 客户端的 `SOURCE`。

**已有数据库升级**按缺失表/字段执行对应增量脚本，不重复运行全量建表脚本。示例：

```powershell
mysql --default-character-set=utf8mb4 -u root -p WorkOrderSystem --execute="SOURCE sql/work_order_system_add_alert_ticket_snapshot.sql"
```

下表文件均位于 `sql/`；先完成数据库升级，再启动使用新字段的服务。

| 增量脚本 | 用途 |
| --- | --- |
| `work_order_system_rbac.sql` | RBAC 角色与权限表 |
| `work_order_system_add_unique_phone.sql` | 手机号唯一约束，已有重复手机号需先处理 |
| `work_order_system_add_attachments.sql` | 附件元数据表 |
| `work_order_system_migrate_attachment_ids.sql` | 旧附件引用迁移为附件 ID |
| `work_order_system_add_configuration_version.sql` | 配置版本号与修改日志 |
| `work_order_system_add_remind_count.sql` | 独立催办次数 |
| `work_order_system_add_skill_applications.sql` | 技能调整申请表 |
| `work_order_system_add_messaging.sql` | Outbox、消费日志及通知事件唯一约束 |
| `work_order_system_add_ticket_terminal_sla.sql` | SLA 终态与终态时间 |
| `work_order_system_add_assignment_scoring.sql` | 分类技能与解决/评价处理人快照 |
| `work_order_system_add_ticket_numbering.sql` | 工单编号事务计数器 |
| `work_order_system_add_sla_defaults.sql` | 分类 SLA 可空并继承系统默认值 |
| `work_order_system_add_notification_email.sql` | 可恢复邮件投递队列 |
| `work_order_system_add_skill_review_audit.sql` | 技能审核通知关联与审计索引 |
| `work_order_system_add_alert_ticket_snapshot.sql` | 升级告警工单编号/标题快照 |

`add_ticket_terminal_sla`、`add_assignment_scoring` 含直接新增列的 DDL，不能重复执行。旧评分数据按当前可确认的处理人补齐，旧告警按工单当前摘要补录，均不能还原已经丢失的历史事实；具体条件以各脚本注释为准。

### 3. 配置服务

各服务的连接配置在 `src/main/resources/bootstrap.yaml`；Search-Service 的 ES 配置、Notification-Service 的邮件配置位于各自的 `application.yaml`。启动前调整本机 MySQL、Redis、MinIO、Nacos 和邮件连接信息。真实密码与密钥通过环境变量或受控配置注入。

所有验签服务使用同一 JWT 签名密钥。在各服务启动终端设置相同值，IDE 启动时填写到运行配置：

```powershell
$env:OAUTH_JWT_SIGNING_KEY = "replace-with-a-local-development-secret"
```

消息、ES 和 SMTP 的开关及配置见后续各节；PowerShell 设置的环境变量只对该终端及其启动的子进程生效。

### 4. 构建与启动

先在根目录构建、运行测试，并将公共模块安装到本地 Maven 仓库：

```powershell
mvn clean install
```

本项目 POM 当前没有配置 Spring Boot 打包插件。可在 IDEA 中运行各服务的 `*Application` 主类；命令行启动则显式指定与项目一致的 Boot 插件版本，每条命令在独立终端运行：

```powershell
mvn -f Authentication/pom.xml org.springframework.boot:spring-boot-maven-plugin:2.3.7.RELEASE:run
mvn -f User-Service/UserAPI/pom.xml org.springframework.boot:spring-boot-maven-plugin:2.3.7.RELEASE:run
mvn -f Search-Service/pom.xml org.springframework.boot:spring-boot-maven-plugin:2.3.7.RELEASE:run
mvn -f Ticket-Service/TicketAPI/pom.xml org.springframework.boot:spring-boot-maven-plugin:2.3.7.RELEASE:run
mvn -f Assign-Engine/pom.xml org.springframework.boot:spring-boot-maven-plugin:2.3.7.RELEASE:run
mvn -f Sla-Monitor/pom.xml org.springframework.boot:spring-boot-maven-plugin:2.3.7.RELEASE:run
mvn -f Notification-Service/pom.xml org.springframework.boot:spring-boot-maven-plugin:2.3.7.RELEASE:run
mvn -f api-gateway/pom.xml org.springframework.boot:spring-boot-maven-plugin:2.3.7.RELEASE:run
```

启动基础设施后，再启动认证、用户和搜索服务、其余业务服务，最后启动网关。不要使用 `-am spring-boot:run` 同时运行整个依赖链，父 POM 和公共模块没有可启动的主类。

## 认证与网关

种子脚本提供以下本地演示账号：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `Admin@123` | 管理员 |
| `handler` | `Handler@123` | 处理人 |
| `user` | `User@123` | 普通用户 |

通过网关获取令牌：

```powershell
curl.exe -X POST http://localhost:63010/api/oauth/token `
  -d "grant_type=password" `
  -d "username=admin" `
  -d "password=Admin@123" `
  -d "client_id=WorkOrderSystem" `
  -d "client_secret=WorkOrderSystemSecret"
```

`client_id` / `client_secret` 使用认证服务当前配置；示例为本地默认值。受保护接口携带返回的 JWT：

```http
Authorization: Bearer <access_token>
```

网关移除 `/api` 前缀后转发：

| 网关路径 | 服务 |
| --- | --- |
| `/api/auth/**`、`/api/oauth/**` | Authentication |
| `/api/users/**`、`/api/handlers/**`、`/api/handler-skills/**`、`/api/departments/**`、`/api/skills/**`、`/api/skill-applications/**` | UserAPI |
| `/api/tickets/**`、`/api/ticket-categories/**`、`/api/files/**` | TicketAPI |
| `/api/assign-engine/**`、`/api/configurations/**`、`/api/assignment-records/**` | Assign-Engine |
| `/api/sla/**` | Sla-Monitor |
| `/api/notifications/**`、`/api/alerts/**` | Notification-Service |
| `/api/dashboard/**`、`/api/handler/**`、`/api/reports/**`、`/api/ratings/**`、`/api/audit-logs`、`/api/audit-logs/**`、`/api/internal/search/**` | Search-Service |

## RocketMQ 与异步业务

本地 Docker 配置位于 `deploy/rocketmq/docker-compose.yml`。启动后确认 Broker 已注册，再创建工单和技能事件 Topic：

```powershell
docker compose -f deploy/rocketmq/docker-compose.yml up -d
docker exec workorder-rocketmq-broker sh mqadmin clusterList -n namesrv:9876
docker exec workorder-rocketmq-broker sh mqadmin updateTopic -n namesrv:9876 -c DefaultCluster -t wo-ticket-event
docker exec workorder-rocketmq-broker sh mqadmin updateTopic -n namesrv:9876 -c DefaultCluster -t wo-skill-event
```

在 UserAPI、TicketAPI、Assign-Engine、Sla-Monitor、Notification-Service 的启动环境中配置：

```powershell
$env:WORK_ORDER_MESSAGING_ENABLED = "true"
$env:ROCKETMQ_NAME_SERVER = "localhost:9876"
$env:WORK_ORDER_TICKET_EVENT_TOPIC = "wo-ticket-event"
$env:WORK_ORDER_SKILL_EVENT_TOPIC = "wo-skill-event"
$env:WORK_ORDER_OUTBOX_ENABLED = "true"
```

业务修改和事件 Outbox 在同一 MySQL 事务提交；后台发送器投递 RocketMQ，消费者将幂等日志与业务结果在同一事务落库。发送重试、Outbox 恢复及各消费组可独立运行；MySQL 提交不代表下游已处理完毕。

- **自动派单**：创建工单发布 `TICKET_CREATED`，SLA 初始化记录，派单引擎生成 `ASSIGNMENT_PROPOSED`，工单服务在仍待分配且处理人未满额时条件确认，再发布派单通知事件。
- **自动升级**：SLA 扫描读取规则，工单服务复核并发布升级事件；通知服务在幂等事务中创建通知与告警。`MANAGER` 指当前处理人的部门负责人，未派单时为提交人部门负责人，不可用时回退启用管理员；`ADMIN` 指全部启用管理员。没有接收人时升级失败，后续扫描重试。扫描周期可通过 `work-order.sla.escalation.scan-interval-ms` 调整。
- **技能审核**：审核事务发布 `SKILL_APPLICATION_REVIEWED`，通知服务在独立消费组生成结果通知，并按渠道配置创建邮件任务；历史审核不补发通知。申请详情仅允许原申请人或管理员访问。

消息契约变更时一起更新生产端、消费端及共享契约模块；各服务应使用相同 Topic，各业务消费者保留独立消费组。

## Elasticsearch 关键词检索

当前关键词查询已接入 ES，MySQL 仍负责读取最新业务字段、复核筛选条件与权限：

| 业务入口 | 有关键词 | 无关键词 |
| --- | --- | --- |
| 管理员工单列表 `GET /api/tickets` | ES 分页命中工单 ID，Ticket-Service 回表 | MySQL 分页 |
| 处理人工单列表 `GET /api/tickets/handler` | 服务端从 JWT 限定当前处理人，ES 返回 ID，Ticket-Service 回表复核归属 | MySQL 按当前处理人分页 |
| 审计 `GET /api/audit-logs`、`/api/audit-logs/configurations` | 独立审计索引检索，回表读取日志和当前展示字段，仅管理员访问 | MySQL 分页 |
| 用户 `GET /api/users`、处理人 `GET /api/handlers` | 独立目录索引返回候选用户 ID，User-Service 在 MySQL 过滤角色/状态并分页，仅管理员访问 | MySQL 查询 |

搜索调用失败、ES 关闭或审计/目录尚未准备完成时，关键词请求明确报错，不自动降级为 SQL `LIKE`。普通用户“我的工单”、看板、工作台汇总、报表和评分仍走原有 MySQL 查询。

### 本地启用

```powershell
docker compose -f Search-Service/docker/compose.yaml up -d --build
docker compose -f Search-Service/docker/compose.yaml ps
Invoke-RestMethod 'http://127.0.0.1:9200/'
Invoke-RestMethod 'http://127.0.0.1:9200/_cat/plugins?format=json'
```

在 **Search-Service 的启动终端**设置后再启动服务：

```powershell
$env:WORK_ORDER_ES_ENABLED = "true"
$env:WORK_ORDER_ES_URIS = "http://localhost:9200"
$env:WORK_ORDER_ES_INITIALIZE_ON_STARTUP = "true"
```

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `WORK_ORDER_ES_ENABLED` | `false` | 启用 ES 组件和内部搜索接口 |
| `WORK_ORDER_ES_USERNAME` / `WORK_ORDER_ES_PASSWORD` | 空 | 服务端启用认证时成对配置，本地 Docker 节点不需要 |
| `WORK_ORDER_ES_TICKET_INDEX_NAME` / `WORK_ORDER_ES_TICKET_INDEX_ALIAS` | `wo-ticket-v2` / `wo-ticket` | 工单物理索引 / 读写别名 |
| `WORK_ORDER_ES_AUDIT_INDEX_NAME` / `WORK_ORDER_ES_AUDIT_INDEX_ALIAS` | `wo-audit-v1` / `wo-audit` | 审计物理索引 / 别名 |
| `WORK_ORDER_ES_AUDIT_SYNC_DELAY_MS` | `30000` | 审计分批回填与周期对账间隔 |
| `WORK_ORDER_ES_DIRECTORY_INDEX_PREFIX` / `WORK_ORDER_ES_DIRECTORY_INDEX_ALIAS` | `wo-directory-v1` / `wo-directory` | 目录快照索引前缀 / 别名 |
| `WORK_ORDER_ES_DIRECTORY_SYNC_DELAY_MS` | `30000` | 目录快照检查间隔 |

审计索引在 ES 启用后自动初始化并分批回填，首轮完成前拒绝关键词搜索；目录读取 MySQL 快照，内容变化时完整写入新索引并原子切换别名。两者当前使用数据库周期同步，不依赖 RocketMQ。

**工单投影尚未接通业务写入、MQ 同步和历史导入。** `WORK_ORDER_ES_INITIALIZE_ON_STARTUP=true` 只创建/校验工单索引和别名，不导入工单。关键词入口已经实现，但新库空索引不会返回现存工单；部署时需准备工单投影及后续同步，不能把创建索引视为搜索数据已就绪。

工单标题和描述使用 `ik_max_word` 建索引、`ik_smart` 查询，编号精确匹配。关键词工单分页每页最多 100 条，查询窗口最多 10000 条；总数取 ES 命中数，回表剔除已删除或归属/状态变化的记录后，当前页可能不足。旧 V1 `standard` 索引需重建并切换别名，启动初始化不会自动迁移。

客户端、IK 映射和旧索引迁移的细节见 [Search-Service 的索引配置说明](Search-Service/README.md#本机-docker-desktop)，当前业务入口和同步范围以本节为准。本地 Docker 配置关闭 ES 认证，端口只绑定本机；数据保存在命名卷，`down -v` 会删除索引数据。

## 通知与邮件

系统配置 `notificationChannels` 使用 `{"internal":true,"email":true}`：站内信固定开启，邮件可关闭，不提供短信。读取旧配置时兼容忽略布尔 `sms` 字段，新保存只接受 `internal` / `email`。认证验证码邮件独立于此配置。

| 接口 | 用途 |
| --- | --- |
| `GET /api/notifications` | 当前用户站内信列表 |
| `GET /api/notifications/unread-count` | 未读数量 |
| `POST /api/notifications/read` | 批量标记已读 |
| `POST /api/notifications/read-all` | 全部标记已读 |
| `GET /api/alerts` | 管理员告警列表 |
| `POST /api/alerts/{id}/handle` | 处理告警 |

站内信列表、未读数和已读操作只处理 `INTERNAL`；邮件状态独立，邮件失败不会覆盖成功的站内告警状态。

Notification-Service 的 SMTP 配置支持以下环境变量：

| 环境变量 | 用途及默认值 |
| --- | --- |
| `NOTIFICATION_MAIL_HOST` | SMTP 服务器 |
| `NOTIFICATION_MAIL_PORT` | `587` |
| `NOTIFICATION_MAIL_USERNAME` / `NOTIFICATION_MAIL_PASSWORD` | SMTP 账号与密码/授权码 |
| `NOTIFICATION_MAIL_FROM` | 发件地址，默认使用登录账号 |
| `NOTIFICATION_MAIL_AUTH` | `true` |
| `NOTIFICATION_MAIL_STARTTLS` | `true`，启用且要求 STARTTLS |
| `NOTIFICATION_MAIL_SSL` | `false`；使用隐式 TLS 的 465 端口时开启并关闭 STARTTLS |
| `NOTIFICATION_MAIL_WORKER_ENABLED` | `true`；设为 `false` 暂停工作者，保留已生成任务 |

服务自动包含 `mail-local` profile。本机可使用被 Git 忽略的 `application-mail-local.yaml`，新环境需自行注入配置，不依赖本机 SMTP 快照。

通知、邮件任务和消费日志在同一事务落库，工作者在事务外执行 SMTP：每 10 秒最多 20 封，租约 5 分钟，最多尝试 5 次，失败按 30/60/120/240 秒退避。邮箱/内容使用任务创建时快照；缺失或非法邮箱直接失败，站内信仍创建。

邮件开关影响后续消费，已生成任务继续处理，历史事件不补发。`SENT` 表示 SMTP 接受，不表示最终送达或阅读；SMTP 接受后、成功回写前进程退出，恢复时可能重复发送。达到 `FAILED` 的任务不会因修正配置自动重发，当前没有管理员重发入口。

## 验证与当前边界

全量测试及按模块测试：

```powershell
mvn test
mvn -pl Search-Service -am test
```

真实 ES / IK 集成测试平时跳过，需在本地节点就绪后显式开启：

```powershell
$env:WORK_ORDER_ES_LIVE_TEST = "true"
mvn -pl Search-Service -am test "-Dtest=TicketSearchIkIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
Remove-Item Env:WORK_ORDER_ES_LIVE_TEST
```

单元测试通过不代表运行服务已部署新代码，也不代表 RocketMQ 已完成消费、邮件已送达或 ES 数据已同步。部署后需用新创建的工单验证派单、SLA 和通知链路，并核对审计/目录回填及工单投影。

当前需要补齐的主要能力是工单搜索投影的历史导入、可靠同步与乱序保护，以及邮件失败任务的管理员重发入口。现有框架版本、开发账号、JWT 默认密钥及未开启认证的本地中间件配置用于当前项目联调；生产部署需单独维护凭据、访问控制与版本升级方案。

这四项能力的实现方案见[工单搜索同步与失败邮件重发设计](docs/工单搜索同步与失败邮件重发设计.md)，包含事务边界、接口、迁移与验收计划；方案不代表功能已经实现。
