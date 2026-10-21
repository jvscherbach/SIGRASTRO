import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';

/** Página provisória das telas que entram nas próximas sprints. Dados vêm de data da rota. */
@Component({
  selector: 'app-em-construcao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QuadroTopoComponent, FolhaSobrepostaComponent, IconeComponent],
  template: `
    <section [attr.aria-label]="titulo">
      <app-quadro-topo [titulo]="titulo" [descricao]="descricao">
        <span etiquetas class="tag amber"><app-icone nome="clock" />Em construção</span>
        <span etiquetas class="tag gray">Entrega prevista na {{ sprint }}</span>
      </app-quadro-topo>
      <app-folha-sobreposta>
        <div class="empty">
          <span class="tile-icon"><app-icone nome="sparkle" /></span>
          <h3>Esta tela ainda está em construção</h3>
          <p>Ela será entregue na {{ sprint }}, conforme o plano de sprints do projeto.</p>
        </div>
      </app-folha-sobreposta>
    </section>
  `,
})
export class EmConstrucaoComponent {
  private readonly dados = inject(ActivatedRoute).snapshot.data;

  protected readonly titulo = this.dados['titulo'] as string;
  protected readonly descricao = this.dados['descricao'] as string;
  protected readonly sprint = this.dados['sprint'] as string;
}
