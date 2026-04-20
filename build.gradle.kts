plugins {
    kotlin("jvm") version "1.9.22"
    antlr
    id("com.gradleup.shadow") version "9.3.0"
    application
}

application {
    mainClass.set("dev.jb.logolsp.MainKt")
}

group = "dev.jb"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.1")
    implementation("org.antlr:antlr4-runtime:4.13.1")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
    implementation(kotlin("stdlib"))
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-no-listener", "-Xexact-output-dir")
    outputDirectory = file("${layout.buildDirectory.get()}/generated-src/antlr/main")
}

tasks.shadowJar {
    mergeServiceFiles()
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDirs(
                "src/main/kotlin",
                "${layout.buildDirectory.get()}/generated-src/antlr/main"
            )
        }
    }
}

