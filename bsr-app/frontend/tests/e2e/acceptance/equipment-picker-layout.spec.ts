import { expect, test } from '@playwright/test'

test.use({ launchOptions: { executablePath: process.env.BSR_E2E_BROWSER_EXECUTABLE } })

test('equipment choices occupy separate rows without overlap at phone width', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/me') {
      await route.fulfill({ json: { accountId: '1', loginName: 'layout-test', identityType: 'EMPLOYEE', identityId: '1', grants: [{ role: 'EMPLOYEE', storeId: '1' }] } })
    } else if (path === '/api/equipment/available') {
      await route.fulfill({ json: Array.from({ length: 24 }, (_, index) => ({ equipmentId: String(index + 1), inventoryNumber: `TEST-20261025-SNOWBOARD-${String(index + 1).padStart(2, '0')}`, equipmentCategory: 'SNOWBOARD', skillLevel: 'BEGINNER', currentDailyRate: '350.00' })) })
    } else {
      await route.fulfill({ status: 500, json: { detail: 'Unexpected endpoint in isolated layout test' } })
    }
  })
  await page.goto('/rental/desk')
  await page.getByLabel('门店编号', { exact: true }).fill('1')
  await page.getByRole('button', { name: '刷新可租雪具', exact: true }).click()
  const choices = page.locator('fieldset label')
  await expect(choices).toHaveCount(24)
  const overlaps = await choices.evaluateAll(labels => {
    const boxes = labels.map(label => label.getBoundingClientRect())
    return boxes.slice(1).filter((box, index) => box.top < boxes[index]!.bottom - 1).length
  })
  expect(overlaps).toBe(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
  await choices.nth(11).locator('input').check()
  await expect(page.locator('fieldset input:checked')).toHaveCount(1)
})
