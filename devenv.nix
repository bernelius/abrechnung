{ pkgs, ... }:

{
  languages.java = {
    enable = true;
    jdk.package = pkgs.javaPackages.compiler.temurin-bin.jdk-25;
  };
  packages = with pkgs; [
    ## for native build
    zlib
    ## runtime libraries for LWJGL audio in native image
    libpulseaudio
    alsa-lib
  ];

  enterShell = ''
    export NIX_LDFLAGS+=" -L${pkgs.zlib}/lib"
    export NATIVE_IMAGE_ZLIB_PATH="${pkgs.zlib}/lib"
    export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath [
      pkgs.libpulseaudio
      pkgs.alsa-lib
      pkgs.stdenv.cc.cc.lib
    ]}"
  '';
}
