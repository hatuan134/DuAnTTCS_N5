import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'

const require = createRequire(import.meta.url)
const ts = require('typescript')
const content = readFileSync(new URL('../src/features/s1-03-reader-registration/passwordPolicy.ts', import.meta.url), 'utf8')
const output = ts.transpileModule(content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
const scope = { exports: {} }
vm.runInNewContext(output, scope)
const validate = scope.exports.validateRegistrationPassword

test('S1-03: at least 8 characters, at least one letter and one digit', () => {
  assert.match(validate('abc1234'), /tối thiểu 8/)
  assert.match(validate('12345678'), /chữ cái/)
  assert.match(validate('abcdefgh'), /chữ số/)
  assert.equal(validate('abc12345'), null)
  assert.equal(validate('Mậtkhẩu123'), null)
  assert.equal(validate('A1' + 'x'.repeat(150)), null)
})
