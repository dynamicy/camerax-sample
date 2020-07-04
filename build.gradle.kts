// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  val kotlinVersion by extra("1.3.72")
  val daggerVersion by extra("2.28.1-alpha")

  repositories {
    google()
    jcenter()
  }
  dependencies {
    classpath("com.android.tools.build:gradle:4.0.0")
    classpath(kotlin("gradle-plugin", version = kotlinVersion))
    classpath("com.google.dagger:hilt-android-gradle-plugin:$daggerVersion")
  }
}

allprojects {
  repositories {
    google()
    jcenter()
  }
}

task<Delete>("clean") {
  delete(rootProject.buildDir)
}