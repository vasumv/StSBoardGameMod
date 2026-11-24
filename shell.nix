# Dev shell for building the Slay the Spire board game mod (targets Java 8).
# Usage: nix-shell --run "mvn package"   (from the repo root)
{ pkgs ? (builtins.getFlake "nixpkgs").legacyPackages.${builtins.currentSystem} }:
pkgs.mkShell {
  packages = [
    pkgs.jdk8
    (pkgs.maven.override { jdk_headless = pkgs.jdk8; })
  ];
  JAVA_HOME = pkgs.jdk8.home;
}
