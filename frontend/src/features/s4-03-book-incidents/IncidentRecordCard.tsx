import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Card from '../../components/ui/Card'
import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { closureLabel, incidentService, incidentTypeLabel, type IncidentRecord } from './incidentService'

export default function IncidentRecordCard({ record }: { record: IncidentRecord }) {
  const [showPhotos, setShowPhotos] = useState(false), [urls, setUrls] = useState<string[]>([]), [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    if (!showPhotos) return
    let active = true; const allocated: string[] = []
    setLoading(true)
    Promise.all(record.photos.map(async photo => {
      const blob = await incidentService.image(record.id, photo.id)
      if (!active) return ''
      const url = URL.createObjectURL(blob); allocated.push(url); return url
    })).then(values => { if (active) setUrls(values) }).catch(e => {
      if (active) { setShowPhotos(false); setError(getApiErrorMessage(e, 'Không tải được ảnh sự cố.')) }
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false; for (const url of allocated) URL.revokeObjectURL(url) }
  }, [showPhotos, record])
  return <Card className="space-y-3 p-5">
    <h3 className="break-words font-semibold">Sự cố #{record.id} · {incidentTypeLabel(record.type)} · {record.item.bookTitle}</h3>
    <p className="break-words text-sm">{record.item.readerName} · <Link className="text-blue-700 underline" to={`/loans/${record.item.loanId}`}>{record.item.loanNumber}</Link> · {record.item.barcode}</p>
    <dl className="grid gap-3 text-sm sm:grid-cols-2">
      <div><dt className="text-slate-500">Bản sao</dt><dd>Đang mượn → Đã loại khỏi kho</dd></div>
      <div><dt className="text-slate-500">Phiếu mượn hiện tại</dt><dd>{record.loanStatus === 'BORROWED' ? 'Đang mượn · còn bản chưa xử lý' : `Đã đóng · ${closureLabel(record.loanStatus)}`}</dd></div>
      <div><dt className="text-slate-500">Bồi thường tự tính</dt><dd>{formatVnd(record.coverPrice)} × {record.factor} = <strong>{formatVnd(record.automaticCompensationVnd ?? record.compensationVnd)}</strong></dd></div>
      <div><dt className="text-slate-500">Thực tế phải bồi thường</dt><dd><strong>{formatVnd(record.compensationVnd)}</strong></dd></div>
      {record.adjustmentReason && <div className="sm:col-span-2"><dt className="text-slate-500">Lý do điều chỉnh</dt><dd className="whitespace-pre-wrap break-words">{record.adjustmentReason}</dd></div>}
      <div><dt className="text-slate-500">Phí trễ hạn đã ghi nhận</dt><dd>{formatVnd(record.overdueFee?.amountVnd ?? 0)} · {record.overdueFee?.overdueDays ?? 0} ngày mở cửa</dd></div>
      <div><dt className="text-slate-500">{record.adjustmentReason ? 'Thủ thư điều chỉnh và ghi nhận' : 'Thủ thư ghi nhận'}</dt><dd>{record.actorName}</dd></div>
      <div><dt className="text-slate-500">Thời điểm</dt><dd>{formatLoanTimestamp(record.recordedAt)}</dd></div>
    </dl>
    <p className="whitespace-pre-wrap break-words text-sm">{record.description}</p>
    <p className="text-xs text-slate-500">Các khoản đã thu/miễn giữ nguyên. Sự cố đã xác nhận không được sửa hoặc xóa.</p>
    <Link className="inline-block text-sm text-blue-700 underline" to="/fee-payments">Xem khoản phải thu trong Thu phí và biên lai</Link>
    {record.photos.length > 0 && <div><Button variant="secondary" disabled={loading} onClick={() => setShowPhotos(v => !v)}>{loading ? 'Đang tải ảnh...' : showPhotos ? 'Ẩn ảnh' : `Xem ${record.photos.length} ảnh`}</Button></div>}
    {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
    {showPhotos && !loading && <div className="grid gap-3 sm:grid-cols-3">{urls.map((url, index) => <figure key={url}>
      <img src={url} alt={`Ảnh tình trạng ${index + 1}`} className="h-40 w-full rounded-lg object-contain" />
      <figcaption className="break-all text-xs">{record.photos[index]?.filename}</figcaption>
    </figure>)}</div>}
  </Card>
}
