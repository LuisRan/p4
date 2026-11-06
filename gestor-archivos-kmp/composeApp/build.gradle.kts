import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo compartido (Compose Multiplatform). Contiene commonMain (toda la UI y la lógica),
// androidMain (SAF/ContentResolver) e iosMain (NSFileManager/UIDocumentPicker).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Con AGP 9 las bibliotecas KMP usan este plugin (reemplaza a com.android.library).
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "mx.ipn.escom.gestor.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        // Habilita las pruebas unitarias de commonTest en la JVM del host (sin emulador).
        withHostTest { }
    }

    // iOS 15+: dispositivo y simulador Apple Silicon. El framework estático "ComposeApp"
    // lo enlaza el proyecto Xcode (script embedAndSignAppleFrameworkForXcode).
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.compose.backhandler)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            api(libs.androidx.activity.compose)
            implementation(libs.androidx.documentfile)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
