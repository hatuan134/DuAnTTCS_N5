import assert from 'node:assert/strict'
import {test} from 'node:test'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import {createRequire} from 'node:module'
const require=createRequire(import.meta.url),react=require('react'),component=fn=>({__esModule:true,default:fn})
function load(file,imports={}){const ctx={exports:{},require:key=>imports[key]??require(key)};vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/s4-03-book-incidents/'+file,import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2022}}).outputText,ctx);return ctx.exports}
function find(tree,pred){if(!tree||typeof tree!=='object')return null;if(Array.isArray(tree)){for(const n of tree){const r=find(n,pred);if(r)return r}return null}return pred(tree)?tree:find(tree.props?.children,pred)}
const logic=load('incidentService.ts',{'../../core/api/apiClient':{apiClient:{}}})
const record={id:9,type:'LOST',compensationVnd:200000,actorName:'Thủ thư',item:{copyStatus:'REMOVED',loanNumber:'PM-1',barcode:'BC-1'},overdueFee:{amountVnd:10000},loanStatus:'LOST'}
const pending={id:7,number:'DC-SC-7',reason:'Tìm lại được sách',caseType:'FOUND',targetStatus:'AVAILABLE',status:'PENDING',requesterName:'Thủ thư',requestedAt:'2026-10-14T00:00:00Z'}
const preview={reversedVnd:200000,cancelledDebtVnd:150000,refundDueVnd:50000,copyStatus:'AVAILABLE',loanStatus:'RETURNED'}
const flush=()=>new Promise(r=>setImmediate(r))
function fixture({role='LIBRARIAN',entries=[],fail='',delay=false}={}){
 const slots=[];let cursor=0,tree,resolve;const calls=[],updated=[]
 const hooks={...react,useState(v){const i=cursor++;if(!(i in slots))slots[i]=v;return[slots[i],n=>slots[i]=typeof n==='function'?n(slots[i]):n]},useRef(v){const i=cursor++;return slots[i]??={current:v}}}
 const api={async adjustments(){calls.push('load');if(fail==='load')throw Error('Lỗi tải lịch sử');return entries},async adjustmentPreview(...args){calls.push(['preview',...args]);if(fail==='preview')throw Error('Lỗi xem trước');return preview},async requestAdjustment(...args){calls.push(['request',...args]);if(fail==='request')throw Error('Lỗi lập bút toán');if(delay)return new Promise(r=>resolve=r);return pending},async confirmAdjustment(...args){calls.push(['confirm',...args]);if(fail==='confirm')throw Error('Lỗi xác nhận');return {...pending,status:'CONFIRMED',...preview}},async detail(){if(fail==='refresh')throw Error('Lỗi tải mới');return {...record,item:{...record.item,copyStatus:'AVAILABLE'},loanStatus:'RETURNED'}}}
 const imports={react:hooks,'../../core/auth/authStorage':{getCurrentUser:()=>({role})},'../s1-02-user-management/accountService':{getApiErrorMessage:e=>e.message},'../s3-01-loans/loanService':{formatLoanTimestamp:s=>s},'../s3-07-returns/ReturnFeeDetails':{formatVnd:n=>String(n)+' VND'},'./incidentService':{...logic,incidentService:api}}
 for(const name of ['Button','Modal','FeedbackAlert'])imports[`../../components/ui/${name}`]=component(()=>null)
 const Panel=load('IncidentAdjustmentPanel.tsx',imports).default
 function render(){cursor=0;tree=Panel({record,onUpdated:r=>updated.push(r)});return tree}render()
 const locate=pred=>find(tree,pred),button=name=>locate(n=>n.props?.children===name)
 async function open(){await button('Lập bút toán điều chỉnh / Xem lịch sử').props.onClick();await flush();render()}
 function reason(text){locate(n=>n.type==='textarea').props.onChange({target:{value:text}});render()}
 async function review(){locate(n=>n.type==='form').props.onSubmit({preventDefault(){}});await flush();render()}
 return{render,find:locate,button,open,reason,review,calls,updated,resolve:()=>resolve?.(pending)}
}
test('request trims reason and never submits money or snapshots from client',async()=>{
 let sent;const m=load('incidentService.ts',{'../../core/api/apiClient':{apiClient:{async post(...args){sent=args;return{data:pending}}}}});await m.incidentService.requestAdjustment(9,' Lý do ','FOUND','AVAILABLE');assert.equal(sent[0],'/book-incidents/9/adjustments');assert.deepEqual(JSON.parse(JSON.stringify(sent[1])),{reason:'Lý do',caseType:'FOUND',targetStatus:'AVAILABLE'})
})
test('blank and oversized reasons remain field errors without preview requests',async()=>{
 for(const value of ['','   ','x'.repeat(1901)]){const f=fixture();await f.open();f.reason(value);await f.review();assert.ok(f.find(n=>n.props?.role==='alert'));assert.equal(f.calls.filter(Array.isArray).length,0)}
})
test('rapid request clicks send once and preserve busy dialog',async()=>{
 const f=fixture({delay:true});await f.open();f.reason('Tìm lại sách');await f.review();const b=f.button('Lập bút toán chờ duyệt');b.props.onClick();b.props.onClick();f.render();assert.equal(f.calls.filter(x=>x[0]==='request').length,1);assert.equal(f.find(n=>n.props?.label==='Xác nhận bút toán điều chỉnh').props.busy,true);f.resolve();await flush();f.render();assert.ok(f.find(n=>n.props?.tone==='success'));assert.equal(f.button('Xem trước và xác nhận bút toán'),null)
})
test('librarian sees pending ledger without confirmation action',async()=>{
 const f=fixture({entries:[pending]});await f.open();assert.ok(f.find(n=>n.props?.children==='Chỉ Quản lý hoặc Admin được xác nhận.'));assert.equal(f.button('Xem trước và xác nhận bút toán'),null)
})
for(const role of ['LIBRARY_MANAGER','ADMIN'])test(role+' confirms after preview and retains ledger after dismissal',async()=>{
 const f=fixture({role,entries:[pending]});await f.open();await f.button('Xem trước và xác nhận bút toán').props.onClick();await flush();f.render();await f.button('Xác nhận đảo giao dịch').props.onClick();await flush();f.render();assert.equal(f.calls.filter(x=>x[0]==='confirm').length,1);const notice=f.find(n=>n.props?.tone==='success');assert.ok(notice);notice.props.onDismiss();f.render();assert.equal(f.find(n=>n.props?.tone==='success'),null);assert.ok(f.find(n=>n.type==='details'));assert.equal(f.updated.length,1)
})
test('preview failure retains reason and form',async()=>{
 const f=fixture({fail:'preview'});await f.open();f.reason('Lý do giữ lại');await f.review();assert.equal(f.find(n=>n.type==='textarea').props.value,'Lý do giữ lại');assert.ok(f.find(n=>n.props?.message==='Lỗi xem trước'));assert.equal(f.find(n=>n.props?.label==='Xác nhận bút toán điều chỉnh'),null)
})
test('failed confirmation retains dialog and retry',async()=>{
 const f=fixture({role:'ADMIN',entries:[pending],fail:'confirm'});await f.open();await f.button('Xem trước và xác nhận bút toán').props.onClick();await flush();f.render();await f.button('Xác nhận đảo giao dịch').props.onClick();await flush();f.render();assert.ok(f.find(n=>n.props?.label==='Xác nhận bút toán điều chỉnh'));assert.ok(f.find(n=>n.props?.message==='Lỗi xác nhận'));assert.ok(f.button('Quay lại'));assert.equal(f.updated.length,0)
})
test('committed request survives failed refresh and explains recovery',async()=>{
 const f=fixture({fail:'refresh'});await f.open();f.reason('Tìm lại sách');await f.review();await f.button('Lập bút toán chờ duyệt').props.onClick();await flush();f.render();assert.ok(f.find(n=>n.props?.message?.startsWith('Bút toán đã lưu.')));assert.ok(f.button('Làm mới lịch sử'));assert.equal(f.find(n=>n.type==='form'),null)
})
test('history error does not fabricate an editable form',async()=>{
 const f=fixture({fail:'load'});await f.open();assert.ok(f.find(n=>n.props?.message==='Lỗi tải lịch sử'));assert.equal(f.find(n=>n.type==='form'),null);assert.ok(f.button('Làm mới lịch sử'))
})
