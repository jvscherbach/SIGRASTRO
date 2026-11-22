import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { duracaoLegivel, formatarData, formatarDataHora } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { DetalheApiService } from './detalhe-api.service';
import { FluxogramaSimplificadoComponent } from './fluxograma-simplificado.component';
import {
  ROTULO_DO_ESTADO_DO_ATO,
  ROTULO_DO_ESTADO_DO_PARTICIPANTE,
  ResponsavelDaPassagem,
  TramitacaoDoRequerimento,
  ehAutomatica,
  passagensVisiveis,
  textoDaVolta,
} from './tramitacao.models';

/** Ids únicos por instância (mais de uma seção na mesma página). */
let instancias = 0;

/**
 * Seção "Tramitação" do detalhe do requerimento (S8, bloco 8; docs/08): o caminho percorrido em lista vertical, com
 * entrada, saída, duração, com quem esteve, as voltas ("2ª passagem: devolvido na validação do parecer"), os atos da
 * emissão, o ramo da correção, a etapa atual e o final. Recolhível; "ocultar etapas automáticas" ligado por padrão (a
 * lista completa fica a um clique). Carrega e falha sozinha: o resto da página não espera nem quebra por ela, e ela não
 * depende de "Onde está agora" (nem o inverso). Nunca mostra id técnico nem CPF (a API não manda CPF; os ids técnicos só
 * vêm para o Administrador e são ignorados). É também o equivalente textual do fluxograma (bloco 9).
 */
