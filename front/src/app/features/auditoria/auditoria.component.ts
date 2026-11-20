import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { mensagemDoErro } from '../../core/mensagens-erro';
import { baixarBlob } from '../../shared/arquivo-local';
import { formatarDataHora } from '../../shared/formato';
import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';
import { AuditoriaApiService } from './auditoria-api.service';
import { AuditoriaDetalheComponent } from './auditoria-detalhe.component';
import {
  EventoDeAuditoria,
  EventosResposta,
  FiltroDeAuditoria,
  GrupoDeAuditoria,
  PERIODOS,
  PeriodoDeAuditoria,
  ROTULO_DO_PERFIL,
  TIPOS_DO_GRUPO,
} from './auditoria.models';

/** Espera depois da última tecla antes de consultar o servidor. */
export const ESPERA_DA_BUSCA_MS = 350;

const FILTRO_INICIAL: FiltroDeAuditoria = { periodo: '7d', texto: '', tipo: '', usuario: '', somenteSigilosos: false, pagina: 0 };

/**
 * Auditoria (RF21 a RF24, docs/08 tela 5 e docs/11): a mesma tela para os grupos `documentos` e `acessos`, escolhido pelo
 * `data.grupo` da rota. O que cada perfil vê (vínculo do Operador, mascaramento de documento sigiloso) é decidido pelo
 * servidor; aqui só se esconde o que o perfil não pode usar ("somente sigilosos" sem permissão de ver sigilosos).
 */
@Component({
  selector: 'app-auditoria',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QuadroTopoComponent, FolhaSobrepostaComponent, IconeComponent, AuditoriaDetalheComponent],
  templateUrl: './auditoria.component.html',
})
export class AuditoriaComponent implements OnInit {
  private readonly api = inject(AuditoriaApiService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly grupo: GrupoDeAuditoria =
    (inject(ActivatedRoute).snapshot.data['grupo'] as GrupoDeAuditoria | undefined) ?? 'documentos';
  protected readonly ehDocumentos = this.grupo === 'documentos';
  protected readonly periodos = PERIODOS;
  protected readonly tipos = TIPOS_DO_GRUPO[this.grupo];
  protected readonly perfis = ROTULO_DO_PERFIL;
  protected readonly formatarDataHora = formatarDataHora;

  protected readonly filtro = signal<FiltroDeAuditoria>({ ...FILTRO_INICIAL });
  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly resposta = signal<EventosResposta | null>(null);
  protected readonly detalhe = signal<number | null>(null);
  protected readonly exportando = signal(false);
  protected readonly erroDaExportacao = signal('');
  protected readonly avisoDaExportacao = signal('');

  protected readonly itens = computed(() => this.resposta()?.conteudo ?? []);
  protected readonly indicadores = computed(() => this.resposta()?.indicadores ?? null);
  /** Sem permissão de ver sigilosos (Operador) o filtro não é oferecido: o servidor o recusaria (400). */
  protected readonly podeFiltrarSigilosos = computed(
    () => this.ehDocumentos && (this.auth.usuario()?.permissoes.verSigilosos ?? false),
  );
  /** O Operador vê os eventos dos seus processos, de qualquer usuário (RN07). */
  protected readonly restritoAoInteressado = computed(() => this.auth.usuario()?.permissoes.restritoAoInteressado ?? false);
  protected readonly titulo = this.ehDocumentos ? 'Auditoria de documentos' : 'Auditoria de acessos';
  protected readonly descricao = this.ehDocumentos
    ? 'Consultas, visualizações, dossiês, falhas e downloads de documentos.'
    : 'Entradas, trocas de perfil e alterações de usuários e de configuração.';

  private assinatura: Subscription | null = null;
  private exportacao: Subscription | null = null;
  private espera: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.assinatura?.unsubscribe();
      this.exportacao?.unsubscribe();
      this.limparEspera();
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.assinatura?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.assinatura = this.api.listar(this.grupo, this.filtro()).subscribe({
      next: (resposta) => {
        this.resposta.set(resposta);
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.carregando.set(false);
        this.erro.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível carregar a auditoria.');
      },
    });
  }

