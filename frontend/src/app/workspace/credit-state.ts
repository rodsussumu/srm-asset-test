import { Injectable, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, Validators, ValidatorFn } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Subject,
  of,
  timer,
  switchMap,
  catchError,
  finalize,
  expand,
  EMPTY,
  reduce,
  timeout,
} from 'rxjs';
import { CreditApi } from '../core/credit-api';
import {
  ApiProblem,
  Assignor,
  Currency,
  Page,
  PricingInput,
  Receivable,
  ReceivableType,
  Settlement,
  SettlementIntent,
  Simulation,
} from '../core/contracts';
import { addDays, businessDate, eligibleDate } from '../core/dates';
import { decimalDisplay, decimalInput } from '../core/decimal';
import { problemMessage } from '../core/session';

const faceValidator: ValidatorFn = (control) =>
  decimalInput(control.value as string) ? null : { money: true };
const dueValidator: ValidatorFn = (control) =>
  eligibleDate(control.value as string) ? null : { date: true };
export type Phase = 'editing' | 'simulating' | 'ready' | 'confirming' | 'confirmed' | 'failed';
@Injectable()
export class CreditState {
  private readonly api = inject(CreditApi);
  private readonly destroy = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  readonly form = this.fb.nonNullable.group({
    assignorId: ['', Validators.required],
    faceValue: ['', faceValidator],
    dueDate: [addDays(businessDate(), 90), dueValidator],
    type: ['DUPLICATA' as ReceivableType, Validators.required],
    paymentCurrency: ['BRL' as Currency, Validators.required],
  });
  readonly assignors = signal<Assignor[]>([]);
  readonly assignorsError = signal('');
  readonly list = signal<Page<Receivable> | null>(null);
  readonly listLoading = signal(false);
  readonly listError = signal('');
  readonly phase = signal<Phase>('editing');
  readonly simulation = signal<Simulation | null>(null);
  readonly selected = signal<Receivable | null>(null);
  readonly settlement = signal<Settlement | null>(null);
  readonly intent = signal<{ key: string; body: SettlementIntent } | null>(null);
  readonly uncertain = signal(false);
  readonly message = signal('');
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly busy = signal(false);
  private readonly inputs = new Subject<PricingInput | null>();
  private readonly listPages = new Subject<number>();

