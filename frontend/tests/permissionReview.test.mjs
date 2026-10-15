import { createS4Require } from './s4TestImports.mjs'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createS4Require(import.meta.url), react = require('react')
const root = new URL('../src/features/s4-09-permission-review/', import.meta.url)
function load(name, imports = {}, globals = {}) {
  const context = { exports: {}, require: key => imports[key] ?? require(key), ...globals }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(name, root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
  }).outputText, context)
  return context.exports
}
const policy = JSON.parse(readFileSync(new URL('../../backend/src/main/resources/permissions/S4-09.2-policy.json', import.meta.url)))
const inspection = { policyVersion: policy.version, roles: policy.roles, modules: policy.modules, fingerprint: 'a'.repeat(64),
  decision: policy.decision, inspectedAt: '2026-10-15T02:41:00Z', reviewedFunctions: policy.entries.length,
  differentFunctions: 0, unresolvedFunctions: 0, entries: policy.entries.map(entry => ({ policy: entry, guard: "hasRole('LIBRARIAN')", mapped: true,
    different: false, roles: policy.roles.map(role => ({ role: role.code, documented: entry.rules[role.code], actualGate: entry.rules[role.code] === 'DENY' ? 'DENY' : 'ALLOW', matches: true })) })) }
const report = { schemaVersion: 1, runKey: '4b40f1ca-5cb8-47d7-b293-eeb16f63466a', policyVersion: policy.version,
  fingerprint: 'a'.repeat(64), kind: 'SIMULATION', environment: 'MOCKMVC', sourceReference: 'Test only', executedBy: 'Người chạy thử',
  startedAt: '2026-10-15T02:41:00Z', completedAt: '2026-10-15T02:42:00Z', checks: [{ endpointId: 'FeePaymentController.collect', role: 'READER',
    scenario: 'STANDARD', expectedStatus: 403, beforeStatus: 200, actualStatus: 403, resourceUnchanged: true,
    detectedAt: '2026-10-15T02:41:10Z', testedAt: '2026-10-15T02:41:50Z', evidence: 'Mô phỏng', correction: 'Khôi phục guard thật' }] }
const detail = { summary: { id: 1, kind: 'SIMULATION', environment: 'MOCKMVC', sourceReference: 'Test only', executedBy: 'Người chạy thử',
  importedBy: 'Quản trị thử', importedAt: '2026-10-15T02:43:00Z', completedAt: report.completedAt, caseCount: 1, detectedCount: 1, fixedCount: 1, failedCount: 0 },
  evidence: report, currentFingerprint: true, results: [{ test: report.checks[0], module: 'FEES', functionName: 'Thu toàn bộ phí', documented: 'DENY', outcome: 'FIXED', passed: true }] }
const service = load('permissionReviewService.ts', { '../../core/api/apiClient': { apiClient: {} } })

test('uses authenticated API client for review and reports, without accepting caller-supplied admin identity', async () => {
  const calls = [], api = load('permissionReviewService.ts', { '../../core/api/apiClient': { apiClient: {
    get: async (...args) => { calls.push(['GET', ...args]); return { data: {} } },
    post: async (...args) => { calls.push(['POST', ...args]); return { data: detail } },
  } } }).permissionReviewService
  await api.inspect(); await api.reports(2); await api.detail(1); await api.importReport(report)
  assert.deepEqual(calls.map(call => call[1]), ['/permission-review', '/permission-review/reports', '/permission-review/reports/1', '/permission-review/reports'])
  assert.equal(calls[1][2].params.page, 2); assert.equal(calls[3][2], report); assert.ok(!('actorId' in calls[3][2]))
})
test('module, keyword and discrepancy filters compose and cover all reviewed system modules', () => {
  for (const module of policy.modules) assert.ok(service.selectEntries(inspection, module.code, '', false).length)
  assert.equal(service.selectEntries(inspection, 'FEES', 'collect-full', false).length, 1)
  assert.equal(service.selectEntries(inspection, 'STOCK', 'collect-full', false).length, 0)
  assert.equal(service.selectEntries(inspection, '', '', true).length, 0)
  const changed = { ...inspection, entries: [{ ...inspection.entries[0], different: true, roles: inspection.entries[0].roles.map((role, i) => ({ ...role, matches: i !== 0 })) }, ...inspection.entries.slice(1)] }
  assert.equal(service.selectEntries(changed, '', '', true).length, 1)
})
test('malformed or empty evidence is rejected and simulated results retain their identity', () => {
  for (const value of ['broken', '{}', '[]', 'null', JSON.stringify({ ...report, checks: [] }), JSON.stringify({ ...report, fingerprint: 'bad' })]) {
    assert.throws(() => service.parseReportFile(value))
  }
  assert.equal(service.parseReportFile(JSON.stringify(report)).kind, 'SIMULATION')
})

