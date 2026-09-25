import {
  useMemo,
  useState,
} from 'react'

import type {
  FormEvent,
} from 'react'

import {
  BookOpen,
  Pencil,
  Plus,
  Power,
  Search,
  Tags,
  UserRound,
  X,
} from 'lucide-react'

import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'

type PageMode =
  | 'authors'
  | 'categories'

type Author = {
  id: number
  name: string
  note: string
  bookCount: number
  active: boolean
}

type Category = {
  id: number
  name: string
  description: string
  parentId: number | null
  bookCount: number
  active: boolean
}

type FormState = {
  name: string
  description: string
  parentId: string
}

type Props = {
  mode: PageMode
}

const AUTHORS_KEY =
  'libra_s1_08_authors'

const CATEGORIES_KEY =
  'libra_s1_08_categories'

const initialAuthors: Author[] = [
  {
    id: 1,
    name: 'Nguyễn Nhật Ánh',
    note: 'Tác giả văn học Việt Nam.',
    bookCount: 12,
    active: true,
  },
  {
    id: 2,
    name: 'Nam Cao',
    note: 'Nhà văn hiện thực Việt Nam.',
    bookCount: 8,
    active: true,
  },
  {
    id: 3,
    name: 'Tô Hoài',
    note: 'Tác giả nhiều tác phẩm văn học thiếu nhi.',
    bookCount: 5,
    active: true,
  },
]

const initialCategories: Category[] = [
  {
    id: 1,
    name: 'Văn học',
    description:
      'Các tác phẩm văn học.',
    parentId: null,
    bookCount: 25,
    active: true,
  },
  {
    id: 2,
    name: 'Văn học trong nước',
    description:
      'Tác phẩm văn học Việt Nam.',
    parentId: 1,
    bookCount: 18,
    active: true,
  },
  {
    id: 3,
    name: 'Văn học nước ngoài',
    description:
      'Tác phẩm văn học nước ngoài.',
    parentId: 1,
    bookCount: 7,
    active: true,
  },
  {
    id: 4,
    name: 'Công nghệ thông tin',
    description:
      'Sách về máy tính và công nghệ.',
    parentId: null,
    bookCount: 14,
    active: true,
  },
]

const emptyForm: FormState = {
  name: '',
  description: '',
  parentId: '',
}

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

