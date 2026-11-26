import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { FUSO_DE_CUIABA } from '../../shared/formato';
import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';
import { CaixaApiService } from './caixa-api.service';
import { CaixaMudancasComponent } from './caixa-mudancas.component';
import { CaixaResposta, ETAPA_CORRECAO, FiltroDaCaixa, LinhaDaCaixa, OrdemDaCaixa } from './caixa.models';

/** Releitura enquanto a caixa prepara (ou espera a atualização pedida): a cada 5 s, no máximo 2 minutos. */
export const INTERVALO_DA_RELEITURA_MS = 5_000;
export const MAXIMO_DE_RELEITURAS = 24;
/** Espera depois da última tecla antes de buscar. */
export const ESPERA_DA_BUSCA_MS = 300;

const HORA = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO_DE_CUIABA, hour: '2-digit', minute: '2-digit' });

/** "Atualizado às HH:MM" (Cuiabá). */
export function horaDaAtualizacao(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  const data = new Date(iso);
  return Number.isNaN(data.getTime()) ? '' : HORA.format(data);
}

/** Dias na etapa em texto: sem dado, "sem informação" (nunca inventa). */
export function textoDosDias(dias: number | null): string {
  if (dias === null || dias === undefined) {
    return 'sem informação';
  }
  if (dias <= 0) {
    return 'hoje';
  }
  return dias === 1 ? '1 dia' : `${dias} dias`;
}

/**
 * Minha caixa (S11, docs/08): os requerimentos na posse do analista na fase de análise. Só lê o instantâneo da API (nunca o
 * SIGA nem o Camunda). A caixinha de aviso abre a lista de mudanças, que grava o visto; o "visto" é em memória no servidor
 * e some quando o sistema reinicia.
 */
@Component({
  selector: 'app-caixa',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CaixaMudancasComponent, FolhaSobrepostaComponent, IconeComponent, QuadroTopoComponent, RouterLink],
  templateUrl: './caixa.component.html',
})
export class CaixaComponent implements OnInit {
  private readonly api = inject(CaixaApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly caixa = signal<CaixaResposta | null>(null);
  protected readonly atualizando = signal(false);
  protected readonly mensagem = signal('');
  protected readonly demorando = signal(false);
  protected readonly mudancasAbertas = signal(false);
  protected readonly etapa = signal('');
  protected readonly busca = signal('');
  protected readonly ordem = signal<OrdemDaCaixa>('MAIS_PARADO');

  protected readonly horaDaAtualizacao = horaDaAtualizacao;
  protected readonly textoDosDias = textoDosDias;
  protected readonly etapaCorrecao = ETAPA_CORRECAO;

  protected readonly mostraLista = computed(() => {
    const c = this.caixa();
    return !!c && !c.semVinculo && (c.estado === 'PRONTO' || c.estado === 'DESATUALIZADO');
  });
  protected readonly temFiltro = computed(() => this.busca().trim() !== '' || this.etapa() !== '');
  protected readonly textoDaCaixinha = computed(() => {
    const n = this.caixa()?.novos ?? 0;
    return n === 1 ? '1 requerimento chegou para você' : `${n} requerimentos chegaram para você`;
  });

  private assinatura: Subscription | null = null;
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private esperaDaBusca: ReturnType<typeof setTimeout> | null = null;
  private releituras = 0;
  /** `geradoEm` de quando se pediu a atualização: a releitura espera uma data diferente. */
  private esperandoDepoisDe: string | null | undefined = undefined;
  /** Depois de fechar a lista de mudanças, o foco vai ao título se o botão da caixinha não existir mais. */
  private focarTituloDepois = false;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.parar();
      this.assinatura?.unsubscribe();
      if (this.esperaDaBusca) {
        clearTimeout(this.esperaDaBusca);
      }
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    const filtro: FiltroDaCaixa = { etapa: this.etapa(), busca: this.busca(), ordem: this.ordem() };
    this.assinatura?.unsubscribe();
    this.assinatura = this.api.obter(filtro).subscribe({
      next: (resposta) => {
        this.caixa.set(resposta);
        this.erro.set('');
        this.carregando.set(false);
        this.decidirReleitura(resposta);
        this.devolverFoco();
      },
      error: (e: HttpErrorResponse) => {
        this.erro.set(mensagemDoErro(e));
        this.carregando.set(false);
      },
    });
  }

