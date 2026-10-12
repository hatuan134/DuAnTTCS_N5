import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import type { ReturnFeeAssessment } from './returnService'

export function formatVnd(value: number): string {
  return `${new Intl.NumberFormat('vi-VN').format(value)} VND`
}

export default function ReturnFeeDetails({ fee, preview = false }: {
  fee?: ReturnFeeAssessment | null; preview?: boolean
}) {
  if (!fee) return <p className="mt-4 text-sm text-slate-500">Phiếu cũ chưa ghi nhận kết quả tính phí. Không tự tính lại từ cấu hình hiện tại.</p>
  const ready = fee.status === 'READY'
  return <section className="mt-4 min-w-0 rounded-xl border border-slate-200 bg-slate-50 p-4" aria-label={preview ? 'Phí trễ hạn dự kiến' : 'Phí trễ hạn đã ghi nhận'}>
    <h3 className="font-semibold text-slate-900">{preview ? 'Phí trễ hạn dự kiến' : 'Phí trễ hạn đã ghi nhận'}</h3>
    {!ready && <p role="alert" className="mt-2 text-sm leading-6 text-amber-800">{fee.message}</p>}
    <dl className="mt-3 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
      <Field label="Hạn trả">{formatLoanTimestamp(fee.dueAt)}</Field>
      <Field label={preview ? 'Thời điểm tra cứu' : 'Ngày trả thực tế'}>{formatLoanTimestamp(fee.returnedAt)}</Field>
      <Field label="Số ngày mở cửa trễ hạn">{ready || fee.status === 'OVERDUE_FEE_NOT_CONFIGURED' ? `${fee.overdueDays} ngày` : 'Chưa xác định'}</Field>
      <Field label="Mức phí áp dụng mỗi ngày">{fee.dailyRateVnd === null ? 'Chưa khai báo' : formatVnd(fee.dailyRateVnd)}</Field>
      <Field label="Tiền trước làm tròn">{fee.rawAmountVnd === null ? 'Chưa xác định' : formatVnd(fee.rawAmountVnd)}</Field>
      <Field label={preview ? 'Số tiền phải thu dự kiến' : 'Số tiền phải thu đã ghi nhận'}>
        <span className={fee.amountVnd ? 'text-red-700' : 'text-emerald-700'}>{fee.amountVnd === null ? 'Chưa xác định' : formatVnd(fee.amountVnd)}</span>
      </Field>
    </dl>
    <p className="mt-3 text-xs leading-5 text-slate-600">Chỉ đếm ngày mở cửa sau hạn trả đến hết ngày trả. Làm tròn tổng phí đến 1.000 VND; đúng 500 đồng làm lên.</p>
    {preview ? <p className="mt-1 text-xs leading-5 text-slate-600">Khi xác nhận, hệ thống tính lại theo ngày trả thực tế và mức phí đang có hiệu lực.</p>
      : ready && <p className="mt-1 text-xs leading-5 text-slate-600">{fee.feeId === null ? 'Không tạo khoản phải thu vì phí bằng 0 VND.' : `Đã tạo khoản phải thu #${fee.feeId}.`} Kết quả giữ nguyên khi cấu hình thay đổi.</p>}
    {fee.excludedDates.length > 0 && <details className="mt-3 text-sm">
      <summary className="cursor-pointer font-medium text-blue-700">{fee.excludedDates.length} ngày đóng cửa không tính phí</summary>
      <ul className="mt-2 grid gap-1 sm:grid-cols-2">
        {fee.excludedDates.map(date => <li key={date} className="break-words">
          {date.split('-').reverse().join('/')} — {fee.closedDates.find(day => day.date === date)?.reason || 'Đóng cửa theo lịch tuần'}
        </li>)}
      </ul>
    </details>}
  </section>
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="min-w-0"><dt className="text-slate-500">{label}</dt><dd className="mt-1 break-words font-semibold text-slate-900">{children}</dd></div>
}
