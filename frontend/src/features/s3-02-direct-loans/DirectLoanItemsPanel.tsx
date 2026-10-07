import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { directLoanService } from './directLoanService'
import type { DirectLoanItem, ReaderLoanEligibility } from './directLoanService'

interface DraftRow {
  id: number
  barcode: string
  item: DirectLoanItem | null
  error: string
  checking: boolean
}

export default function DirectLoanItemsPanel({ reader }: { reader: ReaderLoanEligibility }) {
  const [barcode, setBarcode] = useState('')
  const [items, setItems] = useState<DraftRow[]>([])
  const [limit, setLimit] = useState(reader.remainingBooks)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const active = useRef(true)
  const inFlight = useRef(false)
  const nextId = useRef(0)
  const form = useRef<HTMLFormElement>(null)
  const validItems = items.filter((row) => row.item !== null)
  const atLimit = validItems.length >= limit
  const blocked = !reader.eligible || atLimit
  const unresolved = items.filter((row) => row.item === null).length
  const canConfirm = reader.eligible && validItems.length > 0 && validItems.length <= limit
    && unresolved === 0 && !loading && editingId === null && !barcode.trim()
  const editingIndex = items.findIndex((row) => row.id === editingId)

  function focusBarcode() {
    form.current?.querySelector<HTMLInputElement>('input')?.focus()
  }

  useEffect(() => {
    active.current = true
    focusBarcode()
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
    if (items.some((row) => row.id !== editingId && row.barcode === code)) {
      setError('Mã vạch này đã có trong lượt mượn. Mỗi bản sao chỉ được thêm một lần.')
      return
    }
    if (blocked) {
      setError(reader.eligible
        ? `Không thể thêm sách: lượt mượn sẽ vượt giới hạn ${limit} sách. Vui lòng xóa bớt dòng.`
        : reader.message)
      return
    }
    const rowId = editingId ?? ++nextId.current
    const pending: DraftRow = { id: rowId, barcode: code, item: null, error: '', checking: true }
    setItems((rows) => editingId === null ? [...rows, pending] : rows.map((row) => row.id === rowId ? pending : row))
    setBarcode('')
    setEditingId(null)
    inFlight.current = true
    setLoading(true)
    try {
      // Failed lines never consume quota or enter the server's valid draft.
      const item = await directLoanService.previewItem(reader.cardNumber, code, validItems.map((row) => row.barcode))
      if (!active.current) return
      setLimit(item.remainingBooks)
      setItems((rows) => rows.map((row) => row.id === rowId ? { ...row, item, checking: false } : row))
      setNotice(`Đã kiểm tra sách ${item.bookTitle}.`)
    } catch (e: unknown) {
      if (active.current) {
        const message = getApiErrorMessage(e, 'Không kiểm tra được sách. Vui lòng sửa hoặc kiểm tra lại dòng này.')
        setItems((rows) => rows.map((row) => row.id === rowId ? { ...row, error: message, checking: false } : row))
      }
    } finally {
      inFlight.current = false
      if (active.current) {
        setLoading(false)
        focusBarcode()
      }
    }
  }

  function remove(row: DraftRow) {
    if (inFlight.current) return
    setItems((rows) => rows.filter((item) => item.id !== row.id))
    if (editingId === row.id) { setEditingId(null); setBarcode('') }
    setError('')
    setNotice(`Đã xóa mã vạch ${row.barcode} khỏi lượt mượn.`)
    focusBarcode()
  }

  function edit(row: DraftRow) {
    if (inFlight.current || row.item !== null) return
    setEditingId(row.id); setBarcode(row.barcode); setError(''); setNotice('')
    focusBarcode()
  }

  function cancelEdit() {
    if (inFlight.current) return
    setEditingId(null); setBarcode(''); setError(''); setNotice('')
    focusBarcode()
  }

  function confirmDraft() {
    // S3-02.3 confirms the browser draft only; it does not persist a loan.
    if (!canConfirm || inFlight.current) return
    setNotice(`Đã xác nhận danh sách ${validItems.length} sách hợp lệ. Phiếu mượn chưa được ghi.`)
  }

  return <Card className="p-4 sm:p-6">
    <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <h3 className="text-lg font-semibold text-slate-900">Sách trong lượt mượn</h3>
        <p className="mt-1 text-sm leading-6 text-slate-500">Nhập hoặc quét từng mã vạch rồi nhấn Enter hoặc Thêm sách. Dòng lỗi được giữ lại để sửa hoặc xóa.</p>
      </div>
      <p role="status" aria-live="polite" className="shrink-0 self-start rounded-xl bg-blue-50 px-3 py-2 text-sm font-semibold text-blue-800">
        Dự kiến mượn: {validItems.length} / {limit} sách
      </p>
    </div>
    <form ref={form} onSubmit={add} noValidate className="space-y-3">
      {editingId !== null && <div className="rounded-xl border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">
        Đang sửa mã vạch ở dòng {editingIndex + 1}. Nhập mã đúng rồi nhấn Kiểm tra lại.
      </div>}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <div className="min-w-0 flex-1">
          <Input id="direct-loan-barcode" label="Mã vạch sách" required autoComplete="off" maxLength={100}
            placeholder="Nhập mã vạch của bản sao" value={barcode} error={error}
            disabled={!reader.eligible || loading}
            onChange={(event) => { setBarcode(event.target.value); setError(''); setNotice('') }} />
        </div>
        <div className="flex flex-wrap gap-2 sm:mt-7">
          <Button type="submit" loading={loading} disabled={blocked}>{editingId === null ? 'Thêm sách' : 'Kiểm tra lại'}</Button>
          {editingId !== null && <Button type="button" variant="secondary" disabled={loading} onClick={cancelEdit}>Hủy sửa</Button>}
        </div>
      </div>
      <div aria-live="polite" aria-atomic="true">
        {loading && <p role="status" className="text-sm text-blue-700">Đang tìm sách theo mã vạch…</p>}
        {notice && <p role="status" className="break-words text-sm text-emerald-700">{notice}</p>}
      </div>
    </form>
    {blocked && <p role="alert" className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
      {reader.eligible
        ? `Đã đạt giới hạn ${limit} sách hợp lệ trong lượt này. Không thể thêm sách vì sẽ vượt số sách bạn đọc còn được mượn. Xóa một dòng hợp lệ để nhập sách khác.`
        : reader.message}
    </p>}
    {items.length === 0
      ? <p className="mt-5 rounded-xl border border-dashed border-slate-300 p-5 text-sm leading-6 text-slate-500">Chưa có sách trong lượt mượn. {reader.eligible ? 'Nhập mã vạch để thêm sách đầu tiên.' : 'Bạn đọc cần đủ điều kiện mượn trước khi thêm sách.'}</p>
      : <div className="mt-5 overflow-hidden rounded-xl border border-slate-200">
        <table className="w-full table-fixed text-left text-sm">
          <thead className="bg-slate-50 text-slate-600"><tr>
            <th scope="col" className="w-10 px-2 py-3 text-center sm:w-14">STT</th>
            <th scope="col" className="w-[27%] px-2 py-3 sm:px-3">Mã vạch</th>
            <th scope="col" className="px-2 py-3 sm:px-3">Sách / Kết quả kiểm tra</th>
            <th scope="col" className="w-20 px-2 py-3 text-center sm:w-28">Thao tác</th>
          </tr></thead>
          <tbody className="divide-y divide-slate-200" aria-live="polite">
            {items.map((row, index) => <tr key={row.id} className={`align-top ${row.error ? 'bg-red-50/60' : ''}`}>
              <td className="px-2 py-4 text-center text-slate-500">{index + 1}</td>
              <td className="break-all px-2 py-4 font-mono text-xs text-slate-700 sm:px-3 sm:text-sm">{row.barcode}</td>
              <td className="px-2 py-4 [overflow-wrap:anywhere] sm:px-3">
                {row.item && <p className="mb-2 break-words font-medium text-slate-900">{row.item.bookTitle}</p>}
                <span className={`inline-block max-w-full rounded-lg px-2 py-1 text-xs font-semibold ${row.item ? 'bg-emerald-100 text-emerald-800' : row.checking ? 'bg-blue-100 text-blue-800' : 'bg-red-100 text-red-800'}`}>
                  {row.item ? 'Hợp lệ · Sẵn sàng' : row.checking ? 'Đang kiểm tra' : editingId === row.id ? 'Đang sửa · Chưa hợp lệ' : 'Chưa đủ điều kiện'}
                </span>
                {row.error && <p role="alert" className="mt-2 break-words text-xs leading-5 text-red-700">{row.error}</p>}
              </td>
              <td className="px-1 py-3 text-center sm:px-3">
                <div className="flex flex-col items-center gap-1">
                  {!row.item && <Button type="button" variant="secondary" size="sm"
                    aria-label={`Sửa mã vạch ${row.barcode}`} disabled={loading} onClick={() => edit(row)}>Sửa</Button>}
                  <Button type="button" variant="ghost" size="sm" className="text-red-700 hover:bg-red-50 hover:text-red-800"
                    aria-label={`Xóa sách có mã vạch ${row.barcode}`} disabled={loading} onClick={() => remove(row)}>Xóa</Button>
                </div>
              </td>
            </tr>)}
          </tbody>
        </table>
      </div>}
    <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <p role="status" className={`min-w-0 text-sm leading-6 ${unresolved ? 'text-red-700' : 'text-slate-600'}`}>
        {unresolved ? `Còn ${unresolved} dòng chưa hợp lệ. Sửa hoặc xóa các dòng này trước khi xác nhận; vẫn có thể nhập sách khác.`
          : barcode.trim() ? 'Còn mã vạch chưa được kiểm tra. Thêm sách hoặc xóa nội dung ô nhập trước khi xác nhận.'
          : validItems.length ? 'Tất cả các dòng đã nhập đều hợp lệ.' : 'Danh sách cần có ít nhất một sách hợp lệ.'}
      </p>
      <Button type="button" className="shrink-0 self-start" disabled={!canConfirm} onClick={confirmDraft}>Xác nhận danh sách</Button>
    </div>
    <p className="mt-4 text-xs leading-5 text-slate-500">Danh sách tạm thời, chưa ghi phiếu mượn. Xác nhận danh sách chỉ kiểm tra các dòng đã nhập.</p>
  </Card>
}
