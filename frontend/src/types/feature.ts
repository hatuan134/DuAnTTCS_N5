import type { LucideIcon } from 'lucide-react'
import type { RouteObject } from 'react-router-dom'

export interface NavItem {
  label: string
  to: string
  icon: LucideIcon
  order: number
}

export interface FeatureModule {
  id: string
  order: number

  publicRoutes?: RouteObject[]
  appRoutes?: RouteObject[]

  navItems?: NavItem[]
}