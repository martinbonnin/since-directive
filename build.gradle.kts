plugins {
    id("org.jetbrains.kotlin.jvm").version("1.5.21")
    id("com.apollographql.apollo3").version("3.0.0-alpha01")
}

repositories {
    mavenCentral()
}


dependencies {
    implementation("com.apollographql.apollo3:apollo-runtime:3.0.0-alpha01")
}