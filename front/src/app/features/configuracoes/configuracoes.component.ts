import { ChangeDetectionStrategy, Component, signal } from '@angular/core';

import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';
import { UsuariosConfigComponent } from './usuarios-config.component';

export interface AbaDeConfiguracao {
  id: string;
  rotulo: string;
  /** Abas ainda não construídas aparecem, navegáveis, com o aviso "em construção" (bloco 10). */
  pronta: boolean;
}

export const ABAS: AbaDeConfiguracao[] = [
  { id: 'usuarios', rotulo: 'Usuários e perfis', pronta: true },
  { id: 'dossie', rotulo: 'Dossiê', pronta: false },
  { id: 'conversao', rotulo: 'Conversão', pronta: false },
  { id: 'visualizacao', rotulo: 'Visualização', pronta: false },
  { id: 'conexoes', rotulo: 'Conexões', pronta: false },
  { id: 'auditoria', rotulo: 'Auditoria', pronta: false },
];

/**
 * Configurações (RF25 a RF28, docs/08 tela 6 e docs/12): só o Administrador (guarda de rota pela permissão). Abas com
 * navegação por teclado (setas, Home e End); a aba de usuários é a deste bloco, as demais chegam no bloco 10.
 */
@Component({
  selector: 'app-configuracoes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QuadroTopoComponent, FolhaSobrepostaComponent, IconeComponent, UsuariosConfigComponent],
  templateUrl: './configuracoes.component.html',
})
export class ConfiguracoesComponent {
  protected readonly abas = ABAS;
  protected readonly ativa = signal<string>(ABAS[0].id);

  protected abaAtiva(): AbaDeConfiguracao {
    return ABAS.find((a) => a.id === this.ativa()) ?? ABAS[0];
  }

  protected escolher(id: string): void {
    this.ativa.set(id);
  }

  /** Setas, Home e End movem o foco e a seleção (padrão WAI-ARIA para abas). */
  protected aoTeclar(evento: KeyboardEvent): void {
    const indice = ABAS.findIndex((a) => a.id === this.ativa());
    let novo = indice;
    switch (evento.key) {
      case 'ArrowRight':
        novo = (indice + 1) % ABAS.length;
        break;
      case 'ArrowLeft':
        novo = (indice - 1 + ABAS.length) % ABAS.length;
        break;
      case 'Home':
        novo = 0;
        break;
      case 'End':
        novo = ABAS.length - 1;
        break;
      default:
        return;
    }
    evento.preventDefault();
    this.ativa.set(ABAS[novo].id);
    queueMicrotask(() => document.getElementById(`aba-${ABAS[novo].id}`)?.focus());
  }
}
