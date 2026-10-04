import { useState } from 'react'
import { publicCoverUrl } from './bookCoverService'

const DEFAULT_COVER = `${import.meta.env.BASE_URL}images/default-book-cover.svg`

export default function PublicBookCover({ bookId, url, title, thumbnail = false }: {
  bookId: number; url?: string | null; title: string; thumbnail?: boolean
}) {
  const original = url?.trim() ?? ''
  const managed = original === `/api/v1/books/public/${bookId}/cover`
  const source = original
    ? publicCoverUrl(thumbnail && managed ? `${original}/thumbnail` : original)
    : DEFAULT_COVER
  // Track the failed source instead of a single flag: navigating to another book resets the fallback.
  const [failedSource, setFailedSource] = useState('')
  const fallback = !original || failedSource === source
  return <img
    src={fallback ? DEFAULT_COVER : source}
    alt={fallback ? `Ảnh bìa mặc định của ${title}` : `Ảnh bìa ${title}`}
    width={thumbnail ? 160 : 224}
    height={thumbnail ? 240 : 336}
    loading={thumbnail ? 'lazy' : 'eager'}
    decoding="async"
    onError={fallback ? undefined : () => setFailedSource(source)}
    className={thumbnail
      ? 'mx-auto mb-4 h-60 w-40 rounded-lg border border-slate-200 bg-slate-50 object-contain'
      : 'h-full w-full object-contain'}
  />
}
