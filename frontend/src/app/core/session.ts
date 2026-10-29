import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpInterceptorFn, HttpParams } from '@angular/common/http';
import { catchError, switchMap, tap, throwError, Observable } from 'rxjs';
import { Identity, ApiProblem } from './contracts';

@Injectable({ providedIn: 'root' })
export class Session {
  private readonly http = inject(HttpClient);
  readonly user = signal<Identity | null>(null);
  readonly ready = signal(false);
  private csrf: { headerName: string; token: string } | null = null;
  header() {
    return this.csrf;
  }
  expired() {
    this.user.set(null);
    this.csrf = null;
  }
  refreshToken() {
    return this.http
      .get<{ headerName: string; token: string }>('/api/v1/auth/csrf')
      .pipe(tap((token) => (this.csrf = token)));
  }
  initialize() {
    return this.refreshToken().pipe(
      switchMap(() => this.http.get<Identity>('/api/v1/auth/me')),
      tap((identity) => this.user.set(identity)),
    );
  }
  login(username: string, password: string): Observable<Identity> {
    return this.refreshToken().pipe(
      switchMap(() =>
        this.http.post<void>(
          '/api/v1/auth/login',
          new HttpParams().set('username', username).set('password', password),
        ),
      ),
      switchMap(() => this.initialize()),
    );
  }
  logout() {
    return this.refreshToken().pipe(
      switchMap(() => this.http.post<void>('/api/v1/auth/logout', {})),
      tap(() => this.expired()),
      switchMap(() => this.refreshToken()),
    );
  }
}
export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(Session);
  if (!request.url.startsWith('/api/')) return next(request);
  const csrf = session.header();
  if (csrf && !['GET', 'HEAD', 'OPTIONS'].includes(request.method))
    request = request.clone({ setHeaders: { [csrf.headerName]: csrf.token } });
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) session.expired();
      return throwError(() => error);
    }),
  );
};
export function problemMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'Não foi possível concluir. Tente novamente.';
  const problem = error.error as ApiProblem | null;
  if (error.status === 0 || error.status === 408)
    return 'Resposta não recebida. Consulte o estado ou repita o mesmo pedido.';
  const reference = problem?.correlationId ? ` Referência: ${problem.correlationId}.` : '';
  return (problem?.detail ?? 'Não foi possível concluir a solicitação.') + reference;
}
