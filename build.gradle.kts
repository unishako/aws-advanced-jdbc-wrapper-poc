plugins {
    java
    id("org.springframework.boot") version "4.1.2"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // Doma 3 (Spring Boot 連携スターターは Doma 3.11.1 / Spring Boot 4.0.0 向けのため、Doma 本体は最新版に上書き)
    implementation("org.seasar.doma.boot:doma-spring-boot-starter:3.0.0")
    implementation("org.seasar.doma:doma-core:3.15.0")
    annotationProcessor("org.seasar.doma:doma-processor:3.15.0")

    // AWS Advanced JDBC Wrapper (内部コネクションプールに HikariCP を使用)
    implementation("software.amazon.jdbc:aws-advanced-jdbc-wrapper:4.6.0")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("com.zaxxer:HikariCP")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}
