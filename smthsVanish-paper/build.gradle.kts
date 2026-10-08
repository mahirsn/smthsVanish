dependencies {
    implementation(project(":smthsVanish-common"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.124-stable")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.20")
    testImplementation("io.papermc.paper:paper-api:26.2.build.124-stable")
}

tasks.processResources {
    val descriptorVersion = version.toString()
    inputs.property("descriptorVersion", descriptorVersion)
    filesMatching("paper-plugin.yml") {
        filter { line: String -> line.replace("\${version}", descriptorVersion) }
    }
}

tasks.jar {
    archiveBaseName.set("smthsVanish-paper")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST", "module-info.class")
    }
}
