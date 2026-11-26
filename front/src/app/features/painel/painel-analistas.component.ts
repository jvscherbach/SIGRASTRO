import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarData, formatarDataHora } from '../../shared/formato';
import { FocoPresoDirective } from '../../shared/foco-preso.directive';
import { IconeComponent } from '../../shared/icone.component';
import { PainelApiService } from './painel-api.service';
import { AnalistaDoPainel, AnalistasDoPainelResposta, RequerimentosDoAnalistaResposta, TipoDeCarga } from './painel.models';

export const ESPERA_DA_BUSCA_MS = 300;

/** Qual analista abrir: o da barra clicada, identificado pelo que a barra mostra (rótulo e tipo); nunca por CPF. */
export interface AnalistaEscolhido {
  rotulo: string;
  tipo: TipoDeCarga;
}

/**
 * Aba lateral "Processos por analista" do painel do Gestor (S11, bloco 9, docs/08). Separada do dashboard: abre por cima,
 * lista os requerimentos de um analista com a etapa, com filtros (analista, etapa, busca por interessado ou número). Lê só o
 * instantâneo do painel pela API, então o total do analista é o da barra. O analista é identificado por um identificador
 * opaco; com os nomes ocultos só há categorias sem nome. Fecha com Esc, clique fora e botão; o foco volta ao elemento que a
 * abriu (o dashboard).
 */
