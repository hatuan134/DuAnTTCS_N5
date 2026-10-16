import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, CheckCircle2, Printer, X } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'
import TablePagination from '../../components/ui/TablePagination'
import useTablePagination from '../../hooks/useTablePagination'
import type { BulkCreatedBookCopy, BulkCreateBookCopiesResult } from './bookCopyService'

const CODE128_PATTERNS = [
  '212222', '222122', '222221', '121223', '121322', '131222', '122213', '122312',
  '132212', '221213', '221312', '231212', '112232', '122132', '122231', '113222',
  '123122', '123221', '223211', '221132', '221231', '213212', '223112', '312131',
  '311222', '321122', '321221', '312212', '322112', '322211', '212123', '212321',
  '232121', '111323', '131123', '131321', '112313', '132113', '132311', '211313',
  '231113', '231311', '112133', '112331', '132131', '113123', '113321', '133121',
  '313121', '211331', '231131', '213113', '213311', '213131', '311123', '311321',
  '331121', '312113', '312311', '332111', '314111', '221411', '431111', '111224',
  '111422', '121124', '121421', '141122', '141221', '112214', '112412', '122114',
  '122411', '142112', '142211', '241211', '221114', '413111', '241112', '134111',
  '111242', '121142', '121241', '114212', '124112', '124211', '411212', '421112',
  '421211', '212141', '214121', '412121', '111143', '111341', '131141', '114113',
  '114311', '411113', '411311', '113141', '114131', '311141', '411131', '211412',
  '211214', '211232', '2331112',
] as const

function formatDate(value: string) {
  return value.split('-').reverse().join('/')
}

function shelfLabel(copy: BulkCreatedBookCopy) {
  return copy.shelfName ? `${copy.shelfCode} — ${copy.shelfName}` : copy.shelfCode
}

function encodeCode128B(value: string) {
  const values = Array.from(value, (character) => character.charCodeAt(0) - 32)
  if (values.some((code) => code < 0 || code > 94)) return null
  const checksum = (104 + values.reduce((sum, code, index) => sum + code * (index + 1), 0)) % 103
  return [104, ...values, checksum, 106]
}

function Code128Barcode({ value }: { value: string }) {
  const encoded = useMemo(() => encodeCode128B(value), [value])
  if (!encoded) {
    return <div className="py-3 text-center font-mono text-sm font-semibold">{value}</div>
  }

  const quietZone = 10
  let x = quietZone
  const bars: Array<{ x: number; width: number }> = []
  for (const code of encoded) {
    const pattern = CODE128_PATTERNS[code]
    let drawBar = true
    for (const digit of pattern) {
      const width = Number(digit)
      if (drawBar) bars.push({ x, width })
      x += width
      drawBar = !drawBar
    }
  }
  const totalWidth = x + quietZone

  return (
    <svg
      className="h-14 w-full"
      viewBox={`0 0 ${totalWidth} 58`}
      preserveAspectRatio="none"
      role="img"
      aria-label={`Mã vạch ${value}`}
    >
      <rect width={totalWidth} height="58" fill="white" />
      {bars.map((bar, index) => (
        <rect key={`${bar.x}-${index}`} x={bar.x} y="2" width={bar.width} height="54" fill="black" />
      ))}
    </svg>
  )
}

function BarcodeLabel({ copy, bookTitle }: { copy: BulkCreatedBookCopy; bookTitle: string }) {
  return (
    <article className="bulk-barcode-label rounded-lg border border-slate-300 bg-white p-4 text-slate-950">
      <p className="truncate text-center text-sm font-bold" title={bookTitle}>{bookTitle}</p>
      <Code128Barcode value={copy.barcode} />
      <p className="mt-1 text-center font-mono text-base font-bold tracking-wider">{copy.barcode}</p>
      <div className="mt-2 grid grid-cols-2 gap-x-3 text-[11px] leading-4">
        <p><span className="font-semibold">Kho:</span> {copy.warehouseCode}</p>
        <p><span className="font-semibold">Kệ:</span> {copy.shelfCode}</p>
      </div>
    </article>
  )
}

