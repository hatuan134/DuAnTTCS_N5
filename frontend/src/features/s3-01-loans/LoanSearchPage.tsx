import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import LoanLookupPanel from './LoanLookupPanel'
import { loanRoles } from './loanService'

export default function LoanSearchPage() {
  if (!loanRoles.includes(getCurrentUser()?.role ?? '')) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">Bạn không có quyền tra cứu phiếu mượn.</p>
  return <div className="space-y-5">
    <PageHeader title="Tra cứu phiếu mượn" description="Tìm bằng tên, số điện thoại, mã thẻ, mã vạch hoặc mã phiếu. Danh sách hiển thị 10 phiếu mỗi trang, ưu tiên phiếu đang mượn." />
    <LoanLookupPanel showFilters />
  </div>
}
