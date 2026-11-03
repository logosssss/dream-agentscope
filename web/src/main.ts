/**
 * 前端入口。挂载 Pinia、路由与全局样式；启动时恢复明暗主题。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './app/router'
import { useThemeStore } from '@/platform/theme/useThemeStore'
import './app/styles/main.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
useThemeStore(pinia).hydrate()
app.use(router)
app.mount('#app')
