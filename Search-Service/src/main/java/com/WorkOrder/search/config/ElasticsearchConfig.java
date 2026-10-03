package com.WorkOrder.search.config;

import com.WorkOrder.search.repository.TicketSearchRepository;
import com.WorkOrder.search.service.TicketSearchIndexService;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URI;

/**
 * @author Virgor
 * @date 2026年10月02日 02:12
 * @description Elasticsearch 配置类
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ElasticsearchProperties.class)
@ConditionalOnProperty(
        prefix = "work-order.elasticsearch",
        name = "enabled",
        havingValue = "true"
)
public class ElasticsearchConfig {

    /**
     * 根据节点、认证、超时和连接池配置创建共享客户端。
     * 创建时不主动连接节点，Spring 容器关闭时调用 close 释放连接。
     *
     * @param properties 已绑定并通过字段校验的 Elasticsearch 配置
     * @return 供索引服务和搜索仓库共享的高层 REST 客户端
     * @throws IllegalArgumentException 连接池配置矛盾、凭据不成对、索引名与别名相同或节点地址无效
     */
    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient(
            ElasticsearchProperties properties) {

        if (properties.getMaxConnectionsPerRoute() > properties.getMaxConnections()) {
            throw new IllegalArgumentException("单节点最大连接数不能超过总连接数");
        }

        boolean hasUsername = StringUtils.hasText(properties.getUsername());
        boolean hasPassword = StringUtils.hasText(properties.getPassword());

        if (hasUsername != hasPassword) {
            throw new IllegalArgumentException("Elasticsearch 用户名和密码必须同时配置");
        }
        if (properties.getTicketIndexAlias().equals(properties.getTicketIndexName())) {
            throw new IllegalArgumentException("Elasticsearch 索引名与别名不能相同");
        }

        HttpHost[] hosts = properties.getUris().stream()
                .map(this::toHttpHost)
                .toArray(HttpHost[]::new);

        // 创建 RestClientBuilder
        RestClientBuilder builder = RestClient.builder(hosts);
        // 设置请求超时时间
        builder.setRequestConfigCallback(request -> request
                .setConnectTimeout(properties.getConnectTimeoutMs())
                .setSocketTimeout(properties.getSocketTimeoutMs())
                .setConnectionRequestTimeout(properties.getConnectionRequestTimeoutMs()));

        // 设置连接池大小和路由最大连接数
        builder.setHttpClientConfigCallback(http -> {
            // 设置连接池大小和路由最大连接数
            http.setMaxConnTotal(properties.getMaxConnections());
            http.setMaxConnPerRoute(properties.getMaxConnectionsPerRoute());
            if (hasUsername) {
                // 设置认证信息
                BasicCredentialsProvider credentials = new BasicCredentialsProvider();
                credentials.setCredentials(
                        AuthScope.ANY,
                        new UsernamePasswordCredentials(
                            properties.getUsername(),
                                properties.getPassword()));
                // 设置认证提供者
                http.setDefaultCredentialsProvider(credentials);
            }
            return http;
        });
        return new RestHighLevelClient(builder);
    }

    /**
     * 注册索引初始化和连通性检查服务，此处不创建远程索引。
     *
     * @param client 由容器管理的共享 Elasticsearch 客户端
     * @param properties 索引名、别名及首次创建时使用的分片配置
     * @return 工单搜索索引管理服务
     */
    @Bean
    public TicketSearchIndexService ticketSearchIndexService(
            RestHighLevelClient client, ElasticsearchProperties properties) {
        return new TicketSearchIndexService(client, properties);
    }

    /**
     * 注册工单搜索投影的读写仓库，此处不导入或查询业务数据。
     *
     * @param client 由容器管理的共享 Elasticsearch 客户端
     * @param properties 读写别名及批量写入上限配置
     * @return 工单搜索投影仓库
     */
    @Bean
    public TicketSearchRepository ticketSearchRepository(
            RestHighLevelClient client, ElasticsearchProperties properties) {
        return new TicketSearchRepository(client, properties);
    }

    /**
     * 创建启动后的索引初始化任务，仅在组件和启动初始化开关均开启时注册。
     * 任务执行失败会阻止服务启动，不自动导入工单数据。
     *
     * @param indexService 执行索引及别名校验、创建的服务
     * @return 应用启动后执行一次的初始化任务
     */
    @Bean
    @ConditionalOnProperty(prefix = "work-order.elasticsearch",
            name = "initialize-on-startup", havingValue = "true")
    public ApplicationRunner ticketSearchIndexInitializer(TicketSearchIndexService indexService) {
        return args -> indexService.initializeIndex();
    }

    /**
     * 将节点地址转换为 HTTP 主机，并拒绝 URI 内嵌凭据、业务路径和查询参数。
     * 允许根路径及未显式指定端口的 http/https 地址，异常信息不回显输入内容。
     *
     * @param value 配置中单个 Elasticsearch 节点的 URI
     * @return 包含协议、主机及端口的节点对象
     * @throws IllegalArgumentException URI 格式、协议、主机、端口或附加内容无效
     */
    private HttpHost toHttpHost(String value) {
        URI uri;
        try {
            // 创建 URI 对象
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            // 不回显地址，避免其中误填的凭据进入异常日志。
            throw new IllegalArgumentException("Elasticsearch 节点地址格式无效");
        }
        // 验证 URI 的协议、主机、端口和路径
        String scheme = uri.getScheme();
        boolean validScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        boolean validPath = !StringUtils.hasText(uri.getPath()) || "/".equals(uri.getPath());
        if (!validScheme || !StringUtils.hasText(uri.getHost()) || !validPath
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Elasticsearch 节点需使用 http/https 地址，凭据单独配置，不能带路径或查询参数");
        }
        return new HttpHost(uri.getHost(), uri.getPort(), scheme);
    }
}
