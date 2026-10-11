plugins { id("net.neoforged.moddev") version "2.0.148" }
neoForge {
    version = "21.1.248"
    runs {
        create("server") {
            server()
            gameDirectory.set(layout.buildDirectory.dir("run-server"))
            programArgument("--nogui")
            jvmArgument("-Xmx1G")
        }
    }
    mods { create("pulsenetwork") { sourceSet(sourceSets.main.get()); sourceSet(project(":common").extensions.getByType<SourceSetContainer>()["main"]) } }
}
dependencies { implementation(project(":common")) }
tasks.jar {
    archiveBaseName.set("PulseNetwork-neoforge")
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
