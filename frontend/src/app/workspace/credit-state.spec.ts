import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CreditState } from './credit-state';
import { addDays, businessDate } from '../core/dates';
import { Receivable, Simulation, Settlement } from '../core/contracts';
const id = '00000000-0000-0000-0000-000000000001';
const input = {
  faceValue: '100000.00',
  dueDate: addDays(businessDate(), 90),
  type: 'DUPLICATA' as const,
  paymentCurrency: 'BRL' as const,
};
const receivable: Receivable = { ...input, id, assignorId: id, status: 'AVAILABLE', version: 0 };
const price = {
  ...input,
  indicative: true,
  presentValueBrl: '92859.94',
  discountBrl: '7140.06',
  paymentAmount: '92859.94',
  months: 3,
  fxRate: null,
} as Simulation;
const settled = {
  ...price,
  id: 'settled-id',
  assignorId: id,
  assignorName: 'Cedente',
  actorId: 'operator',
  settledAt: '2026-10-28T15:00:00Z',
  receivableId: id,
  paymentCurrency: 'BRL',
  paymentAmount: '92859.94',
} as Settlement;
describe('credit operation state', () => {
  let state: CreditState;
  let http: HttpTestingController;
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [CreditState, provideHttpClient(), provideHttpClientTesting()],
    });
    state = TestBed.inject(CreditState);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });
  function fill() {
    state.form.patchValue({ ...input, assignorId: id, faceValue: '100.000,00' });
  }
  function ready() {
    state.select(receivable);
    vi.advanceTimersByTime(300);
    http.expectOne('/api/v1/pricing/simulations').flush(price);
  }
  function refreshList() {
    http
      .expectOne((r) => r.url === '/api/v1/receivables')
      .flush({ items: [{ ...receivable, status: 'SETTLED' }], page: 0, size: 20, total: 1 });
  }
  it('waits exactly 300 ms and sends decimal strings', () => {
    fill();
    vi.advanceTimersByTime(299);
    http.expectNone('/api/v1/pricing/simulations');
    vi.advanceTimersByTime(1);
    const request = http.expectOne('/api/v1/pricing/simulations');
    expect(request.request.body.faceValue).toBe('100000.00');
    request.flush(price);
    expect(state.phase()).toBe('ready');
  });
  it('cancels the old request immediately on editing, including during the debounce window', () => {
    fill();
    vi.advanceTimersByTime(300);
    const old = http.expectOne('/api/v1/pricing/simulations');
    state.form.controls.faceValue.setValue('200.000,00');
    expect(old.cancelled).toBe(true);
    expect(state.simulation()).toBeNull();
    vi.advanceTimersByTime(300);
    http
      .expectOne('/api/v1/pricing/simulations')
      .flush({ ...price, faceValue: '200000.00', paymentAmount: '185719.88' });
    expect(state.simulation()?.paymentAmount).toBe('185719.88');
    expect(() => old.flush(price)).toThrow();
  });
  it.each(['0,00', '1e3', '1,001'])('does not simulate invalid amount %s', (value) => {
    fill();
    state.form.controls.faceValue.setValue(value);
    vi.advanceTimersByTime(300);
    http.expectNone('/api/v1/pricing/simulations');
    expect(state.form.controls.faceValue.invalid).toBe(true);
    expect(state.simulation()).toBeNull();
    state.confirm();
    http.expectNone('/api/v1/settlements');
  });
  it('clears a previous result and rejects same-day maturity', () => {
    fill();
    vi.advanceTimersByTime(300);
    http.expectOne('/api/v1/pricing/simulations').flush(price);
    state.form.controls.dueDate.setValue(businessDate());
    expect(state.simulation()).toBeNull();
    vi.advanceTimersByTime(300);
    http.expectNone('/api/v1/pricing/simulations');
  });
  it('shows unavailable FX and supports a subsequent valid simulation', () => {
    fill();
    state.form.controls.paymentCurrency.setValue('USD');
    vi.advanceTimersByTime(300);
    http.expectOne('/api/v1/pricing/simulations').flush(
      {
        code: 'FX_RATE_UNAVAILABLE',
        detail: 'Cotação indisponível.',
        correlationId: 'correlation',
      },
      { status: 503, statusText: 'Unavailable' },
    );
    expect(state.phase()).toBe('failed');
    expect(state.message()).toContain('Cotação indisponível');
    expect(state.message()).toContain('correlation');
    state.form.controls.paymentCurrency.setValue('BRL');
    vi.advanceTimersByTime(300);
    http.expectOne('/api/v1/pricing/simulations').flush(price);
    expect(state.phase()).toBe('ready');
  });
  it('creates the receivable separately and requires explicit confirmation', () => {
    fill();
    state.createReceivable();
    const create = http.expectOne('/api/v1/receivables');
    expect(create.request.body.faceValue).toBe('100000.00');
    state.createReceivable();
    http.expectNone('/api/v1/receivables');
    create.flush(receivable);
    expect(state.form.disabled).toBe(true);
    expect(state.selected()?.id).toBe(id);
    refreshList();
    vi.advanceTimersByTime(300);
    http.expectOne('/api/v1/pricing/simulations').flush(price);
    http.expectNone('/api/v1/settlements');
  });
  it('ignores double clicks and retries the exact key and body after a transport failure', () => {
    ready();
    state.confirm();
    const original = http.expectOne('/api/v1/settlements');
    const key = original.request.headers.get('Idempotency-Key');
    const body = original.request.body;
    state.confirm();
    http.expectNone('/api/v1/settlements');
    original.error(new ProgressEvent('error'));
    expect(state.uncertain()).toBe(true);
    state.newReceivable();
    state.select({ ...receivable, id: 'another' });
    expect(state.selected()?.id).toBe(id);
    state.reprice();
    http.expectNone('/api/v1/pricing/simulations');
    state.confirm();
    const retry = http.expectOne('/api/v1/settlements');
    expect(retry.request.body).toEqual(body);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(settled);
    refreshList();
    expect(state.phase()).toBe('confirmed');
    expect(state.settlement()?.id).toBe('settled-id');
    expect(state.intent()).toBeNull();
  });
  it('times out a hanging confirmation at 15 seconds without losing the intent', () => {
    ready();
    state.confirm();
    const request = http.expectOne('/api/v1/settlements');
    const intent = state.intent();
    vi.advanceTimersByTime(15000);
    expect(request.cancelled).toBe(true);
    expect(state.uncertain()).toBe(true);
    expect(state.intent()).toEqual(intent);
  });
  it('keeps the intent for REQUEST_IN_PROGRESS and server failures', () => {
    ready();
    state.confirm();
    const first = http.expectOne('/api/v1/settlements');
    const key = first.request.headers.get('Idempotency-Key');
    first.flush({ code: 'REQUEST_IN_PROGRESS' }, { status: 409, statusText: 'Conflict' });
    state.confirm();
    const retry = http.expectOne('/api/v1/settlements');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({}, { status: 500, statusText: 'Error' });
    expect(state.intent()?.key).toBe(key);
  });
  it('requires new simulation and explicit confirmation after PRICE_CHANGED with a new key', () => {
    ready();
    state.confirm();
    const original = http.expectOne('/api/v1/settlements');
    const key = original.request.headers.get('Idempotency-Key');
    original.flush({ code: 'PRICE_CHANGED' }, { status: 409, statusText: 'Conflict' });
    expect(state.simulation()).toBeNull();
    expect(state.intent()).toBeNull();
    state.confirm();
    http.expectNone('/api/v1/settlements');
    state.reprice();
    vi.advanceTimersByTime(300);
    http.expectOne('/api/v1/pricing/simulations').flush({ ...price, paymentAmount: '92800.00' });
    http.expectNone('/api/v1/settlements');
    state.confirm();
    const changed = http.expectOne('/api/v1/settlements');
    expect(changed.request.headers.get('Idempotency-Key')).not.toBe(key);
    expect(changed.request.body.expectedPaymentAmount).toBe('92800.00');
    changed.flush(settled);
    refreshList();
  });
  it('handles an already settled receivable without offering another confirmation', () => {
    ready();
    state.confirm();
    http
      .expectOne('/api/v1/settlements')
      .flush({ code: 'RECEIVABLE_ALREADY_SETTLED' }, { status: 409, statusText: 'Conflict' });
    refreshList();
    expect(state.selected()?.status).toBe('SETTLED');
    state.confirm();
    http.expectNone('/api/v1/settlements');
  });
  it('reloads server state and never automatically retries a lost in-memory intention', () => {
    state.initialize();
    http
      .expectOne((r) => r.url === '/api/v1/assignors')
      .flush({ items: [], page: 0, size: 100, total: 0 });
    refreshList();
    expect(state.list()?.items[0].status).toBe('SETTLED');
    state.consult(id);
    http.expectOne(`/api/v1/receivables/${id}`).flush({ ...receivable, status: 'SETTLED' });
    vi.advanceTimersByTime(300);
    expect(state.intent()).toBeNull();
    http.expectNone('/api/v1/settlements');
    http.expectNone('/api/v1/pricing/simulations');
  });
  it('sends the expected FX identifier and USD amount without converting either', () => {
    state.select({ ...receivable, paymentCurrency: 'USD' });
    vi.advanceTimersByTime(300);
    http.expectOne('/api/v1/pricing/simulations').flush({
      ...price,
      paymentCurrency: 'USD',
      paymentAmount: '17094.67',
      fxRate: { id: 'fx-id', rate: '5.43210000' },
    });
    state.confirm();
    const request = http.expectOne('/api/v1/settlements');
    expect(request.request.body).toEqual({
      receivableId: id,
      currency: 'USD',
      expectedPaymentAmount: '17094.67',
      expectedFxRateId: 'fx-id',
    });
    request.flush(settled);
    refreshList();
  });
  it('reads every assignor page and selects a newly created assignor', () => {
    state.loadAssignors();
    http
      .expectOne((r) => r.url === '/api/v1/assignors' && r.params.get('page') === '0')
      .flush({ items: [{ id: 'first', name: 'First' }], page: 0, size: 1, total: 2 });
    http
      .expectOne((r) => r.url === '/api/v1/assignors' && r.params.get('page') === '1')
      .flush({ items: [{ id: 'last', name: 'Last' }], page: 1, size: 1, total: 2 });
    expect(state.assignors()).toHaveLength(2);
    state.createAssignor(' Novo ');
    const request = http.expectOne('/api/v1/assignors');
    expect(request.request.body).toEqual({ name: 'Novo' });
    request.flush({ id, name: 'Novo' });
    expect(state.form.controls.assignorId.value).toBe(id);
  });
});
