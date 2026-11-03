import type { FeatureRouteModule } from '../../app/routing/featureRegistry'
import EmployeeRepairView from './EmployeeRepairView.vue'
import RepairManagementView from './RepairManagementView.vue'
import ContractorRepairView from './ContractorRepairView.vue'

export const routes: FeatureRouteModule['routes'] = [
  { path: '/repair/employee', name: 'repair-employee', component: EmployeeRepairView },
  { path: '/repair/management', name: 'repair-management', component: RepairManagementView },
  { path: '/repair/contractor', name: 'repair-contractor', component: ContractorRepairView },
]

export const menuItems: FeatureRouteModule['menuItems'] = [
  { id: 'repair-employee', label: '维修办理', to: '/repair/employee', order: 50 },
  { id: 'repair-management', label: '维修管理', to: '/repair/management', order: 51 },
  { id: 'repair-contractor', label: '我的维修任务', to: '/repair/contractor', order: 52 },
]
