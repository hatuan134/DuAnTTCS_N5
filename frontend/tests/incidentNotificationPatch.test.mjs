import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require=createRequire(import.meta.url), react=require('react')
const component=fn=>({__esModule:true,default:fn})
function load(path,imports,globals={}){const ctx={exports:{},require:key=>imports[key]??require(key),...globals};vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/'+path,import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText,ctx);return ctx.exports}
function find(tree,pred){if(!tree||typeof tree!=='object')return null;if(Array.isArray(tree)){for(const n of tree){const found=find(n,pred);if(found)return found}return null}return pred(tree)?tree:find(tree.props?.children,pred)}
function hooks(){const slots=[];let cursor=0;return{begin(){cursor=0},react:{...react,useState(v){const i=cursor++;if(!(i in slots))slots[i]=v;return[slots[i],n=>{slots[i]=typeof n==='function'?n(slots[i]):n}]}}}}
for(const [path,publicHeader] of [['components/layout/Header.tsx',false],['components/public/PublicHeader.tsx',true]])test(`${path}: logout clears session and carries one success notice to home`,()=>{
 const h=hooks();let cleared=0,navigation
 const imports={react:h.react,'react-router-dom':{useNavigate:()=> (...args)=>{navigation=args},Link:()=>null,NavLink:()=>null},'../../core/auth/authStorage':{getCurrentUser:()=>({role:'READER'}),clearAuthSession:()=>cleared++},'../../core/auth/usePublicSession':component(()=>({role:'READER',fullName:'Bình'})),'../ui/Modal':component(()=>null),'../brand/LibraLogo':component(()=>null)}
 const tree=load(path,imports).default({});const button=find(tree,n=>n.type==='button'&&n.props['aria-label']===(publicHeader?'Đăng xuất':'Đăng xuất khỏi hệ thống'));assert.ok(button);button.props.onClick();assert.equal(cleared,1);assert.equal(navigation[0],'/');assert.equal(navigation[1].state.loggedOut,true)
})
test('print failure uses dismissible feedback and preserves committed receipt/dialog',async()=>{
 const h=hooks(),receipt={id:9,receiptNumber:'BL-9',transactionNumber:'GD-9',readerName:'Bình',cardNumber:null,collectedAt:'date',collectorName:'An',totalAmountVnd:10000,items:[]}
 const imports={react:h.react,'../../components/ui/Button':component(()=>null),'../../components/ui/Modal':component(()=>null),'../../components/ui/FeedbackAlert':component(()=>null),'../s3-01-loans/loanService':{formatLoanTimestamp:s=>s},'../s3-07-returns/ReturnFeeDetails':{formatVnd:s=>String(s)},'./feePaymentService':{feeTypeLabel:()=>''}}
 const module=load('features/s4-02-fee-payments/FeeReceiptDialog.tsx',imports,{document:{createElement:()=>({style:{},remove(){},contentWindow:{addEventListener(){},focus(){},print(){throw Error('print failed')}}}),body:{appendChild(frame){frame.onload()}}},window:{setTimeout(){}}})
 const render=()=>{h.begin();return module.default({receipt,onClose(){}})}
 let tree=render();find(tree,n=>n.props?.children==='In biên lai').props.onClick();await new Promise(r=>setImmediate(r));tree=render()
 const feedback=find(tree,n=>n.props?.tone==='error');assert.ok(feedback);feedback.props.onDismiss();tree=render()
 assert.equal(find(tree,n=>n.props?.tone==='error'),null);assert.ok(find(tree,n=>n.props?.receipt?.id===9));assert.ok(find(tree,n=>n.props?.label==='Biên lai BL-9'))
})