export default function CatalogManagementPage({
  mode,
}: Props) {
  const [authors, setAuthors] =
    useState<Author[]>(() =>
      readStorage(
        AUTHORS_KEY,
        initialAuthors,
      ),
    )

  const [categories, setCategories] =
    useState<Category[]>(() =>
      readStorage(
        CATEGORIES_KEY,
        initialCategories,
      ),
    )

  const [search, setSearch] =
    useState('')

  const [showInactive, setShowInactive] =
    useState(true)

  const [isModalOpen, setIsModalOpen] =
    useState(false)

  const [editingId, setEditingId] =
    useState<number | null>(null)

  const [form, setForm] =
    useState<FormState>(emptyForm)

  const [error, setError] =
    useState('')

  const isAuthors =
    mode === 'authors'

  const saveAuthors = (
    data: Author[],
  ) => {
    setAuthors(data)

    localStorage.setItem(
      AUTHORS_KEY,
      JSON.stringify(data),
    )
  }

  const saveCategories = (
    data: Category[],
  ) => {
    setCategories(data)

    localStorage.setItem(
      CATEGORIES_KEY,
      JSON.stringify(data),
    )
  }

  const openCreateModal = () => {
    setEditingId(null)
    setForm(emptyForm)
    setError('')
    setIsModalOpen(true)
  }

  const openEditAuthor = (
    item: Author,
  ) => {
    setEditingId(item.id)

    setForm({
      name: item.name,
      description: item.note,
      parentId: '',
    })

    setError('')
    setIsModalOpen(true)
  }

  const openEditCategory = (
    item: Category,
  ) => {
    setEditingId(item.id)

    setForm({
      name: item.name,
      description:
        item.description,
      parentId:
        item.parentId === null
          ? ''
          : String(
              item.parentId,
            ),
    })

    setError('')
    setIsModalOpen(true)
  }

  const closeModal = () => {
    setIsModalOpen(false)
    setEditingId(null)
    setForm(emptyForm)
    setError('')
  }

  const handleAuthorSubmit = () => {
    const name =
      form.name.trim()

    const duplicate =
      authors.some(
        (item) =>
          item.name
            .trim()
            .toLowerCase() ===
            name.toLowerCase() &&
          item.id !== editingId,
      )

    if (duplicate) {
      setError(
        'Tên tác giả đã tồn tại.',
      )
      return
    }

    if (editingId !== null) {
      const updated =
        authors.map(
          (item) =>
            item.id === editingId
              ? {
                  ...item,
                  name,
                  note:
                    form.description
                      .trim(),
                }
              : item,
        )

      saveAuthors(updated)
    } else {
      const newAuthor: Author = {
        id: Date.now(),
        name,
        note:
          form.description.trim(),
        bookCount: 0,
        active: true,
      }

      saveAuthors([
        ...authors,
        newAuthor,
      ])
    }

    closeModal()
  }

  const handleCategorySubmit = () => {
    const name =
      form.name.trim()

    const parentId =
      form.parentId
        ? Number(form.parentId)
        : null

    /*
     * Chặn trường hợp chỉnh sửa một
     * danh mục thành con của chính nó.
     */
    if (
      editingId !== null &&
      parentId === editingId
    ) {
      setError(
        'Thể loại không thể là thể loại cha của chính nó.',
      )
      return
    }

    /*
     * Chỉ cho chọn danh mục cấp 1
     * làm cha => tối đa 2 cấp.
     */
    if (
      parentId !== null
    ) {
      const parent =
        categories.find(
          (item) =>
            item.id === parentId,
        )

      if (!parent) {
        setError(
          'Thể loại cha không tồn tại.',
        )
        return
      }

      if (
        parent.parentId !== null
      ) {
        setError(
          'Thể loại chỉ được xếp tối đa 2 cấp.',
        )
        return
      }
    }

    /*
     * Chặn tên trùng trong cùng
     * một cấp / cùng thể loại cha.
     */
    const duplicate =
      categories.some(
        (item) =>
          item.name
            .trim()
            .toLowerCase() ===
            name.toLowerCase() &&
          item.parentId ===
            parentId &&
          item.id !== editingId,
      )

    if (duplicate) {
      setError(
        'Tên thể loại đã tồn tại trong cùng danh mục.',
      )
      return
    }

    /*
     * Nếu danh mục đang có con,
     * không cho biến nó thành
     * danh mục cấp 2.
     */
    if (
      editingId !== null &&
      parentId !== null
    ) {
      const hasChildren =
        categories.some(
          (item) =>
            item.parentId ===
            editingId,
        )

      if (hasChildren) {
        setError(
          'Thể loại đang có thể loại con nên không thể chuyển thành cấp 2.',
        )
        return
      }
    }

    if (editingId !== null) {
      const updated =
        categories.map(
          (item) =>
            item.id === editingId
              ? {
                  ...item,
                  name,
                  description:
                    form.description
                      .trim(),
                  parentId,
                }
              : item,
        )

      saveCategories(updated)
    } else {
      const newCategory:
        Category = {
        id: Date.now(),
        name,
        description:
          form.description.trim(),
        parentId,
        bookCount: 0,
        active: true,
      }

      saveCategories([
        ...categories,
        newCategory,
      ])
    }

    closeModal()
  }

  const handleSubmit = (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()

    setError('')

    if (!form.name.trim()) {
      setError(
        isAuthors
          ? 'Tên tác giả không được để trống.'
          : 'Tên thể loại không được để trống.',
      )
      return
    }

    if (isAuthors) {
      handleAuthorSubmit()
    } else {
      handleCategorySubmit()
    }
  }

  const toggleAuthor = (
    id: number,
  ) => {
    const updated =
      authors.map(
        (item) =>
          item.id === id
            ? {
                ...item,
                active:
                  !item.active,
              }
            : item,
      )

    saveAuthors(updated)
  }

  const toggleCategory = (
    id: number,
  ) => {
    const item =
      categories.find(
        (category) =>
          category.id === id,
      )

    if (!item) {
      return
    }

    const nextActive =
      !item.active

    /*
     * Khi ngừng danh mục cha,
     * ngừng luôn các danh mục con.
     */
    const updated =
      categories.map(
        (category) => {
          if (
            category.id === id
          ) {
            return {
              ...category,
              active:
                nextActive,
            }
          }

          if (
            !nextActive &&
            category.parentId ===
              id
          ) {
            return {
              ...category,
              active: false,
            }
          }

          return category
        },
      )

    saveCategories(updated)
  }

  const filteredAuthors =
    useMemo(() => {
      const keyword =
        search
          .trim()
          .toLowerCase()

      return authors.filter(
        (item) => {
          const matchesSearch =
            !keyword ||
            item.name
              .toLowerCase()
              .includes(keyword) ||
            item.note
              .toLowerCase()
              .includes(keyword)

          const matchesStatus =
            showInactive ||
            item.active

          return (
            matchesSearch &&
            matchesStatus
          )
        },
      )
    }, [
      authors,
      search,
      showInactive,
    ])

  const filteredCategories =
    useMemo(() => {
      const keyword =
        search
          .trim()
          .toLowerCase()

      return categories.filter(
        (item) => {
          const matchesSearch =
            !keyword ||
            item.name
              .toLowerCase()
              .includes(keyword) ||
            item.description
              .toLowerCase()
              .includes(keyword)

          const matchesStatus =
            showInactive ||
            item.active

          return (
            matchesSearch &&
            matchesStatus
          )
        },
      )
    }, [
      categories,
      search,
      showInactive,
    ])

  /*
   * Chỉ danh mục cấp 1 đang hoạt động
   * mới được chọn làm cha.
   */
  const parentOptions =
    categories.filter(
      (item) =>
        item.parentId === null &&
        item.active &&
        item.id !== editingId,
    )

  const totalCount =
    isAuthors
      ? authors.length
      : categories.length

  const activeCount =
    isAuthors
      ? authors.filter(
          (item) =>
            item.active,
        ).length
      : categories.filter(
          (item) =>
            item.active,
        ).length

  const inactiveCount =
    totalCount - activeCount

  return (
    <div className="space-y-6">
      <PageHeader
        title={
          isAuthors
            ? 'Quản lý tác giả'
            : 'Quản lý thể loại'
        }
        description={
          isAuthors
            ? 'Quản lý danh mục tác giả dùng khi biên mục sách.'
            : 'Quản lý danh mục thể loại và cấu trúc phân cấp tối đa 2 cấp.'
        }
      />

      {/* THỐNG KÊ */}
      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              {isAuthors
                ? 'Tổng tác giả'
                : 'Tổng thể loại'}
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {totalCount}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Đang sử dụng
            </p>

            <p className="mt-2 text-2xl font-semibold text-emerald-700">
              {activeCount}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Ngừng sử dụng
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-700">
              {inactiveCount}
            </p>
          </div>
        </Card>
      </div>

      {/* DANH SÁCH */}
      <Card>
        <div className="border-b border-slate-200 p-5">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                {isAuthors
                  ? 'Danh sách tác giả'
                  : 'Danh sách thể loại'}
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                {isAuthors
                  ? 'Thêm, chỉnh sửa hoặc ngừng sử dụng tác giả.'
                  : 'Thêm, chỉnh sửa hoặc ngừng sử dụng thể loại.'}
              </p>
            </div>

            <div className="flex flex-col gap-3 sm:flex-row">
              <div className="relative">
                <Search
                  size={17}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                />

                <input
                  value={search}
                  onChange={(event) =>
                    setSearch(
                      event.target
                        .value,
                    )
                  }
                  placeholder={
                    isAuthors
                      ? 'Tìm tác giả...'
                      : 'Tìm thể loại...'
                  }
                  className="w-full rounded-lg border border-slate-300 py-2.5 pl-9 pr-3 text-sm outline-none focus:border-blue-500 sm:w-64"
                />
              </div>

              <button
                type="button"
                onClick={
                  openCreateModal
                }
                className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700"
              >
                <Plus size={18} />

                {isAuthors
                  ? 'Thêm tác giả'
                  : 'Thêm thể loại'}
              </button>
            </div>
          </div>

          <label className="mt-4 flex cursor-pointer items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              checked={showInactive}
              onChange={(event) =>
                setShowInactive(
                  event.target
                    .checked,
                )
              }
              className="h-4 w-4 rounded border-slate-300"
            />

            Hiển thị cả mục đã ngừng sử dụng
          </label>
        </div>

        {isAuthors ? (
          <AuthorsTable
            items={
              filteredAuthors
            }
            onEdit={
              openEditAuthor
            }
            onToggle={
              toggleAuthor
            }
          />
        ) : (
          <CategoriesTable
            items={
              filteredCategories
            }
            allCategories={
              categories
            }
            onEdit={
              openEditCategory
            }
            onToggle={
              toggleCategory
            }
          />
        )}
      </Card>

      {/* QUY TẮC */}
      <div className="rounded-xl border border-blue-100 bg-blue-50 p-4">
        <div className="flex gap-3">
          <BookOpen
            size={20}
            className="mt-0.5 shrink-0 text-blue-600"
          />

          <div>
            <p className="text-sm font-semibold text-blue-900">
              Quy tắc danh mục
            </p>

            <p className="mt-1 text-sm leading-6 text-blue-700">
              {isAuthors
                ? 'Tác giả đang gắn với đầu sách không bị xóa khỏi hệ thống. Khi ngừng sử dụng, tác giả sẽ không xuất hiện trong ô chọn của lần biên mục mới nhưng vẫn hiển thị trên các sách cũ.'
                : 'Thể loại chỉ được phân cấp tối đa 2 cấp. Thể loại đang gắn với đầu sách không bị xóa; khi ngừng sử dụng sẽ không xuất hiện trong ô chọn biên mục mới nhưng vẫn giữ trên dữ liệu sách cũ.'}
            </p>
          </div>
        </div>
      </div>

      {/* MODAL */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-lg rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-200 px-6 py-5">
              <div>
                <h2 className="text-xl font-semibold text-slate-900">
                  {editingId !== null
                    ? isAuthors
                      ? 'Chỉnh sửa tác giả'
                      : 'Chỉnh sửa thể loại'
                    : isAuthors
                      ? 'Thêm tác giả'
                      : 'Thêm thể loại'}
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  {isAuthors
                    ? 'Nhập thông tin tác giả.'
                    : 'Nhập thông tin và cấp của thể loại.'}
                </p>
              </div>

              <button
                type="button"
                onClick={closeModal}
                className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"
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
                  {isAuthors
                    ? 'Tên tác giả'
                    : 'Tên thể loại'}

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
                  placeholder={
                    isAuthors
                      ? 'Ví dụ: Nguyễn Nhật Ánh'
                      : 'Ví dụ: Văn học'
                  }
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>

              {!isAuthors && (
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">
                    Thể loại cha
                  </label>

                  <select
                    value={
                      form.parentId
                    }
                    onChange={(
                      event,
                    ) => {
                      setForm({
                        ...form,
                        parentId:
                          event
                            .target
                            .value,
                      })

                      setError('')
                    }}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                  >
                    <option value="">
                      Không có — Cấp 1
                    </option>

                    {parentOptions.map(
                      (item) => (
                        <option
                          key={
                            item.id
                          }
                          value={
                            item.id
                          }
                        >
                          {
                            item.name
                          }{' '}
                          — Cấp 1
                        </option>
                      ),
                    )}
                  </select>

                  <p className="mt-1 text-xs text-slate-400">
                    Chọn một thể loại cấp 1
                    để tạo thể loại cấp 2.
                  </p>
                </div>
              )}

              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  {isAuthors
                    ? 'Ghi chú'
                    : 'Mô tả'}
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
                  placeholder={
                    isAuthors
                      ? 'Thông tin thêm về tác giả...'
                      : 'Mô tả thể loại...'
                  }
                  className="w-full resize-none rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
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
                  className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Hủy
                </button>

                <button
                  type="submit"
                  className="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
                >
                  {editingId !== null
                    ? 'Lưu thay đổi'
                    : isAuthors
                      ? 'Thêm tác giả'
                      : 'Thêm thể loại'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

type AuthorsTableProps = {
  items: Author[]
  onEdit: (item: Author) => void
  onToggle: (id: number) => void
}

function AuthorsTable({
  items,
  onEdit,
  onToggle,
}: AuthorsTableProps) {
  if (items.length === 0) {
    return (
      <EmptyState
        mode="authors"
      />
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[800px]">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50">
            <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
              Tác giả
            </th>

            <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
              Đầu sách
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
          {items.map(
            (item) => (
              <tr
                key={item.id}
                className="border-b border-slate-100 hover:bg-slate-50"
              >
                <td className="px-5 py-4">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
                      <UserRound
                        size={19}
                      />
                    </div>

                    <div>
                      <p className="font-medium text-slate-900">
                        {
                          item.name
                        }
                      </p>

                      <p className="mt-0.5 text-xs text-slate-500">
                        {
                          item.note ||
                          'Không có ghi chú'
                        }
                      </p>
                    </div>
                  </div>
                </td>

                <td className="px-5 py-4 text-center text-sm text-slate-700">
                  {
                    item.bookCount
                  }
                </td>

                <td className="px-5 py-4 text-center">
                  <StatusBadge
                    active={
                      item.active
                    }
                  />
                </td>

                <td className="px-5 py-4">
                  <ActionButtons
                    active={
                      item.active
                    }
                    onEdit={() =>
                      onEdit(item)
                    }
                    onToggle={() =>
                      onToggle(
                        item.id,
                      )
                    }
                  />
                </td>
              </tr>
            ),
          )}
        </tbody>
      </table>
    </div>
  )
}

type CategoriesTableProps = {
  items: Category[]
  allCategories: Category[]
  onEdit: (
    item: Category,
  ) => void
  onToggle: (
    id: number,
  ) => void
}

function CategoriesTable({
  items,
  allCategories,
  onEdit,
  onToggle,
}: CategoriesTableProps) {
  if (items.length === 0) {
    return (
      <EmptyState
        mode="categories"
      />
    )
  }

  const findParentName = (
    parentId: number | null,
  ) => {
    if (
      parentId === null
    ) {
      return '—'
    }

    return (
      allCategories.find(
        (item) =>
          item.id === parentId,
      )?.name ?? '—'
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[950px]">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50">
            <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
              Thể loại
            </th>

            <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
              Thuộc thể loại
            </th>

            <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
              Cấp
            </th>

            <th className="px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
              Đầu sách
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
          {items.map(
            (item) => (
              <tr
                key={item.id}
                className="border-b border-slate-100 hover:bg-slate-50"
              >
                <td className="px-5 py-4">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-violet-50 text-violet-600">
                      <Tags
                        size={19}
                      />
                    </div>

                    <div>
                      <p className="font-medium text-slate-900">
                        {
                          item.name
                        }
                      </p>

                      <p className="mt-0.5 text-xs text-slate-500">
                        {
                          item.description ||
                          'Không có mô tả'
                        }
                      </p>
                    </div>
                  </div>
                </td>

                <td className="px-5 py-4 text-sm text-slate-600">
                  {findParentName(
                    item.parentId,
                  )}
                </td>

                <td className="px-5 py-4 text-center">
                  <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
                    Cấp{' '}
                    {
                      item.parentId ===
                      null
                        ? 1
                        : 2
                    }
                  </span>
                </td>

                <td className="px-5 py-4 text-center text-sm text-slate-700">
                  {
                    item.bookCount
                  }
                </td>

                <td className="px-5 py-4 text-center">
                  <StatusBadge
                    active={
                      item.active
                    }
                  />
                </td>

                <td className="px-5 py-4">
                  <ActionButtons
                    active={
                      item.active
                    }
                    onEdit={() =>
                      onEdit(item)
                    }
                    onToggle={() =>
                      onToggle(
                        item.id,
                      )
                    }
                  />
                </td>
              </tr>
            ),
          )}
        </tbody>
      </table>
    </div>
  )
}

type StatusBadgeProps = {
  active: boolean
}

function StatusBadge({
  active,
}: StatusBadgeProps) {
  return active ? (
    <span className="inline-flex rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
      Đang sử dụng
    </span>
  ) : (
    <span className="inline-flex rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
      Ngừng sử dụng
    </span>
  )
}

type ActionButtonsProps = {
  active: boolean
  onEdit: () => void
  onToggle: () => void
}

function ActionButtons({
  active,
  onEdit,
  onToggle,
}: ActionButtonsProps) {
  return (
    <div className="flex justify-end gap-2">
      <button
        type="button"
        title="Chỉnh sửa"
        onClick={onEdit}
        className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:border-blue-200 hover:bg-blue-50 hover:text-blue-600"
      >
        <Pencil size={16} />
      </button>

      <button
        type="button"
        title={
          active
            ? 'Ngừng sử dụng'
            : 'Sử dụng lại'
        }
        onClick={onToggle}
        className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-100"
      >
        <Power size={16} />
      </button>
    </div>
  )
}

type EmptyStateProps = {
  mode: PageMode
}

function EmptyState({
  mode,
}: EmptyStateProps) {
  return (
    <div className="px-6 py-14 text-center">
      {mode === 'authors' ? (
        <UserRound
          size={38}
          className="mx-auto text-slate-300"
        />
      ) : (
        <Tags
          size={38}
          className="mx-auto text-slate-300"
        />
      )}

      <p className="mt-3 font-medium text-slate-700">
        Không tìm thấy dữ liệu
      </p>

      <p className="mt-1 text-sm text-slate-500">
        Thử thay đổi từ khóa tìm kiếm.
      </p>
    </div>
  )
}