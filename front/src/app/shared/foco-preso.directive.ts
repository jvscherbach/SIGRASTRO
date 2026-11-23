import { DestroyRef, Directive, ElementRef, afterNextRender, inject } from '@angular/core';

const FOCAVEIS = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type=hidden])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'iframe',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

/**
 * Foco de diálogo modal (R2, bloco 14, acessibilidade): ao abrir, o foco vai para o próprio diálogo (o leitor de tela
 * anuncia o rótulo dele e o Esc do diálogo passa a funcionar); Tab e Shift+Tab circulam só dentro dele; ao fechar, o
 * foco volta ao elemento que o abriu. Não muda nenhuma regra do componente que o usa.
 */
@Directive({
  selector: '[appFocoPreso]',
  host: { tabindex: '-1', '(keydown.tab)': 'circular($event, false)', '(keydown.shift.tab)': 'circular($event, true)' },
})
export class FocoPresoDirective {
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly anterior = document.activeElement instanceof HTMLElement ? document.activeElement : null;

  constructor() {
    afterNextRender(() => {
      if (!this.elemento.contains(document.activeElement)) this.elemento.focus();
    });
    inject(DestroyRef).onDestroy(() => {
      const voltar = this.anterior;
      if (voltar && voltar !== document.body) {
        // depois da remoção do diálogo, para não brigar com outro diálogo que esteja abrindo
        queueMicrotask(() => {
          if (voltar.isConnected) voltar.focus();
        });
      }
    });
  }

  protected circular(evento: Event, paraTras: boolean): void {
    const focaveis = [...this.elemento.querySelectorAll<HTMLElement>(FOCAVEIS)].filter((e) => e.checkVisibility?.() ?? true);
    evento.preventDefault();
    if (focaveis.length === 0) {
      this.elemento.focus();
      return;
    }
    const atual = document.activeElement as HTMLElement | null;
    const indice = atual ? focaveis.indexOf(atual) : -1;
    const proximo = paraTras
      ? indice <= 0 ? focaveis[focaveis.length - 1] : focaveis[indice - 1]
      : indice === -1 || indice === focaveis.length - 1 ? focaveis[0] : focaveis[indice + 1];
    proximo.focus();
  }
}
