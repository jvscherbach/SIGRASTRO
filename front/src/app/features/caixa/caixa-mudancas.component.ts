import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, inject, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { FocoPresoDirective } from '../../shared/foco-preso.directive';
import { IconeComponent } from '../../shared/icone.component';
import { CaixaApiService } from './caixa-api.service';
import { AvisoDaCaixa } from './caixa.models';

/**
 * Lista de mudanças da caixa (S11): o que chegou ao analista desde o último visto, com de onde veio. Ao receber a lista, grava o
 * visto só do conjunto recebido; o que chegar depois gera nova caixinha. Fecha com Esc, clique fora e botão; o foco fica
 * preso no diálogo e volta ao elemento que o abriu.
 */
@Component({
  selector: 'app-caixa-mudancas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, FocoPresoDirective, RouterLink],
  host: { '(document:keydown.escape)': 'fechar.emit()' },
  template: `
    <div class="scrim" (click)="aoClicarNoFundo($event)">
      <section class="drawer" appFocoPreso role="dialog" aria-modal="true" aria-labelledby="caixa-mudancas-titulo">
        <header class="d-head">
          <div>
            <span class="eyebrow">Minha caixa</span>
            <h2 id="caixa-mudancas-titulo" style="margin-top: 4px">O que chegou para você</h2>
            <p>Requerimentos que entraram na sua caixa desde a sua última visita.</p>
          </div>
          <button type="button" class="icon-btn" aria-label="Fechar" (click)="fechar.emit()"><app-icone nome="close" /></button>
        </header>

        <div class="d-body">
          @if (carregando()) {
            <p class="muted" role="status">Carregando as mudanças…</p>
          } @else if (erro()) {
            <div class="alert" role="alert" style="margin: 0 0 12px"><app-icone nome="alert" /><span>{{ erro() }}</span></div>
            <button type="button" class="btn sm" (click)="carregar()"><app-icone nome="refresh" />Tentar novamente</button>
          } @else if (avisos().length === 0) {
            <p class="muted" role="status">Nenhuma mudança nova.</p>
          } @else {
            <ul class="caixa-mudancas" aria-label="Requerimentos que chegaram">
              @for (a of avisos(); track a.id) {
                <li>
                  <a [routerLink]="['/consulta/requerimentos', a.id]" (click)="fechar.emit()">
                    <b>{{ a.numero ?? 'Sem número' }}</b>
                    <span class="caixa-interessado">{{ a.interessado ?? 'Interessado não informado' }}</span>
                    <span class="caixa-meta">{{ a.tipo ?? 'Tipo não informado' }} · {{ a.etapa }}</span>
                  </a>
                  <span class="tag gray caixa-origem">{{ a.origem }}</span>
                </li>
              }
            </ul>
          }
        </div>

        <footer class="d-foot">
          <button type="button" class="btn quiet" (click)="fechar.emit()">Fechar</button>
        </footer>
      </section>
    </div>
  `,
})
export class CaixaMudancasComponent implements OnInit {
  private readonly api = inject(CaixaApiService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  readonly fechar = output<void>();
  /** O visto foi gravado: a caixa se relê para a caixinha e as marcas "Novo" sumirem. */
  readonly visto = output<void>();

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly avisos = signal<AvisoDaCaixa[]>([]);

  private assinatura: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.assinatura?.unsubscribe();
      this.anfitriao.remove();
    });
  }

  ngOnInit(): void {
    // Como as outras gavetas: sai da folha da tela e fica acima da barra do topo.
    document.body.appendChild(this.anfitriao);
    this.carregar();
  }

  protected carregar(): void {
    this.assinatura?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.assinatura = this.api.avisos().subscribe({
      next: (resposta) => {
        this.avisos.set(resposta.avisos);
        this.carregando.set(false);
        if (resposta.avisos.length > 0) {
          this.gravarVisto(resposta.avisos.map((a) => a.id));
        }
      },
      error: (e: unknown) => {
        this.carregando.set(false);
        this.erro.set(e instanceof HttpErrorResponse ? mensagemDoErro(e) : 'Não foi possível carregar as mudanças.');
      },
    });
  }

  protected aoClicarNoFundo(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.fechar.emit();
    }
  }

  /** Falha ao gravar o visto não esconde a lista: a caixinha continua e o aviso volta na próxima abertura. */
  private gravarVisto(ids: number[]): void {
    this.api.marcarVisto(ids).subscribe({ next: () => this.visto.emit(), error: () => undefined });
  }
}
