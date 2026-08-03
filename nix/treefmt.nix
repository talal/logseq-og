{pkgs, ...}: {
  projectRootFile = "flake.nix";

  settings.global.excludes = [
    "tldraw/**"
    "deps/**"
    # Auto-generated
    "**/yarn.lock"
    ".agents/**"
    "scratch/**"
    # Release
    "**/node_modules/**"
    "node_modules/**"
    "public/**"
    "target/**"
    # Tests
    "e2e-dump/**"
    "test-results/**"
    # Static assets
    "LICENSE**"
    "src/resources/**"
    "static/**"
    "tmp/**"
  ];

  programs = {
    alejandra.enable = true;
    deadnix.enable = true;
    just.enable = true;
    statix.enable = true;
  };

  programs.dprint = {
    enable = true;
    settings = {
      plugins = pkgs.dprint-plugins.getPluginList (
        plugins:
          with plugins; [
            dprint-plugin-markdown
            dprint-plugin-toml
          ]
      );
      markdown.textWrap = "always";
    };
    includes = ["*.md" "*.toml"];
  };

  programs.typos = {
    enable = true;
    hidden = true;
    includes = ["*.md"];
  };

  settings.formatter = {
    # Priority: deadnix -> statix -> Alejandra
    deadnix.priority = 1;
    statix.priority = 2;
    alejandra.priority = 3;
  };
}
