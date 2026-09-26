import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import axios from 'axios'
import {
  AlertCircle,
  CheckCircle2,
  CreditCard,
  History,
  Pencil,
  Plus,
  Power,
  RefreshCw,
  Trash2,
  Users,
  X,
} from 'lucide-react'

import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'
import LoadingState from '../../components/ui/LoadingState'
import {
  cardTypeService,
  type CardType,
  type CardTypeForm,
  type PolicyHistory,
} from './cardTypeService'

type NumberFieldProps = {
  label: string
  value: number
  onChange: (value: number) => void
  max?: number
  hint?: string
}

const emptyForm: CardTypeForm = {
  name: '',
  description: '',
  duration: 12,
  maxBooks: 5,
  loanDays: 14,
  maxRenewals: 2,
  renewalDays: 7,
}

function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return (
      error.response?.data?.message ||
      error.message ||
      'Không thể kết nối đến máy chủ API.'
    )
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Đã xảy ra lỗi không xác định.'
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  try {
    const d = new Date(dateStr)
    if (isNaN(d.getTime())) return dateStr
    return d.toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    })
  } catch {
    return dateStr
  }
}

export default function CardTypesPage() {
  const [cardTypes, setCardTypes] = useState<CardType[]>([])
  const [history, setHistory] = useState<PolicyHistory[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null)

  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<CardTypeForm>(emptyForm)

  const [formError, setFormError] = useState('')
  const [globalError, setGlobalError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')

  const fetchData = async (isManualRefresh = false) => {
    if (isManualRefresh) {
      setRefreshing(true)
    }
    try {
      setGlobalError('')
      const [cardsData, historyData] = await Promise.all([
        cardTypeService.getCardTypes(),
        cardTypeService.getHistory(),
      ])
      setCardTypes(cardsData)
      setHistory(historyData)
    } catch (err) {
      setGlobalError(getErrorMessage(err))
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => {
    fetchData()
  }, [])

  // Tự động ẩn thông báo thành công sau 4 giây
  useEffect(() => {
    if (!successMessage) return
    const timer = setTimeout(() => {
      setSuccessMessage('')
    }, 4000)
    return () => clearTimeout(timer)
  }, [successMessage])

  const openCreateModal = () => {
    setEditingId(null)
    setForm(emptyForm)
    setFormError('')
    setIsModalOpen(true)
  }

  const openEditModal = (cardType: CardType) => {
    setEditingId(cardType.id)
    setForm({
      name: cardType.name,
      description: cardType.description || '',
      duration: cardType.duration,
      maxBooks: cardType.maxBooks,
      loanDays: cardType.loanDays,
      maxRenewals: cardType.maxRenewals,
      renewalDays: cardType.renewalDays,
    })
    setFormError('')
    setIsModalOpen(true)
  }

  const closeModal = () => {
    setIsModalOpen(false)
    setEditingId(null)
    setFormError('')
  }

  const handleToggleStatus = async (id: number) => {
    try {
      setActionLoadingId(id)
      setGlobalError('')
      const updated = await cardTypeService.toggleStatus(id)
      setCardTypes((current) =>
        current.map((item) => (item.id === id ? updated : item))
      )
      setSuccessMessage(
        updated.active
          ? `Đã kích hoạt lại loại thẻ "${updated.name}".`
          : `Đã tạm ngừng áp dụng loại thẻ "${updated.name}".`
      )
      // Tải lại lịch sử thay đổi mới nhất từ backend
      const historyData = await cardTypeService.getHistory()
      setHistory(historyData)
    } catch (err) {
      setGlobalError(getErrorMessage(err))
    } finally {
      setActionLoadingId(null)
    }
  }

  const handleDelete = async (cardType: CardType) => {
    if (cardType.usageCount && cardType.usageCount > 0) {
      setGlobalError(
        `Không thể xóa loại thẻ "${cardType.name}" vì hiện đang có ${cardType.usageCount} độc giả gắn với thẻ này.`
      )
      return
    }

    if (
      !window.confirm(
        `Bạn có chắc chắn muốn xóa loại thẻ "${cardType.name}" không? Thao tác này không thể hoàn tác.`
      )
    ) {
      return
    }

    try {
      setActionLoadingId(cardType.id)
      setGlobalError('')
      await cardTypeService.deleteCardType(cardType.id)
      setSuccessMessage(`Đã xóa loại thẻ "${cardType.name}" thành công!`)
      await fetchData()
    } catch (err) {
      setGlobalError(getErrorMessage(err))
    } finally {
      setActionLoadingId(null)
    }
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError('')

    const name = form.name.trim()
    if (!name) {
      setFormError('Tên loại thẻ không được để trống.')
      return
    }

    if (
      form.duration <= 0 ||
      form.maxBooks <= 0 ||
      form.loanDays <= 0 ||
      form.maxRenewals <= 0 ||
      form.renewalDays <= 0
    ) {
      setFormError('Tất cả các giá trị số phải lớn hơn 0.')
      return
    }

    if (form.maxBooks > 10) {
      setFormError('Số sách tối đa không được vượt quá 10.')
      return
    }

    if (form.loanDays > 60) {
      setFormError('Số ngày mượn không được vượt quá 60 ngày.')
      return
    }

    if (form.maxRenewals > 10) {
      setFormError('Số lần gia hạn tối đa không được vượt quá 10 lần.')
      return
    }

    if (form.renewalDays > 30) {
      setFormError('Số ngày gia hạn mỗi lần không được vượt quá 30 ngày.')
      return
    }

    if (form.duration > 120) {
      setFormError('Thời hạn thẻ không được vượt quá 120 tháng.')
      return
    }

    setIsSubmitting(true)

    try {
      if (editingId !== null) {
        await cardTypeService.updateCardType(editingId, {
          ...form,
          name,
        })
        setSuccessMessage(`Cập nhật chính sách "${name}" thành công!`)
      } else {
        await cardTypeService.createCardType({
          ...form,
          name,
        })
        setSuccessMessage(`Thêm mới loại thẻ "${name}" thành công!`)
      }
      closeModal()
      await fetchData()
    } catch (err) {
      setFormError(getErrorMessage(err))
    } finally {
      setIsSubmitting(false)
    }
  }

  const activeCount = cardTypes.filter((item) => item.active).length
  const inactiveCount = cardTypes.filter((item) => !item.active).length
  const maxBooksLimit = cardTypes.length > 0
    ? Math.max(...cardTypes.map((c) => c.maxBooks))
    : 10

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <PageHeader
          title="Chính sách mượn"
          description="Quản lý các loại thẻ và quy định mượn, gia hạn sách áp dụng cho từng đối tượng độc giả."
        />

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => fetchData(true)}
            disabled={refreshing || loading}
            title="Tải lại dữ liệu"
            className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50"
          >
            <RefreshCw size={16} className={refreshing ? 'animate-spin' : ''} />
            Làm mới
          </button>

          <button
            type="button"
            onClick={openCreateModal}
            className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700"
          >
            <Plus size={18} />
            Thêm loại thẻ
          </button>
        </div>
      </div>

      {/* THÔNG BÁO GLOBAL */}
      {globalError && (
        <div className="flex items-center justify-between rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800 shadow-sm">
          <div className="flex items-center gap-3">
            <AlertCircle size={20} className="shrink-0 text-red-600" />
            <span>{globalError}</span>
          </div>
          <button
            type="button"
            onClick={() => setGlobalError('')}
            className="rounded p-1 text-red-600 hover:bg-red-100"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {successMessage && (
        <div className="flex items-center justify-between rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 shadow-sm">
          <div className="flex items-center gap-3">
            <CheckCircle2 size={20} className="shrink-0 text-emerald-600" />
            <span>{successMessage}</span>
          </div>
          <button
            type="button"
            onClick={() => setSuccessMessage('')}
            className="rounded p-1 text-emerald-600 hover:bg-emerald-100"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* THỐNG KÊ */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <div className="p-5">
            <p className="text-sm font-medium text-slate-500">Tổng loại thẻ</p>
            <p className="mt-2 text-2xl font-bold text-slate-900">
              {cardTypes.length}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm font-medium text-slate-500">Đang áp dụng</p>
            <p className="mt-2 text-2xl font-bold text-emerald-600">
              {activeCount}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm font-medium text-slate-500">Ngừng áp dụng</p>
            <p className="mt-2 text-2xl font-bold text-slate-500">
              {inactiveCount}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm font-medium text-slate-500">Mượn tối đa</p>
            <p className="mt-2 text-2xl font-bold text-blue-600">
              {maxBooksLimit} <span className="text-sm font-normal text-slate-500">cuốn</span>
            </p>
          </div>
        </Card>
      </div>

      {/* BẢNG CHÍNH SÁCH */}
      <Card>
        <div className="border-b border-slate-200 p-5">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Danh sách loại thẻ và hạn mức mượn
              </h2>
              <p className="mt-0.5 text-sm text-slate-500">
                Thiết lập số sách mượn, ngày mượn và lượt gia hạn theo từng loại đối tượng độc giả.
              </p>
            </div>
          </div>
        </div>

        {loading ? (
          <div className="p-12">
            <LoadingState />
          </div>
        ) : cardTypes.length === 0 ? (
          <div className="p-12 text-center">
            <CreditCard size={40} className="mx-auto text-slate-300" />
            <p className="mt-3 font-medium text-slate-700">Chưa có loại thẻ nào</p>
            <p className="mt-1 text-sm text-slate-500">
              Nhấn &quot;Thêm loại thẻ&quot; để khai báo chính sách mượn đầu tiên.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1050px]">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Loại thẻ
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                    Sách tối đa
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                    Ngày mượn
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                    Số lần gia hạn
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                    Ngày / lần
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                    Thời hạn thẻ
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                    Độc giả gắn thẻ
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                    Trạng thái
                  </th>
                  <th className="px-5 py-3 text-right text-xs font-semibold uppercase text-slate-500">
                    Thao tác
                  </th>
                </tr>
              </thead>

              <tbody>
                {cardTypes.map((cardType) => {
                  const isActionLoading = actionLoadingId === cardType.id
                  return (
                    <tr
                      key={cardType.id}
                      className="border-b border-slate-100 transition hover:bg-slate-50/80"
                    >
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
                            <CreditCard size={20} />
                          </div>
                          <div>
                            <p className="font-semibold text-slate-900">
                              {cardType.name}
                            </p>
                            <p className="mt-0.5 text-xs text-slate-500">
                              {cardType.description || 'Không có mô tả'}
                            </p>
                          </div>
                        </div>
                      </td>

                      <td className="px-5 py-4 text-center text-sm font-semibold text-slate-800">
                        {cardType.maxBooks} <span className="text-xs font-normal text-slate-500">cuốn</span>
                      </td>

                      <td className="px-5 py-4 text-center text-sm text-slate-700">
                        {cardType.loanDays} ngày
                      </td>

                      <td className="px-5 py-4 text-center text-sm text-slate-700">
                        {cardType.maxRenewals} lần
                      </td>

                      <td className="px-5 py-4 text-center text-sm text-slate-700">
                        {cardType.renewalDays} ngày
                      </td>

                      <td className="px-5 py-4 text-center text-sm text-slate-700">
                        {cardType.duration} tháng
                      </td>

                      <td className="px-5 py-4 text-center">
                        <span className="inline-flex items-center gap-1 text-xs font-medium text-slate-600 bg-slate-100 px-2.5 py-1 rounded-md">
                          <Users size={12} />
                          {cardType.usageCount ?? 0}
                        </span>
                      </td>

                      <td className="px-5 py-4 text-center">
                        {cardType.active ? (
                          <span className="inline-flex items-center rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
                            Đang áp dụng
                          </span>
                        ) : (
                          <span className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600 ring-1 ring-inset ring-slate-500/20">
                            Ngừng áp dụng
                          </span>
                        )}
                      </td>

                      <td className="px-5 py-4 text-right">
                        <div className="flex justify-end gap-1.5">
                          <button
                            type="button"
                            title="Chỉnh sửa chính sách"
                            disabled={isActionLoading}
                            onClick={() => openEditModal(cardType)}
                            className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-600 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 disabled:opacity-50"
                          >
                            <Pencil size={15} />
                          </button>

                          <button
                            type="button"
                            title={
                              cardType.active
                                ? 'Tạm ngừng áp dụng thẻ này'
                                : 'Kích hoạt áp dụng lại thẻ này'
                            }
                            disabled={isActionLoading}
                            onClick={() => handleToggleStatus(cardType.id)}
                            className={`inline-flex h-8 w-8 items-center justify-center rounded-lg border transition disabled:opacity-50 ${
                              cardType.active
                                ? 'border-amber-200 text-amber-600 hover:bg-amber-50'
                                : 'border-emerald-200 text-emerald-600 hover:bg-emerald-50'
                            }`}
                          >
                            <Power size={15} />
                          </button>

                          <button
                            type="button"
                            title={
                              (cardType.usageCount ?? 0) > 0
                                ? 'Không thể xóa loại thẻ đang được sử dụng'
                                : 'Xóa loại thẻ'
                            }
                            disabled={isActionLoading || (cardType.usageCount ?? 0) > 0}
                            onClick={() => handleDelete(cardType)}
                            className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-400 transition hover:border-red-300 hover:bg-red-50 hover:text-red-600 disabled:opacity-30 disabled:cursor-not-allowed"
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* QUY TẮC */}
      <div className="rounded-xl border border-blue-100 bg-blue-50/70 p-4">
        <p className="text-sm font-semibold text-blue-900">
          Quy tắc áp dụng chính sách mượn
        </p>
        <p className="mt-1 text-sm leading-6 text-blue-700">
          Khi cập nhật hoặc điều chỉnh hạn mức mượn, chính sách mới chỉ có hiệu lực với các phiếu mượn
          được tạo sau thời điểm cập nhật. Các phiếu mượn đang diễn ra sẽ bảo lưu chính sách tại thời điểm lập phiếu.
        </p>
      </div>

      {/* LỊCH SỬ THAY ĐỔI */}
      <Card>
        <div className="border-b border-slate-200 p-5">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
              <History size={20} />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Nhật ký kiểm toán thay đổi chính sách
              </h2>
              <p className="mt-0.5 text-sm text-slate-500">
                Theo dõi ai đã thay đổi, thời điểm thay đổi và chi tiết trước/sau khi cập nhật.
              </p>
            </div>
          </div>
        </div>

        {history.length === 0 ? (
          <div className="px-6 py-12 text-center">
            <History size={36} className="mx-auto text-slate-300" />
            <p className="mt-3 font-medium text-slate-700">Chưa có bản ghi thay đổi nào</p>
            <p className="mt-1 text-sm text-slate-500">
              Các thao tác thêm, chỉnh sửa hoặc bật/tắt chính sách sẽ được ghi nhận tự động vào đây.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1100px]">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Thời điểm
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Loại thẻ
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Hành động
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Người thực hiện
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Trước thay đổi
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Sau thay đổi
                  </th>
                </tr>
              </thead>

              <tbody>
                {history.map((item) => (
                  <tr key={item.id} className="border-b border-slate-100 align-top hover:bg-slate-50/50">
                    <td className="whitespace-nowrap px-5 py-4 text-sm text-slate-600 font-mono text-xs">
                      {formatDate(item.changedAt)}
                    </td>

                    <td className="whitespace-nowrap px-5 py-4 text-sm font-semibold text-slate-900">
                      {item.cardTypeName}
                    </td>

                    <td className="whitespace-nowrap px-5 py-4">
                      <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20">
                        {item.action}
                      </span>
                    </td>

                    <td className="whitespace-nowrap px-5 py-4 text-sm text-slate-700 font-medium">
                      {item.changedBy || 'Hệ thống'}
                    </td>

                    <td className="max-w-[320px] px-5 py-4 text-xs leading-5 text-slate-500 font-mono bg-slate-50/50 rounded">
                      {item.before || '-'}
                    </td>

                    <td className="max-w-[320px] px-5 py-4 text-xs leading-5 text-slate-700 font-mono bg-blue-50/30 rounded">
                      {item.after || '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* MODAL THÊM / SỬA LOẠI THẺ */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
          <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-200 px-6 py-5">
              <div>
                <h2 className="text-xl font-bold text-slate-900">
                  {editingId !== null ? 'Chỉnh sửa chính sách loại thẻ' : 'Thêm mới loại thẻ'}
                </h2>
                <p className="mt-1 text-sm text-slate-500">
                  Thiết lập tên loại thẻ và các hạn mức mượn, gia hạn sách chi tiết.
                </p>
              </div>

              <button
                type="button"
                onClick={closeModal}
                className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
              >
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-5 p-6">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Tên loại thẻ <span className="ml-0.5 text-red-500">*</span>
                </label>
                <input
                  value={form.name}
                  onChange={(e) => {
                    setForm({ ...form, name: e.target.value })
                    setFormError('')
                  }}
                  placeholder="Ví dụ: Thẻ sinh viên, Thẻ giảng viên, Thẻ nghiên cứu..."
                  className="w-full rounded-lg border border-slate-300 px-3.5 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Mô tả đối tượng
                </label>
                <textarea
                  rows={2}
                  value={form.description}
                  onChange={(e) => {
                    setForm({ ...form, description: e.target.value })
                    setFormError('')
                  }}
                  placeholder="Mô tả phạm vi và quyền lợi của đối tượng áp dụng loại thẻ..."
                  className="w-full resize-none rounded-lg border border-slate-300 px-3.5 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <NumberField
                  label="Số sách mượn tối đa"
                  value={form.maxBooks}
                  max={10}
                  hint="Tối đa 10 cuốn / bạn đọc"
                  onChange={(value) => {
                    setForm({ ...form, maxBooks: value })
                    setFormError('')
                  }}
                />

                <NumberField
                  label="Số ngày mượn một lần"
                  value={form.loanDays}
                  max={60}
                  hint="Tối đa 60 ngày"
                  onChange={(value) => {
                    setForm({ ...form, loanDays: value })
                    setFormError('')
                  }}
                />

                <NumberField
                  label="Số lần gia hạn tối đa"
                  value={form.maxRenewals}
                  max={10}
                  hint="Tối đa 10 lần"
                  onChange={(value) => {
                    setForm({ ...form, maxRenewals: value })
                    setFormError('')
                  }}
                />

                <NumberField
                  label="Số ngày mỗi lần gia hạn"
                  value={form.renewalDays}
                  max={30}
                  hint="Tối đa 30 ngày / lần"
                  onChange={(value) => {
                    setForm({ ...form, renewalDays: value })
                    setFormError('')
                  }}
                />

                <NumberField
                  label="Thời hạn hiệu lực của thẻ (tháng)"
                  value={form.duration}
                  max={120}
                  hint="Mặc định: 12 tháng (tối đa 120 tháng)"
                  onChange={(value) => {
                    setForm({ ...form, duration: value })
                    setFormError('')
                  }}
                />
              </div>

              {formError && (
                <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  <AlertCircle size={16} className="shrink-0 text-red-500" />
                  <span>{formError}</span>
                </div>
              )}

              <div className="flex justify-end gap-3 border-t border-slate-100 pt-5">
                <button
                  type="button"
                  onClick={closeModal}
                  disabled={isSubmitting}
                  className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
                >
                  Hủy
                </button>

                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:opacity-50"
                >
                  {isSubmitting ? (
                    <>
                      <RefreshCw size={16} className="animate-spin" />
                      Đang xử lý...
                    </>
                  ) : editingId !== null ? (
                    'Lưu thay đổi'
                  ) : (
                    'Thêm loại thẻ'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

function NumberField({
  label,
  value,
  onChange,
  max,
  hint,
}: NumberFieldProps) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label} <span className="ml-0.5 text-red-500">*</span>
      </label>

      <input
        type="number"
        min={1}
        max={max}
        value={value || ''}
        onChange={(event) => onChange(Number(event.target.value))}
        className="w-full rounded-lg border border-slate-300 px-3.5 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
      />

      {hint && <p className="mt-1 text-xs text-slate-400">{hint}</p>}
    </div>
  )
}