  constructor() {
    this.inputs
      .pipe(
        switchMap((input) =>
          input
            ? timer(300).pipe(
                switchMap(() => {
                  this.phase.set('simulating');
                  return this.api.simulate(input);
                }),
                catchError((error: unknown) => {
                  this.message.set(problemMessage(error));
                  this.phase.set('failed');
                  this.readFieldErrors(error);
                  return of(null);
                }),
              )
            : of(null),
        ),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((result) => {
        this.simulation.set(result);
        if (result) this.phase.set('ready');
      });
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => this.reprice());
    this.listPages
      .pipe(
        switchMap((page) => {
          this.listLoading.set(true);
          this.listError.set('');
          this.list.set(null);
          return this.api.receivables(page).pipe(
            catchError((error: unknown) => {
              this.listError.set(problemMessage(error));
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((page) => {
        this.list.set(page);
        this.listLoading.set(false);
      });
  }
  initialize() {
    this.loadAssignors();
    this.loadReceivables();
  }
  loadAssignors() {
    this.assignorsError.set('');
    this.api
      .assignors()
      .pipe(
        expand((page) =>
          (page.page + 1) * page.size < page.total ? this.api.assignors(page.page + 1) : EMPTY,
        ),
        reduce((items, page) => [...items, ...page.items], [] as Assignor[]),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (items) => this.assignors.set(items),
        error: (error: unknown) => this.assignorsError.set(problemMessage(error)),
      });
  }
  loadReceivables(page = 0) {
    this.listPages.next(page);
  }
  private input(): PricingInput | null {
    const value = this.form.getRawValue();
    const faceValue = decimalInput(value.faceValue);
    if (!faceValue || !eligibleDate(value.dueDate) || !value.assignorId) return null;
    return {
      faceValue,
      dueDate: value.dueDate,
      type: value.type,
      paymentCurrency: value.paymentCurrency,
    };
  }
  reprice() {
    if (this.intent() || this.phase() === 'confirming' || this.phase() === 'confirmed') return;
    this.simulation.set(null);
    this.message.set('');
    this.fieldErrors.set({});
    this.phase.set('editing');
    this.inputs.next(this.selected()?.status === 'SETTLED' ? null : this.input());
  }
  private readFieldErrors(error: unknown) {
    if (error instanceof HttpErrorResponse)
      this.fieldErrors.set((error.error as ApiProblem | null)?.errors ?? {});
  }
  createAssignor(name: string) {
    if (!name.trim() || name.trim().length > 200 || this.busy()) return;
    this.busy.set(true);
    this.message.set('');
    this.api
      .createAssignor(name.trim())
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (assignor) => {
          this.assignors.update((items) => [assignor, ...items]);
          this.form.controls.assignorId.setValue(assignor.id);
        },
        error: (error: unknown) => this.message.set(problemMessage(error)),
      });
  }
  createReceivable() {
    const input = this.input();
    if (!input || this.busy() || this.selected() || this.intent()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.message.set('');
    this.api
      .createReceivable({ ...input, assignorId: this.form.getRawValue().assignorId })
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (receivable) => {
          this.select(receivable);
          this.loadReceivables();
        },
        error: (error: unknown) => {
          this.message.set(problemMessage(error));
          this.readFieldErrors(error);
          this.loadReceivables();
        },
      });
  }
  select(receivable: Receivable) {
    if (this.intent() || this.phase() === 'confirming' || (this.busy() && this.selected())) return;
    this.inputs.next(null);
    this.simulation.set(null);
    this.settlement.set(null);
    this.phase.set('editing');
    this.message.set('');
    this.selected.set(receivable);
    this.form.patchValue(
      { ...receivable, faceValue: decimalDisplay(receivable.faceValue) },
      { emitEvent: false },
    );
    this.form.disable({ emitEvent: false });
    if (receivable.status === 'SETTLED')
      this.message.set('Recebível já liquidado. Consulte os valores no extrato.');
    else this.reprice();
  }
  newReceivable() {
    if (this.intent() || this.phase() === 'confirming' || this.busy()) return;
    this.selected.set(null);
    this.settlement.set(null);
    this.simulation.set(null);
    this.phase.set('editing');
    this.message.set('');
    this.form.enable({ emitEvent: false });
    this.form.controls.faceValue.setValue('');
  }
  consult(id: string) {
    if (
      !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id) ||
      this.intent() ||
      this.busy()
    ) {
      this.message.set('Informe um identificador UUID válido e conclua a intenção atual.');
      return;
    }
    this.api
      .receivable(id)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (value) => this.select(value),
        error: (error: unknown) => this.message.set(problemMessage(error)),
      });
  }
  confirm() {
    if (this.phase() === 'confirming' || this.phase() === 'confirmed') return;
    const simulation = this.simulation();
    const receivable = this.selected();
    if (
      !receivable ||
      receivable.status !== 'AVAILABLE' ||
      !simulation ||
      (this.phase() !== 'ready' && !this.intent())
    )
      return;
    const intent = this.intent() ?? {
      key: crypto.randomUUID(),
      body: {
        receivableId: receivable.id,
        currency: simulation.paymentCurrency,
        expectedPaymentAmount: simulation.paymentAmount,
        expectedFxRateId: simulation.fxRate?.id ?? null,
      },
    };
    this.intent.set(intent);
    this.phase.set('confirming');
    this.message.set('');
    this.uncertain.set(false);
    this.api
      .settle(intent.body, intent.key)
      .pipe(timeout(15000), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (result) => {
          this.settlement.set(result);
          this.selected.set({ ...receivable, status: 'SETTLED' });
          this.intent.set(null);
          this.phase.set('confirmed');
          this.message.set('Liquidação confirmada.');
          this.loadReceivables();
          this.confirmed.next();
        },
        error: (error: unknown) => {
          this.phase.set('failed');
          const code =
            error instanceof HttpErrorResponse
              ? (error.error as ApiProblem | null)?.code
              : undefined;
          if (code === 'PRICE_CHANGED') {
            this.intent.set(null);
            this.simulation.set(null);
            this.inputs.next(null);
            this.message.set(
              'O preço mudou. Solicite nova simulação e confirme explicitamente o novo valor.',
            );
          } else if (code === 'RECEIVABLE_ALREADY_SETTLED') {
            this.intent.set(null);
            this.simulation.set(null);
            this.selected.set({ ...receivable, status: 'SETTLED' });
            this.message.set('Recebível já liquidado. Consulte o extrato.');
            this.loadReceivables();
            this.confirmed.next();
          } else {
            this.uncertain.set(true);
            this.message.set(
              problemMessage(error) + ' O retry preserva a chave e o corpo originais.',
            );
          }
        },
      });
  }
  readonly confirmed = new Subject<void>();
}
