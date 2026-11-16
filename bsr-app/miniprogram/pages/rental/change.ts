import { rentalProblem, rentalAccessLost, type RentalContract } from './rentalApi';
import { configuredRentalApi } from './configuredApi';
import { configuredRentalStores, equipmentOptions, type RentalStoreOption } from '../../shared/rentalChoices';
const activeTypes = ['ADD', 'REMOVE', 'REPLACE', 'PARTIAL_RETURN'];
const endedRefundTypes = ['REMOVE', 'PARTIAL_RETURN'];
function requestTypes(contract: RentalContract): string[] { return contract.status === 'ENDED' ? endedRefundTypes : activeTypes; }
function returnOptions(contract: RentalContract) {
  return (contract.items ?? []).filter(item => item.active && ['RENTED', 'RETURNED'].includes(item.status))
    .map(item => ({ itemId: item.itemId, equipmentId: item.equipmentId, checked: false }));
}
Page({
  data: { id: '', contract: null as RentalContract | null, storeId: '', storeIndex: 0, stores: [] as RentalStoreOption[], equipmentOptions, equipmentIndex: 0, equipmentCode: '', types: activeTypes, typeIndex: 0, note: '', loading: false, submitting: false, submittedId: '', error: '', requestedReturnItemIds: [] as string[], returnOptions: [] as ReturnType<typeof returnOptions> },
  async onLoad(options: Record<string, string | undefined>) {
    const stores = configuredRentalStores();
    this.setData({ id: options['id'] ?? '', stores, contract: null, requestedReturnItemIds: [], returnOptions: [], loading: true });
    try {
      const contract = await configuredRentalApi().read(this.data.id);
      this.setData({ contract, storeId: contract.serviceStoreId,
        storeIndex: Math.max(0, stores.findIndex(store => store.id === contract.serviceStoreId)),
        types: requestTypes(contract), typeIndex: 0, returnOptions: returnOptions(contract) });
    }
    catch (error) { this.setData({ error: rentalProblem(error) }); }
    finally { this.setData({ loading: false }); }
  },
  async onShow() {
    if (this.data.loading || this.data.submitting || !this.data.id) return;
    this.setData({ contract: null, loading: true });
    try { const contract=await configuredRentalApi().read(this.data.id); this.setData({ contract, types: requestTypes(contract), typeIndex: 0, requestedReturnItemIds: [], returnOptions: returnOptions(contract) }); }
    catch (error) { this.setData({ error: rentalProblem(error) }); }
    finally { this.setData({ loading: false }); }
  },
  store(event: WechatMiniprogram.PickerChange) {
    const storeIndex = Number(event.detail.value);
    const store = this.data.stores[storeIndex];
    if (store) this.setData({ storeId: store.id, storeIndex });
  },
  equipment(event: WechatMiniprogram.PickerChange) {
    const equipmentIndex = Number(event.detail.value);
    const option = this.data.equipmentOptions[equipmentIndex];
    if (option) this.setData({ equipmentIndex, equipmentCode: option.code, error: '' });
  },
  note(event: WechatMiniprogram.Input) { this.setData({ note: event.detail.value }); },
  type(event: WechatMiniprogram.PickerChange) {
    const typeIndex = Number(event.detail.value);
    const adding = this.data.types[typeIndex] === 'ADD';
    this.setData({ typeIndex, ...(adding ? { requestedReturnItemIds: [], returnOptions: this.data.returnOptions.map(item => ({ ...item, checked: false })) } : {}) });
  },
  returned(event: WechatMiniprogram.CheckboxGroupChange) {
    if (this.data.submitting) return;
    const ids = this.data.returnOptions.filter(item => event.detail.value.includes(item.itemId)).map(item => item.itemId);
    this.setData({ requestedReturnItemIds: ids, returnOptions: this.data.returnOptions.map(item => ({ ...item, checked: ids.includes(item.itemId) })) });
  },
  async submit() {
    if (this.data.submitting || this.data.submittedId || !['ACTIVE', 'ENDED'].includes(this.data.contract?.status ?? '')) return;
    const type = this.data.types[this.data.typeIndex] ?? '';
    const addsEquipment = type === 'ADD' || type === 'REPLACE';
    const equipment = this.data.equipmentOptions.find(option => option.code === this.data.equipmentCode);
    if (addsEquipment && !equipment) {
      this.setData({ error: '请选择希望增租或换入的装备。' });
      return;
    }
    const note = addsEquipment && equipment
      ? ['希望换入或新增：' + equipment.label + '（' + equipment.code + '）', this.data.note.trim()].filter(Boolean).join('；')
      : this.data.note;
    this.setData({ submitting: true, error: '' });
    try {
      const saved = await configuredRentalApi().submitChange(this.data.id, { requestedStoreId: this.data.storeId, type, note,
        ...(this.data.requestedReturnItemIds.length ? { requestedReturnItemIds: [...this.data.requestedReturnItemIds] } : {}),
      });
      this.setData({ submittedId: saved.changeId });
    } catch (error) { this.setData({ error: rentalProblem(error), ...(rentalAccessLost(error) ? { contract: null } : {}) }); }
    finally { this.setData({ submitting: false }); }
  },
});
