package com.WorkOrder.search.service;

import com.WorkOrder.search.config.ElasticsearchProperties;
import com.WorkOrder.search.model.TicketSearchWriteTarget;
import com.WorkOrder.search.support.ElasticsearchFailureClassifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Iterator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Virgor
 * @date 2026年10月07日
 * @description 管理工单索引及连通性检查；受管 V3 代次仅使用专属写别名，读别名由导入任务发布。
 */
public class TicketSearchIndexService {

    /**
     * 首次建索引时加载的类路径资源，定义 V2 工单搜索投影及 IK 分词映射。
     */
    private static final String INDEX_RESOURCE = "elasticsearch/ticket-index-v2.json";

    /**
     * 完整、严格的 V3 源版本投影，不在已有 V2 索引上追加版本字段。
     */
    private static final String MANAGED_INDEX_RESOURCE = "elasticsearch/ticket-index-v3.json";

    /**
     * 仅解析映射模板和字段定义，避免受 HTTP 序列化配置影响。
     */
    private static final ObjectMapper MAPPING_MAPPER = new ObjectMapper();

    /**
     * Spring 容器管理的共享客户端，本服务不单独关闭它。
     */
    private final RestHighLevelClient client;

    /**
     * 索引名、统一读写别名及首次创建时使用的主分片和副本配置。
     */
    private final ElasticsearchProperties properties;

