import type { FeatureRouteModule } from '../../app/routing/featureRegistry'
import EquipmentAvailabilityView from './EquipmentAvailabilityView.vue'
import EquipmentCatalogView from './EquipmentCatalogView.vue'

export const routes: FeatureRouteModule['routes'] = [
  { path: '/equipment/catalog', name: 'equipment-catalog', component: EquipmentCatalogView },
  {
    path: '/equipment/available',
    name: 'equipment-available',
    component: EquipmentAvailabilityView,
  },
]

export const menuItems: FeatureRouteModule['menuItems'] = [
  { id: 'equipment-catalog', label: '雪具档案', to: '/equipment/catalog', order: 31 },
  {
    id: 'equipment-available',
    label: '可租雪具',
    to: '/equipment/available',
    order: 32,
  },
]
