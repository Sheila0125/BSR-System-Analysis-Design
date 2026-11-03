import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../HomeView.vue'
import { featureRoutes } from './featureRegistry'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    ...featureRoutes,
  ],
})
