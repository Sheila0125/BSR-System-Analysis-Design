import {
  submitWithRetainedInput,
  type RentalRequestInput,
} from './customerRequestApi';
import { customerRequestApiForConfiguredOrigin } from './configuredApi';

interface EditableItem {
  readonly equipmentCategory: string;
  readonly quantity: string;
  readonly fittingNote: string;
}

Page({
  data: {
    serviceStoreId: '',
    rentalStartDate: '',
    rentalDays: '1',
    skillLevel: '',
    skillIndex: 0,
    skillLevels: ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'],
    skillLabels: ['初级', '中级', '高级', '其他水平（手动填写）'],
    items: [{ equipmentCategory: '', quantity: '1', fittingNote: '' }] as ReadonlyArray<EditableItem>,
    confirmDuplicate: false,
    duplicateRequired: false,
    submitting: false,
    errorMessage: '',
  },

  onStore(event: WechatMiniprogram.Input) {
    this.setData({ serviceStoreId: event.detail.value });
  },

  onStartDate(event: WechatMiniprogram.Input) {
    this.setData({ rentalStartDate: event.detail.value });
  },

  onRentalDays(event: WechatMiniprogram.Input) {
    this.setData({ rentalDays: event.detail.value });
  },

  onSkill(event: WechatMiniprogram.Input) {
    this.setData({ skillLevel: event.detail.value });
  },

  onSkillSelection(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    if (index === 3) {
      this.setData({ skillIndex: index, skillLevel: '' });
      return;
    }
    const code = this.data.skillLevels[index];
    if (code !== undefined) this.setData({ skillIndex: index, skillLevel: code });
  },

  onCategory(event: WechatMiniprogram.Input) {
    this.updateItem(event, { equipmentCategory: event.detail.value });
  },

  onQuantity(event: WechatMiniprogram.Input) {
    this.updateItem(event, { quantity: event.detail.value });
  },

  onFittingNote(event: WechatMiniprogram.Input) {
    this.updateItem(event, { fittingNote: event.detail.value });
  },

  onConfirmDuplicate(event: WechatMiniprogram.SwitchChange) {
    this.setData({ confirmDuplicate: event.detail.value });
  },

  addItem() {
    if (this.data.items.length < 20) {
      this.setData({
        items: [...this.data.items, { equipmentCategory: '', quantity: '1', fittingNote: '' }],
      });
    }
  },

  removeItem(event: WechatMiniprogram.TouchEvent) {
    const index = itemIndex(event);
    if (index !== undefined && this.data.items.length > 1) {
      this.setData({ items: this.data.items.filter((_, item) => item !== index) });
    }
  },

  updateItem(event: WechatMiniprogram.Input, change: Partial<EditableItem>) {
    const index = itemIndex(event);
    if (index !== undefined) {
      this.setData({
        items: this.data.items.map((item, itemIndexValue) =>
          itemIndexValue === index ? { ...item, ...change } : item),
      });
    }
  },

  async submit() {
    const rentalDays = positiveInteger(this.data.rentalDays);
    const quantities = this.data.items.map((item) => positiveInteger(item.quantity));
    if (rentalDays === undefined || quantities.some((quantity) => quantity === undefined)) {
      this.setData({ errorMessage: 'Rental days and quantity must be positive whole numbers.' });
      return;
    }
    const input: RentalRequestInput = {
      serviceStoreId: this.data.serviceStoreId.trim(),
      rentalStartDate: this.data.rentalStartDate,
      rentalDays,
      skillLevel: this.data.skillLevel.trim().toUpperCase(),
      items: this.data.items.map((item, index) => ({
        equipmentCategory: item.equipmentCategory.trim().toUpperCase(),
        quantity: quantities[index] as number,
        fittingNote: emptyToNull(item.fittingNote),
      })),
      confirmDuplicate: this.data.confirmDuplicate,
    };

    this.setData({ submitting: true, errorMessage: '' });
    let outcome;
    try {
      outcome = await submitWithRetainedInput(customerRequestApiForConfiguredOrigin(), input);
    } catch (error) {
      const detail = (error as { detail?: unknown; message?: unknown }).detail;
      const message = (error as { message?: unknown }).message;
      this.setData({
        submitting: false,
        errorMessage: typeof detail === 'string'
          ? detail
          : typeof message === 'string'
            ? message
            : 'Could not submit. Your input is still here.',
      });
      return;
    }
    this.setData({ submitting: false });
    if (outcome.ok) {
      wx.redirectTo({ url: `/pages/customerrequest/detail?id=${outcome.request.requestId}` });
      return;
    }
    this.setData({
      duplicateRequired: outcome.problem.code === 'DUPLICATE_REQUEST_CONFIRMATION_REQUIRED',
      errorMessage: outcome.problem.detail,
    });
  },
});

function positiveInteger(value: string): number | undefined {
  if (!/^[1-9][0-9]*$/.test(value)) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : undefined;
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function itemIndex(event: WechatMiniprogram.BaseEvent): number | undefined {
  const value = Number(event.currentTarget.dataset['index']);
  return Number.isSafeInteger(value) && value >= 0 ? value : undefined;
}
