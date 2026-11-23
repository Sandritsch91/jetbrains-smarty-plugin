import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.1"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = "io.github.sandritsch91"
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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // Same bytecode level as the Java tasks: sinceBuild 243 (2024.3) runs on Java 21.
        jvmTarget.set(JvmTarget.JVM_21)
        // The IDE provides the Kotlin stdlib at runtime (kotlin.stdlib.default.dependency=false in gradle.properties),
        // so stay on the API of the oldest supported IDE: 2024.3 bundles Kotlin 2.0. Raise it together with sinceBuild.
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        // API 2.0 is deprecated in the current compiler but required by the line above
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.sandritsch91.smarty"
        name = "Smarty Templates"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
        // the section of CHANGELOG.md for this version (or [Unreleased] while there is none)
        changeNotes = provider {
            val item = changelog.getOrNull(project.version.toString()) ?: changelog.getUnreleased()
            changelog.renderItem(item.withHeader(false).withEmptySections(false), Changelog.OutputType.HTML)
        }
    }
    // Marketplace release (.github/workflows/release.yml): the certificate and token come from environment secrets
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    buildSearchableOptions = false
    instrumentCode = false
    // ./gradlew verifyPlugin: JetBrains Plugin Verifier against PhpStorm, a newer IDEA and the IDEA build used for compiling.
    pluginVerification {
        ides {
            fun versions(name: String) =
                providers.gradleProperty(name).get().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            versions("verifyPhpStormVersions").forEach { create(IntelliJPlatformType.PhpStorm, it) }
            versions("verifyIdeaVersions").forEach { create(IntelliJPlatformType.IntellijIdeaUltimate, it) }
            val localPath = providers.gradleProperty("platformLocalPath").orNull
            if (!localPath.isNullOrBlank() && file(localPath).exists()) local(localPath)
        }
    }
}

// ./gradlew test -PwithoutPhp: the plugin as in an IDE without the PHP plugin (WebStorm, IDEA without PHP).
// PHP stays on the compile classpath, but is disabled in the test IDE; the tests that need it are skipped.
val withoutPhp = providers.gradleProperty("withoutPhp").isPresent

tasks.prepareTestSandbox {
    if (withoutPhp) disabledPlugins.add("com.jetbrains.php")
}

tasks.test {
    // Tests boot a full headless IDE; give them room.
    maxHeapSize = "2g"
    if (withoutPhp) {
        exclude("**/SmartyPhpTest*", "**/SmartyYiiTest*", "**/SmartyDocumentationTest*")
        systemProperty("smarty.withoutPhp", "true")
    }
}

changelog {
    groups.empty()
    repositoryUrl = "https://github.com/Sandritsch91/jetbrains-smarty-plugin"
}
