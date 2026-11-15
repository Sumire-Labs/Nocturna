<img src="modern/src/main/resources/icon.png" width="128">

# Embeddium Continuous

A LGPL-3.0 FOSS renderer for **Minecraft 1.20.1 / Forge / Java 17**.
This Sumire Labs fork now uses Celeritas common and its Forge 1.20.1 platform implementation,
while retaining the supported Embeddium public APIs and local fixes. See [source provenance](UPSTREAM.md)
and the [migration report](docs/celeritas-migration-2026-11-16.md).

Only Minecraft 1.20.1 is configured or supported. This is not an official Celeritas or Sodium release.
Report issues to [this repository](https://github.com/Sumire-Labs/Embeddium-1.20.1-Continuous/issues).

## Building and checking

Run Gradle with JDK 25. Product sources, unit tests and game runs use the JDK 17 toolchain;
Gradle can download that toolchain if it is not installed.

```shell
./gradlew build
./gradlew :modern:1.20.1-forge:runAuditClient
./gradlew :modern:1.20.1-forge:runValidationClient
./gradlew -PcompatRuntime :modern:1.20.1-forge:runValidationClient
```

The last command loads the pinned CCL, Immersive Engineering and BrandonsCore test versions.
The validation mod creates a fresh superflat world in the development run directory and exits automatically.
It is a separate source set and is never packaged in the release jar. A graphical OpenGL environment is required.
Normal development play uses `./gradlew runClient` and does not load this test mod.

Distributable jars appear under `build/libs/<version>/`. Shader support is already bundled;
do not install a separate Oculus/Iris jar. Rendering addons using removed private renderer internals
need their own Celeritas port; compatibility adapters cover the published APIs described in the migration report.

To verify the imported source, check out the pinned upstream revision and run
`node scripts/verify-celeritas-base.mjs /path/to/celeritas`.

## Credits

* JellySquid & the CaffeineMC team, for making Sodium in the first place, without which this project would not be possible
* Asek3, for the initial port to Forge
* XFactHD, for providing a list of gamebreaking Rubidium issues to start this work off, and for testing early builds
* Pepper, for their invaluable assistance with getting Forge lighting to work on Sodium 0.5
* @CelestialAbyss and @input-Here for making the new logo design

## License

Embeddium is licensed under the Lesser GNU General Public License version 3.

Portions of the option screen code are based on Reese's Sodium Options by FlashyReese, and are used under the terms of
the [MIT license](https://opensource.org/license/mit), located in `modern/src/main/resources/licenses/rso.txt`.
