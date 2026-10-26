{ pkgs, ... }:

{
  languages.java = {
    enable = true;
    jdk.package = pkgs.javaPackages.compiler.temurin-bin.jdk-25;
  };
}
