# Third-party notices

The Gradle Wrapper scripts and `gradle/wrapper/gradle-wrapper.jar` originate from
[Gradle 8.8](https://github.com/gradle/gradle/tree/v8.8.0).
Their Apache License 2.0 notices are retained in the scripts and
inside the JAR (`META-INF/LICENSE`); the full upstream license is also included
at `gradle/wrapper/LICENSE`. The wrapper downloads Gradle 8.8 using the
pinned distribution SHA-256 checksum in `gradle-wrapper.properties`.

Fabric Loader, Fabric API, Yarn and Minecraft are resolved as build/runtime
dependencies, not copied into the source tree. Their respective upstream
licenses and Minecraft's EULA apply separately. The agent renderer references
the game's bundled default skin without redistributing that texture.
