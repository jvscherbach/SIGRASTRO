import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';

import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';
import { BarraPendenciasComponent } from './barra-pendencias.component';
import { ConexoesConfigComponent } from './conexoes-config.component';
import { GrupoDeParametro } from './configuracoes.models';
import { ParametrosAbaComponent } from './parametros-aba.component';
import { ParametrosStore } from './parametros-store.service';
import { UsuariosConfigComponent } from './usuarios-config.component';

export interface AbaDeConfiguracao {
  id: string;
  rotulo: string;
  /** Grupo de parâmetros que a aba edita; ausente nas abas que não são de parâmetros. */
  grupo?: GrupoDeParametro;
}

export const ABAS: AbaDeConfiguracao[] = [
  { id: 'usuarios', rotulo: 'Usuários e perfis' },
  { id: 'dossie', rotulo: 'Dossiê', grupo: 'DOSSIE' },
  { id: 'conversao', rotulo: 'Conversão', grupo: 'CONVERSAO' },
  { id: 'visualizacao', rotulo: 'Visualização', grupo: 'VISUALIZACAO' },
  { id: 'rastro', rotulo: 'Rastro', grupo: 'RASTRO' },
  { id: 'conexoes', rotulo: 'Conexões' },
  { id: 'auditoria', rotulo: 'Auditoria', grupo: 'AUDITORIA' },
];

/**
 * Configurações (RF25 a RF28, docs/08 tela 6 e docs/12): só o Administrador (guarda de rota pela permissão). Abas com
 * navegação por teclado (setas, Home e End). Usuários e perfis grava na hora; as demais abas só editam: as alterações
 * de parâmetros ficam pendentes numa barra fixa e saem num único "Salvar alterações".
 */
@Component({
  selector: 'app-configuracoes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    QuadroTopoComponent,
    FolhaSobrepostaComponent,
    UsuariosConfigComponent,
    ParametrosAbaComponent,
    ConexoesConfigComponent,
    BarraPendenciasComponent,
    IconeComponent,
  ],
  providers: [ParametrosStore],
  templateUrl: './configuracoes.component.html',
  host: { '(window:beforeunload)': 'aoFechar($event)' },
})
export class ConfiguracoesComponent implements OnInit {
  private readonly parametros = inject(ParametrosStore);

  protected readonly abas = ABAS;
  protected readonly ativa = signal<string>(ABAS[0].id);

  ngOnInit(): void {
    this.parametros.carregar();
  }

  temPendencia(): boolean {
    return this.parametros.temPendencia();
  }

  /** Recarregar ou fechar a janela com pendência: o navegador pede a confirmação. */
  protected aoFechar(evento: BeforeUnloadEvent): void {
    if (this.temPendencia()) {
      evento.preventDefault();
    }
  }

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
