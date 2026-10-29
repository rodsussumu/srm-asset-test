import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Session, problemMessage } from './core/session';
import { Workspace } from './workspace/workspace';
@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule, Workspace],
  templateUrl: './app.html',
})
export class App {
  readonly session = inject(Session);
  readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });
  readonly busy = signal(false);
  readonly error = signal('');
  constructor() {
    this.session
      .initialize()
      .pipe(
        takeUntilDestroyed(),
        finalize(() => this.session.ready.set(true)),
      )
      .subscribe({ error: () => this.session.expired() });
  }
  login() {
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    const { username, password } = this.form.getRawValue();
    const request = this.session.login(username, password);
    this.form.controls.password.reset();
    request
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({ error: (error: unknown) => this.error.set(problemMessage(error)) });
  }
  logout() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.session
      .logout()
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({ error: (error: unknown) => this.error.set(problemMessage(error)) });
  }
}
