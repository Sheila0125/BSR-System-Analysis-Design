export function conciseEquipmentName(value: string): string {
  return value
    .replace(/\b(?:19|20)\d{2}[-/.]?\d{2}[-/.]?\d{2}\b/g, '')
    .replace(/\s*[-–—·|]+\s*$/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim() || '未命名雪具'
}

export function conciseInventoryNumber(value: string): string {
  return value.replace(/^(?:TEST|BSR|INV)[-_](?:19|20)\d{6}[-_]/i, '') || value
}