@Component({
  selector: 'app-tramitacao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, FluxogramaSimplificadoComponent],
  template: `
    <section class="tram panel" [attr.aria-labelledby]="'tram-titulo-' + uid" [attr.aria-busy]="carregando()">
      <div class="tram-head">
        <h2 [id]="'tram-titulo-' + uid"><app-icone nome="flow" />Tramitação</h2>
        <div class="tram-acoes">
          @if (aberta() && podeImprimir()) {
            <button type="button" class="btn sm" (click)="imprimir()">Imprimir tramitação</button>
          }
          <button type="button" class="btn sm" [attr.aria-expanded]="aberta()" [attr.aria-controls]="'tram-corpo-' + uid"
            (click)="aberta.set(!aberta())">
            {{ aberta() ? 'Recolher' : 'Expandir' }}
          </button>
        </div>
      </div>

      @if (aberta()) {
        <div [id]="'tram-corpo-' + uid" class="tram-corpo">
          @if (carregando()) {
            <div class="tram-esqueleto" role="status">
              <span class="sr-only">Carregando a tramitação do requerimento…</span>
              <span class="osso grande" aria-hidden="true"></span>
              <span class="osso" aria-hidden="true"></span>
              <span class="osso curto" aria-hidden="true"></span>
            </div>
          } @else if (erro()) {
            <div class="tram-aviso-geral">
              <p role="alert">{{ erro() }}</p>
              <button type="button" class="btn sm" (click)="carregar()"><app-icone nome="refresh" />Tentar novamente</button>
            </div>
          } @else if (tramitacao(); as t) {
            @switch (t.estado) {
              @case ('NAO_SUPORTADO') {
                <p class="tram-texto">Fluxo ainda não suportado pelo Rastro.</p>
              }
              @case ('INDISPONIVEL') {
                <div class="tram-aviso-geral">
                  <p role="status">Não foi possível consultar a tramitação agora. O restante da página não foi afetado.</p>
                  <button type="button" class="btn sm" (click)="carregar()"><app-icone nome="refresh" />Tentar novamente</button>
                </div>
              }
              @case ('SEM_INSTANCIA') {
                @if (t.planoB; as b) {
                  <p class="tram-selo" role="note">{{ b.selo }}</p>
                  @if (t.situacaoLicenciamento; as s) {
                    <p class="tram-situacao"><span class="muted">Situação no Licenciamento: </span><b>{{ s.descricao }}</b></p>
                  }
                  @if (b.semDetalhamento) {
                    <p class="tram-texto">Sem detalhamento disponível.</p>
                  }
                  @if (b.etapas.length) {
                    <ol class="tram-lista" aria-label="Linha do tempo aproximada: etapas e datas deduzidas dos eventos do Licenciamento">
                      @for (e of b.etapas; track $index) {
                        <li class="tram-item">
                          <div class="tram-titulo">
                            <b>{{ e.etapa.rotulo }}</b>
                            <span class="tag gray">Aproximado</span>
                          </div>
                          <p class="tram-datas">
                            <span>Entrada <time [attr.datetime]="e.entrada">{{ formatarData(e.entrada) }}</time></span>
                            @if (e.saida) {
                              <span>Saída <time [attr.datetime]="e.saida">{{ formatarData(e.saida) }}</time></span>
                            }
                            <span>{{ e.eventos }} {{ e.eventos === 1 ? 'evento registrado' : 'eventos registrados' }}</span>
                          </p>
                        </li>
                      }
                    </ol>
                  }
                  @if (b.marcos.length) {
                    <ul class="tram-marcos" aria-label="Datas do requerimento">
                      @for (m of b.marcos; track $index) {
                        <li><span class="muted">{{ m.rotulo }}</span>{{ ' ' }}<time [attr.datetime]="m.data">{{ formatarData(m.data) }}</time></li>
                      }
                    </ul>
                  }
                  @if (b.eventosIgnorados > 0) {
                    <p class="tram-nota muted">{{ b.eventosIgnorados }} {{ b.eventosIgnorados === 1 ? 'evento do Licenciamento não reconhecido não aparece' : 'eventos do Licenciamento não reconhecidos não aparecem' }} aqui.</p>
                  }
                  @if (b.eventosTruncados) {
                    <p class="tram-nota muted">Há mais eventos do que o limite consultado; a linha do tempo pode estar incompleta.</p>
                  }
                }
              }
              @default {
                <app-fluxograma-simplificado [tramitacao]="t" />
                <div class="tram-filtro">
                  <label>
                    <input type="checkbox" [checked]="ocultarAutomaticas()" (change)="alternarFiltro($event)" />
                    Ocultar etapas automáticas
                  </label>
                  @if (ocultarAutomaticas() && automaticasOcultas() > 0) {
                    <span class="muted">{{ automaticasOcultas() }} {{ automaticasOcultas() === 1 ? 'etapa automática oculta' : 'etapas automáticas ocultas' }}</span>
                  }
                </div>

                @if (visiveis().length) {
                  <ol class="tram-lista" aria-label="Etapas percorridas, da mais antiga para a mais recente">
                    @for (p of visiveis(); track $index) {
                      <li class="tram-item" [class.correcao]="p.ramo === 'CORRECAO'" [class.atual]="p.atual">
                        <div class="tram-titulo">
                          <b>{{ p.etapa.rotulo }}</b>
                          @if (p.atual) {
                            <span class="tag lime">Etapa atual</span>
                          }
                          @if (p.ramo === 'CORRECAO') {
                            <span class="tag amber">Ramo da correção</span>
                          }
                          @if (p.emParalelo) {
                            <span class="tag blue">Em paralelo</span>
                          }
                          @if (p.processoConcluido) {
                            <span class="tag dark">Processo concluído</span>
                          }
                        </div>
                        @if (textoDaVolta(p); as volta) {
                          <p class="tram-volta">{{ volta }}</p>
                        }
                        <p class="tram-datas">
                          <span>Entrada <time [attr.datetime]="p.entrada">{{ formatarDataHora(p.entrada) }}</time></span>
                          @if (p.saida; as saida) {
                            <span>Saída <time [attr.datetime]="saida">{{ formatarDataHora(saida) }}</time></span>
                            @if (duracao(p.duracaoSegundos); as d) {
                              <span>Duração {{ d }}</span>
                            }
                          } @else {
                            <span>Em andamento</span>
                          }
                        </p>
                        @if (p.participantes.length) {
                          <ul class="tram-pessoas" aria-label="Participantes da etapa">
                            @for (pa of p.participantes; track $index) {
                              <li>
                                <span>{{ quem(pa) }}<span class="sr-only">: </span></span>
                                <span [class]="tomDoParticipante(pa)">{{ rotuloDoParticipante(pa) }}</span>
                              </li>
                            }
                          </ul>
                        } @else if (p.responsaveis.length) {
                          <p class="tram-quem"><span class="muted">Com quem: </span>{{ responsaveisEmTexto(p.responsaveis) }}</p>
                        }
                        @if (p.atos.length) {
                          <ul class="tram-atos" aria-label="Atos da emissão">
                            @for (a of p.atos; track $index) {
                              <li>
                                <span>{{ a.rotulo }}<span class="sr-only">: </span></span>
                                <span [class]="a.ocorreu ? 'tag lime' : 'tag gray'">{{ rotuloDoAto(a.estado) }}</span>
                              </li>
                            }
                          </ul>
                        }
                      </li>
                    }
                  </ol>
                } @else {
                  <p class="tram-texto">Nenhuma etapa registrada ainda.</p>
                }

                @for (c of t.complementacoes; track $index) {
                  <p class="tram-compl">
                    <app-icone nome="info" />
                    Complementação paralela:
                    desde {{ formatarDataHora(c.entrada) }}{{ c.saida ? ' até ' + formatarDataHora(c.saida) : ', em andamento' }}.
                  </p>
                }

                @if (t.final; as f) {
                  <p class="tram-final">
                    <app-icone nome="check" />
                    <b>{{ f.rotulo }}</b>@if (f.encerradoEm) { em <time [attr.datetime]="f.encerradoEm">{{ formatarData(f.encerradoEm) }}</time>}
                    @if (f.tipo === 'FINAL') {
                      <span class="tag dark">Final</span>
                    }
                  </p>
                }
              }
            }

            @if (avisosDe(t).length) {
              <ul class="tram-avisos" aria-label="Avisos sobre a tramitação">
                @for (a of avisosDe(t); track a.codigo) {
                  <li [class]="a.gravidade === 'ATENCAO' ? 'atencao' : 'info'">
                    <app-icone [nome]="a.gravidade === 'ATENCAO' ? 'alert' : 'info'" />
                    <span><span class="sr-only">{{ a.gravidade === 'ATENCAO' ? 'Atenção: ' : 'Informação: ' }}</span>{{ a.texto }}</span>
                  </li>
                }
              </ul>
            }
          }
        </div>
      }
    </section>
  `,
  styles: `
    :host { display: block; margin-bottom: 22px; }
    .tram { min-height: 96px; }
    .tram-head { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 6px 12px; margin-bottom: 10px; }
    .tram-head h2 { display: flex; align-items: center; gap: 10px; }
    .tram-head h2 .icon { color: var(--olive); width: 23px; height: 23px; }
    .tram-acoes { display: flex; flex-wrap: wrap; gap: 8px; }
    .btn:focus-visible, .tram-filtro input:focus-visible { outline: 2px solid var(--forest); outline-offset: 2px; }
    .tram-filtro { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 16px; margin-bottom: 12px; font-size: 13px; }
    .tram-filtro label { display: inline-flex; align-items: center; gap: 8px; cursor: pointer; }
    .tram-lista { list-style: none; margin: 0; padding: 0; display: grid; gap: 0; }
    .tram-item { padding: 12px 0 12px 14px; border-left: 3px solid var(--line); margin-left: 4px; display: grid; gap: 4px; }
    .tram-item.atual { border-left-color: #6cc51a; }
    .tram-item.correcao { margin-left: 22px; border-left-color: var(--amber); }
    .tram-titulo { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 8px; overflow-wrap: anywhere; }
    .tram-volta { margin: 0; font-size: 13px; font-weight: 600; color: var(--amber); }
    .tram-datas { margin: 0; display: flex; flex-wrap: wrap; gap: 2px 14px; font-size: 13px; color: var(--muted); }
    .tram-quem { margin: 0; font-size: 13px; overflow-wrap: anywhere; }
    .tram-pessoas, .tram-atos, .tram-marcos, .tram-avisos { list-style: none; margin: 4px 0 0; padding: 0; display: grid; gap: 6px; }
    .tram-pessoas li, .tram-atos li { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 10px; font-size: 13px; }
    .tram-marcos { margin-top: 12px; font-size: 13px; }
    .tram-compl { display: flex; align-items: flex-start; gap: 8px; font-size: 13px; margin: 12px 0 0; }
    .tram-compl .icon { width: 16px; height: 16px; flex: none; margin-top: 2px; }
    .tram-final { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 8px; margin: 14px 0 0; font-size: 15px; }
    .tram-final .icon { width: 18px; height: 18px; color: var(--forest); }
    .tram-selo { margin: 0 0 10px; padding: 8px 10px; border-radius: 8px; background: var(--amber-bg); color: var(--amber); font-size: 13px; font-weight: 600; }
    .tram-texto { margin: 0; font-size: 14px; }
    .tram-situacao { margin: 0 0 10px; font-size: 14px; }
    .tram-nota { margin: 10px 0 0; font-size: 12px; }
    .tram-avisos { margin-top: 14px; }
    .tram-avisos li { display: flex; align-items: flex-start; gap: 8px; font-size: 13px; padding: 8px 10px; border-radius: 8px; }
    .tram-avisos li.info { background: var(--blue-bg); color: var(--blue); }
    .tram-avisos li.atencao { background: var(--amber-bg); color: var(--amber); }
    .tram-avisos .icon { width: 16px; height: 16px; flex: none; margin-top: 2px; }
    .tram-aviso-geral { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 14px; }
    .tram-aviso-geral p { margin: 0; }
    .tram-esqueleto { display: grid; gap: 10px; }
    .osso { display: block; height: 14px; width: 60%; border-radius: 6px; background: var(--soft); }
    .osso.grande { height: 24px; width: 40%; }
    .osso.curto { width: 30%; }
  `,
})
export class TramitacaoComponent {
  private readonly api = inject(DetalheApiService);

