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
    // Same version sqldelight-driver pulls transitively; needed at compile time
    // for the SQLiteDataSource/SQLiteConfig setup in DatabaseFactory.
    api(libs.sqlite.jdbc)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