  // --- filtros

  private aplicar(parte: Partial<FiltroDeAuditoria>): void {
    this.limparEspera();
    this.filtro.update((atual) => ({ ...atual, pagina: 0, ...parte }));
    this.carregar();
  }

  protected escolherPeriodo(periodo: PeriodoDeAuditoria): void {
    if (periodo !== this.filtro().periodo) {
      this.aplicar({ periodo });
    }
  }

  protected escolherTipo(evento: Event): void {
    this.aplicar({ tipo: (evento.target as HTMLSelectElement).value });
  }

  protected alternarSigilosos(evento: Event): void {
    this.aplicar({ somenteSigilosos: (evento.target as HTMLInputElement).checked });
  }

  protected digitarTexto(evento: Event): void {
    this.esperarEAplicar({ texto: (evento.target as HTMLInputElement).value });
  }

  protected digitarUsuario(evento: Event): void {
    this.esperarEAplicar({ usuario: (evento.target as HTMLInputElement).value });
  }

  /** O campo mostra o que foi digitado na hora; a consulta ao servidor espera a pausa na digitação. */
  private esperarEAplicar(parte: Partial<FiltroDeAuditoria>): void {
    this.filtro.update((atual) => ({ ...atual, ...parte }));
    this.limparEspera();
    this.espera = setTimeout(() => {
      this.espera = null;
      this.aplicar({});
    }, ESPERA_DA_BUSCA_MS);
  }

  private limparEspera(): void {
    if (this.espera !== null) {
      clearTimeout(this.espera);
      this.espera = null;
    }
  }

  protected limparFiltros(): void {
    this.aplicar({ texto: '', tipo: '', usuario: '', somenteSigilosos: false });
  }

  protected temFiltro(): boolean {
    const f = this.filtro();
    return !!(f.texto || f.tipo || f.usuario || f.somenteSigilosos);
  }

  protected irParaPagina(pagina: number): void {
    this.filtro.update((atual) => ({ ...atual, pagina }));
    this.carregar();
  }

  // --- detalhe e exportação

  protected abrirDetalhe(evento: EventoDeAuditoria): void {
    this.detalhe.set(evento.id);
  }

  protected fecharDetalhe(): void {
    this.detalhe.set(null);
  }

  protected exportar(): void {
    if (this.exportando()) {
      return;
    }
    this.exportando.set(true);
    this.erroDaExportacao.set('');
    this.avisoDaExportacao.set('');
    this.exportacao = this.api.exportar(this.grupo, this.filtro()).subscribe({
      next: (csv) => {
        this.exportando.set(false);
        baixarBlob(csv.blob, csv.nome ?? `auditoria-${this.grupo}.csv`);
        if (csv.truncado) {
          this.avisoDaExportacao.set(
            'O arquivo foi cortado no limite de linhas do servidor e traz só os eventos mais recentes. Use um período menor ou mais filtros para exportar o restante.',
          );
        }
      },
      error: (erro: unknown) => {
        this.exportando.set(false);
        this.erroDaExportacao.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível exportar o CSV.');
      },
    });
  }

  // --- apresentação

  protected processoOuRequerimento(e: EventoDeAuditoria): string {
    if (e.numeroProcesso) {
      return `Processo ${e.numeroProcesso}`;
    }
    return e.numeroRequerimento ? `Requerimento ${e.numeroRequerimento}` : '—';
  }

  /** Texto completo do detalhe (dica ao passar o mouse); o conteúdo quebra linha e nunca é cortado. */
  protected textoDoDetalhe(e: EventoDeAuditoria): string | null {
    const texto = [e.arquivo, e.detalhe, e.sigiloso ? 'Sigiloso' : null].filter(Boolean).join(' · ');
    return texto || null;
  }

  protected tomDoTipo(tipo: string): string {
    return { FALHA: 'red', DOSSIE: 'lime', DOWNLOAD: 'dark', CONFIG: 'amber', USUARIO: 'amber' }[tipo] ?? 'gray';
  }
}
