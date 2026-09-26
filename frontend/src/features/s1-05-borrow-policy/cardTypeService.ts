import { apiClient } from '../../core/api/apiClient'

export interface CardType {
  id: number
  name: string
  description?: string | null
  duration: number
  maxBooks: number
  loanDays: number
  maxRenewals: number
  renewalDays: number
  active: boolean
  createdAt?: string
  updatedAt?: string
  usageCount?: number
}

export interface CardTypeForm {
  name: string
  description: string
  duration: number
  maxBooks: number
  loanDays: number
  maxRenewals: number
  renewalDays: number
}

export interface PolicyHistory {
  id: number
  cardTypeName: string
  action: string
  changedBy: string
  changedAt: string
  before: string
  after: string
}

export const cardTypeService = {
  getCardTypes: async (): Promise<CardType[]> => {
    const response = await apiClient.get<CardType[]>('/card-types')
    return response.data
  },

  getActiveCardTypes: async (): Promise<CardType[]> => {
    const response = await apiClient.get<CardType[]>('/card-types/active')
    return response.data
  },

  getCardTypeById: async (id: number): Promise<CardType> => {
    const response = await apiClient.get<CardType>(`/card-types/${id}`)
    return response.data
  },

  getHistory: async (): Promise<PolicyHistory[]> => {
    const response = await apiClient.get<PolicyHistory[]>('/card-types/history')
    return response.data
  },

  createCardType: async (data: CardTypeForm): Promise<CardType> => {
    const response = await apiClient.post<CardType>('/card-types', data)
    return response.data
  },

  updateCardType: async (id: number, data: CardTypeForm): Promise<CardType> => {
    const response = await apiClient.put<CardType>(`/card-types/${id}`, data)
    return response.data
  },

  toggleStatus: async (id: number): Promise<CardType> => {
    const response = await apiClient.patch<CardType>(`/card-types/${id}/toggle-status`)
    return response.data
  },

  deleteCardType: async (id: number): Promise<void> => {
    await apiClient.delete(`/card-types/${id}`)
  },
}
