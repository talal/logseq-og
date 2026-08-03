{
  pkgs,
  rustToolchain,
  treefmtWrapper,
}: let
  linuxLibraries = with pkgs; [
    alsa-lib
    at-spi2-atk
    at-spi2-core
    atk
    cairo
    cups
    dbus
    expat
    gdk-pixbuf
    glib
    gtk3
    libdrm
    libgbm
    libGL
    librsvg
    libsoup_3
    libx11
    libxcb
    libxcomposite
    libxcursor
    libxdamage
    libxext
    libxfixes
    libxi
    libxkbcommon
    libxrandr
    libxrender
    libxtst
    mesa
    nspr
    nss
    pango
    systemd
    webkitgtk_4_1
  ];
in
  pkgs.mkShell {
    packages = with pkgs;
      [
        # Clojure
        babashka
        clojure
        clojure-lsp
        jdk

        # Node
        nodejs_22
        playwright-driver.browsers
        typescript-language-server
        yarn

        # Rust + Wasm
        rustToolchain
        lld
        cargo-fuzz
        wasm-pack

        # Tools
        alejandra
        just
        parinfer-rust # used by clojure-mcp for faster delimiter repair
        python314 # for scripting
        ripgrep # for better `grep` and `glob_files` performance
        treefmtWrapper
      ]
      ++ linuxLibraries;

    shellHook = ''
      export LD_LIBRARY_PATH=${pkgs.lib.makeLibraryPath linuxLibraries}:$LD_LIBRARY_PATH
      export XDG_DATA_DIRS=${pkgs.gsettings-desktop-schemas}/share/gsettings-schemas/${pkgs.gsettings-desktop-schemas.name}:${pkgs.gtk3}/share/gsettings-schemas/${pkgs.gtk3.name}:$XDG_DATA_DIRS

      # Add the local Node executable directory to PATH
      export PATH="$PWD/node_modules/.bin:$PATH"

      # Skip checking whether the OS is a supported Ubuntu/Debian version
      export PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS=true
      # Point Playwright to the Nix-provided browsers
      export PLAYWRIGHT_BROWSERS_PATH=${pkgs.playwright-driver.browsers}
    '';
  }
