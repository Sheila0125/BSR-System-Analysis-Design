import { customerRequestApiForConfiguredOrigin } from './configuredApi';

const standardBootSizes = Array.from({ length: 41 }, (_, index) => String(32 + index / 2));

function bootSizeSelection(value: string | null) {
  const bootSize = value === null ? '' : String(Number(value));
  const sizes = [...standardBootSizes];
  if (bootSize && !sizes.includes(bootSize)) {
    sizes.push(bootSize);
    sizes.sort((left, right) => Number(left) - Number(right));
  }
  const bootSizes = ['暂不填写', ...sizes];
  return { bootSize, bootSizes, bootSizeIndex: bootSize ? bootSizes.indexOf(bootSize) : 0 };
}

Page({
  data: {
    fullName: '',
    customerNumber: '',
    email: '',
    phone: '',
    address: '',
    birthYear: '',
    heightCm: '',
    weightKg: '',
    ...bootSizeSelection(null),
    heightEstimated: false,
    weightEstimated: false,
    bootSizeEstimated: false,
    loading: false,
    loaded: false,
    saving: false,
    errorMessage: '',
    successMessage: '',
  },

  async onLoad() {
    this.setData({
      fullName: '', customerNumber: '', heightCm: '', weightKg: '', ...bootSizeSelection(null),
      email: '', phone: '', address: '', birthYear: '', loaded: false,
      heightEstimated: false, weightEstimated: false, bootSizeEstimated: false,
      loading: true, errorMessage: '', successMessage: '',
    });
    try {
      const profile = await customerRequestApiForConfiguredOrigin().loadProfile();
      this.setData({
        fullName: profile.fullName,
        customerNumber: profile.customerNumber,
        email: profile.email,
        phone: profile.phone,
        address: profile.address ?? '',
        birthYear: profile.birthYear == null ? '' : String(profile.birthYear),
        loaded: true,
        heightCm: profile.heightCm ?? '',
        weightKg: profile.weightKg ?? '',
        ...bootSizeSelection(profile.bootSize),
        heightEstimated: profile.heightEstimated,
        weightEstimated: profile.weightEstimated,
        bootSizeEstimated: profile.bootSizeEstimated,
      });
    } catch (error) {
      this.setData({ errorMessage: problemMessage(error, 'Could not load your profile.') });
    } finally {
      this.setData({ loading: false });
    }
  },

  onName(event: WechatMiniprogram.Input) { this.setData({ fullName: event.detail.value }); },
  onEmail(event: WechatMiniprogram.Input) { this.setData({ email: event.detail.value }); },
  onPhone(event: WechatMiniprogram.Input) { this.setData({ phone: event.detail.value }); },
  onAddress(event: WechatMiniprogram.Input) { this.setData({ address: event.detail.value }); },
  onBirthYear(event: WechatMiniprogram.Input) { this.setData({ birthYear: event.detail.value }); },

  async saveContact() {
    if (this.data.saving || this.data.loading || !this.data.loaded) return;
    this.setData({ saving: true, errorMessage: '', successMessage: '' });
    try {
      const profile = await customerRequestApiForConfiguredOrigin().updateContactDetails({
        fullName: this.data.fullName.trim(), email: this.data.email.trim(), phone: this.data.phone.trim(),
        address: this.data.address.trim(), birthYear: this.data.birthYear.trim(),
      });
      this.setData({
        fullName: profile.fullName, email: profile.email, phone: profile.phone,
        address: profile.address ?? '', birthYear: profile.birthYear == null ? '' : String(profile.birthYear),
        successMessage: '联系资料已保存。',
      });
    } catch (error) {
      this.setData({ errorMessage: problemMessage(error, '保存失败，已保留您的输入，请修改后重试。') });
    } finally {
      this.setData({ saving: false });
    }
  },

  onHeight(event: WechatMiniprogram.Input) {
    this.setData({ heightCm: event.detail.value });
  },

  onWeight(event: WechatMiniprogram.Input) {
    this.setData({ weightKg: event.detail.value });
  },

  onBootSize(event: WechatMiniprogram.PickerChange) {
    const bootSizeIndex = Number(event.detail.value);
    const selection = this.data.bootSizes[bootSizeIndex];
    if (selection === undefined) return;
    const bootSize = bootSizeIndex === 0 ? '' : selection;
    this.setData({ bootSize, bootSizeIndex, ...(bootSize ? {} : { bootSizeEstimated: false }) });
  },

  onHeightEstimated(event: WechatMiniprogram.SwitchChange) {
    this.setData({ heightEstimated: event.detail.value });
  },

  onWeightEstimated(event: WechatMiniprogram.SwitchChange) {
    this.setData({ weightEstimated: event.detail.value });
  },

  onBootSizeEstimated(event: WechatMiniprogram.SwitchChange) {
    this.setData({ bootSizeEstimated: event.detail.value });
  },

  async save() {
    if (this.data.saving || this.data.loading || !this.data.loaded) return;
    this.setData({ saving: true, errorMessage: '', successMessage: '' });
    try {
      const profile = await customerRequestApiForConfiguredOrigin().updateMeasurements({
        heightCm: emptyToNull(this.data.heightCm),
        heightEstimated: this.data.heightEstimated,
        weightKg: emptyToNull(this.data.weightKg),
        weightEstimated: this.data.weightEstimated,
        bootSize: emptyToNull(this.data.bootSize),
        bootSizeEstimated: this.data.bootSizeEstimated,
      });
      this.setData({
        heightCm: profile.heightCm ?? '',
        weightKg: profile.weightKg ?? '',
        ...bootSizeSelection(profile.bootSize),
        successMessage: 'Measurements saved.',
      });
    } catch (error) {
      this.setData({ errorMessage: problemMessage(error, 'Could not save. Your input is still here.') });
    } finally {
      this.setData({ saving: false });
    }
  },
});

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function problemMessage(error: unknown, fallback: string): string {
  const detail = (error as { detail?: unknown }).detail;
  return typeof detail === 'string' && detail.length > 0 ? detail : fallback;
}
