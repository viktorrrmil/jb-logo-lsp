plugins {
    kotlin("jvm") version "1.9.22"
    antlr
    application
    id("com.gradleup.shadow") version "9.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.1")
    implementation("org.antlr:antlr4-runtime:4.13.1")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
}

application {
    mainClass.set("dev.jb.logolsp.MainKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.generateGrammarSource {
    arguments = listOf("-visitor", "-no-listener")
}

sourceSets {
    main {
        java.srcDir(layout.buildDirectory.dir("generated-src/antlr/main"))
    }
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileJava {
    dependsOn(tasks.generateGrammarSource)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("jb-logo-lsp")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
}