@Component({
  selector: 'app-painel-analistas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, FocoPresoDirective, RouterLink],
  host: { '(document:keydown.escape)': 'fechar.emit()' },
  template: `
    <div class="scrim" (click)="aoClicarNoFundo($event)">
      <section class="drawer painel-analistas" appFocoPreso role="dialog" aria-modal="true" aria-labelledby="painel-analistas-titulo">
        <header class="d-head">
          <div>
            <span class="eyebrow">Painel</span>
            <h2 id="painel-analistas-titulo" style="margin-top: 4px">Processos por analista</h2>
            <p>Requerimentos de cada responsável, com a etapa atual. Os totais são os das barras do painel.</p>
          </div>
          <button type="button" class="icon-btn" aria-label="Fechar" (click)="fechar.emit()"><app-icone nome="close" /></button>
        </header>

        <div class="d-body">
          @if (carregandoAnalistas()) {
            <p class="muted" role="status">Carregando os analistas…</p>
          } @else if (erroAnalistas()) {
            <div class="alert" role="alert" style="margin: 0 0 12px"><app-icone nome="alert" /><span>{{ erroAnalistas() }}</span></div>
            <button type="button" class="btn sm" (click)="carregarAnalistas()"><app-icone nome="refresh" />Tentar novamente</button>
          } @else if (base(); as b) {
            @if (b.estado === 'PREPARANDO') {
              <p class="painel-selo" role="status"><app-icone nome="clock" /><span>O painel ainda está preparando a primeira leitura. Tente de novo em instantes.</span></p>
            } @else if (b.estado === 'INDISPONIVEL') {
              <p class="painel-selo erro" role="alert"><app-icone nome="alert" /><span>Painel indisponível: ainda não houve uma leitura com sucesso.</span></p>
            } @else {
              @if (b.estado === 'DESATUALIZADO') {
                <p class="painel-selo aviso" role="status">
                  <app-icone nome="alert" /><span>Última leitura falhou{{ b.falhouEm ? ' em ' + formatarDataHora(b.falhouEm) : '' }}; mostrando a de {{ formatarDataHora(b.geradoEm) }}.</span>
                </p>
              }
              @if (b.nomesOcultos) {
                <p class="painel-selo" role="status"><app-icone nome="lock" /><span>Nomes ocultos por configuração</span></p>
              }
              @if (b.truncado) {
                <p class="painel-selo aviso" role="status"><app-icone nome="alert" /><span>{{ avisoDeTruncado() }}</span></p>
              }

              <div class="painel-analistas-filtros">
                <label class="campo-rotulado">
                  <span>Analista</span>
                  <select aria-label="Analista" (change)="escolherAnalista($event)">
                    @for (a of b.analistas; track a.analista) {
                      <option [value]="a.analista" [selected]="a.analista === analistaAtual()">{{ a.rotulo }} ({{ a.total }})</option>
                    }
                  </select>
                </label>
                <label class="campo-rotulado">
                  <span>Etapa</span>
                  <select aria-label="Etapa" (change)="escolherEtapa($event)">
                    <option value="" [selected]="etapa() === ''">Todas as etapas</option>
                    @for (e of b.etapas; track e) {
                      <option [value]="e" [selected]="e === etapa()">{{ e }}</option>
                    }
                  </select>
                </label>
                <div class="search">
                  <app-icone nome="search" />
                  <input
                    type="search"
                    aria-label="Buscar por nome do interessado ou número"
                    placeholder="Interessado ou número…"
                    maxlength="100"
                    [value]="busca()"
                    (input)="digitarBusca($event)"
                  />
                </div>
              </div>

              @if (b.analistas.length === 0) {
                <p class="muted" role="status">Nenhum analista com requerimentos na fila.</p>
              } @else if (erroLista()) {
                <div class="alert" role="alert" style="margin: 0 0 12px"><app-icone nome="alert" /><span>{{ erroLista() }}</span></div>
              } @else if (lista(); as l) {
                <p class="painel-analistas-contagem" role="status" aria-live="polite">
                  <b>{{ l.rotulo }}</b>: {{ l.total }} {{ l.total === 1 ? 'requerimento' : 'requerimentos' }} (o total da barra)
                  @if (l.quantidade !== l.total) {
                    · {{ l.quantidade }} com os filtros
                  }
                </p>
                @if (l.limitada) {
                  <p class="painel-selo aviso" role="status"><app-icone nome="info" /><span>Mostrando os 1000 primeiros. Use os filtros para ver os demais.</span></p>
                }
                @if (l.requerimentos.length === 0) {
                  <p class="muted">Nenhum requerimento com esses filtros.</p>
                } @else {
                  <ul class="painel-analistas-lista" aria-label="Requerimentos do analista">
                    @for (r of l.requerimentos; track r.id) {
                      <li>
                        <a [routerLink]="['/consulta/requerimentos', r.id]" (click)="fechar.emit()"><b>{{ r.numero ?? 'Sem número' }}</b></a>
                        <span class="caixa-interessado">{{ r.interessado ?? 'Interessado não informado' }}</span>
                        <span class="caixa-meta">{{ r.etapas.join(' · ') }}</span>
                        <span class="caixa-meta">Protocolo: {{ r.protocolo ? formatarData(r.protocolo) : 'sem data' }}</span>
                      </li>
                    }
                  </ul>
                }
              } @else if (carregandoLista()) {
                <p class="muted" role="status">Carregando a lista…</p>
              }
            }
          }
        </div>

        <footer class="d-foot">
          <button type="button" class="btn quiet" (click)="fechar.emit()">Fechar</button>
        </footer>
      </section>
    </div>
  `,
})
export class PainelAnalistasComponent implements OnInit {
  private readonly api = inject(PainelApiService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  /** O analista da barra clicada (vazio: o primeiro da lista). */
  readonly escolhido = input<AnalistaEscolhido | null>(null);
  readonly fechar = output<void>();

  protected readonly carregandoAnalistas = signal(true);
  protected readonly erroAnalistas = signal('');
  protected readonly base = signal<AnalistasDoPainelResposta | null>(null);
  protected readonly analistaAtual = signal('');
  protected readonly etapa = signal('');
  protected readonly busca = signal('');
  protected readonly lista = signal<RequerimentosDoAnalistaResposta | null>(null);
  protected readonly carregandoLista = signal(false);
  protected readonly erroLista = signal('');

  protected readonly formatarData = formatarData;
  protected readonly formatarDataHora = formatarDataHora;
  protected readonly avisoDeTruncado = computed(
    () =>
      this.base()?.avisos.find((a) => a.codigo === 'TRUNCADO')?.texto ??
      'O painel mostra só os primeiros requerimentos do ano: a lista está parcial.',
  );

  private assinaturaBase: Subscription | null = null;
  private assinaturaLista: Subscription | null = null;
  private esperaDaBusca: ReturnType<typeof setTimeout> | null = null;
  /** Um 404 relê os analistas uma vez só (evita laço se a chave continuar inválida). */
  private jaRelido = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.assinaturaBase?.unsubscribe();
      this.assinaturaLista?.unsubscribe();
      if (this.esperaDaBusca) {
        clearTimeout(this.esperaDaBusca);
      }
      this.anfitriao.remove();
    });
  }

  ngOnInit(): void {
    // Como as outras gavetas: sai da folha da tela e fica acima da barra do topo.
    document.body.appendChild(this.anfitriao);
    this.carregarAnalistas();
  }

  protected carregarAnalistas(): void {
    this.assinaturaBase?.unsubscribe();
    this.carregandoAnalistas.set(true);
    this.erroAnalistas.set('');
    this.assinaturaBase = this.api.analistas().subscribe({
      next: (resposta) => {
        this.base.set(resposta);
        this.carregandoAnalistas.set(false);
        this.analistaAtual.set(this.inicial(resposta.analistas));
        if (this.analistaAtual()) {
          this.carregarLista();
        }
      },
      error: (e: unknown) => {
        this.carregandoAnalistas.set(false);
        this.erroAnalistas.set(e instanceof HttpErrorResponse ? mensagemDoErro(e) : 'Não foi possível carregar os analistas.');
      },
    });
  }

  protected escolherAnalista(evento: Event): void {
    this.analistaAtual.set((evento.target as HTMLSelectElement).value);
    this.carregarLista();
  }

  protected escolherEtapa(evento: Event): void {
    this.etapa.set((evento.target as HTMLSelectElement).value);
    this.carregarLista();
  }

  protected digitarBusca(evento: Event): void {
    this.busca.set((evento.target as HTMLInputElement).value);
    if (this.esperaDaBusca) {
      clearTimeout(this.esperaDaBusca);
    }
    this.esperaDaBusca = setTimeout(() => this.carregarLista(), ESPERA_DA_BUSCA_MS);
  }

  protected aoClicarNoFundo(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.fechar.emit();
    }
  }

  private inicial(analistas: AnalistaDoPainel[]): string {
    const alvo = this.escolhido();
    const achado = alvo ? analistas.find((a) => a.rotulo === alvo.rotulo && a.tipo === alvo.tipo) : undefined;
    return (achado ?? analistas[0])?.analista ?? '';
  }

  private carregarLista(): void {
    this.assinaturaLista?.unsubscribe();
    this.carregandoLista.set(true);
    this.erroLista.set('');
    this.assinaturaLista = this.api.requerimentosDoAnalista(this.analistaAtual(), this.etapa(), this.busca()).subscribe({
      next: (resposta) => {
        this.lista.set(resposta);
        this.carregandoLista.set(false);
        this.jaRelido = false;
      },
      error: (e: unknown) => {
        this.carregandoLista.set(false);
        this.lista.set(null);
        // 404: o instantâneo foi trocado (ex.: os nomes passaram a ser ocultos): relê os analistas.
        if (e instanceof HttpErrorResponse && e.status === 404 && !this.jaRelido) {
          this.jaRelido = true;
          this.carregarAnalistas();
          return;
        }
        this.erroLista.set(e instanceof HttpErrorResponse ? mensagemDoErro(e) : 'Não foi possível carregar a lista.');
      },
    });
  }
}
