import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
function load(path, imports = {}, extra = {}) {
  const context = { exports: {}, require: n => imports[n] ?? require(n), ...extra }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
const path = '../src/features/s4-04-card-lock-suggestions/'
const helpers = load(path + 'cardLockSuggestionService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const settle = () => new Promise(resolve => setImmediate(resolve))
function hooks() {
  const slots = [], effects = []; let cursor = 0
  return { react: { ...react,
    useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial
      return [slots[i], v => { slots[i] = typeof v === 'function' ? v(slots[i]) : v }] },
    useRef(initial) { const i = cursor++; return slots[i] ??= { current: initial } },
    useEffect(fn, deps) { const i = cursor++, old = slots[i]
      if (!old || deps.some((v, n) => !Object.is(v, old.deps[n]))) {
        slots[i] = { deps, cleanup: old?.cleanup }; effects.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() })
      }
    },
  }, render(fn) { cursor = 0; const value = fn(); for (const effect of effects.splice(0)) effect(); return value } }
}
const Stub = () => null, Feedback = () => null, LockDialog = () => null
function find(tree, pred) {
  if (Array.isArray(tree)) { for (const node of tree) { const found = find(node, pred); if (found) return found }; return }
  if (!tree || typeof tree !== 'object') return
  return pred(tree) ? tree : find(tree.props?.children, pred)
}
function text(tree) {
  if (Array.isArray(tree)) return tree.map(text).join(' ')
  if (tree === null || tree === undefined || typeof tree === 'boolean') return ''
  return typeof tree === 'object' ? text(tree.props?.children) : String(tree)
}
function page(file, service, props = {}, role = 'LIBRARY_MANAGER') {
  const f = hooks(), dependencies = { react: f.react,
    'react-router-dom': { Link: Stub },
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    '../s3-01-loans/loanService': { formatLoanTimestamp: v => v },
    './cardLockSuggestionService': { ...helpers, cardLockSuggestionService: service },
    '../s1-04-library-card/LockLibraryCardDialog': { __esModule: true, default: LockDialog },
    './CardLockSuggestionDetailsDialog': { __esModule: true, default: Stub },
    '../../components/ui/FeedbackAlert': { __esModule: true, default: Feedback },
  }
  for (const name of ['Modal', 'Button', 'Card', 'Input', 'PageHeader', 'TablePagination', 'StatusBadge']) dependencies[`../../components/ui/${name}`] = { __esModule: true, default: Stub }
  const Root = load(path + file, dependencies).default
  const wrapper = Root(props), Component = typeof wrapper?.type === 'function' && file !== 'CardLockSuggestionDetailsDialog.tsx' ? wrapper.type : Root
  let tree; function render() { tree = f.render(() => Component(props)); return tree }; render()
  return { render, tree: () => tree, find: pred => find(tree, pred), button: label => find(tree, n => n.props?.children === label), notice: () => find(tree, n => n.type === Feedback) }
}
const settings = { debtThresholdVnd: 100000, updatedAt: '2026-10-14T11:00:00+07:00', lastCheckedAt: null }
const suggestion = { id: 1, cardId: 5, cardNumber: 'TEST-A', readerName: 'Bạn đọc A', status: 'PENDING', reasonCode: 'BOTH', maxOverdueDays: 35, unpaidFeesVnd: '150000', debtThresholdVnd: 100000, currentCardStatus: 'ACTIVE' }
const detail = { suggestion, card: { id: 5, readerName: 'Bạn đọc A', cardNumber: 'TEST-A', status: 'ACTIVE' }, evidence: { overdueItems: [], fees: [] }, currentEvidence: { overdueItems: [], fees: [] }, currentMaxOverdueDays: 35, currentUnpaidFeesVnd: '150000', currentDebtThresholdVnd: 100000, currentReasonCode: 'BOTH' }
test('threshold accepts integer VND, zero and upper boundary; rejects fractions and negatives', () => {
  for (const [raw, expected] of [['0', 0], ['100000', 100000], ['1000000000000', 1e12], ['', null], ['-1', null], ['1.5', null], ['1e5', null], ['1000000000001', null]]) assert.equal(helpers.parseDebtThreshold(raw), expected)
})
test('large total debt formats without losing integer precision', () => {
  assert.equal(helpers.suggestionMoney('9007199254740993'), '9.007.199.254.740.993 ₫')
})
for (const role of ['ADMIN', 'LIBRARIAN', 'READER']) test(`${role} sees permission state and makes no suggestion API request`, () => {
  let calls = 0
  const p = page('CardLockSuggestionsPage.tsx', { list() { calls++ } }, {}, role)
  assert.match(text(p.tree()), /Chỉ Quản lý thư viện/); assert.equal(calls, 0)
})
test('settings update succeeds and notice can dismiss without losing current threshold', async () => {
  let amount
  const p = page('CardLockSuggestionSettingsPage.tsx', { settings: async () => settings, updateSettings: async value => { amount = value; return { ...settings, debtThresholdVnd: value } } })
  await settle(); p.render()
  p.find(n => n.props?.label === 'Ngưỡng tổng nợ phí cho phép (VND)').props.onChange({ target: { value: '200000' } }); p.render()
  p.find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); await settle(); p.render()
  assert.equal(amount, 200000); assert.equal(p.notice().props.tone, 'success')
  p.notice().props.onDismiss(); p.render(); assert.equal(p.notice(), undefined); assert.match(text(p.tree()), /200\.000/)
})
test('invalid settings show temporary warning and persistent field validation without API write', async () => {
  let writes = 0
  const p = page('CardLockSuggestionSettingsPage.tsx', { settings: async () => settings, updateSettings: async () => { writes++ } })
  await settle(); p.render()
  const input = () => p.find(n => n.props?.label === 'Ngưỡng tổng nợ phí cho phép (VND)')
  input().props.onChange({ target: { value: '-1' } }); p.render()
  p.find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); p.render()
  assert.equal(p.notice().props.tone, 'warning'); p.notice().props.onDismiss(); p.render()
  assert.ok(input().props.error); assert.equal(writes, 0)
})
test('settings API failure supplies error feedback and preserves editable value', async () => {
  const p = page('CardLockSuggestionSettingsPage.tsx', { settings: async () => settings, updateSettings: async () => { throw new Error('Không có quyền cập nhật.') } })
  await settle(); p.render(); p.find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); await settle(); p.render()
  assert.equal(p.notice().props.tone, 'error'); assert.match(p.notice().props.message, /Không có quyền/)
})
test('list displays maximum open days, debt, threshold and both violation reasons', async () => {
  const p = page('CardLockSuggestionsPage.tsx', { list: async () => ({ items: [suggestion], page: 0, size: 20, totalItems: 1, totalPages: 1, settings }) })
  await settle(); p.render()
  assert.equal(p.find(n => n.props?.label === 'Quá hạn lớn nhất').props.value, '35 ngày mở cửa'); assert.equal(p.find(n => n.props?.label === 'Tổng nợ tại lần đánh giá').props.value, '150.000 ₫'); assert.equal(p.find(n => n.props?.label === 'Ngưỡng tại lần đánh giá').props.value, '100.000 ₫')
  assert.match(text(p.tree()), /Quá hạn trên 30 ngày mở cửa và nợ phí vượt ngưỡng/)
})
test('list failure retains retry action after temporary error dismisses', async () => {
  const p = page('CardLockSuggestionsPage.tsx', { list: async () => { throw new Error('Lỗi API') } })
  await settle(); p.render(); assert.equal(p.notice().props.tone, 'error')
  p.notice().props.onDismiss(); p.render(); assert.ok(p.button('Thử lại'))
})
test('ignore requires reason; temporary feedback dismissal does not close form or erase field error', async () => {
  let writes = 0
  const p = page('CardLockSuggestionDetailsDialog.tsx', { detail: async () => detail, ignore: async () => { writes++ } }, { id: 1, onClose() {}, onChanged() {} })
  await settle(); p.render(); p.button('Bỏ qua đề xuất').props.onClick(); p.render()
  p.find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); p.render()
  assert.equal(p.notice().props.tone, 'error'); p.notice().props.onDismiss(); p.render()
  assert.ok(p.find(n => n.props?.id === 'ignore-suggestion-error')); assert.ok(p.button('Xác nhận bỏ qua')); assert.equal(writes, 0)
})
test('ignore records supplied reason and returns the server decision without requesting a card lock', async () => {
  const results = [], writes = []
  const p = page('CardLockSuggestionDetailsDialog.tsx', { detail: async () => detail, ignore: async (id, reason) => { writes.push([id, reason]); return { suggestion: { ...suggestion, status: 'IGNORED' }, card: detail.card, accepted: false, message: 'Đã bỏ qua.' } } }, { id: 1, onClose() {}, onChanged: value => results.push(value) })
  await settle(); p.render(); p.button('Bỏ qua đề xuất').props.onClick(); p.render()
  p.find(n => n.props?.id === 'ignore-suggestion-reason').props.onChange({ target: { value: 'Theo dõi thêm' } }); p.render()
  p.find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); await settle(); p.render()
  assert.deepEqual(writes, [[1, 'Theo dõi thêm']]); assert.equal(results[0].suggestion.status, 'IGNORED')
})
for (const accepted of [true, false]) test(`accept adapter forwards lock duration and respects server recheck (${accepted})`, async () => {
  const writes = [], results = []
  const value = { suggestion: { ...suggestion, status: accepted ? 'ACCEPTED' : 'RESOLVED' }, card: { ...detail.card, status: accepted ? 'LOCKED' : 'ACTIVE' }, accepted, message: accepted ? 'Đã khóa.' : 'Đã thanh toán; không khóa.' }
  const p = page('CardLockSuggestionDetailsDialog.tsx', { detail: async () => detail, accept: async (...args) => { writes.push(args); return value } }, { id: 1, onClose() {}, onChanged: result => results.push(result) })
  await settle(); p.render(); p.button('Chấp nhận khóa').props.onClick(); p.render()
  const dialog = p.find(n => n.type === LockDialog)
  assert.match(dialog.props.initialReason, /Quá hạn/)
  const end = '2026-10-21T11:00:00+07:00'
  const card = await dialog.props.onConfirmLock('Xác nhận vi phạm', end); dialog.props.onLocked(card)
  assert.deepEqual(writes, [[1, 'Xác nhận vi phạm', end]]); assert.equal(results[0].accepted, accepted); assert.equal(results[0].card.status, accepted ? 'LOCKED' : 'ACTIVE')
})
test('resolved/ignored proposal detail does not offer a second decision', async () => {
  const p = page('CardLockSuggestionDetailsDialog.tsx', { detail: async () => ({ ...detail, suggestion: { ...suggestion, status: 'IGNORED', decidedBy: 12, deciderName: 'Quản lý A', decidedAt: settings.updatedAt, decisionReason: 'Theo dõi', cardStatusAfterDecision: 'ACTIVE' } }) }, { id: 1, onClose() {}, onChanged() {} })
  await settle(); p.render(); assert.equal(p.button('Chấp nhận khóa'), undefined); assert.equal(p.button('Bỏ qua đề xuất'), undefined)
  assert.match(text(p.tree()), /Quản lý A/); assert.match(text(p.tree()), /Theo dõi/)
})
