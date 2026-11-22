import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarDataHora, tempoDesde } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { FilaDePosicoes } from './fila-de-posicoes.service';
import {
  EtapaDaPosicao,
  PosicaoDoRequerimento,
  RESUMO_DE_ERRO,
  ROTULO_DO_TIPO,
  ResumoDaPosicao,
  resumirPosicao,
} from './posicao.models';

/** Ids únicos por instância (várias posições na mesma página: processo e busca). */
let instancias = 0;

/**
 * Bloco "Onde está agora" do detalhe do requerimento (S7, bloco 8; docs/08): etapa atual, desde quando, com quem,
 * "+N em paralelo", complementação, "x de y" da multi-instância e avisos. Carrega e falha sozinho: o resto da página
 * não espera nem quebra por ele. A altura mínima é reservada desde o esqueleto, para não empurrar os documentos.
 * Nunca mostra id técnico nem CPF (a API não manda CPF; os ids técnicos só vêm para o Administrador e são ignorados).
 */
@Component({
  selector: 'app-onde-esta-agora',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, NgTemplateOutlet],
  template: `
    @if (modo() === 'completo') {
      <section class="onde panel" [attr.aria-labelledby]="'onde-titulo-' + uid" [attr.aria-busy]="carregando()">
        <div class="onde-head">
          <h2 [id]="'onde-titulo-' + uid"><app-icone nome="flow" />Onde está agora</h2>
          @if (posicao()?.modelo; as modelo) {
            <span class="muted onde-modelo">{{ modelo }}</span>
          }
        </div>
        <ng-container [ngTemplateOutlet]="corpo" />
      </section>
    } @else {
      <div class="onde-linha" [attr.aria-busy]="carregando()">
        @if (carregando()) {
          <span class="ponto neutro" aria-hidden="true"></span>
          <span class="muted" role="status">Carregando a etapa…</span>
        } @else if (resumo(); as r) {
          <span [class]="'ponto ' + r.ponto" role="img" [attr.aria-label]="'Estado: ' + r.estado"></span>
          <span class="onde-linha-texto">
            <b>{{ r.texto }}</b>
            @if (r.comQuem) {
              <span class="muted"> · {{ r.comQuem }}</span>
            }
          </span>
          @if (r.tipo) {
            <span class="tag gray">{{ r.tipo }}</span>
          }
          @if (r.paralelos) {
            <span class="tag blue">+{{ r.paralelos }} em paralelo</span>
          }
          @if (r.ponto === 'erro') {
            <button type="button" class="btn sm quiet" aria-label="Consultar a etapa de novo" title="Consultar a etapa de novo" (click)="carregar()">
              <app-icone nome="refresh" />
            </button>
          }
          @if (modo() === 'resumido') {
            <button type="button" class="btn sm" [attr.aria-expanded]="expandido()" [attr.aria-controls]="'onde-corpo-' + uid"
              (click)="expandido.set(!expandido())">
              {{ expandido() ? 'Ver menos' : 'Ver posição completa' }}
            </button>
          }
        }
      </div>
      @if (modo() === 'resumido' && expandido()) {
        <div class="onde-corpo-resumido" [id]="'onde-corpo-' + uid">
          <ng-container [ngTemplateOutlet]="corpo" />
        </div>
      }
    }

    <ng-template #corpo>
      @if (carregando()) {
        <div class="onde-esqueleto" role="status">
          <span class="sr-only">Carregando a posição do requerimento…</span>
          <span class="osso grande" aria-hidden="true"></span>
          <span class="osso" aria-hidden="true"></span>
          <span class="osso curto" aria-hidden="true"></span>
        </div>
      } @else if (erro()) {
        <div class="onde-aviso-geral">
          <p role="alert">{{ erro() }}</p>
          <button type="button" class="btn sm" (click)="carregar()"><app-icone nome="refresh" />Tentar novamente</button>
        </div>
      } @else if (posicao(); as p) {
        @switch (p.estado) {
          @case ('ATIVO') {
            @if (p.etapaAtual; as etapa) {
              <div class="onde-principal">
                <p class="onde-etapa">{{ etapa.rotulo }}</p>
                @if (etapa.tipo) {
                  <span class="tag gray">{{ rotuloDoTipo(etapa) }}</span>
                }
                @if (p.emParalelo.length) {
                  <button type="button" class="tag blue onde-paralelo" [attr.aria-expanded]="paralelosAbertos()"
                    [attr.aria-controls]="'onde-paralelos-' + uid" (click)="paralelosAbertos.set(!paralelosAbertos())">
                    +{{ p.emParalelo.length }} em paralelo
                  </button>
                }
                @if (p.complementacao) {
                  <span class="tag amber">Complementação em andamento</span>
                }
              </div>
              <dl class="onde-linhas">
                <div>
                  <dt>Desde</dt>
                  <dd>
                    @if (etapa.desde) {
                      <time [attr.datetime]="etapa.desde" [attr.title]="formatarDataHora(etapa.desde)">{{ tempoDesde(etapa.desde) }}</time>
                      <span class="sr-only"> (em {{ formatarDataHora(etapa.desde) }})</span>
                      @if (etapa.desdeUltimaEntrada) {
                        <span class="muted"> · desde a última entrada nesta etapa</span>
                      }
                    } @else {
                      <span class="muted">Data não informada</span>
                    }
                  </dd>
                </div>
                <div>
                  <dt>Com quem</dt>
                  <dd>{{ etapa.responsavel?.texto || 'Equipe responsável' }}</dd>
                </div>
                @if (p.multiInstancia; as mi) {
                  <div>
                    <dt>Andamento</dt>
                    <dd>
                      @if (mi.total !== null && mi.concluidos !== null) {
                        {{ mi.concluidos }} de {{ mi.total }} concluídos
                      } @else {
                        {{ mi.ativas }} {{ mi.ativas === 1 ? 'pendente' : 'pendentes' }}
                      }
                    </dd>
                  </div>
                }
              </dl>
              @if (p.multiInstancia?.participantes?.length) {
                <ul class="onde-participantes" aria-label="Participantes da etapa">
                  @for (pa of p.multiInstancia!.participantes; track $index) {
                    <li>
                      <span>{{ pa.nome || pa.papel }}<span class="sr-only">: </span></span>
                      <span [class]="pa.concluiu ? 'tag lime' : 'tag gray'">{{ pa.concluiu ? 'Concluiu' : 'Pendente' }}</span>
                    </li>
                  }
                  @if (p.multiInstancia!.maisParticipantes > 0) {
                    <li class="muted">e mais {{ p.multiInstancia!.maisParticipantes }}</li>
                  }
                </ul>
              }
              @if (paralelosAbertos() && p.emParalelo.length) {
                <ul [id]="'onde-paralelos-' + uid" class="onde-lista" aria-label="Etapas em paralelo">
                  @for (e of p.emParalelo; track $index) {
                    <li>
                      <b>{{ e.rotulo }}</b>
                      @if (e.tipo) {
                        <span class="tag gray">{{ rotuloDoTipo(e) }}</span>
                      }
                      <span class="muted">{{ e.responsavel?.texto || 'Equipe responsável' }}</span>
                      @if (e.desde) {
                        <time class="muted" [attr.datetime]="e.desde" [attr.title]="formatarDataHora(e.desde)">{{ tempoDesde(e.desde) }}</time>
                        <span class="sr-only"> (em {{ formatarDataHora(e.desde) }})</span>
                      }
                    </li>
                  }
                </ul>
              }
            } @else {
              <p class="muted">O fluxo está ativo, mas a etapa atual não pôde ser identificada.</p>
            }
          }
          @case ('ENCERRADO') {
            <div class="onde-principal">
              <p class="onde-etapa">{{ p.final?.rotulo || 'Encerrado' }}</p>
              <span class="tag dark">Final</span>
            </div>
            @if (p.final?.encerradoEm; as fim) {
              <p class="muted">Encerrado em {{ formatarDataHora(fim) }}</p>
            }
          }
          @case ('CONCLUIDO') {
            <div class="onde-principal">
              <p class="onde-etapa">{{ p.final?.rotulo || 'Concluído' }}</p>
              <span class="tag lime">Processo concluído</span>
            </div>
            @if (p.final?.encerradoEm; as desde) {
              <p class="muted">Concluído desde {{ formatarDataHora(desde) }}</p>
            }
          }
          @case ('SEM_INSTANCIA') {
            <p class="onde-texto">Histórico detalhado indisponível para este requerimento.</p>
          }
          @case ('NAO_SUPORTADO') {
            <p class="onde-texto">Fluxo ainda não suportado pelo Rastro.</p>
          }
          @default {
            <div class="onde-aviso-geral">
              <p role="status">Não foi possível consultar o andamento agora. O restante da página não foi afetado.</p>
              <button type="button" class="btn sm" (click)="carregar()"><app-icone nome="refresh" />Tentar novamente</button>
            </div>
          }
        }

        @if (comTaxaVencida(p) && p.situacaoLicenciamento; as s) {
          <p class="onde-situacao">
            <span class="muted">Situação no Licenciamento: </span><b>{{ s.descricao }}</b>
          </p>
        }

        @if (p.avisos.length) {
          <ul class="onde-avisos" aria-label="Avisos sobre a posição">
            @for (a of p.avisos; track a.codigo) {
              <li [class]="a.gravidade === 'ATENCAO' ? 'atencao' : 'info'">
                <app-icone [nome]="a.gravidade === 'ATENCAO' ? 'alert' : 'info'" />
                <span><span class="sr-only">{{ a.gravidade === 'ATENCAO' ? 'Atenção: ' : 'Informação: ' }}</span>{{ a.texto }}</span>
              </li>
            }
          </ul>
        }
      }
    </ng-template>
  `,
  host: { '[class.completo]': "modo() === 'completo'" },
  styles: `
    :host { display: block; }
    :host(.completo) { margin-bottom: 22px; }
    .onde-linha { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; font-size: 13px; min-height: 32px; }
    .onde-linha-texto { overflow-wrap: anywhere; }
    .ponto { display: inline-block; width: 10px; height: 10px; border-radius: 50%; flex: none; background: var(--muted); }
    .ponto.ativo { background: #6cc51a; }
    .ponto.atencao { background: var(--amber); }
    .ponto.encerrado { background: var(--forest); }
    .ponto.erro { background: var(--red); }
    .onde-corpo-resumido { margin-top: 10px; padding: 12px 14px; border: 1px solid var(--line); border-radius: 10px; }
    .onde { min-height: 150px; }
    .onde-head { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 6px 12px; margin-bottom: 10px; }
    .onde-head h2 { display: flex; align-items: center; gap: 10px; }
    .onde-head h2 .icon { color: var(--olive); width: 23px; height: 23px; }
    .onde-modelo { font-size: 12px; }
    .onde-principal { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .onde-etapa { font-size: 20px; font-weight: 700; color: var(--forest); margin: 0; overflow-wrap: anywhere; }
    .onde-paralelo { border: 0; cursor: pointer; font: inherit; font-size: 11px; font-weight: 600; }
    .onde-paralelo:focus-visible, .btn:focus-visible { outline: 2px solid var(--forest); outline-offset: 2px; }
    .onde-linhas { display: flex; flex-wrap: wrap; gap: 6px 28px; margin: 10px 0 0; }
    .onde-linhas dt { font-size: 11px; color: var(--muted); }
    .onde-linhas dd { margin: 0; font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }
    .onde-participantes, .onde-lista, .onde-avisos { list-style: none; margin: 10px 0 0; padding: 0; display: grid; gap: 6px; }
    .onde-participantes li, .onde-lista li { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; font-size: 13px; }
    .onde-avisos li { display: flex; align-items: flex-start; gap: 8px; font-size: 13px; padding: 8px 10px; border-radius: 8px; }
    .onde-avisos li.info { background: var(--blue-bg); color: var(--blue); }
    .onde-avisos li.atencao { background: var(--amber-bg); color: var(--amber); }
    .onde-avisos .icon { width: 16px; height: 16px; flex: none; margin-top: 2px; }
    .onde-texto { margin: 0; font-size: 14px; }
    .onde-situacao { margin: 10px 0 0; font-size: 14px; overflow-wrap: anywhere; }
    .onde-aviso-geral { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 14px; }
    .onde-aviso-geral p { margin: 0; }
    .onde-esqueleto { display: grid; gap: 10px; }
    .osso { display: block; height: 14px; width: 60%; border-radius: 6px; background: var(--soft); }
    .osso.grande { height: 24px; width: 40%; }
    .osso.curto { width: 30%; }
  `,
})
export class OndeEstaAgoraComponent {
  private readonly fila = inject(FilaDePosicoes);

