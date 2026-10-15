import { apiClient } from '../../core/api/apiClient'

export type Rule = 'ALLOW' | 'DENY' | 'OWN' | 'OVERRIDE'
export interface RoleOption { code: string; name: string }
export interface ModuleOption { code: string; name: string; priority: number }
export interface PolicyEntry {
  id: string; module: string; label: string; controller: string; handler: string
  path: string; httpMethods: string[]; rules: Record<string, Rule>; condition: string; mutating: boolean
}
export interface ReviewedEntry {
  policy: PolicyEntry; guard: string; mapped: boolean; different: boolean
  roles: { role: string; documented: Rule; actualGate: 'ALLOW' | 'DENY' | 'UNKNOWN'; matches: boolean }[]
}
export interface Inspection {
  policyVersion: string; decision: string; fingerprint: string; inspectedAt: string
  roles: RoleOption[]; modules: ModuleOption[]; reviewedFunctions: number
  differentFunctions: number; unresolvedFunctions: number; entries: ReviewedEntry[]
}
export interface EvidenceCase {
  endpointId: string; role: string; scenario: 'STANDARD' | 'OVERRIDE' | 'OTHER_OWNER'
  expectedStatus: number; beforeStatus: number | null; actualStatus: number
  resourceUnchanged: boolean | null; detectedAt: string; testedAt: string; evidence: string; correction: string | null
}
export interface ImportReport {
  schemaVersion: 1; runKey: string; policyVersion: string; fingerprint: string
  kind: 'REGRESSION' | 'SIMULATION'; environment: 'MOCKMVC' | 'ISOLATED_POSTGRES'
  sourceReference: string; executedBy: string; startedAt: string; completedAt: string; checks: EvidenceCase[]
}
export interface ReportSummary {
  id: number; runKey: string; kind: ImportReport['kind']; environment: ImportReport['environment']
  sourceReference: string; executedBy: string; importedBy: string; importedAt: string; completedAt: string
  caseCount: number; detectedCount: number; fixedCount: number; failedCount: number
}
export interface CaseResult {
  test: EvidenceCase; module: string; functionName: string; documented: Rule | 'LOGIN_REQUIRED'
  outcome: 'PASS' | 'FIXED' | 'OPEN'; passed: boolean
}
export interface ReportDetail {
  summary: ReportSummary; evidence: ImportReport; results: CaseResult[]; currentFingerprint: boolean
}
export interface ReportPage { items: ReportSummary[]; page: number; size: number; totalItems: number }
const base = '/permission-review'
export const permissionReviewService = {
  async inspect(signal?: AbortSignal) { return (await apiClient.get<Inspection>(base, { signal })).data },
  async reports(page: number, signal?: AbortSignal) {
    return (await apiClient.get<ReportPage>(base + '/reports', { params: { page, size: 10 }, signal })).data
  },
  async detail(id: number, signal?: AbortSignal) { return (await apiClient.get<ReportDetail>(base + '/reports/' + id, { signal })).data },
  async importReport(report: ImportReport) { return (await apiClient.post<ReportDetail>(base + '/reports', report)).data },
}
export const ruleLabels: Record<Rule | 'LOGIN_REQUIRED', string> = {
  ALLOW: 'Được phép', DENY: 'Không được phép', OWN: 'Chỉ dữ liệu cá nhân',
  OVERRIDE: 'Ngoại lệ có lý do', LOGIN_REQUIRED: 'Phải đăng nhập',
}
export const gateLabels = { ALLOW: 'Cho qua cổng vai trò', DENY: 'Bị chặn tại cổng vai trò', UNKNOWN: 'Chưa xác định được' }
export function selectEntries(data: Inspection, module: string, query: string, differencesOnly: boolean) {
  const needle = query.trim().toLocaleLowerCase('vi-VN')
  return data.entries.filter(entry => (!module || entry.policy.module === module)
    && (!differencesOnly || entry.different)
    && (!needle || (entry.policy.label + ' ' + entry.policy.path + ' ' + entry.policy.id).toLocaleLowerCase('vi-VN').includes(needle)))
}
export function parseReportFile(text: string): ImportReport {
  let value: unknown
  try { value = JSON.parse(text) } catch { throw new Error('Tệp không phải JSON hợp lệ.') }
  if (!value || typeof value !== 'object') throw new Error('Tệp không chứa biên bản kiểm thử.')
  const report = value as Partial<ImportReport>
  if (report.schemaVersion !== 1 || !['REGRESSION', 'SIMULATION'].includes(report.kind ?? '')
    || !Array.isArray(report.checks) || !report.checks.length || report.checks.length > 2000
    || typeof report.fingerprint !== 'string' || !/^[a-f0-9]{64}$/.test(report.fingerprint)) {
    throw new Error('Biên bản sai định dạng S4-09.1 hoặc không có kết quả kiểm thử.')
  }
  return report as ImportReport
}
export const formatReviewTime = (value: string) => new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'medium', hour12: false,
}).format(new Date(value))

export function downloadReport(detail: ReportDetail) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(detail, null, 2)], { type: 'application/json;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url; link.download = 'BienBan_S4-09.1_' + detail.summary.id + '.json'
  try { document.body.appendChild(link); link.click() }
  finally { link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000) }
}
