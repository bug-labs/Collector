plugins {
    id("java-library")
    id("io.freefair.lombok") version "9.8.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.groupez.dev/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Provided at runtime through the 'libraries' block of plugin.yml.
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    // Optional integrations, all soft dependencies at runtime.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    compileOnly("com.github.brcdev-minecraft:shopgui-api:3.2.0") { isTransitive = false }
    compileOnly("com.github.Gypopo:EconomyShopGUI-API:1.11.0") { isTransitive = false }
    compileOnly("fr.maxlego08.itemstacker:zitemstacker-api:2.1.0") { isTransitive = false }
    // ExcellentEconomy is reached by reflection instead of a compile dependency: it ships Java 25
    // bytecode, which a Java 21 toolchain cannot read, and pulling it in would force everyone
    // building this plugin onto a newer JDK for the sake of one optional hook.
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

