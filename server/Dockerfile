# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷贝 pom，利用 Docker 层缓存避免每次改代码都重新下载依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

COPY --from=build /build/target/game-companion-server-*.jar /app/app.jar

# 固定 UID/GID，便于宿主机挂载证书与文件时对齐权限
RUN addgroup -S -g 10001 app \
    && adduser -S -u 10001 -G app app \
    && mkdir -p /app/uploads \
    && chown -R app:app /app

USER app
EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O - http://127.0.0.1:8088/api/health | grep -q 200 || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
