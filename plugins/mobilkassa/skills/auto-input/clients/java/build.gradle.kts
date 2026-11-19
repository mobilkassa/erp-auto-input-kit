plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.15.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.release.set(21)
    options.encoding = "UTF-8"
}

// The scenario tests run against the mock kassa: MOCK_URL=http://127.0.0.1:6461 (default).
tasks.test {
    useJUnitPlatform()
    environment("MOCK_URL", System.getenv("MOCK_URL") ?: "http://127.0.0.1:6461")
}
