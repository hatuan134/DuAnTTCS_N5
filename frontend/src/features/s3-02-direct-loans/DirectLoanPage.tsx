import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { loanRoles } from '../s3-01-loans/loanService'
import { directLoanService } from './directLoanService'
import type { ReaderLoanEligibility } from './directLoanService'
import DirectLoanItemsPanel from './DirectLoanItemsPanel'

export default function DirectLoanPage() {
  const allowed = loanRoles.includes(getCurrentUser()?.role ?? '')
  const [cardNumber, setCardNumber] = useState('')
  const [result, setResult] = useState<ReaderLoanEligibility | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [revision, setRevision] = useState(0)
  const requestId = useRef(0)
  const immediate = useRef(false)
  const inFlight = useRef(false)

  useEffect(() => {
    const id = ++requestId.current
    const number = cardNumber.trim()
    const delay = immediate.current ? 0 : 350
    immediate.current = false
    inFlight.current = false
    setResult(null); setError(''); setLoading(false)
    if (!allowed || !number) return
    if (number.length > 100) {
      setError('Mã thẻ không được vượt quá 100 ký tự.')
      return
    }
    setLoading(true)
    const timer = window.setTimeout(() => {
      inFlight.current = true
      directLoanService.checkReader(number)
        .then((data) => { if (id === requestId.current) setResult(data) })
        .catch((e: unknown) => {
          if (id === requestId.current) setError(getApiErrorMessage(e, 'Không kiểm tra được mã thẻ. Vui lòng thử lại.'))
        })
        .finally(() => {
          if (id === requestId.current) { setLoading(false); inFlight.current = false }
        })
    }, delay)
    return () => { window.clearTimeout(timer); ++requestId.current }
  }, [allowed, cardNumber, revision])

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current) return
    if (!cardNumber.trim()) {
      setError('Vui lòng nhập mã thẻ thư viện.')
      return
    }
    immediate.current = true
    setRevision((value) => value + 1)
  }

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">Bạn không có quyền kiểm tra bạn đọc tại quầy.</p>

  return <div className="space-y-5">
    <PageHeader title="Cho mượn tại quầy" description="Nhập hoặc quét mã thẻ để kiểm tra bạn đọc và số sách có thể mượn thêm." />
    <Card className="p-4 sm:p-6">
      <form onSubmit={submit} noValidate className="space-y-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
          <div className="min-w-0 flex-1">
            <Input id="direct-loan-card" label="Mã thẻ thư viện" required autoComplete="off" maxLength={100}
              placeholder="Nhập mã thẻ ghi trên thẻ thư viện" value={cardNumber} error={error}
              onChange={(event) => {
                ++requestId.current
                inFlight.current = false
                setCardNumber(event.target.value); setResult(null); setError(''); setLoading(false)
              }} />
          </div>
          <Button type="submit" variant="secondary" loading={loading} className="sm:mt-7">Kiểm tra thẻ</Button>
        </div>
        <p className="text-xs leading-5 text-slate-500">Thông tin tự cập nhật sau khi ngừng nhập mã thẻ. Có thể nhấn Enter để kiểm tra ngay.</p>
      </form>
      <div aria-live="polite" aria-atomic="true" className="mt-4">
        {loading && <p role="status" className="text-sm text-blue-700">Đang kiểm tra mã thẻ…</p>}
        {!loading && !result && !error && <p className="text-sm text-slate-500">Chưa có bạn đọc được chọn. Vui lòng nhập mã thẻ để bắt đầu.</p>}
      </div>
    </Card>

    {result && <Card className="p-4 sm:p-6">
      <h3 className="mb-4 text-lg font-semibold text-slate-900">Thông tin bạn đọc</h3>
      <dl className="grid gap-4 sm:grid-cols-2">
        <div className="min-w-0"><dt className="text-sm text-slate-500">Tên bạn đọc</dt><dd className="mt-1 break-words font-semibold text-slate-900">{result.readerName}</dd></div>
        <div className="min-w-0"><dt className="text-sm text-slate-500">Loại thẻ</dt><dd className="mt-1 break-words font-semibold text-slate-900">{result.cardTypeName}</dd></div>
        <div className="min-w-0"><dt className="text-sm text-slate-500">Mã thẻ</dt><dd className="mt-1 break-all font-mono text-sm text-slate-900">{result.cardNumber}</dd></div>
        <div><dt className="text-sm text-slate-500">Giới hạn của loại thẻ</dt><dd className="mt-1 font-semibold text-slate-900">{result.maxBooks} sách</dd></div>
      </dl>
      <dl className="mt-5 grid gap-3 sm:grid-cols-2">
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4"><dt className="text-sm text-slate-600">Sách đang mượn chưa trả</dt><dd className="mt-2 text-3xl font-bold text-slate-900">{result.borrowedBooks}</dd></div>
        <div className="rounded-xl border border-blue-200 bg-blue-50 p-4"><dt className="text-sm text-blue-800">Sách còn được mượn thêm</dt><dd className="mt-2 text-3xl font-bold text-blue-800">{result.remainingBooks}</dd></div>
      </dl>
      <div role={result.eligible ? 'status' : 'alert'} className={`mt-4 rounded-xl border p-4 text-sm leading-6 ${result.eligible ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-amber-200 bg-amber-50 text-amber-900'}`}>
        <p className="font-semibold">{result.eligible ? 'Đủ điều kiện mượn' : 'Không đủ điều kiện mượn'}</p>
        <p className="break-words">{result.message}</p>
      </div>
    </Card>}
    {result && <DirectLoanItemsPanel key={`${result.readerId}:${result.cardNumber}:${revision}`} reader={result} />}
    <p className="text-sm leading-6 text-slate-500">Danh sách sách đang nhập chưa được lưu. Khi đổi hoặc kiểm tra lại thẻ, danh sách sẽ bắt đầu lại.</p>
  </div>
}
