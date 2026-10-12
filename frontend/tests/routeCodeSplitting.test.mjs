import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..')
const featureRoot = join(frontend, 'src', 'features')

test('route pages are loaded on demand without eager imports', () => {
  let lazyRoutes = 0
  for (const dir of readdirSync(featureRoot, { withFileTypes: true })) {
    if (!dir.isDirectory()) continue
    const featureFile = join(featureRoot, dir.name, 'feature.tsx')
    if (!existsSync(featureFile)) continue

    const source = readFileSync(featureFile, 'utf8')
    assert.doesNotMatch(source, /^import\s+\w+Page\s+from\s+['"]\.\//m,
      `${dir.name} still eagerly imports a route page`)

    const loadable = [...source.matchAll(/const\s+(\w+Page)\s*=\s*loadablePage\(\(\)\s*=>\s*import\(['"](\.\/[^'"]+)['"]\)\)/g)]
    for (const [, page, sourcePath] of loadable) {
      assert.ok(existsSync(join(featureRoot, dir.name, `${sourcePath}.tsx`)),
        `${dir.name}: import target for ${page} does not exist`)
    }
    lazyRoutes += loadable.length
  }

  assert.ok(lazyRoutes >= 30, `Expected at least 30 lazy route pages, found ${lazyRoutes}`)
})
