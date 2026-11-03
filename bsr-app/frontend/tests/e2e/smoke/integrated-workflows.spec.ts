import { expect, test, type Page } from '@playwright/test'

async function storeRead(page: Page, route: string, heading: string, field: string, button: string, api: string) {
  await page.goto(route)
  await expect(page.getByRole('heading', { name: heading })).toBeVisible()
  await page.getByLabel(field, { exact: true }).fill('39901')
  const response = page.waitForResponse(result => result.url().includes(api))
  await page.getByRole('button', { name: button }).click()
  expect((await response).status()).toBe(200)
  await expect(page.getByRole('alert')).toHaveCount(0)
}

test('AC-UC04/06/07/11/12/16 real browser session reaches five business workflows', async ({ page }) => {
  await page.goto('/identity/login')
  await page.getByLabel('账号').fill('browser-t09')
  await page.getByLabel('密码').fill('smoke-local-only')
  await page.getByRole('button', { name: '登录工作台' }).click()
  await expect(page).toHaveURL(/\/workbenches\/(manager|employee)|\/repair\/contractor|\/entry\/miniprogram/)

  await storeRead(page, '/rental/desk', '租赁办理', '门店编号',
    '查询合同与变更需求', '/api/contracts?storeId=39901')
  await page.goto('/rental/returns')
  await expect(page.getByRole('heading', { name: '归还接收与检查' })).toBeVisible()
  await page.getByLabel('接收门店编号').fill('39901')
  await page.getByLabel('合同编号').fill('39999')
  const missingReturn = page.waitForResponse(result => result.url().includes('/return-lookup?returnStoreId=39901'))
  await page.getByRole('button', { name: '查询归还所需信息' }).click()
  expect((await missingReturn).status()).toBe(404)
  await expect(page.getByRole('alert')).toBeVisible()

  await storeRead(page, '/repair/employee', '维修办理', '门店编号',
    '查询维修任务', '/api/repair-tasks?storeId=39901')
  await storeRead(page, '/storetransfer/management', '门店与调拨', '调出门店编号',
    '查询调拨', '/api/transfers?storeId=39901')
  await storeRead(page, '/scheduling/management', '员工排班', '门店编号',
    '查询门店班次', '/api/shifts?storeId=39901')

  await page.goto('/workbenches/manager')
  await expect(page.getByRole('heading', { name: '门店运营概览' })).toBeVisible()
  await page.getByLabel('授权门店').selectOption('39901')
  const operations = page.waitForResponse(result => result.url().includes('/api/operations?storeId=39901'))
  await page.getByRole('button', { name: '刷新状态' }).click()
  expect((await operations).status()).toBe(200)
})
