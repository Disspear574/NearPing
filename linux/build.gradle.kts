import java.security.MessageDigest
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(when (providers.gradleProperty("linuxTarget").orNull) {
        "x64" -> compose.desktop.linux_x64
        "arm64" -> compose.desktop.linux_arm64
        else -> compose.desktop.currentOs
    })
    implementation(compose.material3)
    implementation(libs.coroutines.core)
}

// Проверка обнаружения без интерфейса: ./gradlew :linux:probe
tasks.register<JavaExec>("probe") {
    group = "verification"
    description = "Поднимает узел и печатает, кого нашёл в сети"
    mainClass = "com.disspear574.earshot.linux.ProbeKt"
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(project.findProperty("seconds")?.toString() ?: "20")
    systemProperty("earshot.debug", "1")
    standardOutput = System.out
}

compose.desktop {
    application {
        mainClass = "com.disspear574.earshot.linux.MainKt"
        nativeDistributions {
            // .deb и AppImage собираются только на Linux: jpackage делает пакет
            // той системы, на которой запущен.
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage, TargetFormat.Dmg)
            packageName = "nearping"
            packageVersion = "0.1.0"
            description = "Call a colleague who is wearing headphones"
            vendor = "disspear574"
            linux {
                menuGroup = "Utility"
                appCategory = "Utility"
            }
        }
    }
}

// Portable bundle can be assembled on macOS with Linux native dependencies.
tasks.register<Sync>("portableLibs") {
    dependsOn(tasks.named("jar"))
    into(layout.buildDirectory.dir("portable/lib"))
    from(tasks.named("jar"))
    from(configurations.runtimeClasspath) {
        eachFile {
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            name = "${hash.take(16)}-$name"
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
