import type { FeatureRouteModule } from '../../app/routing/featureRegistry'
import TransferManagementView from './TransferManagementView.vue'
import TransferHandoffView from './TransferHandoffView.vue'

export const routes: FeatureRouteModule['routes'] = [
  { path: '/storetransfer/management', name: 'storetransfer-management', component: TransferManagementView },
  { path: '/storetransfer/handoff', name: 'storetransfer-handoff', component: TransferHandoffView },
]

export const menuItems: FeatureRouteModule['menuItems'] = [
  { id: 'storetransfer-management', label: '门店与调拨', to: '/storetransfer/management', order: 70 },
  { id: 'storetransfer-handoff', label: '调拨交接', to: '/storetransfer/handoff', order: 71 },
]
