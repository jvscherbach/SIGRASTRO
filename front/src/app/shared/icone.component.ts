import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Ícone do sprite (<app-sprite-icones>), no traço do protótipo. */
@Component({
  selector: 'app-icone',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg class="icon" aria-hidden="true"><use [attr.href]="'#i-' + nome()" /></svg>`,
  styles: `:host { display: inline-flex; }`,
})
export class IconeComponent {
  readonly nome = input.required<string>();
}
