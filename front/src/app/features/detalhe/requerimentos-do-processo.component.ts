import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { IconeComponent } from '../../shared/icone.component';
import { ConsultaApiService } from '../consulta/consulta-api.service';
import { ResultadoBusca } from '../consulta/consulta.models';
import { OndeEstaAgoraComponent } from './onde-esta-agora.component';

/** Requerimentos listados por processo (o back-end aceita até 100 por número de processo). */
const MAXIMO_POR_PROCESSO = 100;

/**
 * Outros requerimentos do mesmo processo (1:N), cada um com a posição resumida e um ponto de estado, expansível para a
 * posição completa (S7, bloco 9; docs/08). A lista vem da busca por número de processo (com o RN07 do back-end: o
 * Operador só recebe os dele). Cada posição carrega e falha sozinha, pela fila com limite de consultas simultâneas.
 * Sem outros requerimentos, a seção não aparece.
 */
@Component({
  selector: 'app-requerimentos-do-processo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconeComponent, OndeEstaAgoraComponent],
  template: `
    @if (falhou()) {
      <section class="rdp panel" aria-label="Outros requerimentos deste processo">
        <p class="muted" role="status">Não foi possível listar os outros requerimentos deste processo.</p>
      </section>
    } @else if (outros().length) {
      <section class="rdp panel" aria-labelledby="rdp-titulo">
        <div class="rdp-head">
          <h2 id="rdp-titulo"><app-icone nome="layers" />Outros requerimentos deste processo</h2>
          <span class="muted">{{ outros().length }} {{ outros().length === 1 ? 'requerimento' : 'requerimentos' }}</span>
        </div>
        <ul class="rdp-lista">
          @for (r of outros(); track r.id) {
            <li>
              <a class="rdp-numero" [routerLink]="['/consulta/requerimentos', r.id]">Requerimento {{ r.numeroRequerimento }}</a>
              <app-onde-esta-agora modo="resumido" [idRequerimento]="r.id" />
            </li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .rdp { margin-bottom: 22px; }
    .rdp-head { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 6px 12px; margin-bottom: 10px; }
    .rdp-head h2 { display: flex; align-items: center; gap: 10px; }
    .rdp-head h2 .icon { color: var(--trail); width: 23px; height: 23px; }
    .rdp-lista { list-style: none; margin: 0; padding: 0; display: grid; gap: 0; }
    .rdp-lista > li { padding: 10px 0; border-top: 1px solid var(--line); display: grid; gap: 4px; }
    .rdp-lista > li:first-child { border-top: 0; }
    .rdp-numero { font-weight: 650; font-size: 13px; }
  `,
})
export class RequerimentosDoProcessoComponent {
  private readonly consulta = inject(ConsultaApiService);

  readonly numeroProcesso = input.required<string>();
  readonly idAtual = input.required<number>();

  protected readonly outros = signal<ResultadoBusca[]>([]);
  protected readonly falhou = signal(false);
  private assinatura: Subscription | null = null;

  constructor() {
    effect(() => {
      const numero = this.numeroProcesso();
      const atual = this.idAtual();
      untracked(() => this.listar(numero, atual));
    });
    inject(DestroyRef).onDestroy(() => this.assinatura?.unsubscribe());
  }

  private listar(numero: string, atual: number): void {
    this.assinatura?.unsubscribe();
    this.outros.set([]);
    this.falhou.set(false);
    this.assinatura = this.consulta
      .buscar({ tipo: 'PROCESSO', numero, pagina: 0, tamanho: MAXIMO_POR_PROCESSO })
      .subscribe({
        next: (pagina) => this.outros.set(pagina.conteudo.filter((r) => r.id !== atual)),
        // 403 ou 404 depois que o detalhe abriu: não há outros visíveis para este usuário, nada a mostrar.
        error: (e: { status?: number }) => this.falhou.set(e?.status !== 403 && e?.status !== 404),
      });
  }
}
