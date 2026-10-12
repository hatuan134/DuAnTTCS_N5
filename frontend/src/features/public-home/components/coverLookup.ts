import type { Book } from '../../s1-08-catalog/catalogService'

/**
 * Optional public-edition cover lookup for hero books lacking a stored cover.
 * Never invent a cover or change the library's data. A successful image is
 * a public Open Library cover, which may be a different edition of the work.
 * Only invoked for up to three books in the hero, not for the catalog grid.
 */
const resultCache = new Map<string, Promise<string | null>>()

function normalized(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim()
}

export function isbnCoverUrl(book: Book) {
  const isbn = (book.isbn || '').replace(/[\s-]/g, '')
  return /^(?:\d{13}|\d{9}[\dX])$/i.test(isbn)
    ? `https://covers.openlibrary.org/b/isbn/${encodeURIComponent(isbn)}-L.jpg?default=false`
    : null
}

export function lookupTitleCover(book: Book): Promise<string | null> {
  const title = normalized(book.title)
  const author = normalized(book.authors?.[0]?.name || book.authorName || '')
  const key = `${title}|${author}`
  if (!title) return Promise.resolve(null)
  const cached = resultCache.get(key)
  if (cached) return cached

  const request = (async () => {
    const controller = new AbortController()
    const timeout = window.setTimeout(() => controller.abort(), 5000)
    try {
      const query = new URLSearchParams({ title: book.title, limit: '6', fields: 'title,author_name,cover_i' })
      const response = await fetch(`https://openlibrary.org/search.json?${query}`, {
        signal: controller.signal,
        mode: 'cors',
        credentials: 'omit',
      })
      if (!response.ok) return null
      const data = await response.json() as { docs?: Array<{ title?: string; author_name?: string[]; cover_i?: number }> }
      const match = data.docs?.find(item => {
        if (!item.cover_i || normalized(item.title || '') !== title) return false
        if (!author) return true
        return item.author_name?.some(name => {
          const other = normalized(name)
          return other === author || other.includes(author) || author.includes(other)
        }) ?? false
      })
      return match?.cover_i
        ? `https://covers.openlibrary.org/b/id/${match.cover_i}-L.jpg?default=false`
        : null
    } catch {
      return null
    } finally {
      clearTimeout(timeout)
    }
  })()
  resultCache.set(key, request)
  return request
}