  readonly idRequerimento = input.required<number>();

  protected readonly uid = ++instancias;
  protected readonly tramitacao = signal<TramitacaoDoRequerimento | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly aberta = signal(true);
  /** Padrão ligado (prompt da S8): a lista completa continua a um clique. */
  protected readonly ocultarAutomaticas = signal(true);

  protected readonly visiveis = computed(() => {
    const t = this.tramitacao();
    return t ? passagensVisiveis(t.passagens, this.ocultarAutomaticas()) : [];
  });
  protected readonly automaticasOcultas = computed(() => {
    const t = this.tramitacao();
    return t ? t.passagens.length - passagensVisiveis(t.passagens, true).length : 0;
  });

  protected readonly formatarData = formatarData;
  protected readonly formatarDataHora = formatarDataHora;
  protected readonly textoDaVolta = textoDaVolta;
  protected readonly ehAutomatica = ehAutomatica;

  private assinatura: Subscription | null = null;

  constructor() {
    effect(() => {
      this.idRequerimento();
      untracked(() => this.carregar());
    });
    inject(DestroyRef).onDestroy(() => this.assinatura?.unsubscribe());
  }

  /** Só há o que imprimir com caminho (ATIVO ou ENCERRADO) ou com o histórico aproximado. */
  protected readonly podeImprimir = computed(() => {
    const t = this.tramitacao();
    return !!t && !this.erro() && (t.passagens.length > 0 || t.planoB !== null);
  });

