plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)
}

sqldelight {
    databases {
        create("KenangDb") {
            packageName.set("id.kenang.core.db")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    api(libs.sqldelight.driver)
    api(libs.sqldelight.coroutines)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
