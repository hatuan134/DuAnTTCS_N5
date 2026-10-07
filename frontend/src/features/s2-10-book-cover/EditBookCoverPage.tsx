import { Navigate, useParams } from 'react-router-dom'

export default function EditBookCoverPage() {
  const { bookId } = useParams()

  return (
    <Navigate
      to={`/books/${bookId ?? ''}`}
      replace
      state={{ openCoverEditor: true }}
    />
  )
}
