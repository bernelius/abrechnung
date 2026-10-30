{
  description = "Abrechnung — Terminal-based invoicing and reporting tool";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      inherit (nixpkgs) lib;
      version = lib.trim (builtins.readFile ./VERSION);
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = f: lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      packages = forAllSystems (
        pkgs:
        let
          jdk = pkgs.javaPackages.compiler.temurin-bin.jdk-25;
          gradle = pkgs.gradle_9;
        in
        {
          abrechnung = pkgs.stdenv.mkDerivation (finalAttrs: {
            pname = "abrechnung";
            inherit version;
            src = ./.;

            nativeBuildInputs = [
              gradle
              pkgs.makeWrapper
            ];

            buildInputs = [
              jdk
              pkgs.libpulseaudio
              pkgs.alsa-lib
              pkgs.stdenv.cc.cc.lib
            ];

            mitmCache = gradle.fetchDeps {
              pkg = finalAttrs.finalPackage;
              data = ./deps.json;
            };

            __darwinAllowLocalNetworking = true;

            JAVA_HOME = "${jdk}";

            gradleFlags = [
              "-Dfile.encoding=utf-8"
              "-Dorg.gradle.java.home=${jdk}"
              "-Dorg.gradle.configuration-cache=false"
            ];

            gradleBuildTask = "installDist";
            dontUseGradleCheck = true;

            installPhase = ''
              mkdir -p $out
              cp -r app/build/install/app/* $out/

              # Rename the startup script to match the package name
              mv $out/bin/app $out/bin/abrechnung

              # Patch the script to add required JVM flags and library path.
              # The Gradle-generated script uses DEFAULT_JVM_OPTS for JVM flags.
              sed -i \
                -e 's|DEFAULT_JVM_OPTS=\"|DEFAULT_JVM_OPTS=\"--enable-native-access=ALL-UNNAMED |' \
                -e "s|DEFAULT_JVM_OPTS='|DEFAULT_JVM_OPTS='--enable-native-access=ALL-UNNAMED |" \
                $out/bin/abrechnung

              wrapProgram $out/bin/abrechnung \
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
        }
      );

    };
}
