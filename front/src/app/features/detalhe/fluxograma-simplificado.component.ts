import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CaixaDoFluxograma, montarFluxograma } from './fluxograma.models';
import { TramitacaoDoRequerimento } from './tramitacao.models';

/** Ids únicos por instância (as dicas se ligam à caixa por `aria-describedby`). */
let instancias = 0;

/**
 * Fluxograma simplificado da tramitação (S8, bloco 9; docs/08): HTML, CSS e SVG simples, **sem biblioteca**, em linha
 * reta, só com o que o requerimento já percorreu e a etapa atual destacada (nunca etapas futuras, nunca losango).
 * Uma volta é uma nova caixa com "2ª passagem", sem seta de retorno; a correção fica numa faixa própria; o final é uma
 * bola preta com o rótulo escrito em cima. Em tela estreita a linha quebra, sem rolagem horizontal da página.
 *
 * Acessibilidade: a lista da seção Tramitação é o equivalente textual completo; aqui o conjunto tem `aria-label`
 * resumido, cada caixa é um item de lista focável com a dica (tooltip) ligada por `aria-describedby`, e o estado vem em
 * texto e ícone além da cor. Impressão: formas e texto (borda cheia, grossa ou tracejada), legível em preto e branco.
 * Não renderiza nada sem caminho (plano B, não suportado, indisponível): a seção mostra o texto correspondente.
 */
