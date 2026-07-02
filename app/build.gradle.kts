/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 LSPosed Contributors
 */

import java.time.Instant

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.autoresconfig)
    alias(libs.plugins.lsplugin.apksign)
}

apksign {
    storeFileProperty = "androidStoreFile"
    storePasswordProperty = "androidStorePassword"
    keyAliasProperty = "androidKeyAlias"
    keyPasswordProperty = "androidKeyPassword"
}

val defaultManagerPackageName: String by rootProject.extra

android {
    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = defaultManagerPackageName
        buildConfigField("long", "BUILD_TIME", Instant.now().epochSecond.toString())
    }

    packaging {
        resources {
            excludes += "META-INF/**"
            excludes += "okhttp3/**"
            excludes += "kotlin/**"
            excludes += "org/**"
            excludes += "**.properties"
            excludes += "**.bin"
        }
    }

    dependenciesInfo.includeInApk = false

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }

    sourceSets { named("main") { res { srcDirs("src/common/res") } } }
    namespace = defaultManagerPackageName
}

autoResConfig {
    generateClass = true
    generateRes = false
    generatedClassFullName = "org.lsposed.manager.util.LangList"
    generatedArrayFirstItem = "SYSTEM"
}

dependencies {
    annotationProcessor(libs.glide.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.materialkolor)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
    implementation(libs.glide)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.rikkax.core)
    implementation(libs.appiconloader)
    implementation(libs.hiddenapibypass)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(projects.services.managerService)
}

configurations.all {
    exclude("androidx.appcompat", "appcompat")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    // org.jetbrains:annotations is required on the compile classpath by the Kotlin
    // compiler backend (nullability codegen). Keep it out of the runtime classpath only,
    // so it is not bundled into the APK (@NotNull is CLASS-retention, unused at runtime).
    if (name.endsWith("RuntimeClasspath")) {
        exclude("org.jetbrains", "annotations")
    }
}
