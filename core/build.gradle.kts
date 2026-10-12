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

// The XCFramework goes where macos/project.yml expects it.
val installXCFramework by tasks.registering(Copy::class) {
    dependsOn("assembleCoreReleaseXCFramework")
    from(layout.buildDirectory.dir("XCFrameworks/release"))
    into(rootProject.layout.projectDirectory.dir("macos/Frameworks"))
}
