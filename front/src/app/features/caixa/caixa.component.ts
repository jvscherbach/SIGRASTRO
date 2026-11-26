import { ChangeDetectionStrategy, Component } from '@angular/core';

import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';

/** Caixa de entrada do Analista (S11). Tela provisória do bloco 2: a caixa de verdade chega no bloco 7. */
@Component({
  selector: 'app-caixa',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FolhaSobrepostaComponent, IconeComponent, QuadroTopoComponent],
  template: `
    <section aria-label="Minha caixa">
      <app-quadro-topo
        titulo="Minha caixa"
        sobretitulo="Área de trabalho"
        descricao="Os requerimentos que estão com você na análise."
      />
      <app-folha-sobreposta>
        <p class="caixa-em-construcao" role="status"><app-icone nome="clock" /> Em construção</p>
      </app-folha-sobreposta>
    </section>
  `,
})
export class CaixaComponent {}
