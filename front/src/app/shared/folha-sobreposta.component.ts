import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Folha branca que se sobrepõe ao quadro do topo. */
@Component({
  selector: 'app-folha-sobreposta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `:host { display: block; }`,
  template: `<div class="dash-sheet"><ng-content /></div>`,
})
export class FolhaSobrepostaComponent {}
