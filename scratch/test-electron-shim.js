const Module = require('module');
const originalLoad = Module._load;
const electron = {
  app: {
    getPath(name) {
      return name === 'home' ? process.cwd() : process.cwd();
    },
    getName() {
      return 'logseq-og-test';
    },
    getVersion() {
      return 'test';
    },
    isPackaged: false,
  },
};
Module._load = function (request, parent, isMain) {
  if (request === 'electron') return electron;
  return originalLoad.call(this, request, parent, isMain);
};
