plugins {
    java
    id("io.spring.dependency-management") version "1.1.7"
    id("net.serenity-bdd.serenity-gradle-plugin") version "5.4.2"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16")
    }
}

val serenityVersion = "5.4.1"
val cucumberVersion = "8.0.4"

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform" || requested.group == "org.junit.jupiter") {
            useVersion("6.1.3")
        }
    }
}

dependencies {
    testImplementation(project(":"))

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.16")
    testImplementation("org.springframework.boot:spring-boot-testcontainers:3.5.16")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")

    testImplementation("net.serenity-bdd:serenity-core:$serenityVersion")
    testImplementation("net.serenity-bdd:serenity-cucumber:$serenityVersion")
    testImplementation("net.serenity-bdd:serenity-junit5:$serenityVersion")
    testImplementation("net.serenity-bdd:serenity-spring:$serenityVersion")

    testImplementation("org.junit.platform:junit-platform-suite:6.1.3")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-spring:$cucumberVersion")

    testImplementation("io.rest-assured:rest-assured:5.5.1")
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
    }

    val colimaSocket = File(System.getProperty("user.home"), ".colima/default/docker.sock")
    if (System.getenv("DOCKER_HOST") == null && colimaSocket.exists()) {
        environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    }

    finalizedBy("aggregate")
}
