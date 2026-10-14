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
