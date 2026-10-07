# 智能工单调度系统（后端）

基于 Spring Boot、Spring Cloud Alibaba 与 OAuth2/JWT 的微服务工单系统后端，提供工单流转、智能派单、SLA 监控、通知告警、组织与技能管理、审计和报表查询。

MySQL 保存业务数据；RocketMQ 配合事务性 Outbox 驱动派单、SLA、通知和工单搜索投影同步；Elasticsearch 提供关键词检索，业务响应仍从 MySQL 读取。本文按当前代码说明部署与功能边界。

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
| `Notification-Service` | `Notification` | 63030 | 站内通知、邮件投递与重发审计、升级告警 |
| `Search-Service` | `Search` | 63040 | 关键词搜索、工单投影同步与索引运维、工作台、看板、审计与报表 |
| `Base-Utility` | — | — | 公共模型、异常、Jackson 与 MyBatis 配置 |
| `Security-Common` | — | — | JWT 鉴权与当前用户解析 |
| `Messaging-Common` | — | — | 事件信封、Outbox、重试恢复与幂等消费 |
| `Ticket-Event-Contract` | — | — | 工单业务事件快照、搜索变更契约与共享校验 |
| `Skill-Event-Contract` | — | — | 技能审核事件快照与共享校验 |

`UserModel` / `UserService`、`TicketModel` / `TicketService` 是各业务服务内部的模型和实现模块，不独立启动。业务请求统一通过网关 `http://localhost:63010` 访问；上表端口以各模块的 `bootstrap.yaml` 为准。

## 已实现的功能

- **工单生命周期**：提交、自动/手动派单、响应、回复、催办、转派、升级、提交解决、确认、关闭、撤销和评价，包含状态历史、操作记录、内部备注与附件。每张工单最多催办 3 次，催办次数与升级级别独立。
- **编号与 SLA 默认值**：按 `ticketNoRule` 和 MySQL 事务计数器生成编号，序号达到配置位数上限时返回冲突。分类响应/解决 SLA 可分别设为 `null`，创建时读取 `slaDefaults`；配置不存在时使用 30/240 分钟。截止时间在创建时固定，后续配置更新不追溯修改旧工单。
- **统一派单评分**：自动派单、手动派单和转派复用技能匹配、实时在办负载及历史 SLA/评价评分。子分类未配置技能时继承最近上级；负载由待响应和处理中工单实时计算。解决与评价保存处理人归属快照，转派后的业绩归实际提交解决的接手人。管理员修改 `assignWeights` 后无需重启生效，配置保存使用版本号控制并发。
- **SLA 与升级**：工单创建、解决、关闭、撤销和升级事件更新 SLA 记录；`escalationRules` 驱动自动升级。默认每 60 秒扫描一页、最多 200 单，工单服务锁定源记录并复核规则后升至最高匹配级别；空规则关闭自动升级，规则调整不回退已有级别，终态工单不再升级。
- **通知与告警**：派单、回复、催办、转派、升级、解决、关闭、撤销及技能审核结果生成通知。支持站内信、未读数、批量已读和全部已读；邮件通过独立任务队列发送，管理员可查看投递任务、单条重发失败邮件并查询重发审计。升级告警保存事件发生时的工单编号/标题快照，历史展示不再同步请求工单详情。
- **组织、技能和查询**：用户、部门树、处理人档案、技能标签、技能申请与审核；普通用户、处理人、管理员三类权限；管理员看板、处理人工作台、报表、评价明细、工单及配置审计。关键词查询的具体路径见下文。
- **工单搜索同步与运维**：业务写入原子递增源版本，同事务登记独立搜索事件；Search-Service 回源写入 ES，以外部版本防止乱序覆盖。支持历史导入、断点续跑、完整验证、独立发布、周期对账和单工单修复，关键词查询通过共享 `READY` 状态及真实读别名校验后开放。

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

消息、Elasticsearch、工单搜索事件发布和投影同步开关默认关闭。关闭消息后，自动派单、事件驱动的 SLA 同步和通知链路不会运行；工单关键词搜索还要求完成导入、验证和发布，仅开启 ES 不会开放搜索。无关键词列表仍可查询 MySQL。

### 2. 初始化数据库

**空库首次部署**按顺序执行：