export default function BulkCreateBookCopiesResult({
  bookTitle,
  result,
  onBack,
}: {
  bookTitle: string
  result: BulkCreateBookCopiesResult
  onBack: () => void
}) {
  const [printMode, setPrintMode] = useState(false)
  const createdCopies = result.createdCopies ?? []
  const copyPagination = useTablePagination(createdCopies)

  useEffect(() => {
    if (!printMode) return undefined
    document.body.classList.add('bulk-label-printing')
    return () => document.body.classList.remove('bulk-label-printing')
  }, [printMode])

  useEffect(() => () => document.body.classList.remove('bulk-label-printing'), [])

  if (printMode) {
    return (
      <div>
        <div className="bulk-label-print-controls mb-5 flex flex-wrap items-center justify-between gap-3">
          <Button type="button" variant="secondary" onClick={() => setPrintMode(false)}>
            <X size={17} /> Đóng chế độ in
          </Button>
          <Button type="button" onClick={() => window.print()}>
            <Printer size={17} /> In {createdCopies.length} nhãn
          </Button>
        </div>

        <div className="bulk-label-print-controls mb-5">
          <PageHeader
            title="Chế độ in nhãn mã vạch"
            description={`Chỉ gồm ${createdCopies.length} bản sao vừa tạo trong lô hiện tại. Kiểm tra nhãn trước khi in.`}
          />
        </div>

        <section className="bulk-label-print-area" aria-label="Nhãn mã vạch của lô vừa tạo">
          <div className="bulk-label-grid grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {copyPagination.pageItems.map((copy) => (
              <BarcodeLabel key={copy.barcode} copy={copy} bookTitle={bookTitle} />
            ))}
          </div>
        </section>
      </div>
    )
  }

  return (
    <div>
      <button
        type="button"
        onClick={onBack}
        className="mb-4 inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:underline"
      >
        <ArrowLeft size={16} /> Quay lại chi tiết đầu sách
      </button>

      <PageHeader
        title="Kết quả tạo lô bản sao"
        description="Đối chiếu đúng các bản sao vừa tạo và mở chế độ in nhãn cho lô hiện tại."
      />

      <Card className="mb-6 border-emerald-200 bg-emerald-50 p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex gap-3">
            <CheckCircle2 className="mt-0.5 shrink-0 text-emerald-600" size={24} />
            <div>
              <h3 className="font-semibold text-emerald-950">Tạo lô thành công</h3>
              <p className="mt-1 text-sm text-emerald-900">
                Đã tạo <strong>{result.createdCount}</strong> bản sao cho đầu sách <strong>{bookTitle}</strong>.
              </p>
              <p className="mt-1 text-sm text-emerald-800">
                Khoảng mã đã sử dụng: {result.startBarcode} — {result.endBarcode}, không tính các mã bị bỏ qua.
              </p>
            </div>
          </div>
          <Button type="button" onClick={() => setPrintMode(true)} disabled={createdCopies.length === 0}>
            <Printer size={17} /> Mở chế độ in nhãn
          </Button>
        </div>
      </Card>

      <Card className="overflow-hidden">
        <div className="border-b border-slate-200 px-6 py-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-lg font-semibold text-slate-900">Các bản sao vừa tạo trong lô</h3>
              <p className="mt-1 text-sm text-slate-500">
                Bảng này chỉ lấy từ kết quả của thao tác tạo lô vừa hoàn tất, không đưa bản sao cũ vào.
              </p>
            </div>
            <div className="rounded-lg bg-blue-50 px-4 py-2 text-sm text-blue-900">
              Tổng số vừa tạo: <strong className="text-lg">{result.createdCount}</strong>
            </div>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="data-table min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3">STT</th>
                <th className="px-5 py-3">Mã vạch</th>
                <th className="px-5 py-3">Đầu sách</th>
                <th className="px-5 py-3">Kho</th>
                <th className="px-5 py-3">Kệ</th>
                <th className="px-5 py-3">Ngày nhập</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {copyPagination.pageItems.map((copy, index) => (
                <tr key={copy.barcode}>
                  <td className="px-5 py-4 font-semibold text-slate-500">
                    {copyPagination.startIndex + index + 1}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 font-mono font-semibold text-blue-700">{copy.barcode}</td>
                  <td className="px-5 py-4 text-slate-800">
                    <div className="font-medium">{bookTitle}</div>
                    <div className="mt-1 text-xs text-slate-400">Đầu sách #{copy.bookId}</div>
                  </td>
                  <td className="px-5 py-4 text-slate-700">{copy.warehouseCode} — {copy.warehouseName}</td>
                  <td className="px-5 py-4 text-slate-700">{shelfLabel(copy)}</td>
                  <td className="whitespace-nowrap px-5 py-4 text-slate-700">{formatDate(copy.receivedDate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination
          page={copyPagination.page}
          totalItems={copyPagination.totalItems}
          totalPages={copyPagination.totalPages}
          pageSize={copyPagination.pageSize}
          onPageChange={copyPagination.goToPage}
        />
      </Card>

      <Card className="mt-6 p-5">
        <h3 className="font-semibold text-slate-900">Mã đã bỏ qua ({result.skippedBarcodes.length})</h3>
        {result.skippedBarcodes.length > 0 ? (
          <p className="mt-2 max-h-40 overflow-y-auto break-words font-mono text-sm text-amber-800">
            {result.skippedBarcodes.join(', ')}
          </p>
        ) : (
          <p className="mt-2 text-sm text-slate-500">Không có mã bị bỏ qua trong lô này.</p>
        )}
      </Card>

      <div className="mt-6 flex flex-wrap gap-3">
        <Button type="button" onClick={() => setPrintMode(true)} disabled={createdCopies.length === 0}>
          <Printer size={17} /> Mở chế độ in nhãn
        </Button>
        <Button type="button" variant="secondary" onClick={onBack}>
          <ArrowLeft size={17} /> Quay lại chi tiết đầu sách
        </Button>
      </div>
    </div>
  )
}
