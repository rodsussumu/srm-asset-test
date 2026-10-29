import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  Assignor,
  Page,
  PricingInput,
  Receivable,
  Settlement,
  SettlementIntent,
  Simulation,
  Statement,
} from './contracts';
@Injectable({ providedIn: 'root' })
export class CreditApi {
  private readonly http = inject(HttpClient);
  assignors(page = 0) {
    return this.http.get<Page<Assignor>>('/api/v1/assignors', { params: { page, size: 100 } });
  }
  createAssignor(name: string) {
    return this.http.post<Assignor>('/api/v1/assignors', { name });
  }
  simulate(input: PricingInput) {
    return this.http.post<Simulation>('/api/v1/pricing/simulations', input);
  }
  createReceivable(input: PricingInput & { assignorId: string }) {
    return this.http.post<Receivable>('/api/v1/receivables', input);
  }
  receivables(page = 0) {
    return this.http.get<Page<Receivable>>('/api/v1/receivables', { params: { page, size: 20 } });
  }
  receivable(id: string) {
    return this.http.get<Receivable>(`/api/v1/receivables/${encodeURIComponent(id)}`);
  }
  settle(body: SettlementIntent, key: string) {
    return this.http.post<Settlement>('/api/v1/settlements', body, {
      headers: { 'Idempotency-Key': key },
    });
  }
  statement(filters: Record<string, string | number>) {
    return this.http.get<Statement>('/api/v1/settlements', {
      params: new HttpParams({ fromObject: filters }),
    });
  }
  syncFx() {
    return this.http.post('/api/v1/fx-rates/sync', {});
  }
}