```powershell
mysql --default-character-set=utf8mb4 -u root -p --execute="SOURCE sql/work_order_system_schema.sql"
mysql --default-character-set=utf8mb4 -u root -p WorkOrderSystem --execute="SOURCE sql/work_order_system_add_messaging.sql"
mysql --default-character-set=utf8mb4 -u root -p WorkOrderSystem --execute="SOURCE sql/work_order_system_add_ticket_search_phase3.sql"
mysql --default-character-set=utf8mb4 -u root -p WorkOrderSystem --execute="SOURCE sql/work_order_system_seed.sql"
```

全量建表脚本已包含业务表、邮件重发字段及审计表、技能审核关联、告警快照、评分归属、SLA 终态字段，以及搜索阶段一的源版本和基础控制表。第二步补充消息 Outbox 和消费日志，第三步补充搜索阶段三的任务字段及独立对账表，第四步初始化演示账号和权限。空库也不能省略阶段三迁移。PowerShell 不支持 Bash 风格的 `<` 输入重定向，示例使用 MySQL 客户端的 `SOURCE`。

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
| `work_order_system_add_ticket_search_sync.sql` | 工单 `source_version`、搜索控制状态和基础导入任务表 |
| `work_order_system_add_ticket_search_phase3.sql` | 导入验证/发布/恢复字段和独立对账表，依赖上一项或全量建表脚本 |
| `work_order_system_add_ticket_terminal_sla.sql` | SLA 终态与终态时间 |
| `work_order_system_add_assignment_scoring.sql` | 分类技能与解决/评价处理人快照 |
| `work_order_system_add_ticket_numbering.sql` | 工单编号事务计数器 |
| `work_order_system_add_sla_defaults.sql` | 分类 SLA 可空并继承系统默认值 |
| `work_order_system_add_notification_email.sql` | 可恢复邮件投递队列 |
| `work_order_system_add_email_retry.sql` | 管理员单条邮件重发轮次、累计次数和幂等审计 |
| `work_order_system_add_skill_review_audit.sql` | 技能审核通知关联与审计索引 |
| `work_order_system_add_alert_ticket_snapshot.sql` | 升级告警工单编号/标题快照 |

`add_ticket_terminal_sla`、`add_assignment_scoring` 含直接新增列的 DDL，不能重复执行。旧评分数据按当前可确认的处理人补齐，旧告警按工单当前摘要补录，均不能还原已经丢失的历史事实；具体条件以各脚本注释为准。

已有库接入工单搜索时，先执行 `add_ticket_search_sync`，再执行 `add_ticket_search_phase3`。两者可重复执行，不重置已有源版本或任务进度；全部 TicketAPI 实例完成源版本和事件写入口升级后，再开启正式同步。

邮件重发升级要求已有基础邮件任务表；缺少时先执行 `add_notification_email`。先停止全部旧邮件工作者，执行 `add_email_retry`，再升级全部 Notification-Service 实例并恢复工作者。管理员在“邮件投递”页查看失败任务并重排原收件地址和内容；上线不会自动重发历史失败邮件。接口、部署与验证记录见 [阶段四实施记录](docs/工单搜索同步阶段四实施记录.md)。

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

本地 Docker 配置位于 `deploy/rocketmq/docker-compose.yml`。启动后确认 Broker 已注册，再创建工单、技能和独立搜索事件 Topic：

```powershell
docker compose -f deploy/rocketmq/docker-compose.yml up -d
docker exec workorder-rocketmq-broker sh mqadmin clusterList -n namesrv:9876
docker exec workorder-rocketmq-broker sh mqadmin updateTopic -n namesrv:9876 -c DefaultCluster -t wo-ticket-event
docker exec workorder-rocketmq-broker sh mqadmin updateTopic -n namesrv:9876 -c DefaultCluster -t wo-skill-event
docker exec workorder-rocketmq-broker sh mqadmin updateTopic -n namesrv:9876 -c DefaultCluster -t wo-ticket-search-event
```

在 UserAPI、TicketAPI、Assign-Engine、Sla-Monitor、Notification-Service 的启动环境中配置：

```powershell
$env:WORK_ORDER_MESSAGING_ENABLED = "true"
$env:ROCKETMQ_NAME_SERVER = "localhost:9876"
$env:WORK_ORDER_TICKET_EVENT_TOPIC = "wo-ticket-event"
$env:WORK_ORDER_SKILL_EVENT_TOPIC = "wo-skill-event"
$env:WORK_ORDER_OUTBOX_ENABLED = "true"
```

业务修改和事件 Outbox 在同一 MySQL 事务提交；后台发送器投递 RocketMQ，派单、SLA 和通知消费者将幂等日志与业务结果在同一事务落库。搜索消费者每次重新回源，以 ES 外部版本处理重复和乱序消息。发送重试、Outbox 恢复及各消费组可独立运行；MySQL 提交不代表下游已处理完毕。

