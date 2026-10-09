import { useEffect, useState } from 'react'
import { ClockAlert, Phone, RefreshCw } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { tableActionClassName } from '../../components/ui/TableActionButton'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp, loanRoles } from '../s3-01-loans/loanService'
import { overdueLoanService } from './overdueLoanService'
import type { OverdueLoanItem } from './overdueLoanService'

export default function OverdueLoansPage() {
  const allowed = loanRoles.includes(getCurrentUser()?.role ?? '')
  const [items, setItems] = useState<OverdueLoanItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (!allowed) return

    let active = true
    setLoading(true)
    setError('')

    overdueLoanService.list()
      .then((data) => {
        if (active) setItems(data)
      })
      .catch((requestError: unknown) => {
        if (active) {
          setItems([])
          setError(getApiErrorMessage(
            requestError,
            'Không tải được danh sách phiếu mượn quá hạn. Vui lòng thử lại.',
          ))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [allowed, revision])

  if (!allowed) {
    return (
      <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Bạn không có quyền xem danh sách phiếu mượn quá hạn.
      </p>
    )
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Phiếu mượn quá hạn"
        description="Chỉ tính các ngày thư viện mở cửa từ sau hạn trả đến hôm nay, kể cả ngày hôm nay nếu mở cửa. Số ngày trễ nhiều nhất được ưu tiên trước."
        action={(
          <Button
            type="button"
            variant="secondary"
            loading={loading}
            onClick={() => setRevision((value) => value + 1)}
          >
            <RefreshCw size={16} />
            Làm mới
          </Button>
        )}
      />

      {error && (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <p>{error}</p>
          <p className="mt-1 text-xs text-red-600">Nhấn “Làm mới” để tải lại danh sách.</p>
        </div>
      )}

      {loading && <LoadingState />}

      {!loading && !error && items.length === 0 && (
        <EmptyState
          title="Hôm nay không có phiếu quá hạn"
          description="Không có sách chưa trả nào có hạn trả trước ngày hôm nay."
        />
      )}

      {!loading && !error && items.length > 0 && (
        <>
          <Card className="overflow-hidden xl:hidden">
            <div className="divide-y divide-slate-100">
              {items.map((item, index) => (
                <article key={item.itemId} className="space-y-4 p-4 sm:p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        STT {index + 1} · {item.loanNumber}
                      </p>
                      <p className="mt-1 break-words text-base font-semibold text-slate-950">
                        {item.readerName}
                      </p>
                    </div>
                    <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full border border-red-200 bg-red-50 px-3 py-1 text-sm font-semibold text-red-700">
                      <ClockAlert size={15} />
                      Trễ {item.overdueDays} ngày mở cửa
                    </span>
                  </div>

                  <dl className="grid gap-3 text-sm sm:grid-cols-2">
                    <div className="min-w-0">
                      <dt className="text-slate-500">Số điện thoại</dt>
                      <dd className="mt-1 break-words font-medium text-slate-900">
                        {item.readerPhone ? (
                          <a className="inline-flex items-center gap-1.5 text-blue-700 hover:underline" href={`tel:${item.readerPhone}`}>
                            <Phone size={14} />
                            {item.readerPhone}
                          </a>
                        ) : 'Chưa cập nhật'}
                      </dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-slate-500">Tên sách</dt>
                      <dd className="mt-1 break-words font-medium text-slate-900">{item.bookTitle}</dd>
                    </div>
                    <div>
                      <dt className="text-slate-500">Hạn trả</dt>
                      <dd className="mt-1 font-medium text-slate-900">{formatLoanTimestamp(item.dueAt, true)}</dd>
                    </div>
                  </dl>

                  <Link to={`/loans/${item.loanId}`} className={tableActionClassName('primary')}>
                    Xem phiếu
                  </Link>
                </article>
              ))}
            </div>
          </Card>

          <Card className="hidden overflow-hidden xl:block">
            <div className="overflow-x-auto">
              <table className="data-table min-w-full divide-y divide-slate-200 text-sm">
                <caption className="sr-only">
                  Danh sách sách quá hạn, sắp xếp theo số ngày trễ giảm dần
                </caption>
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-4 py-3">STT</th>
                    <th scope="col" className="px-4 py-3">Mã phiếu</th>
                    <th scope="col" className="px-4 py-3">Bạn đọc</th>
                    <th scope="col" className="px-4 py-3">Số điện thoại</th>
                    <th scope="col" className="px-4 py-3">Tên sách</th>
                    <th scope="col" className="px-4 py-3">Hạn trả</th>
                    <th scope="col" className="px-4 py-3 text-center">Ngày trễ (mở cửa)</th>
                    <th scope="col" className="px-4 py-3">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {items.map((item, index) => (
                    <tr key={item.itemId} className="hover:bg-slate-50/70">
                      <td className="px-4 py-4 font-semibold text-slate-500">{index + 1}</td>
                      <td className="max-w-48 break-all px-4 py-4 font-mono font-semibold text-slate-900">
                        {item.loanNumber}
                      </td>
                      <td className="max-w-56 break-words px-4 py-4 font-medium text-slate-900">
                        {item.readerName}
                      </td>
                      <td className="whitespace-nowrap px-4 py-4 text-slate-700">
                        {item.readerPhone ? (
                          <a className="text-blue-700 hover:underline" href={`tel:${item.readerPhone}`}>
                            {item.readerPhone}
                          </a>
                        ) : (
                          <span className="text-slate-400">Chưa cập nhật</span>
                        )}
                      </td>
                      <td className="max-w-72 break-words px-4 py-4 text-slate-700">{item.bookTitle}</td>
                      <td className="whitespace-nowrap px-4 py-4 text-slate-700">
                        {formatLoanTimestamp(item.dueAt, true)}
                      </td>
                      <td className="px-4 py-4 text-center">
                        <span className="inline-flex min-w-20 justify-center rounded-full border border-red-200 bg-red-50 px-3 py-1 font-semibold text-red-700">
                          {item.overdueDays} ngày mở cửa
                        </span>
                      </td>
                      <td className="px-4 py-4">
                        <Link
                          to={`/loans/${item.loanId}`}
                          className={tableActionClassName('primary')}
                          aria-label={`Xem phiếu ${item.loanNumber}`}
                        >
                          Xem phiếu
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </>
      )}
    </div>
  )
}
