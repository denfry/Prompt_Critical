subprojects {
    repositories {
        mavenCentral()
    }

    group = providers.gradleProperty("mod_group_id").get()
    version = "${providers.gradleProperty("mod_version").get()}+${rootProject.libs.versions.minecraft.get()}"

    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(21)
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
    }
}
