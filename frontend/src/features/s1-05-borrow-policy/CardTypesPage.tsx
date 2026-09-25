import { useState } from 'react'
import type { FormEvent } from 'react'

import {
  CreditCard,
  History,
  Pencil,
  Plus,
  Power,
  X,
} from 'lucide-react'

import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'

type CardType = {
  id: number
  name: string
  description: string
  duration: number
  maxBooks: number
  loanDays: number
  maxRenewals: number
  renewalDays: number
  active: boolean
}

type CardTypeForm = {
  name: string
  description: string
  duration: number
  maxBooks: number
  loanDays: number
  maxRenewals: number
  renewalDays: number
}

type NumberFieldProps = {
  label: string
  value: number
  onChange: (value: number) => void
  max?: number
  hint?: string
}

type PolicyHistory = {
  id: number
  cardTypeName: string
  action: string
  changedBy: string
  changedAt: string
  before: string
  after: string
}

const CURRENT_USER = 'Quản lý thư viện'

const initialCardTypes: CardType[] = [
  {
    id: 1,
    name: 'Thẻ sinh viên',
    description: 'Dành cho sinh viên đang học tại trường.',
    duration: 12,
    maxBooks: 5,
    loanDays: 14,
    maxRenewals: 2,
    renewalDays: 7,
    active: true,
  },
  {
    id: 2,
    name: 'Thẻ cán bộ',
    description: 'Dành cho cán bộ, giảng viên và nhân viên.',
    duration: 24,
    maxBooks: 10,
    loanDays: 30,
    maxRenewals: 3,
    renewalDays: 14,
    active: true,
  },
]

const emptyForm: CardTypeForm = {
  name: '',
  description: '',
  duration: 12,
  maxBooks: 1,
  loanDays: 1,
  maxRenewals: 1,
  renewalDays: 1,
}

function formatPolicy(
  cardType: CardType,
) {
  return [
    `${cardType.maxBooks} sách`,
    `${cardType.loanDays} ngày mượn`,
    `${cardType.maxRenewals} lần gia hạn`,
    `${cardType.renewalDays} ngày/lần`,
    `${cardType.duration} tháng`,
    cardType.active
      ? 'Đang áp dụng'
      : 'Ngừng áp dụng',
  ].join(' • ')
}

