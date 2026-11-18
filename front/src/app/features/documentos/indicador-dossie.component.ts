import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { IconeComponent } from '../../shared/icone.component';
import { AcompanhamentoDeDossieService } from './acompanhamento-dossie.service';

/**
 * Indicador da geração do dossiê na barra do topo (visível em todas as telas enquanto houver pedido ativo): texto com o
 * andamento e barra pequena; clicar reabre a gaveta no mesmo pedido. Concluído, decisão, erro e encerrado ficam até o
 * usuário abrir ou dispensar. Fica dentro da barra fixa: não muda a altura de nenhum quadro da tela.
 */
@Component({
  selector: 'app-indicador-dossie',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  template: `
    @if (servico.indicador(); as i) {
      <div class="ind-dossie" [attr.data-tipo]="i.tipo" role="status">
        <button type="button" class="ind-abrir" [attr.aria-label]="'Abrir a gaveta: ' + i.texto" (click)="servico.abrirGaveta()">
          <span class="ind-texto">{{ i.texto }}</span>
          @if (i.percentual !== null) {
            <span class="ind-barra" aria-hidden="true"><i [style.width.%]="i.percentual"></i></span>
          }
        </button>
        @if (i.tipo !== 'andamento') {
          <button type="button" class="ind-dispensar" aria-label="Dispensar aviso do dossiê" (click)="servico.dispensar()">
            <app-icone nome="close" />
          </button>
        }
      </div>
    }
  `,
})
export class IndicadorDossieComponent {
  protected readonly servico = inject(AcompanhamentoDeDossieService);
}
