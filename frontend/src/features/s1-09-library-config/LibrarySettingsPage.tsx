import {
  useMemo,
  useState,
} from 'react'

import type {
  FormEvent,
} from 'react'

import {
  CalendarDays,
  Calculator,
  Clock3,
  Pencil,
  Plus,
  Rows3,
  Save,
  Trash2,
  Warehouse,
  X,
} from 'lucide-react'

import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'

type PageMode =
  | 'warehouse'
  | 'calendar'

type Props = {
  mode: PageMode
}

/* =========================
   KHO & KỆ
========================= */

type WarehouseItem = {
  id: number
  name: string
  description: string
}

type ShelfItem = {
  id: number
  warehouseId: number
  code: string
  name: string
  copyCount: number
}

type WarehouseForm = {
  name: string
  description: string
}

type ShelfForm = {
  warehouseId: string
  code: string
  name: string
}

const WAREHOUSES_KEY =
  'libra_s1_09_warehouses'

const SHELVES_KEY =
  'libra_s1_09_shelves'

const initialWarehouses:
  WarehouseItem[] = [
  {
    id: 1,
    name: 'Kho A',
    description:
      'Kho sách chính của thư viện.',
  },
  {
    id: 2,
    name: 'Kho B',
    description:
      'Kho sách tham khảo.',
  },
]

const initialShelves:
  ShelfItem[] = [
  {
    id: 1,
    warehouseId: 1,
    code: 'A01',
    name: 'Kệ Văn học',
    copyCount: 35,
  },
  {
    id: 2,
    warehouseId: 1,
    code: 'A02',
    name: 'Kệ Công nghệ',
    copyCount: 18,
  },
  {
    id: 3,
    warehouseId: 2,
    code: 'B01',
    name: 'Kệ tham khảo',
    copyCount: 0,
  },
]

const emptyWarehouseForm:
  WarehouseForm = {
  name: '',
  description: '',
}

const emptyShelfForm:
  ShelfForm = {
  warehouseId: '',
  code: '',
  name: '',
}

/* =========================
   LỊCH HOẠT ĐỘNG
========================= */

type WeeklyScheduleItem = {
  day: number
  label: string
  open: boolean
  openTime: string
  closeTime: string
}

type ClosedDate = {
  id: number
  date: string
  name: string
}

type ClosedDateForm = {
  date: string
  name: string
}

const SCHEDULE_KEY =
  'libra_s1_09_schedule'

const CLOSED_DATES_KEY =
  'libra_s1_09_closed_dates'

const initialSchedule:
  WeeklyScheduleItem[] = [
  {
    day: 1,
    label: 'Thứ Hai',
    open: true,
    openTime: '08:00',
    closeTime: '17:00',
  },
  {
    day: 2,
    label: 'Thứ Ba',
    open: true,
    openTime: '08:00',
    closeTime: '17:00',
  },
  {
    day: 3,
    label: 'Thứ Tư',
    open: true,
    openTime: '08:00',
    closeTime: '17:00',
  },
  {
    day: 4,
    label: 'Thứ Năm',
    open: true,
    openTime: '08:00',
    closeTime: '17:00',
  },
  {
    day: 5,
    label: 'Thứ Sáu',
    open: true,
    openTime: '08:00',
    closeTime: '17:00',
  },
  {
    day: 6,
    label: 'Thứ Bảy',
    open: true,
    openTime: '08:00',
    closeTime: '12:00',
  },
  {
    day: 7,
    label: 'Chủ Nhật',
    open: false,
    openTime: '08:00',
    closeTime: '17:00',
  },
]

const initialClosedDates:
  ClosedDate[] = [
  {
    id: 1,
    date: '2026-01-01',
    name: 'Tết Dương lịch',
  },
  {
    id: 2,
    date: '2026-09-02',
    name: 'Quốc khánh',
  },
]

const emptyClosedDateForm:
  ClosedDateForm = {
  date: '',
  name: '',
}

/* =========================
   LOCAL STORAGE
========================= */

function readStorage<T>(
  key: string,
  fallback: T,
): T {
  try {
    const raw =
      localStorage.getItem(key)

    if (!raw) {
      return fallback
    }

    return JSON.parse(raw)
  } catch {
    return fallback
  }
}

/* =========================
   COMPONENT CHÍNH
========================= */

