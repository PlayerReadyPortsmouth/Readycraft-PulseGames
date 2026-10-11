plugins { java }
subprojects {
    apply(plugin = "java")
    group = "uk.co.playerready"
    version = "0.1.0"
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.opencollab.dev/main/")
    }
    extensions.configure<JavaPluginExtension> { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testImplementation"("org.mockito:mockito-core:5.15.2")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
project(":paper") {
    dependencies {
        "implementation"(project(":common"))
        "compileOnly"("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
        "testImplementation"("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    }
}
project(":velocity") {
    dependencies {
        "implementation"(project(":common"))
        "compileOnly"("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
        "testImplementation"("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
        "annotationProcessor"("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
        "compileOnly"("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
    }
}
configure(listOf(project(":paper"), project(":velocity"))) {
    tasks.named<Jar>("jar") {
        archiveBaseName.set("PulseNetwork-${project.name}")
        from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
