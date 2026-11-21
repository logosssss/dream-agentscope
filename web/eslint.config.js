import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'

/**
 * 前端 lint 基线：只拦"确定是错"的（未定义变量、Vue 必需规则），不做格式化。
 * 格式化/风格统一另开任务，避免第一次引入就产生全仓 diff。
 */
export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', 'coverage/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        // .vue 里 <script lang="ts"> 交给 typescript-eslint 解析
        parser: tseslint.parser,
      },
    },
  },
  {
    languageOptions: {
      globals: { ...globals.browser },
    },
    rules: {
      // 路由页按功能命名（LandingView 等），不强制多词
      'vue/multi-word-component-names': 'off',
    },
  },
)