    /**
     * 创建索引管理服务，仅保存依赖，不主动连接节点或创建索引。
     *
     * @param client 由 Spring 容器管理的共享 Elasticsearch 客户端
     * @param properties 已完成绑定和校验的 Elasticsearch 配置
     */
    public TicketSearchIndexService(RestHighLevelClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 使用客户端的 HEAD 探测检查节点连通性，不执行索引读写。
     *
     * @return 客户端收到成功探测响应时为 true，判定节点不可用时为 false
     * @throws IOException 网络通信失败或探测超时
     */
    public boolean ping() throws IOException {
        return client.ping(RequestOptions.DEFAULT);
    }

    /**
     * 原子创建登记的 V3 物理索引和专属写别名，不绑定或切换业务读别名。
     * 相同目标已存在时只校验；已有物理索引却缺少专属别名时拒绝接管。
     * 并发创建只接受明确的 resource_already_exists_exception，随后重新核对目标及映射。
     *
     * @param target 持久控制记录解析得到的不可变代次目标
     * @return 已通过完整校验的物理索引名称
     * @throws IOException 别名或映射不兼容、网络失败、创建未确认
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String createManagedIndex(TicketSearchWriteTarget target) throws IOException {
        validateTargetNames(target);
        String existing = findSingleIndex(target.getWriteAlias());
        if (existing != null) {
            validateManagedIndex(target, existing);
            return existing;
        }
        if (client.indices().exists(new GetIndexRequest(target.getPhysicalIndex()), RequestOptions.DEFAULT)) {
            throw new IOException("Elasticsearch 受管物理索引已存在但缺少专属写别名，拒绝接管目标 "
                    + target.getPhysicalIndex());
        }

        // 索引与专属写别名一起创建；本阶段不绑定读别名，未完成导入的数据不会提前开放查询。
        CreateIndexRequest request = new CreateIndexRequest(target.getPhysicalIndex())
                .source(readIndexDefinition(MANAGED_INDEX_RESOURCE), XContentType.JSON)
                .settings(Settings.builder()
                        .put("index.number_of_shards", properties.getNumberOfShards())
                        .put("index.number_of_replicas", properties.getNumberOfReplicas()))
                .alias(new Alias(target.getWriteAlias()).writeIndex(true));
        try {
            CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
            if (!response.isAcknowledged() || !response.isShardsAcknowledged()) {
                throw new IOException("Elasticsearch 受管索引创建尚未确认完成，请检查集群后重试");
            }
        } catch (ElasticsearchStatusException exception) {
            if (exception.status() != RestStatus.BAD_REQUEST
                    || !ElasticsearchFailureClassifier.hasType(exception, "resource_already_exists_exception")) {
                throw exception;
            }
        }
        // 并发创建冲突只表示目标已存在，还必须核对别名、映射和可写能力后才能复用。
        validateWriteTarget(target);
        return target.getPhysicalIndex();
    }

    /**
     * 以新的只读请求确认专属别名唯一指向登记索引、可写且无过滤和路由，并校验完整 V3 映射。
     * 不创建目标、不补充映射，也不回退到业务读别名；消费失败必须保留消息重试。
     *
     * @param target 本次投影请求已经固定的代次目标
     * @throws IOException 目标缺失、不匹配、字段映射不兼容或网络请求失败
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void validateWriteTarget(TicketSearchWriteTarget target) throws IOException {
        validateTargetNames(target);
        String indexName = findSingleIndex(target.getWriteAlias());
        if (indexName == null) {
            throw new IOException("Elasticsearch 受管写别名不存在：" + target.getWriteAlias());
        }
        validateManagedIndex(target, indexName);
    }

    /**
     * 读取业务读别名当前指向的物理索引，不检查可写标志，兼容首次迁移的 V2 写别名。
     * 过滤、路由、无法解析的别名响应直接失败；缺失返回空集合。
     *
     * @return 只读物理索引集合；调用方结合登记目标判断多目标是否允许
     * @throws IOException 网络错误或别名具有过滤/路由条件
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Set<String> inspectReadAlias() throws IOException {
        return Collections.unmodifiableSet(new LinkedHashSet<>(readAliasMetadata().keySet()));
    }

    /**
     * 确认读别名已经唯一发布到该任务且明确禁止写入，同时重新核对专属写目标和完整 V3 映射。
     *
     * @param target 从共享 READY 或 PUBLISHING 记录解析的目标
     * @throws IOException 别名、读写能力、目标或映射不一致
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void validatePublishedReadAlias(TicketSearchWriteTarget target) throws IOException {
        validateWriteTarget(target);
        Map<String, Set<AliasMetadata>> aliases = readAliasMetadata();
        if (aliases.size() != 1 || !aliases.containsKey(target.getPhysicalIndex())) {
            throw new IOException("Elasticsearch 工单读别名尚未唯一发布到登记代次");
        }
        AliasMetadata metadata = findAliasMetadata(aliases.get(target.getPhysicalIndex()),
                properties.getTicketIndexAlias());
        if (metadata == null || !Boolean.FALSE.equals(metadata.writeIndex())) {
            throw new IOException("Elasticsearch 工单业务读别名必须显式禁止写入");
        }
    }

    /**
     * 在单个 ES 别名操作中移除已确认旧读目标并添加新只读目标，不修改任何任务专属写别名。
     * 预期旧目标由管理服务从数据库登记的旧任务或明确配置的 V2 索引取得，不能来自 HTTP 索引参数。
     * 已经指向相同合法目标时只校验；超时或未确认向调用方传播，须回读同任务而非新建代次。
     *
     * @param target 已完成验证、由唯一发布操作固定的新任务目标
     * @param expectedPrevious 管理服务确认的精确旧读目标集合，首次无别名时为空
     * @throws IOException 旧目标被改变、目标未经登记命名、请求失败或发布未确认
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void publishReadAlias(TicketSearchWriteTarget target, Set<String> expectedPrevious) throws IOException {
        validateWriteTarget(target);
        if (expectedPrevious == null) {
            throw new IOException("Elasticsearch 工单发布必须提供登记的旧读目标");
        }
        String readAlias = properties.getTicketIndexAlias();
        if (readAlias.equals(target.getWriteAlias()) || readAlias.equals(target.getPhysicalIndex())) {
            throw new IOException("Elasticsearch 工单读别名不能与专属写别名或物理索引相同");
        }
        Set<String> expected = new LinkedHashSet<>(expectedPrevious);
        for (String index : expected) {
            if (!isAllowedPreviousIndex(index)) {
                throw new IOException("Elasticsearch 工单发布拒绝接管未登记的旧索引");
            }
        }
        Map<String, Set<AliasMetadata>> aliases = readAliasMetadata();
        Set<String> actual = aliases.keySet();
        AliasMetadata current = aliases.size() == 1
                ? findAliasMetadata(aliases.get(target.getPhysicalIndex()), readAlias) : null;
        if (actual.equals(Collections.singleton(target.getPhysicalIndex()))
                && current != null && Boolean.FALSE.equals(current.writeIndex())) {
            // 前一次请求可能已经完成切换但尚未写回数据库，回读后恢复同任务即可。
            return;
        }
        // 只切换管理服务登记的旧目标，发现外部改动时拒绝接管，避免误移除其他索引。
        if (!actual.equals(expected)) {
            throw new IOException("Elasticsearch 工单读别名与登记的预期旧目标不一致，拒绝发布");
        }
        // remove 与 add 放在同一个别名请求中，查询只能看到切换前或切换后的完整目标。
        IndicesAliasesRequest request = new IndicesAliasesRequest();
        for (String previous : expected) {
            request.addAliasAction(IndicesAliasesRequest.AliasActions.remove().index(previous).alias(readAlias));
        }
        request.addAliasAction(IndicesAliasesRequest.AliasActions.add().index(target.getPhysicalIndex())
                .alias(readAlias).writeIndex(false));
        if (!client.indices().updateAliases(request, RequestOptions.DEFAULT).isAcknowledged()) {
            throw new IOException("Elasticsearch 工单读别名发布尚未确认，请回读原任务目标");
        }
        // 收到确认后仍核对实际读目标，任务服务只有据此确认成功才会登记 READY。
        validatePublishedReadAlias(target);
    }

    /**
     * 仅允许精确配置的旧索引或调用方从任务表登记取得的规范 V3 名称。
     *
     * @param index 待接管的旧读别名物理索引名称
     * @return 为明确配置的旧索引名或规范 V3 任务索引名时为 true
     */
    private boolean isAllowedPreviousIndex(String index) {
        if (index == null) {
            return false;
        }
        if (index.equals(properties.getTicketIndexName())) {
            return true;
        }
        String prefix = "wo-ticket-v3-";
        if (!index.startsWith(prefix)) {
            return false;
        }
        try {
            long jobId = Long.parseLong(index.substring(prefix.length()));
            return jobId > 0 && index.equals(prefix + jobId);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /**
     * 获取指定别名的元数据；没有匹配项时返回 null。
     *
     * @param aliases 同一个物理索引上的别名元数据集合
     * @param aliasName 需要精确匹配的别名名称
     * @return 指定别名的元数据；没有匹配项时为 null
     */
    private AliasMetadata findAliasMetadata(Set<AliasMetadata> aliases, String aliasName) {
        return aliases == null ? null : aliases.stream()
                .filter(metadata -> aliasName.equals(metadata.alias())).findFirst().orElse(null);
    }

    /**
     * 业务读别名必须保持完整文档可见，不能配置过滤或路由。
     *
     * @return 业务读别名的物理索引到别名元数据映射；别名缺失时为空
     * @throws IOException 处理过程中发生IO异常时
     */
    private Map<String, Set<AliasMetadata>> readAliasMetadata() throws IOException {
        String readAlias = properties.getTicketIndexAlias();
        GetAliasesResponse response = client.indices()
                .getAlias(new GetAliasesRequest(readAlias), RequestOptions.DEFAULT);
        if (response.status() == RestStatus.NOT_FOUND) {
            return Collections.emptyMap();
        }
        if (response.status() != RestStatus.OK) {
            throw new IOException("Elasticsearch 工单读别名读取失败");
        }
        Map<String, Set<AliasMetadata>> aliases = response.getAliases();
        for (Set<AliasMetadata> definitions : aliases.values()) {
            AliasMetadata metadata = findAliasMetadata(definitions, readAlias);
            if (metadata == null || metadata.filter() != null || metadata.indexRouting() != null
                    || metadata.searchRouting() != null) {
                throw new IOException("Elasticsearch 工单读别名不能带过滤或路由条件");
            }
        }
        return aliases;
    }

    /**
     * 只允许系统约定的任务专属命名，防止接受物理索引、读别名或其他任务的别名。
     *
     * @param target 来自数据库登记记录的任务、代次、物理索引和专属写别名
     * @throws IOException 处理过程中发生IO异常时
     */
    private void validateTargetNames(TicketSearchWriteTarget target) throws IOException {
        if (target == null || target.getJobId() <= 0 || target.getGeneration() <= 0) {
            throw new IOException("Elasticsearch 受管目标必须包含有效任务 ID 和索引代次");
        }
        if (!("wo-ticket-v3-" + target.getJobId()).equals(target.getPhysicalIndex())
                || !("wo-ticket-write-" + target.getJobId()).equals(target.getWriteAlias())) {
            throw new IOException("Elasticsearch 受管索引及专属写别名必须与登记的任务 ID 一致");
        }
    }

    /**
     * 别名及字段校验不会修改已存在的索引，避免将 V2 内部版本与外部源版本混用。
     *
     * @param target 本批固定的受管任务代次
     * @param indexName 别名实际指向、应与目标登记一致的物理索引名
     * @throws IOException 处理过程中发生IO异常时
     */
    private void validateManagedIndex(TicketSearchWriteTarget target, String indexName) throws IOException {
        if (!target.getPhysicalIndex().equals(indexName)) {
            throw new IOException("Elasticsearch 受管写别名目标与登记的物理索引不一致："
                    + target.getWriteAlias());
        }
        MappingMetadata metadata = client.indices()
                .getMapping(new GetMappingsRequest().indices(indexName), RequestOptions.DEFAULT)
                .mappings().get(indexName);
        Map<String, Object> mapping = metadata == null ? null : metadata.sourceAsMap();
        if (mapping == null || !"strict".equals(mapping.get("dynamic"))) {
            throw new IOException("Elasticsearch 受管 V3 映射必须采用 dynamic=strict");
        }
        rejectDisabledSourceOrRequiredRouting(mapping);

        // 核对完整字段协议，而非只检查 sourceVersion，避免把旧索引误当作可用的新代次。
        JsonNode expected = MAPPING_MAPPER.readTree(readIndexDefinition(MANAGED_INDEX_RESOURCE))
                .path("mappings").path("properties");
        Object actual = mapping.get("properties");
        if (!(actual instanceof Map)) {
            throw new IOException("Elasticsearch 受管 V3 映射缺少完整 properties");
        }
        Map<?, ?> fields = (Map<?, ?>) actual;
        Iterator<Map.Entry<String, JsonNode>> expectedFields = expected.fields();
        while (expectedFields.hasNext()) {
            Map.Entry<String, JsonNode> expectedField = expectedFields.next();
            validateManagedField(expectedField.getKey(), expectedField.getValue(), fields.get(expectedField.getKey()));
        }
        if (fields.size() != expected.size()) {
            throw new IOException("Elasticsearch 受管 V3 字段集合与完整投影映射不一致");
        }
    }

    /**
     * 源文档必须可回读，写入请求不使用额外 routing。
     *
     * @param mapping 物理索引的完整映射定义，包含 _source 和 _routing 配置
     * @throws IOException 处理过程中发生IO异常时
     */
    private void rejectDisabledSourceOrRequiredRouting(Map<String, Object> mapping) throws IOException {
        Object source = mapping.get("_source");
        Object routing = mapping.get("_routing");
        if (source instanceof Map) {
            Map<?, ?> sourceSettings = (Map<?, ?>) source;
            if (Boolean.FALSE.equals(sourceSettings.get("enabled"))
                    || sourceSettings.containsKey("includes") || sourceSettings.containsKey("excludes")) {
                throw new IOException("Elasticsearch 受管 V3 索引必须保留完整 _source，不能禁用或过滤字段");
            }
        }
        if (routing instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) routing).get("required"))) {
            throw new IOException("Elasticsearch 受管 V3 索引不能要求额外 routing");
        }
    }

