import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { IconeComponent } from '../../shared/icone.component';
import { EXPLICACAO_DA_CLASSE, Parametro, ROTULO_DA_CLASSE, ROTULO_DA_OPCAO } from './configuracoes.models';
import { ParametrosStore, textoDoValor } from './parametros-store.service';

/**
 * Um parâmetro editável: o campo certo para o tipo (caixa de marcação, número com faixa, lista ou botões de opção), as
 * etiquetas da classe de aplicação, o padrão do ambiente e "Restaurar padrão". Editar só registra a pendência no store.
 */
@Component({
  selector: 'app-parametro-campo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  templateUrl: './parametro-campo.component.html',
})
export class ParametroCampoComponent {
  protected readonly store = inject(ParametrosStore);

  readonly parametro = input.required<Parametro>();

  protected readonly rotuloDaClasse = ROTULO_DA_CLASSE;
  protected readonly explicacao = EXPLICACAO_DA_CLASSE;
  protected readonly textoDoValor = textoDoValor;

  /** Opções que são números (retenção em anos) viram lista; as de texto viram botões de opção. */
  protected comoLista(p: Parametro): boolean {
    return !!p.opcoes?.every((o) => /^\d+$/.test(o));
  }

  protected opcaoRotulo(p: Parametro, opcao: string): string {
    if (p.nome.startsWith('auditoria.')) {
      return opcao === '1' ? '1 ano' : `${opcao} anos`;
    }
    return ROTULO_DA_OPCAO[opcao] ?? opcao;
  }

  protected idDe(p: Parametro, sufixo = ''): string {
    return `param-${p.nome.replace(/\./g, '-')}${sufixo}`;
  }

  protected digitarNumero(p: Parametro, evento: Event): void {
    this.store.editar(p, (evento.target as HTMLInputElement).value.trim());
  }

  protected escolher(p: Parametro, evento: Event): void {
    this.store.editar(p, (evento.target as HTMLInputElement | HTMLSelectElement).value);
  }

  protected alternar(p: Parametro, evento: Event): void {
    this.store.editar(p, (evento.target as HTMLInputElement).checked ? 'true' : 'false');
  }

  protected faixa(p: Parametro): string {
    return p.minimo !== null && p.maximo !== null ? `Faixa: ${p.minimo} a ${p.maximo}` : '';
  }
}