const stub = component => ({ __esModule: true, default: component })
const Feedback = props => react.createElement('p', {}, props.message)
const Modal = props => props.children
function imports(role, api, hooks = react) {
  return { react: hooks,
    '../../components/ui/Button': stub(({ children, loading, ...props }) => react.createElement('button', { ...props, disabled: props.disabled || loading }, children)),
    '../../components/ui/Card': stub(({ children }) => react.createElement('section', {}, children)),
    '../../components/ui/EmptyState': stub(({ title, description }) => react.createElement('p', {}, title + description)),
    '../../components/ui/FeedbackAlert': stub(Feedback), '../../components/ui/LoadingState': stub(() => react.createElement('p', {}, 'Đang tải')),
    '../../components/ui/Modal': stub(Modal), '../../components/ui/PageHeader': stub(({ title }) => react.createElement('h1', {}, title)),
    '../../components/ui/TablePagination': stub(() => null),
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (error, fallback) => error.message || fallback },
    './permissionReviewService': { ...service, permissionReviewService: api },
  }
}
test('direct navigation by non-admin renders denial and starts no review API request', () => {
  for (const role of ['LIBRARIAN', 'LIBRARY_MANAGER', 'READER', undefined]) {
    let calls = 0
    const page = load('PermissionReviewPage.tsx', imports(role, { inspect: () => { calls++ } })).default
    assert.match(renderToStaticMarkup(react.createElement(page)), /Không có quyền truy cập/)
    assert.equal(calls, 0)
  }
})
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) { for (const value of node) { const result = find(value, predicate); if (result) return result }; return undefined }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
function harness(api = {}) {
  const slots = [], pending = []; let cursor = 0, tree
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value }] },
    useRef(initial) { const i = cursor++; return slots[i] ??= { current: initial } },
    useEffect(fn, deps) { const i = cursor++, old = slots[i]
      if (!old || deps.some((value, n) => !Object.is(value, old.deps[n]))) { slots[i] = { deps, cleanup: old?.cleanup }; pending.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() }) }
    },
  }
  const module = load('PermissionReviewPage.tsx', imports('ADMIN', {
    inspect: async () => inspection, reports: async () => ({ items: [], page: 0, size: 10, totalItems: 0 }),
    detail: async () => detail, importReport: async () => detail,
    latestRun: async () => null, coverage: async () => ({ totalApis: 191, coveredApis: 1, requiredRoleCases: 764, coveredRoleCases: 1, passedCases: 1, failedCases: 0, unresolvedFindings: 0, missing: [], eligible: false, reasons: ['Còn thiếu kiểm thử'], completion: null }),
    exportReport: async () => ({ report: detail, coverage: {}, sprintReports: [] }), ...api,
  }, hooks), { AbortController, File: class {} })
  function render() { cursor = 0; tree = module.PermissionReviewWorkspace(); for (const effect of pending.splice(0)) effect(); return tree }
  render()
  return { render, node: predicate => find(tree, predicate),
    notice: () => find(tree, value => value.type === Feedback), modal: () => find(tree, value => value.type === Modal),
    choose: file => find(tree, value => value.type === 'input' && value.props.type === 'file').props.onChange({ target: { files: [file] }, currentTarget: { value: 'selected-file' } }),
    unmount: () => slots.forEach(slot => slot?.cleanup?.()),
  }
}
const settle = () => new Promise(resolve => setImmediate(resolve))
test('invalid file produces temporary warning while fixed file validation remains after dismissal', async () => {
  const page = harness(); await settle(); page.render()
  page.choose({ name: 'broken.json', size: 10, text: async () => 'broken' }); await settle(); page.render()
  assert.equal(page.notice().props.tone, 'warning')
  page.notice().props.onDismiss(); page.render(); assert.equal(page.notice(), undefined)
  assert.ok(page.node(node => node.type === 'p' && node.props.role === 'alert'))
  assert.equal(page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.disabled, true)
  page.unmount()
})
test('successful import resists double submit and keeps the report open after temporary success dismissal', async () => {
  let resolve, calls = 0
  const pending = new Promise(done => { resolve = done })
  const page = harness({ importReport: async () => { calls++; return pending } }); await settle(); page.render()
  page.choose({ name: 'simulation.json', size: 2000, text: async () => JSON.stringify(report) }); await settle(); page.render()
  const button = page.node(node => node.props?.children?.[1] === 'Lưu biên bản')
  button.props.onClick(); button.props.onClick(); assert.equal(calls, 1)
  resolve(detail); await settle(); page.render()
  assert.equal(page.notice().props.tone, 'success'); assert.ok(page.modal())
  page.notice().props.onDismiss(); page.render(); assert.equal(page.notice(), undefined); assert.ok(page.modal())
  assert.match(renderToStaticMarkup(page.modal()), /MÔ PHỎNG/)
  page.unmount()
})
test('API rejection leaves the selected evidence available for retry and shows error feedback', async () => {
  const page = harness({ importReport: async () => { throw new Error('Source đã thay đổi. Chạy lại kiểm thử.') } }); await settle(); page.render()
  page.choose({ name: 'regression.json', size: 2000, text: async () => JSON.stringify(report) }); await settle(); page.render()
  page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.onClick(); await settle(); page.render()
  assert.equal(page.notice().props.tone, 'error'); assert.match(page.notice().props.message, /Source đã thay đổi/)
  assert.equal(page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.disabled, false)
  page.unmount()
})
test('closing the report early never remounts its temporary notification outside the modal', async () => {
  const page = harness(); await settle(); page.render()
  page.choose({ name: 'simulation.json', size: 2000, text: async () => JSON.stringify(report) }); await settle(); page.render()
  page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.onClick(); await settle(); page.render()
  assert.ok(page.notice()); assert.ok(page.modal())
  page.modal().props.onClose(); page.render()
  assert.equal(page.modal(), undefined); assert.equal(page.notice(), undefined)
  page.unmount()
})


