// Fixtures only: these covers and records are never imported into the application.
const { chromium } = require(process.env.LIBRA_PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const root = path.resolve(__dirname, '..')
const port = Number(process.env.LIBRA_QA_PORT || 5185)
const out = process.env.LIBRA_QA_OUTPUT || path.join(os.tmpdir(), 'libra-immersive-qa')
fs.mkdirSync(out, { recursive: true })
const records = ['Những miền tri thức', 'Đi tìm ý nghĩa', 'Tư duy thiết kế', 'Vũ trụ trong tầm tay', 'Nghệ thuật đọc sách'].map((title, i) => ({
  id: 55 - i * 11, title, subtitle: null, authorName: ['Nguyễn An', 'Minh Châu', 'Hoàng Nam', 'Hà Linh', 'Trần Anh'][i], authors: [],
  categoryName: 'Sách kiểm thử', categoryId: 1, publicationYear: i === 0 ? 1990 : 2025, createdAt: `2026-10-${11-i}T08:00:00Z`,
  coverImageUrl: `/api/v1/books/public/${55-i*11}/cover?v=qa`, availableCount: i === 0 ? 0 : 2, copyCount: 3, hasCopies: true,
  description: 'Bản ghi mô phỏng chỉ phục vụ QA.', availableCopies: [], queueCount: 0,
}))
function cover(index) {
  const colors = ['#173b5c', '#bb693f', '#335a51', '#333e77', '#ebe1c5']; const light = index === 4 ? '#283f4d' : '#f3ebd5'
  return `<svg xmlns="http://www.w3.org/2000/svg" width="320" height="480" viewBox="0 0 320 480"><rect width="320" height="480" fill="${colors[index]}"/><rect x="18" y="18" width="284" height="444" fill="none" stroke="${light}" opacity=".35"/><text x="36" y="52" font-family="sans-serif" font-size="10" letter-spacing="3" fill="${light}">LIBRA · QA</text><circle cx="240" cy="345" r="145" fill="none" stroke="${light}" opacity=".4" stroke-width="28"/><circle cx="55" cy="300" r="105" fill="none" stroke="${light}" opacity=".22" stroke-width="45"/><text x="35" y="130" font-family="serif" font-size="29" fill="${light}">${['NHỮNG MIỀN','ĐI TÌM','TƯ DUY','VŨ TRỤ','NGHỆ THUẬT'][index]}</text><text x="35" y="172" font-family="serif" font-size="29" fill="${light}">${['TRI THỨC','Ý NGHĨA','THIẾT KẾ','TRONG TẦM TAY','ĐỌC SÁCH'][index]}</text><text x="35" y="427" font-family="sans-serif" font-size="14" fill="${light}">${records[index].authorName}</text></svg>`
}
;(async () => {
  const { createServer } = await import('vite')
  const server = await createServer({ root, server: { host: '127.0.0.1', port, strictPort: true } }); await server.listen()
  const browser = await chromium.launch({ headless: true, executablePath: process.env.LIBRA_BROWSER_EXECUTABLE || undefined,
    args: ['--no-sandbox','--disable-dev-shm-usage','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'] })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  let mode = 'success', count = 5, alternate = false
  const requests = [], errors = [], results = []
  async function route(route) {
    const u = new URL(route.request().url()); const endpoint = u.pathname.replace('/api/v1', '')
    requests.push({ endpoint, params: Object.fromEntries(u.searchParams), method: route.request().method() })
    if (/\/cover/.test(endpoint)) {
      if (mode === 'broken-cover') return route.fulfill({ status: 404, body: '' })
      const id = Number(endpoint.split('/')[3]); return route.fulfill({ contentType: 'image/svg+xml', body: cover(Math.max(0, records.findIndex(b => b.id === id))) })
    }
    let data = [], status = 200
    if (endpoint === '/books/public/search') {
      if (mode === 'error') { status = 503; data = { message: 'QA unavailable' } }
      else {
        if (mode === 'loading') await new Promise(resolve => setTimeout(resolve, 900))
        const items = records.slice(0, count).map(b => ({ ...b, ...(mode === 'no-cover' ? { coverImageUrl: null } : {}) }))
        if (alternate && items.length) items[0] = { ...items[0], id: 66, title: 'Đầu sách vừa bổ sung' }
        data = { content: items, page: 0, size: Number(u.searchParams.get('size') || 10), totalElements: count, totalPages: count ? 1 : 0, first: true, last: true, sort: u.searchParams.get('sort') }
      }
    } else if (endpoint === '/books/public/filters') data = { categories: [], publicationYears: [] }
    else if (/^\/books\/public\/\d+$/.test(endpoint)) data = records.find(b => b.id === Number(endpoint.split('/').pop())) || records[0]
    else if (endpoint.endsWith('/queue')) data = { queueCount: 0 }
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  }
  await context.route('**/api/v1/**', route)
  const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message))
  const base = `http://127.0.0.1:${port}`
  const go = async () => { await page.goto(base); await page.locator('.hero-showcase').waitFor(); await page.waitForTimeout(400) }
  const settled = async () => { await page.locator('.floating-book-link').first().waitFor(); await page.waitForTimeout(180) }
  const check = async (name, fn) => { try { await fn(); results.push({ name, status: 'PASS' }); console.log('PASS', name) } catch (e) { results.push({ name, status: 'FAIL', error: e.message }); console.error('FAIL', name, e.message) } }
  await check('public hero requests only five createdAt records; old publication and zero availability remain visible', async () => {
    await go(); await settled()
    const req = requests.find(r => r.endpoint === '/books/public/search')
    assert.equal(req.params.sort, 'createdAt'); assert.equal(req.params.size, '5'); assert.equal(req.params.page, '0'); assert.equal(req.params.availableOnly, undefined)
    assert.equal(await page.locator('.floating-book-link').count(), 5)
    assert.equal(await page.locator('.floating-book-link').first().getAttribute('href'), '/catalog/books/55')
    assert.equal(await page.locator('img[src*="library-illustration.svg"]').count(), 0)
    assert.equal(requests.some(r => r.endpoint === '/books'), false)
  })
  for (const width of [1440,1024,768,390,375]) await check(`responsive ${width}: visible real books, search, readable info, no horizontal overflow`, async () => {
    await page.setViewportSize({ width, height: 1000 }); await go(); await settled()
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
    const visible = page.locator('.floating-book-link:visible'); assert.equal(await visible.count(), width < 640 ? 3 : 5)
    const links = await visible.all()
    for (const link of links) {
      const box = await link.boundingBox(); assert.ok(box.x >= 0 && box.x + box.width <= width + 1)
      const image = link.locator('img'); assert.ok(await image.getAttribute('alt'))
      assert.ok(await image.evaluate(el => el.complete && el.naturalWidth > 0))
    }
    await page.screenshot({ path: `${out}/landing-${width}.png`, fullPage: true, animations: 'disabled' })
  })
  await check('five clickable covers navigate to their own existing detail route without reload', async () => {
    await page.setViewportSize({ width: 1440, height: 1000 })
    for (const book of records) {
      await go(); await settled(); await page.evaluate(() => { window.__spaMarker = 17 })
      await page.locator(`.floating-book-link[href="/catalog/books/${book.id}"]`).click()
      await page.waitForURL(`**/catalog/books/${book.id}`); assert.equal(await page.evaluate(() => window.__spaMarker), 17)
    }
  })
  await check('hover pauses floating, pointer tilt responds smoothly, leave returns to rest', async () => {
    await go(); await settled(); const link = page.locator('.floating-book-link').first(); await link.scrollIntoViewIfNeeded()
    const box = await link.boundingBox(); await page.mouse.move(box.x + box.width * .8, box.y + box.height * .35); await page.waitForTimeout(280)
    assert.equal(await link.locator('.book-levitation').evaluate(el => getComputedStyle(el).animationPlayState), 'paused')
    assert.ok(Math.abs(await link.evaluate(el => parseFloat(el.style.getPropertyValue('--pointer-y')))) > 1)
    assert.notEqual(await link.locator('.book-lift').evaluate(el => getComputedStyle(el).transform), 'matrix(1, 0, 0, 1, 0, 0)')
    for (let i = 0; i < 12; i++) await page.mouse.move(box.x + box.width * (i % 2 ? .2 : .8), box.y + box.height * .4)
    await page.mouse.move(20, 100); await page.waitForTimeout(650)
    assert.ok(Math.abs(await link.evaluate(el => parseFloat(el.style.getPropertyValue('--pointer-y')))) < .1)
    assert.equal(await link.locator('.book-levitation').evaluate(el => getComputedStyle(el).animationPlayState), 'running')
  })
  await check('keyboard focus is visible and Enter opens the matching book', async () => {
    await go(); await settled(); const link = page.locator('.floating-book-link').first(); await link.focus(); await page.keyboard.press('Tab'); await page.keyboard.press('Shift+Tab')
    assert.equal(await link.evaluate(el => el === document.activeElement), true)
    assert.ok(await link.evaluate(el => parseFloat(getComputedStyle(el).outlineWidth) >= 2))
    await page.keyboard.press('Enter'); await page.waitForURL('**/catalog/books/55')
  })
  await check('one and two books render without duplicates or blank slots', async () => {
    for (const n of [1, 2]) { count = n; await go(); await settled(); assert.equal(await page.locator('.floating-book-link').count(), n); await page.screenshot({ path: `${out}/count-${n}.png`, animations: 'disabled' }) }
    count = 5
  })
  await check('empty library, API failure with retry, and loading never block search', async () => {
    count = 0; await go(); assert.ok(await page.getByText('Những cuốn sách mới sẽ sớm xuất hiện.').isVisible()); assert.equal(await page.locator('.floating-book-link').count(), 0)
    count = 5; mode = 'error'; await go(); assert.ok(await page.getByRole('button', { name: 'Thử lại tủ sách', exact: true }).isVisible())
    mode = 'success'; await page.getByRole('button', { name: 'Thử lại tủ sách', exact: true }).click(); await settled()
    mode = 'loading'; await page.goto(base); await page.getByText('Đang mở những trang sách mới…').waitFor(); assert.ok(await page.locator('#home-keyword').isEnabled()); await settled(); mode = 'success'
  })
  await check('missing and failed cover use existing LIBRA fallback', async () => {
    for (const value of ['no-cover','broken-cover']) { mode = value; await go(); await settled(); await page.waitForTimeout(200); for (const image of await page.locator('.volume-front img:visible').all()) assert.ok((await image.getAttribute('src')).endsWith('default-book-cover.svg')) }
    mode = 'success'
  })
  await check('reload fetches the newly eligible title; search still forwards the keyword', async () => {
    alternate = true; await go(); await settled(); assert.equal(await page.locator('.floating-book-link').first().getAttribute('href'), '/catalog/books/66'); alternate = false
    await page.locator('#home-keyword').fill('Mắt biếc'); await page.getByRole('button', { name: 'Khám phá sách', exact: true }).click(); await page.waitForURL('**/catalog?keyword=*'); assert.equal(new URL(page.url()).searchParams.get('keyword'), 'Mắt biếc')
  })
  await check('WebGL shader compiles; one canvas, bounded resolution, real rendered variation', async () => {
    await go(); await settled(); await page.waitForFunction(() => document.querySelector('.liquid-canvas')?.dataset.renderer === 'webgl', null, { timeout: 10000 })
    assert.equal(await page.locator('canvas').count(), 1)
    const metrics = await page.locator('canvas').evaluate(canvas => ({ width: canvas.width, height: canvas.height, running: canvas.dataset.running }))
    assert.ok(metrics.width * metrics.height <= 905000); assert.equal(metrics.running, 'true')
    await page.screenshot({ path: `${out}/webgl-frame.png` })
    await page.mouse.move(1050, 360); await page.waitForTimeout(300); await page.screenshot({ path: `${out}/webgl-pointer.png` })
    assert.notDeepEqual(fs.readFileSync(`${out}/webgl-frame.png`), fs.readFileSync(`${out}/webgl-pointer.png`))
  })
  await check('pause/resume and offscreen stop the canvas and CSS animation', async () => {
    await go(); await settled(); await page.getByRole('button', { name: 'Tạm dừng chuyển động', exact: true }).click()
    assert.equal(await page.locator('.immersive-hero').getAttribute('data-motion'), 'paused')
    assert.equal(await page.locator('canvas').getAttribute('data-running'), 'false')
    await page.getByRole('button', { name: 'Bật chuyển động', exact: true }).click(); await page.waitForTimeout(60)
    assert.equal(await page.locator('canvas').getAttribute('data-running'), 'true')
    await page.locator('footer').scrollIntoViewIfNeeded(); await page.waitForTimeout(120); assert.equal(await page.locator('canvas').getAttribute('data-running'), 'false')
    await page.evaluate(() => scrollTo(0, 0)); await page.waitForTimeout(120); assert.equal(await page.locator('canvas').getAttribute('data-running'), 'true')
  })
  await check('reduced motion removes WebGL and floating without losing links', async () => {
    await page.emulateMedia({ reducedMotion: 'reduce' }); await go(); await settled()
    assert.equal(await page.locator('canvas').count(), 0)
    assert.equal(await page.locator('.book-levitation').first().evaluate(el => getComputedStyle(el).animationName), 'none')
    await page.locator('.floating-book-link').first().click(); await page.waitForURL('**/catalog/books/55'); await page.emulateMedia({ reducedMotion: 'no-preference' })
  })
  await check('WebGL unavailable still displays books and usable detail links', async () => {
    const fallback = await context.newPage()
    await fallback.addInitScript(() => { const original = HTMLCanvasElement.prototype.getContext; HTMLCanvasElement.prototype.getContext = function (kind, ...args) { return kind === 'webgl' ? null : original.call(this, kind, ...args) } })
    await fallback.goto(base); await fallback.locator('.floating-book-link').first().waitFor(); await fallback.waitForFunction(() => document.querySelector('canvas')?.dataset.renderer === 'fallback')
    await fallback.locator('.floating-book-link').first().click(); await fallback.waitForURL('**/catalog/books/55'); await fallback.close()
  })
  await check('context loss falls back; repeated SPA navigation cleans up canvases', async () => {
    await go(); await settled(); await page.locator('canvas').evaluate(el => el.dispatchEvent(new Event('webglcontextlost', { cancelable: true })))
    assert.equal(await page.locator('canvas').getAttribute('data-renderer'), 'fallback')
    for (let i = 0; i < 3; i++) {
      await page.getByRole('link', { name: 'Tra cứu sách', exact: true }).first().click(); await page.waitForURL('**/catalog'); await page.locator('canvas').waitFor({ state: 'detached' }); assert.equal(await page.locator('canvas').count(), 0)
      await page.getByRole('navigation', { name: 'Điều hướng công khai', exact: true }).getByRole('link', { name: 'Trang chủ', exact: true }).click(); await settled(); assert.equal(await page.locator('canvas').count(), 1)
    }
  })
  await check('direct catalog navigation does not load landing or graphics chunks', async () => {
    const clean = await browser.newContext(); const loaded = []; await clean.route('**/api/v1/**', route)
    const other = await clean.newPage(); other.on('request', req => loaded.push(req.url())); await other.goto(`${base}/catalog`); await other.locator('#catalog-keyword').waitFor({ timeout: 3000 }).catch(() => other.locator('.catalog-search-panel').waitFor())
    assert.equal(loaded.some(url => /liquidRenderer|LiquidParticleBackground|FloatingBook3D|\/public-home\/HomePage/.test(url)), false); await clean.close()
  })
  await check('long title stays bounded through hover and keeps a complete accessible name', async () => {
    const oldTitle = records[0].title; records[0].title = 'Một đầu sách với nhan đề rất dài để kiểm chứng cách hiển thị thông tin đầy đủ mà không xô lệch bố cục thư viện số'
    await go(); await settled(); const link = page.locator('.floating-book-link').first(); const before = await link.boundingBox(); await link.hover(); const after = await link.boundingBox()
    assert.ok(Math.abs(before.height - after.height) < 2); assert.ok((await link.getAttribute('aria-label')).includes(records[0].title)); records[0].title = oldTitle
  })
  await check('touch mode limits rendering quality and opens a book with one tap', async () => {
    const touchContext = await browser.newContext({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true, deviceScaleFactor: 3 }); await touchContext.route('**/api/v1/**', route)
    const touch = await touchContext.newPage(); await touch.goto(base); await touch.locator('.floating-book-link').first().waitFor(); await touch.waitForTimeout(250)
    const pixels = await touch.locator('canvas').evaluate(c => c.width * c.height); assert.ok(pixels <= 305000)
    await touch.locator('.floating-book-link').first().tap(); await touch.waitForURL('**/catalog/books/55'); await touchContext.close()
  })
  await check('hidden document suspends motion and unmount cancels all scene animation frames', async () => {
    const lifeContext = await browser.newContext(); await lifeContext.route('**/api/v1/**', route)
    await lifeContext.addInitScript(() => {
      const request = window.requestAnimationFrame.bind(window), cancel = window.cancelAnimationFrame.bind(window), pending = new Set()
      window.requestAnimationFrame = cb => { const id = request(t => { pending.delete(id); cb(t) }); pending.add(id); return id }
      window.cancelAnimationFrame = id => { pending.delete(id); cancel(id) }
      window.__pendingFrames = () => pending.size
    })
    const life = await lifeContext.newPage(); await life.goto(base); await life.locator('.floating-book-link').first().waitFor(); await life.waitForTimeout(350)
    await life.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: true }); document.dispatchEvent(new Event('visibilitychange')) }); await life.waitForTimeout(100)
    assert.equal(await life.locator('.immersive-hero').getAttribute('data-motion'), 'paused'); assert.equal(await life.evaluate(() => window.__pendingFrames()), 0)
    await life.evaluate(() => { delete document.hidden; document.dispatchEvent(new Event('visibilitychange')) }); await life.waitForTimeout(100)
    assert.equal(await life.locator('.immersive-hero').getAttribute('data-motion'), 'active')
    await life.getByRole('link', { name: 'Tra cứu sách', exact: true }).first().click(); await life.waitForURL('**/catalog'); await life.waitForTimeout(350)
    assert.equal(await life.locator('canvas').count(), 0); assert.equal(await life.evaluate(() => window.__pendingFrames()), 0); await lifeContext.close()
  })
  await check('no runtime errors and no business writes', async () => { assert.deepEqual(errors, []); assert.equal(requests.some(r => r.method !== 'GET'), false) })
  fs.writeFileSync(`${out}/results.json`, JSON.stringify({ results, errors }, null, 2))
  await browser.close(); await server.close(); if (results.some(r => r.status === 'FAIL')) process.exitCode = 1
})().catch(e => { console.error(e); process.exitCode = 1 })
