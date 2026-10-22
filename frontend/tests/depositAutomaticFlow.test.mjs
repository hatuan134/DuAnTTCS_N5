import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { webcrypto } from 'node:crypto'
import vm from 'node:vm'
import ts from 'typescript'
const require=createRequire(import.meta.url), react=require('react')
const flush=()=>new Promise(resolve=>setImmediate(resolve))
function find(node,predicate) {
  if(!node || typeof node!=='object')return
  if(Array.isArray(node)){for(const item of node){const found=find(item,predicate);if(found)return found}return}
  return predicate(node)?node:find(node.props?.children,predicate)
}
function harness(prepare) {
  let cursor=0,tree,props={mode:'collect',card:'TV-1',barcodes:['A'],disabled:false}
  const slots=[],effects=[],calls=[],transactions=[]
  const hooks={...react,
    useState(initial){const i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return[slots[i],v=>{slots[i]=typeof v==='function'?v(slots[i]):v}]},
    useRef(initial){const i=cursor++;return slots[i]??={current:initial}},
    useEffect(fn,deps){const i=cursor++,old=slots[i];if(!old||deps.some((v,n)=>!Object.is(v,old.deps[n]))){slots[i]={deps};effects.push(()=>{old?.cleanup?.();slots[i].cleanup=fn()})}},
  }
  const modules={react:hooks,
    axios:{isAxiosError:e=>!!e.response},
    '../../core/auth/authStorage':{getCurrentUser:()=>({id:1,role:'ADMIN'})},
    './depositService':{depositService:{prepare:async(card,codes)=>{calls.push([card,codes]);return prepare(card,codes)},transact:async(...args)=>{transactions.push(args);return {kind:'COLLECT',totalVnd:50000,transactionNumber:'COC-1'}}},depositActionAmount:a=>a.requiredVnd,depositKindLabel:{COLLECT:'Thu cọc'}},
    './depositRecovery':{readPendingDeposit:()=>null,savePendingDeposit:()=>{},clearPendingDeposit:()=>{}},
    './DepositPolicyPanel':{__esModule:true,default:'Policy'},
    '../s1-02-user-management/accountService':{getApiErrorMessage:e=>e.message},
    '../s3-07-returns/ReturnFeeDetails':{formatVnd:String},
    '../s3-01-loans/loanService':{formatLoanTimestamp:String},
  }
  const ctx={exports:{},sessionStorage:{},crypto:webcrypto,require:n=>modules[n]??(n.startsWith('../../components/ui/')?{__esModule:true,default:n.split('/').at(-1)}:require(n))}
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/deposits/DepositPage.tsx',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText,ctx)
  function render(next){if(next)props={...props,...next};cursor=0;const wrapper=ctx.exports.InlineDeposits(props);tree=wrapper.type(wrapper.props);for(const effect of effects.splice(0))effect();return tree}
  render()
  return {render,calls,transactions,node:p=>find(tree,p),text:()=>JSON.stringify(tree)}
}
const account=code=>({id:code==='A'?1:2,readerId:7,requiredVnd:50000,bookTitle:code,barcode:code,percentage:50,coverPrice:100000,collectedVnd:0})
test('opening details automatically loads deposit without collecting cash and ignores old responses',async()=>{
  let resolveA
  const h=harness(async(_,codes)=>codes[0]==='A'?new Promise(resolve=>{resolveA=resolve}):[account('B')])
  assert.equal(h.calls.length,1);assert.equal(h.transactions.length,0)
  h.render({barcodes:['B']});await flush();h.render()
  assert.ok(h.text().includes('B'))
  resolveA([account('A')]);await flush();h.render()
  assert.equal(h.node(n=>n.type==='p'&&n.props?.children?.[0]==='A'),undefined)
  assert.equal(h.transactions.length,0)
})
test('changing selected books cancels an unsubmitted confirmation and stale click cannot charge',async()=>{
  const h=harness(async(_,codes)=>codes.map(account));await flush();h.render()
  h.node(n=>n.type==='Button'&&n.props.children==='Thu tổng tiền cọc tại quầy').props.onClick();h.render()
  assert.ok(h.node(n=>n.type==='Button'&&n.props.children==='Xác nhận đã thực hiện'))
  h.render({barcodes:['B']});h.render();await flush();h.render()
  assert.equal(h.node(n=>n.type==='Button'&&n.props.children==='Xác nhận đã thực hiện'),undefined)
  assert.equal(h.transactions.length,0)
})
test('inactive policy is an informative state, not zero money or a payment action',async()=>{
  const h=harness(async()=>{throw {message:'inactive',response:{data:{code:'DEPOSIT_NOT_ACTIVE'}}}})
  await flush();h.render()
  assert.match(h.text(),/Chưa có chính sách cọc có hiệu lực/)
  assert.equal(h.node(n=>n.type==='FeedbackAlert'&&n.props.tone==='error'),undefined)
  assert.equal(h.node(n=>n.type==='Button'&&n.props.children==='Thu tổng tiền cọc tại quầy'),undefined)
})