@Component({
  selector: 'app-fluxograma-simplificado',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet],
  template: `
    @if (fluxo(); as f) {
      @if (f.itens.length) {
        <div class="fluxo" role="group" [attr.aria-label]="f.resumo">
          <ol class="fluxo-linha">
            @for (item of f.itens; track $index; let ultimo = $last; let n = $index) {
              @if (item.tipo === 'caixa') {
                <li class="no">
                  <ng-container [ngTemplateOutlet]="caixa" [ngTemplateOutletContext]="{ c: item.caixa, i: n, j: 0 }" />
                  @if (!ultimo || f.final) {
                    <ng-container [ngTemplateOutlet]="seta" />
                  }
                </li>
              } @else {
                <li class="no faixa">
                  <div class="faixa-quadro">
                    <p class="faixa-rotulo">{{ item.rotulo }}</p>
                    <ol class="faixa-linha" [attr.aria-label]="item.rotulo">
                      @for (c of item.caixas; track $index; let ultimaDaFaixa = $last) {
                        <li class="no">
                          <ng-container [ngTemplateOutlet]="caixa" [ngTemplateOutletContext]="{ c: c, i: n, j: $index }" />
                          @if (!ultimaDaFaixa) {
                            <ng-container [ngTemplateOutlet]="seta" />
                          }
                        </li>
                      }
                    </ol>
                  </div>
                  @if (!ultimo || f.final) {
                    <ng-container [ngTemplateOutlet]="seta" />
                  }
                </li>
              }
            }
            @if (f.final; as fim) {
              <li class="no final">
                <span class="final-rotulo">{{ fim.rotulo }}</span>
                <svg class="bola" viewBox="0 0 28 28" role="img" [attr.aria-label]="'Final: ' + fim.rotulo" focusable="false">
                  <circle cx="14" cy="14" r="12" fill="#000" />
                </svg>
                @if (fim.data) {
                  <span class="final-data muted">{{ fim.data }}</span>
                }
              </li>
            }
          </ol>
        </div>
      }
    }

    <ng-template #seta>
      <svg class="seta" viewBox="0 0 24 12" aria-hidden="true" focusable="false">
        <path d="M0 6h19M14 1l6 5-6 5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </ng-template>

    <ng-template #caixa let-c="c" let-i="i" let-j="j">
      <div [class]="'caixa ' + c.estado" tabindex="0" [attr.aria-describedby]="'fx-' + uid + '-' + i + '-' + j">
        <span class="caixa-rotulo">{{ c.rotulo }}</span>
        @if (c.passagem) {
          <span class="caixa-passagem">{{ c.passagem }}</span>
        }
        @if (c.detalhe) {
          <span class="caixa-detalhe">{{ c.detalhe }}</span>
        }
        <span class="caixa-data">{{ c.entrada }}</span>
        @if (c.financeiro) {
          <span class="caixa-fin">{{ c.financeiro }}</span>
        }
        @if (c.taxaVencida) {
          <span class="caixa-vencida"><span class="icone-estado" aria-hidden="true">!</span>Taxa vencida</span>
        }
        <span class="caixa-estado">
          <span class="icone-estado" aria-hidden="true">{{ icone(c) }}</span>{{ c.textoDoEstado }}@if (c.concluido) {<span> · Processo concluído</span>}
        </span>
        <span class="dica" role="tooltip" [id]="'fx-' + uid + '-' + i + '-' + j">
          @for (linha of c.dica; track $index) {
            <span>{{ linha }}</span>
          }
        </span>
      </div>
    </ng-template>
  `,
  styles: `
    :host { display: block; margin-bottom: 16px; }
    .fluxo { padding: 10px 0 4px; }
    .fluxo-linha, .faixa-linha { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; align-items: stretch; gap: 10px 4px; }
    .no { display: flex; align-items: center; gap: 4px; min-width: 0; }
    .seta { width: 22px; height: 12px; flex: none; color: var(--muted); }
    .caixa { position: relative; display: grid; gap: 2px; width: 148px; max-width: 100%; padding: 8px 10px; border-radius: 10px;
      border: 2px solid var(--forest); background: #fff; color: inherit; font-size: 12px; overflow-wrap: anywhere; }
    .caixa:focus-visible { outline: 3px solid var(--forest); outline-offset: 2px; }
    .caixa.atual { border-width: 4px; background: var(--soft); }
    .caixa.aguardando { border-style: dashed; border-color: var(--amber); background: var(--amber-bg); }
    .caixa-rotulo { font-weight: 700; font-size: 13px; line-height: 1.25; }
    .caixa-passagem { font-weight: 600; text-decoration: underline; }
    .caixa-detalhe, .caixa-data { color: var(--muted); }
    .caixa-fin { font-weight: 600; }
    .caixa-vencida { justify-self: start; margin-top: 2px; padding: 1px 6px; border: 2px solid var(--red); border-radius: 6px; color: var(--red); font-weight: 700; }
    .caixa-estado { margin-top: 2px; font-weight: 600; }
    .icone-estado { display: inline-block; width: 1.3em; }
    .dica { display: none; position: absolute; left: 0; top: calc(100% + 6px); z-index: 5; min-width: 190px; max-width: 260px; padding: 8px 10px;
      border-radius: 8px; background: #1f2a1f; color: #fff; font-size: 12px; font-weight: 400; box-shadow: 0 6px 18px rgba(0, 0, 0, .25); }
    .dica span { display: block; }
    .caixa:hover .dica, .caixa:focus .dica, .caixa:focus-visible .dica { display: block; }
    .faixa-quadro { padding: 6px 8px 8px; border: 2px dotted var(--amber); border-radius: 12px; }
    .faixa-rotulo { margin: 0 0 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .03em; }
    .no.final { flex-direction: column; align-items: center; gap: 2px; padding: 0 6px; }
    .final-rotulo { font-weight: 700; font-size: 13px; text-align: center; max-width: 150px; overflow-wrap: anywhere; }
    .bola { width: 28px; height: 28px; }
    .final-data { font-size: 11px; }
    @media (max-width: 480px) {
      .caixa { width: 132px; }
      .no { flex-wrap: wrap; }
    }
    @media print {
      .caixa { break-inside: avoid; background: #fff !important; }
      .caixa-vencida { border-color: #000; color: #000; border-style: double; border-width: 4px; }
      .dica { display: none !important; }
      .bola circle { fill: #000; }
    }
  `,
})
export class FluxogramaSimplificadoComponent {
  readonly tramitacao = input.required<TramitacaoDoRequerimento>();

  protected readonly uid = ++instancias;
  protected readonly fluxo = computed(() => montarFluxograma(this.tramitacao()));

  /** Ícone do estado (além da cor e do texto): ✓ concluída, ● atual, ◷ aguardando. */
  protected icone(c: CaixaDoFluxograma): string {
    return c.estado === 'concluida' ? '✓' : c.estado === 'atual' ? '●' : '◷';
  }
}
