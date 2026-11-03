import { apiJson } from '@/shared/api/http'
import type {
  EvalCaseSummary,
  EvalDatasetSummary,
  EvalRunSummary,
} from '@/shared/types/api.types'

/**
 * 评测目录。数据集可列表；用例与执行只有写入，不调模型。
 */

/** 最近评测数据集，含停用。 */
export function listEvalDatasets(limit = 50): Promise<EvalDatasetSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<EvalDatasetSummary[]>(`/api/admin/eval-datasets?${q}`)
}

/** 登记数据集，版本由服务端固定 1.0.0。 */
export function registerEvalDataset(body: {
  datasetId: string
  name: string
  description: string
}): Promise<EvalDatasetSummary> {
  return apiJson<EvalDatasetSummary>('/api/admin/eval-datasets', {
    method: 'POST',
    body: JSON.stringify({
      datasetId: body.datasetId,
      name: body.name,
      description: body.description || null,
    }),
  })
}

/** 停用仍启用的 1.0.0；停用后不再开跑。 */
export function disableEvalDataset(datasetId: string): Promise<EvalDatasetSummary> {
  return apiJson<EvalDatasetSummary>(
    `/api/admin/eval-datasets/${encodeURIComponent(datasetId)}/disable`,
    { method: 'POST' },
  )
}

/** 恢复已停用的 1.0.0。 */
export function restoreEvalDataset(datasetId: string): Promise<EvalDatasetSummary> {
  return apiJson<EvalDatasetSummary>(
    `/api/admin/eval-datasets/${encodeURIComponent(datasetId)}/restore`,
    { method: 'POST' },
  )
}

/** 给该数据集 1.0.0 追加用例。 */
export function addEvalCase(
  datasetId: string,
  body: { caseNo: number; inputText: string; expectedText: string },
): Promise<EvalCaseSummary> {
  return apiJson<EvalCaseSummary>(
    `/api/admin/eval-datasets/${encodeURIComponent(datasetId)}/cases`,
    {
      method: 'POST',
      body: JSON.stringify({
        caseNo: body.caseNo,
        inputText: body.inputText,
        expectedText: body.expectedText || null,
      }),
    },
  )
}

/** 为数据集 1.0.0 登记一次执行，状态停在 RUNNING。 */
export function startEvalRun(datasetId: string): Promise<EvalRunSummary> {
  return apiJson<EvalRunSummary>('/api/admin/eval-runs', {
    method: 'POST',
    body: JSON.stringify({ datasetId }),
  })
}

/** 给仍在跑的执行写入通过数，改成 SUCCEEDED。 */
export function scoreEvalRun(evalRunId: string, passedCases: number): Promise<EvalRunSummary> {
  return apiJson<EvalRunSummary>(
    `/api/admin/eval-runs/${encodeURIComponent(evalRunId)}/score`,
    {
      method: 'POST',
      body: JSON.stringify({ passedCases }),
    },
  )
}
