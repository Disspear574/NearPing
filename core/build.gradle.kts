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
            // Сырые ключи X25519 и Ed25519 первым классом: в JDK они кодируются
            // через BigInteger, и совпасть с CryptoKit побайтово там легко не получится.
            implementation(libs.jmdns)
        }
    }
}

// XCFramework кладётся туда, где его ждёт macos/project.yml.
val installXCFramework by tasks.registering(Copy::class) {
    dependsOn("assembleCoreReleaseXCFramework")
    from(layout.buildDirectory.dir("XCFrameworks/release"))
    into(rootProject.layout.projectDirectory.dir("macos/Frameworks"))
}
