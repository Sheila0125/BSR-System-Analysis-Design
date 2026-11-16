declare function getApp<T>(): T;

export interface RentalStoreOption {
  id: string;
  name: string;
}

export const equipmentOptions = [
  { code: 'SKI', label: '双板' },
  { code: 'SNOWBOARD', label: '单板' },
  { code: 'BOOTS', label: '雪鞋' },
  { code: 'POLES', label: '雪杖' },
  { code: 'GOGGLES', label: '雪镜' },
  { code: 'HELMET', label: '头盔' },
];

export function configuredRentalStores(): RentalStoreOption[] {
  return getApp<{ globalData?: { rentalStores?: RentalStoreOption[] } }>().globalData?.rentalStores ?? [];
}
