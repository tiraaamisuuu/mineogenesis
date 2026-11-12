{ pkgs ? import <nixpkgs> { } }:

let
  # LWJGL loads these by name at runtime; adding a JDK alone is insufficient.
  clientLibraries = with pkgs; [
    libglvnd
    libx11
    libxcursor
    libxrandr
    libxi
    libxinerama
    libxxf86vm
    openal
    alsa-lib
    udev
  ];
in
pkgs.mkShell {
  packages = [ pkgs.jdk21 ];
  JAVA_HOME = "${pkgs.jdk21}";

  shellHook = ''
    export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath clientLibraries}:/run/opengl-driver/lib''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
  '';
}
