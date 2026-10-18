import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRight, BookOpen, ChevronLeft, ChevronRight, CreditCard, Clock3 } from 'lucide-react'
import PublicBookCover from '../s2-10-book-cover/PublicBookCover'
import { allowedAiPath } from './navigation'
import { apiClient } from '../../core/api/apiClient'
import type { AiBlock, AiRow } from './types'

import { text, date, status } from './format'
function Jump({ href, label, role, onNavigate }: { href: unknown; label: string; role: string; onNavigate: () => void }) {
  if (!allowedAiPath(text(href), role)) return null
  return <Link className="lai-link" to={text(href)} onClick={onNavigate}>{label}<ArrowUpRight size={14} aria-hidden="true" /></Link>
}
/** Action uses the existing READER-only reservation endpoint; the LLM never issues write requests. */
function ReserveAction({ bookId, title }: { bookId: unknown; title: string }) {
  const [confirming, setConfirming] = useState(false)
  const [pending, setPending] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState('')
  const id = Number(bookId)
  if (!Number.isSafeInteger(id) || id <= 0) return null
  const reserve = async () => {
    if (pending) return
    setPending(true);setError('')
    try {
      await apiClient.post(`/books/${id}/reservations`)
      setSuccess(true);setConfirming(false)
    } catch (cause: unknown) {
      const failure = cause as { response?: { data?: { message?: string } } }
      setError(failure.response?.data?.message || 'Không thể đặt giữ. Vui lòng kiểm tra điều kiện thẻ và tình trạng sách.')
    } finally { setPending(false) }
  }
  if (success) return <div className="lai-reserve-result" role="status">Đã gửi yêu cầu đặt giữ. <Link to="/my-reservations">Xem đơn đặt giữ</Link></div>
  return <div className="lai-reserve-area">
    {!confirming ? <button type="button" className="lai-reserve-start" onClick={() => setConfirming(true)}>Đặt giữ sách</button> : <div className="lai-reserve-confirm">
      <p>Xác nhận gửi yêu cầu đặt giữ <strong>{title}</strong>? Thư viện sẽ kiểm tra điều kiện và số bản thực tế.</p>
      <div><button type="button" disabled={pending} onClick={() => void reserve()}>{pending ? 'Đang xử lý…' : 'Xác nhận đặt giữ'}</button><button type="button" disabled={pending} onClick={() => {setConfirming(false);setError('')}}>Hủy</button></div>
    </div>}
    {error && <p className="lai-reserve-error" role="alert">{error}</p>}
  </div>
}
function Facts({ row }: { row: AiRow }) {
  const dates: [string, string][] = [['borrowedAt', 'Ngày mượn'], ['dueAt', 'Hạn trả'], ['returnedAt', 'Ngày trả'], ['issuedAt', 'Ngày cấp'], ['expiresAt', 'Hết hạn'], ['reservedAt', 'Ngày đặt'], ['pickupDeadline', 'Hạn nhận'], ['timestamp', 'Ngày thực hiện']]
  return <dl className="lai-facts">{dates.filter(([key]) => row[key] != null).map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{date(row[key])}</dd></div>)}
    {row.overdueDays != null && <div><dt>Số ngày mở cửa trễ</dt><dd>{text(row.overdueDays)}</dd></div>}
    {row.queuePosition != null && <div><dt>Vị trí hàng chờ</dt><dd>{text(row.queuePosition)}</dd></div>}
    {row.cardNumber != null && <div><dt>Mã thẻ</dt><dd>{text(row.cardNumber)}</dd></div>}
    {row.cardTypeName != null && <div><dt>Loại thẻ</dt><dd>{text(row.cardTypeName)}</dd></div>}
    {row.barcode != null && <div><dt>Mã bản sao</dt><dd>{text(row.barcode)}</dd></div>}
    {row.location != null && <div><dt>Vị trí</dt><dd>{text(row.location)}</dd></div>}
  </dl>
}
export default function AiBlocks({ block, role, onNavigate }: { block: AiBlock; role: string; onNavigate: () => void }) {
  const track = useRef<HTMLDivElement>(null)
  const scroll = (direction: number) => track.current?.scrollBy({ left: direction * 230, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
  const daily = (Array.isArray(block.meta.daily) ? block.meta.daily : []) as AiRow[]
  // Derived day-by-day values are calculated from the verified report, not from the LLM.
  const counts = daily.map(row => {
    const value = Number(row.count)
    return Number.isFinite(value) && value >= 0 ? value : 0
  })
  const dailyTotal = counts.reduce((sum, current) => sum + current, 0)
  const activeDays = counts.filter(count => count > 0).length
  const peak = Math.max(0, ...counts)
  const max = Math.max(1, peak)
  let runningTotal = 0
  const detailedDays = daily.map((row, index) => {
    const count = counts[index]
    runningTotal += count
    return { date: date(row.date), count, accumulated: runningTotal, share: dailyTotal > 0 ? count / dailyTotal * 100 : 0, peak: peak > 0 && count === peak }
  })
  return <section className="lai-results" aria-label={block.title}>
    <div className="lai-results-heading"><h4>{block.title}</h4>{block.type === 'books' && block.items.length > 1 && <div className="lai-carousel-controls"><button type="button" aria-label="Sách trước" onClick={() => scroll(-1)}><ChevronLeft size={16} /></button><button type="button" aria-label="Sách tiếp theo" onClick={() => scroll(1)}><ChevronRight size={16} /></button></div>}</div>
    {block.meta.total != null && <p className="lai-caption">{text(block.meta.total)} kết quả · Hiển thị {block.items.length}{block.meta.page != null ? ` · Trang ${Number(block.meta.page) + 1}` : ''}</p>}
    {block.items.length === 0 && <div className="lai-empty-result"><BookOpen size={22} /><p>Không có dữ liệu phù hợp với truy vấn này.</p></div>}
    {block.type === 'books' ? <div className="lai-book-track" ref={track}>{block.items.map((row, i) => <article className="lai-book" key={text(row.id) || i}>
      <div className="lai-cover"><PublicBookCover bookId={Number(row.id)} title={text(row.title)} url={text(row.coverImageUrl)} thumbnail /></div>
      <div className="lai-book-body"><span className="lai-category">{text(row.categoryName) || 'Sách thư viện'}</span><h5>{text(row.title)}</h5><p>{text(row.authorName)}</p>
        {row.summary != null && text(row.summary) !== '' && <p className="lai-book-summary">{text(row.summary)}</p>}
        <span className={`lai-badge ${Number(row.availableCount) > 0 ? 'lai-ready' : ''}`}>{Number(row.availableCount) > 0 ? `Sẵn sàng: ${text(row.availableCount)}/${text(row.copyCount)}` : 'Chưa có bản sẵn sàng'}</span>
        <Jump href={row.href} label="Xem chi tiết" role={role} onNavigate={onNavigate} />
        {row.canOpenReservation === true && role === 'READER' && <ReserveAction bookId={row.id} title={text(row.title)} />}
      </div></article>)}</div> : block.type === 'statistics' ? <>
        <p className="lai-caption">{date(block.meta.startDate)} – {date(block.meta.endDate)}</p>
        <div className="lai-metrics">{block.items.map((row, i) => <div key={i}><span>{text(row.label)}</span><strong>{Number(row.value).toLocaleString('vi-VN')}</strong></div>)}</div>
        {block.meta.overdueRate != null && Number.isFinite(Number(block.meta.overdueRate)) && <p className="lai-stats-note">Tỷ lệ phiếu quá hạn cuối kỳ: <strong>{Number(block.meta.overdueRate).toLocaleString('vi-VN', { maximumFractionDigits: 2 })}%</strong> (theo báo cáo hệ thống).</p>}
        {daily.length > 0 && <section className="lai-chart lai-daily" aria-label="Chi tiết lượt mượn theo ngày">
          <div className="lai-daily-heading"><h5>Lượt mượn bản sao theo ngày</h5><span>Chi tiết luôn hiển thị</span></div>
          <div className="lai-daily-summary">
            <div><span>Phát sinh trong kỳ</span><strong>{dailyTotal.toLocaleString('vi-VN')} lượt</strong></div>
            <div><span>Ngày có mượn</span><strong>{activeDays}/{daily.length} ngày</strong></div>
            <div><span>Cao nhất/ngày</span><strong>{peak.toLocaleString('vi-VN')} lượt</strong>{peak > 0 && <small>{detailedDays.find(row => row.peak)?.date}</small>}</div>
          </div>
          <div className="lai-bars" role="img" aria-label={`Biểu đồ lượt mượn theo ngày: ${dailyTotal} lượt trong ${daily.length} ngày`}>{detailedDays.map((row, i) => <div key={i} title={`${row.date}: ${row.count} lượt`} style={{ height: `${Math.max(2, row.count / max * 100)}%` }} />)}</div>
          <div className="lai-chart-axis"><span>{detailedDays[0]?.date}</span><span>Ngày</span><span>{detailedDays[detailedDays.length - 1]?.date}</span></div>
          <div className="lai-daily-table-wrap" role="region" aria-label="Bảng chi tiết lượt mượn mỗi ngày" tabIndex={0}>
            <table className="lai-daily-table"><thead><tr><th>Ngày</th><th>Lượt mượn</th><th>Lũy kế</th><th>Tỷ trọng</th></tr></thead><tbody>
              {detailedDays.map((row, i) => <tr key={i} className={row.peak ? 'lai-peak-day' : ''}><td>{row.date}{row.peak ? <span className="lai-peak-label">Cao nhất</span> : null}</td><td>{row.count.toLocaleString('vi-VN')}</td><td>{row.accumulated.toLocaleString('vi-VN')}</td><td>{row.share.toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%</td></tr>)}
            </tbody><tfoot><tr><th>Tổng kỳ</th><th>{dailyTotal.toLocaleString('vi-VN')}</th><th>{dailyTotal.toLocaleString('vi-VN')}</th><th>{dailyTotal > 0 ? '100%' : '—'}</th></tr></tfoot></table>
          </div>
          <p className="lai-stats-note">Lượt mượn là số bản sao được mượn, không phải số phiếu mượn. Cuộn trong bảng để xem thêm ngày.</p>
        </section>}
      </> : <div className="lai-records">{block.items.map((row, i) => <article className={`lai-record ${row.overdue ? 'lai-overdue' : ''} ${block.type === 'libraryCard' ? 'lai-library-card' : ''}`} key={text(row.id) || i}>
        {block.type === 'libraryCard' && <CreditCard size={25} aria-hidden="true" />}
        {row.text != null ? <p>{text(row.text)}</p> : <><h5>{text(row.title)}</h5>{row.value != null && <p className="lai-caption">Số lượng: <strong>{Number(row.value).toLocaleString('vi-VN')}</strong></p>}{row.loanNumber != null && <p className="lai-caption">Phiếu {text(row.loanNumber)}</p>}
          {(row.status != null || row.registrationStatus != null) && <span className="lai-badge">{row.overdue === true && <Clock3 size={12} />}{status(row.status ?? row.registrationStatus)}</span>}<Facts row={row} />
          {Array.isArray(row.books) && <ul>{(row.books as AiRow[]).map((book, j) => <li key={j}>{text(book.title)} · Hạn {date(book.dueAt)}</li>)}</ul>}
          <Jump href={row.href} label={block.type === 'loans' && /^\/loans\/\d+$/.test(text(row.href)) ? 'Xem phiếu mượn' : 'Xem chi tiết'} role={role} onNavigate={onNavigate} /></>}
      </article>)}</div>}
    <div className="lai-actions">{block.actions.map((action, i) => <Jump key={i} href={action.href} label={action.label} role={role} onNavigate={onNavigate} />)}</div>
    {Number(block.meta.total) > (Number(block.meta.page || 0) + 1) * Number(block.meta.size || block.items.length || 1) && <p className="lai-caption">Còn kết quả khác. Hỏi “Xem trang tiếp theo” hoặc mở danh sách đầy đủ.</p>}
  </section>
}
