const fs = require('fs')
const utils = require('util')
const cp = require('child_process')
const exec = utils.promisify(cp.exec)
const path = require('path')
const gulp = require('gulp')
const cleanCSS = require('gulp-clean-css')
const del = require('del')

const outputPath = path.join(__dirname, 'static')
const resourcesPath = path.join(__dirname, 'resources')
const resourceFilePath = path.join(resourcesPath, '**')

const css = {
  watchCSS() {
    return cp.spawn(`yarn css:watch`, {
      shell: true,
      stdio: 'inherit',
    })
  },

  buildCSS(...params) {
    return gulp.series(
      () => exec(`yarn css:build`, {}),
      css._optimizeCSSForRelease
    )(...params)
  },

  _optimizeCSSForRelease() {
    return gulp
      .src(path.join(outputPath, 'css', 'style.css'))
      .pipe(cleanCSS())
      .pipe(gulp.dest(path.join(outputPath, 'css')))
  },
}

const common = {
  clean() {
    return del([
      './static/**/*',
      '!./static/yarn.lock',
      '!./static/node_modules',
    ])
  },

  syncResourceFile() {
    return gulp.src(resourceFilePath).pipe(gulp.dest(outputPath))
  },

  // NOTE: All assets from node_modules are copied to the output directory
  syncAssetFiles(...params) {
    return gulp.series(
      () =>
        gulp
          .src([
            'node_modules/katex/dist/katex.min.js',
            'node_modules/katex/dist/contrib/mhchem.min.js',
            'node_modules/html2canvas/dist/html2canvas.min.js',
            'node_modules/interactjs/dist/interact.min.js',
            'node_modules/photoswipe/dist/umd/*.js',
            'node_modules/reveal.js/dist/reveal.js',
            'node_modules/marked/marked.min.js',
            'node_modules/@highlightjs/cdn-assets/highlight.min.js',
            'node_modules/@isomorphic-git/lightning-fs/dist/lightning-fs.min.js',
          ])
          .pipe(gulp.dest(path.join(outputPath, 'js'))),
      () =>
        gulp
          .src([
            'node_modules/pdfjs-dist/legacy/build/pdf.mjs',
            'node_modules/pdfjs-dist/legacy/build/pdf.worker.mjs',
            'node_modules/pdfjs-dist/legacy/web/pdf_viewer.mjs',
          ])
          .pipe(gulp.dest(path.join(outputPath, 'js', 'pdfjs'))),
      () =>
        gulp
          .src(['node_modules/pdfjs-dist/cmaps/*.*'])
          .pipe(gulp.dest(path.join(outputPath, 'js', 'pdfjs', 'cmaps'))),
      () =>
        gulp
          .src([
            'node_modules/@tabler/icons/iconfont/tabler-icons.min.css',
            'node_modules/inter-ui/inter.css',
            'node_modules/reveal.js/dist/theme/fonts/source-sans-pro/**',
          ])
          .pipe(gulp.dest(path.join(outputPath, 'css'))),
      () =>
        gulp
          .src('node_modules/inter-ui/Inter (web)/*.*')
          .pipe(gulp.dest(path.join(outputPath, 'css', 'Inter (web)'))),
      () =>
        gulp
          .src([
            'node_modules/@tabler/icons/iconfont/fonts/**',
            'node_modules/katex/dist/fonts/*.woff2',
          ])
          .pipe(gulp.dest(path.join(outputPath, 'css', 'fonts')))
    )(...params)
  },

  keepSyncResourceFile() {
    return gulp.watch(
      resourceFilePath,
      { ignoreInitial: true },
      common.syncResourceFile
    )
  },
}

exports.electron = () => {
  if (!fs.existsSync(path.join(outputPath, 'node_modules'))) {
    cp.execSync('yarn', {
      cwd: outputPath,
      stdio: 'inherit',
    })
  }

  cp.execSync('yarn electron:dev', {
    cwd: outputPath,
    stdio: 'inherit',
  })
}

exports.electronMaker = async () => {
  const pkgPath = path.join(outputPath, 'package.json')
  const pkg = require(pkgPath)
  const version = fs
    .readFileSync(path.join(__dirname, 'src/main/frontend/version.cljs'))
    .toString()
    .match(/[0-9.]{3,}/)[0]

  if (!version) {
    throw new Error('release version error in src/**/*/version.cljs')
  }

  pkg.version = version
  fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2))

  if (!fs.existsSync(path.join(outputPath, 'node_modules'))) {
    cp.execSync('yarn', {
      cwd: outputPath,
      stdio: 'inherit',
    })
  }

  cp.execSync('yarn electron:make', {
    cwd: outputPath,
    stdio: 'inherit',
  })
}

exports.clean = common.clean
exports.watch = gulp.series(
  common.syncResourceFile,
  common.syncAssetFiles,
  gulp.parallel(common.keepSyncResourceFile, css.watchCSS)
)
exports.build = gulp.series(
  common.clean,
  common.syncResourceFile,
  common.syncAssetFiles,
  css.buildCSS
)