  /**
   * "Imprimir tramitação" (S8, bloco 9): marca o body só durante a impressão para o CSS de `impressao.css` deixar sair
   * apenas esta seção, em preto e branco legível.
   */
  protected imprimir(): void {
    const corpo = document.body;
    const limpar = () => corpo.classList.remove('imprimindo-tramitacao');
    corpo.classList.add('imprimindo-tramitacao');
    window.addEventListener('afterprint', limpar, { once: true });
    window.print();
  }

  /** No plano B o selo fixo já mostra o texto do aviso `HISTORICO_APROXIMADO`: não repete. */
  protected avisosDe(t: TramitacaoDoRequerimento): TramitacaoDoRequerimento['avisos'] {
    return t.planoB ? t.avisos.filter((a) => a.codigo !== 'HISTORICO_APROXIMADO') : t.avisos;
  }

  protected duracao(segundos: number | null): string {
    return duracaoLegivel(segundos);
  }

  protected alternarFiltro(evento: Event): void {
    this.ocultarAutomaticas.set((evento.target as HTMLInputElement).checked);
  }

  /** Nome (se permitido) com o papel; sem nome, só o papel. Nunca CPF. */
  protected quem(r: ResponsavelDaPassagem): string {
    return r.nome ? `${r.nome} · ${r.papel}` : r.papel;
  }

  protected responsaveisEmTexto(responsaveis: ResponsavelDaPassagem[]): string {
    return responsaveis.map((r) => this.quem(r)).join('; ');
  }

  protected rotuloDoParticipante(r: ResponsavelDaPassagem): string {
    return ROTULO_DO_ESTADO_DO_PARTICIPANTE[r.estado];
  }

  protected tomDoParticipante(r: ResponsavelDaPassagem): string {
    return r.estado === 'CONCLUIU' ? 'tag lime' : r.estado === 'PENDENTE' ? 'tag amber' : 'tag gray';
  }

  protected rotuloDoAto(estado: keyof typeof ROTULO_DO_ESTADO_DO_ATO): string {
    return ROTULO_DO_ESTADO_DO_ATO[estado];
  }

  carregar(): void {
    this.assinatura?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.assinatura = this.api.tramitacao(this.idRequerimento()).subscribe({
      next: (t) => {
        this.tramitacao.set(t);
        this.carregando.set(false);
      },
      error: (e: unknown) => {
        this.tramitacao.set(null);
        this.erro.set(
          e instanceof HttpErrorResponse
            ? mensagemDoErro(e)
            : 'Não foi possível consultar a tramitação agora. O restante da página não foi afetado.',
        );
        this.carregando.set(false);
      },
    });
  }
}
