plugins {
    java
}

group = "uk.co.playerready"
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paperApiVersion").get()}")
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("paperApiVersion").get()}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    inputs.property("pluginVersion", project.version.toString())
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("PulseGames")
}

tasks.test {
    useJUnitPlatform()
}
