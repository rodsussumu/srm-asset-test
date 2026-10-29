import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Workspace } from './workspace';
import { Session } from '../core/session';
import { addDays, businessDate } from '../core/dates';

describe('Portuguese operator workspace', () => {
  let fixture: ComponentFixture<Workspace>;
  let http: HttpTestingController;
  let root: HTMLElement;
  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [Workspace],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Workspace);
    root = fixture.nativeElement;
    http
      .expectOne((r) => r.url === '/api/v1/assignors')
      .flush({
        items: [{ id: 'cedente-id', name: 'Empresa Cedente' }],
        page: 0,
        size: 100,
        total: 1,
      });
    http
      .expectOne((r) => r.url === '/api/v1/receivables')
      .flush({ items: [], page: 0, size: 20, total: 0 });
    http
      .expectOne((r) => r.url === '/api/v1/settlements')
      .flush({ items: [], page: 0, size: 20, total: 0, totalsByCurrency: [] });
    fixture.detectChanges();
  });
  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    fixture.destroy();
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });
  function input(id: string, value: string) {
    const element = root.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    element.value = value;
    element.dispatchEvent(
      new Event(element instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }),
    );
    element.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }
  it('associates every input and select with a visible label and uses native keyboard controls', () => {
    for (const field of root.querySelectorAll('input, select')) {
      expect(field.id).not.toBe('');
      expect(root.querySelector(`label[for="${field.id}"]`)?.textContent?.trim()).toBeTruthy();
      expect(field.getAttribute('tabindex')).not.toBe('-1');
    }
    expect(root.querySelector('#face')?.getAttribute('type')).toBe('text');
    expect(root.querySelector('#face')?.getAttribute('inputmode')).toBe('decimal');
    expect(root.textContent).toContain('Nenhum recebível encontrado');
    expect(root.textContent).toContain('Nenhuma liquidação');
  });
  it('shows local field errors and keeps confirmation unavailable for invalid money and dates', () => {
    input('face', '0,00');
    input('due', businessDate());
    expect(root.querySelector('#face-error')?.textContent).toContain('0,01');
    expect(root.querySelector('#due-error')?.textContent).toContain('1080');
    expect(root.querySelector('#face')?.getAttribute('aria-invalid')).toBe('true');
    expect(root.textContent).not.toContain('Confirmar liquidação');
    vi.advanceTimersByTime(300);
    http.expectNone('/api/v1/pricing/simulations');
  });
  it('renders USD parameters, indicative pricing and exact maximum cents', () => {
    input('assignor', 'cedente-id');
    input('face', '9.999.999.999.999.999,99');
    input('due', addDays(businessDate(), 90));
    input('currency', 'USD');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();
    expect(root.textContent).toContain('Calculando preço indicativo');
    const request = http.expectOne('/api/v1/pricing/simulations');
    expect(request.request.body.faceValue).toBe('9999999999999999.99');
    request.flush({
      ...request.request.body,
      indicative: true,
      months: 3,
      presentValueBrl: '9285994109197483.99',
      discountBrl: '714005890802516.00',
      paymentAmount: '1709467293256205.67',
      baseRate: '0.0100',
      spread: '0.0150',
      operationDate: businessDate(),
      policyVersion: 'monthly-ceil-30d-v1',
      roundingMode: 'HALF_EVEN',
      fxRate: {
        id: 'fx-id',
        rate: '5.43210000',
        effectiveAt: '2026-10-28T15:00:00Z',
        origin: 'mock',
      },
    });
    fixture.detectChanges();
    expect(root.textContent).toContain('1.709.467.293.256.205,67');
    expect(root.textContent).toContain('BRL por USD');
    expect(root.textContent).toContain('monthly-ceil-30d-v1');
    expect(root.textContent).toContain('Preço indicativo');
    expect(root.textContent).toContain('fx-id');
  });
  it('submits a new receivable using the native form submit event', () => {
    input('assignor', 'cedente-id');
    input('face', '100,01');
    root
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const request = http.expectOne('/api/v1/receivables');
    expect(request.request.body.faceValue).toBe('100.01');
    request.flush(
      { errors: { faceValue: 'Valor rejeitado pelo servidor.' } },
      { status: 400, statusText: 'Bad Request' },
    );
    http
      .expectOne((r) => r.url === '/api/v1/receivables')
      .flush({ items: [], page: 0, size: 20, total: 0 });
    fixture.detectChanges();
    expect(root.querySelector('#face-error')?.textContent).toContain(
      'Valor rejeitado pelo servidor',
    );
  });
  it('only shows administrative synchronization to ADMIN', () => {
    expect(root.textContent).not.toContain('Administração de câmbio');
    TestBed.inject(Session).user.set({ username: 'admin', roles: ['ADMIN', 'OPERATOR'] });
    fixture.detectChanges();
    expect(root.textContent).toContain('Administração de câmbio');
  });
});