test('role and comparison-state filters distinguish unknown rights from a concrete mismatch', () => {
  const row = inspection.entries[0]
  const sample = { ...inspection, entries: [{ ...row, different: true, roles: row.roles.map(role => ({ ...role, matches: false, documented: role.role === 'READER' ? 'UNKNOWN' : role.documented })) }] }
  assert.equal(service.selectEntries(sample, '', '', false, 'READER', 'UNKNOWN').length, 1)
  assert.equal(service.selectEntries(sample, '', '', false, 'ADMIN', 'UNKNOWN').length, 0)
  assert.equal(service.selectEntries(sample, '', '', false, 'ADMIN', 'DIFFERENT').length, 1)
})
test('server-run, progress, coverage, confirmation and export use protected review URLs', async () => {
  const calls = [], api = load('permissionReviewService.ts', { '../../core/api/apiClient': { apiClient: {
    get: async (...args) => { calls.push(['GET', ...args]); return { data: null } },
    post: async (...args) => { calls.push(['POST', ...args]); return { data: {} } },
  } } }).permissionReviewService
  await api.startRun(); await api.latestRun(); await api.runStatus('run-key'); await api.coverage(7); await api.complete(7); await api.exportReport(7)
  assert.deepEqual(calls.map(call => call[1]), ['/permission-review/runs', '/permission-review/runs/latest', '/permission-review/runs/run-key', '/permission-review/reports/7/coverage', '/permission-review/reports/7/complete', '/permission-review/reports/7/export'])
  assert.equal(calls[0].length, 2)
})
test('a missing-coverage report keeps completion disabled', async () => {
  const page = harness(); await settle(); page.render()
  page.choose({ name: 'simulation.json', size: 2000, text: async () => JSON.stringify(report) }); await settle(); page.render()
  page.node(node => node.props?.children?.[1] === 'Lưu biên bản').props.onClick(); await settle(); page.render(); await settle(); page.render()
  const button = page.node(node => node.props?.children?.[1] === 'Xác nhận hoàn tất rà soát')
  assert.equal(button.props.disabled, true); page.unmount()
})
