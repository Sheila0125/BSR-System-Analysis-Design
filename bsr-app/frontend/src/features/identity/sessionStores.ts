import type { InjectionKey, Ref } from 'vue'
import type { CurrentActor } from './authApi'
import { requestJson } from '../../shared/http/apiClient'

export type StoreRole = 'EMPLOYEE' | 'MANAGER'
interface StoreChoice { storeId: string; storeName: string; storeNumber: string }

export const sessionActorKey: InjectionKey<Ref<CurrentActor | null>> = Symbol('sessionActor')
export const storeChoicesKey: InjectionKey<Ref<StoreChoice[]>> = Symbol('storeChoices')
export const storeChoicesStatusKey: InjectionKey<{ status: Ref<'loading' | 'ready' | 'error'>; retry: () => void }> = Symbol('storeChoicesStatus')
export const loadStoreChoices = () => requestJson<StoreChoice[]>('/api/stores/choices')
