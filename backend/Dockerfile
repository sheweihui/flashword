# 使用官方 OpenJDK 17 镜像作为基础
FROM openjdk:17-slim

# 设置工作目录
WORKDIR /app

# 复制 Maven 构建的 JAR 包
COPY target/backend-1.0-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用（带 JVM 优化参数）
ENTRYPOINT ["java", \
    "-Xms256m", \
    "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-jar", "app.jar"]
