import { expect, test } from '@playwright/test'

test.use({ launchOptions: { executablePath: process.env.BSR_E2E_BROWSER_EXECUTABLE } })

test('staff can select and remove several long equipment entries at phone width', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  let storeReads = 0
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/me') {
      await route.fulfill({ json: { accountId: '1', loginName: 'layout-test', identityType: 'EMPLOYEE', identityId: '1', grants: [{ role: 'EMPLOYEE', storeId: '1' }] } })
    } else if (path === '/api/equipment/available') {
      await route.fulfill({ json: Array.from({ length: 24 }, (_, index) => ({ equipmentId: String(index + 1), inventoryNumber: `TEST-20261025-SNOWBOARD-${String(index + 1).padStart(2, '0')}`, equipmentName: `Burton Process 2026-10-25`, equipmentCategory: 'SNOWBOARD', skillLevel: 'BEGINNER', homeStoreId: '1', currentStoreId: '1', status: 'AVAILABLE', currentDailyRate: '350.00' })) })
    } else if (path === '/api/employee/rental-requests') {
      await route.fulfill({ json: [
        { requestId: '7', requestNumber: 'RQ-READY', customerId: '51', serviceStoreId: '1', status: 'ACCEPTED', rentalStartDate: '2026-12-20', rentalDays: 2, skillLevel: 'BEGINNER', submittedAt: '2026-11-23T10:00:00', acceptedByEmployeeId: '1', items: [{ equipmentCategory: 'SNOWBOARD', quantity: 2, fittingNote: '板长 150 cm' }], supplements: [] },
        { requestId: '8', requestNumber: 'RQ-WAIT', status: 'PENDING' },
      ] })
    } else if (path === '/api/stores/choices') {
      if (++storeReads === 1) {
        await route.fulfill({ status: 503, json: { detail: 'Temporary store-name lookup failure' } })
      } else {
        await route.fulfill({ json: [{ storeId: '1', storeName: '雪场门店', storeNumber: 'NORTH' }] })
      }
    } else {
      await route.fulfill({ status: 500, json: { detail: 'Unexpected endpoint in isolated layout test' } })
    }
  })
  await page.goto('/rental/desk')
  await page.getByLabel('门店编号', { exact: true }).selectOption('1')
  await expect(page.getByLabel('门店编号')).toContainText('雪场门店')
  expect(storeReads).toBe(2)
  const requests = page.getByLabel('已受理需求')
  await page.getByRole('button', { name: '选择需求 RQ-READY' }).click()
  await expect(requests).not.toContainText('RQ-WAIT')
  await expect(page.getByLabel('已选需求详情')).toContainText('板长 150 cm')
  const equipment = page.getByLabel('可租雪具', { exact: true })
  await expect(equipment.locator('option:not([disabled])')).toHaveCount(24)
  for (const id of ['12', '15']) {
    await equipment.selectOption(id)
    await page.getByRole('button', { name: '添加雪具', exact: true }).click()
  }
  const selected = page.getByRole('list', { name: '已选雪具' })
  await expect(selected.getByRole('listitem')).toHaveCount(2)
  await expect(page.getByLabel('配货进度')).toContainText('单板 2 / 2')
  await expect(equipment.locator('option[value="12"]')).toHaveCount(0)
  await page.getByRole('button', { name: '移除 TEST-20261025-SNOWBOARD-12' }).click()
  await expect(selected.getByRole('listitem')).toHaveCount(1)
  await expect(equipment.locator('option[value="12"]')).toHaveCount(1)
  await expect(page.getByRole('button', { name: '创建合同草稿' })).toBeDisabled()
  await equipment.selectOption('12')
  await page.getByRole('button', { name: '添加雪具', exact: true }).click()
  await expect(page.getByRole('button', { name: '创建合同草稿' })).toBeEnabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
})
