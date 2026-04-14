import com.android.build.api.dsl.ApkSigningConfig
import groovy.json.JsonSlurper

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}
fun MutableMap<Any?, Any?>.getString(key: String): String? {
  if (!containsKey(key)) {
    println("'$key' not set.")
    return null
  }
  val ret = get(key)
  if (ret !is String) {
    println("type of '$key' should be string.")
    return null
  }
  return ret
}

val appVersionCodeFromGit
  get(): Int? {
    return try {
      val cmd = "git rev-list HEAD --count"
      val process = ProcessBuilder(cmd.split(" ")).start()
      val result = String(process.inputStream.readAllBytes()).trim()
      result.toInt()
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

val appVersionNameFromGitBranch
  get():String? {
    val str = try {
      val cmd = "git symbolic-ref --short -q HEAD"
      val process = ProcessBuilder(cmd.split(" ")).start()
      String(process.inputStream.readAllBytes()).trim()
    } catch (e: Exception) {
      e.printStackTrace()
      return null
    }
    return """\d+.\d+.\d+""".toRegex().find(str)?.value
  }

val appVersionNameFromGitTag
  get():String? {
    val str = try {
      val cmd = "git describe --tags"
      val process = ProcessBuilder(cmd.split(" ")).start()
      String(process.inputStream.readAllBytes()).trim()
    } catch (e: Exception) {
      e.printStackTrace()
      return null
    }
    return """\d+.\d+.\d+""".toRegex().find(str)?.value
  }

val appVersionCode
  get():Int {
    val ret = appVersionCodeFromGit ?: 9999
    println("versionCode: $ret")
    return ret
  }

val appVersionName
  get():String {
    val ret = appVersionNameFromGitBranch ?: appVersionNameFromGitTag ?: "0.0.1"
    println("versionName: $ret")
    return ret
  }

data class KeystoreConfig(
  val storeFile: String, val storePassword: String, val keyAlias: String, val keyPassword: String
)

fun readKeystoreConfig(path: String): KeystoreConfig? {
  println("[START] readKeystoreConfig( path = $path )")
  val ret = run {
    val method = "readKeystoreProps"
    val file = File(path)
    if (!file.exists()) {
      println("[$method] file not existed.")
      return@run null
    }
    val config = try {
      val jEle = JsonSlurper().parseText(file.readText())
      if (jEle !is Map<*, *>) return@run null
      jEle.toMutableMap()
    } catch (e: Exception) {
      println("[$method] parse json failed!")
      e.printStackTrace()
      return@run null
    }
    val storeFile = config.getString("storeFile") ?: return@run null
    val storePassword = config.getString("storePassword") ?: return@run null
    val keyAlias = config.getString("keyAlias") ?: return@run null
    val keyPassword = config.getString("keyPassword") ?: return@run null
    val keyFile = File(storeFile)
    if (!keyFile.exists()) {
      println("[$method] $storeFile is not exists!")
      return@run null
    }
    return@run KeystoreConfig(storeFile, storePassword, keyAlias, keyPassword)
  }
  println("[END] readKeystoreConfig( path = $path )")
  return ret
}

fun ApkSigningConfig.handleKeystoreConfig() {
  val config = readKeystoreConfig("${rootProject.projectDir.path}/keystore.$name.json")
  if (config == null) {
    println("$name keystore config is not set. check file 'keystore.$name.json'")
    return
  }
  storeFile = file(config.storeFile)
  storePassword = config.storePassword
  keyAlias = config.keyAlias
  keyPassword = config.keyPassword
//  println("${this.name} keystore properties:")
//  println("   storeFile: $storeFile")
//  println("   storePassword: $storePassword")
//  println("   keyAlias: $keyAlias")
//  println("   keyPassword: $keyPassword")
}

android {
  namespace = "ink.gim.lfw"
  compileSdk = 36

  signingConfigs {
    getByName("debug") { handleKeystoreConfig() }
    create("release") { handleKeystoreConfig() }
  }

  defaultConfig {
    applicationId = "ink.gim.lfw"
    minSdk = 23
    targetSdk = 35
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  applicationVariants.all {
    outputs
      .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
      .forEach { output ->
        // 自定义输出文件名
        val newName = "lfw.${buildType.name}.v$versionName.$versionCode.apk"
        output.outputFileName = newName
      }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
  }
}

dependencies {

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}