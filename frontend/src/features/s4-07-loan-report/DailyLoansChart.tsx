import { useEffect, useRef, useState } from 'react'
import type { DailyLoanCount } from './loanReportService'

interface DailyLoansChartProps {
  data: DailyLoanCount[]
  totalBorrowed: number
}

const formatNumber = (value: number) => value.toLocaleString('vi-VN')
const formatDate = (isoDate: string) => isoDate.split('-').reverse().join('/')

function showDateTick(date: string, index: number, length: number): boolean {
  if (length <= 10) return true
  if (length <= 35) return index % 5 === 0 || index === length - 1
  return index === 0 || index === length - 1 || date.endsWith('-01') || index % Math.ceil(length / 10) === 0
}

/** Uses SVG only: no new chart dependency or independent API call. */
export default function DailyLoansChart({ data, totalBorrowed }: DailyLoansChartProps) {
  const [selected, setSelected] = useState<string | null>(null)
  const [availableWidth, setAvailableWidth] = useState(1200)
  const container = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const element = container.current
    if (!element) return
    const resize = () => setAvailableWidth(element.clientWidth)
    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(element)
    return () => observer.disconnect()
  }, [data.length])
  const actualTotal = data.reduce((sum, day) => sum + day.borrowCount, 0)
  const selectedDay = data.find(day => day.date === selected) ?? null
  // Fit ordinary date ranges; retain local scrolling when there are too many
  // columns to distinguish at a minimum three pixels per day.
  const naturalWidth = Math.max(320, Math.min(1200, data.length * 26 + 95))
  const width = Math.max(Math.min(naturalWidth, Math.max(240, data.length * 3 + 95)), Math.min(naturalWidth, availableWidth))
  const height = 310
  const left = 55
  const right = 22
  const top = 25
  const bottom = 62
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  const maximum = Math.max(1, ...data.map(day => day.borrowCount))
  const tickStep = Math.max(1, Math.ceil(maximum / 4))
  const topValue = Math.ceil(maximum / tickStep) * tickStep
  const columnStep = plotWidth / Math.max(1, data.length)
  const columnWidth = Math.min(26, Math.max(2, columnStep * 0.68))
  const ticks = Array.from({ length: Math.floor(topValue / tickStep) + 1 }, (_, index) => index * tickStep)

  return <section aria-label="Biểu đồ lượt mượn theo ngày" className="min-w-0">
    <div className="border-b border-slate-200 px-4 py-5 sm:px-6">
      <h2 className="text-lg font-bold text-slate-900">Số lượt mượn theo ngày</h2>
      <p className="mt-1 text-sm text-slate-500">
        Mỗi bản sách được mượn tính một lượt theo ngày phát sinh (giờ Việt Nam). Chạm, di chuột hoặc dùng Tab để xem từng cột.
      </p>
      {actualTotal !== totalBorrowed && <p role="alert" className="mt-2 text-sm font-medium text-red-700">
        Số liệu biểu đồ chưa khớp chỉ số tổng quan. Vui lòng tải lại báo cáo.
      </p>}
      {actualTotal === 0 && <p role="status" className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
        Không có lượt mượn nào trong khoảng thời gian đã chọn. Các ngày đều được hiển thị với giá trị 0.
      </p>}
    </div>
    {data.length > 0 && <div className="px-3 pt-4 pb-3 sm:px-6">
      <div ref={container} className="min-w-0 overflow-x-auto" tabIndex={0} aria-label="Cuộn ngang để xem toàn bộ ngày nếu cần">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          width={width}
          height={height}
          className="mx-auto block max-w-none"
          role="group"
          aria-label={`Số lượt mượn của ${data.length} ngày, tổng ${formatNumber(actualTotal)} lượt`}
        >
          {ticks.map(value => {
            const y = top + plotHeight - value / topValue * plotHeight
            return <g key={value}>
              <line x1={left} x2={width - right} y1={y} y2={y} stroke="#e2e8f0" strokeDasharray={value === 0 ? undefined : '4 4'} />
              <text x={left - 10} y={y + 4} textAnchor="end" fontSize="12" fill="#64748b">{formatNumber(value)}</text>
            </g>
          })}
          {data.map((day, index) => {
            const x = left + (index + 0.5) * columnStep
            const barHeight = day.borrowCount / topValue * plotHeight
            const active = selectedDay?.date === day.date
            return <g key={day.date}>
              <rect
                x={x - columnWidth / 2}
                y={top + plotHeight - Math.max(2, barHeight)}
                width={columnWidth}
                height={Math.max(2, barHeight)}
                rx={Math.min(4, columnWidth / 4)}
                fill={active ? '#1d4ed8' : day.borrowCount === 0 ? '#94a3b8' : '#3b82f6'}
                tabIndex={0}
                role="button"
                aria-label={`${formatDate(day.date)}: ${formatNumber(day.borrowCount)} lượt mượn`}
                data-testid={`daily-bar-${day.date}`}
                data-count={day.borrowCount}
                onMouseEnter={() => setSelected(day.date)}
                onFocus={() => setSelected(day.date)}
                onClick={() => setSelected(day.date)}
                onKeyDown={event => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    setSelected(day.date)
                  }
                }}
              >
                <title>{`${formatDate(day.date)}: ${formatNumber(day.borrowCount)} lượt mượn`}</title>
              </rect>
              {showDateTick(day.date, index, data.length) && <text
                x={x} y={height - bottom + 22} textAnchor="middle" fontSize="11" fill="#475569"
              >{data.length > 35 ? day.date.slice(5).replace('-', '/') : formatDate(day.date).slice(0, 5)}</text>}
            </g>
          })}
          <text x={left + plotWidth / 2} y={height - 7} textAnchor="middle" fontSize="12" fill="#64748b">Ngày (Asia/Ho_Chi_Minh)</text>
          <text x={15} y={top + plotHeight / 2} textAnchor="middle" fontSize="12" fill="#64748b"
            transform={`rotate(-90 15 ${top + plotHeight / 2})`}>Lượt mượn</text>
        </svg>
      </div>
      <p className="mt-2 min-h-6 text-center text-sm text-slate-700" role="status" aria-live="polite">
        {selectedDay
          ? <><strong>{formatDate(selectedDay.date)}</strong>: <strong>{formatNumber(selectedDay.borrowCount)}</strong> lượt mượn</>
          : `Tổng ${formatNumber(actualTotal)} lượt mượn trong ${data.length} ngày. Chọn một cột để xem chi tiết.`}
      </p>
    </div>}
  </section>
}
