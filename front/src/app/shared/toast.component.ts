import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toast-pilha" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class.erro]="toast.tipo === 'erro'" [attr.role]="toast.tipo === 'erro' ? 'alert' : 'status'">
          {{ toast.texto }}
          @if (toast.acao; as acao) {
            <button type="button" class="toast-acao" (click)="toasts.executar(toast)">{{ acao.rotulo }}</button>
          }
        </div>
      }
    </div>
  `,
})
export class ToastComponent {
  protected readonly toasts = inject(ToastService);
}
