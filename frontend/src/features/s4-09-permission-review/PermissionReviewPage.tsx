import { useEffect, useRef, useState } from 'react'
import { Download, FileCheck2, RefreshCw, ShieldCheck, Upload, X } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import LoadingState from '../../components/ui/LoadingState'
import Modal from '../../components/ui/Modal'
import PageHeader from '../../components/ui/PageHeader'
import TablePagination from '../../components/ui/TablePagination'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import {
  downloadReport, formatReviewTime, gateLabels, parseReportFile, permissionReviewService, ruleLabels, selectEntries,
  type ImportReport, type Inspection, type ReportDetail, type ReportPage, type ReviewedEntry,
} from './permissionReviewService'

export default function PermissionReviewPage() {
  if (getCurrentUser()?.role !== 'ADMIN') {
    return <Card className="p-6"><h1 className="font-semibold text-slate-900">Không có quyền truy cập</h1>
      <p className="mt-2 text-sm text-slate-600">Chỉ Quản trị hệ thống được xem bảng đối chiếu và biên bản rà soát phân quyền.</p></Card>
  }
  return <PermissionReviewWorkspace />
}

export function PermissionReviewWorkspace() {
  const [data, setData] = useState<Inspection | null>(null)
  const [reports, setReports] = useState<ReportPage | null>(null)
  const [loading, setLoading] = useState(true), [reportsLoading, setReportsLoading] = useState(true)
  const [revision, setRevision] = useState(0), [reportPage, setReportPage] = useState(0), [page, setPage] = useState(1)
  const [module, setModule] = useState(''), [query, setQuery] = useState(''), [differences, setDifferences] = useState(false)
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error' | 'warning'; message: string } | null>(null)
  const [report, setReport] = useState<ReportDetail | null>(null), [opening, setOpening] = useState<number | null>(null)
  const [file, setFile] = useState<ImportReport | null>(null), [filename, setFilename] = useState(''), [fileError, setFileError] = useState('')
  const [importing, setImporting] = useState(false)
  const importLock = useRef(false), fileVersion = useRef(0), alive = useRef(true)
  const detailController = useRef<AbortController | null>(null)
  useEffect(() => { alive.current = true; return () => { alive.current = false; detailController.current?.abort(); fileVersion.current++ } }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    permissionReviewService.inspect(controller.signal)
      .then(value => { if (!controller.signal.aborted) setData(value) })
      .catch(error => { if (!controller.signal.aborted) { setData(null); setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được bảng đối chiếu phân quyền.') }) } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [revision])
  useEffect(() => {
    const controller = new AbortController()
    setReportsLoading(true)
    permissionReviewService.reports(reportPage, controller.signal)
      .then(value => { if (!controller.signal.aborted) setReports(value) })
      .catch(error => { if (!controller.signal.aborted) { setReports(null); setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được danh sách biên bản.') }) } })
      .finally(() => { if (!controller.signal.aborted) setReportsLoading(false) })
    return () => controller.abort()
  }, [revision, reportPage])

  async function chooseFile(selected?: File) {
    const version = ++fileVersion.current
    setFile(null); setFilename(selected?.name ?? ''); setFileError('')
    if (!selected) return
    try {
      if (selected.size > 1024 * 1024) throw new Error('Biên bản tối đa 1 MB. Chọn tệp JSON kết quả của bộ kiểm thử S4-09.1.')
      const parsed = parseReportFile(await selected.text())
      if (alive.current && version === fileVersion.current) setFile(parsed)
    } catch (error) {
      if (!alive.current || version !== fileVersion.current) return
      const message = error instanceof Error ? error.message : 'Không đọc được tệp biên bản.'
      setFileError(message); setFeedback({ tone: 'warning', message })
    }
  }
  async function importFile() {
    if (!file || importLock.current) return
    importLock.current = true; setImporting(true); setFeedback(null)
    try {
      const saved = await permissionReviewService.importReport(file)
      if (!alive.current) return
      setReport(saved); setFile(null); setFilename(''); setFileError(''); setReportPage(0); setRevision(value => value + 1)
      setFeedback({ tone: 'success', message: 'Đã lưu biên bản #' + saved.summary.id + '. Kết quả được phân loại theo bằng chứng kiểm thử.' })
    } catch (error) {
      if (alive.current) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không lưu được biên bản rà soát.') })
    } finally { importLock.current = false; if (alive.current) setImporting(false) }
  }
  async function openReport(id: number) {
    detailController.current?.abort()
    setFeedback(null)
    const controller = new AbortController(); detailController.current = controller
    setOpening(id)
    try { const value = await permissionReviewService.detail(id, controller.signal); if (!controller.signal.aborted) setReport(value) }
    catch (error) { if (!controller.signal.aborted) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được chi tiết biên bản.') }) }
    finally { if (!controller.signal.aborted) setOpening(null) }
  }
  const entries = data ? selectEntries(data, module, query, differences) : []
  const pageSize = 10, totalPages = Math.max(1, Math.ceil(entries.length / pageSize)), currentPage = Math.min(page, totalPages)
  function resetFilter() { setPage(1) }
  const notify = feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />
  function closeReport() { setReport(null); setFeedback(null) }
  const paginationClass = 'px-3 sm:px-5 [&>div]:flex-wrap [&>div>span]:min-w-0'
  return <div className="min-w-0 space-y-5">
    <PageHeader title="Rà soát phân quyền" description="Đối chiếu các API nghiệp vụ trọng yếu với bảng quyền đã chốt của bốn vai trò."
      action={<Button type="button" variant="secondary" disabled={loading || importing} onClick={() => setRevision(value => value + 1)}><RefreshCw size={16} />Làm mới</Button>} />
    {!report && notify}
    {loading ? <LoadingState /> : !data ? <Card className="p-6"><EmptyState title="Chưa tải được bảng phân quyền" description="Bấm Làm mới để thử lại khi kết nối máy chủ hoạt động." /></Card> : <>
      <div className="grid gap-3 sm:grid-cols-3">
        <Metric label="API / thao tác đã đối chiếu" value={data.reviewedFunctions} />
        <Metric label="Thao tác lệch cổng quyền" value={data.differentFunctions} warning={data.differentFunctions > 0} />
        <Metric label="Thao tác chưa xác định" value={data.unresolvedFunctions} warning={data.unresolvedFunctions > 0} />
      </div>
      <Card className="space-y-3 p-4 sm:p-5">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900"><ShieldCheck size={19} />Căn cứ đối chiếu</h2>
        <p className="text-sm leading-6 text-slate-600">{data.decision} Phiên bản {data.policyVersion}. Đối chiếu lúc {formatReviewTime(data.inspectedAt)}.</p>
        <p className="text-sm leading-6 text-slate-600">Quyền thực tế bên dưới là cổng kiểm tra vai trò của API đang chạy. Quyền theo dữ liệu cá nhân và luồng ngoại lệ còn được kiểm tra ở tầng nghiệp vụ; xem bằng chứng trong biên bản kiểm thử để xác nhận các điều kiện này.</p>
        <details className="text-sm"><summary className="cursor-pointer font-medium text-slate-700">Xem dấu vân tay bản kiểm thử</summary><code className="mt-2 block break-all rounded-lg bg-slate-50 p-3 text-xs">{data.fingerprint}</code></details>
      </Card>
      <Card className="space-y-4 p-4 sm:p-5">
        <div className="grid items-end gap-3 md:grid-cols-2">
          <label className="text-sm font-medium text-slate-700">Phân hệ
            <select value={module} onChange={event => { setModule(event.target.value); resetFilter() }} className="mt-1.5 min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3">
              <option value="">Tất cả phân hệ</option>{data.modules.map(item => <option key={item.code} value={item.code}>{item.priority}. {item.name}</option>)}
            </select></label>
          <label className="text-sm font-medium text-slate-700">Tìm chức năng hoặc API
            <input value={query} maxLength={150} onChange={event => { setQuery(event.target.value); resetFilter() }} placeholder="Ví dụ: thu phí, collect-full" className="mt-1.5 min-h-11 w-full rounded-xl border border-slate-300 px-3" /></label>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3 text-sm"><label className="flex min-h-11 items-center gap-2 text-slate-700"><input type="checkbox" checked={differences} onChange={event => { setDifferences(event.target.checked); resetFilter() }} />Chỉ hiện sai lệch hoặc chưa xác định</label>
          <span role="status" className="text-slate-500">{entries.length} / {data.reviewedFunctions} thao tác khớp bộ lọc</span></div>
      </Card>
      {entries.length ? <div className="space-y-4">{entries.slice((currentPage - 1) * pageSize, currentPage * pageSize).map(entry => <ComparisonCard key={entry.policy.id} entry={entry} data={data} />)}
        <TablePagination className={paginationClass} page={currentPage} totalItems={entries.length} totalPages={totalPages} pageSize={pageSize} onPageChange={setPage} /></div>
        : <Card className="p-5"><EmptyState title={differences ? 'Không có sai lệch cổng quyền theo bộ lọc' : 'Không có chức năng phù hợp'} description="Thay đổi phân hệ hoặc từ khóa để xem các thao tác khác. Kết quả này không thay thế kiểm thử API và kiểm thử sở hữu dữ liệu." /></Card>}
    </>}
    <Card className="space-y-4 p-4 sm:p-5">
      <h2 className="flex items-center gap-2 font-semibold text-slate-900"><Upload size={18} />Nhập kết quả kiểm thử</h2>
      <p className="text-sm leading-6 text-slate-600">Chọn tệp JSON do bộ kiểm thử riêng tạo ra. Thao tác này chỉ lưu bằng chứng và biên bản, không chạy giao dịch mượn, trả hoặc thu tiền trên hệ thống đang dùng. Biên bản mô phỏng luôn được ghi rõ.</p>
      <label className="block text-sm font-medium text-slate-700">Tệp kết quả JSON (tối đa 1 MB)
        <span className="mt-2 flex min-h-11 min-w-0 flex-wrap items-center gap-3 rounded-xl border border-slate-300 p-2 focus-within:ring-4 focus-within:ring-blue-100">
          <span className="rounded-lg bg-slate-100 px-3 py-2 text-sm font-semibold">Chọn tệp JSON</span>
          <span className="min-w-0 break-all text-sm font-normal text-slate-500">{filename || 'Chưa chọn tệp'}</span>
          <input type="file" accept=".json,application/json" disabled={importing} aria-invalid={Boolean(fileError)} aria-describedby={fileError ? 'review-file-error' : undefined}
            onChange={event => { const selected = event.target.files?.[0]; event.currentTarget.value = ''; void chooseFile(selected) }} className="sr-only" />
        </span></label>
      {fileError && <p id="review-file-error" role="alert" className="break-words text-sm text-red-700">{fileError}</p>}
      {file && <p className="break-words text-sm text-slate-600">{filename} · {file.kind === 'SIMULATION' ? 'Mô phỏng riêng' : 'Kiểm thử hồi quy'} · {file.checks.length} trường hợp</p>}
      <Button type="button" disabled={!file || importing || !data} loading={importing} onClick={() => void importFile()}><FileCheck2 size={17} />Lưu biên bản</Button>
    </Card>
    <Card className="overflow-hidden">
      <div className="border-b border-slate-200 p-4 sm:p-5"><h2 className="font-semibold text-slate-900">Biên bản rà soát đã lưu</h2><p className="mt-1 text-sm text-slate-500">Số sai lệch và số đã khắc phục được tính riêng trong từng biên bản.</p></div>
      {reportsLoading ? <LoadingState /> : !reports ? <p className="p-5 text-sm text-slate-600">Chưa tải được biên bản. Bấm Làm mới để thử lại.</p> : !reports.items.length ? <div className="p-5"><EmptyState title="Chưa có biên bản" description="Chạy bộ kiểm thử trên môi trường riêng, sau đó nhập tệp kết quả ở phía trên." /></div> : <>
        <div className="divide-y divide-slate-200">{reports.items.map(item => <article key={item.id} className="space-y-3 p-4 sm:p-5">
          <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="font-semibold text-slate-900">Biên bản #{item.id} <span className={'ml-2 rounded-lg px-2 py-1 text-xs ' + (item.kind === 'SIMULATION' ? 'bg-amber-50 text-amber-800' : 'bg-blue-50 text-blue-800')}>{item.kind === 'SIMULATION' ? 'Mô phỏng riêng' : 'Hồi quy'}</span></h3>
            <Button type="button" variant="secondary" size="sm" loading={opening === item.id} disabled={opening !== null} onClick={() => void openReport(item.id)}>Xem biên bản</Button></div>
          <p className="break-words text-sm text-slate-600">{item.caseCount} trường hợp · {item.detectedCount} sai lệch · {item.fixedCount} đã khắc phục · {item.failedCount} chưa đạt</p>
          <p className="break-words text-xs leading-5 text-slate-500">Chạy bởi {item.executedBy} · {formatReviewTime(item.completedAt)}. Nhập bởi {item.importedBy} · {formatReviewTime(item.importedAt)}. Môi trường: {item.environment}.</p>
        </article>)}</div>
        <TablePagination className={paginationClass} page={reportPage + 1} totalItems={reports.totalItems} totalPages={Math.max(1, Math.ceil(reports.totalItems / reports.size))} pageSize={reports.size} onPageChange={value => setReportPage(value - 1)} />
      </>}
    </Card>
    {report && <Modal label={'Biên bản rà soát #' + report.summary.id} onClose={closeReport}>
      <section className="max-h-[85dvh] w-[min(960px,calc(100vw-32px))] overflow-y-auto rounded-2xl bg-white p-4 sm:p-6">
        <div className="flex items-start justify-between gap-3"><h2 className="text-lg font-semibold text-slate-900">Biên bản rà soát #{report.summary.id}</h2><Button type="button" variant="ghost" size="sm" onClick={closeReport} aria-label="Đóng biên bản"><X size={18} /></Button></div>
        <div className="mt-4 space-y-4">
          {notify}
          <p className="rounded-xl bg-slate-50 p-3 text-sm leading-6 text-slate-700">{report.summary.kind === 'SIMULATION' ? 'MÔ PHỎNG: sai lệch được tạo có chủ đích trong môi trường kiểm thử riêng, không phải kết luận lỗi thật của hệ thống.' : 'Kết quả bộ kiểm thử được Admin nhập từ môi trường riêng; không phải một lần chạy API trên máy chủ này.'}</p>
          {!report.currentFingerprint && <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">Biên bản thuộc bản code khác. Cần chạy lại để xác nhận trạng thái hiện tại.</p>}
          <dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-slate-500">Người chạy kiểm thử</dt><dd className="break-words font-medium">{report.summary.executedBy}</dd></div><div><dt className="text-slate-500">Nguồn kiểm thử</dt><dd className="break-words font-medium">{report.summary.sourceReference}</dd></div><div><dt className="text-slate-500">Người nhập biên bản</dt><dd className="break-words font-medium">{report.summary.importedBy}</dd></div><div><dt className="text-slate-500">Thời điểm nhập</dt><dd>{formatReviewTime(report.summary.importedAt)}</dd></div></dl>
          <div className="grid gap-3 sm:grid-cols-3"><Metric label="Sai lệch phát hiện" value={report.summary.detectedCount} /><Metric label="Đã khắc phục" value={report.summary.fixedCount} /><Metric label="Chưa đạt" value={report.summary.failedCount} warning={report.summary.failedCount > 0} /></div>
          <Button type="button" variant="secondary" onClick={() => { try { downloadReport(report); setFeedback({ tone: 'success', message: 'Đã tạo tệp biên bản JSON.' }) } catch { setFeedback({ tone: 'error', message: 'Không tạo được tệp biên bản. Vui lòng thử lại.' }) } }}><Download size={16} />Tải biên bản JSON</Button>
          {report.results.map(result => <article key={result.test.endpointId + result.test.role + result.test.scenario} className="space-y-2 rounded-xl border border-slate-200 p-4 text-sm">
            <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold text-slate-900">{result.functionName}</h3><span className={'rounded-lg px-2 py-1 text-xs font-semibold ' + (result.passed ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700')}>{result.outcome === 'FIXED' ? 'Đã khắc phục' : result.passed ? 'Đạt' : 'Chưa đạt'}</span></div>
            <p className="break-words text-slate-600">{data?.modules.find(item => item.code === result.module)?.name ?? result.module} · {data?.roles.find(item => item.code === result.test.role)?.name ?? 'Chưa đăng nhập'} · {result.test.scenario}</p>
            <p>Theo tài liệu: {ruleLabels[result.documented]} · HTTP mong đợi: {result.test.expectedStatus}</p>
            <p>Trước: {result.test.beforeStatus ?? 'Không có sai lệch trước đó'} → Kiểm thử lại: <strong>{result.test.actualStatus}</strong></p>
            <p>Dữ liệu sau yêu cầu bị từ chối: {result.test.resourceUnchanged === true ? 'Không thay đổi' : result.test.resourceUnchanged === false ? 'Có thay đổi' : 'Không áp dụng / chưa kiểm tra'}</p>
            <p className="break-words leading-6 text-slate-600">Bằng chứng: {result.test.evidence}</p>
            {result.test.correction && <p className="break-words leading-6">Nội dung sửa: {result.test.correction}</p>}
            <p className="text-xs leading-5 text-slate-500">Phát hiện: {formatReviewTime(result.test.detectedAt)} · Kiểm thử lại: {formatReviewTime(result.test.testedAt)}</p>
          </article>)}
        </div>
      </section>
    </Modal>}
  </div>
}

function Metric({ label, value, warning = false }: { label: string; value: number; warning?: boolean }) {
  return <Card className="p-4"><p className="text-sm text-slate-500">{label}</p><p className={'mt-2 text-2xl font-semibold ' + (warning ? 'text-red-700' : 'text-slate-900')}>{value.toLocaleString('vi-VN')}</p></Card>
}
function ComparisonCard({ entry, data }: { entry: ReviewedEntry; data: Inspection }) {
  return <Card className="min-w-0 space-y-4 p-4 sm:p-5">
    <div className="flex flex-wrap items-start justify-between gap-2"><div className="min-w-0"><p className="text-xs font-medium text-slate-500">{data.modules.find(item => item.code === entry.policy.module)?.name}</p><h3 className="mt-1 break-words font-semibold text-slate-900">{entry.policy.label}</h3></div>
      <span className={'rounded-lg px-2.5 py-1 text-xs font-semibold ' + (entry.different ? 'bg-red-50 text-red-700' : 'bg-emerald-50 text-emerald-800')}>{entry.different ? 'Cần kiểm tra' : 'Khớp cổng quyền'}</span></div>
    <code className="block break-all rounded-lg bg-slate-50 p-3 text-xs leading-5 text-slate-600">{entry.policy.httpMethods.join(' / ')} {entry.policy.path}</code>
    <div className="grid min-w-0 gap-3 sm:grid-cols-2 xl:grid-cols-4">{entry.roles.map(role => <div key={role.role} className={'min-w-0 rounded-xl border p-3 ' + (role.matches ? 'border-slate-200' : 'border-red-200 bg-red-50')}>
      <p className="break-words text-sm font-semibold text-slate-900">{data.roles.find(item => item.code === role.role)?.name}</p>
      <dl className="mt-3 space-y-2 text-xs leading-5"><div><dt className="text-slate-500">Theo tài liệu</dt><dd className="font-medium text-slate-800">{ruleLabels[role.documented]}</dd></div><div><dt className="text-slate-500">Cổng API thực tế</dt><dd className={role.matches ? 'text-slate-700' : 'font-semibold text-red-700'}>{gateLabels[role.actualGate]}</dd></div></dl>
    </div>)}</div>
    {entry.policy.condition && <p className="text-sm leading-6 text-slate-600">Điều kiện: {entry.policy.condition}</p>}
    {Object.values(entry.policy.rules).includes('OWN') && <p className="text-xs leading-5 text-slate-500">Dữ liệu cá nhân được xác định từ tài khoản đăng nhập; không lấy danh tính từ tham số do trình duyệt tự gửi.</p>}
    <details className="text-xs text-slate-500"><summary className="cursor-pointer">Xem căn cứ trong source</summary><p className="mt-2 break-all">{entry.policy.controller}.{entry.policy.handler}</p><code className="mt-1 block break-all">{entry.guard}</code></details>
  </Card>
}
