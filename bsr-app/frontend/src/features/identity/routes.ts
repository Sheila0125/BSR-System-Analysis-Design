import type { FeatureRouteModule } from '../../app/routing/featureRegistry'
import LoginView from './LoginView.vue'
import RoleGrantView from './RoleGrantView.vue'
import SessionView from './SessionView.vue'

export const routes: FeatureRouteModule['routes'] = [
  { path: '/identity/login', name: 'identity-login', component: LoginView },
  { path: '/identity/session', name: 'identity-session', component: SessionView },
  { path: '/identity/role-grants', name: 'identity-role-grants', component: RoleGrantView },
]

export const menuItems: FeatureRouteModule['menuItems'] = [
  { id: 'identity-session', label: '账号信息', to: '/identity/session', order: 10 },
  { id: 'identity-role-grants', label: '账号与权限', to: '/identity/role-grants', order: 20 },
]
