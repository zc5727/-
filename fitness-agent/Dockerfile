# =========================================================
# 智能体服务镜像：多阶段构建
#   构建阶段 maven + JDK17（依赖层单独缓存，源码改动不重拉依赖）
#   运行阶段 仅 JRE17，非 root 用户运行
# =========================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY maven-settings.xml /root/.m2/settings.xml

# 先只拷 pom 拉依赖，充分利用 Docker 层缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms192m -Xmx384m -XX:+UseSerialGC -Dfile.encoding=UTF-8"

# 运行镜像默认不带 curl，装上以便做容器级健康检查
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r agent && useradd -r -g agent agent

COPY --from=build /build/target/fitness-agent.jar /app/app.jar
# 知识库随镜像带一份默认版本：即使不挂卷也能用。
# 运行时 docker-compose 会把仓库里的 fitness-agent/knowledge 挂到同一路径覆盖它，
# 于是「改一篇 Markdown 不必重建镜像」这件事才成立。
COPY knowledge /app/knowledge
RUN chown -R agent:agent /app
USER agent

EXPOSE 9091

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD ["sh","-c","curl -fsS http://127.0.0.1:9091/actuator/health | grep -q '\"status\":\"UP\"'"]

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
