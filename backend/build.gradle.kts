plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	// Load tests in src/gatling (M3); run with ./gradlew gatlingRun, never part of build.
	id("io.gatling.gradle") version "3.16.0"
}

group = "io.vurli"
version = "0.0.1-SNAPSHOT"
description = "Vurli link shortener"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("com.github.ben-manes.caffeine:caffeine")
	// In-memory rate limits (K42).
	implementation("com.bucket4j:bucket4j_jdk17-core:8.21.0")
	// Reads the DB-IP country database (MaxMind DB format) for click statistics (K44).
	implementation("com.maxmind.db:maxmind-db:4.2.0")
	// Argon2 password hashing in Spring Security needs BouncyCastle.
	implementation("org.bouncycastle:bcprov-jdk18on:1.86")
	// QR codes; only the core encoder, images are drawn without AWT extras.
	implementation("com.google.zxing:core:3.5.4")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// bootRun is local development only: dev profile with seed workspace and dev API.
tasks.bootRun {
	systemProperty("spring.profiles.active", "dev")
}