- **自动派单**：创建工单发布 `TICKET_CREATED`，SLA 初始化记录，派单引擎生成 `ASSIGNMENT_PROPOSED`，工单服务在仍待分配且处理人未满额时条件确认，再发布派单通知事件。
- **自动升级**：SLA 扫描读取规则，工单服务复核并发布升级事件；通知服务在幂等事务中创建通知与告警。`MANAGER` 指当前处理人的部门负责人，未派单时为提交人部门负责人，不可用时回退启用管理员；`ADMIN` 指全部启用管理员。没有接收人时升级失败，后续扫描重试。扫描周期可通过 `work-order.sla.escalation.scan-interval-ms` 调整。
- **技能审核**：审核事务发布 `SKILL_APPLICATION_REVIEWED`，通知服务在独立消费组生成结果通知，并按渠道配置创建邮件任务；历史审核不补发通知。申请详情仅允许原申请人或管理员访问。
- **工单搜索同步**：工单创建及投影字段更新登记 `TICKET_SEARCH_CHANGED`，使用 `wo-ticket-search-event` / `CHANGED`，不复用通知事件。事件携带工单 ID 和源版本，Search-Service 从 MySQL 读取当前完整数据后写入受管代次；开关和发布流程见下节。

消息契约变更时一起更新生产端、消费端及共享契约模块；各服务应使用相同 Topic，各业务消费者保留独立消费组。

## Elasticsearch 关键词检索

当前关键词查询已接入 ES，MySQL 仍负责读取最新业务字段、复核筛选条件与权限：

| 业务入口 | 有关键词 | 无关键词 |
| --- | --- | --- |
| 管理员工单列表 `GET /api/tickets` | ES 分页命中工单 ID，Ticket-Service 回表 | MySQL 分页 |
| 处理人工单列表 `GET /api/tickets/handler` | 服务端从 JWT 限定当前处理人，ES 返回 ID，Ticket-Service 回表复核归属 | MySQL 按当前处理人分页 |
| 审计 `GET /api/audit-logs`、`/api/audit-logs/configurations` | 独立审计索引检索，回表读取日志和当前展示字段，仅管理员访问 | MySQL 分页 |
| 用户 `GET /api/users`、处理人 `GET /api/handlers` | 独立目录索引返回候选用户 ID，User-Service 在 MySQL 过滤角色/状态并分页，仅管理员访问 | MySQL 查询 |

搜索调用失败、ES 关闭或索引尚未准备完成时，关键词请求明确报错，不自动降级为 SQL `LIKE`。工单同步关闭、导入/验证/发布中、任务失败或共享状态与真实别名不一致时，工单关键词请求返回 HTTP 503 / `1090`；无关键词列表和详情继续使用原有路径。普通用户“我的工单”、看板、工作台汇总、报表和评分仍走原有 MySQL 查询。

### 本地启用

```powershell
docker compose -f Search-Service/docker/compose.yaml up -d --build
docker compose -f Search-Service/docker/compose.yaml ps
Invoke-RestMethod 'http://127.0.0.1:9200/'
Invoke-RestMethod 'http://127.0.0.1:9200/_cat/plugins?format=json'
```

在 **TicketAPI 的每个启动终端**开启源端搜索事件发布；消息和 Outbox 同时开启，并与 Search-Service 使用相同 NameServer 和搜索 Topic：

```powershell
$env:WORK_ORDER_MESSAGING_ENABLED = "true"
$env:ROCKETMQ_NAME_SERVER = "localhost:9876"
$env:WORK_ORDER_OUTBOX_ENABLED = "true"
$env:WORK_ORDER_TICKET_SEARCH_EVENT_TOPIC = "wo-ticket-search-event"
$env:WORK_ORDER_TICKET_SEARCH_PUBLISH_ENABLED = "true"
```

在 **Search-Service 的启动终端**设置后再启动服务：

```powershell
$env:WORK_ORDER_ES_ENABLED = "true"
$env:WORK_ORDER_ES_URIS = "http://localhost:9200"
$env:WORK_ORDER_MESSAGING_ENABLED = "true"
$env:ROCKETMQ_NAME_SERVER = "localhost:9876"
$env:WORK_ORDER_TICKET_SEARCH_EVENT_TOPIC = "wo-ticket-search-event"
$env:WORK_ORDER_TICKET_SEARCH_SYNC_ENABLED = "true"
$env:WORK_ORDER_ES_INITIALIZE_ON_STARTUP = "false"
```

