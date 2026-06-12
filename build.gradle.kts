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
    maven("https://maven.citizensnpcs.co/repo")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paperApiVersion").get()}")
    compileOnly("net.citizensnpcs:citizens-main:${providers.gradleProperty("citizensVersion").get()}") {
        exclude(group = "*", module = "*")
    }
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("PulseGames")
}
