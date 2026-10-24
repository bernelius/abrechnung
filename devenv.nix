{
  pkgs,
  ...
}:

let
  libraries = [
    pkgs.libpulseaudio
    pkgs.stdenv.cc.cc.lib
  ];
in
{
  languages.java = {
    enable = true;
    jdk.package = pkgs.javaPackages.compiler.temurin-bin.jdk-26;
  };

  packages = libraries;

  enterShell = ''
    export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath libraries}''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
  '';
}
