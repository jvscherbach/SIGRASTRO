import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarDataHora, tempoDesde } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { DetalheApiService } from './detalhe-api.service';
import { EtapaDaPosicao, PosicaoDoRequerimento, ROTULO_DO_TIPO } from './posicao.models';

/**
 * Bloco "Onde está agora" do detalhe do requerimento (S7, bloco 8; docs/08): etapa atual, desde quando, com quem,
 * "+N em paralelo", complementação, "x de y" da multi-instância e avisos. Carrega e falha sozinho: o resto da página
 * não espera nem quebra por ele. A altura mínima é reservada desde o esqueleto, para não empurrar os documentos.
 * Nunca mostra id técnico nem CPF (a API não manda CPF; os ids técnicos só vêm para o Administrador e são ignorados).
 */
@Component({
  selector: 'app-onde-esta-agora',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  template: `
    <section class="onde panel" aria-labelledby="onde-titulo" [attr.aria-busy]="carregando()">
      <div class="onde-head">
        <h2 id="onde-titulo"><app-icone nome="flow" />Onde está agora</h2>
        @if (posicao()?.modelo; as modelo) {
          <span class="muted onde-modelo">{{ modelo }}</span>
        }
      </div>

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
                    aria-controls="onde-paralelos" (click)="paralelosAbertos.set(!paralelosAbertos())">
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
                <ul id="onde-paralelos" class="onde-lista" aria-label="Etapas em paralelo">
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
    </section>
  `,
  styles: `
    :host { display: block; margin-bottom: 22px; }
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
    .onde-aviso-geral { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 14px; }
    .onde-aviso-geral p { margin: 0; }
    .onde-esqueleto { display: grid; gap: 10px; }
    .osso { display: block; height: 14px; width: 60%; border-radius: 6px; background: var(--soft); }
    .osso.grande { height: 24px; width: 40%; }
    .osso.curto { width: 30%; }
  `,
})
export class OndeEstaAgoraComponent {
  private readonly api = inject(DetalheApiService);

  readonly idRequerimento = input.required<number>();

  protected readonly posicao = signal<PosicaoDoRequerimento | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly paralelosAbertos = signal(false);

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

  protected rotuloDoTipo(etapa: EtapaDaPosicao): string {
    return etapa.tipo ? ROTULO_DO_TIPO[etapa.tipo] : '';
  }

  carregar(): void {
    this.assinatura?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.assinatura = this.api.posicao(this.idRequerimento()).subscribe({
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
