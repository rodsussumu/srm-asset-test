import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of, Subject, switchMap } from 'rxjs';
import { CreditApi } from '../core/credit-api';
import { Statement } from '../core/contracts';
import { businessDate, statementPeriod } from '../core/dates';
import { problemMessage } from '../core/session';
@Injectable()
export class StatementState {
  private readonly api = inject(CreditApi);
  private readonly destroy = inject(DestroyRef);
  readonly form = inject(FormBuilder).nonNullable.group({
    from: businessDate(),
    through: businessDate(),
    assignorId: '',
    currency: '',
  });
  readonly result = signal<Statement | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly page = signal(0);
  private readonly queries = new Subject<Record<string, string | number> | null>();
  constructor() {
    this.queries
      .pipe(
        switchMap((query) =>
          query
            ? this.api.statement(query).pipe(
                catchError((error: unknown) => {
                  this.error.set(problemMessage(error));
                  return of(null);
                }),
              )
            : of(null),
        ),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((result) => {
        this.result.set(result);
        this.loading.set(false);
      });
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => this.load(0));
  }
  load(page = this.page()) {
    this.page.set(page);
    this.error.set('');
    this.result.set(null);
    this.loading.set(true);
    try {
      const { from, through, assignorId, currency } = this.form.getRawValue();
      const query: Record<string, string | number> = {
        ...statementPeriod(from, through),
        page,
        size: 20,
      };
      if (assignorId) query['assignorId'] = assignorId;
      if (currency) query['currency'] = currency;
      this.queries.next(query);
    } catch {
      this.queries.next(null);
      this.error.set('Informe datas válidas e um período ordenado de até 366 dias.');
    }
  }
}
