import { createS4Require } from './s4TestImports.mjs'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require=createS4Require(import.meta.url), react=require('react')
const root=new URL('../src/features/s4-03-book-incidents/',import.meta.url)
function load(file,imports={},globals={}) {
  const ctx={exports:{},require:key=>imports[key]??require(key),...globals}
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(file,root),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2022}}).outputText,ctx)
  return ctx.exports
}
const logic=load('incidentService.ts',{'../../core/api/apiClient':{apiClient:{}}})
test('factor validation and Vietnamese decimal',()=>{
  assert.equal(logic.parseFactor('1,5'),1.5);assert.equal(logic.parseFactor('2'),2)
  for(const text of ['', '0','-1','1.234','NaN','1e2','10000000000'])assert.equal(logic.parseFactor(text),null)
})
test('optional photos, boundary size, unsupported format and sixth photo',()=>{
  const file={name:'a.webp',type:'image/webp',size:5*1024*1024}
  assert.equal(logic.validateIncidentFiles([]),'');assert.equal(logic.validateIncidentFiles([file]),'')
  assert.ok(logic.validateIncidentFiles(Array(6).fill(file)));assert.ok(logic.validateIncidentFiles([{...file,size:file.size+1}]))
  assert.ok(logic.validateIncidentFiles([{...file,name:'a.svg',type:'image/svg+xml'}]))
})
test('multipart uses preview token and never sends client price or amount',async()=>{
  let sent;class Form{values=[];append(...args){this.values.push(args)}}
  const m=load('incidentService.ts',{'../../core/api/apiClient':{apiClient:{async post(...args){sent=args;return{data:{id:9}}}}}},{FormData:Form})
  await m.incidentService.confirm({item:{itemId:7},type:'LOST',token:'reviewed'},' Mất ',[])
  assert.equal(sent[0],'/book-incidents');assert.deepEqual(sent[1].values,[['itemId','7'],['type','LOST'],['description','Mất'],['previewToken','reviewed']]);assert.equal(sent[2].headers['Content-Type'],undefined)
})
function nodes(tree,pred,result=[]){if(!tree||typeof tree!=='object')return result;if(Array.isArray(tree)){tree.forEach(n=>nodes(n,pred,result));return result}if(pred(tree))result.push(tree);nodes(tree.props?.children,pred,result);return result}
const item={itemId:1,loanId:2,loanNumber:'PM-2',readerId:3,readerName:'Bình',barcode:'BC-1',bookTitle:'Sách',coverPrice:100000,dueAt:null}
const preview={item,type:'LOST',factor:2,compensationVnd:200000,token:'token',overdueFee:{amountVnd:0,overdueDays:0}}
const record={...preview,id:9,item:{...item,copyStatus:'REMOVED'},photos:[]}
function fixture(role='LIBRARIAN',failure='',pending=false){
 const slots=[];let cursor=0,tree,resolve,payload;const calls=[]
 const hooks={...react,useState(v){const i=cursor++;if(!(i in slots))slots[i]=v;return[slots[i],n=>{slots[i]=typeof n==='function'?n(slots[i]):n}]},useRef(v){const i=cursor++;return slots[i]??={current:v}},useEffect(){}}
 const component=fn=>({__esModule:true,default:fn})
 const imports={react:hooks,'react-router-dom':{Link:()=>null},'../../core/auth/authStorage':{getCurrentUser:()=>({role})},'../s1-02-user-management/accountService':{getApiErrorMessage:e=>e.message},'../s3-01-loans/loanService':{formatLoanTimestamp:s=>s},'../s3-07-returns/ReturnFeeDetails':{formatVnd:s=>`${s} VND`},'./IncidentRecordCard':component(()=>null),'./incidentService':{...logic,incidentService:{async search(){calls.push('search');return[item]},async preview(){calls.push('preview');if(failure==='preview')throw Error('Thiếu giá');return preview},async confirm(...args){payload=args;calls.push('confirm');if(failure==='confirm')throw Error('Phiếu đã đóng');if(pending)return new Promise(r=>{resolve=r});return record},async history(){return[record]}}}}
 for(const name of ['Button','Card','Input','FeedbackAlert','Modal','PageHeader'])imports[`../../components/ui/${name}`]=component(()=>null)
 const page=load('BookIncidentsPage.tsx',imports).default()
 function render(){cursor=0;tree=typeof page.type==='function'?page.type(page.props):page;return tree}render()
 return{render,calls,payload:()=>payload,find:p=>nodes(tree,p)[0],button:s=>nodes(tree,n=>n.props?.children===s)[0],forms:()=>nodes(tree,n=>n.type==='form'),resolve:()=>resolve?.(record)}
}
const event={preventDefault(){}}
async function select(f){await f.forms()[0].props.onSubmit(event);f.render();f.button('Chọn bản sách').props.onClick();f.render()}
function describe(f){f.find(n=>n.type==='textarea').props.onChange({target:{value:'Mất sách khi di chuyển'}});f.render()}
const flush=()=>new Promise(r=>setImmediate(r))
test('unsupported roles cannot mount data workspace',()=>{for(const role of ['READER','UNKNOWN']){const f=fixture(role);assert.deepEqual(f.calls,[]);assert.equal(f.find(n=>n.props?.role==='alert').props.role,'alert')}})
test('manager can view incident history without recording new incidents',()=>{
 for(const role of ['LIBRARY_MANAGER']) { const f=fixture(role);assert.equal(f.forms().length,1);assert.equal(f.button('Tìm phiếu'),undefined);assert.ok(f.button('Xem nhật ký')) }
})

