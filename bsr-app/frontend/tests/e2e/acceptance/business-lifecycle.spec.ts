import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const password = 'acceptance-local-only' // Fictional, isolated TEST_DB fixture only.
interface Contract {
  contractId: string; status: string; amountDue: string
  items: Array<{ status: string; returnedAt: string | null }>
}

async function post<T>(request: APIRequestContext, path: string, data: unknown = {}): Promise<T> {
  const csrf = await request.get('/api/auth/csrf')
  expect(csrf.status()).toBe(200)
  const token = await csrf.json() as { headerName: string; token: string }
  const response = await request.post(path, { data, headers: { [token.headerName]: token.token } })
  expect(response.ok(), `${path}: ${await response.text()}`).toBe(true)
  return await response.json() as T
}

async function login(page: Page, loginName: string) {
  await page.goto('/identity/login')
  await page.getByLabel('账号').fill(loginName)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录工作台', exact: true }).click()
  await expect(page).toHaveURL(/\/workbenches\/(manager|employee)|\/repair\/contractor|\/entry\/miniprogram/)
}

async function persisted(request: APIRequestContext, base: number) {
  const response = await request.get(`/api/contracts?storeId=${base + 1}`)
  expect(response.status()).toBe(200)
  const contracts = await response.json() as Contract[]
  expect(contracts).toHaveLength(1)
  expect(contracts[0]?.status).toBe('ENDED')
  expect(contracts[0]?.amountDue).toBe('298.50')
  expect(contracts[0]?.items.every(item => item.status === 'RETURNED' && item.returnedAt)).toBe(true)
  const shifts = await request.get(`/api/shifts?storeId=${base + 1}`)
  expect(shifts.status()).toBe(200)
  expect(await shifts.json()).toEqual(expect.arrayContaining([expect.objectContaining({ status: 'CANCELLED' })]))
  const transfers = await request.get(`/api/transfers?storeId=${base + 1}`)
  expect(transfers.status()).toBe(200)
  expect(await transfers.json()).toEqual(expect.arrayContaining([expect.objectContaining({ status: 'COMPLETED' })]))
  const repair = await request.get(`/api/repair-tasks/${base + 12}`)
  expect(repair.status()).toBe(200)
  expect(await repair.json()).toEqual(expect.objectContaining({ status: 'PENDING_CHARGE' }))
  const audit = await request.get(`/api/audit-events?objectType=RENTAL_CONTRACT&objectId=${contracts[0]?.contractId}`)
  expect(audit.status()).toBe(200)
  expect(await audit.json()).toEqual(expect.arrayContaining([
    expect.objectContaining({ actionCode: 'EXTERNAL_RESULT_RECORDED' }),
    expect.objectContaining({ actionCode: 'RENTAL_ITEMS_ISSUED' }),
  ]))
}

