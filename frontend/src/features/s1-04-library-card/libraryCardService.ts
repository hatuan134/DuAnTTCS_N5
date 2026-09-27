import { apiClient } from '../../core/api/apiClient'

export interface PendingReaderApplication {
  userId: number
  fullName: string
  memberCode: string
  dateOfBirth: string
  email: string
  phone?: string | null
  address?: string | null
  submittedAt: string
}

export interface LibraryCard {
  id: number
  cardNumber: string
  userId: number
  readerName: string
  memberCode: string
  cardTypeId: number
  cardTypeName: string
  issuedAt: string
  expiresAt: string
  status: string
  createdAt: string
}

export interface MyLibraryCard {
  userId: number
  fullName: string
  memberCode: string
  dateOfBirth: string
  registrationStatus: string
  rejectionReason?: string | null
  cardNumber?: string | null
  cardTypeName?: string | null
  issuedAt?: string | null
  expiresAt?: string | null
  cardStatus?: string | null
}

export interface PendingFilters {
  search?: string
  fromDate?: string
  toDate?: string
}

export const libraryCardService = {
  getPending: async (
    filters: PendingFilters = {},
  ): Promise<PendingReaderApplication[]> => {
    const response = await apiClient.get<PendingReaderApplication[]>(
      '/library-cards/pending',
      { params: filters },
    )
    return response.data
  },

  getIssued: async (): Promise<LibraryCard[]> => {
    const response = await apiClient.get<LibraryCard[]>('/library-cards')
    return response.data
  },

  approve: async (
    userId: number,
    cardTypeId: number,
    expiresAt: string,
  ): Promise<LibraryCard> => {
    const response = await apiClient.post<LibraryCard>(
      `/library-cards/${userId}/approve`,
      { cardTypeId, expiresAt },
    )
    return response.data
  },

  reject: async (
    userId: number,
    reason: string,
  ): Promise<void> => {
    await apiClient.post(`/library-cards/${userId}/reject`, { reason })
  },

  getMine: async (): Promise<MyLibraryCard> => {
    const response = await apiClient.get<MyLibraryCard>('/library-cards/me')
    return response.data
  },
}
