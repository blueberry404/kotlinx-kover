plugins {
    kotlin("jvm") version "1.6.10"

    `java-gradle-plugin`
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
}

sourceSets {
    create("functionalTest") {
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath
        runtimeClasspath += output + compileClasspath
    }
}

dependencies {
    implementation(gradleApi())
    // exclude transitive dependency on stdlib, the Gradle version should be used
    compileOnly(kotlin("stdlib"))

    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
    compileOnly("com.android.tools.build:gradle:7.2.0")

    testImplementation(kotlin("test"))

//    "functionalTestImplementation"(gradleTestKit())
//    // dependencies only for plugin's classpath to work with Kotlin Multi-Platform and Android plugins
//    "functionalTestCompileOnly"("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
//    "functionalTestCompileOnly"("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.6.10")
//    "functionalTestCompileOnly"("org.jetbrains.kotlin:kotlin-compiler-runner:1.6.10")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}



tasks.register<Test>("functionalTest") {
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath


    // While gradle testkit supports injection of the plugin classpath it doesn't allow using dependency notation
    // to determine the actual runtime classpath for the plugin. It uses isolation, so plugins applied by the build
    // script are not visible in the plugin classloader. This means optional dependencies (dependent on applied plugins -
    // for example kotlin multiplatform) are not visible even if they are in regular gradle use. This hack will allow
    // extending the classpath. It is based upon: https://docs.gradle.org/6.0/userguide/test_kit.html#sub:test-kit-classpath-injection
    // Create a configuration to register the dependencies against
    doFirst {
        val file = File(temporaryDir, "plugin-classpath.txt")
        file
            .writeText(sourceSets["functionalTest"].compileClasspath.joinToString("\n"))
        systemProperties["plugin-classpath"] = file.absolutePath
    }
}

tasks.check { dependsOn("functionalTest") }


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        allWarningsAsErrors = true

        // Kover works with the stdlib of at least version `1.4.x`
        languageVersion = "1.4"
        apiVersion = "1.4"
        // Kotlin compiler 1.6 issues a warning if `languageVersion` or `apiVersion` 1.4 is used - suppress it
        freeCompilerArgs = freeCompilerArgs + "-Xsuppress-version-warnings"
    }
}

// override version in deploy
properties["DeployVersion"]?.let { version = it }


publishing {
    publications {
        // `pluginMaven` - standard name for the publication task of the `java-gradle-plugin`
        create<MavenPublication>("pluginMaven") {
            // `java` component will be added by the `java-gradle-plugin` later
            addExtraMavenArtifacts(project, project.sourceSets.main.get().allSource)
        }
    }

    addMavenRepository(project)
    addMavenMetadata()
    publications.withType(MavenPublication::class).all {
        signPublicationIfKeyPresent(project)
    }
}


gradlePlugin {
    plugins {
        create("Kover") {
            id = "org.jetbrains.kotlinx.kover"
            implementationClass = "kotlinx.kover.KoverPlugin"
            displayName = "Kotlin Code Coverage Plugin"
            description = "Evaluate code coverage for projects written in Kotlin"
        }
    }
}
