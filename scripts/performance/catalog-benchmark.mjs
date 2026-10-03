import { chromium } from 'playwright'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { performance } from 'node:perf_hooks'
import { cpus, totalmem } from 'node:os'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const api = (process.env.S2055_API_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '')
const web = (process.env.S2055_WEB_URL || 'http://localhost:5173').replace(/\/$/, '')
const runs = Number(process.env.S2055_RUNS || 10)
const limitMs = 1500
if (!Number.isInteger(runs) || runs < 1) throw new Error('S2055_RUNS phải là số nguyên dương.')
const label = (process.env.S2055_LABEL || 'after').replace(/[^a-zA-Z0-9_-]/g, '_')
const output = resolve(process.env.S2055_OUTPUT || fileURLToPath(new URL('../../docs/sprint/S2-05.5-results', import.meta.url)), `${label}-${new Date().toISOString().replace(/[:.]/g, '-')}`)
const rows = []
const report = {
  label, startedAt: new Date().toISOString(), api, web, runs, limitMs,
  timing: 'API: before fetch -> parsed JSON; UI: capture click/change -> correct DOM confirmed + two animation frames. No samples discarded.',
  cache: 'Warm cache after fixture preflight; first navigation and typing/filter preparation excluded. Background refresh remains enabled.',
  environment: { node: process.version, platform: process.platform, arch: process.arch, cpu:cpus()[0]?.model, logicalCpus:cpus().length, memoryBytes:totalmem(), commit:process.env.S2055_COMMIT || 'not recorded' },
  status: 'SETUP_FAILED', samples: rows,
}
const norm = (value) => (value || '').normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase().trim().replace(/\s+/g, ' ')
const isbnDigits = (value) => /\p{L}/u.test(value || '') ? '' : (value || '').replace(/[^0-9]/g, '')
const stems = ['Mắt biếc', 'Lập trình', 'Văn học', 'Kinh tế']
const authorName = (n) => `S2055 Nguyễn Nhật Ánh ${String(n).padStart(3, '0')}`
const fixtures = Array.from({ length: 5000 }, (_, i) => {
  const n = i + 1
  const authors = [authorName(1 + i % 100)]
  if (n % 5 === 0) authors.push(authorName(1 + n % 100))
  return { n, isbn: `9799901${String(n).padStart(6, '0')}`, title: `S2055 ${stems[i % 4]} ${String(n).padStart(5, '0')}`,
    authors, categoryName: `S2055 Thể loại ${1 + i % 4}`, publicationYear: 2000 + i % 25,
    copyCount: 3, availableCount: n % 3 === 0 ? 0 : 1 }
})
let categoryIds
let browser
const query = (extra = {}) => ({ keyword: 'S2055', page: 0, size: 20, sort: 'relevance', ...extra })
function params(q) { return new URLSearchParams(Object.entries(q).filter(([,v]) => v !== undefined).map(([k,v]) => [k, String(v)])) }
async function get(path) {
  const start = performance.now()
  const res = await fetch(`${api}${path}`, { signal: AbortSignal.timeout(10000), headers: { 'Cache-Control': 'no-cache' } })
  const data = await res.json()
  const ms = performance.now() - start
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${JSON.stringify(data)}`)
  return { data, ms }
}
function score(value, text, exact, partial) { return value && value === text ? exact : value.includes(text) ? partial : 0 }
function expected(q) {
  const text = norm(q.keyword), isbn = isbnDigits(q.keyword)
  const selected = fixtures.filter((b) => (!q.categoryId || categoryIds[b.categoryName] === q.categoryId)
    && (!q.publicationYear || q.publicationYear === b.publicationYear)
    && (!q.availableOnly || b.availableCount > 0)
    && (!text || norm(b.title).includes(text) || b.authors.some((a) => norm(a).includes(text))
      || (isbn ? isbnDigits(b.isbn).includes(isbn) : norm(b.isbn).includes(text))))
  const relevance = (b) => !text ? 0 : score(norm(b.title), text, 100, 40)
    + Math.max(...b.authors.map((a) => score(norm(a), text, 80, 30)))
    + score(isbn ? isbnDigits(b.isbn) : norm(b.isbn), isbn || text, 120, 20)
  selected.sort((a,b) => {
    const primary = q.sort === 'publicationYear' ? b.publicationYear - a.publicationYear : relevance(b) - relevance(a)
    const at = norm(a.title), bt = norm(b.title)
    return primary || (at < bt ? -1 : at > bt ? 1 : 0) || a.id - b.id
  })
  const totalPages = Math.ceil(selected.length / q.size)
  const page = totalPages ? Math.min(q.page, totalPages - 1) : 0
  return { content: selected.slice(page*q.size, (page+1)*q.size), totalElements: selected.length, totalPages,
    page, size: q.size, sort: q.sort, first: page === 0, last: totalPages === 0 || page === totalPages-1 }
}
function validate(data, q) {
  const exp = expected(q)
  for (const key of ['totalElements','totalPages','page','size','sort','first','last']) assert.equal(data[key], exp[key], key)
  assert.deepEqual(data.content.map((b) => b.id), exp.content.map((b) => b.id), 'IDs and global sort order')
  for (let i=0;i<data.content.length;i++) {
    const b = data.content[i], e = exp.content[i]
    for (const key of ['isbn','title','categoryName','publicationYear','copyCount','availableCount']) assert.equal(b[key], e[key], `${b.id}.${key}`)
    assert.equal(b.hasCopies, true)
    assert.deepEqual(b.authors.map((a) => a.name).sort(), [...e.authors].sort(), `authors:${b.id}`)
  }
  return exp
}
async function preflight() {
  const { data: options } = await get('/books/public/filters')
  categoryIds = Object.fromEntries(options.categories.map((c) => [c.name, c.id]))
  for (let n=1;n<=4;n++) assert.ok(categoryIds[`S2055 Thể loại ${n}`], 'Thiếu thể loại dữ liệu mẫu.')
  const seen = new Set()
  const byIsbn = new Map(fixtures.map((b) => [b.isbn, b]))
  for (let page=0;page<250;page++) {
    const { data } = await get(`/books/public/search?${params(query({ page }))}`)
    assert.equal(data.totalElements, 5000, 'Phải đủ đúng 5000 đầu sách S2055; chạy seed trước.')
    assert.equal(data.page, page)
    for (const b of data.content) {
      const e = byIsbn.get(b.isbn)
      assert.ok(e, `Sách ngoài fixture: ${b.isbn}`)
      assert.ok(!seen.has(b.id), 'Trùng ID giữa các trang')
      seen.add(b.id); e.id = b.id
      for (const key of ['title','categoryName','publicationYear','copyCount','availableCount']) assert.equal(b[key], e[key], `fixture:${b.isbn}.${key}`)
      assert.deepEqual(b.authors.map((a) => a.name).sort(), [...e.authors].sort())
    }
  }
  assert.equal(seen.size, 5000)
  report.fixture = { books: 5000, authors: 100, categories: 4, copies: 15000 }
}
async function ready(page) { await page.getByRole('button', { name: 'Tra cứu', exact: true }).waitFor(); await assertEnabled(page) }
async function assertEnabled(page) { await page.waitForFunction(() => !document.querySelector('button[type="submit"]')?.disabled) }
async function fill(page, q) {
  await ready(page)
  await page.getByRole('searchbox').fill(q.keyword)
  await page.locator('#catalog-category').selectOption(q.categoryId ? String(q.categoryId) : '')
  await page.locator('#catalog-year').selectOption(q.publicationYear ? String(q.publicationYear) : '')
  await page.getByRole('checkbox').setChecked(Boolean(q.availableOnly))
}
function matches(url, q) {
  const u = new URL(url)
  if (u.pathname !== new URL(`${api}/books/public/search`).pathname) return false
  return u.origin === new URL(api).origin && (u.searchParams.get('keyword') || '') === q.keyword
    && Number(u.searchParams.get('page') || 0) === q.page && (u.searchParams.get('sort') || 'relevance') === q.sort
    && Number(u.searchParams.get('categoryId') || 0) === (q.categoryId || 0)
    && Number(u.searchParams.get('publicationYear') || 0) === (q.publicationYear || 0)
    && (u.searchParams.get('availableOnly') === 'true') === Boolean(q.availableOnly)
}
async function arm(page, selector, event) {
  await page.evaluate(({selector,event}) => {
    window.__s2055Start = undefined
    const handler = (e) => {
      if (e.target instanceof Element && e.target.closest(selector)) {
        window.__s2055Start = performance.now(); document.removeEventListener(event, handler, true)
      }
    }
    document.addEventListener(event, handler, true)
  }, { selector, event })
}
async function resultVisible(page, data) {
  await page.waitForFunction(({ids,total}) => {
    if (document.querySelector('[role="alert"]') || document.querySelector('button[type="submit"]')?.disabled) return false
    const actual = Array.from(document.querySelectorAll('article a')).map((a) => Number(a.getAttribute('href').split('/').at(-1)))
    return JSON.stringify(actual) === JSON.stringify(ids)
      && document.body.innerText.includes(`${total} đầu sách phù hợp`)
  }, { ids: data.content.map((b) => b.id), total: data.totalElements }, { timeout: 10000 })
}
async function action(page, q, selector, event, perform, measured) {
  if (measured) await arm(page, selector, event)
  const responsePromise = page.waitForResponse((r) => matches(r.url(), q), { timeout: 10000 })
  await perform()
  const res = await responsePromise
  const data = await res.json()
  assert.equal(res.status(), 200)
  validate(data,q)
  await resultVisible(page,data)
  await res.finished()
  const ms = measured ? await page.evaluate(() => new Promise((resolve, reject) => requestAnimationFrame(() => requestAnimationFrame(() => {
    if (typeof window.__s2055Start !== 'number') { reject(new Error('Missing user-event start marker')); return }
    resolve(performance.now() - window.__s2055Start)
  })))) : undefined
  return { ms, data, networkMs: res.request().timing().responseEnd }
}
async function apply(page,q) {
  await fill(page,q)
  // Set sort using the page's existing change handler (also applies the filled filters).
  const current = await page.locator('#catalog-sort').inputValue()
  if (current !== q.sort) await action(page,q,'#catalog-sort','change',() => page.locator('#catalog-sort').selectOption(q.sort),false)
  else await action(page,q,'button[type="submit"]','click',() => page.getByRole('button',{name:'Tra cứu',exact:true}).click(),false)
}
const scenarios = [
  { id:'01-title', q:query({keyword:'S2055 Mắt biếc'}) },
  { id:'02-author', q:query({keyword:'S2055 Nguyễn Nhật Ánh 001'}) },
  { id:'03-isbn', q:query({keyword:'9799901000001'}) },
  { id:'04-no-accents', q:query({keyword:'s2055 mat biec'}) },
  { id:'05-combined', q:query({keyword:'S2055',publicationYear:2020,availableOnly:true}), categoryName:'S2055 Thể loại 1' },
  { id:'06-next-page', q:query({keyword:'S2055 Mắt biếc',page:1}), action:'next' },
  { id:'07-relevance', q:query({keyword:'S2055 Nguyễn Nhật Ánh 001'}), action:'sort' },
  { id:'08-publication-year', q:query({keyword:'S2055 Mắt biếc',sort:'publicationYear'}), action:'sort' },
]
try {
  console.log('Preflight: validating 5000 books and 15000 copies...')
  await preflight()
  scenarios.find((s) => s.categoryName).q.categoryId = categoryIds['S2055 Thể loại 1']
  browser = await chromium.launch({ headless: process.env.S2055_HEADLESS !== 'false' })
  report.environment.browser = browser.version()
  const page = await browser.newPage({ viewport:{width:1366,height:768} })
  page.setDefaultTimeout(10000)
  await page.goto(`${web}/catalog`)
  await ready(page)
  await page.locator('#catalog-category option').filter({hasText:'S2055 Thể loại 1'}).waitFor({state:'attached'})
  for (const scenario of scenarios) {
    for (let iteration=1;iteration<=runs;iteration++) {
      // HTTP samples are diagnostic and checked independently from rendered UI samples.
      const apiRow = {scenario:scenario.id,mode:'api',iteration,query:scenario.q,ms:null,correct:false,pass:false}
      rows.push(apiRow)
      try {
        const {data,ms}=await get(`/books/public/search?${params(scenario.q)}`)
        validate(data,scenario.q);Object.assign(apiRow,{ms,correct:true,pass:ms<limitMs})
      } catch(e) { apiRow.error=String(e) }
      const uiRow = {scenario:scenario.id,mode:'ui',iteration,query:scenario.q,ms:null,correct:false,pass:false}
      rows.push(uiRow)
      try {
        let measured
        if (scenario.action === 'next') {
          await apply(page,{...scenario.q,page:0})
          measured=await action(page,scenario.q,'nav button:last-child','click',() => page.getByRole('button',{name:'Trang sau'}).click(),true)
        } else if (scenario.action === 'sort') {
          await apply(page,{...scenario.q,sort:scenario.q.sort==='relevance'?'publicationYear':'relevance'})
          measured=await action(page,scenario.q,'#catalog-sort','change',() => page.locator('#catalog-sort').selectOption(scenario.q.sort),true)
        } else {
          await apply(page,query({keyword:'S2055',sort:scenario.q.sort}))
          await fill(page,scenario.q)
          measured=await action(page,scenario.q,'button[type="submit"]','click',() => page.getByRole('button',{name:'Tra cứu',exact:true}).click(),true)
        }
        Object.assign(uiRow,{ms:measured.ms,networkMs:measured.networkMs,correct:true,pass:measured.ms<limitMs})
      } catch(e) {uiRow.error=String(e)}
      console.log(`${scenario.id} #${iteration}: API=${apiRow.ms?.toFixed(1) ?? 'ERROR'}ms UI=${uiRow.ms?.toFixed(1) ?? 'ERROR'}ms ${apiRow.pass&&uiRow.pass?'PASS':'FAIL'}`)
    }
  }
  report.status=rows.every((r) => r.pass)?'PASS':'FAIL'
  report.summary=scenarios.map((s) => ({scenario:s.id,...Object.fromEntries(['api','ui'].map((mode) => {
    const samples=rows.filter((r) => r.scenario===s.id&&r.mode===mode)
    const times=samples.map((r) => r.ms).filter(Number.isFinite).sort((a,b) => a-b)
    return [mode,{samples:samples.length,failures:samples.filter((r) => !r.pass).length,
      maxMs:times.length?Math.max(...times):null,p95Ms:times.length?times[Math.ceil(times.length*.95)-1]:null}]
  }))}))
} catch(e) {report.error=String(e);console.error(e)}
finally {
  if (browser) await browser.close()
  report.finishedAt=new Date().toISOString()
  await mkdir(output,{recursive:true})
  await writeFile(resolve(output,'results.json'),JSON.stringify(report,null,2))
  const cell=(s) => `"${String(s??'').replace(/"/g,'""')}"`
  await writeFile(resolve(output,'results.csv'),['scenario,mode,iteration,ms,networkMs,correct,pass,error',
    ...rows.map((r) => [r.scenario,r.mode,r.iteration,r.ms,r.networkMs,r.correct,r.pass,r.error].map(cell).join(','))].join('\n'))
  console.log(`Result: ${report.status}. Reports: ${output}`)
  process.exitCode=report.status==='PASS'?0:1
}
