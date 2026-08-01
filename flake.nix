{
  description = "Development shell for Logseq OG";

  inputs = {
    nixpkgs.url = "https://channels.nixos.org/nixpkgs-unstable/nixexprs.tar.zst";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = {
    nixpkgs,
    rust-overlay,
    treefmt-nix,
    ...
  }: let
    overlays = [(import rust-overlay)];
    pkgs = import nixpkgs {
      system = "x86_64-linux";
      inherit overlays;
    };

    rustToolchain = pkgs.rust-bin.nightly.latest.default.override {
      extensions = ["rust-src" "rust-analyzer" "llvm-tools-preview"];
      targets = ["wasm32-unknown-unknown"];
    };

    treefmtEval = treefmt-nix.lib.evalModule pkgs ./nix/treefmt.nix;
  in {
    formatter.x86_64-linux = treefmtEval.config.build.wrapper;

    devShells.x86_64-linux.default = import ./nix/devShell.nix {
      inherit pkgs rustToolchain;
      treefmtWrapper = treefmtEval.config.build.wrapper;
    };
  };
}
