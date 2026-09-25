import type { FeatureModule } from '../types/feature'

type FeatureFile = {
  default: FeatureModule
}

const featureFiles = import.meta.glob<FeatureFile>(
  '../features/*/feature.tsx',
  {
    eager: true,
  },
)

export const featureModules = Object.values(featureFiles)
  .map((module) => module.default)
  .sort((a, b) => a.order - b.order)

export const publicRoutes = featureModules.flatMap(
  (feature) => feature.publicRoutes ?? [],
)

export const appRoutes = featureModules.flatMap(
  (feature) => feature.appRoutes ?? [],
)

export const navItems = featureModules
  .flatMap((feature) => feature.navItems ?? [])
  .sort((a, b) => a.order - b.order)