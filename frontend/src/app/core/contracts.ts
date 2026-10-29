export type Currency = 'BRL' | 'USD';
export type ReceivableType = 'DUPLICATA' | 'CHEQUE';
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
export interface Assignor {
  id: string;
  name: string;
}
export interface PricingInput {
  faceValue: string;
  dueDate: string;
  type: ReceivableType;
  paymentCurrency: Currency;
}
export interface Receivable extends PricingInput {
  id: string;
  assignorId: string;
  status: 'AVAILABLE' | 'SETTLED';
  version: number;
}
export interface FxRate {
  id: string;
  rate: string;
  effectiveAt: string;
  origin: string;
  recordedAt: string;
}
export interface Simulation extends PricingInput {
  indicative: boolean;
  presentValueBrl: string;
  discountBrl: string;
  paymentAmount: string;
  months: number;
  baseRate: string;
  spread: string;
  operationAt: string;
  operationDate: string;
  businessTimezone: string;
  termConvention: string;
  roundingMode: string;
  policyVersion: string;
  fxRate: FxRate | null;
}
export interface Settlement extends Omit<
  Simulation,
  'indicative' | 'businessTimezone' | 'termConvention' | 'roundingMode'
> {
  id: string;
  receivableId: string;
  assignorId: string;
  assignorName: string;
  actorId: string;
  settledAt: string;
}
export interface SettlementIntent {
  receivableId: string;
  currency: Currency;
  expectedPaymentAmount: string;
  expectedFxRateId: string | null;
}
export interface Statement extends Page<Settlement> {
  totalsByCurrency: { currency: Currency; count: number; paymentTotal: string }[];
}
export interface Identity {
  username: string;
  roles: string[];
}
export interface ApiProblem {
  code?: string;
  detail?: string;
  correlationId?: string;
  errors?: Record<string, string>;
}
