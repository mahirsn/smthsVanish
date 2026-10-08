dependencies {
    implementation(project(":smthsVanish-common"))
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.12.6")
    compileOnly("com.velocitypowered:velocity-api:4.2.0") {
        exclude(group = "com.velocitypowered", module = "velocity-brigadier")
    }
}

tasks.processResources {
    val descriptorVersion = version.toString()
    inputs.property("descriptorVersion", descriptorVersion)
    filesMatching("velocity-plugin.json") {
        filter { line: String -> line.replace("\${version}", descriptorVersion) }
    }
}

tasks.jar {
    archiveBaseName.set("smthsVanish-velocity")
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
