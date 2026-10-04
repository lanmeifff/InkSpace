# ── 构建阶段：利用依赖缓存分层，源码改动不会重复下载依赖 ──
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 国内构建走阿里云仓库，避免中央仓拉取超时
COPY docker/maven-settings.xml /build/maven-settings.xml
COPY pom.xml .
RUN mvn -B -q -s /build/maven-settings.xml dependency:go-offline
COPY src ./src
RUN mvn -B -q -s /build/maven-settings.xml clean package -DskipTests

# ── 运行阶段：只带 JRE，镜像更小；非 root 运行 ──
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 1001 appuser

COPY --from=build /build/target/*.jar app.jar

RUN mkdir -p /app/uploads && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fs http://localhost:8080/api/v1/ping || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
