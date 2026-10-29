import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Session, sessionInterceptor } from './session';
describe('session and CSRF', () => {
  let session: Session;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    session = TestBed.inject(Session);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });
  it('renews CSRF after login, submits form encoding and keeps credentials out of storage', () => {
    const local = vi.spyOn(Storage.prototype, 'setItem');
    session.login('operator', 'private-password').subscribe();
    http.expectOne('/api/v1/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'before' });
    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.headers.get('X-CSRF-TOKEN')).toBe('before');
    expect(login.request.body.toString()).toBe('username=operator&password=private-password');
    login.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'after' });
    http.expectOne('/api/v1/auth/me').flush({ username: 'operator', roles: ['OPERATOR'] });
    expect(session.header()?.token).toBe('after');
    expect(session.user()?.username).toBe('operator');
    expect(local).not.toHaveBeenCalled();
    local.mockRestore();
  });
  it('renews after logout and drops identity', () => {
    session.user.set({ username: 'operator', roles: ['OPERATOR'] });
    session.logout().subscribe();
    http.expectOne('/api/v1/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'current' });
    const logout = http.expectOne('/api/v1/auth/logout');
    expect(logout.request.headers.get('X-CSRF-TOKEN')).toBe('current');
    logout.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'anonymous' });
    expect(session.user()).toBeNull();
    expect(session.header()?.token).toBe('anonymous');
  });
  it('returns to login on session expiry and never sends a CSRF token to other origins', () => {
    session.refreshToken().subscribe();
    http.expectOne('/api/v1/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'private' });
    const client = TestBed.inject(HttpClient);
    client.post('https://example.invalid/other', {}).subscribe();
    const external = http.expectOne('https://example.invalid/other');
    expect(external.request.headers.has('X-CSRF-TOKEN')).toBe(false);
    external.flush({});
    session.user.set({ username: 'operator', roles: ['OPERATOR'] });
    client.get('/api/v1/receivables').subscribe({ error: () => {} });
    http.expectOne('/api/v1/receivables').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(session.user()).toBeNull();
    expect(session.header()).toBeNull();
  });
});
