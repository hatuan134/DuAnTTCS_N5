import type { PublicCatalogSearch } from './catalogService'

export function readCatalogQuery(params: URLSearchParams): PublicCatalogSearch {
  const positive = (key: string) => { const n = Number(params.get(key)); return Number.isSafeInteger(n) && n > 0 ? n : undefined }
  return { keyword: params.get('keyword')?.trim() ?? '', page: 0,
    sort: params.get('sort') === 'publicationYear' ? 'publicationYear' : 'relevance',
    categoryId: positive('categoryId'), publicationYear: positive('publicationYear'), availableOnly: params.get('availableOnly') === 'true' || undefined }
}

export function catalogQueryParams(query: PublicCatalogSearch) {
  const params = new URLSearchParams()
  if (query.keyword.trim()) params.set('keyword', query.keyword.trim())
  if (query.categoryId) params.set('categoryId', String(query.categoryId))
  if (query.publicationYear) params.set('publicationYear', String(query.publicationYear))
  if (query.availableOnly) params.set('availableOnly', 'true')
  if (query.sort !== 'relevance') params.set('sort', query.sort)
  return params
}