Search-Service 的 `work-order.messaging.outbox.enabled` 保持 `false`，只消费搜索事件。同步开启后由导入任务创建 V3 受管索引，旧启动初始化器不再参与建工单索引；`WORK_ORDER_ES_INITIALIZE_ON_STARTUP` 不能代替历史导入或独立发布。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `WORK_ORDER_ES_ENABLED` / `WORK_ORDER_ES_URIS` | `false` / `http://localhost:9200` | 启用 ES 组件并配置节点地址 |
| `WORK_ORDER_ES_USERNAME` / `WORK_ORDER_ES_PASSWORD` | 空 | 服务端启用认证时成对配置，本地 Docker 节点不需要 |
| `WORK_ORDER_TICKET_SEARCH_PUBLISH_ENABLED` | `false` | TicketAPI 登记搜索变更 Outbox；关闭时业务 SQL 仍递增源版本 |
| `WORK_ORDER_TICKET_SEARCH_SYNC_ENABLED` | `false` | Search-Service 启用消费、导入、对账及查询就绪控制，要求 ES 和消息底座同时开启 |
| `WORK_ORDER_TICKET_SEARCH_EVENT_TOPIC` | `wo-ticket-search-event` | 源端与 Search-Service 共用的搜索 Topic |
| `WORK_ORDER_TICKET_SEARCH_CONSUMER_GROUP` | `search-ticket-projection-v1` | Search-Service 搜索消费组 |
| `WORK_ORDER_TICKET_SEARCH_SOURCE_ZONE_ID` | `Asia/Shanghai` | MySQL DATETIME 转搜索文档时间所用的业务时区 |
| `WORK_ORDER_ES_TICKET_INDEX_ALIAS` | `wo-ticket` | 工单业务读别名，受管同步不向该别名写入 |
| `WORK_ORDER_ES_TICKET_INDEX_NAME` | `wo-ticket-v2` | 旧初始化目标及首次发布允许接管的明确旧物理目标，受管 V3 名称由任务生成 |
| `WORK_ORDER_ES_AUDIT_INDEX_NAME` / `WORK_ORDER_ES_AUDIT_INDEX_ALIAS` | `wo-audit-v1` / `wo-audit` | 审计物理索引 / 别名 |
| `WORK_ORDER_ES_AUDIT_SYNC_DELAY_MS` | `30000` | 审计分批回填与周期对账间隔 |
| `WORK_ORDER_ES_DIRECTORY_INDEX_PREFIX` / `WORK_ORDER_ES_DIRECTORY_INDEX_ALIAS` | `wo-directory-v1` / `wo-directory` | 目录快照索引前缀 / 别名 |
| `WORK_ORDER_ES_DIRECTORY_SYNC_DELAY_MS` | `30000` | 目录快照检查间隔 |

审计索引在 ES 启用后自动初始化并分批回填，首轮完成前拒绝关键词搜索；目录读取 MySQL 快照，内容变化时完整写入新索引并原子切换别名。两者当前使用数据库周期同步，不依赖 RocketMQ。

首次接管已有工单读别名前，核对其唯一物理目标，并将 Search-Service 的 `WORK_ORDER_ES_TICKET_INDEX_NAME` 配置为该实际旧索引名；默认值只适用于旧目标为 `wo-ticket-v2` 的环境。目标不匹配时发布返回 HTTP 409 / `1091`；没有旧读别名的空库可直接创建任务并发布。

### 工单投影与搜索开放

日常更新的主链路为：业务 SQL 原子递增 `tickets.source_version` → 同事务登记搜索 Outbox → Relay 投递 RocketMQ → Search-Service 回源读取完整工单与当前版本 → ES `EXTERNAL` 版本写入。业务数据与 Outbox 一起回滚；ES 不进入业务数据库事务。旧事件到达时也读取当前源行，同版本或更高版本已存在时视为已覆盖，其他写入失败交由 MQ 重投。

每个导入任务使用 `wo-ticket-v3-{jobId}` 物理索引和不可变写别名 `wo-ticket-write-{jobId}`。导入、增量消息、验证修复复用同一版本化写入通路；`wo-ticket` 在发布后仅作为业务读别名。状态主线为 `NOT_READY → IMPORTING → VERIFYING → PUBLISHING → READY`，导入或验证失败进入 `FAILED`。

管理员接口均要求 `ADMIN`，复用现有网关路由：

