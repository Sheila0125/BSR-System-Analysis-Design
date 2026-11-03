import type { FeatureRouteModule } from '../../app/routing/featureRegistry'
import ShiftManagementView from './ShiftManagementView.vue'
import MyShiftsView from './MyShiftsView.vue'

export const routes: FeatureRouteModule['routes'] = [
  { path: '/scheduling/management', name: 'scheduling-management', component: ShiftManagementView },
  { path: '/scheduling/my-shifts', name: 'scheduling-my-shifts', component: MyShiftsView },
]

export const menuItems: FeatureRouteModule['menuItems'] = [
  { id: 'scheduling-management', label: '员工排班', to: '/scheduling/management', order: 80 },
  { id: 'scheduling-my-shifts', label: '我的班次', to: '/scheduling/my-shifts', order: 81 },
]
