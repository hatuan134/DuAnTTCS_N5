import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
function load(path, imports = {}, extra = {}) {
  const context = { exports: {}, require: n => imports[n] ?? require(n), ...extra }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
const helper = load('../src/features/s2-08-my-reservations/pickupLastDay.ts')
const now = Date.parse('2026-10-15T07:00:00+07:00')
for (const [name,status,deadline,expected] of [
  ['today','READY_FOR_PICKUP','2026-10-15T17:00:00+07:00',true],
  ['tomorrow','READY_FOR_PICKUP','2026-10-16T17:00:00+07:00',false],
  ['yesterday','READY_FOR_PICKUP','2026-10-14T17:00:00+07:00',false],
  ['expired before morning','READY_FOR_PICKUP','2026-10-15T06:00:00+07:00',false],
  ['exact expiry','READY_FOR_PICKUP','2026-10-15T07:00:00+07:00',false],
  ['fulfilled','FULFILLED','2026-10-15T17:00:00+07:00',false],
  ['cancelled','CANCELLED','2026-10-15T17:00:00+07:00',false],
  ['invalid','READY_FOR_PICKUP','invalid',false],
  ['missing','READY_FOR_PICKUP',null,false],
  ['UTC stored date','READY_FOR_PICKUP','2026-10-15T10:00:00Z',true],
]) test('reader last-day warning: '+name, () => assert.equal(helper.isLastDayPickup(status,deadline,now),expected))
test('Vietnam midnight uses correct local calendar date', () => assert.equal(helper.vietnamDateKey(Date.parse('2026-10-14T17:00:00Z')),'2026-10-15'))
test('preview and live API use distinct authenticated endpoints and dates', async () => {
  const calls = []
  const api = load('../src/features/s2-09-ready-pickup/readyEmailService.ts', {
    '../../core/api/apiClient': { apiClient: {
      get: async (...args) => { calls.push(args); return { data: {} } },
      post: async (...args) => { calls.push(args); return { data: {} } },
    } },
  }).readyEmailService
  await api.previewLastDay('2026-10-15'); await api.runLastDay('2026-10-15')
  assert.equal(calls[0][0],'/reservations/pickup-last-day-reminders/preview');assert.equal(calls[0][1].params.date,'2026-10-15')
  assert.equal(calls[1][0],'/reservations/pickup-last-day-reminders/run');assert.equal(calls[1][1].date,'2026-10-15')
})
const settle = () => new Promise(resolve => setImmediate(resolve))
const Button = () => null, Feedback = () => null, Confirm = () => null, Stub = () => null
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) { for (const child of node) { const found=find(child,predicate);if(found)return found } return undefined }
  return predicate(node) ? node : find(node.props?.children,predicate)
}
function text(node) { return node == null || typeof node === 'boolean' ? '' : Array.isArray(node) ? node.map(text).join(' ') : typeof node === 'object' ? text(node.props?.children) : String(node) }
function fixture(api) {
  let cursor=0,tree
  const slots=[],effects=[]
  const hooks={...react,
    useState(initial){const i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return [slots[i],v=>slots[i]=typeof v==='function'?v(slots[i]):v]},
    useRef(value){const i=cursor++;return slots[i]??={current:value}},
    useEffect(fn,deps){const i=cursor++,old=slots[i];if(!old||deps.some((v,n)=>!Object.is(v,old.deps[n]))){slots[i]={deps,cleanup:old?.cleanup};effects.push(()=>{old?.cleanup?.();slots[i].cleanup=fn()})}},
  }
  const Component=load('../src/features/s2-09-ready-pickup/PickupLastDayReminderPanel.tsx',{
    react:hooks,'react-router-dom':{Link:Stub},
    '../../components/ui/Button':{__esModule:true,default:Button},
    '../../components/ui/Card':{__esModule:true,default:Stub},
    '../../components/ui/FeedbackAlert':{__esModule:true,default:Feedback},
    '../../components/ui/ConfirmActionDialog':{__esModule:true,default:Confirm},
    '../s1-02-user-management/accountService':{getApiErrorMessage:e=>e.message},
    '../s2-08-my-reservations/pickupLastDay':{vietnamDateKey:()=> '2026-10-15'},
    './pickupService':{formatPickupDate:v=>v},
    './readyEmailService':{readyEmailService:api,pickupEmailLabels:{SENT:'Đã gửi'}},
  }).default
  const render=()=>{cursor=0;tree=Component();effects.splice(0).forEach(fn=>fn());return tree}
  render()
  return {render,tree:()=>tree,button:label=>find(tree,n=>n.type===Button&&text(n).includes(label)),notice:()=>find(tree,n=>n.type===Feedback),confirm:()=>find(tree,n=>n.type===Confirm),date:()=>find(tree,n=>n.props?.id==='pickup-reminder-date')}
}
const report={date:'2026-10-15',evaluatedAt:'2026-10-15T07:00:00+07:00',sendTime:'07:00',timeZone:'Asia/Ho_Chi_Minh',preview:true,queuedCount:0,eligibleCount:1,message:'Đã xem trước',entries:[{reservationId:20,bookTitle:'Cơ sở dữ liệu',readerName:'Bạn đọc A',recipientEmail:'a@example.com',pickupDeadline:'2026-10-15T17:00:00+07:00',pickupLocation:'Quầy 1',eligible:true,reason:'Đủ điều kiện',notificationStatus:null,subject:'Hôm nay là ngày cuối',emailContent:'<p>Nội dung</p>'}]}
test('preview displays deadlines, location and retained results after dismissing temporary notice',async()=>{
  const calls=[],p=fixture({previewLastDay:async date=>{calls.push(date);return report}})
  p.button('Xem trước theo ngày').props.onClick();await settle();p.render()
  assert.deepEqual(calls,['2026-10-15']);assert.match(text(p.tree()),/Cơ sở dữ liệu/);assert.match(text(p.tree()),/Quầy 1/)
  p.notice().props.onDismiss();p.render();assert.equal(p.notice(),undefined);assert.match(text(p.tree()),/Cơ sở dữ liệu/)
})
test('future preview keeps live sending disabled',()=>{
  const p=fixture({});p.date().props.onChange({target:{value:'2026-10-16'}});p.render();assert.equal(p.button('Chạy gửi thật hôm nay').props.disabled,true)
})
test('live sending requires confirmation and prevents duplicate submissions while pending',async()=>{
  let resolve,calls=0
  const p=fixture({runLastDay:()=>{calls++;return new Promise(r=>resolve=r)}})
  p.button('Chạy gửi thật hôm nay').props.onClick();p.render();assert.ok(p.confirm());assert.equal(calls,0)
  p.confirm().props.onConfirm();p.confirm().props.onConfirm();p.render();assert.equal(calls,1);assert.equal(p.button('Xem trước theo ngày').props.disabled,true)
  resolve({...report,preview:false});await settle();p.render();assert.equal(p.confirm(),undefined);assert.ok(p.notice())
})
test('API failure produces dismissible feedback and keeps retry controls usable',async()=>{
  const p=fixture({previewLastDay:async()=>{throw new Error('Lỗi API')}})
  p.button('Xem trước theo ngày').props.onClick();await settle();p.render();assert.equal(p.notice().props.tone,'error')
  p.notice().props.onDismiss();p.render();assert.equal(p.notice(),undefined);assert.equal(p.button('Xem trước theo ngày').props.disabled,false)
})
test('missing date produces warning without API call',()=>{
  const p=fixture({});p.date().props.onChange({target:{value:''}});p.render()
  p.button('Xem trước theo ngày').props.onClick();p.render();assert.equal(p.notice().props.tone,'warning')
})