| 接口 | 用途 |
| --- | --- |
| `GET /api/internal/search/tickets/index/status` | 共享状态、当前任务、对账进度及搜索 Topic 的 Outbox/MQ 健康 |
| `POST /api/internal/search/tickets/index/imports` | 创建异步历史导入或重建任务 |
| `GET /api/internal/search/tickets/index/imports/{id}` | 查看任务进度、完成标记和错误 |
| `POST /api/internal/search/tickets/index/imports/{id}/resume` | 续跑失败任务或人工恢复未确认的发布 |
| `POST /api/internal/search/tickets/index/imports/{id}/publish` | 完整验证后独立发布读别名 |
| `POST /api/internal/search/tickets/index/repairs/{ticketId}` | 按 MySQL 当前完整数据与源版本修复单张工单 |

首次开放或重建按以下顺序操作：

1. 完成数据库升级，部署并核对全部 TicketAPI 源写入口，开启上述源端和 Search-Service 配置。
2. 创建导入任务。`requestId` 为 32 位十六进制 UUID，网络重试复用同一 ID；`reason` 为 1–500 字。创建新代次立即暂停工单关键词查询。
3. 等待任务完成历史导入和从头完整验证，确认 `verifiedAt`、`refreshCompletedAt` 已记录；此时仍为 `VERIFYING`，不会自动发布。
4. 核对全部源实例部署清单和状态接口中的消息健康，再调用 `publish`。发布刷新目标、原子切换读别名并回读校验，最后保存共享 `READY`。
5. 确认状态接口 `ready=true`，再通过管理员和处理人工单列表验证关键词搜索。

创建请求示例：

```json
{"requestId":"8b2a7c1235a641bb8e5e88d5aacdd501","reason":"首次历史工单导入"}
```

发布请求须列出全部实际写工单的源实例，示例身份与版本替换为实际部署值；这些字段是管理员的部署核对记录：

```json
{
  "sourceInventoryComplete": true,
  "sourceInstances": [{
    "instanceId": "ticket-api-instance-1",
    "deployedVersion": "实际部署版本",
    "publishEnabled": true,
    "outboxEnabled": true,
    "eventWritePathsVerified": true
  }]
}
```

默认每批 500 条、每次调度最多 10 批，导入间隔 1 秒、租约 120 秒；整批成功才推进持久游标。`READY` 后每 30 秒调度独立对账，完整轮次从头扫描并修复缺失或落后投影。配置位于 `work-order.elasticsearch.ticket-sync`。

关键词查询在执行前和返回前都核对共享 `READY` 代次与真实 ES 读别名，查询期间开始重建会丢弃旧结果并返回 503。发布结果不确定时保留 `PUBLISHING`；人工恢复需携带原发布令牌并确认原发布进程已停止，操作细节见 [阶段三实施记录](docs/工单搜索同步阶段三实施记录.md)。旧索引保留，但重建期间不会持续接收全部新数据，回退前需要重新追平和验证。

工单标题和描述使用 `ik_max_word` 建索引、`ik_smart` 查询，编号精确匹配。关键词工单分页每页最多 100 条，查询窗口最多 10000 条；总数取 ES 命中数，回表剔除已删除或归属/状态变化的记录后，当前页可能不足。旧 V1/V2 数据通过受管任务从 MySQL 重新导入 V3，不直接给旧索引增加版本字段，也不依靠启动初始化迁移。

本地 Docker 配置关闭 ES 认证，端口只绑定本机；数据保存在命名卷，`down -v` 会删除索引数据。工单搜索契约、版本写入和索引运维的详细记录见文末文档入口。

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
| `GET /api/notifications/email-deliveries` | 管理员投递任务分页，默认 `FAILED`，支持 `ALL`、状态及创建时间筛选 |
| `GET /api/notifications/email-deliveries/{id}` | 管理员查看原任务详情和原收件地址 |
| `GET /api/notifications/email-deliveries/{id}/retry-logs` | 管理员查看原任务的人工重发审计 |
| `POST /api/notifications/email-deliveries/{id}/retry` | 管理员单条重排失败邮件，实际发送由工作者执行 |

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

通知、邮件任务和消费日志在同一事务落库，工作者在事务外执行 SMTP：每 10 秒最多处理 20 个任务，租约 5 分钟，每轮前 5 次领取允许发送，失败按 30/60/120/240 秒退避。领取次数包含领取后崩溃的尝试；第 6 次租约恢复只收尾失败，不再调用 SMTP。邮箱/内容使用任务创建时快照；缺失或非法邮箱直接失败，站内信仍创建。

