import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    id("io.papermc.paperweight.patcher") version "2.0.0-beta.19"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

paperweight {
    upstreams.register("purpur") {
        repo = github("PurpurMC", "Purpur")
        ref = providers.gradleProperty("purpurRef")

        patchFile {
            path = "purpur-server/build.gradle.kts"
            outputFile = file("divinemc-server/build.gradle.kts")
            patchFile = file("divinemc-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "purpur-api/build.gradle.kts"
            outputFile = file("divinemc-api/build.gradle.kts")
            patchFile = file("divinemc-api/build.gradle.kts.patch")
        }
        patchRepo("paperApi") {
            upstreamPath = "paper-api"
            patchesDir = file("divinemc-api/paper-patches")
            outputDir = file("paper-api")
        }
        patchDir("purpurApi") {
            upstreamPath = "purpur-api"
            excludes = listOf("build.gradle.kts", "build.gradle.kts.patch", "paper-patches")
            patchesDir = file("divinemc-api/purpur-patches")
            outputDir = file("purpur-api")
        }
    }
}


val repairPaperweightUpstreamState by tasks.registering {
    doLast {
        val upstreamsRoot = layout.projectDirectory.dir(".gradle/caches/paperweight/upstreams").asFile
        if (!upstreamsRoot.exists()) return@doLast

        upstreamsRoot.walkTopDown()
            .filter { it.isDirectory && it.name == ".git" }
            .forEach { gitDir ->
                val hasBaseBranch = providers.exec {
                    commandLine("git", "--git-dir=${gitDir.absolutePath}", "rev-parse", "--verify", "base")
                    isIgnoreExitValue = true
                }.result.get().exitValue == 0

                if (hasBaseBranch) {
                    return@forEach
                }

                val hasHead = providers.exec {
                    commandLine("git", "--git-dir=${gitDir.absolutePath}", "rev-parse", "--verify", "HEAD")
                    isIgnoreExitValue = true
                }.result.get().exitValue == 0

                if (hasHead) {
                    logger.lifecycle("Repairing paperweight upstream checkout at ${gitDir.parentFile.absolutePath} (creating missing 'base' branch)")
                    providers.exec {
                        commandLine("git", "--git-dir=${gitDir.absolutePath}", "update-ref", "refs/heads/base", "HEAD")
                        isIgnoreExitValue = true
                    }.result.get()
                }
            }
    }
}

gradle.allprojects {
    tasks.matching {
        it.name == "checkoutPurpurRepo" ||
            it.name == "applyUpstream" ||
            it.name == "applyAllPatches" ||
            (it.name.startsWith("applyPurpur") && it.name.endsWith("FilePatches"))
    }.configureEach {
        dependsOn(rootProject.tasks.named("repairPaperweightUpstreamState"))
    }
}


allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.compileJava {
        options.compilerArgs.add("-Xlint:-deprecation")
        options.isWarnings = false
    }

    tasks.withType(JavaCompile::class.java).configureEach {
        options.isFork = true
        options.forkOptions.memoryMaximumSize = "4G"
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
        options.isFork = true
    }
    tasks.withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources> {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test> {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven("https://repo.bxteam.org/snapshots") {
                name = "divinemc"

                credentials.username = System.getenv("REPO_USERNAME")
                credentials.password = System.getenv("REPO_PASSWORD")
            }
        }
    }
}

tasks.register("printMinecraftVersion") {
    doLast {
        println(providers.gradleProperty("mcVersion").get().trim())
    }
}
