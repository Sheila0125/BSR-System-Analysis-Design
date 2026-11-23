import { expect, test } from '@playwright/test'
import { createServer } from 'vite'

test('AC-WX05 public entry explains missing code and displays only a configured fixed image', async ({ browser }) => {
  const previous = process.env.VITE_MINIPROGRAM_QR_IMAGE

  try {
    for (const configuredImage of ['', '/assets/official-mini-program-code.png']) {
      process.env.VITE_MINIPROGRAM_QR_IMAGE = configuredImage
      const server = await createServer({ server: { host: '127.0.0.1', port: 0 } })
      const page = await browser.newPage()

      try {
        await server.listen()
        const origin = server.resolvedUrls?.local[0]
        if (!origin) throw new Error('Vite did not expose a local URL')

        await page.goto(new URL('/entry/miniprogram', origin).toString())
        await expect(page.getByRole('heading', { name: '顾客微信小程序' })).toBeVisible()

        const code = page.getByRole('img', { name: '官方顾客小程序码' })
        if (configuredImage) {
          await expect(code).toHaveAttribute('src', configuredImage)
          await expect(page.getByText('请用微信扫描小程序码，进入顾客端。')).toBeVisible()
        } else {
          await expect(code).toHaveCount(0)
          await expect(page.getByText('顾客小程序码暂未配置')).toBeVisible()
        }

        await expect(page.locator('canvas')).toHaveCount(0)
      } finally {
        await page.close()
        await server.close()
      }
    }
  } finally {
    if (previous === undefined) delete process.env.VITE_MINIPROGRAM_QR_IMAGE
    else process.env.VITE_MINIPROGRAM_QR_IMAGE = previous
  }
})
