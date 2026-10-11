# 后端：SpringBoot 2.7.2 (Java 8) + MyBatis —— 多阶段构建
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build

# 使用国内镜像加速依赖下载
COPY maven-settings.xml /root/.m2/settings.xml

# 先解析依赖，利用 Docker 层缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再编译源码
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:8-jre
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

COPY --from=build /build/target/*.jar /app/app.jar

EXPOSE 9090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