  readonly idRequerimento = input.required<number>();
  /**
   * `completo`: o bloco inteiro (detalhe do requerimento). `resumido`: uma linha (ponto de estado, etapa, com quem,
   * selo) que abre o bloco completo (outros requerimentos do processo). `celula`: só a linha (coluna da busca).
   */
  readonly modo = input<'completo' | 'resumido' | 'celula'>('completo');

  protected readonly uid = ++instancias;
  protected readonly posicao = signal<PosicaoDoRequerimento | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly paralelosAbertos = signal(false);
  protected readonly expandido = signal(false);
  protected readonly resumo = computed<ResumoDaPosicao | null>(() => {
    const p = this.posicao();
    return this.erro() ? RESUMO_DE_ERRO : p ? resumirPosicao(p) : null;
  });

  protected readonly formatarDataHora = formatarDataHora;
  protected readonly tempoDesde = tempoDesde;

  private assinatura: Subscription | null = null;

  constructor() {
    effect(() => {
      this.idRequerimento();
      untracked(() => this.carregar());
    });
    inject(DestroyRef).onDestroy(() => this.assinatura?.unsubscribe());
  }

  /**
   * S9, bloco 9: com o aviso "Taxa vencida" (o SIGA ainda não marca a taxa como vencida), a situação do Licenciamento
   * aparece ao lado, **como está**, para o aviso fazer sentido; nunca troca o texto da situação.
   */
  protected comTaxaVencida(p: PosicaoDoRequerimento): boolean {
    return p.avisos.some((a) => a.codigo === 'TAXA_VENCIDA');
  }

  protected rotuloDoTipo(etapa: EtapaDaPosicao): string {
    return etapa.tipo ? ROTULO_DO_TIPO[etapa.tipo] : '';
  }

  carregar(): void {
    this.assinatura?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.assinatura = this.fila.posicao(this.idRequerimento()).subscribe({
      next: (p) => {
        this.posicao.set(p);
        this.carregando.set(false);
      },
      error: (e: unknown) => {
        this.posicao.set(null);
        this.erro.set(
          e instanceof HttpErrorResponse
            ? mensagemDoErro(e)
            : 'Não foi possível consultar o andamento agora. O restante da página não foi afetado.',
        );
        this.carregando.set(false);
      },
    });
  }
}
