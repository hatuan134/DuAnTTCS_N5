import {
  useEffect,
  useMemo,
  useState,
} from 'react'

import type {
  FormEvent,
} from 'react'

import {
  AlertCircle,
  AlertTriangle,
  BookOpen,
  CheckCircle2,
  CornerDownRight,
  Pencil,
  Plus,
  Power,
  RefreshCw,
  Search,
  Tags,
  Trash2,
  UserRound,
  X,
} from 'lucide-react'

import { Link, useNavigate } from 'react-router-dom'
import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'
import StatusBadge from '../../components/ui/StatusBadge'
import {
  catalogService,
  type Author,
  type Book,
  type CatalogBookForm,
  type Category,
} from './catalogService'

type PageMode = 'authors' | 'categories' | 'books'

type Props = {
  mode: PageMode
}

interface AuthorFormState {
  name: string
  note: string
}

interface CategoryFormState {
  name: string
  description: string
  parentId: string
}

interface BookFormState {
  title: string
  subtitle: string
  authorIds: number[]
  categoryId: string
  isbn: string
  publisher: string
  publicationYear: string
  pageCount: string
  description: string
}

interface DuplicateTitleWarningState {
  title: string
  matchingRule?: string
  books: Book[]
}

const emptyAuthorForm: AuthorFormState = {
  name: '',
  note: '',
}

const emptyCategoryForm: CategoryFormState = {
  name: '',
  description: '',
  parentId: '',
}

const emptyBookForm: BookFormState = {
  title: '',
  subtitle: '',
  authorIds: [],
  categoryId: '',
  isbn: '',
  publisher: '',
  publicationYear: '',
  pageCount: '',
  description: '',
}

