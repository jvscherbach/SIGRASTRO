import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Marca SIGRASTRO: escudo árvore-vida e nome em dois pesos (SIG em 500, RASTRO em 700).
 * Com `mostrarNome` falso, o conjunto vira só o escudo com nome acessível "SIGRASTRO".
 */
@Component({
  selector: 'app-marca',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 10px; vertical-align: middle; }
    .escudo { display: block; flex: none; border: 0; border-radius: 0; }
    .wm { font-family: var(--display); font-weight: 700; font-size: 1.2rem; letter-spacing: .01em; line-height: 1; white-space: nowrap; }
    .l { font-weight: 500; }
  `,
  template: `
    @if (mostrarNome()) {
      <img class="escudo" src="assets/img/sigrastro-escudo.svg" alt="" [style.width.px]="tamanho()" [style.height.px]="tamanho()" />
      <span class="wm"><span class="l">SIG</span>RASTRO</span>
    } @else {
      <span class="conjunto" role="img" aria-label="SIGRASTRO">
        <img class="escudo" src="assets/img/sigrastro-escudo.svg" alt="" [style.width.px]="tamanho()" [style.height.px]="tamanho()" />
      </span>
    }
  `,
})
export class MarcaComponent {
  /** Largura e altura do escudo, em pixels. Não usar abaixo de 24. */
  readonly tamanho = input(36);
  readonly mostrarNome = input(true);
}
