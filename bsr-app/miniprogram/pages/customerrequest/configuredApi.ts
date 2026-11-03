import { customerAuthForOrigin } from '../../shared/auth/customerAuth';
import { CustomerRequestApi } from './customerRequestApi';

export function customerRequestApiForConfiguredOrigin(): CustomerRequestApi {
  const origin = getApp<{ globalData?: { apiOrigin?: string } }>().globalData?.apiOrigin;
  if (origin === undefined) {
    throw new Error('The secure BSR API origin is not configured.');
  }
  return new CustomerRequestApi(customerAuthForOrigin(origin));
}
