/** @type {import('stylelint').Config} */
export default {
  extends: ['stylelint-config-standard'],
  ignoreFiles: ['src/test/docs*/logseq/custom.css'],
  rules: {
    'at-rule-no-unknown': [
      true,
      {
        ignoreAtRules: [
          'tailwind',
          'apply',
          'variants',
          'responsive',
          'screen',
          'layer',
        ],
      },
    ],
    'no-descending-specificity': null,
    'block-no-empty': null,
    'selector-class-pattern': null,
    'property-no-vendor-prefix': null,
    'keyframes-name-pattern': null,
    'selector-id-pattern': null,
    'declaration-property-value-keyword-no-deprecated': null,
    'declaration-property-value-no-unknown': null,
  },
}
