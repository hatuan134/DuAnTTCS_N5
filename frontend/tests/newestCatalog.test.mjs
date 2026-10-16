import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'

function fixture() {
  const requests = []
  const response = { content: [{ id: 11, publicationYear: 1990 }, { id: 7, publicationYear: 2025 }], sort: 'createdAt' }
  const context = { exports: {}, require() { return { apiClient: { async get(url, options) { requests.push({ url, options }); return { data: response } } } } } }
  const source = readFileSync(new URL('../src/features/s1-08-catalog/catalogService.ts', import.meta.url), 'utf8')
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, context)
  return { service: context.exports.catalogService, requests, response }
}

test('hero requests five newest from the public endpoint without availability filtering or client sorting', async () => {
  const { service, requests, response } = fixture()
  const signal = new AbortController().signal
  assert.equal(await service.searchPublicBooks({ keyword: '', page: 0, size: 5, sort: 'createdAt' }, signal), response)
  const { url, options } = requests[0]
  assert.equal(url, '/books/public/search'); assert.equal(options.params.size, 5)
  assert.equal(options.params.sort, 'createdAt'); assert.equal(options.params.page, 0)
  assert.equal(options.params.availableOnly, undefined); assert.equal(options.signal, signal)
  assert.deepEqual(response.content.map(book => book.id), [11, 7])
})

test('existing catalog defaults to ten rows and retains all search filters and cancellation', async () => {
  const { service, requests } = fixture()
  for (const sort of ['relevance', 'publicationYear']) {
    const signal = new AbortController().signal
    await service.searchPublicBooks({ keyword: '  Sách  ', page: 2, sort, categoryId: 6, publicationYear: 2008, availableOnly: true }, signal)
    const { options } = requests.at(-1)
    assert.equal(options.params.size, 10); assert.equal(options.params.keyword, 'Sách')
    assert.equal(options.params.categoryId, 6); assert.equal(options.params.publicationYear, 2008)
    assert.equal(options.params.availableOnly, true); assert.equal(options.params.page, 2)
    assert.equal(options.params.sort, sort); assert.equal(options.signal, signal)
  }
})