    /**
     * 逐字段校验类型、IK、日期格式及查询所需索引/排序能力，禁止静默补充错误映射。
     *
     * @param name V3 模板要求的字段名称
     * @param expected V3 模板中的预期字段映射节点
     * @param actual 服务器返回的实际字段映射定义
     * @throws IOException 处理过程中发生IO异常时
     */
    private void validateManagedField(String name, JsonNode expected, Object actual) throws IOException {
        if (!(actual instanceof Map)) {
            throw new IOException("Elasticsearch 受管 V3 映射缺少字段 " + name);
        }
        Map<?, ?> definition = (Map<?, ?>) actual;
        Iterator<Map.Entry<String, JsonNode>> requiredSettings = expected.fields();
        while (requiredSettings.hasNext()) {
            Map.Entry<String, JsonNode> setting = requiredSettings.next();
            if ("fields".equals(setting.getKey())) {
                Object actualFields = definition.get("fields");
                if (!(actualFields instanceof Map)) {
                    throw new IOException("Elasticsearch 搜索索引缺少 " + name + ".raw 原文检索字段，请重建索引");
                }
                Iterator<Map.Entry<String, JsonNode>> subfields = setting.getValue().fields();
                while (subfields.hasNext()) {
                    Map.Entry<String, JsonNode> subfield = subfields.next();
                    validateManagedField(name + "." + subfield.getKey(), subfield.getValue(),
                            ((Map<?, ?>) actualFields).get(subfield.getKey()));
                }
                continue;
            }
            if (!setting.getValue().asText().equals(definition.get(setting.getKey()))) {
                throw new IOException("Elasticsearch 受管 V3 字段 " + name + " 的 "
                        + setting.getKey() + " 必须为 " + setting.getValue().asText());
            }
        }
        if (Boolean.FALSE.equals(definition.get("index"))
                || (!"text".equals(expected.path("type").asText())
                && Boolean.FALSE.equals(definition.get("doc_values")))) {
            throw new IOException("Elasticsearch 受管 V3 字段 " + name + " 不能禁用索引或排序数据");
        }
    }

