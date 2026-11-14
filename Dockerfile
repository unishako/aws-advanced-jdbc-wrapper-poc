# aws-advanced-jdbc-wrapper-poc
# - build  : Gradle でビルド
# - test   : DB(Writer/Reader)に接続するテストを実行 (compose の app-test から使用)
# - runtime: 実行用イメージ
FROM gradle:9.9.1-jdk25 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src src
RUN gradle bootJar --no-daemon -x test

FROM build AS test
ENV RUN_DB_TESTS=true
CMD ["gradle", "test", "--no-daemon", "--info"]

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
ENV TZ=Asia/Tokyo
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
