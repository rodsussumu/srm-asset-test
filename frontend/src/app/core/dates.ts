const zone = 'America/Sao_Paulo';
const formatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: zone,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
export function businessDate(instant = new Date()): string {
  const parts = formatter.formatToParts(instant);
  return ['year', 'month', 'day']
    .map((type) => parts.find((p) => p.type === type)!.value)
    .join('-');
}
export function addDays(date: string, days: number): string {
  const parsed = new Date(`${date}T12:00:00Z`);
  parsed.setUTCDate(parsed.getUTCDate() + days);
  return parsed.toISOString().slice(0, 10);
}
export function eligibleDate(date: string, today = businessDate()): boolean {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(date) &&
    date > today &&
    date <= addDays(today, 1080) &&
    Number.isFinite(Date.parse(`${date}T12:00:00Z`)) &&
    new Date(`${date}T12:00:00Z`).toISOString().slice(0, 10) === date
  );
}
/** First instant of a São Paulo civil day, including historical DST midnight gaps. */
export function dayStart(date: string): string {
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
    new Date(`${date}T12:00:00Z`).toISOString().slice(0, 10) !== date
  )
    throw new Error('Data inválida.');
  const anchor = Date.parse(`${date}T00:00:00Z`);
  let low = anchor - 86400000;
  let high = anchor + 86400000;
  while (low < high) {
    const middle = Math.floor((low + high) / 2);
    if (businessDate(new Date(middle)) < date) low = middle + 1;
    else high = middle;
  }
  return new Date(low).toISOString();
}
export function statementPeriod(from: string, through: string): { from: string; to: string } {
  dayStart(through);
  const start = dayStart(from);
  const end = dayStart(addDays(through, 1));
  if (from > through || Date.parse(end) - Date.parse(start) > 366 * 86400000)
    throw new Error('Informe um período ordenado de até 366 dias.');
  return { from: start, to: end };
}
export function displayInstant(value: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    timeZone: zone,
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}
