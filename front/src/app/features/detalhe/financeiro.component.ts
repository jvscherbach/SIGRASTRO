import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarData, formatarReais } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { DetalheApiService } from './detalhe-api.service';
import {
  FinanceiroDoRequerimento,
  LancamentoFinanceiro,
  ROTULO_DA_SITUACAO,
  SELO_DO_RESUMO,
  nomeDoLancamento,
  tomDaSituacao,
} from './financeiro.models';

/** Ids únicos por instância (mais de uma seção na mesma página). */
let instancias = 0;

/**
 * Seção "Taxas e pagamentos" do detalhe do requerimento (S9, bloco 8; docs/08): o resumo financeiro (selo com texto e
 * ícone) e os lançamentos em ordem de emissão (taxa, situação, emitida, vence, paga, DAR, valor e desconto), as isenções
 * ("Isento de Taxa em dd/mm/aaaa", com a nota "data a confirmar" quando a data não é a do lançamento) e os avisos, entre
 * eles "Taxa vencida em dd/mm/aaaa".
 *
 * <p>A situação é a do Licenciamento, como está: a "taxa vencida por data" é uma marca à parte, ao lado da situação. Carrega
 * e falha sozinha: o resto da página não espera nem quebra por ela, e ela não depende da tramitação (nem o inverso). Valor
 * e desconto só existem na resposta com o parâmetro de valores ligado; sem eles, a coluna some. Nunca mostra id técnico,
 * CPF nem chave de arquivo (a API não os manda). Em tela estreita a tabela vira cartões, sem rolagem horizontal.
 */
