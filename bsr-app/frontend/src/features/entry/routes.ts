import type { FeatureRouteModule } from '../../app/routing/featureRegistry'
import WorkbenchView from '../../app/workbenches/WorkbenchView.vue'
import MiniProgramEntryView from './MiniProgramEntryView.vue'

export const routes: FeatureRouteModule['routes'] = [
  { path: '/workbenches/employee', name: 'employee-workbench', component: WorkbenchView, props: { mode: 'employee' } },
  { path: '/workbenches/manager', name: 'manager-workbench', component: WorkbenchView, props: { mode: 'manager' } },
  { path: '/entry/miniprogram', name: 'miniprogram-entry', component: MiniProgramEntryView },
]

export const menuItems: FeatureRouteModule['menuItems'] = [
  { id: 'employee-workbench', label: '员工工作台', to: '/workbenches/employee', order: 21 },
  { id: 'manager-workbench', label: '雪场工作台', to: '/workbenches/manager', order: 22 },
  { id: 'miniprogram-entry', label: '顾客微信小程序', to: '/entry/miniprogram', order: 23 },
]
