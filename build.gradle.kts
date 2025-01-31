import groovy.xml.XmlSlurper
import org.codehaus.groovy.runtime.ResourceGroovyMethods
import java.io.FileNotFoundException
import java.net.URI

plugins {
    `maven-publish`
    java
    id("org.ajoberstar.grgit") version("+")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:23.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")

    testImplementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("org.openjdk.jmh:jmh-generator-annprocess:1.33")
    testImplementation("org.openjdk.jmh:jmh-core:1.33")

    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.33")
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

tasks.withType(JavaCompile::class) {
    options.release = 16
}

sourceSets {
    main {
        java {
            srcDir("src/main/java")
        }
    }
    test {
        java {
            srcDir("src/test/java")
        }
    }
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.javadoc)
    archiveClassifier = "javadoc"
    from(tasks.javadoc.get().destinationDir)
}

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveClassifier = "sources"
    from(sourceSets.main.get().allSource)
}

tasks.withType(Test::class) {
    useJUnitPlatform()
    ignoreFailures = false
    failFast = false
}

tasks.jar {
    manifest {
        attributes(
            "XJS-Version" to archiveVersion,
        )
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("tag", "apiNote:a:API note")
}

artifacts {
    archives(tasks.jar)
    archives(javadocJar)
    archives(sourcesJar)
}

java {
    withJavadocJar()
    withSourcesJar()
}

val env: MutableMap<String, String> = System.getenv()

publishing {
    val mavenUrl = env["MAVEN_URL"]
    val mavenUsername = env["MAVEN_USERNAME"]
    val mavenPassword = env["MAVEN_PASSWORD"]

    //val release = mavenUrl?.contains("release")
    val snapshot = mavenUrl?.contains("snapshot")

    val publishingValid = rootProject == project && !mavenUrl.isNullOrEmpty() && !mavenUsername.isNullOrEmpty() && !mavenPassword.isNullOrEmpty()

    val publishVersion = project.version.toString()//makeModrinthVersion(mod_version)
    val snapshotPublishVersion = "$publishVersion-infinity-compat-SNAPSHOT" //publishVersion + if (snapshot == true) "-SNAPSHOT" else ""

    val publishGroup = project.group.toString()
    val artifact = rootProject.base.archivesName.get().lowercase()

    val hash = if (grgit.branch != null && grgit.branch.current() != null) grgit.branch.current().fullName else ""

    publications {
        var publish = true
        try {
            if (publishingValid) {
                try {
                    val xml = ResourceGroovyMethods.getText(
                        URI.create("$mavenUrl/${publishGroup.replace('.', '/')}/$snapshotPublishVersion/$publishVersion.pom").toURL()
                    )
                    val metadata = XmlSlurper().parseText(xml)

                    if (metadata.getProperty("hash").equals(hash)) {
                        publish = false
                    }
                } catch (ignored: FileNotFoundException) {
                    // No existing version was published, so we can publish
                }
            } else {
                publish = false
            }
        } catch (e: Exception) {
            publish = false
            println("Unable to publish to maven. The maven server may be offline.")
        }

        if (publish) {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

                artifact(javadocJar)

                pom {
                    groupId = publishGroup
                    artifactId = artifact
                    version = snapshotPublishVersion
                    withXml {
                        asNode().appendNode("properties").appendNode("hash", hash)
                    }
                }
            }
        }
    }
    repositories {

        if (publishingValid) {
            maven {
                url = uri(mavenUrl!!)

                credentials {
                    username = mavenUsername
                    password = mavenPassword
                }
            }
        } else {
            mavenLocal()
        }
    }
}