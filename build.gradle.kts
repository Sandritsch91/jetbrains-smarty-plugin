import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
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

/** Branch of the target IDE, e.g. 262 for 2026.2 or 263 for 2026.3. */
fun targetBranch(localPath: String?): Int {
    if (localPath != null) {
        val info = file("$localPath/product-info.json").readText()
        return Regex("\"buildNumber\"\\s*:\\s*\"(\\d+)").find(info)!!.groupValues[1].toInt()
    }
    val (year, release) = providers.gradleProperty("platformVersion").get().split(".").map { it.toInt() }
    return (year - 2000) * 10 + release
}

dependencies {
    intellijPlatform {
        val localPath = providers.gradleProperty("platformLocalPath").orNull
            ?.takeIf { it.isNotBlank() && file(it).exists() }
        if (localPath != null) {
            local(localPath)
        } else {
            create(
                providers.gradleProperty("platformType").get(),
                providers.gradleProperty("platformVersion").get()
            )
        }
        // Since 2026.3 XML/HTML support is the bundled plugin com.intellij.xml (it still provides the
        // com.intellij.modules.xml module that plugin.xml depends on); before that it was part of the core.
        if (targetBranch(localPath) >= 263) bundledPlugin("com.intellij.xml")
        // Optional at runtime (META-INF/smarty-php.xml): template variables passed in from PHP code.
        // Bundled in PhpStorm; for IntelliJ IDEA Ultimate the matching version comes from JetBrains Marketplace.
        val phpBundled = localPath == null && providers.gradleProperty("platformType").get() == "PS"
        if (phpBundled) bundledPlugin("com.jetbrains.php") else compatiblePlugin("com.jetbrains.php")
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
    // ./gradlew verifyPlugin: JetBrains Plugin Verifier against PhpStorm, a newer IDEA and the IDEA build used for compiling.
    pluginVerification {
        ides {
            fun versions(name: String) = providers.gradleProperty(name).get().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            versions("verifyPhpStormVersions").forEach { create(IntelliJPlatformType.PhpStorm, it) }
            versions("verifyIdeaVersions").forEach { create(IntelliJPlatformType.IntellijIdeaUltimate, it) }
            val localPath = providers.gradleProperty("platformLocalPath").orNull
            if (!localPath.isNullOrBlank() && file(localPath).exists()) local(localPath)
        }
    }
}

tasks.test {
    // Tests boot a full headless IDE; give them room.
    maxHeapSize = "2g"
}