export default function CatalogManagementPage({ mode: initialMode }: Props) {
  const navigate = useNavigate()
  const [currentTab, setCurrentTab] = useState<PageMode>(initialMode)
  const [authors, setAuthors] = useState<Author[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [books, setBooks] = useState<Book[]>([])
  const [publisherOptions, setPublisherOptions] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [apiError, setApiError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Filters
  const [search, setSearch] = useState('')
  const [showInactive, setShowInactive] = useState(true)

  // Author Modal
  const [isAuthorModalOpen, setIsAuthorModalOpen] = useState(false)
  const [editingAuthorId, setEditingAuthorId] = useState<number | null>(null)
  const [authorForm, setAuthorForm] = useState<AuthorFormState>(emptyAuthorForm)
  const [authorFormError, setAuthorFormError] = useState('')

  // Category Modal
  const [isCategoryModalOpen, setIsCategoryModalOpen] = useState(false)
  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null)
  const [categoryForm, setCategoryForm] = useState<CategoryFormState>(emptyCategoryForm)
  const [categoryFormError, setCategoryFormError] = useState('')

  // Book Modal (Biên mục mới)
  const [isBookModalOpen, setIsBookModalOpen] = useState(false)
  const [bookForm, setBookForm] = useState<BookFormState>(emptyBookForm)
  const [bookFormError, setBookFormError] = useState('')
  const [isbnError, setIsbnError] = useState('')
  const [bookSubmitting, setBookSubmitting] = useState(false)
  const [duplicateTitleWarning, setDuplicateTitleWarning] = useState<DuplicateTitleWarningState | null>(null)

  // Delete constraint dialog
  const [deleteDialog, setDeleteDialog] = useState<{
    open: boolean
    type: 'author' | 'category'
    item: Author | Category | null
    cannotDeleteReason?: string
  }>({
    open: false,
    type: 'author',
    item: null,
  })

  // Load all data
  const loadData = async () => {
    setLoading(true)
    setApiError(null)
    try {
      const [authorsData, categoriesData, booksData, publishersData] = await Promise.all([
        catalogService.getAuthors(),
        catalogService.getCategories(),
        catalogService.getBooks(),
        catalogService.getPublisherOptions(),
      ])
      setAuthors(authorsData)
      setCategories(categoriesData)
      setBooks(booksData)
      setPublisherOptions(publishersData)
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Không thể tải dữ liệu danh mục.'
      setApiError(msg)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [])

  useEffect(() => {
    setCurrentTab(initialMode)
  }, [initialMode])

  const showNotification = (msg: string) => {
    setSuccessMessage(msg)
    setTimeout(() => {
      setSuccessMessage(null)
    }, 4000)
  }

  // --- Author Actions ---
  const openCreateAuthorModal = () => {
    setEditingAuthorId(null)
    setAuthorForm(emptyAuthorForm)
    setAuthorFormError('')
    setIsAuthorModalOpen(true)
  }

  const openEditAuthorModal = (author: Author) => {
    setEditingAuthorId(author.id)
    setAuthorForm({
      name: author.name,
      note: author.note || '',
    })
    setAuthorFormError('')
    setIsAuthorModalOpen(true)
  }

  const handleAuthorSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setAuthorFormError('')
    const trimmed = authorForm.name.trim()
    if (!trimmed) {
      setAuthorFormError('Tên tác giả không được để trống.')
      return
    }

    try {
      if (editingAuthorId !== null) {
        await catalogService.updateAuthor(editingAuthorId, {
          name: trimmed,
          note: authorForm.note.trim(),
        })
        showNotification(`Đã cập nhật thông tin tác giả "${trimmed}".`)
      } else {
        await catalogService.createAuthor({
          name: trimmed,
          note: authorForm.note.trim(),
        })
        showNotification(`Đã thêm tác giả mới "${trimmed}".`)
      }
      setIsAuthorModalOpen(false)
      loadData()
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Đã xảy ra lỗi khi lưu tác giả.'
      setAuthorFormError(msg)
    }
  }

  const handleToggleAuthor = async (author: Author) => {
    try {
      const updated = await catalogService.toggleAuthorStatus(author.id)
      const actionText = updated.active ? 'kích hoạt lại' : 'ngừng sử dụng'
      showNotification(`Đã ${actionText} tác giả "${updated.name}".`)
      loadData()
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Không thể đổi trạng thái tác giả.'
      setApiError(msg)
    }
  }

  const handleDeleteAuthorClick = (author: Author) => {
    if (author.bookCount > 0) {
      setDeleteDialog({
        open: true,
        type: 'author',
        item: author,
        cannotDeleteReason: `Không cho phép xoá: Tác giả "${author.name}" đang gắn với ${author.bookCount} đầu sách trong hệ thống. Quy tắc chỉ cho phép ngừng sử dụng để bảo toàn dữ liệu sách cũ.`,
      })
    } else {
      setDeleteDialog({
        open: true,
        type: 'author',
        item: author,
        cannotDeleteReason: undefined,
      })
    }
  }

  // --- Category Actions ---
  const openCreateCategoryModal = () => {
    setEditingCategoryId(null)
    setCategoryForm(emptyCategoryForm)
    setCategoryFormError('')
    setIsCategoryModalOpen(true)
  }

  const openEditCategoryModal = (category: Category) => {
    setEditingCategoryId(category.id)
    setCategoryForm({
      name: category.name,
      description: category.description || '',
      parentId: category.parentId ? String(category.parentId) : '',
    })
    setCategoryFormError('')
    setIsCategoryModalOpen(true)
  }

  const handleCategorySubmit = async (e: FormEvent) => {
    e.preventDefault()
    setCategoryFormError('')
    const trimmed = categoryForm.name.trim()
    if (!trimmed) {
      setCategoryFormError('Tên thể loại không được để trống.')
      return
    }

    const parentIdNum = categoryForm.parentId ? Number(categoryForm.parentId) : null

    // Client check self parent
    if (editingCategoryId !== null && parentIdNum === editingCategoryId) {
      setCategoryFormError('Thể loại không thể là cha của chính nó.')
      return
    }

    try {
      if (editingCategoryId !== null) {
        await catalogService.updateCategory(editingCategoryId, {
          name: trimmed,
          description: categoryForm.description.trim(),
          parentId: parentIdNum,
        })
        showNotification(`Đã cập nhật thể loại "${trimmed}".`)
      } else {
        await catalogService.createCategory({
          name: trimmed,
          description: categoryForm.description.trim(),
          parentId: parentIdNum,
        })
        showNotification(`Đã thêm thể loại mới "${trimmed}".`)
      }
      setIsCategoryModalOpen(false)
      loadData()
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Đã xảy ra lỗi khi lưu thể loại.'
      setCategoryFormError(msg)
    }
  }

  const handleToggleCategory = async (category: Category) => {
    try {
      const updated = await catalogService.toggleCategoryStatus(category.id)
      const actionText = updated.active ? 'kích hoạt lại' : 'ngừng sử dụng'
      showNotification(
        `Đã ${actionText} thể loại "${updated.name}"${
          !updated.active && category.level === 1
            ? ' (các thể loại con cũng đã được tự động ngừng sử dụng)'
            : ''
        }.`,
      )
      loadData()
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Không thể đổi trạng thái thể loại.'
      setApiError(msg)
    }
  }

  const handleDeleteCategoryClick = (category: Category) => {
    // Check if category has children
    const hasChildren = categories.some((c) => c.parentId === category.id)
    if (hasChildren) {
      setDeleteDialog({
        open: true,
        type: 'category',
        item: category,
        cannotDeleteReason: `Không cho phép xoá: Thể loại "${category.name}" đang có các thể loại con trực thuộc. Vui lòng chuyển hoặc xoá các thể loại con trước.`,
      })
      return
    }

    if (category.bookCount > 0) {
      setDeleteDialog({
        open: true,
        type: 'category',
        item: category,
        cannotDeleteReason: `Không cho phép xoá: Thể loại "${category.name}" đang gắn với ${category.bookCount} đầu sách trong hệ thống. Quy tắc chỉ cho phép ngừng sử dụng để bảo toàn dữ liệu sách cũ.`,
      })
      return
    }

    setDeleteDialog({
      open: true,
      type: 'category',
      item: category,
      cannotDeleteReason: undefined,
    })
  }

  const confirmDelete = async () => {
    if (!deleteDialog.item || deleteDialog.cannotDeleteReason) {
      setDeleteDialog({ open: false, type: 'author', item: null })
      return
    }

    try {
      if (deleteDialog.type === 'author') {
        await catalogService.deleteAuthor(deleteDialog.item.id)
        showNotification(`Đã xoá tác giả "${deleteDialog.item.name}".`)
      } else {
        await catalogService.deleteCategory(deleteDialog.item.id)
        showNotification(`Đã xoá thể loại "${deleteDialog.item.name}".`)
      }
      setDeleteDialog({ open: false, type: 'author', item: null })
      loadData()
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Không thể xoá mục này.'
      setApiError(msg)
      setDeleteDialog({ open: false, type: 'author', item: null })
    }
  }

  // --- Book / Biên mục sách mới Actions ---
  const openCreateBookModal = () => {
    setBookForm(emptyBookForm)
    setBookFormError('')
    setIsbnError('')
    setDuplicateTitleWarning(null)
    setIsBookModalOpen(true)
  }

  const buildCatalogPayload = (confirmDuplicateTitle: boolean): CatalogBookForm | null => {
    setBookFormError('')
    setIsbnError('')

    const currentYear = new Date().getFullYear()
    const publicationYear = Number(bookForm.publicationYear)
    const pageCount = Number(bookForm.pageCount)

    if (!bookForm.title.trim()) {
      setBookFormError('Nhan đề không được để trống.')
      return null
    }
    if (bookForm.authorIds.length === 0) {
      setBookFormError('Vui lòng chọn ít nhất một tác giả từ danh mục tác giả.')
      return null
    }
    if (!bookForm.categoryId) {
      setBookFormError('Vui lòng chọn thể loại cho đầu sách.')
      return null
    }
    if (!bookForm.publisher) {
      setBookFormError('Vui lòng chọn nhà xuất bản từ danh mục hiện có.')
      return null
    }

    const normalizedIsbn = bookForm.isbn.trim()
    if (normalizedIsbn && !/^(?:[0-9]{10}|[0-9]{13})$/.test(normalizedIsbn)) {
      setIsbnError('ISBN phải gồm đúng 10 hoặc 13 chữ số và không chứa chữ cái hay ký tự đặc biệt.')
      return null
    }

    if (!Number.isInteger(publicationYear) || publicationYear < 1 || publicationYear > currentYear) {
      setBookFormError(`Năm xuất bản phải là số nguyên từ 1 đến ${currentYear}.`)
      return null
    }
    if (!Number.isInteger(pageCount) || pageCount < 1) {
      setBookFormError('Số trang phải là số nguyên lớn hơn 0.')
      return null
    }

    return {
      title: bookForm.title.trim(),
      subtitle: bookForm.subtitle.trim() || undefined,
      authorIds: bookForm.authorIds,
      categoryId: Number(bookForm.categoryId),
      isbn: normalizedIsbn || undefined,
      publisher: bookForm.publisher,
      publicationYear,
      pageCount,
      description: bookForm.description.trim() || undefined,
      confirmDuplicateTitle,
    }
  }

  const submitBook = async (confirmDuplicateTitle: boolean) => {
    if (bookSubmitting) return

    const payload = buildCatalogPayload(confirmDuplicateTitle)
    if (!payload) return

    setBookSubmitting(true)
    try {
      const created = await catalogService.catalogBook(payload)
      setDuplicateTitleWarning(null)
      setIsBookModalOpen(false)
      navigate(`/books/${created.id}`, {
        state: { successMessage: `Tạo đầu sách "${created.title}" thành công.` },
      })
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Đã xảy ra lỗi khi tạo hồ sơ đầu sách.'
      const code = err.response?.data?.code
      const details = err.response?.data?.details
      const field = details?.field

      if (code === 'TITLE_ALREADY_EXISTS') {
        const duplicateBooks = Array.isArray(details?.duplicates) ? details.duplicates as Book[] : []
        setDuplicateTitleWarning({
          title: typeof details?.title === 'string' ? details.title : bookForm.title.trim(),
          matchingRule: typeof details?.matchingRule === 'string' ? details.matchingRule : undefined,
          books: duplicateBooks,
        })
      } else {
        setDuplicateTitleWarning(null)
        if (field === 'isbn' || code === 'INVALID_ISBN_FORMAT' || code === 'ISBN_ALREADY_EXISTS') {
          setIsbnError(msg)
        } else {
          setBookFormError(msg)
        }
      }
    } finally {
      setBookSubmitting(false)
    }
  }

  const handleBookSubmit = async (e: FormEvent) => {
    e.preventDefault()
    await submitBook(false)
  }

  const confirmDuplicateTitleAndCreate = async () => {
    await submitBook(true)
  }

  // Active authors for new cataloging dropdown (Requirement: Deactivated items do NOT appear)
  const activeAuthorsForCataloging = useMemo(() => {
    return authors.filter((a) => a.active)
  }, [authors])

  // Active categories for new cataloging dropdown
  const activeCategoriesForCataloging = useMemo(() => {
    return categories.filter((c) => c.active)
  }, [categories])

  // Parent options for category creation (Level 1 active categories, excluding self)
  const level1CategoriesForParent = useMemo(() => {
    return categories.filter(
      (c) => c.parentId === null && c.active && c.id !== editingCategoryId,
    )
  }, [categories, editingCategoryId])

  // Check if current category being edited has child categories
  const currentCategoryHasChildren = useMemo(() => {
    if (editingCategoryId === null) return false
    return categories.some((c) => c.parentId === editingCategoryId)
  }, [categories, editingCategoryId])

  // Filtered lists for tables
  const filteredAuthors = useMemo(() => {
    const kw = search.trim().toLowerCase()
    return authors.filter((a) => {
      const matchKw =
        !kw ||
        a.name.toLowerCase().includes(kw) ||
        (a.note && a.note.toLowerCase().includes(kw))
      const matchStatus = showInactive || a.active
      return matchKw && matchStatus
    })
  }, [authors, search, showInactive])

  const filteredCategories = useMemo(() => {
    const kw = search.trim().toLowerCase()
    return categories.filter((c) => {
      const matchKw =
        !kw ||
        c.name.toLowerCase().includes(kw) ||
        (c.description && c.description.toLowerCase().includes(kw)) ||
        (c.parentName && c.parentName.toLowerCase().includes(kw))
      const matchStatus = showInactive || c.active
      return matchKw && matchStatus
    })
  }, [categories, search, showInactive])

  const filteredBooks = useMemo(() => {
    const kw = search.trim().toLowerCase()
    return books.filter((b) => {
      return (
        !kw ||
        b.title.toLowerCase().includes(kw) ||
        (b.authors?.length
          ? b.authors.some((author) => author.name.toLowerCase().includes(kw))
          : b.authorName.toLowerCase().includes(kw)) ||
        b.categoryName.toLowerCase().includes(kw) ||
        (b.isbn && b.isbn.toLowerCase().includes(kw)) ||
        (b.publisher && b.publisher.toLowerCase().includes(kw))
      )
    })
  }, [books, search])

  // Stats
  const authorsActiveCount = authors.filter((a) => a.active).length
  const categoriesActiveCount = categories.filter((c) => c.active).length

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <PageHeader
          title={
            currentTab === 'authors'
              ? 'Danh mục Tác giả'
              : currentTab === 'categories'
                ? 'Danh mục Thể loại'
                : 'Biên mục Sách (Kiểm chứng danh mục)'
          }
          description={
            currentTab === 'authors'
              ? 'Khai báo và quản lý tác giả chuẩn hoá, phục vụ biên mục sách nhanh chóng.'
              : currentTab === 'categories'
                ? 'Khai báo danh mục thể loại phân cấp tối đa 2 cấp (ví dụ: Văn học trong nước dưới Văn học).'
                : 'Biên mục đầu sách: kiểm chứng danh mục đã ngừng sử dụng không hiện trong ô chọn mới nhưng vẫn hiển thị trên sách cũ.'
          }
        />

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={loadData}
            title="Làm mới dữ liệu"
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50"
          >
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
            <span>Tải lại</span>
          </button>
        </div>
      </div>

      {/* Notifications */}
      {successMessage && (
        <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 shadow-sm">
          <CheckCircle2 size={18} className="text-emerald-600 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {apiError && (
        <div className="flex items-center justify-between rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 shadow-sm">
          <div className="flex items-center gap-2">
            <AlertCircle size={18} className="text-red-600 shrink-0" />
            <span>{apiError}</span>
          </div>
          <button
            type="button"
            onClick={() => setApiError(null)}
            className="text-red-500 hover:text-red-700"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* Tabs navigation */}
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-200 pb-3">
        <button
          type="button"
          onClick={() => {
            setCurrentTab('authors')
            setSearch('')
          }}
          className={`inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition ${
            currentTab === 'authors'
              ? 'bg-blue-600 text-white shadow-sm'
              : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'
          }`}
        >
          <UserRound size={17} />
          <span>Tác giả ({authors.length})</span>
        </button>

        <button
          type="button"
          onClick={() => {
            setCurrentTab('categories')
            setSearch('')
          }}
          className={`inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition ${
            currentTab === 'categories'
              ? 'bg-blue-600 text-white shadow-sm'
              : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'
          }`}
        >
          <Tags size={17} />
          <span>Thể loại ({categories.length})</span>
        </button>

        <button
          type="button"
          onClick={() => {
            setCurrentTab('books')
            setSearch('')
          }}
          className={`inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition ${
            currentTab === 'books'
              ? 'bg-blue-600 text-white shadow-sm'
              : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'
          }`}
        >
          <BookOpen size={17} />
          <span>Sách đã biên mục ({books.length})</span>
        </button>
      </div>

      {/* Stats Cards */}
      <div className="grid gap-4 sm:grid-cols-4">
        <Card>
          <div className="p-5">
            <p className="text-sm font-medium text-slate-500">Tổng tác giả</p>
            <div className="mt-2 flex items-baseline justify-between">
              <span className="text-2xl font-bold text-slate-900">{authors.length}</span>
              <span className="text-xs text-emerald-600 font-medium">
                {authorsActiveCount} đang dùng
              </span>
            </div>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm font-medium text-slate-500">Tổng thể loại</p>
            <div className="mt-2 flex items-baseline justify-between">
              <span className="text-2xl font-bold text-slate-900">{categories.length}</span>
              <span className="text-xs text-emerald-600 font-medium">
                {categoriesActiveCount} đang dùng
              </span>
            </div>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm font-medium text-slate-500">Phân cấp thể loại</p>
            <div className="mt-2 flex items-baseline justify-between">
              <span className="text-2xl font-bold text-violet-700">Tối đa 2 cấp</span>
              <span className="text-xs text-slate-500">Cấp 1 & Cấp 2</span>
            </div>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm font-medium text-slate-500">Đầu sách trong thư viện</p>
            <div className="mt-2 flex items-baseline justify-between">
              <span className="text-2xl font-bold text-blue-700">{books.length}</span>
              <span className="text-xs text-slate-500">Đã biên mục</span>
            </div>
          </div>
        </Card>
      </div>

      {/* Main Card */}
      <Card>
        {/* Table Toolbar */}
        <div className="border-b border-slate-200 p-5">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                {currentTab === 'authors'
                  ? 'Danh sách Tác giả'
                  : currentTab === 'categories'
                    ? 'Danh sách Thể loại (Cây phân cấp)'
                    : 'Danh sách Đầu sách đã Biên mục'}
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                {currentTab === 'authors'
                  ? 'Quản lý tác giả: thêm mới, chỉnh sửa, đổi trạng thái hoặc xoá an toàn.'
                  : currentTab === 'categories'
                    ? 'Quản lý thể loại: phân cấp lồng tối đa 2 cấp, kiểm soát trùng tên trong cùng danh mục.'
                    : 'Kiểm chứng: Sách cũ giữ nguyên tác giả/thể loại (kể cả khi đã ngừng sử dụng); Biên mục mới chỉ chọn mục đang hoạt động.'}
              </p>
            </div>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
              <div className="relative">
                <Search
                  size={17}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                />
                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder={
                    currentTab === 'authors'
                      ? 'Tìm tên tác giả...'
                      : currentTab === 'categories'
                        ? 'Tìm thể loại...'
                        : 'Tìm tên sách, tác giả, ISBN...'
                  }
                  className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 sm:w-64"
                />
              </div>

              {currentTab === 'authors' && (
                <button
                  type="button"
                  onClick={openCreateAuthorModal}
                  className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700"
                >
                  <Plus size={18} />
                  <span>Thêm tác giả</span>
                </button>
              )}

              {currentTab === 'categories' && (
                <button
                  type="button"
                  onClick={openCreateCategoryModal}
                  className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700"
                >
                  <Plus size={18} />
                  <span>Thêm thể loại</span>
                </button>
              )}

              {currentTab === 'books' && (
                <button
                  type="button"
                  onClick={openCreateBookModal}
                  className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-emerald-700"
                >
                  <Plus size={18} />
                  <span>Biên mục sách mới</span>
                </button>
              )}
            </div>
          </div>

          {currentTab !== 'books' && (
            <div className="mt-4 flex items-center gap-2">
              <input
                id="showInactiveCheckbox"
                type="checkbox"
                checked={showInactive}
                onChange={(e) => setShowInactive(e.target.checked)}
                className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
              />
              <label
                htmlFor="showInactiveCheckbox"
                className="cursor-pointer text-sm text-slate-600"
              >
                Hiển thị cả mục đã ngừng sử dụng
              </label>
            </div>
          )}
        </div>

        {/* Content Table by Tab */}
        {currentTab === 'authors' && (
          <AuthorsTable
            items={filteredAuthors}
            onEdit={openEditAuthorModal}
            onToggle={handleToggleAuthor}
            onDelete={handleDeleteAuthorClick}
          />
        )}

        {currentTab === 'categories' && (
          <CategoriesTable
            items={filteredCategories}
            onEdit={openEditCategoryModal}
            onToggle={handleToggleCategory}
            onDelete={handleDeleteCategoryClick}
          />
        )}

        {currentTab === 'books' && (
          <BooksTable
            items={filteredBooks}
            onOpenCatalogModal={openCreateBookModal}
          />
        )}
      </Card>

      {/* Rules Notice Box */}
      <div className="rounded-xl border border-blue-100 bg-blue-50/70 p-5">
        <div className="flex items-start gap-3">
          <BookOpen size={20} className="mt-0.5 shrink-0 text-blue-600" />
          <div className="space-y-1.5">
            <h4 className="text-sm font-semibold text-blue-900">
              Quy tắc nghiệp vụ S1-08 (Khai báo danh mục & Biên mục sách)
            </h4>
            <ul className="list-disc pl-5 text-sm leading-relaxed text-blue-800 space-y-1">
              <li>
                <strong>Không trùng tên:</strong> Tên tác giả là duy nhất trong danh mục tác giả;
                Tên thể loại là duy nhất trong cùng danh mục cha / cùng cấp.
              </li>
              <li>
                <strong>Xếp lồng tối đa 2 cấp:</strong> Thể loại chỉ được tối đa 2 cấp (ví dụ:
                "Văn học trong nước" nằm dưới "Văn học"). Không cho phép tạo thể loại cấp 3.
              </li>
              <li>
                <strong>Bảo vệ dữ liệu sách:</strong> Không cho xoá tác giả hoặc thể loại đang gắn
                với ít nhất một đầu sách; chỉ cho phép <em>ngừng sử dụng</em>.
              </li>
              <li>
                <strong>Quy tắc biên mục:</strong> Danh mục đã ngừng sử dụng không xuất hiện trong ô
                chọn khi biên mục mới, nhưng vẫn hiển thị đầy đủ và rõ ràng trên các sách cũ đã biên
                mục.
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* MODAL: Thêm / Sửa Tác giả */}
      {isAuthorModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-lg rounded-2xl bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
              <h3 className="text-lg font-semibold text-slate-900">
                {editingAuthorId !== null ? 'Chỉnh sửa tác giả' : 'Thêm tác giả mới'}
              </h3>
              <button
                type="button"
                onClick={() => setIsAuthorModalOpen(false)}
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              >
                <X size={19} />
              </button>
            </div>

            <form onSubmit={handleAuthorSubmit} className="space-y-4 p-6">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Tên tác giả <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={authorForm.name}
                  onChange={(e) => {
                    setAuthorForm({ ...authorForm, name: e.target.value })
                    setAuthorFormError('')
                  }}
                  placeholder="Ví dụ: Nguyễn Nhật Ánh, Nam Cao..."
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                  autoFocus
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Ghi chú / Tiểu sử
                </label>
                <textarea
                  rows={3}
                  value={authorForm.note}
                  onChange={(e) => setAuthorForm({ ...authorForm, note: e.target.value })}
                  placeholder="Thông tin thêm về tác giả..."
                  className="w-full resize-none rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                />
              </div>

              {authorFormError && (
                <div className="flex items-center gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-700 border border-red-200">
                  <AlertCircle size={16} className="shrink-0" />
                  <span>{authorFormError}</span>
                </div>
              )}

              <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
                <button
                  type="button"
                  onClick={() => setIsAuthorModalOpen(false)}
                  className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Huỷ
                </button>
                <button
                  type="submit"
                  className="rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700 shadow-sm"
                >
                  {editingAuthorId !== null ? 'Lưu thay đổi' : 'Thêm tác giả'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: Thêm / Sửa Thể loại */}
      {isCategoryModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-lg rounded-2xl bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
              <h3 className="text-lg font-semibold text-slate-900">
                {editingCategoryId !== null ? 'Chỉnh sửa thể loại' : 'Thêm thể loại mới'}
              </h3>
              <button
                type="button"
                onClick={() => setIsCategoryModalOpen(false)}
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              >
                <X size={19} />
              </button>
            </div>

            <form onSubmit={handleCategorySubmit} className="space-y-4 p-6">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Tên thể loại <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={categoryForm.name}
                  onChange={(e) => {
                    setCategoryForm({ ...categoryForm, name: e.target.value })
                    setCategoryFormError('')
                  }}
                  placeholder="Ví dụ: Văn học, Văn học trong nước, Lập trình Web..."
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                  autoFocus
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Thuộc thể loại cha (Phân cấp tối đa 2 cấp)
                </label>
                <select
                  value={categoryForm.parentId}
                  disabled={currentCategoryHasChildren}
                  onChange={(e) => {
                    setCategoryForm({ ...categoryForm, parentId: e.target.value })
                    setCategoryFormError('')
                  }}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 disabled:bg-slate-100 disabled:text-slate-500"
                >
                  <option value="">Không có — Là thể loại cấp 1</option>
                  {level1CategoriesForParent.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {cat.name} (Cấp 1)
                    </option>
                  ))}
                </select>
                {currentCategoryHasChildren ? (
                  <p className="mt-1 text-xs text-amber-600 font-medium">
                    Thể loại này đang có các thể loại con nên bắt buộc giữ ở Cấp 1 (không thể chuyển
                    thành Cấp 2).
                  </p>
                ) : (
                  <p className="mt-1 text-xs text-slate-500">
                    Chọn một thể loại Cấp 1 đang hoạt động để tạo thể loại Cấp 2. Thể loại chỉ xếp
                    lồng tối đa 2 cấp.
                  </p>
                )}
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Mô tả thể loại
                </label>
                <textarea
                  rows={3}
                  value={categoryForm.description}
                  onChange={(e) =>
                    setCategoryForm({ ...categoryForm, description: e.target.value })
                  }
                  placeholder="Mô tả tóm tắt về thể loại sách..."
                  className="w-full resize-none rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                />
              </div>

              {categoryFormError && (
                <div className="flex items-center gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-700 border border-red-200">
                  <AlertCircle size={16} className="shrink-0" />
                  <span>{categoryFormError}</span>
                </div>
              )}

              <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
                <button
                  type="button"
                  onClick={() => setIsCategoryModalOpen(false)}
                  className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Huỷ
                </button>
                <button
                  type="submit"
                  className="rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700 shadow-sm"
                >
                  {editingCategoryId !== null ? 'Lưu thay đổi' : 'Thêm thể loại'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: Tạo hồ sơ đầu sách cơ bản - S2-01.1 */}
      {isBookModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
          <div className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
            <div className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
              <div>
                <h3 className="text-lg font-semibold text-slate-900">Tạo hồ sơ đầu sách</h3>
                <p className="mt-0.5 text-xs text-slate-500">
                  ISBN có thể để trống; nếu nhập phải gồm đúng 10 hoặc 13 chữ số và không được trùng.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setIsBookModalOpen(false)}
                disabled={bookSubmitting}
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50"
                aria-label="Đóng biểu mẫu"
              >
                <X size={19} />
              </button>
            </div>

            <form onSubmit={handleBookSubmit} className="space-y-5 p-6">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">
                    Nhan đề <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={bookForm.title}
                    onChange={(e) => {
                      setBookForm({ ...bookForm, title: e.target.value })
                      setBookFormError('')
                    }}
                    placeholder="Ví dụ: Tôi thấy hoa vàng trên cỏ xanh"
                    maxLength={255}
                    required
                    autoFocus
                    className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                  />
                </div>

                <div className="sm:col-span-2">
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">
                    Nhan đề phụ
                  </label>
                  <input
                    type="text"
                    value={bookForm.subtitle}
                    onChange={(e) => setBookForm({ ...bookForm, subtitle: e.target.value })}
                    placeholder="Nhập nhan đề phụ nếu có"
                    maxLength={255}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                  />
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">
                    Tác giả <span className="text-red-500">*</span>
                  </label>
                  <select
                    value=""
                    onChange={(e) => {
                      const authorId = Number(e.target.value)
                      if (!authorId) return

                      if (bookForm.authorIds.includes(authorId)) {
                        setBookFormError('Tác giả này đã được chọn cho đầu sách.')
                        return
                      }

                      setBookForm({
                        ...bookForm,
                        authorIds: [...bookForm.authorIds, authorId],
                      })
                      setBookFormError('')
                    }}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                  >
                    <option value="">-- Thêm tác giả --</option>
                    {activeAuthorsForCataloging.map((author) => (
                      <option
                        key={author.id}
                        value={author.id}
                        disabled={bookForm.authorIds.includes(author.id)}
                      >
                        {author.name}{bookForm.authorIds.includes(author.id) ? ' — Đã chọn' : ''}
                      </option>
                    ))}
                  </select>

                  {bookForm.authorIds.length > 0 ? (
                    <div className="mt-2 flex flex-wrap gap-2">
                      {bookForm.authorIds.map((authorId) => {
                        const author = activeAuthorsForCataloging.find((item) => item.id === authorId)
                        if (!author) return null

                        return (
                          <span
                            key={author.id}
                            className="inline-flex items-center gap-1.5 rounded-full bg-blue-50 px-3 py-1.5 text-xs font-medium text-blue-700"
                          >
                            {author.name}
                            <button
                              type="button"
                              onClick={() => {
                                setBookForm({
                                  ...bookForm,
                                  authorIds: bookForm.authorIds.filter((id) => id !== author.id),
                                })
                                setBookFormError('')
                              }}
                              className="rounded-full p-0.5 hover:bg-blue-100"
                              aria-label={`Bỏ tác giả ${author.name}`}
                              title={`Bỏ tác giả ${author.name}`}
                            >
                              <X size={13} />
                            </button>
                          </span>
                        )
                      })}
                    </div>
                  ) : (
                    <p className="mt-1 text-[11px] text-slate-400">
                      Chọn ít nhất một tác giả. Có thể thêm nhiều tác giả và bỏ từng tác giả trước khi lưu.
                    </p>
                  )}
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">
                    Thể loại <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={bookForm.categoryId}
                    onChange={(e) => {
                      setBookForm({ ...bookForm, categoryId: e.target.value })
                      setBookFormError('')
                    }}
                    required
                    className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                  >
                    <option value="">-- Chọn thể loại --</option>
                    {activeCategoriesForCataloging.map((cat) => (
                      <option key={cat.id} value={cat.id}>
                        {cat.level === 2 ? `↳ ${cat.name} (${cat.parentName})` : cat.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">
                    ISBN
                  </label>
                  <input
                    type="text"
                    value={bookForm.isbn}
                    onChange={(e) => {
                      setBookForm({ ...bookForm, isbn: e.target.value })
                      setIsbnError('')
                      setBookFormError('')
                    }}
                    placeholder="Nhập ISBN 10 hoặc 13 chữ số nếu có"
                    maxLength={50}
                    inputMode="numeric"
                    aria-invalid={Boolean(isbnError)}
                    aria-describedby={isbnError ? 'book-isbn-error' : 'book-isbn-help'}
                    className={`w-full rounded-lg border px-3 py-2.5 text-sm outline-none ${
                      isbnError
                        ? 'border-red-400 focus:border-red-500 focus:ring-1 focus:ring-red-500'
                        : 'border-slate-300 focus:border-blue-500'
                    }`}
                  />
                  {isbnError ? (
                    <p id="book-isbn-error" className="mt-1 text-xs font-medium text-red-600">
                      {isbnError}
                    </p>
                  ) : (
                    <p id="book-isbn-help" className="mt-1 text-[11px] text-slate-400">
                      Có thể để trống. Nếu nhập, chỉ chấp nhận đúng 10 hoặc 13 chữ số.
                    </p>
                  )}
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">
                    Nhà xuất bản <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={bookForm.publisher}
                    onChange={(e) => {
                      setBookForm({ ...bookForm, publisher: e.target.value })
                      setBookFormError('')
                    }}
                    required
                    className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                  >
                    <option value="">-- Chọn nhà xuất bản --</option>
                    {publisherOptions.map((publisher) => (
                      <option key={publisher} value={publisher}>{publisher}</option>
                    ))}
                  </select>
                  {publisherOptions.length === 0 && (
                    <p className="mt-1 text-[11px] text-amber-600">
                      Chưa có nhà xuất bản nào trong dữ liệu đầu sách hiện tại.
                    </p>
                  )}
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">
                    Năm xuất bản <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    value={bookForm.publicationYear}
                    onChange={(e) => {
                      setBookForm({ ...bookForm, publicationYear: e.target.value })
                      setBookFormError('')
                    }}
                    min={1}
                    max={new Date().getFullYear()}
                    step={1}
                    required
                    placeholder={String(new Date().getFullYear())}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                  />
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">
                    Số trang <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    value={bookForm.pageCount}
                    onChange={(e) => {
                      setBookForm({ ...bookForm, pageCount: e.target.value })
                      setBookFormError('')
                    }}
                    min={1}
                    step={1}
                    required
                    placeholder="Ví dụ: 320"
                    className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Tóm tắt nội dung
                </label>
                <textarea
                  rows={4}
                  value={bookForm.description}
                  onChange={(e) => setBookForm({ ...bookForm, description: e.target.value })}
                  placeholder="Nhập tóm tắt nội dung đầu sách..."
                  maxLength={1000}
                  className="w-full resize-y rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500"
                />
              </div>

              {bookFormError && (
                <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
                  <AlertCircle size={16} className="shrink-0" />
                  <span>{bookFormError}</span>
                </div>
              )}

              <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
                <button
                  type="button"
                  onClick={() => setIsBookModalOpen(false)}
                  disabled={bookSubmitting}
                  className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Huỷ
                </button>
                <button
                  type="submit"
                  disabled={bookSubmitting || publisherOptions.length === 0}
                  className="rounded-lg bg-emerald-600 px-5 py-2 text-sm font-medium text-white shadow-sm hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {bookSubmitting ? 'Đang lưu...' : 'Lưu đầu sách'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* S2-01.4: Cảnh báo nhan đề trùng. Không lưu cho tới khi thủ thư xác nhận. */}
      {duplicateTitleWarning && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
          <div className="w-full max-w-2xl rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start gap-4 border-b border-amber-200 bg-amber-50 px-6 py-5">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-amber-100 text-amber-700">
                <AlertTriangle size={22} />
              </div>
              <div className="min-w-0 flex-1">
                <h3 className="text-lg font-semibold text-slate-900">Nhan đề đã tồn tại</h3>
                <p className="mt-1 text-sm leading-relaxed text-slate-700">
                  Đã tìm thấy hồ sơ có nhan đề <strong>“{duplicateTitleWarning.title}”</strong>.
                  Hệ thống chưa lưu đầu sách mới. Hãy kiểm tra bản ghi cũ hoặc xác nhận nếu đây thực sự là một đầu sách cần tạo riêng.
                </p>
                {duplicateTitleWarning.matchingRule && (
                  <p className="mt-2 text-xs text-amber-800">{duplicateTitleWarning.matchingRule}</p>
                )}
              </div>
            </div>

            <div className="max-h-[52vh] space-y-3 overflow-y-auto p-6">
              {duplicateTitleWarning.books.length > 0 ? (
                duplicateTitleWarning.books.map((book) => {
                  const authorNames = (book.authors?.length
                    ? book.authors.map((author) => author.name)
                    : [book.authorName]
                  ).filter(Boolean).join(', ')

                  return (
                    <div key={book.id} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="min-w-0">
                          <p className="font-semibold text-slate-900">{book.title}</p>
                          <div className="mt-2 grid gap-1 text-xs text-slate-600 sm:grid-cols-2">
                            <span><strong>Tác giả:</strong> {authorNames || 'Không rõ'}</span>
                            <span><strong>ISBN:</strong> {book.isbn || 'Chưa có ISBN'}</span>
                            <span><strong>NXB:</strong> {book.publisher || '—'}</span>
                            <span><strong>Năm XB:</strong> {book.publicationYear || '—'}</span>
                          </div>
                        </div>
                        <Link
                          to={`/books/${book.id}`}
                          target="_blank"
                          rel="noreferrer"
                          className="shrink-0 rounded-lg border border-blue-200 bg-white px-3 py-2 text-xs font-semibold text-blue-700 hover:bg-blue-50"
                        >
                          Mở hồ sơ cũ
                        </Link>
                      </div>
                    </div>
                  )
                })
              ) : (
                <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                  Hệ thống phát hiện nhan đề trùng nhưng chưa tải được thông tin hồ sơ cũ. Có thể hủy để kiểm tra danh mục hoặc xác nhận vẫn tạo.
                </div>
              )}
            </div>

            <div className="flex flex-col-reverse gap-3 border-t border-slate-200 px-6 py-4 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => setDuplicateTitleWarning(null)}
                disabled={bookSubmitting}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Hủy, quay lại kiểm tra
              </button>
              <button
                type="button"
                onClick={() => void confirmDuplicateTitleAndCreate()}
                disabled={bookSubmitting}
                className="rounded-lg bg-amber-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-amber-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {bookSubmitting ? 'Đang lưu...' : 'Vẫn tạo đầu sách mới'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DIALOG: Xác nhận xoá / Cảnh báo ràng buộc không cho xoá */}
      {deleteDialog.open && deleteDialog.item && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            {deleteDialog.cannotDeleteReason ? (
              // Không cho xoá - Cảnh báo ràng buộc
              <div>
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-amber-100 text-amber-600 mb-4">
                  <AlertTriangle size={24} />
                </div>
                <h3 className="text-lg font-semibold text-slate-900">
                  Không thể xoá {deleteDialog.type === 'author' ? 'tác giả' : 'thể loại'}
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-slate-600">
                  {deleteDialog.cannotDeleteReason}
                </p>
                <div className="mt-6 flex justify-end">
                  <button
                    type="button"
                    onClick={() => setDeleteDialog({ open: false, type: 'author', item: null })}
                    className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
                  >
                    Đã hiểu
                  </button>
                </div>
              </div>
            ) : (
              // Cho phép xoá khi 0 đầu sách
              <div>
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600 mb-4">
                  <Trash2 size={24} />
                </div>
                <h3 className="text-lg font-semibold text-slate-900">
                  Xác nhận xoá {deleteDialog.type === 'author' ? 'tác giả' : 'thể loại'}
                </h3>
                <p className="mt-2 text-sm text-slate-600">
                  Bạn có chắc chắn muốn xoá{' '}
                  {deleteDialog.type === 'author' ? 'tác giả' : 'thể loại'}{' '}
                  <strong>"{deleteDialog.item.name}"</strong> khỏi hệ thống? Mục này hiện chưa gắn
                  với đầu sách nào.
                </p>
                <div className="mt-6 flex justify-end gap-3">
                  <button
                    type="button"
                    onClick={() => setDeleteDialog({ open: false, type: 'author', item: null })}
                    className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    Huỷ
                  </button>
                  <button
                    type="button"
                    onClick={confirmDelete}
                    className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 shadow-sm"
                  >
                    Xác nhận xoá
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// ==========================================
// TABLE: Authors Table
// ==========================================
interface AuthorsTableProps {
  items: Author[]
  onEdit: (author: Author) => void
  onToggle: (author: Author) => void
  onDelete: (author: Author) => void
}

function AuthorsTable({ items, onEdit, onToggle, onDelete }: AuthorsTableProps) {
  if (items.length === 0) {
    return (
      <div className="py-12 text-center">
        <UserRound size={36} className="mx-auto text-slate-300" />
        <p className="mt-2 text-sm font-medium text-slate-600">Không tìm thấy tác giả nào</p>
      </div>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
            <th className="px-6 py-3.5">Tác giả</th>
            <th className="px-6 py-3.5 text-center">Số đầu sách</th>
            <th className="px-6 py-3.5 text-center">Trạng thái</th>
            <th className="px-6 py-3.5 text-right">Thao tác</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((author) => (
            <tr key={author.id} className="hover:bg-slate-50/70 transition">
              <td className="px-6 py-4">
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
                    <UserRound size={18} />
                  </div>
                  <div>
                    <div className="font-semibold text-slate-900">{author.name}</div>
                    <div className="text-xs text-slate-500 max-w-md truncate">
                      {author.note || 'Chưa có ghi chú'}
                    </div>
                  </div>
                </div>
              </td>

              <td className="px-6 py-4 text-center">
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    author.bookCount > 0
                      ? 'bg-blue-50 text-blue-700'
                      : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {author.bookCount} sách
                </span>
              </td>

              <td className="px-6 py-4 text-center">
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${
                    author.active
                      ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                      : 'bg-slate-100 text-slate-600 border border-slate-200'
                  }`}
                >
                  {author.active ? 'Đang sử dụng' : 'Ngừng sử dụng'}
                </span>
              </td>

              <td className="px-6 py-4 text-right">
                <div className="flex items-center justify-end gap-1.5">
                  <button
                    type="button"
                    onClick={() => onEdit(author)}
                    title="Chỉnh sửa tác giả"
                    className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-blue-600 transition"
                  >
                    <Pencil size={16} />
                  </button>

                  <button
                    type="button"
                    onClick={() => onToggle(author)}
                    title={author.active ? 'Ngừng sử dụng tác giả' : 'Kích hoạt lại tác giả'}
                    className={`rounded-lg p-1.5 transition ${
                      author.active
                        ? 'text-slate-500 hover:bg-amber-50 hover:text-amber-600'
                        : 'text-slate-500 hover:bg-emerald-50 hover:text-emerald-600'
                    }`}
                  >
                    <Power size={16} />
                  </button>

                  <button
                    type="button"
                    onClick={() => onDelete(author)}
                    title={
                      author.bookCount > 0
                        ? 'Không thể xoá vì đang gắn với đầu sách'
                        : 'Xoá tác giả'
                    }
                    className="rounded-lg p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600 transition"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ==========================================
// TABLE: Categories Table
// ==========================================
interface CategoriesTableProps {
  items: Category[]
  onEdit: (category: Category) => void
  onToggle: (category: Category) => void
  onDelete: (category: Category) => void
}

function CategoriesTable({ items, onEdit, onToggle, onDelete }: CategoriesTableProps) {
  if (items.length === 0) {
    return (
      <div className="py-12 text-center">
        <Tags size={36} className="mx-auto text-slate-300" />
        <p className="mt-2 text-sm font-medium text-slate-600">Không tìm thấy thể loại nào</p>
      </div>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
            <th className="px-6 py-3.5">Thể loại</th>
            <th className="px-6 py-3.5">Thuộc thể loại cha</th>
            <th className="px-6 py-3.5 text-center">Cấp</th>
            <th className="px-6 py-3.5 text-center">Số đầu sách</th>
            <th className="px-6 py-3.5 text-center">Trạng thái</th>
            <th className="px-6 py-3.5 text-right">Thao tác</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((cat) => (
            <tr key={cat.id} className="hover:bg-slate-50/70 transition">
              <td className="px-6 py-4">
                <div className="flex items-center gap-2.5">
                  {cat.level === 2 && (
                    <CornerDownRight size={16} className="text-slate-400 shrink-0 ml-3" />
                  )}
                  <div
                    className={`flex h-8 w-8 items-center justify-center rounded-lg ${
                      cat.level === 1
                        ? 'bg-violet-100 text-violet-700'
                        : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    <Tags size={16} />
                  </div>
                  <div>
                    <div className="font-semibold text-slate-900">{cat.name}</div>
                    <div className="text-xs text-slate-500 max-w-sm truncate">
                      {cat.description || 'Chưa có mô tả'}
                    </div>
                  </div>
                </div>
              </td>

              <td className="px-6 py-4 text-sm text-slate-600">
                {cat.parentName ? (
                  <span className="font-medium text-violet-900">{cat.parentName}</span>
                ) : (
                  <span className="text-slate-400">— (Thể loại gốc)</span>
                )}
              </td>

              <td className="px-6 py-4 text-center">
                <span
                  className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold ${
                    cat.level === 1
                      ? 'bg-violet-50 text-violet-700 border border-violet-200'
                      : 'bg-sky-50 text-sky-700 border border-sky-200'
                  }`}
                >
                  Cấp {cat.level}
                </span>
              </td>

              <td className="px-6 py-4 text-center">
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    cat.bookCount > 0
                      ? 'bg-blue-50 text-blue-700'
                      : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {cat.bookCount} sách
                </span>
              </td>

              <td className="px-6 py-4 text-center">
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${
                    cat.active
                      ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                      : 'bg-slate-100 text-slate-600 border border-slate-200'
                  }`}
                >
                  {cat.active ? 'Đang sử dụng' : 'Ngừng sử dụng'}
                </span>
              </td>

              <td className="px-6 py-4 text-right">
                <div className="flex items-center justify-end gap-1.5">
                  <button
                    type="button"
                    onClick={() => onEdit(cat)}
                    title="Chỉnh sửa thể loại"
                    className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-blue-600 transition"
                  >
                    <Pencil size={16} />
                  </button>

                  <button
                    type="button"
                    onClick={() => onToggle(cat)}
                    title={cat.active ? 'Ngừng sử dụng thể loại' : 'Kích hoạt lại thể loại'}
                    className={`rounded-lg p-1.5 transition ${
                      cat.active
                        ? 'text-slate-500 hover:bg-amber-50 hover:text-amber-600'
                        : 'text-slate-500 hover:bg-emerald-50 hover:text-emerald-600'
                    }`}
                  >
                    <Power size={16} />
                  </button>

                  <button
                    type="button"
                    onClick={() => onDelete(cat)}
                    title={
                      cat.bookCount > 0
                        ? 'Không thể xoá vì đang gắn với đầu sách'
                        : 'Xoá thể loại'
                    }
                    className="rounded-lg p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600 transition"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ==========================================
// TABLE: Books Table (Kiểm chứng biên mục)
// ==========================================
interface BooksTableProps {
  items: Book[]
  onOpenCatalogModal: () => void
}

function BooksTable({ items, onOpenCatalogModal }: BooksTableProps) {
  if (items.length === 0) {
    return (
      <div className="py-12 text-center">
        <BookOpen size={36} className="mx-auto text-slate-300" />
        <p className="mt-2 text-sm font-medium text-slate-600">Chưa có đầu sách nào được biên mục</p>
        <button
          type="button"
          onClick={onOpenCatalogModal}
          className="mt-3 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700"
        >
          <Plus size={15} />
          <span>Biên mục cuốn sách đầu tiên</span>
        </button>
      </div>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
            <th className="px-6 py-3.5">Tiêu đề sách / ISBN</th>
            <th className="px-6 py-3.5">Tác giả</th>
            <th className="px-6 py-3.5">Thể loại</th>
            <th className="px-6 py-3.5">Nhà xuất bản</th>
            <th className="px-6 py-3.5 text-center">Năm XB</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((book) => (
            <tr key={book.id} className="hover:bg-slate-50/70 transition">
              <td className="px-6 py-4">
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600">
                    <BookOpen size={18} />
                  </div>
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <Link to={`/books/${book.id}`} className="font-semibold text-blue-700 hover:underline">{book.title}</Link>
                      {!book.hasCopies && (
                        <StatusBadge status="NO_COPY" />
                      )}
                    </div>
                    <div className="mt-1 text-xs text-slate-500">
                      {book.hasCopies
                        ? `${book.copyCount} bản sao · Nhấn tên sách để xem chi tiết`
                        : 'Nhấn tên sách để xem chi tiết và thêm bản sao'}
                    </div>
                    <div className="text-xs text-slate-500">
                      {book.isbn ? `ISBN: ${book.isbn}` : 'Chưa có ISBN'}
                    </div>
                    <div className="mt-2">
                      <Link to={`/books/${book.id}/cover/edit`} className="text-xs font-semibold text-blue-600 hover:underline">
                        Chỉnh sửa ảnh bìa
                      </Link>
                    </div>
                  </div>
                </div>
              </td>

              <td className="px-6 py-4">
                <div className="flex flex-wrap items-center gap-1.5">
                  {(book.authors?.length
                    ? book.authors
                    : [{ id: book.authorId ?? -1, name: book.authorName, active: book.authorActive }]
                  ).map((author) => (
                    <span
                      key={author.id}
                      className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700"
                    >
                      {author.name}
                      {!author.active && (
                        <span
                          title="Tác giả này đã ngừng sử dụng nhưng vẫn hiển thị chính xác trên sách cũ"
                          className="rounded bg-amber-100 px-1 py-0.5 text-[9px] font-semibold text-amber-800"
                        >
                          Đã ngừng dùng
                        </span>
                      )}
                    </span>
                  ))}
                </div>
              </td>

              <td className="px-6 py-4">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-slate-800">{book.categoryName}</span>
                  {!book.categoryActive && (
                    <span
                      title="Thể loại này đã ngừng sử dụng nhưng vẫn hiển thị chính xác trên sách cũ"
                      className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-800"
                    >
                      Đã ngừng dùng
                    </span>
                  )}
                </div>
              </td>

              <td className="px-6 py-4 text-sm text-slate-600">
                {book.publisher || '—'}
              </td>

              <td className="px-6 py-4 text-center text-sm font-medium text-slate-700">
                {book.publicationYear || '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
