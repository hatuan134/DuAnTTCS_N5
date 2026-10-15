import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { test } from 'node:test'
const require = createRequire(import.meta.url), react = require('react')
const stub = () => null, Feedback = () => null
const component = value => ({ __esModule: true, default: value })
function load(path, imports, globals = {}) {
 const ctx = { exports: {}, require: name => imports[name] ?? require(name), ...globals }
 vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
 }).outputText, ctx); return ctx.exports
}
function find(node, predicate) {
 if (!node || typeof node !== 'object') return null
 if (Array.isArray(node)) { for (const child of node) { const hit = find(child, predicate); if (hit) return hit } return null }
 return predicate(node) ? node : find(node.props?.children, predicate)
}
function fixture(role, service = {}) {
 let cursor = 0; const slots = [], effects = []
 const hooks = { ...react,
  useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial
   return [slots[i], next => { slots[i] = typeof next === 'function' ? next(slots[i]) : next }] },
  useRef(value) { const i = cursor++; return slots[i] ??= { current: value } },
  useEffect(fn, deps) { const i = cursor++, old = slots[i]; if (!old || deps.some((d, n) => d !== old.deps[n])) {
   slots[i] = { deps }; effects.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() })
  } },
 }
 const imports = { react: hooks, 'react-router-dom': { Navigate: stub, Link: stub },
  '../../core/auth/authStorage': { getCurrentUser: () => role ? { role } : null },
  '../../components/ui/Button': component(stub), '../../components/ui/FeedbackAlert': component(Feedback),
  '../../components/ui/Card': component(stub), '../../components/ui/PageHeader': component(stub),
  '../../components/ui/EmptyState': component(stub), '../../components/ui/TablePagination': component(stub),
  '../../hooks/useTablePagination': component(items => ({ pageItems: items, goToPage() {} })),
  '../s1-02-user-management/accountService': { getApiErrorMessage: (e, fallback) => e.message || fallback },
  './reportService': { formatTime: x => x, formatMoney: x => String(x), ...service },
 }
 const Page = load('../src/features/s4-08-overdue-debt-export/OverdueDebtExportPage.tsx', imports, { AbortController }).default
 let tree
 return { render() { cursor = 0; tree = Page(); effects.splice(0).forEach(f => f()); return tree },
  exportButton() { return find(tree, n => n.props?.action)?.props.action }, notice() { return find(tree, n => n.type === Feedback) } }
}
const data = { readers: [{ readerId: 1, cardNumber: '001', fullName: 'Nguyễn Văn A', phone: '0901', overdueBooks: 3, maxOverdueDays: 15, outstandingVnd: 75000 }], totalReaders: 1, snapshotAt: '2026-10-15' }
const settle = () => new Promise(resolve => setImmediate(resolve))
for (const role of ['READER', 'LIBRARIAN', 'ADMIN', null]) test(`role ${role} never loads private preview`, () => {
 let calls = 0; const f = fixture(role, { getPreview() { calls++ } }); f.render(); assert.equal(calls, 0)
})
test('export locks immediately against duplicate clicks and success uses shared feedback', async () => {
 let complete, calls = 0, saved = 0
 const f = fixture('LIBRARY_MANAGER', { getPreview: async () => data,
  exportExcel: () => { calls++; return new Promise(resolve => { complete = resolve }) }, saveDownload() { saved++ } })
 f.render(); await settle(); f.render(); assert.equal(f.exportButton().props.disabled, false)
 const action = f.exportButton().props.onClick; action(); action(); f.render(); assert.equal(calls, 1); assert.equal(f.exportButton().props.loading, true)
 complete({ filename: 'DanhSachQuaHan_NoPhi_15-10-2026.xlsx', content: new Blob(['PK']) })
 await settle(); f.render(); assert.equal(saved, 1); assert.equal(f.notice().props.tone, 'success')
 f.notice().props.onDismiss(); f.render(); assert.equal(f.notice(), null)
})
test('export failure provides dismissible error and preserves preview', async () => {
 const f = fixture('LIBRARY_MANAGER', { getPreview: async () => data, exportExcel: async () => { throw Error('Tạo tệp thất bại') } })
 f.render(); await settle(); f.render(); f.exportButton().props.onClick(); await settle(); f.render()
 assert.equal(f.notice().props.message, 'Tạo tệp thất bại'); assert.equal(f.notice().props.tone, 'error')
 assert.equal(f.exportButton().props.disabled, false); assert.equal(f.exportButton().props.loading, false)
})
test('empty preview still permits exporting six headers', async () => {
 const f = fixture('LIBRARY_MANAGER', { getPreview: async () => ({ ...data, readers: [], totalReaders: 0 }) })
 f.render(); await settle(); f.render(); assert.equal(f.exportButton().props.disabled, false)
})
test('preview failure cannot export stale data', async () => {
 const f = fixture('LIBRARY_MANAGER', { getPreview: async () => { throw Error('Lỗi tải dữ liệu') } })
 f.render(); await settle(); f.render(); assert.equal(f.exportButton().props.disabled, true); assert.equal(f.notice().props.tone, 'error')
})
test('download service POST, server filename and blob JSON errors', async () => {
 let captured
 const api = { post: async (...args) => { captured = args; return { data: new Blob(['PK']), headers: { 'content-disposition': 'attachment; filename="DanhSachQuaHan_NoPhi_15-10-2026.xlsx"' } } } }
 const service = load('../src/features/s4-08-overdue-debt-export/reportService.ts', {
  '../../core/api/apiClient': { apiClient: api }, axios: { __esModule: true, default: { isAxiosError: e => e?.isAxiosError } },
 }, { Blob, Intl, Date })
 assert.equal((await service.exportExcel()).filename, 'DanhSachQuaHan_NoPhi_15-10-2026.xlsx')
 assert.equal(captured[0], '/reports/overdue-debts/export'); assert.equal(captured[2].responseType, 'blob')
 const error = { isAxiosError: true, response: { data: new Blob([JSON.stringify({ message: 'Không có quyền' })]) } }
 api.post = async () => { throw error }
 await assert.rejects(service.exportExcel(), value => value === error && value.response.data.message === 'Không có quyền')
})
