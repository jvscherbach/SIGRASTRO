import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Situacao } from './api-tipos';
import { tomDaSituacao } from './formato';

@Component({
  selector: 'app-situacao-tag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="tag" [class]="tom()"><span class="dot"></span>{{ situacao().descricao }}</span>`,
})
export class SituacaoTagComponent {
  readonly situacao = input.required<Situacao>();
  protected readonly tom = computed(() => tomDaSituacao(this.situacao().codigo));
}
