import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ submitRequest: vi.fn(), loadProfile: vi.fn() }));
vi.mock('../../pages/customerrequest/configuredApi', () => ({ customerRequestApiForConfiguredOrigin: () => api }));
interface FormPage {
  data: Record<string, unknown>;
  setData(patch: Record<string, unknown>): void;
  onSkillSelection(event: { detail: { value: string } }): void;
  onSkill(event: { detail: { value: string } }): void;
  onLoad(): Promise<void>;
  onStore(event: { detail: { value: string } }): void;
  onCategory(event: { detail: { value: string }; currentTarget: { dataset: { index: number } } }): void;
  submit(): Promise<void>;
}
let page: FormPage;
beforeEach(() => {
  vi.resetModules(); vi.resetAllMocks();
  api.loadProfile.mockResolvedValue({ heightCm: '173', weightKg: '68', heightEstimated: false, weightEstimated: true });
  vi.stubGlobal('Page', (options: FormPage) => {
    page = { ...options, data: structuredClone(options.data), setData(patch) { Object.assign(this.data, patch); } };
  });
  vi.stubGlobal('getApp', () => ({ globalData: { rentalStores: [{ id: '3', name: '演示门店' }] } }));
  vi.stubGlobal('wx', { redirectTo: vi.fn() });
});
afterEach(() => vi.unstubAllGlobals());
it('submits the original business code after selecting a Chinese skill label', async () => {
  api.submitRequest.mockResolvedValue({ requestId: '7' });
  await import(`../../pages/customerrequest/${'create'}.ts`);
  page.setData({ serviceStoreId: '3', rentalStartDate: '2026-11-01',
    items: [{ equipmentCategory: 'SKI', quantity: '1', fittingNote: '' }] });
  page.onSkillSelection({ detail: { value: '2' } });
  await page.submit();
  expect(api.submitRequest).toHaveBeenCalledWith(expect.objectContaining({ skillLevel: 'ADVANCED' }));
  expect(page.data['skillLevel']).toBe('ADVANCED');
});
it('retains manual skill entry for the existing free-text business field', async () => {
  api.submitRequest.mockResolvedValue({ requestId: '8' });
  await import(`../../pages/customerrequest/${'create'}.ts`);
  page.setData({ serviceStoreId: '3', rentalStartDate: '2026-11-01',
    items: [{ equipmentCategory: 'SKI', quantity: '1', fittingNote: '' }] });
  page.onSkillSelection({ detail: { value: '3' } });
  expect(page.data['skillIndex']).toBe(3);
  page.onSkill({ detail: { value: 'CUSTOM_LEVEL' } });
  await page.submit();
  expect(api.submitRequest).toHaveBeenCalledWith(expect.objectContaining({ skillLevel: 'CUSTOM_LEVEL' }));
});

it('submits the selected store and equipment codes and refuses blank equipment', async () => {
  api.submitRequest.mockResolvedValue({ requestId: '9' });
  await import(`../../pages/customerrequest/${'create'}.ts`);
  await page.onLoad();
  page.onStore({ detail: { value: '0' } });
  page.setData({ rentalStartDate: '2026-11-17' });
  page.onSkillSelection({ detail: { value: '0' } });
  expect(page.data['skiLength']).toBe('158–168 cm');
  expect(page.data['snowboardLength']).toBe('150–153 cm');
  await page.submit();
  expect(api.submitRequest).not.toHaveBeenCalled();
  page.onCategory({ detail: { value: '2' }, currentTarget: { dataset: { index: 0 } } });
  await page.submit();
  expect(api.submitRequest).toHaveBeenCalledWith(expect.objectContaining({
    serviceStoreId: '3', items: [{ equipmentCategory: 'BOOTS', quantity: 1, fittingNote: null }],
  }));
});
