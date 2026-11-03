import type { RouteRecordRaw } from 'vue-router'

export interface FeatureMenuItem {
  id: string
  label: string
  to: string
  order: number
}

export interface FeatureRouteModule {
  routes: RouteRecordRaw[]
  menuItems?: FeatureMenuItem[]
}

const discovered = import.meta.glob<FeatureRouteModule>('../../features/*/routes.ts', {
  eager: true,
})

const modules = Object.entries(discovered).sort(([left], [right]) => left.localeCompare(right))

export const featureRoutes: RouteRecordRaw[] = modules.flatMap(([, module]) => module.routes)

export const featureMenuItems: FeatureMenuItem[] = modules
  .flatMap(([, module]) => module.menuItems ?? [])
  .sort((left, right) => left.order - right.order || left.id.localeCompare(right.id))
