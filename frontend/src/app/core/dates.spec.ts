import { businessDate, eligibleDate, statementPeriod, dayStart } from './dates';
describe('São Paulo civil dates', () => {
  it('uses São Paulo rather than the browser timezone', () => {
    expect(businessDate(new Date('2026-10-28T02:59:59Z'))).toBe('2026-10-27');
    expect(businessDate(new Date('2026-10-28T03:00:00Z'))).toBe('2026-10-28');
  });
  it('creates an inclusive final civil day with exclusive next-day UTC bound', () => {
    expect(statementPeriod('2026-10-28', '2026-10-28')).toEqual({
      from: '2026-10-28T03:00:00.000Z',
      to: '2026-10-29T03:00:00.000Z',
    });
  });
  it('handles the historical DST midnight gap and end of DST', () => {
    expect(dayStart('2018-11-04')).toBe('2018-11-04T03:00:00.000Z');
    expect(dayStart('2018-11-05')).toBe('2018-11-05T02:00:00.000Z');
    expect(statementPeriod('2019-02-16', '2019-02-16')).toEqual({
      from: '2019-02-16T02:00:00.000Z',
      to: '2019-02-17T03:00:00.000Z',
    });
  });
  it('rejects invalid or excessive periods and accepts 366 days', () => {
    expect(() => statementPeriod('2026-02-30', '2026-03-01')).toThrow();
    expect(() => statementPeriod('2026-10-29', '2026-10-28')).toThrow();
    expect(() => statementPeriod('2025-01-01', '2026-01-02')).toThrow();
    expect(() => statementPeriod('2024-01-01', '2024-12-31')).not.toThrow();
  });
  it('enforces days 1 to 1080', () => {
    expect(eligibleDate('2026-10-28', '2026-10-28')).toBe(false);
    expect(eligibleDate('2026-10-29', '2026-10-28')).toBe(true);
    expect(eligibleDate('2029-10-12', '2026-10-28')).toBe(true);
    expect(eligibleDate('2029-10-13', '2026-10-28')).toBe(false);
  });
});
