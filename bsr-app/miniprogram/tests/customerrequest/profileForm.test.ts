import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ loadProfile: vi.fn(), updateContactDetails: vi.fn(), updateMeasurements: vi.fn() }));
vi.mock('../../pages/customerrequest/configuredApi', () => ({ customerRequestApiForConfiguredOrigin: () => api }));
interface ProfilePage {
  data: Record<string, unknown>;
  setData(patch: Record<string, unknown>): void;
  onLoad(): Promise<void>;
  saveContact(): Promise<void>;
  save(): Promise<void>;
  onBootSize(event: { detail: { value: string } }): void;
}
let page: ProfilePage;
const profile = {
  customerId: '3', customerNumber: 'C-3', fullName: 'Course customer',
  email: 'customer@test.invalid', phone: '555', address: 'Course road', birthYear: 1992,
  heightCm: '170.25', weightKg: null, bootSize: null,
  heightEstimated: true, weightEstimated: false, bootSizeEstimated: false,
};
beforeEach(() => {
  vi.resetModules(); vi.resetAllMocks();
  vi.stubGlobal('Page', (options: ProfilePage) => {
    page = { ...options, data: structuredClone(options.data), setData(patch) { Object.assign(this.data, patch); } };
  });
  api.loadProfile.mockResolvedValue(profile);
});
afterEach(() => vi.unstubAllGlobals());
async function load() { await import(`../../pages/customerrequest/${'profile'}.ts`); await page.onLoad(); }

it('loads full contact details and saves them independently from edited measurements', async () => {
  await load();
  expect(page.data['email']).toBe('customer@test.invalid');
  expect(page.data['birthYear']).toBe('1992');
  page.setData({ fullName: 'Changed name', email: 'new@test.invalid', phone: '666', address: '', birthYear: '', heightCm: '180.50' });
  api.updateContactDetails.mockResolvedValue({ ...profile, fullName: 'Changed name', email: 'new@test.invalid', phone: '666', address: null, birthYear: null });
  await page.saveContact();
  expect(api.updateContactDetails).toHaveBeenCalledWith({ fullName: 'Changed name', email: 'new@test.invalid', phone: '666', address: '', birthYear: '' });
  expect(api.updateMeasurements).not.toHaveBeenCalled();
  expect(page.data['heightCm']).toBe('180.50');
});

it('retains failed contact input, blocks duplicate submission and disables saves after load failure', async () => {
  await load();
  page.setData({ email: 'invalid', address: 'Unsaved road' });
  api.updateContactDetails.mockRejectedValue({ detail: 'Invalid email' });
  await page.saveContact();
  expect(page.data['email']).toBe('invalid');
  expect(page.data['address']).toBe('Unsaved road');
  expect(page.data['errorMessage']).toBe('Invalid email');
  page.setData({ saving: true });
  await page.saveContact();
  expect(api.updateContactDetails).toHaveBeenCalledTimes(1);
  page.setData({ saving: false });
  api.loadProfile.mockRejectedValue({ detail: 'Network failed' });
  await page.onLoad();
  await page.saveContact();
  await page.save();
  expect(api.updateContactDetails).toHaveBeenCalledTimes(1);
  expect(api.updateMeasurements).not.toHaveBeenCalled();
});

it('keeps a recorded nonstandard size in the picker and can select a half size', async () => {
  api.loadProfile.mockResolvedValue({ ...profile, bootSize: '42.7' });
  api.updateMeasurements.mockResolvedValue({ ...profile, bootSize: '42.5' });
  await load();
  const sizes = page.data['bootSizes'] as string[];
  expect(sizes).toContain('42.7');
  page.onBootSize({ detail: { value: String(sizes.indexOf('42.5')) } });
  await page.save();
  expect(api.updateMeasurements).toHaveBeenCalledWith(expect.objectContaining({ bootSize: '42.5' }));
});
