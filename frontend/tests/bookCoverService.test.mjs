import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'

const require = createRequire(import.meta.url)
const root = new URL('../src/features/s2-10-book-cover/', import.meta.url)
function fixture(fail = false) {
  const messages = []
  const requests = []
  let closes = 0
  const apiClient = {
    defaults: { baseURL: 'http://localhost:8081/api/v1' },
    async post(...args) { requests.push(args); if (fail) throw new Error('upload failed') },
  }
  const context = {
    exports: {}, FormData, Event,
    window: { dispatchEvent(event) { messages.push(event.type) }, BroadcastChannel: true },
    BroadcastChannel: class {
      constructor(name) { assert.equal(name, 'catalog-availability') }
      postMessage(message) { messages.push(message) }
      close() { closes++ }
    },
    require(name) {
      if (name === '../../core/api/apiClient') return { apiClient }
      throw new Error(`Unexpected import ${name}`)
    },
  }
  const source = readFileSync(new URL('bookCoverService.ts', root), 'utf8')
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
  vm.runInNewContext(output, context)
  return { api: context.exports, messages, requests, get closes() { return closes } }
}

test('legacy and versioned thumbnail URLs target the API origin and keep version after /thumbnail', () => {
  const { api } = fixture()
  assert.equal(api.publicBookImageUrl(7, '/api/v1/books/public/7/cover', true),
    'http://localhost:8081/api/v1/books/public/7/cover/thumbnail')
  assert.equal(api.publicBookImageUrl(7, '/api/v1/books/public/7/cover?v=new', true),
    'http://localhost:8081/api/v1/books/public/7/cover/thumbnail?v=new')
})
test('detail uses full original and a changed version produces a changed image source', () => {
  const { api } = fixture()
  assert.equal(api.publicBookImageUrl(7, '/api/v1/books/public/7/cover?v=new', false),
    'http://localhost:8081/api/v1/books/public/7/cover?v=new')
  assert.notEqual(api.publicBookImageUrl(7, '/api/v1/books/public/7/cover?v=old', true),
    api.publicBookImageUrl(7, '/api/v1/books/public/7/cover?v=new', true))
})
test('external and another book URLs are not treated as this book managed cover', () => {
  const { api } = fixture()
  assert.equal(api.publicBookImageUrl(7, 'https://images.example.test/cover.png', true),
    'https://images.example.test/cover.png')
  assert.equal(api.managedCoverPath(7, '/api/v1/books/public/8/cover?v=new'), null)
  assert.equal(api.managedCoverPath(7, '/api/v1/books/public/7/cover-not-a-cover'), null)
})
test('successful upload notifies same tab and the existing catalog channel after POST', async () => {
  const f = fixture()
  await f.api.uploadBookCover(7, new Blob(['new image']))
  assert.equal(f.requests[0][0], '/books/7/cover')
  assert.ok(f.requests[0][1] instanceof FormData)
  assert.equal(f.messages[0], 'catalog-cover-updated')
  assert.equal(f.messages[1].type, 'cover-updated')
  assert.equal(f.messages[1].bookId, 7)
  assert.equal(f.closes, 1)
})
test('failed upload sends no refresh notification', async () => {
  const f = fixture(true)
  await assert.rejects(f.api.uploadBookCover(7, new Blob(['invalid'])), /upload failed/)
  assert.equal(f.messages.length, 0)
})

function renderCover(props) {
  const f = fixture()
  const context = {
    exports: {},
    require(name) { return name === './bookCoverService' ? f.api : require(name) },
  }
  const source = readFileSync(new URL('PublicBookCover.tsx', root), 'utf8')
    .replace('import.meta.env.BASE_URL', "'/'")
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  vm.runInNewContext(output, context)
  const React = require('react')
  return renderToStaticMarkup(React.createElement(context.exports.default, props))
}
test('public list component renders the new versioned thumbnail URL', () => {
  const html = renderCover({ bookId: 7, title: 'Sách A', url: '/api/v1/books/public/7/cover?v=new', thumbnail: true })
  assert.match(html, /\/cover\/thumbnail\?v=new/)
  assert.match(html, /width="160"/)
})
test('public detail component renders original image URL, without thumbnail', () => {
  const html = renderCover({ bookId: 7, title: 'Sách A', url: '/api/v1/books/public/7/cover?v=new' })
  assert.match(html, /\/cover\?v=new/)
  assert.doesNotMatch(html, /\/thumbnail/)
})
test('book without cover keeps shared default image', () => {
  const html = renderCover({ bookId: 8, title: 'Sách B', url: null, thumbnail: true })
  assert.match(html, /\/images\/default-book-cover.svg/)
  assert.match(html, /Ảnh bìa mặc định/)
})
