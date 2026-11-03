import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ submitRequest: vi.fn() }));
vi.mock('../../pages/customerrequest/configuredApi', () => ({ customerRequestApiForConfiguredOrigin: () => api }));
interface FormPage {
  data: Record<string, unknown>;
  setData(patch: Record<string, unknown>): void;
  onSkillSelection(event: { detail: { value: string } }): void;
  onSkill(event: { detail: { value: string } }): void;
  submit(): Promise<void>;
}
let page: FormPage;
beforeEach(() => {
  vi.resetModules(); vi.resetAllMocks();
  vi.stubGlobal('Page', (options: FormPage) => {
    page = { ...options, data: structuredClone(options.data), setData(patch) { Object.assign(this.data, patch); } };
  });
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
