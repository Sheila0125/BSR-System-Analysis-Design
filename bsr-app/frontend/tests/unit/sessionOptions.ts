import { ref } from 'vue'
import { sessionActorKey, type StoreRole } from '../../src/features/identity/sessionStores'
import type { CurrentActor } from '../../src/features/identity/authApi'

export function sessionOptions(ids: string[], roles: StoreRole[] = ['EMPLOYEE', 'MANAGER']) {
  return { global: { provide: { [sessionActorKey as symbol]: ref<CurrentActor>({
    accountId: '1', loginName: 'course-test', identityType: 'EMPLOYEE', identityId: '2',
    grants: roles.flatMap(role => ids.map(storeId => ({ role, storeId }))),
  }) } } }
}
