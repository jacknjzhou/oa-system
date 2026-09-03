# ---- 构建阶段：容器内编译打包 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先单独复制 pom 并拉取依赖，作为独立缓存层
# 仅当 pom.xml 变化时才重新下载依赖，日常改代码可复用缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再复制源码编译打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre-alpine
LABEL authors="oa-system"
WORKDIR /app
COPY --from=build /app/target/oa-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
