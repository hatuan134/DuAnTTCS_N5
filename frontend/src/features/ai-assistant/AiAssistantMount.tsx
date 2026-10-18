import { lazy, Suspense } from 'react'
const Assistant = lazy(() => import('./AiAssistant'))
export default function AiAssistantMount() { return <Suspense fallback={null}><Assistant /></Suspense> }
