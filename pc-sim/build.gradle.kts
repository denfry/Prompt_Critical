plugins {
    `java-library`
    alias(libs.plugins.jmh)
}

dependencies {
    // НИКАКИХ runtime-зависимостей (05 §5.1.2). Только тесты.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jqwik)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform { includeEngines("junit-jupiter", "jqwik", "archunit") }
}
