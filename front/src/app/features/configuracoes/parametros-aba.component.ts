import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { IconeComponent } from '../../shared/icone.component';
import { GrupoDeParametro } from './configuracoes.models';
import { DiagnosticoFinanceiroComponent } from './diagnostico-financeiro.component';
import { ParametroCampoComponent } from './parametro-campo.component';
import { ParametrosStore } from './parametros-store.service';

const PREFIXO_ORIGEM = 'dossie.origem.';

/**
 * Painel de uma aba de parâmetros (Dossiê, Conversão, Visualização, Auditoria e o tempo limite de Conexões): um campo por
 * parâmetro do grupo. Editar não grava: as alterações vão para o `ParametrosStore` e só saem no "Salvar alterações" da
 * barra fixa. Regras fixas (ordem cronológica, geometrias, compactados) aparecem só como leitura.
 */
@Component({
  selector: 'app-parametros-aba',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, ParametroCampoComponent, DiagnosticoFinanceiroComponent],
  templateUrl: './parametros-aba.component.html',
})
export class ParametrosAbaComponent {
  protected readonly store = inject(ParametrosStore);

  readonly grupo = input.required<GrupoDeParametro>();

  /** Origens dos documentos (7 chaves) ficam num bloco à parte, já que todas valem na hora. */
  protected readonly gerais = computed(() => this.store.doGrupo(this.grupo()).filter((p) => !p.nome.startsWith(PREFIXO_ORIGEM)));
  protected readonly origens = computed(() => this.store.doGrupo(this.grupo()).filter((p) => p.nome.startsWith(PREFIXO_ORIGEM)));
}
