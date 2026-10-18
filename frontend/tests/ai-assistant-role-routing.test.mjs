import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
const source = readFileSync(new URL('../src/features/ai-assistant/navigation.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const mod={exports:{}}
new Function('exports','module',js)(mod.exports,mod)
const {allowedAiPath:allowed}=mod.exports

test('guest and reader cannot open staff routes',()=>{
  for(const href of ['/dashboard','/users','/readers','/fee-payments','/library-cards','/reservations/ready-for-pickup']){
    assert.equal(allowed(href,'PUBLIC'),false,href)
    assert.equal(allowed(href,'READER'),false,href)
  }
})
test('staff can reach relevant pages and fee collection stays restricted',()=>{
  for(const role of ['LIBRARIAN','LIBRARY_MANAGER','ADMIN']){
    assert.equal(allowed('/authors',role),true)
    assert.equal(allowed('/categories',role),true)
    assert.equal(allowed('/reservations/ready-for-pickup',role),true)
  }
  assert.equal(allowed('/fee-payments','LIBRARY_MANAGER'),false)
  assert.equal(allowed('/fee-payments','ADMIN'),true)
  assert.equal(allowed('/warehouse-shelves','LIBRARIAN'),false)
  assert.equal(allowed('/warehouse-shelves','LIBRARY_MANAGER'),true)
})
test('unknown links and query injections remain blocked',()=>{
  assert.equal(allowed('https://evil.test/users','ADMIN'),false)
  assert.equal(allowed('/users?role=ADMIN','ADMIN'),false)
  assert.equal(allowed('/compensation-settings','READER'),false)
})
