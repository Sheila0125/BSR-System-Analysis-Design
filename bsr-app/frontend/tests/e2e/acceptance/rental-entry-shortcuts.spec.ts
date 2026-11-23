import { expect, test } from '@playwright/test'

test.use({ launchOptions: { executablePath: process.env.BSR_E2E_BROWSER_EXECUTABLE } })

test('employee sees a simulated receipt and remaining amount without re-entering transaction data', async ({ page }) => {
  const charge = { chargeId: '21', type: 'INITIAL', amount: '100.00', status: 'PENDING' }
  const contract = { contractId: '7', contractNumber: 'RC-7', customerId: '2', serviceStoreId: '3', sourceRequestId: '9',
    status: 'PENDING_PAYMENT', rentalStartDate: '2026-11-23', rentalDays: 1, amountDue: '100.00', items: [], charges: [charge] }
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
        await route.fulfill({ json: [contract] })
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
  await page.getByLabel('门店合同').selectOption('7')
  await expect(page.getByLabel('费用', { exact: true })).toHaveValue('21')
  await expect(page.getByLabel('交易记录')).toContainText('SIM-DISPLAY-1')
  await expect(page.getByLabel('金额', { exact: true })).toHaveValue('69.99')
  await expect(page.getByLabel('外部交易编号', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('第三方处理时间（本机时区）')).toHaveAttribute('type', 'datetime-local')
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
})
