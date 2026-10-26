import java.util.Properties

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.dokka) apply false
}

val releaseVersion = providers
    .fileContents(layout.projectDirectory.file("version.properties"))
    .asText
    .map { contents ->
        Properties()
            .apply { load(contents.reader()) }
            .getProperty("version")
    }
    .get()

allprojects {
    version = releaseVersion
}
