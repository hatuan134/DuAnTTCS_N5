import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const tick = () => new Promise(resolve => setImmediate(resolve))
const filterSource = readFileSync(new URL('../src/features/s3-09-overdue-loans/overdueFilter.ts', import.meta.url), 'utf8')
const filterContext = { exports: {}, require }
vm.runInNewContext(ts.transpileModule(filterSource, {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText, filterContext)
const filterHelpers = filterContext.exports

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
    '../../components/ui/Input': { __esModule: true, default: Stub },
    '../../components/ui/FeedbackAlert': { __esModule: true, default: ({ message }) => ({ type: 'span', props: { children: message } }) },
    '../../components/ui/EmptyState': { __esModule: true, default: Stub },
    '../../components/ui/LoadingState': { __esModule: true, default: Stub },
    '../../components/ui/PageHeader': { __esModule: true, default: Stub },
    '../../components/ui/TableActionButton': { tableActionClassName: () => 'action', TableActions: Stub, __esModule: true, default: Stub },
    '../../components/ui/TablePagination': { __esModule: true, default: Stub },
    '../../hooks/useTablePagination': { __esModule: true, default: items => ({ pageItems: items, startIndex: 0, page: 1, totalPages: 1, totalItems: items.length, pageSize: 10, goToPage() {} }) },

    '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'LIBRARIAN' }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: error => error.message },
    '../s3-01-loans/loanService': { formatLoanTimestamp: value => value, loanRoles: ['LIBRARIAN'] },
    './overdueLoanService': { overdueLoanService: { list } },
    './overdueFilter': filterHelpers,
    './OverdueContactDialog': { __esModule: true, default: Stub },
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

test('opening the list displays server-calculated calendar days, including zero on the due date', async () => {
  let requests = 0
  const f = fixture(async () => { requests++; return [item(0)] })
  await tick(); f.render()
  assert.equal(requests, 1)
  assert.match(text(f.tree()).replace(/\s+/g, ' '), /Trễ 0 ngày lịch/)
  assert.match(find(f.tree(), n => n.props?.title === 'Phiếu mượn quá hạn').props.description, /Tính theo ngày lịch/i)
  assert.match(text(f.tree()), /Ngày trễ \(ngày lịch\)/)
})

test('refresh requests a newly calculated overdue count instead of reusing a cached number', async () => {
  let requests = 0
  const f = fixture(async () => [item(++requests)])
  await tick(); f.render()
  assert.match(text(f.tree()).replace(/\s+/g, ' '), /Trễ 1 ngày lịch/)
  const refresh = find(f.tree(), node => typeof node.props?.onClick === 'function' && /Làm mới/.test(text(node)))
  assert.ok(refresh)
  refresh.props.onClick()
  f.render()
  await tick(); f.render()
  assert.equal(requests, 2)
  assert.match(text(f.tree()).replace(/\s+/g, ' '), /Trễ 2 ngày lịch/)
})

test('failed refresh shows actionable error state and clears old loan rows', async () => {
  let requests = 0
  const f = fixture(async () => { if (requests++) throw new Error('Không tải được lịch thư viện.'); return [item(1)] })
  await tick(); f.render()
  const refresh = find(f.tree(), node => typeof node.props?.onClick === 'function' && /Làm mới/.test(text(node)))
  refresh.props.onClick()
  f.render()
  await tick(); f.render()
  assert.ok(find(f.tree(), node => node.props?.message?.includes('Không tải được lịch thư viện.')), 'Lỗi tải dữ liệu phải được truyền cho FeedbackAlert')
  assert.doesNotMatch(text(f.tree()).replace(/\s+/g, ' '), /Trễ 1 ngày lịch/)
})

function collect(tree, predicate, result = []) {
  if (!tree || typeof tree !== 'object') return result
  if (Array.isArray(tree)) { for (const child of tree) collect(child, predicate, result); return result }
  if (predicate(tree)) result.push(tree)
  collect(tree.props?.children, predicate, result)
  return result
}
function visibleDays(tree) {
  return collect(tree, node => node.type === 'article').map(node =>
    Number(text(node).replace(/\s+/g, ' ').match(/Trễ (\d+) ngày lịch/)?.[1]))
}
function changeInput(f, id, value) {
  find(f.tree(), node => node.props?.id === id).props.onChange({ target: { value } })
  f.render()
}
function applyForm(f) {
  find(f.tree(), node => node.type === 'form').props.onSubmit({ preventDefault() {} })
  f.render()
}
const variedItems = [7, 31, 8, 30, 45, 0].map((days, i) => ({
  ...item(days), loanId: i + 1, itemId: i + 1,
}))

test('librarian quick buttons >7 and >30 update both table and mobile list', async () => {
  const f = fixture(async () => variedItems)
  await tick(); f.render()
  assert.equal(visibleDays(f.tree()).length, 6)
  find(f.tree(), n => n.props?.onClick && text(n).replace(/\s+/g, ' ').trim() === 'Trên 7 ngày').props.onClick()
  f.render()
  assert.deepEqual(visibleDays(f.tree()), [45, 31, 30, 8])
  assert.match(text(f.tree()).replace(/\s+/g, ' '), /Tìm thấy 4 phiếu phù hợp/)
  find(f.tree(), n => n.props?.onClick && text(n).replace(/\s+/g, ' ').trim() === 'Trên 30 ngày').props.onClick()
  f.render()
  assert.deepEqual(visibleDays(f.tree()), [45, 31])
  assert.match(text(f.tree()).replace(/\s+/g, ' '), /Tìm thấy 2 phiếu phù hợp/)
})

test('custom inclusive interval 7..30 and reset without another API request', async () => {
  let calls = 0
  const f = fixture(async () => { calls++; return variedItems })
  await tick(); f.render()
  changeInput(f, 'overdue-minimum', '7')
  changeInput(f, 'overdue-maximum', '30')
  applyForm(f)
  assert.deepEqual(visibleDays(f.tree()), [30, 8, 7])
  find(f.tree(), n => n.props?.onClick && text(n).includes('Xóa bộ lọc')).props.onClick()
  f.render()
  assert.deepEqual(visibleDays(f.tree()), [45, 31, 30, 8, 7, 0])
  assert.equal(calls, 1)
})

test('zero matches displays filtered empty state; clear restores all rows', async () => {
  const f = fixture(async () => [item(7)])
  await tick(); f.render()
  find(f.tree(), n => n.props?.onClick && text(n).replace(/\s+/g, ' ').trim() === 'Trên 30 ngày').props.onClick()
  f.render()
  assert.deepEqual(visibleDays(f.tree()), [])
  assert.ok(find(f.tree(), n => n.props?.title === 'Không có phiếu phù hợp'))
  assert.match(text(f.tree()).replace(/\s+/g, ' '), /Tìm thấy 0 phiếu phù hợp/)
  find(f.tree(), n => n.props?.onClick && text(n).includes('Xóa bộ lọc')).props.onClick()
  f.render()
  assert.deepEqual(visibleDays(f.tree()), [7])
})

test('invalid interval refuses to apply and displays 3000ms FeedbackAlert notification', async () => {
  const f = fixture(async () => variedItems)
  await tick(); f.render()
  changeInput(f, 'overdue-minimum', '31')
  changeInput(f, 'overdue-maximum', '7')
  applyForm(f)
  assert.deepEqual(visibleDays(f.tree()), [45, 31, 30, 8, 7, 0])
  const notice = find(f.tree(), n => n.props?.tone === 'error' && n.props?.message?.includes('Khoảng lọc'))
  assert.ok(notice)
  assert.match(notice.props.message, /tối thiểu không được lớn hơn tối đa/)
})

test('manual >N input excludes day N and filter remains applied after refresh', async () => {
  let calls = 0
  const f = fixture(async () => { calls++; return variedItems })
  await tick(); f.render()
  changeInput(f, 'overdue-minimum', '8')
  find(f.tree(), n => n.type === 'input' && n.props?.type === 'checkbox').props.onChange({ target: { checked: true } })
  f.render()
  applyForm(f)
  assert.deepEqual(visibleDays(f.tree()), [45, 31, 30])
  const refresh = find(f.tree(), n => n.props?.onClick && text(n).includes('Làm mới'))
  refresh.props.onClick()
  f.render()
  await tick(); f.render()
  assert.deepEqual(visibleDays(f.tree()), [45, 31, 30])
  assert.equal(calls, 2)
})
