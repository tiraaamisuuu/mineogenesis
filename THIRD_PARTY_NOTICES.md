# Third-party notices

The Gradle Wrapper scripts and `gradle/wrapper/gradle-wrapper.jar` originate from
[FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod/tree/1.21)
and Gradle. Their Apache License 2.0 notices are retained in the scripts and
inside the JAR (`META-INF/LICENSE`). The wrapper downloads Gradle 8.8 using the
pinned distribution SHA-256 checksum in `gradle-wrapper.properties`.

Fabric Loader, Fabric API, Yarn and Minecraft are resolved as build/runtime
dependencies, not copied into the source tree. Their respective upstream
licenses and Minecraft's EULA apply separately. The agent renderer references
the game's bundled default skin without redistributing that texture.