export default function CardTypesPage() {
  const [cardTypes, setCardTypes] =
    useState<CardType[]>(initialCardTypes)

  const [history, setHistory] =
    useState<PolicyHistory[]>([])

  const [isModalOpen, setIsModalOpen] =
    useState(false)

  const [editingId, setEditingId] =
    useState<number | null>(null)

  const [form, setForm] =
    useState<CardTypeForm>(emptyForm)

  const [error, setError] =
    useState('')

  const addHistory = (
    cardTypeName: string,
    action: string,
    before: string,
    after: string,
  ) => {
    const record: PolicyHistory = {
      id: Date.now(),
      cardTypeName,
      action,
      changedBy: CURRENT_USER,
      changedAt:
        new Date().toLocaleString(
          'vi-VN',
        ),
      before,
      after,
    }

    setHistory((current) => [
      record,
      ...current,
    ])
  }

  const openCreateModal = () => {
    setEditingId(null)
    setForm(emptyForm)
    setError('')
    setIsModalOpen(true)
  }

  const openEditModal = (
    cardType: CardType,
  ) => {
    setEditingId(cardType.id)

    setForm({
      name: cardType.name,
      description:
        cardType.description,
      duration: cardType.duration,
      maxBooks: cardType.maxBooks,
      loanDays: cardType.loanDays,
      maxRenewals:
        cardType.maxRenewals,
      renewalDays:
        cardType.renewalDays,
    })

    setError('')
    setIsModalOpen(true)
  }

  const closeModal = () => {
    setIsModalOpen(false)
    setEditingId(null)
    setError('')
  }

  const handleToggleStatus = (
    id: number,
  ) => {
    const oldItem =
      cardTypes.find(
        (item) => item.id === id,
      )

    if (!oldItem) {
      return
    }

    const updatedItem: CardType = {
      ...oldItem,
      active: !oldItem.active,
    }

    setCardTypes((current) =>
      current.map((item) =>
        item.id === id
          ? updatedItem
          : item,
      ),
    )

    addHistory(
      oldItem.name,
      oldItem.active
        ? 'Ngừng áp dụng'
        : 'Áp dụng lại',
      formatPolicy(oldItem),
      formatPolicy(updatedItem),
    )
  }

  const handleSubmit = (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()

    const name =
      form.name.trim()

    if (!name) {
      setError(
        'Tên loại thẻ không được để trống.',
      )
      return
    }

    if (
      form.duration <= 0 ||
      form.maxBooks <= 0 ||
      form.loanDays <= 0 ||
      form.maxRenewals <= 0 ||
      form.renewalDays <= 0
    ) {
      setError(
        'Tất cả các giá trị số phải lớn hơn 0.',
      )
      return
    }

    if (form.maxBooks > 10) {
      setError(
        'Số sách tối đa không được vượt quá 10.',
      )
      return
    }

    if (form.loanDays > 60) {
      setError(
        'Số ngày mượn không được vượt quá 60.',
      )
      return
    }

    const duplicatedName =
      cardTypes.some(
        (item) =>
          item.name
            .trim()
            .toLowerCase() ===
            name.toLowerCase() &&
          item.id !== editingId,
      )

    if (duplicatedName) {
      setError(
        'Tên loại thẻ đã tồn tại.',
      )
      return
    }

    if (editingId !== null) {
      const oldItem =
        cardTypes.find(
          (item) =>
            item.id === editingId,
        )

      if (!oldItem) {
        return
      }

      const updatedItem: CardType = {
        ...oldItem,
        ...form,
        name,
      }

      const before =
        formatPolicy(oldItem)

      const after =
        formatPolicy(updatedItem)

      setCardTypes((current) =>
        current.map((item) =>
          item.id === editingId
            ? updatedItem
            : item,
        ),
      )

      if (
        before !== after ||
        oldItem.name !== name ||
        oldItem.description !==
          form.description
      ) {
        addHistory(
          name,
          'Chỉnh sửa chính sách',
          before,
          after,
        )
      }
    } else {
      const newCardType: CardType = {
        id: Date.now(),
        ...form,
        name,
        active: true,
      }

      setCardTypes((current) => [
        ...current,
        newCardType,
      ])

      addHistory(
        name,
        'Thêm loại thẻ',
        'Chưa tồn tại',
        formatPolicy(newCardType),
      )
    }

    closeModal()
  }

  const activeCount =
    cardTypes.filter(
      (item) => item.active,
    ).length

  const inactiveCount =
    cardTypes.filter(
      (item) => !item.active,
    ).length

  return (
    <div className="space-y-6">
      <PageHeader
        title="Chính sách mượn"
        description="Quản lý loại thẻ và các quy định mượn sách áp dụng cho từng nhóm bạn đọc."
      />

      {/* THỐNG KÊ */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Tổng loại thẻ
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {cardTypes.length}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Đang áp dụng
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {activeCount}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Ngừng áp dụng
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {inactiveCount}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Số sách tối đa
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-900">
              10
            </p>
          </div>
        </Card>
      </div>

      {/* BẢNG CHÍNH SÁCH */}
      <Card>
        <div className="border-b border-slate-200 p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Loại thẻ và chính sách mượn
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                Thiết lập giới hạn mượn và
                gia hạn cho từng loại thẻ.
              </p>
            </div>

            <button
              type="button"
              onClick={openCreateModal}
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700"
            >
              <Plus size={18} />
              Thêm loại thẻ
            </button>
          </div>
        </div>

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
                  Trạng thái
                </th>

                <th className="px-5 py-3 text-right text-xs font-semibold uppercase text-slate-500">
                  Thao tác
                </th>
              </tr>
            </thead>

            <tbody>
              {cardTypes.map(
                (cardType) => (
                  <tr
                    key={cardType.id}
                    className="border-b border-slate-100 transition hover:bg-slate-50"
                  >
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
                          <CreditCard
                            size={20}
                          />
                        </div>

                        <div>
                          <p className="font-medium text-slate-900">
                            {cardType.name}
                          </p>

                          <p className="mt-0.5 text-xs text-slate-500">
                            {
                              cardType.description
                            }
                          </p>
                        </div>
                      </div>
                    </td>

                    <td className="px-5 py-4 text-center text-sm text-slate-700">
                      {cardType.maxBooks}
                    </td>

                    <td className="px-5 py-4 text-center text-sm text-slate-700">
                      {cardType.loanDays}{' '}
                      ngày
                    </td>

                    <td className="px-5 py-4 text-center text-sm text-slate-700">
                      {
                        cardType.maxRenewals
                      }{' '}
                      lần
                    </td>

                    <td className="px-5 py-4 text-center text-sm text-slate-700">
                      {
                        cardType.renewalDays
                      }{' '}
                      ngày
                    </td>

                    <td className="px-5 py-4 text-center text-sm text-slate-700">
                      {cardType.duration}{' '}
                      tháng
                    </td>

                    <td className="px-5 py-4 text-center">
                      {cardType.active ? (
                        <span className="inline-flex rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
                          Đang áp dụng
                        </span>
                      ) : (
                        <span className="inline-flex rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
                          Ngừng áp dụng
                        </span>
                      )}
                    </td>

                    <td className="px-5 py-4">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          title="Chỉnh sửa"
                          onClick={() =>
                            openEditModal(
                              cardType,
                            )
                          }
                          className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-600"
                        >
                          <Pencil
                            size={16}
                          />
                        </button>

                        <button
                          type="button"
                          title={
                            cardType.active
                              ? 'Ngừng áp dụng'
                              : 'Áp dụng lại'
                          }
                          onClick={() =>
                            handleToggleStatus(
                              cardType.id,
                            )
                          }
                          className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 transition hover:bg-slate-100"
                        >
                          <Power
                            size={16}
                          />
                        </button>
                      </div>
                    </td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* QUY TẮC */}
      <div className="rounded-xl border border-blue-100 bg-blue-50 p-4">
        <p className="text-sm font-semibold text-blue-900">
          Quy tắc áp dụng chính sách
        </p>

        <p className="mt-1 text-sm leading-6 text-blue-700">
          Khi thay đổi chính sách, quy định
          mới chỉ áp dụng cho các phiếu mượn
          được tạo sau thời điểm cập nhật.
          Các phiếu đang mở giữ nguyên chính
          sách cũ.
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
                Lịch sử thay đổi chính sách
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                Theo dõi người sửa, thời
                điểm và giá trị trước/sau.
              </p>
            </div>
          </div>
        </div>

        {history.length === 0 ? (
          <div className="px-6 py-12 text-center">
            <History
              size={36}
              className="mx-auto text-slate-300"
            />

            <p className="mt-3 font-medium text-slate-700">
              Chưa có thay đổi
            </p>

            <p className="mt-1 text-sm text-slate-500">
              Các thao tác thêm, sửa hoặc
              thay đổi trạng thái sẽ xuất
              hiện tại đây.
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
                {history.map(
                  (item) => (
                    <tr
                      key={item.id}
                      className="border-b border-slate-100 align-top"
                    >
                      <td className="whitespace-nowrap px-5 py-4 text-sm text-slate-600">
                        {
                          item.changedAt
                        }
                      </td>

                      <td className="whitespace-nowrap px-5 py-4 text-sm font-medium text-slate-900">
                        {
                          item.cardTypeName
                        }
                      </td>

                      <td className="whitespace-nowrap px-5 py-4">
                        <span className="rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700">
                          {
                            item.action
                          }
                        </span>
                      </td>

                      <td className="whitespace-nowrap px-5 py-4 text-sm text-slate-600">
                        {
                          item.changedBy
                        }
                      </td>

                      <td className="max-w-[300px] px-5 py-4 text-sm leading-6 text-slate-500">
                        {item.before}
                      </td>

                      <td className="max-w-[300px] px-5 py-4 text-sm leading-6 text-slate-700">
                        {item.after}
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* MODAL THÊM / SỬA */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-200 px-6 py-5">
              <div>
                <h2 className="text-xl font-semibold text-slate-900">
                  {editingId !== null
                    ? 'Chỉnh sửa loại thẻ'
                    : 'Thêm loại thẻ'}
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  Thiết lập loại thẻ và
                  chính sách mượn tương ứng.
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

            <form
              onSubmit={handleSubmit}
              className="space-y-5 p-6"
            >
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Tên loại thẻ
                  <span className="ml-1 text-red-500">
                    *
                  </span>
                </label>

                <input
                  value={form.name}
                  onChange={(event) => {
                    setForm({
                      ...form,
                      name:
                        event.target
                          .value,
                    })
                    setError('')
                  }}
                  placeholder="Ví dụ: Thẻ sinh viên"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Mô tả
                </label>

                <textarea
                  rows={3}
                  value={
                    form.description
                  }
                  onChange={(event) => {
                    setForm({
                      ...form,
                      description:
                        event.target
                          .value,
                    })
                    setError('')
                  }}
                  placeholder="Mô tả đối tượng sử dụng loại thẻ..."
                  className="w-full resize-none rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <NumberField
                  label="Số sách tối đa"
                  value={
                    form.maxBooks
                  }
                  max={10}
                  hint="Tối đa 10 cuốn"
                  onChange={(value) => {
                    setForm({
                      ...form,
                      maxBooks:
                        value,
                    })
                    setError('')
                  }}
                />

                <NumberField
                  label="Số ngày mượn"
                  value={
                    form.loanDays
                  }
                  max={60}
                  hint="Tối đa 60 ngày"
                  onChange={(value) => {
                    setForm({
                      ...form,
                      loanDays:
                        value,
                    })
                    setError('')
                  }}
                />

                <NumberField
                  label="Số lần gia hạn tối đa"
                  value={
                    form.maxRenewals
                  }
                  onChange={(value) => {
                    setForm({
                      ...form,
                      maxRenewals:
                        value,
                    })
                    setError('')
                  }}
                />

                <NumberField
                  label="Số ngày mỗi lần gia hạn"
                  value={
                    form.renewalDays
                  }
                  onChange={(value) => {
                    setForm({
                      ...form,
                      renewalDays:
                        value,
                    })
                    setError('')
                  }}
                />

                <NumberField
                  label="Thời hạn thẻ (tháng)"
                  value={
                    form.duration
                  }
                  onChange={(value) => {
                    setForm({
                      ...form,
                      duration:
                        value,
                    })
                    setError('')
                  }}
                />
              </div>

              {error && (
                <div className="rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
                  {error}
                </div>
              )}

              <div className="flex justify-end gap-3 border-t border-slate-100 pt-5">
                <button
                  type="button"
                  onClick={closeModal}
                  className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
                >
                  Hủy
                </button>

                <button
                  type="submit"
                  className="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700"
                >
                  {editingId !== null
                    ? 'Lưu thay đổi'
                    : 'Thêm loại thẻ'}
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
        {label}
        <span className="ml-1 text-red-500">
          *
        </span>
      </label>

      <input
        type="number"
        min={1}
        max={max}
        value={value}
        onChange={(event) =>
          onChange(
            Number(
              event.target.value,
            ),
          )
        }
        className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
      />

      {hint && (
        <p className="mt-1 text-xs text-slate-400">
          {hint}
        </p>
      )}
    </div>
  )
}