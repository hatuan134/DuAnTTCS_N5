import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
const context={exports:{},require:()=>({apiClient:{}})}
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/s4-05-loan-reminders/loanReminderService.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,context)
const feedback=context.exports.retryFeedback
for(const [name,input,tone,message] of [
 ['success',{totalProcessed:1,succeededCount:1,stillPendingCount:0,maxRetriesReachedCount:0},'success','1 thành công'],
 ['temporary failure',{totalProcessed:1,succeededCount:0,stillPendingCount:1,maxRetriesReachedCount:0},'info','1 chờ thử lại'],
 ['exhausted retry',{totalProcessed:1,succeededCount:0,stillPendingCount:0,maxRetriesReachedCount:1},'warning','1 hết lượt thử'],
 ['empty queue',{totalProcessed:0,succeededCount:0,stillPendingCount:0,maxRetriesReachedCount:0},'info','Không có email'],
])test(`old retry action with actual backend DTO: ${name}`,()=>{
 const result=feedback(input);assert.equal(result.tone,tone);assert.ok(result.message.includes(message))
})
