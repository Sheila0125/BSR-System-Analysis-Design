import { customerAuthForOrigin } from '../../shared/auth/customerAuth';
import { RentalApi } from './rentalApi';
export function configuredRentalApi(): RentalApi {
  const origin = getApp<{ globalData?: { apiOrigin?: string } }>().globalData?.apiOrigin;
  if (!origin) throw new Error('The secure BSR API origin is not configured.');
  return new RentalApi(customerAuthForOrigin(origin));
}
