import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StatementState } from './statement-state';
const empty = { items: [], page: 0, size: 20, total: 0, totalsByCurrency: [] };
describe('remote statements', () => {
  let state: StatementState;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [StatementState, provideHttpClient(), provideHttpClientTesting()],
    });
    state = TestBed.inject(StatementState);
    http = TestBed.inject(HttpTestingController);
    state.form.setValue(
      { from: '2026-10-01', through: '2026-10-28', assignorId: '', currency: '' },
      { emitEvent: false },
    );
  });
  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    TestBed.resetTestingModule();
  });
  it('translates civil dates to half-open UTC and requests the selected remote page', () => {
    state.load(2);
    expect(state.loading()).toBe(true);
    const request = http.expectOne((r) => r.url === '/api/v1/settlements');
    expect(request.request.params.get('from')).toBe('2026-10-01T03:00:00.000Z');
    expect(request.request.params.get('to')).toBe('2026-10-29T03:00:00.000Z');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('assignorId')).toBe(false);
    request.flush({ ...empty, page: 2 });
    expect(state.loading()).toBe(false);
  });
  it('resets to page zero and cancels the old request when any filter changes', () => {
    state.load(2);
    const previous = http.expectOne((r) => r.params.get('page') === '2');
    state.form.controls.assignorId.setValue('assignor-id');
    expect(previous.cancelled).toBe(true);
    const next = http.expectOne((r) => r.params.get('assignorId') === 'assignor-id');
    expect(next.request.params.get('page')).toBe('0');
    next.flush(empty);
    expect(state.page()).toBe(0);
    state.load(1);
    http.expectOne((r) => r.params.get('page') === '1').flush({ ...empty, page: 1 });
    state.form.controls.currency.setValue('USD');
    const usd = http.expectOne((r) => r.params.get('currency') === 'USD');
    expect(usd.request.params.get('page')).toBe('0');
    usd.flush(empty);
  });
  it('preserves independent totals for currencies and empty pages', () => {
    state.load();
    const totals = [
      { currency: 'BRL', count: 30, paymentTotal: '9285994.10' },
      { currency: 'USD', count: 2, paymentTotal: '34189.34' },
    ];
    http
      .expectOne((r) => r.url === '/api/v1/settlements')
      .flush({ ...empty, total: 32, totalsByCurrency: totals });
    expect(state.result()?.totalsByCurrency).toEqual(totals);
    state.load(5);
    http
      .expectOne((r) => r.params.get('page') === '5')
      .flush({ ...empty, page: 5, total: 32, totalsByCurrency: totals });
    expect(state.result()?.items).toEqual([]);
    expect(state.result()?.totalsByCurrency).toEqual(totals);
  });
  it('shows errors without stale totals, recovers, and rejects invalid ranges locally', () => {
    state.load();
    http
      .expectOne((r) => r.url === '/api/v1/settlements')
      .flush({ detail: 'Serviço indisponível.' }, { status: 503, statusText: 'Unavailable' });
    expect(state.error()).toContain('Serviço indisponível');
    expect(state.result()).toBeNull();
    expect(state.loading()).toBe(false);
    state.load();
    http.expectOne((r) => r.url === '/api/v1/settlements').flush(empty);
    expect(state.error()).toBe('');
    state.form.controls.from.setValue('2027-01-01');
    http.expectNone((r) => r.url === '/api/v1/settlements');
    expect(state.error()).toContain('período ordenado');
  });
});