    /**
     * 按 UTF-8 加载索引模板；旧初始化器和受管创建使用不同的资源。
     *
     * @param resource 类路径内的索引模板资源路径
     * @return 以 UTF-8 读取的完整索引定义 JSON
     * @throws IOException 处理过程中发生IO异常时
     */
    private String readIndexDefinition(String resource) throws IOException {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }
    }

    /**
     * 创建缺失的索引及写别名，返回别名实际指向的物理索引。
     * 已有物理索引却缺少别名时拒绝绑定，避免接管未知结构的索引。
     * 已有合法别名时校验 IK 映射并幂等补充缺失的响应截止时间 date 字段，保留别名指向。
     * 旧 standard 映射必须重建索引后切换别名；并发创建时重新检查别名和映射。
     *
     * @return 搜索别名当前指向的唯一物理索引名，可能是已部署的其他版本
     * @throws IOException 资源读取或网络请求失败、别名不符合约束、字段映射不兼容或创建未确认
     * @throws ElasticsearchStatusException 服务端拒绝索引请求且无法通过已存在的合法别名恢复
     */
    public String initializeIndex() throws IOException {
        // 查找别名指向的索引
        String existing = findSingleIndex();
        if (existing != null) {
            validateAndCompleteMapping(existing);
            return existing;
        }
        String indexName = properties.getTicketIndexName();
        if (client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
            throw new IOException("Elasticsearch 物理索引已存在但缺少搜索别名，请核对映射后手动配置别名");
        }
        // 创建索引请求
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        // 从类路径加载索引映射定义
        try (InputStream input = new ClassPathResource(INDEX_RESOURCE).getInputStream()) {
            request.source(StreamUtils.copyToString(input, StandardCharsets.UTF_8), XContentType.JSON);
        }
        request.settings(Settings.builder()
                .put("index.number_of_shards", properties.getNumberOfShards())
                .put("index.number_of_replicas", properties.getNumberOfReplicas()));
        // 添加搜索别名并设置为主写别名
        request.alias(new Alias(properties.getTicketIndexAlias()).writeIndex(true));
        // 创建索引并等待确认
        try {
            CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
            if (!response.isAcknowledged() || !response.isShardsAcknowledged()) {
                throw new IOException("Elasticsearch 索引创建尚未确认完成，请检查集群后重试初始化");
            }
        } catch (ElasticsearchStatusException exception) {
            // 多个实例同时初始化时，另一实例可能已经完成原子建索引和别名。
            if (exception.status() != RestStatus.BAD_REQUEST
                    || !ElasticsearchFailureClassifier.hasType(exception, "resource_already_exists_exception")
                    || findSingleIndex() == null) {
                throw exception;
            }
        }
        String initialized = findSingleIndex();
        if (initialized == null) {
            throw new IOException("Elasticsearch 索引创建后仍未找到搜索别名");
        }
        validateAndCompleteMapping(initialized);
        return initialized;
    }

    /**
     * 校验别名实际指向索引的标题和描述采用约定的 IK 建索引及查询分词器。
     * 拒绝缺失全文字段及旧 standard 映射；全文映射兼容后仅补充缺失的截止时间字段。
     *
     * @param indexName 已通过别名约束校验的物理索引名
     * @throws IOException 请求失败、全文或截止时间映射不兼容，或补充映射未获确认
     */
    private void validateAndCompleteMapping(String indexName) throws IOException {
        // 获取索引映射
        MappingMetadata mapping = client.indices()
                .getMapping(new GetMappingsRequest().indices(indexName),
                        RequestOptions.DEFAULT)
                .mappings()
                .get(indexName);

        // 获取字段映射
        Object fields = mapping == null ? null : mapping.sourceAsMap().get("properties");
        for (String field : new String[]{"title", "description"}) {
            // 获取字段定义
            Object definition = fields instanceof Map ? ((Map<?, ?>) fields).get(field) : null;
            if (!(definition instanceof Map)) {
                throw new IOException("Elasticsearch 搜索索引缺少全文字段 " + field + "，请使用 V2 IK 映射重建索引");
            }
            Map<?, ?> settings = (Map<?, ?>) definition;
            if (!"text".equals(settings.get("type"))
                    || !"ik_max_word".equals(settings.get("analyzer"))
                    || !"ik_smart".equals(settings.get("search_analyzer"))) {
                throw new IOException("Elasticsearch 搜索索引的 " + field
                        + " 必须使用 ik_max_word 建索引、ik_smart 查询，请重建索引并切换搜索别名");
            }
        }
        ensureResponseDeadlineMapping(indexName, (Map<?, ?>) fields);
    }

    /**
     * 仅为缺失字段追加可排序的 date 映射，不改写现有字段或回填历史文档。
     * 已有字段必须为 date 且未禁用 doc_values；并发追加相同映射保持幂等。
     *
     * @param indexName 索引名称
     * @param fields 字段集合
     * @throws IOException 处理过程中发生IO异常时
     */
    private void ensureResponseDeadlineMapping(String indexName, Map<?, ?> fields) throws IOException {
        Object definition = fields.get("responseDeadline");
        if (definition != null) {
            if (!(definition instanceof Map) || !"date".equals(((Map<?, ?>) definition).get("type"))
                    || Boolean.FALSE.equals(((Map<?, ?>) definition).get("doc_values"))) {
                throw new IOException("Elasticsearch responseDeadline 必须为可排序的 date 字段，请重建索引并切换搜索别名");
            }
            return;
        }
        PutMappingRequest request = new PutMappingRequest(indexName)
                .source("{\"properties\":{\"responseDeadline\":{\"type\":\"date\","
                        + "\"format\":\"strict_date_optional_time\"}}}", XContentType.JSON);
        if (!client.indices().putMapping(request, RequestOptions.DEFAULT).isAcknowledged()) {
            throw new IOException("Elasticsearch 响应截止时间映射补充尚未确认，请检查集群后重试初始化");
        }
    }

    /**
     * 查找搜索别名的唯一目标，并校验别名可写且没有过滤和路由条件。
     * 单索引别名未显式设置写标记时可用于写入，显式设置为 false 时拒绝使用。
     *
     * @return 别名唯一指向的物理索引名；服务端确认别名不存在时为 null
     * @throws IOException 请求失败、响应解析失败或别名的目标数量及读写条件不符合要求
     */
    private String findSingleIndex() throws IOException {
        return findSingleIndex(properties.getTicketIndexAlias());
    }

    /**
     * 查询指定别名；约束对旧搜索别名和任务专属写别名一致。
     *
     * @param aliasName 需要查询并校验可写约束的别名名称
     * @return 别名唯一指向的物理索引名；服务端确认别名不存在时为 null
     * @throws IOException 处理过程中发生IO异常时
     */
    private String findSingleIndex(String aliasName) throws IOException {
        GetAliasesResponse response = client
                .indices()
                .getAlias(
                new GetAliasesRequest(aliasName), RequestOptions.DEFAULT);
        if (response.status() == RestStatus.NOT_FOUND) {
            return null;
        }

        // 解析响应
        Map<String, Set<AliasMetadata>> aliases = response.getAliases();
        if (response.status() != RestStatus.OK || aliases.size() != 1) {
            throw new IOException("Elasticsearch 搜索别名必须只指向一个物理索引");
        }
        // 获取别名元数据
        Map.Entry<String, Set<AliasMetadata>> entry = aliases.entrySet().iterator().next();
        AliasMetadata metadata = entry
                .getValue()
                .stream()
                .filter(alias -> aliasName.equals(alias.alias()))
                .findFirst().orElse(null);
        if (metadata == null || Boolean.FALSE.equals(metadata.writeIndex())
                || metadata.filter() != null || metadata.indexRouting() != null
                || metadata.searchRouting() != null) {
            throw new IOException("Elasticsearch 搜索别名必须可写且不带过滤或路由条件");
        }
        return entry.getKey();
    }
}
