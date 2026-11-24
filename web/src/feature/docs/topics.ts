/**
 * 文档站的固定主题名。顺序与服务端树一致，页面不要自己再排。
 */
export const DOC_TOPICS = [
  { id: 'architecture', label: '架构与决策' },
  { id: 'agent-engineering', label: 'Agent 工程' },
  { id: 'knowledge-retrieval', label: '知识与检索' },
  { id: 'data-storage', label: '数据与存储' },
  { id: 'frontend', label: '前端与交互' },
  { id: 'ops', label: '运维与部署' },
  { id: 'career', label: '职业与成长' },
] as const

/** 未知主题键原样显示，避免页面空白。 */
export function topicLabel(topic: string): string {
  const found = DOC_TOPICS.find((item) => item.id === topic)
  return found ? found.label : topic
}

/** 路径种类只做角标，不参与能不能看。 */
export function docTypeLabel(docType: string): string {
  if (docType === 'readme') {
    return '说明'
  }
  if (docType === 'handbook') {
    return '手册'
  }
  if (docType === 'tutorial') {
    return '教程'
  }
  if (docType === 'reference') {
    return '参考'
  }
  if (docType === 'landing') {
    return '落地'
  }
  return '指南'
}
