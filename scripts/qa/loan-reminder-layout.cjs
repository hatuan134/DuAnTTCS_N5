// Local generated HTML only. No app login, network navigation, or SMTP.
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');

async function main() {
  const root = path.resolve(__dirname, '../..');
  const output = path.join(root, 'backend/target/reminder-previews');
  const profile = path.join(output, 'chrome-' + Date.now());
  fs.mkdirSync(profile, { recursive: true });
  const chrome = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
  const child = spawn(chrome, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--disable-background-networking', '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank'],
    { windowsHide: true, stdio: 'ignore' });
  child.on('error', error => { throw error });
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  let ws;
  try {
    const portFile = path.join(profile, 'DevToolsActivePort');
    for (let n = 0; n < 100 && !fs.existsSync(portFile); n++) await sleep(200);
    const [port, endpoint] = fs.readFileSync(portFile, 'utf8').trim().split(/\r?\n/);
    ws = new WebSocket(`ws://127.0.0.1:${port}${endpoint}`);
    await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject });
    let next = 1;
    const pending = new Map();
    ws.onmessage = e => {
      const message = JSON.parse(e.data);
      if (!pending.has(message.id)) return;
      const { resolve, reject, timer } = pending.get(message.id);
      clearTimeout(timer); pending.delete(message.id);
      message.error ? reject(new Error(JSON.stringify(message.error))) : resolve(message.result);
    };
    const send = (method, params = {}, sessionId) => new Promise((resolve, reject) => {
      const id = next++;
      const timer = setTimeout(() => reject(new Error('CDP timeout: ' + method)), 15000);
      pending.set(id, { resolve, reject, timer });
      ws.send(JSON.stringify({ id, method, params, sessionId }));
    });
    const { targetId } = await send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
    const call = (method, params) => send(method, params, sessionId);
    await call('Page.enable');
    const results = [];
    for (const name of ['one-book', 'many-books', 'fee-demo']) {
      const url = pathToFileURL(path.join(output, name + '.html')).href;
      for (const width of [320, 375, 390, 414, 1280]) {
        await call('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: false });
        await call('Page.navigate', { url });
        for (let n = 0; n < 100; n++) {
          const ready = await call('Runtime.evaluate', { expression: 'document.readyState === "complete" && document.querySelectorAll("h2").length > 0', returnByValue: true });
          if (ready.result.value) break;
          await sleep(50);
        }
        const { result } = await call('Runtime.evaluate', { expression: `JSON.stringify({
          viewport: innerWidth, scrollWidth: document.documentElement.scrollWidth,
          cards: document.querySelectorAll('h2').length,
          overflow: [...document.querySelectorAll('td,h1,h2,p,strong')].filter(e =>
            e.getBoundingClientRect().right > innerWidth + 1 || e.scrollWidth > e.clientWidth + 1 && e.clientWidth > 0).length,
          text: document.body.innerText.includes('Mã vạch bản sao') && document.body.innerText.includes('Tổng phí trễ hạn tạm tính')
        })`, returnByValue: true });
        const metrics = JSON.parse(result.value);
        assert.equal(metrics.viewport, width);
        assert.ok(metrics.scrollWidth <= width, `${name} overflows at ${width}`);
        assert.equal(metrics.overflow, 0, `${name}: overflowing elements at ${width}`);
        assert.equal(metrics.cards, name === 'one-book' ? 1 : name === 'fee-demo' ? 2 : 8);
        assert.ok(metrics.text);
        const shot = await call('Page.captureScreenshot', { format: 'png' });
        fs.writeFileSync(path.join(output, `${name}-${width}.png`), Buffer.from(shot.data, 'base64'));
        results.push({ name, width, ...metrics });
      }
    }
    fs.writeFileSync(path.join(output, 'layout-results.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify(results, null, 2));
    await send('Browser.close');
  } finally {
    ws?.close();
    child.kill();
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 });
