import { decimalDisplay, decimalInput } from './decimal';
describe('decimal strings', () => {
  it.each([
    ['100.000,00', '100000.00'],
    ['0,01', '0.01'],
    ['00025,5', '25.50'],
    ['9.999.999.999.999.999,99', '9999999999999999.99'],
  ])('normalizes %s without losing cents', (input, expected) =>
    expect(decimalInput(input)).toBe(expected),
  );
  it.each([
    '',
    '0',
    '0,00',
    '-1,00',
    '1e3',
    '1.00',
    '1,001',
    '1.23.456,00',
    '10000000000000000,00',
    'R$ 12,00',
  ])('rejects %s', (input) => expect(decimalInput(input)).toBeNull());
  it('formats maximum payment and aggregate strings without numeric conversion', () => {
    expect(decimalDisplay('9999999999999999999999999999.99')).toBe(
      '9.999.999.999.999.999.999.999.999.999,99',
    );
    expect(decimalDisplay('0.01')).toBe('0,01');
    expect(decimalDisplay('17094.67')).toBe('17.094,67');
  });
});