export default function LibrarySettingsPage({
  mode,
}: Props) {
  if (mode === 'warehouse') {
    return <WarehouseShelfPage />
  }

  return <LibraryCalendarPage />
}

/* =========================
   KHO & KỆ
========================= */

function WarehouseShelfPage() {
  const [
    warehouses,
    setWarehouses,
  ] = useState<WarehouseItem[]>(
    () =>
      readStorage(
        WAREHOUSES_KEY,
        initialWarehouses,
      ),
  )

  const [
    shelves,
    setShelves,
  ] = useState<ShelfItem[]>(
    () =>
      readStorage(
        SHELVES_KEY,
        initialShelves,
      ),
  )

  const [
    warehouseModal,
    setWarehouseModal,
  ] = useState(false)

  const [
    shelfModal,
    setShelfModal,
  ] = useState(false)

  const [
    editingWarehouseId,
    setEditingWarehouseId,
  ] = useState<number | null>(
    null,
  )

  const [
    editingShelfId,
    setEditingShelfId,
  ] = useState<number | null>(
    null,
  )

  const [
    warehouseForm,
    setWarehouseForm,
  ] = useState<WarehouseForm>(
    emptyWarehouseForm,
  )

  const [
    shelfForm,
    setShelfForm,
  ] = useState<ShelfForm>(
    emptyShelfForm,
  )

  const [error, setError] =
    useState('')

  const [notice, setNotice] =
    useState('')

  const saveWarehouses = (
    data: WarehouseItem[],
  ) => {
    setWarehouses(data)

    localStorage.setItem(
      WAREHOUSES_KEY,
      JSON.stringify(data),
    )
  }

  const saveShelves = (
    data: ShelfItem[],
  ) => {
    setShelves(data)

    localStorage.setItem(
      SHELVES_KEY,
      JSON.stringify(data),
    )
  }

  const openCreateWarehouse =
    () => {
      setEditingWarehouseId(null)

      setWarehouseForm(
        emptyWarehouseForm,
      )

      setError('')
      setWarehouseModal(true)
    }

  const openEditWarehouse = (
    item: WarehouseItem,
  ) => {
    setEditingWarehouseId(
      item.id,
    )

    setWarehouseForm({
      name: item.name,
      description:
        item.description,
    })

    setError('')
    setWarehouseModal(true)
  }

  const openCreateShelf = () => {
    setEditingShelfId(null)

    setShelfForm({
      ...emptyShelfForm,
      warehouseId:
        warehouses.length > 0
          ? String(
              warehouses[0].id,
            )
          : '',
    })

    setError('')
    setShelfModal(true)
  }

  const openEditShelf = (
    item: ShelfItem,
  ) => {
    setEditingShelfId(item.id)

    setShelfForm({
      warehouseId: String(
        item.warehouseId,
      ),
      code: item.code,
      name: item.name,
    })

    setError('')
    setShelfModal(true)
  }

  const handleWarehouseSubmit = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()

    setError('')
    setNotice('')

    const name =
      warehouseForm.name.trim()

    if (!name) {
      setError(
        'Tên kho không được để trống.',
      )
      return
    }

    const duplicate =
      warehouses.some(
        (item) =>
          item.name
            .trim()
            .toLowerCase() ===
            name.toLowerCase() &&
          item.id !==
            editingWarehouseId,
      )

    if (duplicate) {
      setError(
        'Tên kho đã tồn tại.',
      )
      return
    }

    if (
      editingWarehouseId !== null
    ) {
      const updated =
        warehouses.map(
          (item) =>
            item.id ===
            editingWarehouseId
              ? {
                  ...item,
                  name,
                  description:
                    warehouseForm
                      .description
                      .trim(),
                }
              : item,
        )

      saveWarehouses(updated)

      setNotice(
        'Cập nhật kho thành công.',
      )
    } else {
      saveWarehouses([
        ...warehouses,
        {
          id: Date.now(),
          name,
          description:
            warehouseForm
              .description
              .trim(),
        },
      ])

      setNotice(
        'Thêm kho thành công.',
      )
    }

    setWarehouseModal(false)
  }

  const handleShelfSubmit = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()

    setError('')
    setNotice('')

    const warehouseId =
      Number(
        shelfForm.warehouseId,
      )

    const code =
      shelfForm.code
        .trim()
        .toUpperCase()

    const name =
      shelfForm.name.trim()

    if (!warehouseId) {
      setError(
        'Vui lòng chọn kho.',
      )
      return
    }

    if (!code) {
      setError(
        'Mã kệ không được để trống.',
      )
      return
    }

    if (!name) {
      setError(
        'Tên kệ không được để trống.',
      )
      return
    }

    /*
     * Mã kệ phải duy nhất
     * trong cùng một kho.
     */
    const duplicate =
      shelves.some(
        (item) =>
          item.warehouseId ===
            warehouseId &&
          item.code
            .trim()
            .toLowerCase() ===
            code.toLowerCase() &&
          item.id !==
            editingShelfId,
      )

    if (duplicate) {
      setError(
        'Mã kệ đã tồn tại trong kho này.',
      )
      return
    }

    if (
      editingShelfId !== null
    ) {
      const updated =
        shelves.map(
          (item) =>
            item.id ===
            editingShelfId
              ? {
                  ...item,
                  warehouseId,
                  code,
                  name,
                }
              : item,
        )

      saveShelves(updated)

      setNotice(
        'Cập nhật kệ thành công.',
      )
    } else {
      saveShelves([
        ...shelves,
        {
          id: Date.now(),
          warehouseId,
          code,
          name,
          copyCount: 0,
        },
      ])

      setNotice(
        'Thêm kệ thành công.',
      )
    }

    setShelfModal(false)
  }

  const handleDeleteShelf = (
    item: ShelfItem,
  ) => {
    setError('')
    setNotice('')

    /*
     * AC S1-09:
     * Không cho xóa kệ đang có
     * bản sao sách.
     */
    if (item.copyCount > 0) {
      setError(
        `Không thể xóa kệ ${item.code} vì đang có ${item.copyCount} bản sao sách.`,
      )
      return
    }

    const accepted =
      window.confirm(
        `Bạn có chắc muốn xóa kệ ${item.code}?`,
      )

    if (!accepted) {
      return
    }

    saveShelves(
      shelves.filter(
        (shelf) =>
          shelf.id !== item.id,
      ),
    )

    setNotice(
      `Đã xóa kệ ${item.code}.`,
    )
  }

  const getWarehouseName = (
    id: number,
  ) =>
    warehouses.find(
      (item) =>
        item.id === id,
    )?.name ?? 'Không xác định'

  return (
    <div className="space-y-6">
      <PageHeader
        title="Kho & kệ"
        description="Quản lý vị trí lưu trữ sách trong thư viện."
      />

      {error && (
        <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {notice && (
        <div className="rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          {notice}
        </div>
      )}

      {/* THỐNG KÊ */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Tổng số kho
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {warehouses.length}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Tổng số kệ
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {shelves.length}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Bản sao đang xếp kệ
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {shelves.reduce(
                (
                  total,
                  item,
                ) =>
                  total +
                  item.copyCount,
                0,
              )}
            </p>
          </div>
        </Card>
      </div>

      {/* KHO */}
      <Card>
        <div className="flex items-center justify-between border-b border-slate-200 p-5">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              Danh sách kho
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              Các khu vực lưu trữ sách
              của thư viện.
            </p>
          </div>

          <button
            type="button"
            onClick={
              openCreateWarehouse
            }
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Plus size={18} />
            Thêm kho
          </button>
        </div>

        <div className="grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-3">
          {warehouses.map(
            (item) => {
              const shelfCount =
                shelves.filter(
                  (shelf) =>
                    shelf.warehouseId ===
                    item.id,
                ).length

              return (
                <div
                  key={item.id}
                  className="rounded-xl border border-slate-200 p-5"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
                      <Warehouse
                        size={20}
                      />
                    </div>

                    <button
                      type="button"
                      onClick={() =>
                        openEditWarehouse(
                          item,
                        )
                      }
                      className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50"
                    >
                      <Pencil
                        size={16}
                      />
                    </button>
                  </div>

                  <h3 className="mt-4 font-semibold text-slate-900">
                    {item.name}
                  </h3>

                  <p className="mt-1 min-h-10 text-sm text-slate-500">
                    {item.description ||
                      'Không có mô tả'}
                  </p>

                  <div className="mt-4 border-t border-slate-100 pt-3 text-sm text-slate-600">
                    {shelfCount} kệ
                  </div>
                </div>
              )
            },
          )}
        </div>
      </Card>

      {/* KỆ */}
      <Card>
        <div className="flex items-center justify-between border-b border-slate-200 p-5">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              Danh sách kệ
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              Mã kệ phải duy nhất trong
              từng kho.
            </p>
          </div>

          <button
            type="button"
            onClick={
              openCreateShelf
            }
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Plus size={18} />
            Thêm kệ
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[850px]">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                  Mã kệ
                </th>

                <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                  Tên kệ
                </th>

                <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                  Kho
                </th>

                <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                  Bản sao
                </th>

                <th className="px-5 py-3 text-right text-xs font-semibold uppercase text-slate-500">
                  Thao tác
                </th>
              </tr>
            </thead>

            <tbody>
              {shelves.map(
                (item) => (
                  <tr
                    key={item.id}
                    className="border-b border-slate-100 hover:bg-slate-50"
                  >
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet-50 text-violet-600">
                          <Rows3
                            size={18}
                          />
                        </div>

                        <span className="font-semibold text-slate-900">
                          {item.code}
                        </span>
                      </div>
                    </td>

                    <td className="px-5 py-4 text-sm text-slate-700">
                      {item.name}
                    </td>

                    <td className="px-5 py-4 text-sm text-slate-700">
                      {getWarehouseName(
                        item.warehouseId,
                      )}
                    </td>

                    <td className="px-5 py-4 text-center">
                      <span
                        className={`rounded-full px-3 py-1 text-xs font-medium ${
                          item.copyCount >
                          0
                            ? 'bg-blue-50 text-blue-700'
                            : 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        {
                          item.copyCount
                        }{' '}
                        bản
                      </span>
                    </td>

                    <td className="px-5 py-4">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          title="Chỉnh sửa"
                          onClick={() =>
                            openEditShelf(
                              item,
                            )
                          }
                          className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-blue-50 hover:text-blue-600"
                        >
                          <Pencil
                            size={16}
                          />
                        </button>

                        <button
                          type="button"
                          title="Xóa kệ"
                          onClick={() =>
                            handleDeleteShelf(
                              item,
                            )
                          }
                          className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-red-50 hover:text-red-600"
                        >
                          <Trash2
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

      <div className="rounded-xl border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-700">
        Kệ đang có bản sao sách không
        thể bị xóa khỏi hệ thống.
      </div>

      {/* MODAL KHO */}
      {warehouseModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-lg rounded-2xl bg-white shadow-2xl">
            <ModalHeader
              title={
                editingWarehouseId !==
                null
                  ? 'Chỉnh sửa kho'
                  : 'Thêm kho'
              }
              onClose={() =>
                setWarehouseModal(
                  false,
                )
              }
            />

            <form
              onSubmit={
                handleWarehouseSubmit
              }
              className="space-y-5 p-6"
            >
              <TextField
                label="Tên kho"
                value={
                  warehouseForm.name
                }
                required
                onChange={(value) => {
                  setWarehouseForm({
                    ...warehouseForm,
                    name: value,
                  })
                  setError('')
                }}
              />

              <TextAreaField
                label="Mô tả"
                value={
                  warehouseForm
                    .description
                }
                onChange={(value) =>
                  setWarehouseForm({
                    ...warehouseForm,
                    description:
                      value,
                  })
                }
              />

              {error && (
                <ErrorBox
                  message={error}
                />
              )}

              <ModalActions
                submitText={
                  editingWarehouseId !==
                  null
                    ? 'Lưu thay đổi'
                    : 'Thêm kho'
                }
                onCancel={() =>
                  setWarehouseModal(
                    false,
                  )
                }
              />
            </form>
          </div>
        </div>
      )}

      {/* MODAL KỆ */}
      {shelfModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-lg rounded-2xl bg-white shadow-2xl">
            <ModalHeader
              title={
                editingShelfId !== null
                  ? 'Chỉnh sửa kệ'
                  : 'Thêm kệ'
              }
              onClose={() =>
                setShelfModal(false)
              }
            />

            <form
              onSubmit={
                handleShelfSubmit
              }
              className="space-y-5 p-6"
            >
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Kho
                  <span className="ml-1 text-red-500">
                    *
                  </span>
                </label>

                <select
                  value={
                    shelfForm.warehouseId
                  }
                  onChange={(event) => {
                    setShelfForm({
                      ...shelfForm,
                      warehouseId:
                        event.target
                          .value,
                    })
                    setError('')
                  }}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                >
                  <option value="">
                    Chọn kho
                  </option>

                  {warehouses.map(
                    (item) => (
                      <option
                        key={item.id}
                        value={item.id}
                      >
                        {item.name}
                      </option>
                    ),
                  )}
                </select>
              </div>

              <TextField
                label="Mã kệ"
                value={
                  shelfForm.code
                }
                required
                placeholder="Ví dụ: A03"
                onChange={(value) => {
                  setShelfForm({
                    ...shelfForm,
                    code: value,
                  })
                  setError('')
                }}
              />

              <TextField
                label="Tên kệ"
                value={
                  shelfForm.name
                }
                required
                placeholder="Ví dụ: Kệ Khoa học"
                onChange={(value) => {
                  setShelfForm({
                    ...shelfForm,
                    name: value,
                  })
                  setError('')
                }}
              />

              {error && (
                <ErrorBox
                  message={error}
                />
              )}

              <ModalActions
                submitText={
                  editingShelfId !==
                  null
                    ? 'Lưu thay đổi'
                    : 'Thêm kệ'
                }
                onCancel={() =>
                  setShelfModal(false)
                }
              />
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

/* =========================
   LỊCH ĐÓNG CỬA
========================= */

function LibraryCalendarPage() {
  const [
    schedule,
    setSchedule,
  ] = useState<
    WeeklyScheduleItem[]
  >(() =>
    readStorage(
      SCHEDULE_KEY,
      initialSchedule,
    ),
  )

  const [
    closedDates,
    setClosedDates,
  ] = useState<ClosedDate[]>(
    () =>
      readStorage(
        CLOSED_DATES_KEY,
        initialClosedDates,
      ),
  )

  const [
    holidayModal,
    setHolidayModal,
  ] = useState(false)

  const [
    editingHolidayId,
    setEditingHolidayId,
  ] = useState<number | null>(
    null,
  )

  const [
    holidayForm,
    setHolidayForm,
  ] = useState<ClosedDateForm>(
    emptyClosedDateForm,
  )

  const [dueDate, setDueDate] =
    useState('')

  const [error, setError] =
    useState('')

  const [notice, setNotice] =
    useState('')

  const saveSchedule = () => {
    setError('')
    setNotice('')

    const invalid =
      schedule.find(
        (item) =>
          item.open &&
          item.closeTime <=
            item.openTime,
      )

    if (invalid) {
      setError(
        `Giờ đóng cửa của ${invalid.label} phải sau giờ mở cửa.`,
      )
      return
    }

    localStorage.setItem(
      SCHEDULE_KEY,
      JSON.stringify(schedule),
    )

    setNotice(
      'Đã lưu lịch làm việc theo tuần.',
    )
  }

  const saveClosedDates = (
    data: ClosedDate[],
  ) => {
    setClosedDates(data)

    localStorage.setItem(
      CLOSED_DATES_KEY,
      JSON.stringify(data),
    )
  }

  const updateSchedule = (
    day: number,
    changes:
      Partial<WeeklyScheduleItem>,
  ) => {
    setSchedule((current) =>
      current.map((item) =>
        item.day === day
          ? {
              ...item,
              ...changes,
            }
          : item,
      ),
    )

    setNotice('')
    setError('')
  }

  const openCreateHoliday = () => {
    setEditingHolidayId(null)
    setHolidayForm(
      emptyClosedDateForm,
    )
    setError('')
    setHolidayModal(true)
  }

  const openEditHoliday = (
    item: ClosedDate,
  ) => {
    setEditingHolidayId(
      item.id,
    )

    setHolidayForm({
      date: item.date,
      name: item.name,
    })

    setError('')
    setHolidayModal(true)
  }

  const handleHolidaySubmit = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()

    setError('')
    setNotice('')

    if (!holidayForm.date) {
      setError(
        'Vui lòng chọn ngày nghỉ.',
      )
      return
    }

    if (
      !holidayForm.name.trim()
    ) {
      setError(
        'Tên ngày nghỉ không được để trống.',
      )
      return
    }

    const duplicate =
      closedDates.some(
        (item) =>
          item.date ===
            holidayForm.date &&
          item.id !==
            editingHolidayId,
      )

    if (duplicate) {
      setError(
        'Ngày này đã được khai báo là ngày đóng cửa.',
      )
      return
    }

    if (
      editingHolidayId !== null
    ) {
      const updated =
        closedDates.map(
          (item) =>
            item.id ===
            editingHolidayId
              ? {
                  ...item,
                  date:
                    holidayForm.date,
                  name:
                    holidayForm.name.trim(),
                }
              : item,
        )

      saveClosedDates(updated)

      setNotice(
        'Cập nhật ngày đóng cửa thành công.',
      )
    } else {
      saveClosedDates([
        ...closedDates,
        {
          id: Date.now(),
          date:
            holidayForm.date,
          name:
            holidayForm.name.trim(),
        },
      ])

      setNotice(
        'Thêm ngày đóng cửa thành công.',
      )
    }

    setHolidayModal(false)
  }

  const removeHoliday = (
    item: ClosedDate,
  ) => {
    const accepted =
      window.confirm(
        `Xóa ngày nghỉ "${item.name}"?`,
      )

    if (!accepted) {
      return
    }

    saveClosedDates(
      closedDates.filter(
        (date) =>
          date.id !== item.id,
      ),
    )

    setNotice(
      'Đã xóa ngày đóng cửa.',
    )
  }

  const sortedClosedDates =
    useMemo(
      () =>
        [...closedDates].sort(
          (a, b) =>
            a.date.localeCompare(
              b.date,
            ),
        ),
      [closedDates],
    )

  const adjustedDueDate =
    useMemo(() => {
      if (!dueDate) {
        return ''
      }

      return findNextOpenDate(
        dueDate,
        schedule,
        closedDates,
      )
    }, [
      dueDate,
      schedule,
      closedDates,
    ])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Lịch đóng cửa"
        description="Quản lý lịch làm việc hàng tuần và các ngày nghỉ cụ thể của thư viện."
      />

      {error && (
        <ErrorBox
          message={error}
        />
      )}

      {notice && (
        <div className="rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          {notice}
        </div>
      )}

      {/* LỊCH TUẦN */}
      <Card>
        <div className="flex items-center justify-between border-b border-slate-200 p-5">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
              <Clock3 size={20} />
            </div>

            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Lịch làm việc theo tuần
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                Thiết lập ngày mở cửa và
                giờ hoạt động.
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={saveSchedule}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Save size={17} />
            Lưu lịch
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[700px]">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                  Ngày
                </th>

                <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                  Hoạt động
                </th>

                <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                  Mở cửa
                </th>

                <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
                  Đóng cửa
                </th>
              </tr>
            </thead>

            <tbody>
              {schedule.map(
                (item) => (
                  <tr
                    key={item.day}
                    className="border-b border-slate-100"
                  >
                    <td className="px-5 py-4 font-medium text-slate-800">
                      {item.label}
                    </td>

                    <td className="px-5 py-4 text-center">
                      <label className="inline-flex cursor-pointer items-center gap-2">
                        <input
                          type="checkbox"
                          checked={
                            item.open
                          }
                          onChange={(
                            event,
                          ) =>
                            updateSchedule(
                              item.day,
                              {
                                open:
                                  event
                                    .target
                                    .checked,
                              },
                            )
                          }
                          className="h-4 w-4 rounded"
                        />

                        <span
                          className={`text-sm ${
                            item.open
                              ? 'text-emerald-700'
                              : 'text-slate-500'
                          }`}
                        >
                          {item.open
                            ? 'Mở cửa'
                            : 'Đóng cửa'}
                        </span>
                      </label>
                    </td>

                    <td className="px-5 py-4 text-center">
                      <input
                        type="time"
                        disabled={
                          !item.open
                        }
                        value={
                          item.openTime
                        }
                        onChange={(
                          event,
                        ) =>
                          updateSchedule(
                            item.day,
                            {
                              openTime:
                                event
                                  .target
                                  .value,
                            },
                          )
                        }
                        className="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-100 disabled:text-slate-400"
                      />
                    </td>

                    <td className="px-5 py-4 text-center">
                      <input
                        type="time"
                        disabled={
                          !item.open
                        }
                        value={
                          item.closeTime
                        }
                        onChange={(
                          event,
                        ) =>
                          updateSchedule(
                            item.day,
                            {
                              closeTime:
                                event
                                  .target
                                  .value,
                            },
                          )
                        }
                        className="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-100 disabled:text-slate-400"
                      />
                    </td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* NGÀY NGHỈ */}
      <Card>
        <div className="flex items-center justify-between border-b border-slate-200 p-5">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-violet-50 text-violet-600">
              <CalendarDays
                size={20}
              />
            </div>

            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Ngày nghỉ / ngày đóng cửa
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                Có thể khai báo trước
                các ngày nghỉ cho cả năm.
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={
              openCreateHoliday
            }
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Plus size={18} />
            Thêm ngày nghỉ
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[650px]">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                  Ngày
                </th>

                <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                  Lý do
                </th>

                <th className="px-5 py-3 text-right text-xs font-semibold uppercase text-slate-500">
                  Thao tác
                </th>
              </tr>
            </thead>

            <tbody>
              {sortedClosedDates.map(
                (item) => (
                  <tr
                    key={item.id}
                    className="border-b border-slate-100"
                  >
                    <td className="px-5 py-4 font-medium text-slate-800">
                      {formatDateVi(
                        item.date,
                      )}
                    </td>

                    <td className="px-5 py-4 text-sm text-slate-600">
                      {item.name}
                    </td>

                    <td className="px-5 py-4">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() =>
                            openEditHoliday(
                              item,
                            )
                          }
                          className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-blue-50 hover:text-blue-600"
                        >
                          <Pencil
                            size={16}
                          />
                        </button>

                        <button
                          type="button"
                          onClick={() =>
                            removeHoliday(
                              item,
                            )
                          }
                          className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-red-50 hover:text-red-600"
                        >
                          <Trash2
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

      {/* KIỂM TRA HẠN TRẢ */}
      <Card>
        <div className="border-b border-slate-200 p-5">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 text-amber-600">
              <Calculator
                size={20}
              />
            </div>

            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Kiểm tra ngày hạn trả
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                Mô phỏng quy tắc tự đẩy
                hạn trả sang ngày mở cửa
                kế tiếp.
              </p>
            </div>
          </div>
        </div>

        <div className="p-5">
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Hạn trả dự kiến
              </label>

              <input
                type="date"
                value={dueDate}
                onChange={(event) =>
                  setDueDate(
                    event.target.value,
                  )
                }
                className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
              />
            </div>

            <div>
              <p className="mb-1.5 text-sm font-medium text-slate-700">
                Hạn trả thực tế
              </p>

              <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm">
                {!dueDate ? (
                  <span className="text-slate-400">
                    Chọn ngày để kiểm tra
                  </span>
                ) : adjustedDueDate ===
                  dueDate ? (
                  <span className="font-medium text-emerald-700">
                    {formatDateVi(
                      adjustedDueDate,
                    )}{' '}
                    — Thư viện mở cửa
                  </span>
                ) : (
                  <span className="font-medium text-amber-700">
                    {formatDateVi(
                      adjustedDueDate,
                    )}{' '}
                    — Đã tự chuyển sang
                    ngày mở cửa kế tiếp
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      </Card>

      {/* MODAL NGÀY NGHỈ */}
      {holidayModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-lg rounded-2xl bg-white shadow-2xl">
            <ModalHeader
              title={
                editingHolidayId !==
                null
                  ? 'Chỉnh sửa ngày nghỉ'
                  : 'Thêm ngày nghỉ'
              }
              onClose={() =>
                setHolidayModal(
                  false,
                )
              }
            />

            <form
              onSubmit={
                handleHolidaySubmit
              }
              className="space-y-5 p-6"
            >
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Ngày
                  <span className="ml-1 text-red-500">
                    *
                  </span>
                </label>

                <input
                  type="date"
                  value={
                    holidayForm.date
                  }
                  onChange={(event) => {
                    setHolidayForm({
                      ...holidayForm,
                      date:
                        event.target
                          .value,
                    })

                    setError('')
                  }}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                />
              </div>

              <TextField
                label="Tên ngày nghỉ"
                value={
                  holidayForm.name
                }
                required
                placeholder="Ví dụ: Nghỉ Quốc khánh"
                onChange={(value) => {
                  setHolidayForm({
                    ...holidayForm,
                    name: value,
                  })

                  setError('')
                }}
              />

              {error && (
                <ErrorBox
                  message={error}
                />
              )}

              <ModalActions
                submitText={
                  editingHolidayId !==
                  null
                    ? 'Lưu thay đổi'
                    : 'Thêm ngày nghỉ'
                }
                onCancel={() =>
                  setHolidayModal(
                    false,
                  )
                }
              />
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

/* =========================
   DATE HELPERS
========================= */

function parseLocalDate(
  value: string,
) {
  return new Date(
    `${value}T12:00:00`,
  )
}

function toIsoDate(
  date: Date,
) {
  const year =
    date.getFullYear()

  const month =
    String(
      date.getMonth() + 1,
    ).padStart(2, '0')

  const day =
    String(
      date.getDate(),
    ).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function getLibraryDay(
  date: Date,
) {
  const jsDay =
    date.getDay()

  return jsDay === 0
    ? 7
    : jsDay
}

function isOpenDate(
  dateString: string,
  schedule:
    WeeklyScheduleItem[],
  closedDates:
    ClosedDate[],
) {
  const explicitlyClosed =
    closedDates.some(
      (item) =>
        item.date ===
        dateString,
    )

  if (explicitlyClosed) {
    return false
  }

  const date =
    parseLocalDate(dateString)

  const day =
    getLibraryDay(date)

  return (
    schedule.find(
      (item) =>
        item.day === day,
    )?.open ?? false
  )
}

function findNextOpenDate(
  dateString: string,
  schedule:
    WeeklyScheduleItem[],
  closedDates:
    ClosedDate[],
) {
  let current =
    parseLocalDate(dateString)

  for (
    let index = 0;
    index < 370;
    index += 1
  ) {
    const value =
      toIsoDate(current)

    if (
      isOpenDate(
        value,
        schedule,
        closedDates,
      )
    ) {
      return value
    }

    current.setDate(
      current.getDate() + 1,
    )
  }

  return dateString
}

function formatDateVi(
  value: string,
) {
  if (!value) {
    return ''
  }

  return parseLocalDate(
    value,
  ).toLocaleDateString(
    'vi-VN',
  )
}

/* =========================
   UI COMPONENTS
========================= */

type ModalHeaderProps = {
  title: string
  onClose: () => void
}

function ModalHeader({
  title,
  onClose,
}: ModalHeaderProps) {
  return (
    <div className="flex items-center justify-between border-b border-slate-200 px-6 py-5">
      <h2 className="text-xl font-semibold text-slate-900">
        {title}
      </h2>

      <button
        type="button"
        onClick={onClose}
        className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"
      >
        <X size={20} />
      </button>
    </div>
  )
}

type TextFieldProps = {
  label: string
  value: string
  onChange: (
    value: string,
  ) => void
  required?: boolean
  placeholder?: string
}

function TextField({
  label,
  value,
  onChange,
  required = false,
  placeholder,
}: TextFieldProps) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}

        {required && (
          <span className="ml-1 text-red-500">
            *
          </span>
        )}
      </label>

      <input
        value={value}
        onChange={(event) =>
          onChange(
            event.target.value,
          )
        }
        placeholder={placeholder}
        className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
      />
    </div>
  )
}

type TextAreaFieldProps = {
  label: string
  value: string
  onChange: (
    value: string,
  ) => void
}

function TextAreaField({
  label,
  value,
  onChange,
}: TextAreaFieldProps) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </label>

      <textarea
        rows={3}
        value={value}
        onChange={(event) =>
          onChange(
            event.target.value,
          )
        }
        className="w-full resize-none rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
      />
    </div>
  )
}

type ModalActionsProps = {
  submitText: string
  onCancel: () => void
}

function ModalActions({
  submitText,
  onCancel,
}: ModalActionsProps) {
  return (
    <div className="flex justify-end gap-3 border-t border-slate-100 pt-5">
      <button
        type="button"
        onClick={onCancel}
        className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
      >
        Hủy
      </button>

      <button
        type="submit"
        className="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
      >
        {submitText}
      </button>
    </div>
  )
}

function ErrorBox({
  message,
}: {
  message: string
}) {
  return (
    <div className="rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
      {message}
    </div>
  )
}