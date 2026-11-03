import { rentalProblem, rentalAccessLost, type RentalContract } from './rentalApi';
import { configuredRentalApi } from './configuredApi';
const activeTypes = ['ADD', 'REMOVE', 'REPLACE', 'PARTIAL_RETURN'];
const endedRefundTypes = ['REMOVE', 'PARTIAL_RETURN'];
function requestTypes(contract: RentalContract): string[] { return contract.status === 'ENDED' ? endedRefundTypes : activeTypes; }
function returnOptions(contract: RentalContract) {
  return (contract.items ?? []).filter(item => item.active && ['RENTED', 'RETURNED'].includes(item.status))
    .map(item => ({ itemId: item.itemId, equipmentId: item.equipmentId, checked: false }));
}
Page({
  data: { id: '', contract: null as RentalContract | null, storeId: '', types: activeTypes, typeIndex: 0, note: '', loading: false, submitting: false, submittedId: '', error: '', requestedReturnItemIds: [] as string[], returnOptions: [] as ReturnType<typeof returnOptions> },
  async onLoad(options: Record<string, string | undefined>) {
    this.setData({ id: options['id'] ?? '', contract: null, requestedReturnItemIds: [], returnOptions: [], loading: true });
    try { const contract = await configuredRentalApi().read(this.data.id); this.setData({ contract, storeId: contract.serviceStoreId, types: requestTypes(contract), typeIndex: 0, returnOptions: returnOptions(contract) }); }
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
  store(event: WechatMiniprogram.Input) { this.setData({ storeId: event.detail.value }); },
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
    this.setData({ submitting: true, error: '' });
    try {
      const saved = await configuredRentalApi().submitChange(this.data.id, { requestedStoreId: this.data.storeId, type: this.data.types[this.data.typeIndex] ?? '', note: this.data.note,
        ...(this.data.requestedReturnItemIds.length ? { requestedReturnItemIds: [...this.data.requestedReturnItemIds] } : {}),
      });
      this.setData({ submittedId: saved.changeId });
    } catch (error) { this.setData({ error: rentalProblem(error), ...(rentalAccessLost(error) ? { contract: null } : {}) }); }
    finally { this.setData({ submitting: false }); }
  },
});
