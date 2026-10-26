{
  description = "Abrechnung — Terminal-based invoicing and reporting tool";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      inherit (nixpkgs) lib;
      version = lib.trim (builtins.readFile ./VERSION);
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f: lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      packages = forAllSystems (pkgs:
        let
          jdk = pkgs.javaPackages.compiler.temurin-bin.jdk-25;

          # Gradle 9.3.0 pinned by the project's wrapper, fetched deterministically.
          gradle930 = pkgs.fetchzip {
            url = "https://services.gradle.org/distributions/gradle-9.3.0-bin.zip";
            hash = "sha256-NR2yq3V/BBm4n295eSrlY5eZ9eYpCkrqIsTqHOoJvSw=";
          };

          gradleCmd = "${gradle930}/bin/gradle";

          # Fixed-output derivation that prefetches all Gradle dependencies.
          # The hash must be updated whenever build.gradle.kts or gradle/libs.versions.toml changes.
          gradleDeps = pkgs.stdenvNoCC.mkDerivation {
            pname = "abrechnung-gradle-deps";
            inherit version;
            src = ./.;

            nativeBuildInputs = [ jdk pkgs.cacert ];

            buildPhase = ''
              export HOME=$(mktemp -d)
              export GRADLE_USER_HOME=$HOME/.gradle
              export JAVA_HOME=${jdk}

              ${gradleCmd} --no-daemon :app:dependencies -Pversion=${version} > /dev/null 2>&1 || true
              ${gradleCmd} --no-daemon :app:shadowJar -x test -Pversion=${version} > /dev/null 2>&1 || true
            '';

            installPhase = ''
              mkdir -p $out/caches $out/wrapper
              cp -r $GRADLE_USER_HOME/caches/modules-2 $out/caches/ 2>/dev/null || true
              cp -r $GRADLE_USER_HOME/caches/transforms-2 $out/caches/ 2>/dev/null || true
              cp -r $GRADLE_USER_HOME/caches/transforms-3 $out/caches/ 2>/dev/null || true
              cp -r $GRADLE_USER_HOME/caches/jars-9 $out/caches/ 2>/dev/null || true
              cp -r $GRADLE_USER_HOME/wrapper/dists $out/wrapper/ 2>/dev/null || true

              # Normalize timestamps for reproducible hashing.
              find $out -type f -exec touch -d '1970-01-01T00:00:00Z' {} +
              find $out -type d -exec touch -d '1970-01-01T00:00:00Z' {} +
              find $out -name '*.lock' -delete
              find $out -name 'gc.properties' -delete
            '';

            outputHashAlgo = "sha256";
            outputHashMode = "recursive";
            outputHash = "sha256-2LeqiMUIRTLRodCUrjKuMe4yoQ5uCsDDHyZ7C7FfEvU=";
          };

          abrechnung = pkgs.stdenv.mkDerivation {
            pname = "abrechnung";
            inherit version;
            src = ./.;

            nativeBuildInputs = [ pkgs.makeWrapper ];
            buildInputs = [ jdk pkgs.libpulseaudio pkgs.alsa-lib ];

            buildPhase = ''
              export HOME=$(mktemp -d)
              export GRADLE_USER_HOME=$HOME/.gradle
              export JAVA_HOME=${jdk}

              # Restore cached dependencies and make them writable.
              mkdir -p $GRADLE_USER_HOME/caches
              cp -r ${gradleDeps}/caches/* $GRADLE_USER_HOME/caches/ 2>/dev/null || true
              chmod -R +w $GRADLE_USER_HOME/caches

              ${gradleCmd} --offline --no-daemon :app:shadowJar -x test -Pversion=${version}
            '';

            installPhase = ''
              mkdir -p $out/share/java
              cp app/build/libs/abrechnung-all.jar $out/share/java/

              makeWrapper ${jdk}/bin/java $out/bin/abrechnung \
                --add-flags "--enable-native-access=ALL-UNNAMED" \
                --add-flags "--sun-misc-unsafe-memory-access=allow" \
                --add-flags "-jar $out/share/java/abrechnung-all.jar" \
                --prefix LD_LIBRARY_PATH : ${lib.makeLibraryPath [ pkgs.libpulseaudio pkgs.alsa-lib ]}
            '';

            meta = with lib; {
              description = "Terminal-based invoicing and reporting tool";
              license = licenses.mit;
              platforms = platforms.linux;
              mainProgram = "abrechnung";
            };
          };
        in
        {
          default = abrechnung;
          abrechnung = abrechnung;
        }
      );

      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShell {
          packages = with pkgs; [
            javaPackages.compiler.temurin-bin.jdk-25
          ];
        };
      });
    };
}
