import { useEffect, useRef, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { Download, History, RefreshCw, X } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'
import TablePagination from '../../components/ui/TablePagination'
import useTablePagination from '../../hooks/useTablePagination'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { exportExcel, formatMoney, formatTime, getPreview, getFilterOptions, minimumDaysError, saveDownload, type Snapshot, type CardTypeOption, type ReportFilters } from './reportService'

const columns = ['Mã thẻ', 'Họ tên', 'Số điện thoại', 'Số cuốn quá hạn', 'Số ngày trễ dài nhất', 'Tổng phí đang nợ']
export default function OverdueDebtExportPage() {
  const allowed = ['LIBRARY_MANAGER', 'ADMIN'].includes(getCurrentUser()?.role ?? '')
  const [data, setData] = useState<Snapshot | null>(null)
  const [minimum, setMinimum] = useState(''), [cardType, setCardType] = useState('')
  const [options, setOptions] = useState<CardTypeOption[]>([]), [optionsFailed, setOptionsFailed] = useState(false)
  const [loadedKey, setLoadedKey] = useState('')
  const fieldError = minimumDaysError(minimum)
  const filters: ReportFilters = {
    ...(minimum !== '' && !fieldError ? { minOverdueDays: Number(minimum) } : {}),
    ...(cardType !== '' ? { cardTypeId: Number(cardType) } : {}),
  }
  const [loading, setLoading] = useState(true), [failed, setFailed] = useState(false)
  const [revision, setRevision] = useState(0), [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<{ tone: 'error' | 'success'; message: string } | null>(null)
  const pending = useRef(false), mounted = useRef(true)
  const requestKey = `${minimum}|${cardType}|${revision}`
  const previewReady = !fieldError && !loading && !!data && loadedKey === requestKey
  const pagination = useTablePagination(data?.readers ?? [], `${loadedKey}|${data?.snapshotAt ?? ''}`)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  useEffect(() => {
    if (!allowed) return
    const controller = new AbortController()
    setOptionsFailed(false)
    getFilterOptions(controller.signal).then(value => { if (!controller.signal.aborted) setOptions(value) })
      .catch(error => { if (!controller.signal.aborted) {
        setOptionsFailed(true)
        setNotice({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được loại thẻ. Bấm “Làm mới” để thử lại.') })
      } })
    return () => controller.abort()
  }, [allowed, revision])
  useEffect(() => {
    if (!allowed) return
    const controller = new AbortController()
    setLoading(true); setFailed(false); setData(null); setNotice(null)
    if (fieldError) { setLoading(false); return () => controller.abort() }
    getPreview(controller.signal, filters).then(value => { if (!controller.signal.aborted) {
      setData(value); setLoadedKey(requestKey)
    } })
      .catch(error => { if (!controller.signal.aborted) {
        setFailed(true); setNotice({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được danh sách. Vui lòng thử lại.') })
      } }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [allowed, revision, minimum, cardType, fieldError])
  async function download() {
    if (pending.current || !previewReady) return
    pending.current = true; setBusy(true); setNotice(null)
    try {
      const result = await exportExcel(filters)
      if (!mounted.current) return
      saveDownload(result.filename, result.content)
      setNotice({ tone: 'success', message: `Đã tạo ${result.filename} và gửi yêu cầu tải xuống.` })
    } catch (error) {
      if (mounted.current) setNotice({ tone: 'error', message: getApiErrorMessage(error, 'Không hoàn tất được xuất Excel. Xem lịch sử xuất và thử lại.') })
    } finally { pending.current = false; if (mounted.current) setBusy(false) }
  }
  if (!allowed) return <Navigate to="/dashboard" replace />
  return <div className="min-w-0 space-y-5">
    <PageHeader title="Xuất danh sách quá hạn và nợ phí" description="Một dòng cho mỗi bạn đọc đang quá hạn hoặc còn khoản phí chưa thanh toán."
      action={<Button type="button" onClick={() => void download()} loading={busy} disabled={!previewReady}><Download size={16} />Xuất Excel</Button>} />
    <div className="flex flex-wrap items-center justify-between gap-3">
      <Button type="button" variant="secondary" disabled={loading || busy} onClick={() => setRevision(v => v + 1)}><RefreshCw size={16} />Làm mới</Button>
      <Link className="inline-flex min-h-11 items-center gap-2 rounded-xl px-3 text-sm font-semibold text-blue-700 hover:bg-blue-50" to="/reports/overdue-debts/history"><History size={17} />Lịch sử xuất dữ liệu</Link>
    </div>
    <Card className="p-4 sm:p-5">
      <div className="grid items-start gap-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_auto]">
        <div className="min-w-0">
          <label htmlFor="minimum-overdue-days" className="mb-2 block text-sm font-semibold text-slate-700">Số ngày trễ tối thiểu</label>
          <input id="minimum-overdue-days" type="text" inputMode="numeric" value={minimum} disabled={busy}
            placeholder="Không giới hạn" aria-invalid={!!fieldError} aria-describedby={fieldError ? 'minimum-days-error' : 'minimum-days-help'}
            onChange={event => { if (!pending.current) setMinimum(event.target.value) }}
            className={`min-h-11 w-full rounded-xl border bg-white px-3.5 text-sm outline-none focus:ring-4 disabled:bg-slate-100 ${fieldError ? 'border-red-400 focus:ring-red-100' : 'border-slate-300 focus:ring-blue-100'}`} />
          {fieldError ? <p id="minimum-days-error" className="mt-1.5 text-xs font-medium text-red-600">{fieldError}</p>
            : <p id="minimum-days-help" className="mt-1.5 text-xs text-slate-500">Từ ngưỡng đã nhập trở lên, tính theo ngày mở cửa.</p>}
        </div>
        <div className="min-w-0">
          <label htmlFor="report-card-type" className="mb-2 block text-sm font-semibold text-slate-700">Loại thẻ</label>
          <select id="report-card-type" value={cardType} disabled={busy || optionsFailed}
            onChange={event => { if (!pending.current) setCardType(event.target.value) }}
            className="min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3 text-sm outline-none focus:ring-4 focus:ring-blue-100 disabled:bg-slate-100">
            <option value="">Tất cả loại thẻ</option>
            {options.map(option => <option key={option.id} value={option.id}>{option.name}{option.active ? '' : ' (Ngừng áp dụng)'}</option>)}
          </select>
          {optionsFailed && <p className="mt-1.5 text-xs text-red-600">Chưa tải được loại thẻ. Bấm “Làm mới” để thử lại.</p>}
        </div>
        <Button type="button" variant="secondary" className="sm:col-span-2 lg:col-span-1 lg:mt-7" disabled={busy || (minimum === '' && cardType === '')}
          onClick={() => { if (!pending.current) { setMinimum(''); setCardType(''); setRevision(v => v + 1) } }}><X size={16} />Xóa bộ lọc</Button>
      </div>
      <p className="mt-4 text-sm leading-6 text-slate-600">Hai điều kiện được kết hợp đồng thời. Bạn đọc chỉ nợ phí có số ngày trễ là 0; được giữ lại khi bỏ trống hoặc nhập 0.</p>
    </Card>
    {notice && <FeedbackAlert {...notice} onDismiss={() => setNotice(null)} />}
    <Card className="p-4 text-sm leading-6 text-slate-600 sm:p-5">
      <p>Ngày trễ tính theo lịch mở cửa, không tính ngày đến hạn; có tính ngày chốt nếu thư viện mở cửa. Sách đã qua ngày hẹn trả vẫn được đếm là quá hạn khi số ngày mở cửa trễ bằng 0.</p>
      <p className="mt-2">Công nợ gồm mọi khoản phải thu hợp lệ, trừ đã thu và miễn giảm; bỏ khoản bồi thường đã được xác nhận điều chỉnh hủy. Không tự cộng phí dự kiến của sách chưa trả.</p>
      <p className="mt-2">Khi bấm xuất, hệ thống lấy dữ liệu mới và ghi nhật ký. Số liệu có thể khác bản xem trước nếu có trả sách hoặc thanh toán mới. Tệp chứa thông tin cá nhân; chỉ chia sẻ cho người được phép.</p>
    </Card>
    <Card className="min-w-0 overflow-hidden">
      <div className="border-b border-slate-200 p-4 sm:p-5"><h2 className="text-lg font-bold text-slate-900">Danh sách xem trước{data && loadedKey === requestKey ? ` (${data.totalReaders.toLocaleString('vi-VN')} bạn đọc)` : ''}</h2>
        {data && loadedKey === requestKey && <p className="mt-1 text-xs text-slate-500">Chốt lúc {formatTime(data.snapshotAt)} (giờ Việt Nam). Xuất toàn bộ kết quả đã lọc, gồm cả các trang sau.</p>}</div>
      {fieldError ? <p className="p-5 text-sm text-slate-600">Sửa số ngày trễ tối thiểu để xem trước và xuất dữ liệu.</p> : loading || (data && loadedKey !== requestKey) ? <p role="status" className="p-8 text-center text-sm text-slate-500">Đang tổng hợp quá hạn và công nợ...</p>
        : failed ? <p className="p-5 text-sm text-slate-600">Chưa tải được dữ liệu. Kiểm tra kết nối và bấm “Làm mới”.</p>
        : data?.readers.length ? <>
          <div className="space-y-3 p-4 lg:hidden">{pagination.pageItems.map(row => <article key={row.readerId} className="rounded-xl border border-slate-200 p-4">
            <h3 className="break-words font-semibold text-slate-900">{row.fullName}</h3><dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
              {[row.cardNumber ?? 'Chưa cấp thẻ', row.phone ?? 'Chưa có', row.overdueBooks, `${row.maxOverdueDays} ngày mở cửa`, formatMoney(row.outstandingVnd)].map((value, i) => <div key={i} className={i === 4 ? 'col-span-2' : 'min-w-0'}><dt className="text-xs text-slate-500">{columns[[0,2,3,4,5][i]]}</dt><dd className="mt-1 break-words font-medium">{value}</dd></div>)}
            </dl></article>)}</div>
          <table className="hidden w-full table-fixed text-left text-sm lg:table"><thead className="bg-slate-50 text-slate-600"><tr>{columns.map((name, i) => <th scope="col" key={name} className={`px-3 py-3 font-semibold ${i > 2 ? 'text-right' : ''}`}>{name}</th>)}</tr></thead>
            <tbody>{pagination.pageItems.map(row => <tr key={row.readerId} className="border-t border-slate-100 hover:bg-slate-50"><td className="break-words px-3 py-4">{row.cardNumber ?? 'Chưa cấp thẻ'}</td><td className="break-words px-3 py-4 font-semibold">{row.fullName}</td><td className="break-words px-3 py-4">{row.phone ?? 'Chưa có'}</td><td className="px-3 py-4 text-right tabular-nums">{row.overdueBooks}</td><td className="px-3 py-4 text-right tabular-nums">{row.maxOverdueDays}</td><td className="break-words px-3 py-4 text-right font-semibold tabular-nums">{formatMoney(row.outstandingVnd)}</td></tr>)}</tbody>
          </table>
          <TablePagination {...pagination} onPageChange={pagination.goToPage} />
        </> : <div className="p-4"><EmptyState title="Không có bạn đọc phù hợp" description="Bạn vẫn có thể xuất tệp Excel chỉ gồm 6 tiêu đề cột. Yêu cầu xuất vẫn được ghi nhật ký." /></div>}
    </Card>
  </div>
}
