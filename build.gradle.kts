import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  kotlin("jvm") version "1.2.71"
}

group = "codes.craftsman"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val test by tasks.getting(Test::class) {
  useJUnitPlatform { }
}

dependencies {
  compile(kotlin("stdlib-jdk8"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
  testCompile("io.kotlintest:kotlintest-runner-junit5:3.1.10")
  testImplementation("io.mockk:mockk:1.8.13")
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}