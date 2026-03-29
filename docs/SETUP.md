# Setup Notes

## Java

This project requires Java 17 or newer.
Recommended:
- `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`

Verify:

```powershell
java -version
echo $env:JAVA_HOME
```

## Android SDK

Ensure `local.properties` contains:

```properties
sdk.dir=C\:\\Users\\tarca\\AppData\\Local\\Android\\Sdk
```

## Common Build Issues

- `SDK location not found`:
  - create/fix `local.properties`
- `D8 OutOfMemoryError`:
  - keep `org.gradle.jvmargs=-Xmx4096m` in `gradle.properties`
- `Compose compiler plugin is required`:
  - ensure `org.jetbrains.kotlin.plugin.compose` is applied in Gradle scripts
