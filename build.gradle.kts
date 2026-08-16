import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

plugins {
    kotlin("jvm") version "2.3.20"
    `java-library`
    `maven-publish`
}

group = "org.tekfive"
version = providers.gradleProperty("releaseVersion").getOrElse("1.0.0")

repositories {
    mavenCentral()
    maven {
        name = "JitPack"
        url = uri("https://jitpack.io")
    }
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(kotlin("stdlib"))
    api(kotlin("reflect"))
    api("com.github.TekFive:ack:v1.0.0")
    // Exact commit tagged v1.0.0. JitPack's tag coordinate collides with the legacy 1.0.0 tag.
    api("com.github.TekFive:jfk:55b9e2676e")
    api("jakarta.servlet:jakarta.servlet-api:6.0.0")
    api("javax.servlet:javax.servlet-api:4.0.1")
    api("org.eclipse.jetty:jetty-server:12.1.7")
    api("io.undertow:undertow-core:2.3.21.Final")

    api("org.slf4j:slf4j-api:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.slf4j:slf4j-simple:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    doLast {
        val jarFile = archiveFile.get().asFile
        // Write the archive in the OS temp dir first; direct zip writes under this shared workspace mount can be sparse.
        val tempJar = File.createTempFile("${project.name}-${name}-", ".jar")
        val manifestFile = temporaryDir.resolve("MANIFEST.MF")
        manifest.writeTo(manifestFile)

        val entries = sortedMapOf<String, File>()
        sourceSets.main.get().output.files
            .filter { it.exists() }
            .forEach { outputRoot ->
                if (outputRoot.isDirectory) {
                    outputRoot.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val entryName = outputRoot.toPath()
                                .relativize(file.toPath())
                                .toString()
                                .replace(File.separatorChar, '/')

                            if (entryName != "META-INF/MANIFEST.MF") {
                                entries.putIfAbsent(entryName, file)
                            }
                        }
                } else {
                    entries.putIfAbsent(outputRoot.name, outputRoot)
                }
            }

        try {
            manifestFile.inputStream().use { manifestInput ->
                tempJar.outputStream().buffered().use { fileOutput ->
                    JarOutputStream(fileOutput, Manifest(manifestInput)).use { jarOutput ->
                        entries.forEach { (entryName, file) ->
                            val entry = JarEntry(entryName)
                            entry.time = 0L
                            jarOutput.putNextEntry(entry)
                            file.inputStream().use { it.copyTo(jarOutput) }
                            jarOutput.closeEntry()
                        }
                    }
                }
            }

            tempJar.copyTo(jarFile, overwrite = true)
        } finally {
            tempJar.delete()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "kviash"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri(
                System.getenv("GITHUB_REPOSITORY")?.let { "https://maven.pkg.github.com/$it" }
                    ?: "https://maven.pkg.github.com/TekFive/kviash",
            )
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key") as String?
            }
        }
    }
}
