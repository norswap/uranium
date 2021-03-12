# Install Uranium

The project's artifacts are hosted on [Maven Central], and on a public [Artifactory repository].

The only difference is that new releases will land on the artifactory repository a few hours
earlier.

[Maven Central]: https://search.maven.org/artifact/com.norswap/uranium/
[Artifactory repository]: https://norswap.jfrog.io/artifactory/maven/

**Version:** If the version in this file is not current, don't forget to replace it by a recent
version!

## Using Gradle

With the Kotlin DSL (`build.gradle.kts`):

```kotlin
repositories {
    // ...
    mavenCentral()
    // and/or:
    maven {
        url = uri("https://norswap.jfrog.io/artifactory/maven")
    }
}

dependencies {
    // ...
    implementation("com.norswap:uranium:1.0.8-ALPHA")
}
```

With the Groovy DSL (`build.gradle`):

```groovy
repositories {
    // ...
    mavenCentral()
    // and/or:
    maven {
        url 'https://norswap.jfrog.io/artifactory/maven'
    }
}

dependencies {
    // ...
    implementation 'com.norswap:uranium:1.0.8-ALPHA'
}
```

## Using Maven

In `pom.xml`:

```xml
<project>
  ...
  <repositories>
    ...
    <!-- no repository declaration needed for using Maven Central -->
    <repository>
        <id>artifactory-norswap</id>
        <url>https://norswap.jfrog.io/artifactory/maven</url>
    </repository>
  </repositories>
  <dependencies>
    ...
    <dependency>
      <groupId>com.norswap</groupId>
      <artifactId>uranium</artifactId>
      <version>1.0.8-ALPHA</version>
    </dependency>  
  </dependencies>
</project>
```