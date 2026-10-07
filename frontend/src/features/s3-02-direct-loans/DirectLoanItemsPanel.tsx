import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { directLoanService } from './directLoanService'
import type { DirectLoanItem, ReaderLoanEligibility } from './directLoanService'

export default function DirectLoanItemsPanel({ reader }: { reader: ReaderLoanEligibility }) {
  const [barcode, setBarcode] = useState('')
  const [items, setItems] = useState<DirectLoanItem[]>([])
  const [limit, setLimit] = useState(reader.remainingBooks)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(false)
  const active = useRef(true)
  const inFlight = useRef(false)
  const form = useRef<HTMLFormElement>(null)
  const atLimit = items.length >= limit
  const blocked = !reader.eligible || atLimit

  useEffect(() => {
    active.current = true
    form.current?.querySelector<HTMLInputElement>('input')?.focus()
    return () => { active.current = false }
  }, [])

  async function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current) return
    setError(''); setNotice('')
    const code = barcode.trim()
    if (!code || code.length > 100) {
      setError('Vui lòng nhập mã vạch từ 1 đến 100 ký tự.')
      return
    }
    if (items.some((item) => item.barcode === code)) {
      setError('Mã vạch này đã có trong lượt mượn. Mỗi bản sao chỉ được thêm một lần.')
      return
    }
    if (blocked) {
      setError(reader.eligible
        ? `Không thể thêm sách: lượt mượn sẽ vượt giới hạn ${limit} sách. Vui lòng xóa bớt dòng.`
        : reader.message)
      return
    }
    inFlight.current = true
    setLoading(true)
    try {
      const item = await directLoanService.previewItem(reader.cardNumber, code, items.map((row) => row.barcode))
      if (!active.current) return
      setLimit(item.remainingBooks)
      setItems((rows) => [...rows, item])
      setBarcode('')
      setNotice(`Đã thêm sách ${item.bookTitle}.`)
    } catch (e: unknown) {
      if (active.current) setError(getApiErrorMessage(e, 'Không thêm được sách. Vui lòng thử lại.'))
    } finally {
      inFlight.current = false
      if (active.current) {
        setLoading(false)
        form.current?.querySelector<HTMLInputElement>('input')?.focus()
      }
    }
  }

  function remove(code: string) {
    if (inFlight.current) return
    setItems((rows) => rows.filter((row) => row.barcode !== code))
    setError('')
    setNotice(`Đã xóa mã vạch ${code} khỏi lượt mượn.`)
  }

  return <Card className="p-4 sm:p-6">
    <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <h3 className="text-lg font-semibold text-slate-900">Sách trong lượt mượn</h3>
        <p className="mt-1 text-sm leading-6 text-slate-500">Nhập hoặc quét từng mã vạch rồi nhấn Enter hoặc Thêm sách.</p>
      </div>
      <p role="status" aria-live="polite" className="shrink-0 self-start rounded-xl bg-blue-50 px-3 py-2 text-sm font-semibold text-blue-800">
        Dự kiến mượn: {items.length} / {limit} sách
      </p>
    </div>
    <form ref={form} onSubmit={add} noValidate className="space-y-3">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <div className="min-w-0 flex-1">
          <Input id="direct-loan-barcode" label="Mã vạch sách" required autoComplete="off" maxLength={100}
            placeholder="Nhập mã vạch của bản sao" value={barcode} error={error}
            disabled={!reader.eligible || loading}
            onChange={(event) => { setBarcode(event.target.value); setError(''); setNotice('') }} />
        </div>
        <Button type="submit" loading={loading} disabled={blocked} className="sm:mt-7">Thêm sách</Button>
      </div>
      <div aria-live="polite" aria-atomic="true">
        {loading && <p role="status" className="text-sm text-blue-700">Đang tìm sách theo mã vạch…</p>}
        {notice && <p role="status" className="break-words text-sm text-emerald-700">{notice}</p>}
      </div>
    </form>
    {blocked && <p role="alert" className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
      {reader.eligible
        ? `Đã đạt giới hạn ${limit} sách trong lượt này. Không thể thêm sách vì sẽ vượt số sách bạn đọc còn được mượn. Xóa một dòng để nhập sách khác.`
        : reader.message}
    </p>}
    {items.length === 0
      ? <p className="mt-5 rounded-xl border border-dashed border-slate-300 p-5 text-sm leading-6 text-slate-500">Chưa có sách trong lượt mượn. {reader.eligible ? 'Nhập mã vạch để thêm sách đầu tiên.' : 'Bạn đọc cần đủ điều kiện mượn trước khi thêm sách.'}</p>
      : <div className="mt-5 overflow-hidden rounded-xl border border-slate-200">
        <table className="w-full table-fixed text-left text-sm">
          <thead className="bg-slate-50 text-slate-600"><tr>
            <th scope="col" className="w-10 px-2 py-3 text-center sm:w-14">STT</th>
            <th scope="col" className="w-[30%] px-2 py-3 sm:px-3">Mã vạch</th>
            <th scope="col" className="px-2 py-3 sm:px-3">Tên sách</th>
            <th scope="col" className="w-16 px-2 py-3 text-center sm:w-24">Thao tác</th>
          </tr></thead>
          <tbody className="divide-y divide-slate-200">
            {items.map((item, index) => <tr key={item.barcode} className="align-top">
              <td className="px-2 py-4 text-center text-slate-500">{index + 1}</td>
              <td className="break-all px-2 py-4 font-mono text-xs text-slate-700 sm:px-3 sm:text-sm">{item.barcode}</td>
              <td className="break-words px-2 py-4 font-medium text-slate-900 [overflow-wrap:anywhere] sm:px-3">{item.bookTitle}</td>
              <td className="px-1 py-3 text-center sm:px-3">
                <Button type="button" variant="ghost" size="sm" className="text-red-700 hover:bg-red-50 hover:text-red-800"
                  aria-label={`Xóa sách có mã vạch ${item.barcode}`} disabled={loading} onClick={() => remove(item.barcode)}>Xóa</Button>
              </td>
            </tr>)}
          </tbody>
        </table>
      </div>}
    <p className="mt-4 text-xs leading-5 text-slate-500">Danh sách tạm thời, chưa ghi phiếu mượn. Có thể xóa dòng rồi thêm lại mã vạch đó.</p>
  </Card>
}
