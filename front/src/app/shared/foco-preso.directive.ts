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

/** Diálogos abertos, do mais antigo ao mais recente: só o do topo segura o foco (ex.: pré-visualização sobre a gaveta). */
const ABERTOS: FocoPresoDirective[] = [];

/**
 * Foco de diálogo modal (R2, bloco 14, acessibilidade): ao abrir, o foco vai para o próprio diálogo (o leitor de tela
 * anuncia o rótulo dele e o Esc do diálogo passa a funcionar); Tab e Shift+Tab circulam só dentro dele; ao fechar, o
 * foco volta ao elemento que o abriu. Bloco 15: o foco também não escapa quando o diálogo se atualiza sozinho (ex.: a
 * gaveta troca os botões do rodapé quando o pedido sai de "Enviando" para "Gerando"): se o elemento com foco some ou é
 * desabilitado, ou se o foco vai parar fora do diálogo do topo, ele volta ao primeiro elemento focável do diálogo.
 * Não muda nenhuma regra do componente que o usa.
 */
@Directive({
  selector: '[appFocoPreso]',
  host: {
    tabindex: '-1',
    '(keydown.tab)': 'circular($event, false)',
    '(keydown.shift.tab)': 'circular($event, true)',
    '(document:focusin)': 'aoFocarFora($event)',
  },
})
export class FocoPresoDirective {
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly anterior = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  /** Só depois do foco inicial o diálogo passa a recolocar o foco perdido. */
  private pronto = false;

  constructor() {
    ABERTOS.push(this);
    const observador = typeof MutationObserver === 'undefined' ? null : new MutationObserver(() => this.aoMudar());
    observador?.observe(this.elemento, { childList: true, subtree: true, attributes: true, attributeFilter: ['disabled'] });
    afterNextRender(() => {
      if (!this.elemento.contains(document.activeElement)) this.elemento.focus();
      this.pronto = true;
    });
    inject(DestroyRef).onDestroy(() => {
      observador?.disconnect();
      ABERTOS.splice(ABERTOS.indexOf(this), 1);
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
    const focaveis = this.focaveis();
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

  /** O foco foi para fora (ex.: o Tab partiu do `body` depois de o botão focado sumir). */
  protected aoFocarFora(evento: FocusEvent): void {
    const alvo = evento.target;
    if (!this.noTopo() || !(alvo instanceof Node) || this.elemento.contains(alvo)) return;
    this.recolocar();
  }

  /** O diálogo mudou: se o elemento com foco saiu do DOM (foco no `body`) ou ficou desabilitado, recoloca. */
  private aoMudar(): void {
    if (!this.noTopo() || !this.elemento.isConnected) return;
    const ativo = document.activeElement;
    const perdido = !ativo || ativo === document.body;
    const desabilitado = ativo instanceof HTMLElement && this.elemento.contains(ativo) && ativo.matches(':disabled');
    if (perdido || desabilitado) this.recolocar();
  }

  private noTopo(): boolean {
    return this.pronto && ABERTOS[ABERTOS.length - 1] === this;
  }

  private recolocar(): void {
    (this.focaveis()[0] ?? this.elemento).focus();
  }

  private focaveis(): HTMLElement[] {
    return [...this.elemento.querySelectorAll<HTMLElement>(FOCAVEIS)].filter((e) => e.checkVisibility?.() ?? true);
  }
}
