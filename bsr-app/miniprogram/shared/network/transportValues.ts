declare const identifierBrand: unique symbol;
declare const amountBrand: unique symbol;

export type IdentifierString = string & { readonly [identifierBrand]: true };
export type AmountString = string & { readonly [amountBrand]: true };

export function identifierString(value: string): IdentifierString {
  if (!/^[1-9]\d*$/.test(value)) {
    throw new Error('Identifiers must be positive decimal strings.');
  }
  return value as IdentifierString;
}

export function amountString(value: string): AmountString {
  if (!/^-?(?:0|[1-9]\d*)\.\d{2}$/.test(value)) {
    throw new Error('Amounts must be decimal strings with exactly two fraction digits.');
  }
  return value as AmountString;
}
