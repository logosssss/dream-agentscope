<script setup lang="ts">
/**
 * 公开介绍页。品牌 Dream-SaaS，点「开始使用」进产品；未登录由路由守卫去登录页。
 */
import SiteComplianceFooter from '@/platform/compliance/SiteComplianceFooter.vue'
import { hasSession } from '@/shared/auth/session'
import { useRouter } from 'vue-router'

const router = useRouter()
const githubUrl = 'https://github.com/logosssss/dream-agentscope'

/** 已登录直达搭子；未登录去 /companion，守卫会带 redirect 踢去登录。 */
async function startUsing() {
  await router.push(hasSession() ? '/companion' : { path: '/companion' })
}
</script>

<template>
  <div class="landing">
    <div class="landing__glow landing__glow--a" aria-hidden="true" />
    <div class="landing__glow landing__glow--b" aria-hidden="true" />
    <div class="landing__grain" aria-hidden="true" />

    <main class="landing__main">
      <p class="landing__brand">Dream-SaaS</p>
      <h1 class="landing__title">智能搭子 · 多 Agent 实战平台</h1>
      <p class="landing__lead">
        从 RAG 到多 Agent 编排的全栈底座。开源可自建，点进去就能试用。
      </p>
      <div class="landing__actions">
        <button type="button" class="landing__cta" @click="startUsing">开始使用</button>
        <a class="landing__link" :href="githubUrl" target="_blank" rel="noopener noreferrer">
          GitHub
        </a>
      </div>
    </main>

    <SiteComplianceFooter class="landing__foot" />
  </div>
</template>

<style scoped>
.landing {
  position: relative;
  min-height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  color: #0c1222;
  background:
    linear-gradient(165deg, #f7f4ef 0%, #e8eef6 42%, #dde8f2 100%);
  font-family: "Sora", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.landing__glow {
  position: absolute;
  border-radius: 9999px;
  filter: blur(64px);
  opacity: 0.55;
  pointer-events: none;
  animation: landing-drift 14s ease-in-out infinite alternate;
}

.landing__glow--a {
  width: min(52vw, 28rem);
  height: min(52vw, 28rem);
  left: -8%;
  top: 12%;
  background: radial-gradient(circle, #f9a8d4 0%, transparent 70%);
}

.landing__glow--b {
  width: min(48vw, 26rem);
  height: min(48vw, 26rem);
  right: -6%;
  bottom: 8%;
  background: radial-gradient(circle, #93c5fd 0%, transparent 70%);
  animation-delay: -4s;
}

.landing__grain {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.07;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

.landing__main {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 40rem;
  margin: 0 auto;
  padding: 4.5rem 1.5rem 3rem;
  animation: landing-rise 0.7s ease-out both;
}

.landing__brand {
  margin: 0;
  font-size: clamp(2.4rem, 7vw, 3.75rem);
  font-weight: 700;
  letter-spacing: -0.04em;
  line-height: 1.05;
  animation: landing-rise 0.8s ease-out 0.05s both;
}

.landing__title {
  margin: 1rem 0 0;
  font-size: clamp(1.15rem, 2.8vw, 1.45rem);
  font-weight: 500;
  letter-spacing: -0.01em;
  color: #1e293b;
  animation: landing-rise 0.8s ease-out 0.12s both;
}

.landing__lead {
  margin: 1rem 0 0;
  max-width: 32rem;
  font-size: 1.05rem;
  line-height: 1.65;
  color: #475569;
  animation: landing-rise 0.8s ease-out 0.18s both;
}

.landing__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem 1.25rem;
  margin-top: 2rem;
  animation: landing-rise 0.8s ease-out 0.24s both;
}

.landing__cta {
  border: 0;
  border-radius: 0.75rem;
  padding: 0.85rem 1.4rem;
  font: inherit;
  font-weight: 600;
  color: #fff;
  background: #ec4899;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  box-shadow: 0 10px 28px rgb(236 72 153 / 0.28);
}

.landing__cta:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 32px rgb(236 72 153 / 0.34);
}

.landing__cta:focus-visible {
  outline: 2px solid #be185d;
  outline-offset: 3px;
}

.landing__link {
  font-weight: 600;
  color: #0f172a;
  text-decoration: none;
  border-bottom: 1px solid rgb(15 23 42 / 0.35);
  padding-bottom: 0.1rem;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.landing__link:hover {
  color: #be185d;
  border-color: #be185d;
}

.landing__foot {
  position: relative;
  z-index: 1;
}

@keyframes landing-rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes landing-drift {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(3%, -4%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .landing__glow,
  .landing__main,
  .landing__brand,
  .landing__title,
  .landing__lead,
  .landing__actions {
    animation: none;
  }

  .landing__cta {
    transition: none;
  }
}
</style>
