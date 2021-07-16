plugins {
    id("org.jetbrains.kotlin.jvm").version("1.5.21")
    id("com.apollographql.apollo3").version("3.0.0-alpha01")
}

repositories {
    mavenCentral()
}


dependencies {
    implementation("com.apollographql.apollo3:apollo-runtime:3.0.0-alpha01")
    implementation("com.apollographql.apollo3:apollo-ast:3.0.0-alpha01")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

apollo {
    introspection {
        endpointUrl.set("https://api.github.com/graphql")
        headers.put("Authorization", provider {
            "bearer ${file("github_token").readText().trim()} "
        })
        schemaFile.set(file("src/main/graphql/schema.graphqls"))
    }

    codegenModels.set("operationBased")
}