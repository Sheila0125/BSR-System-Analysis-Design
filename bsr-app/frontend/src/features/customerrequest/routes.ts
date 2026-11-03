import type { FeatureRouteModule } from '../../app/routing/featureRegistry'
import EmployeeRequestQueueView from './EmployeeRequestQueueView.vue'
import CustomerVerificationView from './CustomerVerificationView.vue'

export const routes: FeatureRouteModule['routes'] = [
  {
    path: '/customer-requests/employee-queue',
    name: 'customer-request-employee-queue',
    component: EmployeeRequestQueueView,
  },
  {
    path: '/customer-requests/verify-customer',
    name: 'customer-request-verify-customer',
    component: CustomerVerificationView,
  },
]

export const menuItems: FeatureRouteModule['menuItems'] = [
  {
    id: 'customer-request-employee-queue',
    label: '租赁需求队列',
    to: '/customer-requests/employee-queue',
    order: 30,
  },
  {
    id: 'customer-request-verify-customer',
    label: '顾客核验',
    to: '/customer-requests/verify-customer',
    order: 31,
  },
]
