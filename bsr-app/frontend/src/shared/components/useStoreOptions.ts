import { ref, shallowRef, watch, type Ref } from 'vue'
import { errorText } from '../presentation/zh'

export function useStoreOptions<T>(storeId: Readonly<Ref<string>>, load: (storeId: string) => Promise<T[]>) {
  const options = shallowRef<T[]>([])
  const loading = ref(false)
  const error = ref('')
  const revision = ref(0)

  watch([storeId, revision], async ([id], previous, onCleanup) => {
    if (id !== previous?.[0]) options.value = []
    error.value = ''
    loading.value = Boolean(id)
    if (!id) return

    let current = true
    onCleanup(() => { current = false })
    try {
      const rows = await load(id)
      if (current) options.value = rows
    } catch (failure) {
      if (current) error.value = errorText(failure)
    } finally {
      if (current) loading.value = false
    }
  }, { immediate: true })

  return { options, loading, error, refresh: () => { revision.value++ } }
}
