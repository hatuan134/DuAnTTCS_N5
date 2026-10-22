import { createRequire } from 'node:module'

// Existing tests render feature components shallowly. Keep new shared UI display-only here;
// its timer is exercised separately in s4FeedbackFields.test.mjs.
export function createS4Require(url) {
  const native = createRequire(url)
  const runtime = native('react/jsx-runtime')
  const field = Symbol('TimedFieldMessage')
  // Route code is lazy-loaded in production. The test harness verifies feature
  // metadata without importing every page and accidentally mounting network calls.
  const loadablePage = () => Object.assign(() => null, { displayName: 'LoadablePage' })
  const jsx = factory => (type, props, key) => type === field
    ? factory('p', { ...props, role: 'alert' }, key) : factory(type, props, key)
  return Object.assign(name => name === 'react/jsx-runtime'
    ? { ...runtime, jsx: jsx(runtime.jsx), jsxs: jsx(runtime.jsxs) }
    : name === '../../app/loadablePage' || name.endsWith('/app/loadablePage')
      ? { loadablePage }
    : name.endsWith('/TimedFieldMessage') || name === './TimedFieldMessage'
      ? { __esModule: true, default: field }
      : name.endsWith('/LoanLookupPanel') || name === './LoanLookupPanel'
        ? { __esModule: true, default: () => null }
        : name.endsWith('/deposits/DepositPage')
          ? { InlineDeposits: () => null }
          : native(name), native)
}