  protected escolherEtapa(id: string): void {
    this.etapa.set(id);
    this.carregar();
  }

  protected digitarBusca(evento: Event): void {
    this.busca.set((evento.target as HTMLInputElement).value);
    if (this.esperaDaBusca) {
      clearTimeout(this.esperaDaBusca);
    }
    this.esperaDaBusca = setTimeout(() => this.carregar(), ESPERA_DA_BUSCA_MS);
  }

  protected escolherOrdem(evento: Event): void {
    this.ordem.set((evento.target as HTMLSelectElement).value as OrdemDaCaixa);
    this.carregar();
  }

  protected limparFiltros(): void {
    this.busca.set('');
    this.etapa.set('');
    this.carregar();
  }

  protected abrirMudancas(): void {
    this.mudancasAbertas.set(true);
  }

  protected fecharMudancas(): void {
    this.mudancasAbertas.set(false);
    this.focarTituloDepois = true;
    this.carregar();
  }

  /** O visto foi gravado: relê para a caixinha e as marcas "Novo" sumirem (a lista de mudanças segue aberta). */
  protected aoGravarVisto(): void {
    this.carregar();
  }

  protected atualizarAgora(): void {
    if (this.atualizando()) {
      return;
    }
    this.atualizando.set(true);
    this.mensagem.set('');
    this.api.atualizar().subscribe({
      next: () => {
        this.atualizando.set(false);
        this.mensagem.set('Atualização pedida. A lista muda sozinha quando a leitura terminar.');
        this.esperandoDepoisDe = this.caixa()?.geradoEm ?? null;
        this.releituras = 0;
        this.demorando.set(false);
        this.agendar();
      },
      error: (e: HttpErrorResponse) => {
        this.atualizando.set(false);
        const espera = (e.error as { tentarEmSegundos?: number } | null)?.tentarEmSegundos;
        this.mensagem.set(
          e.status === 429 && typeof espera === 'number'
            ? `A caixa foi atualizada há pouco. Tente de novo em ${espera} segundos.`
            : mensagemDoErro(e),
        );
      },
    });
  }

  protected linhaComInteressado(l: LinhaDaCaixa): boolean {
    return l.posse === 'COM_INTERESSADO';
  }

  private devolverFoco(): void {
    if (!this.focarTituloDepois) {
      return;
    }
    this.focarTituloDepois = false;
    // Depois de renderizar: a caixinha pode ter sumido e o foco não pode ficar no vazio.
    queueMicrotask(() => {
      if (document.activeElement === document.body || !document.activeElement?.isConnected) {
        this.titulo()?.nativeElement.focus();
      }
    });
  }

  /** Preparando (ou à espera da atualização pedida): relê a cada 5 s por até 2 minutos. */
  private decidirReleitura(resposta: CaixaResposta): void {
    const espera =
      resposta.estado === 'PREPARANDO' ||
      (this.esperandoDepoisDe !== undefined && resposta.geradoEm === this.esperandoDepoisDe);
    if (!espera) {
      this.esperandoDepoisDe = undefined;
      this.demorando.set(false);
      this.parar();
      return;
    }
    if (this.releituras >= MAXIMO_DE_RELEITURAS) {
      this.demorando.set(true);
      this.esperandoDepoisDe = undefined;
      this.parar();
      return;
    }
    this.agendar();
  }

  private agendar(): void {
    this.parar();
    this.temporizador = setTimeout(() => {
      this.releituras += 1;
      this.carregar();
    }, INTERVALO_DA_RELEITURA_MS);
  }

  private parar(): void {
    if (this.temporizador) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
