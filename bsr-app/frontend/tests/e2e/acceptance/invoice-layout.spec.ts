import { expect, test } from '@playwright/test'
import { resolve } from 'node:path'

test.use({ launchOptions: { executablePath: process.env.BSR_E2E_BROWSER_EXECUTABLE } })
test('staff can open the invoice with readable balances and long identifiers at phone and desktop widths',async({page},info)=>{
  const width=info.project.name.includes('mobile') ? 390 : 1360
  await page.setViewportSize({width,height:950})
  const item={itemId:'8',equipmentId:'9007199254740993',dailyRateSnapshot:'99.50',status:'PENDING_ISSUE',active:true,checkedOutAt:null,returnedAt:null,returnStoreId:null}
  const contract={contractId:'7',contractNumber:'RC-INVOICE-LAYOUT',customerId:'2',serviceStoreId:'1',sourceRequestId:'3',contractDate:'2026-10-27',rentalStartDate:'2026-12-20',rentalDays:3,amountDue:'418.50',status:'PENDING_PAYMENT',items:[item],charges:[{chargeId:'11',type:'INITIAL',amount:'418.50',status:'PENDING',createdAt:'2026-10-27T08:00:00Z'}]}
  const invoice={invoiceNumber:'INV-RC-INVOICE-LAYOUT',documentType:'COURSE_DEMO_INVOICE',currency:'NOK',contractId:'7',contractNumber:contract.contractNumber,customerId:'2',customerName:'课堂演示顾客',customerNumber:'C-TEST-2',serviceStoreId:'1',storeName:'BSR 雪场门店',contractDate:contract.contractDate,rentalStartDate:contract.rentalStartDate,rentalDays:3,contractStatus:contract.status,amountDue:'418.50',totalPaid:'100.00',totalRefunded:'0.00',outstandingPayment:'318.50',outstandingRefund:'0.00',items:[item],charges:[{...contract.charges[0],outstandingAmount:'318.50',canSimulatePayment:true,transactions:[{transactionId:'21',chargeId:'11',type:'PAYMENT',externalId:'SIM-12345678-1234-4234-9234-123456789012',amount:'100.00',status:'SUCCEEDED',processedAt:'2026-10-27T08:00:00Z',recordedAt:'2026-10-27T08:00:00Z',recordedByEmployeeId:null,source:'SIMULATED',simulatedByAccountId:'2'}]}]}
  const errors:string[]=[]
  page.on('pageerror',error=>errors.push(error.message))
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname
    if(path==='/api/auth/me') await route.fulfill({json:{accountId:'1',loginName:'invoice-layout',identityType:'EMPLOYEE',identityId:'1',grants:[{role:'EMPLOYEE',storeId:'1'}]}})
    else if(path==='/api/contracts') await route.fulfill({json:[contract]})
    else if(path==='/api/contracts/7/invoice') await route.fulfill({json:invoice})
    else if(path==='/api/rental-change-requests' || path==='/api/equipment/available') await route.fulfill({json:[]})
    else await route.fulfill({status:500,json:{detail:'Unexpected endpoint in isolated invoice layout test'}})
  })
  await page.goto('/rental/desk')
  await page.getByLabel('门店编号',{exact:true}).fill('1')
  await page.getByRole('button',{name:'查询合同与变更需求',exact:true}).click()
  await page.getByRole('button',{name:/RC-INVOICE-LAYOUT/}).click()
  await page.getByRole('button',{name:'查看发票与付款凭证',exact:true}).click()
  const invoicePanel=page.getByRole('region',{name:'电子发票',exact:true})
  await expect(invoicePanel.getByTestId('invoice-outstanding')).toContainText('318.50')
  await expect(invoicePanel).toContainText('顾客模拟付款')
  await expect(invoicePanel).toContainText('9007199254740993')
  const overlaps=await invoicePanel.locator('.invoice-balances > div').evaluateAll(cards=>{
    const boxes=cards.map(card=>card.getBoundingClientRect())
    return boxes.flatMap((a,i)=>boxes.slice(i+1).filter(b=>Math.min(a.right,b.right)-Math.max(a.left,b.left)>1 && Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>1)).length
  })
  expect(overlaps).toBe(0)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)).toBe(false)
  expect(errors).toEqual([])
  await invoicePanel.screenshot({path:resolve(process.env.BSR_LAYOUT_OUTPUT ?? 'E:/系统分析设计/bsr-internal/invoice-payment-20261027',`invoice-${width}.png`)})
})
