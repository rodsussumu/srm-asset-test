import { Component, DestroyRef, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Session, problemMessage } from '../core/session';
import { CreditApi } from '../core/credit-api';
import { decimalDisplay } from '../core/decimal';
import { displayInstant } from '../core/dates';
import { CreditState } from './credit-state';
import { StatementState } from './statement-state';
@Component({
  selector: 'app-workspace',
  imports: [ReactiveFormsModule],
  providers: [CreditState, StatementState],
  templateUrl: './workspace.html',
})
export class Workspace {
  readonly credit = inject(CreditState);
  readonly statement = inject(StatementState);
  readonly session = inject(Session);
  private readonly api = inject(CreditApi);
  private readonly destroy = inject(DestroyRef);
  readonly money = decimalDisplay;
  readonly instant = displayInstant;
  fxMessage = '';
  fxBusy = false;
  constructor() {
    this.credit.initialize();
    this.statement.load();
    this.credit.confirmed.pipe(takeUntilDestroyed()).subscribe(() => this.statement.load(0));
  }
  syncFx() {
    if (this.fxBusy) return;
    this.fxBusy = true;
    this.fxMessage = '';
    this.api
      .syncFx()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          this.fxMessage = 'Cotação sincronizada. Solicite nova simulação para usar o preço atual.';
          this.fxBusy = false;
        },
        error: (error: unknown) => {
          this.fxMessage = problemMessage(error);
          this.fxBusy = false;
        },
      });
  }
  fieldError(name: keyof typeof this.credit.form.controls): string {
    const server = this.credit.fieldErrors()[name];
    if (server) return server;
    const control = this.credit.form.controls[name];
    if (!control.touched || !control.invalid) return '';
    if (name === 'faceValue')
      return 'Informe de R$ 0,01 a R$ 9.999.999.999.999.999,99, com até dois centavos (ex.: 100.000,00).';
    if (name === 'dueDate')
      return 'Informe um vencimento entre amanhã e 1080 dias após hoje, em São Paulo.';
    return 'Selecione um cedente.';
  }
}
