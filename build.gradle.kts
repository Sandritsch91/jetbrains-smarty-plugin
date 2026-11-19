import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "com.smartyplugin"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localPath = providers.gradleProperty("platformLocalPath").orNull
        if (!localPath.isNullOrBlank() && file(localPath).exists()) {
            local(localPath)
        } else {
            create(
                providers.gradleProperty("platformType").get(),
                providers.gradleProperty("platformVersion").get()
            )
        }
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

intellijPlatform {
    pluginConfiguration {
        id = "com.smartyplugin.smarty"
        name = "Smarty Templates"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
    instrumentCode = false
}

tasks.test {
    // Tests boot a full headless IDE; give them room.
    maxHeapSize = "2g"
}
