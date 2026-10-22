import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const react = require('react')

function load(path, imports = {}, extra = {}) {
  const context = { exports: {}, require: n => imports[n] ?? require(n), ...extra }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}

const path = '../src/features/s4-05-loan-reminders/'

test('unknown reminder fees are distinct from known zero and malformed historical snapshots', () => {
  const { reminderFeeIncomplete } = load(path + 'loanReminderService.ts', { '../../core/api/apiClient': { apiClient: {} } })
  assert.equal(reminderFeeIncomplete('[{"estimatedFeeVnd":0}]'), false)
  assert.equal(reminderFeeIncomplete('[{"estimatedFeeVnd":15000}]'), false)
  for (const value of ['[{"estimatedFeeVnd":null}]', '[{}]', '[]', 'null', 'broken']) assert.equal(reminderFeeIncomplete(value), true)
})

test('reminder history only offers current groups and two send outcomes', () => {
  const hooks = { ...react, useState: value => [value, () => {}], useEffect: () => {}, useCallback: fn => fn }
  const imports = {
    react: hooks,
    './loanReminderService': { loanReminderService: {} },
    '../s1-02-user-management/accountService': { getApiErrorMessage: () => '' },
    './EmailPreviewModal': { __esModule: true, default: () => null },
  }
  for (const name of ['FeedbackAlert', 'PageHeader', 'Button', 'StatusBadge', 'LoadingState', 'EmptyState', 'TablePagination']) {
    imports[`../../components/ui/${name}`] = { __esModule: true, default: name }
  }
  const page = load(path + 'LoanRemindersPage.tsx', imports).default()
  const nodes = []
  function visit(value) {
    if (Array.isArray(value)) return value.forEach(visit)
    if (!value || typeof value !== 'object') return
    nodes.push(value)
    visit(value.props?.children)
    visit(value.props?.action)
  }
  visit(page)
  const header = nodes.find(n => n.props?.title === 'Theo dõi email nhắc trả sách tự động')
  assert.ok(header.props.description.includes('đến khi được trả hoặc xử lý hoàn tất'))
  assert.deepEqual(nodes.filter(n => n.props?.onClick).map(n => n.props.children).flat().filter(x => typeof x === 'string'), ['Làm mới'])
  const options = nodes.filter(n => n.type === 'option')
  assert.ok(options.some(n => n.props.value === 'OVERDUE_DAILY'))
  assert.ok(!nodes.some(n => n.type === 'optgroup'))
  assert.deepEqual(options.filter(n => n.props.value).map(n => n.props.value),
    ['DUE_IN_2_DAYS', 'DUE_TODAY', 'OVERDUE_DAILY', 'SENT', 'FAILED'])
  assert.ok(!JSON.stringify(page).includes('3 lần'))
})

test('send outcome belongs to its own log and uncertain results explain why', () => {
  const { reminderSendResult } = load(path + 'loanReminderService.ts', { '../../core/api/apiClient': { apiClient: {} } })
  assert.equal(reminderSendResult('SENT', 'old failure').reason, null)
  assert.equal(reminderSendResult('FAILED_MAX_RETRIES', 'SMTP từ chối').label, 'Gửi thất bại')
  assert.equal(reminderSendResult('FAILED_MAX_RETRIES', 'SMTP từ chối').reason, 'SMTP từ chối')
  for (const status of ['FAILED', 'PENDING_RETRY', 'PROCESSING', 'REVIEW_REQUIRED', 'UNKNOWN', 'SKIPPED']) {
    assert.equal(reminderSendResult(status).status, 'FAILED')
    assert.ok(reminderSendResult(status).reason)
  }
  assert.match(reminderSendResult('REVIEW_REQUIRED').reason, /Chưa xác định/)
  assert.match(reminderSendResult('PROCESSING').reason, /đang được xử lý/)
})

test('email audit detail omits trigger source and shows failure reasons', () => {
  const logic = load(path + 'loanReminderService.ts', { '../../core/api/apiClient': { apiClient: {} } })
  const imports = {
    react: { ...react, useState: () => ['attempts', () => {}] },
    './loanReminderService': logic,
  }
  for (const name of ['Modal', 'StatusBadge', 'Button']) {
    imports[`../../components/ui/${name}`] = { __esModule: true, default: name }
  }
  const tree = load(path + 'EmailPreviewModal.tsx', imports).default({
    isOpen: true, onClose: () => {}, subject: 'Nhắc hạn', recipientName: 'Bạn đọc',
    recipientEmail: 'reader@example.test', htmlContent: '<p>Nội dung</p>',
    reminderGroup: 'OVERDUE_7_DAYS', status: 'FAILED_MAX_RETRIES', errorMessage: 'SMTP từ chối',
    attempts: [{ id: 1, attemptNumber: 1, attemptedAt: '2026-10-21T07:00:00+07:00',
      status: 'FAILED', errorMessage: 'SMTP từ chối', triggeredBy: 'MANUAL_RETRY', triggeredByUserName: 'Internal actor' }],
  })
  const rendered = JSON.stringify(tree)
  assert.ok(rendered.includes('SMTP từ chối'))
  assert.ok(rendered.includes('Gửi thất bại'))
  for (const hidden of ['Nguồn kích hoạt', 'MANUAL_RETRY', 'Internal actor', 'Lượt gửi đã tạm dừng']) {
    assert.ok(!rendered.includes(hidden))
  }
})

test('loanReminderService API methods target correct endpoints', async () => {
  const calls = []
  const mockClient = {
    get: async (url, options) => {
      calls.push({ method: 'GET', url, options })
      return { data: { ok: true } }
    },
    post: async (url, body) => {
      calls.push({ method: 'POST', url, body })
      return { data: { ok: true } }
    },
  }

  const { loanReminderService } = load(path + 'loanReminderService.ts', {
    '../../core/api/apiClient': { apiClient: mockClient },
  })

  await loanReminderService.getPreview('2026-10-14')
  assert.equal(calls[0].method, 'GET')
  assert.equal(calls[0].url, '/loan-reminders/preview')
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0].options)), { params: { date: '2026-10-14' } })

  await loanReminderService.trigger('2026-10-14')
  assert.equal(calls[1].method, 'POST')
  assert.equal(calls[1].url, '/loan-reminders/trigger')
  assert.deepEqual(JSON.parse(JSON.stringify(calls[1].body)), { targetDate: '2026-10-14' })

  await loanReminderService.getHistory({ date: '2026-10-14', page: 0, size: 10 })
  assert.equal(calls[2].method, 'GET')
  assert.equal(calls[2].url, '/loan-reminders/history')
  assert.deepEqual(JSON.parse(JSON.stringify(calls[2].options)), { params: { date: '2026-10-14', page: 0, size: 10 } })
})

test('feature module registers route and navItem with correct roles', () => {
  const feature = load(path + 'feature.tsx', {
    '../../app/loadablePage': { loadablePage: (fn) => fn },
    'lucide-react': { BellRing: () => null },
  }).default

  assert.equal(feature.id, 's4-05-loan-reminders')
  assert.ok(feature.appRoutes.some(r => r.path === 'loans/reminders'))

  const nav = feature.navItems.find(n => n.to === '/loans/reminders')
  assert.ok(nav)
  assert.equal(nav.label, 'Nhắc nhở đến hạn')
  assert.deepEqual(JSON.parse(JSON.stringify(nav.roles)), ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'])
})