test('description required without sending preview',async()=>{const f=fixture();await select(f);await f.forms()[1].props.onSubmit(event);f.render();assert.deepEqual(f.calls,['search']);assert.ok(f.find(n=>n.props?.id==='description-error'))})
test('preview failure retains form and shows API feedback',async()=>{const f=fixture('LIBRARIAN','preview');await select(f);describe(f);await f.forms()[1].props.onSubmit(event);f.render();assert.ok(f.find(n=>n.props?.message==='Thiếu giá'));assert.ok(f.find(n=>n.type==='textarea'))})
test('success removes processed copy and dismissal preserves result',async()=>{const f=fixture();await select(f);describe(f);await f.forms()[1].props.onSubmit(event);f.render();await f.button('Xác nhận ghi nhận sự cố').props.onClick();await flush();f.render();assert.equal(f.calls.filter(v=>v==='confirm').length,1);assert.equal(f.button('Chọn bản sách'),undefined);f.find(n=>n.props?.tone==='success').props.onDismiss();f.render();assert.ok(f.find(n=>n.props?.record?.id===9))})
test('rapid double confirmation sends one request and keeps busy modal',async()=>{const f=fixture('LIBRARIAN','',true);await select(f);describe(f);await f.forms()[1].props.onSubmit(event);f.render();const b=f.button('Xác nhận ghi nhận sự cố');b.props.onClick();b.props.onClick();f.render();assert.equal(f.calls.filter(v=>v==='confirm').length,1);assert.equal(f.find(n=>n.props?.label==='Xác nhận sự cố sách').props.busy,true);f.resolve();await flush();f.render();assert.ok(f.find(n=>n.props?.record?.id===9))})
test('failed confirmation preserves input and closes stale preview',async()=>{const f=fixture('LIBRARIAN','confirm');await select(f);describe(f);await f.forms()[1].props.onSubmit(event);f.render();await f.button('Xác nhận ghi nhận sự cố').props.onClick();await flush();f.render();assert.ok(f.find(n=>n.props?.message==='Phiếu đã đóng'));assert.equal(f.find(n=>n.type==='textarea').props.value,'Mất sách khi di chuyển');assert.equal(f.find(n=>n.props?.label==='Xác nhận sự cố sách'),undefined)})


test('adjustment accepts whole VND increases, decreases, zero and ceiling; rejects format, amount and reason',()=>{
  for(const amount of ['250000','350000','0','10000000']) assert.equal(logic.validateCompensationAdjustment(300000,amount,' Lý do '),'')
  assert.equal(logic.validateCompensationAdjustment(300000,'300000',''),'')
  for(const amount of ['', '-1','1.5','1e3','NaN','250.000','250,000','10000001']) assert.ok(logic.validateCompensationAdjustment(300000,amount,'Lý do'))
  for(const reason of ['', '  ', '\t\n', 'x'.repeat(1901)]) assert.ok(logic.validateCompensationAdjustment(300000,'250000',reason))
})
test('adjustment sends proposed amount and reason, not the computed snapshot or price',async()=>{
  let sent;class Form{values=[];append(...args){this.values.push(args)}}
  const m=load('incidentService.ts',{'../../core/api/apiClient':{apiClient:{async post(...args){sent=args;return{data:{id:9}}}}}},{FormData:Form})
  await m.incidentService.confirm({item:{itemId:7},type:'DAMAGED',token:'reviewed'},' Hỏng ',[],{amount:'250000',reason:' Lý do '})
  assert.deepEqual(sent[1].values.slice(-2),[['proposedCompensationVnd','250000'],['adjustmentReason','Lý do']])
  assert.equal(sent[1].values.some(([key])=>['coverPrice','factor','automaticCompensationVnd'].includes(key)),false)
})
async function reviewAdjustment(f){await select(f);describe(f);await f.forms()[1].props.onSubmit(event);f.render();f.button('Điều chỉnh số tiền bồi thường').props.onClick();f.render()}
function fillAdjustment(f,amount,reason){f.find(n=>n.props?.label==='Số tiền bồi thường đề xuất (VND) *').props.onChange({target:{value:amount}});f.render();f.find(n=>n.props?.id==='adjustment-reason').props.onChange({target:{value:reason}});f.render()}
test('changed amount requires reason and prevents request until valid',async()=>{
  const f=fixture();await reviewAdjustment(f);fillAdjustment(f,'150000','   ')
  await f.button('Xác nhận ghi nhận sự cố').props.onClick();f.render()
  assert.equal(f.calls.includes('confirm'),false);assert.ok(f.find(n=>n.props?.role==='alert'))
  fillAdjustment(f,'150000','Sách còn phục hồi được');await f.button('Xác nhận ghi nhận sự cố').props.onClick();await flush();f.render()
  assert.equal(f.payload()[3].amount,'150000');assert.equal(f.payload()[3].reason,'Sách còn phục hồi được')
})
test('cancel adjustment preserves incident form, preview and automatic amount',async()=>{
  const f=fixture();await reviewAdjustment(f);fillAdjustment(f,'0','Miễn bồi thường')
  f.button('Hủy điều chỉnh').props.onClick();f.render()
  assert.ok(f.button('Điều chỉnh số tiền bồi thường'));assert.equal(f.find(n=>n.type==='textarea').props.value,'Mất sách khi di chuyển')
  assert.ok(f.find(n=>n.props?.label==='Xác nhận sự cố sách'))
  await f.button('Xác nhận ghi nhận sự cố').props.onClick();await flush()
  assert.equal(f.payload()[3],undefined)
})
