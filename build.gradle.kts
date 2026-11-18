buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        constraints {
            add("classpath", "com.google.guava:guava:33.8.0-jre")
            add("classpath", "org.apache.commons:commons-lang3:3.22.0")
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.7")
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.87")
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.87")
            add("classpath", "org.bouncycastle:bcutil-jdk18on:1.87")
            add("classpath", "org.jdom:jdom2:2.0.6.1")
        }
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode = LockMode.STRICT
    }
}
