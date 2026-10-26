{
  description = "Abrechnung — Terminal-based invoicing and reporting tool";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { nixpkgs, ... }:
    let
      inherit (nixpkgs) lib;
      version = lib.trim (builtins.readFile ./VERSION);
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = f: lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});

      # Gradle version pinned by the project's wrapper. The nixpkgs Gradle used
      # for the build must match it (asserted below).
      wrapperGradleVersion =
        let
          line = lib.findFirst (lib.hasInfix "distributionUrl=") null (
            lib.splitString "\n" (builtins.readFile ./gradle/wrapper/gradle-wrapper.properties)
          );
          match = builtins.match ".*gradle-([0-9]+\\.[0-9]+\\.[0-9]+)-bin\\.zip" line;
        in
        if match == null then null else builtins.head match;
    in
    {
      packages = forAllSystems (
        pkgs:
        let
          jdk = pkgs.javaPackages.compiler.temurin-bin.jdk-25;

          # Wrapped Gradle, pinned to the same version as the project's wrapper
          # (gradle/wrapper/gradle-wrapper.properties), so nixpkgs updates can't
          # silently change the build tool. The wrapper's setup hook manages
          # GRADLE_USER_HOME, --no-daemon, and a reproducible-archives init
          # script; mitm-cache provides reproducible dependency fetching.
          # When bumping Gradle, update the wrapper too and prefetch the new
          # distribution hash with:
          #   nix store prefetch-file https://services.gradle.org/distributions/gradle-X.Y.Z-bin.zip
          gradle =
            (pkgs.gradle-packages.mkGradle {
              version = "9.8.0";
              hash = "sha256-uv1c6c+uoPvM/chDmhrEL71M2cidyamIIo2KJjmljmw=";
              defaultJava = jdk;
            }).wrapped;

          abrechnung = pkgs.stdenvNoCC.mkDerivation (finalAttrs: {
            pname = "abrechnung";
            inherit version;
            src = ./.;

            nativeBuildInputs = [
              gradle
              pkgs.makeWrapper
            ];

            # Gradle dependencies are locked in deps.json (URL -> hash) and served
            # to the build through a local MITM proxy by the Gradle setup hook.
            # Regenerate deps.json after dependency, plugin, or Gradle version
            # changes with (run from the repository root):
            #   nix build .#abrechnung.mitmCache.updateScript && ./result
            mitmCache = gradle.fetchDeps {
              pkg = finalAttrs.finalPackage;
              data = ./deps.json;
            };

            gradleBuildTask = ":app:shadowJar";
            # Task used by mitmCache.updateScript to fetch all dependencies
            # (nixpkgs' default nixDownloadDeps Groovy task is broken on Gradle 9).
            gradleUpdateTask = "nixDownloadAllDeps";
            # Resolving all configurations captures Project state that the
            # configuration cache cannot serialize (and Kover accesses
            # Task.project at execution time). Disable the cache for the
            # dependency update run only.
            preGradleUpdate = ''
              gradleFlagsArray+=("-Dorg.gradle.configuration-cache=false")
            '';
            gradleFlags = [
              "-Pversion=${version}"
              # Toolchains must come from the JDK running Gradle, never from a
              # (proxied) foojay download.
              "-Dorg.gradle.java.installations.auto-download=false"
            ];

            installPhase = ''
              mkdir -p $out/share/java
              cp app/build/libs/abrechnung-all.jar $out/share/java/

              makeWrapper ${jdk}/bin/java $out/bin/abrechnung \
                --add-flags "--enable-native-access=ALL-UNNAMED" \
                --add-flags "--sun-misc-unsafe-memory-access=allow" \
                --add-flags "-jar $out/share/java/abrechnung-all.jar" \
                --prefix LD_LIBRARY_PATH : ${
                  lib.makeLibraryPath [
                    pkgs.libpulseaudio
                    pkgs.alsa-lib
                    pkgs.stdenv.cc.cc.lib
                  ]
                }
            '';

            meta = with lib; {
              description = "Terminal-based invoicing and reporting tool";
              license = licenses.mit;
              platforms = platforms.linux;
              mainProgram = "abrechnung";
            };
          });
        in
        assert lib.assertMsg (gradle.version == wrapperGradleVersion) ''
          flake.nix: pinned Gradle ${gradle.version} does not match gradle-wrapper.properties (${wrapperGradleVersion}).
          Keep the two in sync: update the mkGradle pin above (prefetch the new zip hash) or fix the wrapper.'';
        {
          default = abrechnung;
          inherit abrechnung;
        }
      );
    };
}
