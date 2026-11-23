import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Faixa do topo do padrão SIGRASTRO: fundo em degradê, paisagem do Cerrado (imagem decorativa) e um cartão de título
 * claro e semitransparente à esquerda, com o título e o subtítulo da tela. O conteúdo projetado (busca, cartões)
 * fica abaixo do cartão, dentro da faixa.
 */
@Component({
  selector: 'app-quadro-topo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `:host { display: block; }`,
  template: `
    <div class="dash-hero" [class.tall]="alto()">
      <img class="dash-scene" src="assets/img/cerrado.svg" alt="" />
      <div class="dash-hero-inner">
        <div class="dash-hero-top">
          <div class="dash-headline">
            <span class="eyebrow">{{ sobretitulo() }}</span>
            <h1 style="margin-top: 6px">{{ titulo() }}</h1>
            <p>{{ descricao() }}</p>
            <div class="tags"><ng-content select="[etiquetas]" /></div>
          </div>
        </div>
        <ng-content />
      </div>
    </div>
  `,
})
export class QuadroTopoComponent {
  readonly sobretitulo = input('Vistas do Processo');
  readonly titulo = input.required<string>();
  readonly descricao = input('');
  /** Faixa alta (consulta, telas em construção) ou compacta (detalhe, que traz cartões de vidro). */
  readonly alto = input(true);
}
