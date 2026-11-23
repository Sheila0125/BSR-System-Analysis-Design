import { expect, test } from '@playwright/test'

test.use({ launchOptions: { executablePath: process.env.BSR_E2E_BROWSER_EXECUTABLE } })

test('employee sees a simulated receipt and remaining amount without re-entering transaction data', async ({ page }) => {
  const charge = { chargeId: '21', type: 'INITIAL', amount: '100.00', status: 'PENDING' }
  const contract = { contractId: '7', contractNumber: 'RC-7', customerId: '2', serviceStoreId: '3', sourceRequestId: '9',
    status: 'PENDING_PAYMENT', rentalStartDate: '2026-11-23', rentalDays: 1, amountDue: '100.00', charges: [charge],
    items: [{ equipmentId: '41', active: true, itemId: '81', status: 'PENDING_ISSUE', dailyRateSnapshot: '100.00', checkedOutAt: null, returnedAt: null }] }
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    switch (path) {
      case '/api/auth/me':
        await route.fulfill({ json: { accountId: '1', loginName: 'staff-demo', identityType: 'EMPLOYEE', identityId: '1',
          grants: [{ role: 'EMPLOYEE', storeId: '3' }] } })
        break
      case '/api/stores/choices':
        await route.fulfill({ json: [{ storeId: '3', storeName: '演示门店', storeNumber: 'DEMO' }] })
        break
      case '/api/contracts':
        await route.fulfill({ json: [contract, { ...contract, contractId: '8', contractNumber: 'RC-8', status: 'ENDED', items: [{ ...contract.items[0], equipmentId: '42' }] }] })
        break
      case '/api/equipment/catalog':
        await route.fulfill({ json: [
          { equipmentId: '41', equipmentCategory: 'SKI', equipmentName: '测试双板', inventoryNumber: 'SK-41' },
          { equipmentId: '42', equipmentCategory: 'BOOTS', equipmentName: '测试雪鞋', inventoryNumber: 'BT-42' },
        ] })
        break
      case '/api/rental-charges/21/transactions':
        await route.fulfill({ json: [{ transactionId: '15', chargeId: '21', type: 'PAYMENT', externalId: 'SIM-DISPLAY-1',
          amount: '30.01', status: 'SUCCEEDED', processedAt: '2026-11-23T08:00:00Z', source: 'SIMULATED' }] })
        break
      default:
        await route.fulfill({ json: [] })
    }
  })

  await page.goto('/rental/desk')
  await page.getByLabel('门店编号', { exact: true }).selectOption('3')
  await page.getByRole('button', { name: '查询合同与变更需求' }).click()
  const card = page.locator('[data-test="contract-7"]')
  await expect(card).toHaveAttribute('aria-pressed', 'true')
  await expect(card).toContainText('双板')
  await expect(card).not.toContainText('RC-7')
  await page.getByLabel('搜索合同').fill('雪鞋')
  await expect(card).toHaveCount(0)
  await expect(page.locator('[data-test="contract-8"]')).toContainText('雪鞋')
  await expect(page.getByLabel('费用', { exact: true })).toHaveValue('21')
  await expect(page.getByLabel('交易记录')).toContainText('SIM-DISPLAY-1')
  await expect(page.getByLabel('金额', { exact: true })).toHaveValue('69.99')
  await expect(page.getByLabel('外部交易编号', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('第三方处理时间（本机时区）')).toHaveAttribute('type', 'datetime-local')
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
})
