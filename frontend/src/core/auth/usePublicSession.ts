import { useEffect, useState } from 'react'
import { ensureValidSession } from './authService'
import { getCurrentUser, getAccessToken } from './authStorage'

export default function usePublicSession() {
  const [user, setUser] = useState(() => getAccessToken() ? getCurrentUser() : null)
  useEffect(() => {
    let active = true
    const sync = async () => {
      const valid = await ensureValidSession()
      if (active) setUser(valid ? getCurrentUser() : null)
    }
    void sync()
    window.addEventListener('storage', sync)
    window.addEventListener('libra-session-changed', sync)
    return () => { active = false; window.removeEventListener('storage', sync); window.removeEventListener('libra-session-changed', sync) }
  }, [])
  return user
}
