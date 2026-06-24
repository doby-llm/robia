plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":additional-info-core"))
    implementation(libs.tensorflow.lite)
}

application {
    mainClass.set("com.gusanitolabs.robia.additionalinfo.cli.AdditionalInfoCliKt")
}
