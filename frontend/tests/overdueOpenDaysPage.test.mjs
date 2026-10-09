import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const tick = () => new Promise(resolve => setImmediate(resolve))

function fixture(list) {
  const states = [], effects = []
  let cursor = 0, tree
  const hooks = {
    useState(value) {
      const position = cursor++
      if (!(position in states)) states[position] = value
      return [states[position], next => { states[position] = typeof next === 'function' ? next(states[position]) : next }]
    },
    useEffect(effect, dependencies) {
      const position = cursor++
      const old = states[position]
      if (!old || dependencies.some((value, index) => !Object.is(value, old.dependencies[index]))) {
        states[position] = { dependencies, cleanup: old?.cleanup }
        effects.push(() => { old?.cleanup?.(); states[position].cleanup = effect() })
      }
    },
  }
  const Stub = () => null
  const imports = {
    react: hooks,
    'react-router-dom': { Link: Stub },
    '../../components/ui/Button': { __esModule: true, default: Stub },
    '../../components/ui/Card': { __esModule: true, default: Stub },
    '../../components/ui/EmptyState': { __esModule: true, default: Stub },
    '../../components/ui/LoadingState': { __esModule: true, default: Stub },
    '../../components/ui/PageHeader': { __esModule: true, default: Stub },
    '../../components/ui/TableActionButton': { tableActionClassName: () => 'action' },
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'LIBRARIAN' }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: error => error.message },
    '../s3-01-loans/loanService': { formatLoanTimestamp: value => value, loanRoles: ['LIBRARIAN'] },
    './overdueLoanService': { overdueLoanService: { list } },
  }
  const source = readFileSync(new URL('../src/features/s3-09-overdue-loans/OverdueLoansPage.tsx', import.meta.url), 'utf8')
  const context = { exports: {}, require: name => imports[name] ?? require(name) }
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  const render = () => {
    cursor = 0
    tree = context.exports.default()
    for (const callback of effects.splice(0)) callback()
    return tree
  }
  render()
  return { render, tree: () => tree }
}

function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) {
    for (const child of node) { const found = find(child, predicate); if (found) return found }
    return undefined
  }
  return predicate(node) ? node : (find(node.props?.children, predicate) ?? find(node.props?.action, predicate))
}
function text(node) {
  if (node == null || typeof node === 'boolean') return ''
  if (Array.isArray(node)) return node.map(text).join(' ')
  return typeof node === 'object' ? text(node.props?.children) : String(node)
}
const item = days => ({ loanId: 5, loanNumber: 'PM-05', itemId: 9, readerId: 12,
  readerName: 'Bạn đọc mẫu', readerPhone: '0900000000', bookId: 1, bookTitle: 'Sách mẫu',
  dueAt: '2026-10-09T17:00:00+07:00', overdueDays: days })

test('opening the list displays server-calculated open days, including zero after a closure', async () => {
  let requests = 0
  const f = fixture(async () => { requests++; return [item(0)] })
  await tick(); f.render()
  assert.equal(requests, 1)
  assert.match(text(f.tree()).replace(/\s+/g, ' '), /Trễ 0 ngày mở cửa/)
  assert.match(find(f.tree(), n => n.props?.title === 'Phiếu mượn quá hạn').props.description, /Chỉ tính các ngày thư viện mở cửa/i)
  assert.match(text(f.tree()), /Ngày trễ \(mở cửa\)/)
})

test('refresh requests a newly calculated overdue count instead of reusing a cached number', async () => {
  let requests = 0
  const f = fixture(async () => [item(++requests)])
  await tick(); f.render()
  assert.match(text(f.tree()).replace(/\s+/g, ' '), /Trễ 1 ngày mở cửa/)
  const refresh = find(f.tree(), node => typeof node.props?.onClick === 'function' && /Làm mới/.test(text(node)))
  assert.ok(refresh)
  refresh.props.onClick()
  f.render()
  await tick(); f.render()
  assert.equal(requests, 2)
  assert.match(text(f.tree()).replace(/\s+/g, ' '), /Trễ 2 ngày mở cửa/)
})

test('failed refresh shows actionable error state and clears old loan rows', async () => {
  let requests = 0
  const f = fixture(async () => { if (requests++) throw new Error('Không tải được lịch thư viện.'); return [item(1)] })
  await tick(); f.render()
  const refresh = find(f.tree(), node => typeof node.props?.onClick === 'function' && /Làm mới/.test(text(node)))
  refresh.props.onClick()
  f.render()
  await tick(); f.render()
  assert.match(text(f.tree()), /Không tải được lịch thư viện/)
  assert.doesNotMatch(text(f.tree()).replace(/\s+/g, ' '), /Trễ 1 ngày mở cửa/)
})