@Component({
  selector: 'app-financeiro',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  template: `
    <section class="fin panel" [attr.aria-labelledby]="'fin-titulo-' + uid" [attr.aria-busy]="carregando()">
      <div class="fin-head">
        <h2 [id]="'fin-titulo-' + uid"><app-icone nome="coins" />Taxas e pagamentos</h2>
        <button type="button" class="btn sm" [attr.aria-expanded]="aberta()" [attr.aria-controls]="'fin-corpo-' + uid"
          (click)="aberta.set(!aberta())">
          {{ aberta() ? 'Recolher' : 'Expandir' }}
        </button>
      </div>

      @if (aberta()) {
        <div [id]="'fin-corpo-' + uid" class="fin-corpo">
          @if (carregando()) {
            <div class="fin-esqueleto" role="status">
              <span class="sr-only">Carregando as taxas do requerimento…</span>
              <span class="osso grande" aria-hidden="true"></span>
              <span class="osso" aria-hidden="true"></span>
            </div>
          } @else if (erro()) {
            <div class="fin-aviso-geral">
              <p role="alert">{{ erro() }}</p>
              <button type="button" class="btn sm" (click)="carregar()"><app-icone nome="refresh" />Tentar novamente</button>
            </div>
          } @else if (financeiro(); as f) {
            @if (f.estado === 'INDISPONIVEL') {
              <div class="fin-aviso-geral">
                <p role="status">Não foi possível consultar as taxas agora. O restante da página não foi afetado.</p>
                <button type="button" class="btn sm" (click)="carregar()"><app-icone nome="refresh" />Tentar novamente</button>
              </div>
            } @else {
              @if (f.resumo; as resumo) {
                <p class="fin-resumo">
                  <span class="muted">Situação financeira: </span>
                  <span [class]="'tag ' + selo(resumo).tom"><app-icone [nome]="selo(resumo).icone" />{{ selo(resumo).texto }}</span>
                </p>
              }

              @if (f.avisos.length) {
                <ul class="fin-avisos" aria-label="Avisos sobre as taxas">
                  @for (a of f.avisos; track a.codigo) {
                    <li [class]="a.gravidade === 'ATENCAO' ? 'atencao' : 'info'">
                      <app-icone [nome]="a.gravidade === 'ATENCAO' ? 'alert' : 'info'" />
                      <span><span class="sr-only">{{ a.gravidade === 'ATENCAO' ? 'Atenção: ' : 'Informação: ' }}</span>{{ a.texto }}</span>
                    </li>
                  }
                </ul>
              }

              @if (f.isencoes.length) {
                <ul class="fin-isencoes" aria-label="Isenções">
                  @for (i of f.isencoes; track $index) {
                    <li>
                      <app-icone nome="check" />
                      <span>
                        <b>{{ i.texto }}</b>
                        @if (i.data; as data) {
                          em <time [attr.datetime]="data">{{ formatarData(data) }}</time>
                          @if (i.dataAConfirmar) {
                            <span class="tag gray">data a confirmar</span>
                          }
                        } @else {
                          <span class="muted">— data não registrada</span>
                        }
                      </span>
                    </li>
                  }
                </ul>
              }

              @if (f.lancamentos.length) {
                <table class="fin-tabela">
                  <caption class="sr-only">Lançamentos de taxa, da emissão mais antiga para a mais recente</caption>
                  <thead>
                    <tr>
                      <th scope="col">Taxa</th>
                      <th scope="col">Situação</th>
                      <th scope="col">Emitida em</th>
                      <th scope="col">Vence em</th>
                      <th scope="col">Paga em</th>
                      <th scope="col">Nº do DAR</th>
                      @if (f.mostraValores) {
                        <th scope="col" class="num">Valor</th>
                        <th scope="col" class="num">Desconto</th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (l of f.lancamentos; track l.ordem) {
                      <tr [class.vencida]="l.vencida">
                        <th scope="row" class="fin-nome">
                          {{ nome(l) }}
                          @if (l.inconsistente) {
                            <span class="tag gray">Dados incompletos</span>
                          }
                        </th>
                        <td data-label="Situação">
                          <span [class]="'tag ' + tom(l)">{{ rotuloDaSituacao(l) }}</span>
                          @if (l.vencidaPorData) {
                            <span class="tag red"><app-icone nome="alert" />Taxa vencida</span>
                          }
                        </td>
                        <td data-label="Emitida em">{{ dataOuTraco(l.emitidaEm) }}</td>
                        <td data-label="Vence em">{{ dataOuTraco(l.venceEm) }}</td>
                        <td data-label="Paga em">{{ dataOuTraco(l.pagaEm) }}</td>
                        <td data-label="Nº do DAR" class="num-dar">{{ l.numeroDar || '—' }}</td>
                        @if (f.mostraValores) {
                          <td data-label="Valor" class="num">{{ reais(l.valor) }}</td>
                          <td data-label="Desconto" class="num">{{ reais(l.desconto) }}</td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              } @else if (f.resumo === 'SEM_TAXA') {
                <p class="fin-texto">Este requerimento não tem taxa registrada.</p>
              }
            }
          }
        </div>
      }
    </section>
  `,
  styles: `
    :host { display: block; margin-bottom: 22px; }
    .fin { min-height: 96px; }
    .fin-head { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 6px 12px; margin-bottom: 10px; }
    .fin-head h2 { display: flex; align-items: center; gap: 10px; }
    .fin-head h2 .icon { color: var(--trail); width: 23px; height: 23px; }
    .btn:focus-visible { outline: 2px solid var(--trail-deep); outline-offset: 2px; }
    .fin-resumo { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; margin: 0 0 12px; font-size: 14px; }
    .fin-resumo .tag, .fin-tabela .tag { display: inline-flex; align-items: center; gap: 4px; }
    .fin-resumo .tag .icon, .fin-tabela .tag .icon { width: 14px; height: 14px; }
    .fin-avisos, .fin-isencoes { list-style: none; margin: 0 0 12px; padding: 0; display: grid; gap: 6px; }
    .fin-avisos li { display: flex; align-items: flex-start; gap: 8px; font-size: 13px; padding: 8px 10px; border-radius: 8px; }
    .fin-avisos li.info { background: var(--trail-tint); color: var(--trail-deep); }
    .fin-avisos li.atencao { background: var(--marker-tint); color: var(--warn); }
    .fin-avisos .icon { width: 16px; height: 16px; flex: none; margin-top: 2px; }
    .fin-isencoes li { display: flex; align-items: flex-start; gap: 8px; font-size: 14px; overflow-wrap: anywhere; }
    .fin-isencoes .icon { width: 16px; height: 16px; flex: none; margin-top: 3px; color: var(--trail-deep); }
    .fin-tabela { width: 100%; border-collapse: collapse; font-size: 13px; }
    .fin-tabela th, .fin-tabela td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--line); vertical-align: top; }
    .fin-tabela thead th { font-weight: 600; color: var(--slate); white-space: nowrap; }
    .fin-tabela tbody th { font-weight: 600; }
    .fin-tabela .num { text-align: right; white-space: nowrap; }
    .fin-tabela .num-dar { overflow-wrap: anywhere; }
    .fin-tabela td .tag + .tag { margin-left: 6px; }
    .fin-nome .tag { margin-left: 6px; font-weight: 400; }
    .fin-texto { margin: 0; font-size: 14px; }
    .fin-aviso-geral { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 14px; }
    .fin-aviso-geral p { margin: 0; }
    .fin-esqueleto { display: grid; gap: 10px; }
    .osso { display: block; height: 14px; width: 60%; border-radius: 6px; background: var(--trail-tint); }
    .osso.grande { height: 24px; width: 40%; }
    @media (max-width: 720px) {
      .fin-tabela, .fin-tabela tbody, .fin-tabela tr, .fin-tabela th, .fin-tabela td { display: block; width: 100%; }
      .fin-tabela thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
      .fin-tabela tr { border: 1px solid var(--line); border-radius: 10px; padding: 6px 4px; margin-bottom: 10px; }
      .fin-tabela th, .fin-tabela td { border-bottom: 0; padding: 4px 10px; }
      .fin-tabela td { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 4px 12px; }
      .fin-tabela td::before { content: attr(data-label); color: var(--slate); flex: none; }
      .fin-tabela .num { text-align: right; }
      .fin-tabela td .tag + .tag { margin-left: 0; }
    }
  `,
})
export class FinanceiroComponent {
  private readonly api = inject(DetalheApiService);

