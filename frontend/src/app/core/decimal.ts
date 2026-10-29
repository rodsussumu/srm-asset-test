/** Brazilian input -> API decimal. Never coerce money to a binary number. */
export function decimalInput(input: string): string | null {
  const text = input.trim();
  if (!/^(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d{1,2})?$/.test(text)) return null;
  const [whole, cents = ''] = text.replaceAll('.', '').split(',');
  const integer = whole.replace(/^0+(?=\d)/, '');
  if (integer.length > 16) return null;
  const result = `${integer}.${cents.padEnd(2, '0')}`;
  return result === '0.00' ? null : result;
}
export function decimalDisplay(value: string): string {
  const [integer, fraction = ''] = value.split('.');
  return `${integer.replace(/\B(?=(\d{3})+(?!\d))/g, '.')},${fraction.padEnd(2, '0')}`;
}
