# 本机使用已下载的国内镜像，版本与项目 Java 客户端保持一致。
ARG ELASTICSEARCH_IMAGE=docker.m.daocloud.io/library/elasticsearch:7.12.1@sha256:622f854572780281bc85b5fde33be27e99670941ed8b7eea5ba4aaf533fa64ec
FROM ${ELASTICSEARCH_IMAGE}

# 校验值来自两条 IK 官方分发地址，下载后必须一致；插件版本需与 ES 完全匹配。
ARG IK_DOWNLOAD_URL=https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-7.12.1.zip
RUN curl --fail --show-error --silent --location --retry 3 --max-time 120 \
        "${IK_DOWNLOAD_URL}" -o /tmp/analysis-ik.zip \
    && echo "422a878225e56a0947eb01e8490f2054a1bce6cb0ec5b4be1cd10c22680b14d7b1811566e2c635fcc8df70c7701e15b2390b85455206e0ccb1b52bacde570686  /tmp/analysis-ik.zip" | sha512sum --check - \
    && bin/elasticsearch-plugin install --batch file:///tmp/analysis-ik.zip \
    && rm /tmp/analysis-ik.zip
