import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.dokka)
}

group = "io.github.dmerzlyakov"

kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    android {
        namespace = "com.dmerzliakov.freezekit.mvi"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.runtimeCompose)
            api(libs.kotlinx.coroutinesCore)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

mavenPublishing {
    coordinates(group.toString(), "freezekit-mvi", version.toString())
    publishToMavenCentral(automaticRelease = false)
    signAllPublications()

    pom {
        name.set("MobileFreezeKit MVI")
        description.set("Kotlin Multiplatform MVI presentation contracts, ViewModel and Compose integration")
        inceptionYear.set("2026")
        url.set("https://github.com/DMerzlyakov/mobile-freeze-kit")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("DMerzlyakov")
                name.set("Danila Merzlyakov")
                email.set("merzlyakovdanila@gmail.com")
                organization.set("Danila Merzlyakov")
                organizationUrl.set("https://github.com/DMerzlyakov")
                url.set("https://github.com/DMerzlyakov")
            }
        }

        scm {
            url.set("https://github.com/DMerzlyakov/mobile-freeze-kit")
            connection.set("scm:git:https://github.com/DMerzlyakov/mobile-freeze-kit.git")
            developerConnection.set("scm:git:ssh://git@github.com/DMerzlyakov/mobile-freeze-kit.git")
        }
    }
}