邮件开关影响后续消费，已生成任务继续处理，历史事件不补发。`SENT` 表示 SMTP 接受，不表示最终送达或阅读；SMTP 接受后、成功回写前进程退出，恢复时可能重复发送。达到 `FAILED` 的任务不会因修正配置自动重发，需由管理员显式发起新一轮投递。

### 管理员单条重发

前端入口为 `/admin/email-deliveries` 的“邮件投递”页。后端除校验 JWT `ADMIN`，还复核账号当前启用状态和管理员角色；列表收件地址脱敏，详情不公开正文、领取令牌或租约。

重发只接受 `FAILED`、原邮箱合法且轮次符合预期的任务，保留原收件地址、主题和正文，不读取用户当前邮箱替换快照。请求示例：

```json
{
  "requestId": "b7509b9e93814203b6ddc2fd0ea39f25",
  "expectedRetryRound": 0,
  "reason": "SMTP 配置已修复，重新投递原邮件"
}
```

`requestId` 为 32 位十六进制 UUID，`expectedRetryRound` 使用详情中的当前非负轮次，`reason` 为 1–200 字。同一次网络重试保留原 ID、轮次和原因；下一次人工重发使用新 ID 和最新轮次。状态、轮次或请求语义冲突返回 HTTP 409 / `1100`。

接受后将原任务改为 `PENDING`，`retry_round` 加一、本轮 `attempts` 清零，保留累计 `total_attempts`；关联 EMAIL 通知和重发审计同事务更新，不新建站内信或改变已读状态。成功响应仅确认重新排队，包含 `acceptedRetryRound`、`currentStatus` 和 `workerEnabled`；同语义幂等重放返回原接受轮次，不重复重排。

邮件渠道关闭仍允许重发既有任务，工作者关闭时允许排队等待恢复。需要暂停全部发送时设置 `NOTIFICATION_MAIL_WORKER_ENABLED=false`，`notificationChannels.email=false` 只控制新任务创建。

## 验证与当前边界

全量测试及按模块测试：

```powershell
mvn test
mvn -pl Search-Service -am test
```

真实 ES / IK 和外部版本集成测试平时跳过，需在本地节点就绪后显式开启：

```powershell
$env:WORK_ORDER_ES_LIVE_TEST = "true"
mvn -pl Search-Service -am test "-Dtest=TicketSearchIkIntegrationTest,TicketSearchVersionIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
Remove-Item Env:WORK_ORDER_ES_LIVE_TEST
```

默认 `mvn test` 不执行全部真实 MySQL、RocketMQ、ES 链路测试；各阶段实施记录提供相应开关、隔离资源及验证范围。单元测试通过不代表运行服务已部署新代码，也不代表 RocketMQ 已完成消费、邮件已送达或 ES 数据已同步。

工单搜索版本化同步、历史导入、发布门禁和管理员邮件重发已包含在当前代码中，搜索发布与同步开关仍需按部署步骤显式启用。部署后用新创建的工单验证派单、SLA、通知和投影更新，核对审计/目录回填；邮件重发需确认原任务轮次、审计及实际工作者结果。

现有框架版本、开发账号、JWT 默认密钥及未开启认证的本地中间件配置用于当前项目联调；生产部署需单独维护凭据、访问控制与版本升级方案。各阶段记录中的本机隔离验收结果有各自范围，不代表当前业务环境已经开启搜索或完成外部邮件投递。

## 相关文档

| 文档 | 内容 |
| --- | --- |
| [工单搜索同步与失败邮件重发设计](docs/工单搜索同步与失败邮件重发设计.md) | 分阶段设计、事务边界和验收计划 |
| [搜索阶段一实施记录](docs/工单搜索同步阶段一实施记录.md) | 源版本、搜索事件 Outbox 和基础迁移 |
| [搜索阶段二实施记录](docs/工单搜索同步阶段二实施记录.md) | 消费回源、V3 映射及 ES 外部版本写入 |
| [搜索阶段三实施记录](docs/工单搜索同步阶段三实施记录.md) | 导入、验证、独立发布、对账、故障恢复及本机隔离联调 |
| [邮件阶段四实施记录](docs/工单搜索同步阶段四实施记录.md) | 管理员重发接口、前端、迁移、投递轮次及验证范围 |
| [领域术语](CONTEXT.md) | 工单事件快照、搜索投影、邮件重发与投递轮次的含义 |
