import type { FeatureRouteModule } from '../../app/routing/featureRegistry'
import AuditSearchView from './AuditSearchView.vue'

export const routes: FeatureRouteModule['routes'] = [
  { path: '/audit/events', name: 'audit-events', component: AuditSearchView },
]

export const menuItems: FeatureRouteModule['menuItems'] = [
  { id: 'audit-events', label: '审计日志', to: '/audit/events', order: 30 },
]
