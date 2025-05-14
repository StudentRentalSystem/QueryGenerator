plugins {
    id("java")
    id("maven-publish")
}

group = "io.github.studentrentalsystem"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/StudentRentalSystem/LLMDataParser")
        credentials {
            username = System.getenv("USERNAME")
            password = System.getenv("TOKEN")
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("io.github.studentrentalsystem:llmdataparser:1.0.1")
    implementation("org.json:json:20231013")
}

tasks.test {
    useJUnitPlatform()
}

publishing {

    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])
            groupId = "io.github.studentrentalsystem"
            artifactId = "querygenerator"
            version = "1.0.0"
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/StudentRentalSystem/QueryGenerator")
            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("TOKEN")
            }
        }
    }
}

