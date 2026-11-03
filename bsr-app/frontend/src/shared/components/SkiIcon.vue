<script setup lang="ts">
withDefaults(defineProps<{ name?: string }>(), { name: 'mountain' })
const paths: Record<string, string> = {
  mountain: 'M2 21 10 3l5 10 3-6 6 14H2m5-11 3 3 3-3m2 3 3 3 3-2',
  grid: 'M3 3h7v7H3z M15 3h7v7h-7z M3 15h7v7H3z M15 15h7v7h-7z',
  file: 'M6 2h12v21H6z M9 7h6 M9 11h6 M9 15h6',
  users: 'M8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8m-6 10v-3a6 6 0 0 1 12 0v3m3-18a4 4 0 0 1 0 8m1 3a6 6 0 0 1 4 5v2',
  ski: 'M9 2 3 20q-1 4 2 2L12 2 M18 2l-6 18q-1 4 2 2L21 2 M3 10h18 M1 15h18',
  wrench: 'M21 3a7 7 0 0 1-9 9L4 21a2 2 0 0 1-3-3l9-8a7 7 0 0 1 9-9l-4 4 3 3 3-5',
  transfer: 'M3 7h18m-5-5 5 5-5 5 M21 18H3m5-5-5 5 5 5',
  calendar: 'M3 5h19v18H3z M3 10h19 M8 2v6 M17 2v6 M7 15h3 M14 15h3 M7 19h3',
  shield: 'M12 2 3 6v7c0 5 9 10 9 10s9-5 9-10V6l-9-4 M8 12l3 3 6-7',
  clock: 'M23 12a11 11 0 1 1-22 0 11 11 0 0 1 22 0 M12 5v7l5 3',
  phone: 'M7 2h10v21H7z M10 5h4 M11 20h2',
  arrow: 'M3 12h18 M16 7l5 5-5 5',
  refresh: 'M21 6v6h-6 M21 12a9 9 0 1 0-3 7',
  logout: 'M9 3H3v19h6 M9 12h13 M17 7l5 5-5 5',
  snow: 'M12 1v22 M2 6l20 12 M2 18 22 6 M8 3l4 4 4-4 M8 21l4-4 4 4',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 23v-4a8 8 0 0 1 16 0v4',
}
</script>

<template>
  <svg
    class="icon"
    viewBox="0 0 26 26"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] ?? paths.mountain" />
  </svg>
</template>
