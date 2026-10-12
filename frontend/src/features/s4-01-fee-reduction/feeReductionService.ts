import { apiClient } from '../../core/api/apiClient'

export interface FeeReductionRequest {
  id: number
  feeId: number
  loanId: number
  loanNumber: string
  readerName: string
  bookTitle: string
  barcode: string
  requestedBy: number | null
  requesterName: string
  originalAmountVnd: number
  requestedAmountVnd: number
  outstandingAmountVnd: number
  reason: string
  submittedAt: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  paidAmountVnd: number
  approvedAmountVnd: number
  decidedBy: number | null
  deciderName: string | null
  decidedAt: string | null
  decisionReason: string | null
}

export interface OverdueFeeDetail {
  id: number
  loanId: number
  loanNumber: string
  readerName: string
  bookTitle: string
  barcode: string
  originalAmountVnd: number
  paidAmountVnd: number
  outstandingAmountVnd: number
  returnedAt: string
  pendingRequest: FeeReductionRequest | null
  waivedAmountVnd: number
  reductionRequests: FeeReductionRequest[]
}

export interface FeeReductionRequestPage {
  items: FeeReductionRequest[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export const feeReductionService = {
  async detail(id: number): Promise<OverdueFeeDetail> {
    return (await apiClient.get<OverdueFeeDetail>(`/overdue-fees/${id}`)).data
  },
  async create(id: number, amountVnd: number, reason: string): Promise<FeeReductionRequest> {
    return (await apiClient.post<FeeReductionRequest>(`/overdue-fees/${id}/reduction-requests`, { amountVnd, reason })).data
  },
  async pending(page: number): Promise<FeeReductionRequestPage> {
    return (await apiClient.get<FeeReductionRequestPage>('/overdue-fees/reduction-requests/pending', { params: { page } })).data
  },
  async approve(requestId: number): Promise<FeeReductionRequest> {
    return (await apiClient.post<FeeReductionRequest>(`/overdue-fees/reduction-requests/${requestId}/approve`)).data
  },
  async reject(requestId: number, reason: string): Promise<FeeReductionRequest> {
    return (await apiClient.post<FeeReductionRequest>(`/overdue-fees/reduction-requests/${requestId}/reject`, { reason })).data
  },
}

export function canApproveFeeReduction(request: FeeReductionRequest): boolean {
  return request.status === 'PENDING' && request.paidAmountVnd === 0
    && request.requestedAmountVnd > 0 && request.requestedAmountVnd <= request.outstandingAmountVnd
}

export function feeAfterApproval(request: FeeReductionRequest): number | null {
  return canApproveFeeReduction(request) ? request.outstandingAmountVnd - request.requestedAmountVnd : null
}
