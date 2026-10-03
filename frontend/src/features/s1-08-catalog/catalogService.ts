import { apiClient } from '../../core/api/apiClient'

export interface Author {
  id: number
  name: string
  note: string
  active: boolean
  bookCount: number
  createdAt: string
  updatedAt: string
}

export interface Category {
  id: number
  name: string
  description: string
  parentId: number | null
  parentName: string | null
  level: number
  active: boolean
  bookCount: number
  createdAt: string
  updatedAt: string
}

export interface BookAuthor {
  id: number
  name: string
  active: boolean
}

export interface Book {
  id: number
  isbn: string | null
  title: string
  subtitle: string | null
  // Ba trường dưới được giữ để tương thích response cũ.
  authorId: number | null
  authorName: string
  authorActive: boolean
  authors: BookAuthor[]
  categoryId: number
  categoryName: string
  categoryActive: boolean
  publisher: string | null
  publicationYear: number | null
  pageCount: number | null
  description: string | null
  createdAt: string
  copyCount: number
  hasCopies: boolean
  availableCount: number
  coverImageUrl: string | null
  availableCopies?: BookAvailableCopyLocation[]
  queueCount?: number | null
  earliestExpectedReturnDate?: string | null
  expectedReturnNotice?: string | null
}

export interface BookQueueInfo {
  queueCount: number
  earliestExpectedReturnDate?: string | null
  expectedReturnNotice?: string | null
  hasOverdueCopies: boolean
}

export interface BookAvailableCopyLocation {
  copyId: number
  barcode: string | null
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  shelfId: number
  shelfCode: string
  shelfName: string | null
}

export interface PublicCatalogFilters {
  categoryId?: number
  publicationYear?: number
  availableOnly?: boolean
}

export type PublicCatalogSort = 'relevance' | 'publicationYear'

export interface PublicCatalogPage {
  content: Book[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  sort: PublicCatalogSort
}

export interface PublicCatalogSearch extends PublicCatalogFilters {
  keyword: string
  page: number
  sort: PublicCatalogSort
}

export interface PublicCatalogFilterOptions {
  categories: { id: number; name: string }[]
  publicationYears: number[]
}

export interface AuthorForm {
  name: string
  note: string
}

export interface CategoryForm {
  name: string
  description: string
  parentId: number | null
}

export interface CatalogBookForm {
  title: string
  subtitle?: string
  authorIds: number[]
  categoryId: number
  isbn?: string
  publisher: string
  publicationYear: number
  pageCount: number
  description?: string
  confirmDuplicateTitle?: boolean
}

export const catalogService = {
  // --- AUTHORS ---
  getAuthors: async (): Promise<Author[]> => {
    const res = await apiClient.get<Author[]>('/authors')
    return res.data
  },

  getActiveAuthors: async (): Promise<Author[]> => {
    const res = await apiClient.get<Author[]>('/authors/active')
    return res.data
  },

  getAuthorById: async (id: number): Promise<Author> => {
    const res = await apiClient.get<Author>(`/authors/${id}`)
    return res.data
  },

  createAuthor: async (data: AuthorForm): Promise<Author> => {
    const res = await apiClient.post<Author>('/authors', data)
    return res.data
  },

  updateAuthor: async (id: number, data: AuthorForm): Promise<Author> => {
    const res = await apiClient.put<Author>(`/authors/${id}`, data)
    return res.data
  },

  toggleAuthorStatus: async (id: number): Promise<Author> => {
    const res = await apiClient.patch<Author>(`/authors/${id}/toggle-status`)
    return res.data
  },

  deleteAuthor: async (id: number): Promise<void> => {
    await apiClient.delete(`/authors/${id}`)
  },

  // --- CATEGORIES ---
  getCategories: async (): Promise<Category[]> => {
    const res = await apiClient.get<Category[]>('/categories')
    return res.data
  },

  getActiveCategories: async (): Promise<Category[]> => {
    const res = await apiClient.get<Category[]>('/categories/active')
    return res.data
  },

  getCategoryById: async (id: number): Promise<Category> => {
    const res = await apiClient.get<Category>(`/categories/${id}`)
    return res.data
  },

  createCategory: async (data: CategoryForm): Promise<Category> => {
    const res = await apiClient.post<Category>('/categories', data)
    return res.data
  },

  updateCategory: async (id: number, data: CategoryForm): Promise<Category> => {
    const res = await apiClient.put<Category>(`/categories/${id}`, data)
    return res.data
  },

  toggleCategoryStatus: async (id: number): Promise<Category> => {
    const res = await apiClient.patch<Category>(`/categories/${id}/toggle-status`)
    return res.data
  },

  deleteCategory: async (id: number): Promise<void> => {
    await apiClient.delete(`/categories/${id}`)
  },

  // --- BOOKS / BIÊN MỤC ---
  getBooks: async (): Promise<Book[]> => {
    const res = await apiClient.get<Book[]>('/books')
    return res.data
  },

  getPublicBooks: async (
    keyword?: string,
    filters: PublicCatalogFilters = {},
    signal?: AbortSignal,
  ): Promise<Book[]> => {
    const normalizedKeyword = keyword?.trim()
    const res = await apiClient.get<Book[]>('/books/public', {
      params: {
        keyword: normalizedKeyword || undefined,
        categoryId: filters.categoryId,
        publicationYear: filters.publicationYear,
        availableOnly: filters.availableOnly || undefined,
      },
      signal,
    })
    return res.data
  },

  searchPublicBooks: async (
    query: PublicCatalogSearch,
    signal?: AbortSignal,
  ): Promise<PublicCatalogPage> => {
    const res = await apiClient.get<PublicCatalogPage>('/books/public/search', {
      params: {
        keyword: query.keyword.trim() || undefined,
        categoryId: query.categoryId,
        publicationYear: query.publicationYear,
        availableOnly: query.availableOnly || undefined,
        page: query.page,
        size: 20,
        sort: query.sort,
      },
      signal,
    })
    return res.data
  },

  getPublicFilterOptions: async (signal?: AbortSignal): Promise<PublicCatalogFilterOptions> => {
    const res = await apiClient.get<PublicCatalogFilterOptions>('/books/public/filters', { signal })
    return res.data
  },

  getPublicBookById: async (id: number): Promise<Book> => {
    const res = await apiClient.get<Book>(`/books/public/${id}`)
    return res.data
  },

  getPublicAvailableCopies: async (id: number): Promise<BookAvailableCopyLocation[]> => {
    const res = await apiClient.get<BookAvailableCopyLocation[]>(`/books/public/${id}/available-copies`)
    return res.data
  },

  getPublicBookQueue: async (id: number): Promise<BookQueueInfo> => {
    const res = await apiClient.get<BookQueueInfo>(`/books/public/${id}/queue`)
    return res.data
  },

  getPublisherOptions: async (): Promise<string[]> => {
    const res = await apiClient.get<string[]>('/books/publishers')
    return res.data
  },

  catalogBook: async (data: CatalogBookForm): Promise<Book> => {
    const res = await apiClient.post<Book>('/books', data)
    return res.data
  },
}
