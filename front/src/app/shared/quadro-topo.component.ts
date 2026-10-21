import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

import { cachoeira } from './cena-cachoeira';
import { IconeComponent } from './icone.component';

/** Quadro do topo do padrão de quadros sobrepostos: ilustração, título e, opcionalmente, cartões de vidro. */
@Component({
  selector: 'app-quadro-topo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  styles: `:host { display: block; }`,
  template: `
    <div class="dash-hero tall">
      <div class="dash-scene" aria-hidden="true" [innerHTML]="cena()"></div>
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
      <span class="dash-place"><app-icone nome="pin" />Cachoeira Véu de Noiva · Chapada dos Guimarães - MT</span>
    </div>
  `,
})
export class QuadroTopoComponent {
  readonly sobretitulo = input('Vistas do Processo');
  readonly titulo = input.required<string>();
  readonly descricao = input('');

  private readonly sanitizer = inject(DomSanitizer);

  // SVG estático gerado pelo próprio código (cena do protótipo), por isso é seguro marcá-lo como confiável.
  protected readonly cena = computed(() => this.sanitizer.bypassSecurityTrustHtml(cachoeira('Q')));
}