if (process.env.E2E_PERSISTENCE_ONLY === '1') {
  test('AC-UC02 AC-BR13 fresh backend process retains ended rental, cancelled shift and completed transfer', async ({ page }, info) => {
    const base = info.project.name.startsWith('mobile') ? 43000 : 42000
    await login(page, `a10-employee-${base}`)
    await persisted(page.request, base)
  })
} else {
  test('AC-UC01 AC-UC04 AC-UC06 AC-UC07 AC-UC09 AC-UC11 AC-UC12 AC-UC13 real employee manager contractor browser lifecycle', async ({ page, browser, baseURL }, info) => {
    test.setTimeout(120_000)
    page.setDefaultTimeout(10_000)
    const base = info.project.name.startsWith('mobile') ? 43000 : 42000
    const store = String(base + 1)
    const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Oslo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
    const customer = await browser.newContext({ baseURL })
    const contractor = await browser.newContext({ baseURL, viewport: info.project.use.viewport })
    try {
      // Customer uses the same real HTTP contract as the native mini-program.
      // Native wx.request/device compatibility is separately pending EV-WX02.
      await post(customer.request, '/api/auth/login', { loginName: `a10-customer-${base}`, password })
      const requested = await post<{ requestId: string; requestNumber: string }>(customer.request, '/api/customer/requests', {
        serviceStoreId: store, rentalStartDate: date, rentalDays: 3, skillLevel: 'INTERMEDIATE',
        items: [{ equipmentCategory: 'SKI', quantity: 1, fittingNote: null }], confirmDuplicate: false,
      })
      await login(page, `a10-employee-${base}`)
      await page.goto('/customer-requests/employee-queue')
      await page.getByLabel('门店编号', { exact: true }).selectOption(store)
      await expect(page.getByRole('button', { name: '受理需求', exact: true })).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()?.width ?? 0)
      await page.getByRole('button', { name: '受理需求', exact: true }).click()
      await expect(page.getByRole('status')).toContainText('已受理')

      await page.goto('/rental/desk')
      await page.getByLabel('门店编号', { exact: true }).selectOption(store)
      await page.getByRole('button', { name: `选择需求 ${requested.requestNumber}`, exact: true }).click()
      await expect(page.getByLabel('已选需求详情')).toContainText('双板')
      await page.getByLabel('可租雪具', { exact: true }).selectOption(String(base + 6))
      await page.getByRole('button', { name: '添加雪具', exact: true }).click()
      const createdResponse = page.waitForResponse(r => r.url().endsWith('/api/contracts') && r.request().method() === 'POST')
      await page.getByRole('button', { name: '创建合同草稿' }).click()
      const created = await (await createdResponse).json() as Contract
      await expect(page.getByRole('heading', { level: 2, name: /草稿/ })).toBeVisible()
      await page.getByRole('button', { name: '提交顾客确认' }).click()
      await expect(page.getByRole('heading', { level: 2, name: /待确认/ })).toBeVisible()
      await post(customer.request, `/api/contracts/${created.contractId}/confirm`)
      await page.getByRole('button', { name: '刷新顾客确认状态' }).click()
      await expect(page.getByRole('heading', { level: 2, name: /待付款/ })).toBeVisible()
      await page.getByRole('combobox', { name: '费用', exact: true }).selectOption({ index: 1 })
      await page.getByLabel('外部交易编号').fill(`A10-BROWSER-${base}`)
      await expect(page.getByLabel('金额', { exact: true })).toHaveValue('298.50')
      await page.getByRole('combobox', { name: '结果', exact: true }).selectOption('SUCCEEDED')
      await page.getByLabel('第三方处理时间（本机时区）').fill(`${date}T08:00:00`)
      await page.getByRole('button', { name: '登记外部交易结果' }).click()
      await expect(page.getByRole('heading', { level: 2, name: /待发放/ })).toBeVisible()
      await page.getByLabel(`雪具 ${base + 6}`, { exact: false }).check()
      await page.getByRole('button', { name: '登记所选雪具发放' }).click()
      await expect(page.getByRole('heading', { level: 2, name: /有效/ })).toBeVisible()
      await page.goto('/rental/returns')
      await page.getByLabel('接收门店编号').selectOption(store)
      await page.locator(`[data-test="contract-${created.contractId}"]`).click()
      await page.getByLabel(`雪具 ${base + 6}`, { exact: false }).check()
      await page.getByRole('button', { name: '确认实物接收与检查' }).click()
      await expect(page.getByRole('heading', { level: 2, name: /已结束/ })).toBeVisible()
      await page.reload()
      await page.getByLabel('接收门店编号').selectOption(store)
      await page.getByRole('button', { name: '跨店或按合同编号查询' }).click()
      await page.getByLabel('合同编号', { exact: true }).fill(created.contractId)
      await page.getByRole('button', { name: '查询归还所需信息' }).click()
      await expect(page.getByRole('heading', { level: 2, name: /已结束/ })).toBeVisible()

      await page.goto('/scheduling/management')
      await page.getByLabel('门店编号', { exact: true }).selectOption(store)
      await page.getByLabel('员工', { exact: true }).selectOption(String(base + 3))
      await page.getByLabel('排班日期').fill('2027-01-15')
      const scheduledResponse = page.waitForResponse(r => r.url().endsWith('/api/shifts') && r.request().method() === 'POST')
      await page.getByTestId('save-shift').click()
      const scheduled = await (await scheduledResponse).json() as { shiftId: string }
      await expect(page.getByRole('status')).toContainText('已保存')
      await page.getByLabel(`取消原因：${scheduled.shiftId}`).fill('Acceptance cancellation retains original history')
      await page.getByTestId(`cancel-${scheduled.shiftId}`).click()
      await expect(page.getByRole('status')).toContainText('已取消')

      await page.goto('/storetransfer/management')
      await page.getByLabel('调出门店编号').selectOption(store)
      await page.getByLabel('调入门店编号').selectOption(String(base + 9))
      await page.getByLabel('可调拨雪具', { exact: true }).selectOption(String(base + 7))
      await page.getByRole('button', { name: '添加雪具', exact: true }).click()
      const transferResponse = page.waitForResponse(r => r.url().endsWith('/api/transfers') && r.request().method() === 'POST')
      await page.getByTestId('create-transfer').click()
      const transfer = await (await transferResponse).json() as { transferId: string; items: Array<{ itemId: string }> }
      await page.getByTestId(`approve-${transfer.transferId}`).click()
      await expect(page.getByRole('status')).toContainText('已批准')
      await page.goto('/storetransfer/handoff')
      await page.getByLabel('交接门店编号').selectOption(store)
      await page.getByTestId('load-handoffs').click()
      await page.getByTestId(`dispatch-${transfer.items[0]?.itemId}`).click()
      await expect(page.getByRole('status')).toContainText('已发出')
      await page.getByLabel('交接门店编号').selectOption(String(base + 9))
      await page.getByTestId('load-handoffs').click()
      await page.getByTestId(`receive-${transfer.items[0]?.itemId}`).click()
      await expect(page.getByRole('status')).toContainText('已接收')

      const repairPage = await contractor.newPage()
      repairPage.setDefaultTimeout(10_000)
      await login(repairPage, `a10-contractor-${base}`)
      await repairPage.goto('/repair/contractor')
      await repairPage.getByTestId('view-unstarted').click()
      await repairPage.getByTestId(`select-task-${base + 12}`).click()
      await repairPage.getByTestId('start-repair').click()
      await repairPage.getByLabel('维修日期').fill(date)
      await repairPage.getByLabel('工时（小时）').fill('0.25')
      await repairPage.getByLabel('处理结果').fill('Fictional edge repaired')
      await repairPage.getByTestId('add-labor').click()
      await expect(repairPage.getByLabel('工时（小时）')).toHaveValue('')
      await repairPage.getByRole('button', { name: '提交维修完成' }).click()
      await expect(repairPage.getByRole('heading', { level: 2, name: /待验收/ })).toBeVisible()
      await page.goto('/repair/employee')
      await page.getByLabel('门店编号', { exact: true }).selectOption(store)
      await page.getByTestId('load-repairs').click()
      await page.getByLabel('维修任务', { exact: true }).selectOption(String(base + 12))
      await page.getByTestId('accept-repair').click()
      await expect(page.getByRole('heading', { level: 2, name: /待确认费用/ })).toBeVisible()
      await expect(page.getByRole('alert')).toHaveCount(0)
      await persisted(page.request, base)

      const samples: number[] = []
      for (let batch = 0; batch < 10; batch++) {
        await Promise.all(Array.from({ length: 5 }, async () => {
          const start = performance.now()
          const response = await page.request.get(`/api/operations?storeId=${store}`)
          expect(response.status()).toBe(200)
          samples.push(performance.now() - start)
        }))
      }
      samples.sort((a, b) => a - b)
      console.log(`P01_MEASUREMENT project=${info.project.name} dataset=1-contract-1-repair-1-transfer-1-shift concurrency=5 samples=50 p50_ms=${samples[24]?.toFixed(2)} p95_ms=${samples[47]?.toFixed(2)} threshold=UNCONFIRMED verdict=NOT_ACCEPTED`)
    } finally {
      await Promise.allSettled([customer.close(), contractor.close()])
    }
  })
}
