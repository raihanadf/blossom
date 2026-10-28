{
  description = "blossom minecraft mod dev shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "aarch64-darwin" "x86_64-darwin" "x86_64-linux" "aarch64-linux" ];
      forAll = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      # minecraft 26.x needs java 25
      devShells = forAll (pkgs: {
        default = pkgs.mkShell {
          packages = [ pkgs.zulu25 pkgs.python3 ];
          JAVA_HOME = pkgs.zulu25.home;
        };
      });
    };
}
