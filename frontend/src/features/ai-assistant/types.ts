export type AiAction = { label: string; href: string }
export type AiRow = Record<string, unknown>
export type AiBlock = { type: 'books' | 'loans' | 'libraryCard' | 'reservations' | 'statistics' | 'copies' | 'audit' | 'overview' | 'records' | 'navigation' | 'notice'; title: string; items: AiRow[]; meta: AiRow; actions: AiAction[] }
export type AiResponse = { conversationId: string; message: string; blocks: AiBlock[]; provider: string; degraded: boolean; turnsRemaining: number }
export type ChatMessage = { id: number; role: 'user' | 'assistant'; text: string; blocks?: AiBlock[]; provider?: string }