  readonly idRequerimento = input.required<number>();

  protected readonly uid = ++instancias;
  protected readonly financeiro = signal<FinanceiroDoRequerimento | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly aberta = signal(true);

  protected readonly formatarData = formatarData;
  protected readonly selo = (resumo: keyof typeof SELO_DO_RESUMO) => SELO_DO_RESUMO[resumo];

  private assinatura: Subscription | null = null;

  constructor() {
    effect(() => {
      this.idRequerimento();
      untracked(() => this.carregar());
    });
    inject(DestroyRef).onDestroy(() => this.assinatura?.unsubscribe());
  }

  protected nome(l: LancamentoFinanceiro): string {
    return nomeDoLancamento(l);
  }

  protected tom(l: LancamentoFinanceiro): string {
    return tomDaSituacao(l.situacao);
  }

  /** A situação como o Licenciamento a grava; a "taxa vencida por data" vem à parte, ao lado. */
  protected rotuloDaSituacao(l: LancamentoFinanceiro): string {
    return ROTULO_DA_SITUACAO[l.situacao];
  }

  protected dataOuTraco(data: string | null): string {
    return data ? formatarData(data) : '—';
  }

  /** Valor ausente é "não informado", nunca R$ 0,00. */
  protected reais(valor: number | null | undefined): string {
    return formatarReais(valor) || 'Não informado';
  }

  carregar(): void {
    this.assinatura?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.assinatura = this.api.financeiro(this.idRequerimento()).subscribe({
      next: (f) => {
        this.financeiro.set(f);
        this.carregando.set(false);
      },
      error: (e: unknown) => {
        this.financeiro.set(null);
        this.erro.set(
          e instanceof HttpErrorResponse
            ? mensagemDoErro(e)
            : 'Não foi possível consultar as taxas agora. O restante da página não foi afetado.',
        );
        this.carregando.set(false);
      },
    });
  }
}
