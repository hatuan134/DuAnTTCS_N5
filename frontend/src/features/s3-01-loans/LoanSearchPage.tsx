import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Search } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp, loanRoles, loanService } from './loanService'
import type { LoanSearchResult } from './loanService'

function loanStatus(status: LoanSearchResult['status']) {
  switch (status) {
    case 'RETURNED': return 'Đã trả'
    case 'PARTIALLY_RETURNED': return 'Đang mượn · Đã trả một phần'
    case 'BORROWED': return 'Đang mượn'
    default: return 'Chưa có bản sao'
  }
}

export default function LoanSearchPage() {
  const allowed = loanRoles.includes(getCurrentUser()?.role ?? '')
  const [code, setCode] = useState('')
  const [inputError, setInputError] = useState('')
  const [requestError, setRequestError] = useState('')
  const [results, setResults] = useState<LoanSearchResult[]>([])
  const [lastCode, setLastCode] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const requestSequence = useRef(0)

  useEffect(() => () => { requestSequence.current += 1 }, [])

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!allowed || loading) return
    const normalized = code.trim()
    setInputError('')
    setRequestError('')
    setLastCode(null)
    setResults([])
    if (!normalized || normalized.length > 100) {
      setInputError('Vui lòng nhập mã thẻ, mã vạch hoặc mã phiếu từ 1 đến 100 ký tự.')
      return
    }
    const sequence = ++requestSequence.current
    setLoading(true)
    try {
      const data = await loanService.search(normalized)
      if (sequence === requestSequence.current) {
        setResults(data)
        setLastCode(normalized)
      }
    } catch (error: unknown) {
      if (sequence === requestSequence.current) {
        setRequestError(getApiErrorMessage(error, 'Không tra cứu được phiếu mượn. Vui lòng thử lại.'))
      }
    } finally {
      if (sequence === requestSequence.current) setLoading(false)
    }
  }

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
    Bạn không có quyền tra cứu phiếu mượn.
  </p>

  return <div className="space-y-5">
    <PageHeader title="Tra cứu phiếu mượn" description="Dùng cùng một ô để tra cứu bằng mã thẻ thư viện, mã vạch bản sao hoặc mã phiếu mượn." />
    <Card className="p-5 sm:p-6">
      <form noValidate onSubmit={(event) => { void search(event) }} className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <Input
            label="Mã cần tra cứu" required value={code} maxLength={101}
            placeholder="Nhập mã thẻ, mã vạch hoặc mã phiếu mượn"
            startIcon={<Search size={18} />} error={inputError}
            onChange={(event) => {
              ++requestSequence.current
              setCode(event.target.value)
              setInputError('')
              setRequestError('')
              setLastCode(null)
              setResults([])
              setLoading(false)
            }}
          />
        </div>
        <Button type="submit" loading={loading} className="w-full sm:w-auto">
          <Search size={17} />Tra cứu
        </Button>
      </form>
      <p className="mt-3 text-xs text-slate-500">Có thể nhập mã có khoảng trắng ở đầu hoặc cuối; hệ thống sẽ tự loại bỏ khoảng trắng đó.</p>
    </Card>
    {requestError && <FeedbackAlert message={requestError} tone="error" onDismiss={() => setRequestError('')} />}
    {loading && <div role="status"><LoadingState /></div>}
    {!loading && lastCode !== null && <section aria-label="Kết quả tra cứu" className="space-y-4">
      <h2 className="text-base font-semibold text-slate-900">
        Kết quả tra cứu: <span className="break-all font-mono text-blue-700">{lastCode}</span>
        <span className="ml-2 text-sm font-normal text-slate-500">({results.length} phiếu mượn)</span>
      </h2>
      {results.length === 0 && <Card className="p-5 text-sm text-slate-700" >Không tìm thấy phiếu mượn.</Card>}
      {results.map((loan, index) => <Card key={loan.id} className="overflow-hidden">
        <div className="grid items-start gap-4 border-b border-slate-200 bg-slate-50 px-4 py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:px-5">
          <div className="min-w-0">
            <p className="text-xs font-semibold text-slate-500">Phiếu {index + 1}</p>
            <Link to={`/loans/${loan.id}`} className="mt-1 block w-fit max-w-full break-all font-mono text-base font-bold text-blue-700 hover:underline">
              {loan.loanNumber}
            </Link>
            <dl className="mt-3 grid gap-x-4 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
              <div className="min-w-0"><dt className="text-slate-500">Mã thẻ</dt><dd className="mt-1 break-all font-mono font-medium text-slate-900">{loan.cardNumber || 'Chưa có thẻ'}</dd></div>
              <div className="min-w-0"><dt className="text-slate-500">Bạn đọc</dt><dd className="mt-1 break-words font-medium text-slate-900">{loan.readerName}</dd></div>
              <div><dt className="text-slate-500">Ngày mượn</dt><dd className="mt-1 font-medium text-slate-900">{formatLoanTimestamp(loan.borrowedAt, true)}</dd></div>
              <div><dt className="text-slate-500">Trạng thái phiếu</dt><dd className={`mt-1 font-semibold ${loan.status === 'RETURNED' ? 'text-emerald-700' : 'text-slate-900'}`}>{loanStatus(loan.status)}</dd></div>
            </dl>
          </div>
          <Link to={`/loans/${loan.id}`} className="inline-flex min-h-10 items-center justify-center whitespace-nowrap rounded-xl border border-blue-600 bg-white px-3 text-sm font-semibold text-blue-700 transition hover:bg-blue-50">
            Xem chi tiết
          </Link>
        </div>
        <div className="px-4 py-4 sm:px-5">
          <h3 className="mb-3 text-sm font-semibold text-slate-700">Bản sao trong phiếu ({loan.items.length})</h3>
          {loan.items.length === 0 ? <p className="text-sm text-slate-500">Phiếu chưa có thông tin bản sao.</p>
            : <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
              {loan.items.map((item, itemIndex) => <li key={`${loan.id}-${item.barcode}-${itemIndex}`} className="grid gap-x-4 gap-y-2 p-3 text-sm sm:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)_auto] sm:items-center">
                <div className="min-w-0"><p className="text-xs text-slate-500">Mã vạch</p><p className="mt-1 break-all font-mono font-semibold text-slate-900">{item.barcode}</p></div>
                <div className="min-w-0"><p className="text-xs text-slate-500">Tên sách</p><p className="mt-1 break-words font-medium text-slate-900">{item.bookTitle}</p></div>
                <div><p className="text-xs text-slate-500">Hạn trả</p><p className="mt-1 font-medium text-slate-900">{formatLoanTimestamp(item.dueAt, true)}</p>
                  <p className={`mt-1 text-xs font-semibold ${item.status === 'RETURNED' ? 'text-emerald-700' : 'text-blue-700'}`}>{item.status === 'RETURNED' ? 'Đã trả' : 'Đang mượn'}</p></div>
              </li>)}
            </ul>}
        </div>
      </Card>)}
    </section>}
  </div>
}
