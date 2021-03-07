# Install Uranium

The project's artifacts are hosted on [Bintray] and available from JCenter.

It's also possible to use [JitPack] as an alternative (detailed instructions not provided).

[Bintray]: https://bintray.com/norswap/maven/uranium
[JitPack]: https://jitpack.io/#norswap/uranium

**Version:** If the version in this file is not current, don't forget to replace it by a recent
version!

## Using Gradle

With the Kotlin DSL (`build.gradle.kts`):

```kotlin
repositories {
    // ...
    jcenter()
}

dependencies {
    // ...
    implementation("norswap:uranium:1.0.8-ALPHA")
}
```

With the Groovy DSL (`build.gradle`):

```groovy
repositories {
    // ...
    jcenter()
}

dependencies {
    // ...
    implementation 'norswap:uranium:1.0.8-ALPHA'
}
```

## Using Maven

In `pom.xml`:

```xml
<project>
  ...
  <repositories>
    ...
    <repository>
      <id>jcenter</id>
      <url>https://jcenter.bintray.com</url>
    </repository>
  </repositories>
  <dependencies>
    ...
    <dependency>
      <groupId>norswap</groupId>
      <artifactId>uranium</artifactId>
      <version>1.0.8-ALPHA</version>
    </dependency>  
  </dependencies>
</project>
```