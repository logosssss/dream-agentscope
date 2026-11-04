<script setup lang="ts">
/**
 * 公开页合规脚注：版权、技术来源、公众号、开源仓、ICP/公安备案。
 * 仅挂在介绍页与登录/注册，不进登录后业务壳。
 */
const githubUrl = 'https://github.com/logosssss/dream-agentscope'
const agentscopeUrl = 'https://github.com/agentscope-ai/agentscope'
const icpUrl = 'https://beian.miit.gov.cn/'
/** 公安备案号查询；code 与号牌数字段一致。 */
const policeUrl =
  'https://beian.mps.gov.cn/#/query/webSearch?code=44098302441256'
</script>

<template>
  <footer class="site-compliance" aria-label="站点备案与版权信息">
    <p class="site-compliance__line">
      <span>© 2026 Dream-SaaS</span>
      <span class="site-compliance__dot" aria-hidden="true">·</span>
      <span>
        基于
        <a class="site-compliance__a" :href="agentscopeUrl" target="_blank" rel="noopener noreferrer">AgentScope</a>
        构建
      </span>
      <span class="site-compliance__dot" aria-hidden="true">·</span>
      <span>公众号 Java宋转AI</span>
      <span class="site-compliance__dot" aria-hidden="true">·</span>
      <a class="site-compliance__a" :href="githubUrl" target="_blank" rel="noopener noreferrer">
        GitHub dream-agentscope
      </a>
      <span class="site-compliance__dot" aria-hidden="true">·</span>
      <a class="site-compliance__a" :href="icpUrl" target="_blank" rel="noopener noreferrer">
        粤ICP备20036536号
      </a>
      <span class="site-compliance__dot" aria-hidden="true">·</span>
      <a class="site-compliance__a site-compliance__police" :href="policeUrl" target="_blank" rel="noopener noreferrer">
        <img class="site-compliance__badge" src="/beian-police.png" width="14" height="14" alt="" />
        粤公网安备44098302441256号
      </a>
    </p>
  </footer>
</template>

<style scoped>
.site-compliance {
  width: 100%;
  padding: 1rem 1.25rem 1.35rem;
  text-align: center;
}

.site-compliance__line {
  margin: 0;
  font-size: 0.75rem;
  line-height: 1.7;
  color: var(--color-text-muted, #64748b);
}

.site-compliance__dot {
  margin: 0 0.35rem;
  opacity: 0.7;
}

.site-compliance__a {
  color: inherit;
  text-decoration: none;
  border-bottom: 1px solid transparent;
}

.site-compliance__a:hover {
  color: var(--color-brand-primary, #ec4899);
  border-bottom-color: currentColor;
}

.site-compliance__police {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  vertical-align: baseline;
}

.site-compliance__badge {
  display: inline-block;
  width: 14px;
  height: 14px;
  object-fit: contain;
}

@media (max-width: 640px) {
  .site-compliance__line {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.2rem;
  }

  .site-compliance__dot {
    display: none;
  }
}
</style>
