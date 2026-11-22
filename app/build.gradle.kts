import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.fabric.ledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fabric.ledger"
        minSdk = 24
        targetSdk = 35
        // دقائق منذ 1970: يزداد مع كل بناء سواء محلياً أو على GitHub فلا يُرفض التحديث كـ «إصدار أقدم»
        versionCode = (System.currentTimeMillis() / 60000L).toInt()
        // يتغير تلقائياً مع كل بناء:
        //  - على GitHub: 1.<رقم التشغيل>  (نفس رقم ملف الـ APK والـ Release)
        //  - محلياً: 1.dev.<تاريخ.وقت البناء>  مثل 1.dev.261122.1530
        val run = System.getenv("GITHUB_RUN_NUMBER")
        versionName = if (run != null) "1.$run"
        else "1.dev." + SimpleDateFormat("yyMMdd.HHmm", Locale.US).format(Date())
    }

    // مفتاح توقيع ثابت داخل المشروع: كل البناءات (محلية أو GitHub) تُوقَّع بنفس المفتاح فيعمل التحديث فوق النسخة القديمة.
    // للنشر على المتجر أو لمشروع عام: غيّر الكلمات عبر متغيرات البيئة KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD أو استبدل الملف.
    signingConfigs {
        create("shared") {
            storeFile = file("fabric-ledger.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "fabricledger"
            keyAlias = System.getenv("KEY_ALIAS") ?: "fabric"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "fabricledger"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
