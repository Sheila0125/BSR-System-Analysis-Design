import type { FeatureRouteModule } from '../../app/routing/featureRegistry'
import RentalDeskView from './RentalDeskView.vue'
import RentalReturnView from './RentalReturnView.vue'
import RentalAdjustmentView from './RentalAdjustmentView.vue'
export const routes: FeatureRouteModule['routes'] = [
  { path: '/rental/desk', name: 'rental-desk', component: RentalDeskView },
  { path: '/rental/returns', name: 'rental-returns', component: RentalReturnView },
  { path: '/rental/adjustments', name: 'rental-adjustments', component: RentalAdjustmentView },
]
export const menuItems: FeatureRouteModule['menuItems'] = [
  { id: 'rental-desk', label: '租赁办理', to: '/rental/desk', order: 40 },
  { id: 'rental-returns', label: '归还检查', to: '/rental/returns', order: 41 },
  { id: 'rental-adjustments', label: '租赁调整', to: '/rental/adjustments', order: 42 },
]
