import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    jvm()

    val xcf = XCFramework("core")
    macosArm64 {
        binaries.framework {
            baseName = "core"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(libs.jmdns)
        }
    }
}

// The XCFramework goes where the NearPingKit package expects it.
// Copy turns the framework's Versions symlinks into folders, which SwiftPM rejects; ditto keeps them.
val installXCFramework by tasks.registering(Exec::class) {
    dependsOn("assembleCoreReleaseXCFramework")
    val source = layout.buildDirectory.dir("XCFrameworks/release/core.xcframework").get().asFile.path
    val target = rootProject.layout.projectDirectory.dir("apps/appleApp/NearPingKit/Frameworks/core.xcframework").asFile.path
    commandLine("sh", "-c", "rm -rf \"$1\" && ditto \"$0\" \"$1\"", source, target)
}
