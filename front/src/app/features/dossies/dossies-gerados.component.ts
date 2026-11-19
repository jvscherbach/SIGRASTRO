import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { Observable, Subscription } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { mensagemDoErro, mensagemRealDoErro } from '../../core/mensagens-erro';
import { PageResponse } from '../../shared/api-tipos';
import { baixarBlob, baixarDireto, nomeDoContentDisposition, pdfRecebidoInteiro } from '../../shared/arquivo-local';
import { agoraEmCuiaba, formatarDataHora, formatarTamanho, minutosAte } from '../../shared/formato';
import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';
import { AcompanhamentoDeDossieService, MENSAGEM_PDF_INCOMPLETO } from '../documentos/acompanhamento-dossie.service';
import { DossiesApiService } from '../documentos/documentos-api.service';
import { DossieGerado, SituacaoDossieGerado } from '../documentos/documentos.models';

/** Atualização automática leve: a lista muda devagar (gerações levam minutos), e a gaveta tem o polling fino. */
export const INTERVALO_DA_LISTA_MS = 30_000;
export const TAMANHO_DA_PAGINA = 20;

const EM_ANDAMENTO: readonly SituacaoDossieGerado[] = ['AGUARDANDO', 'OBTENDO', 'MONTANDO'];

export const ROTULO_DA_SITUACAO: Record<SituacaoDossieGerado, string> = {
  AGUARDANDO: 'Na fila',
  OBTENDO: 'Obtendo documentos',
  MONTANDO: 'Montando o PDF',
  PENDENTE_DECISAO: 'Aguardando decisão',
  CONCLUIDO: 'Pronto',
  CANCELADO: 'Cancelado',
  EXPIRADO: 'Expirado',
  BAIXADO: 'Arquivo baixado',
  FALHA: 'Falhou',
};

const TOM_DA_SITUACAO: Record<SituacaoDossieGerado, string> = {
  AGUARDANDO: 'blue',
  OBTENDO: 'blue',
  MONTANDO: 'blue',
  PENDENTE_DECISAO: 'amber',
  CONCLUIDO: 'lime',
  CANCELADO: 'red',
  EXPIRADO: 'gray',
  BAIXADO: 'dark',
  FALHA: 'red',
};

/** "Expirado · nenhum arquivo gerado" quando expirou à espera de decisão (docs/08). */
export function rotuloDaSituacao(d: Pick<DossieGerado, 'situacao' | 'arquivoGerado' | 'baixado'>): string {
  if (d.situacao === 'EXPIRADO' && !d.arquivoGerado) {
    return 'Expirado · nenhum arquivo gerado';
  }
  if (d.situacao === 'CONCLUIDO' && d.baixado) {
    // Janela de nova tentativa (D77): entregue ao navegador, ainda dá para baixar de novo.
    return 'Pronto · já baixado';
  }
  return ROTULO_DA_SITUACAO[d.situacao];
}

/** Erro de uma ação na linha: o texto real e se "Tentar novamente" (repetir o download) faz sentido. */
export interface MensagemDaAcao {
  id: string;
  texto: string;
  /** A falha foi ao baixar: a linha oferece "Tentar novamente", "Gerar novamente" e "Descartar" (S6, bloco 3). */
  deDownload: boolean;
}

const ARQUIVO_PERDIDO = ['DOSSIE_ARQUIVO_AUSENTE', 'DOSSIE_ARQUIVO_INVALIDO', 'DOSSIE_EXPIRADO'];

/**
 * Tela "Dossiês gerados" (S4, docs/08): pedidos do histórico em memória, com situação, expiração e ações. O que cada
 * perfil vê é decidido pelo servidor (o Operador só recebe os seus e nunca `geradoPor`). Baixar e pré-visualizar usam
 * Blob com o token no cabeçalho (ticket para dossiê grande); "Decidir" abre a gaveta de geração com o pedido existente.
 */
@Component({
  selector: 'app-dossies-gerados',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QuadroTopoComponent, FolhaSobrepostaComponent, IconeComponent],
  templateUrl: './dossies-gerados.component.html',
  host: { '(document:visibilitychange)': 'aoMudarVisibilidade()' },
})
export class DossiesGeradosComponent implements OnInit {
  private readonly api = inject(DossiesApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly carregando = signal(true);
  protected readonly atualizando = signal(false);
  protected readonly erro = signal('');
  protected readonly resposta = signal<PageResponse<DossieGerado> | null>(null);
  protected readonly numeroDaPagina = signal(0);
  protected readonly somenteMeus = signal(false);
  protected readonly filtroDeSituacao = signal<SituacaoDossieGerado | ''>('');
  protected readonly agora = signal(agoraEmCuiaba());
  /** Linha com ação em curso (botões desabilitados) e mensagem de erro da última ação. */
  protected readonly ocupado = signal<string | null>(null);
  protected readonly mensagemDaAcao = signal<MensagemDaAcao | null>(null);
  protected readonly previa = signal<{ url: string; seguro: SafeResourceUrl } | null>(null);

  /** Gestor e Administrador veem os pedidos de todos e podem filtrar os seus (docs/07). */
  protected readonly podeFiltrarMeus = computed(() => this.auth.usuario()?.permissoes.restritoAoInteressado === false);
  protected readonly itens = computed(() => this.resposta()?.conteudo ?? []);
  /** A coluna só existe quando a API manda o campo (nunca para o Operador). */
  protected readonly mostrarGeradoPor = computed(() => this.itens().some((d) => d.geradoPor !== undefined));
  protected readonly listados = computed(() => {
    const filtro = this.filtroDeSituacao();
    return filtro ? this.itens().filter((d) => d.situacao === filtro) : this.itens();
  });
  protected readonly situacoes = Object.keys(ROTULO_DA_SITUACAO) as SituacaoDossieGerado[];

  protected readonly rotuloDaSituacao = rotuloDaSituacao;
  protected readonly rotuloDoFiltro = ROTULO_DA_SITUACAO;
  protected readonly tomDaSituacao = TOM_DA_SITUACAO;
  protected readonly formatarDataHora = formatarDataHora;
  protected readonly formatarTamanho = formatarTamanho;

  private assinatura: Subscription | null = null;
  private acao: Subscription | null = null;
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private destruido = false;
  private readonly destroyRef = inject(DestroyRef);
  private readonly acompanhamento = inject(AcompanhamentoDeDossieService);

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.destruido = true;
      this.pararAtualizacao();
      this.assinatura?.unsubscribe();
      this.acao?.unsubscribe();
      this.descartarPrevia();
    });
  }

  ngOnInit(): void {
    // Ao fechar (minimizar) a gaveta a lista se atualiza: a situação do pedido pode ter mudado.
    this.acompanhamento.gavetaFechada$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.carregar());
    this.carregar();
  }

  /** Carrega a página atual. Com a lista na tela, atualiza sem trocá-la por "Carregando". */
  protected carregar(): void {
    this.pararAtualizacao();
    this.assinatura?.unsubscribe();
    const silencioso = this.resposta() !== null;
    this.carregando.set(!silencioso);
    this.atualizando.set(silencioso);
    this.erro.set('');
    this.assinatura = this.api.listar(this.numeroDaPagina(), TAMANHO_DA_PAGINA, this.somenteMeus()).subscribe({
      next: (resposta) => {
        this.resposta.set(resposta);
        this.agora.set(agoraEmCuiaba());
        this.carregando.set(false);
        this.atualizando.set(false);
        this.agendarAtualizacao();
      },
      error: (erro: unknown) => {
        this.carregando.set(false);
        this.atualizando.set(false);
        this.resposta.set(null);
        this.erro.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível carregar os dossiês.');
      },
    });
  }

  /** Aba oculta: nada de atualização; ao voltar, atualiza na hora e retoma o intervalo. */
  protected aoMudarVisibilidade(): void {
    if (document.visibilityState === 'hidden') {
      this.pararAtualizacao();
    } else if (!this.carregando() && !this.erro()) {
      this.carregar();
    }
  }

  private agendarAtualizacao(): void {
    if (this.destruido || document.visibilityState === 'hidden') {
      return;
    }
    this.temporizador = setTimeout(() => this.carregar(), INTERVALO_DA_LISTA_MS);
  }

  private pararAtualizacao(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }

  protected alternarSomenteMeus(evento: Event): void {
    this.somenteMeus.set((evento.target as HTMLInputElement).checked);
    this.numeroDaPagina.set(0);
    this.carregar();
  }

  protected escolherSituacao(evento: Event): void {
    this.filtroDeSituacao.set((evento.target as HTMLSelectElement).value as SituacaoDossieGerado | '');
  }

  protected irParaPagina(pagina: number): void {
    this.numeroDaPagina.set(pagina);
    this.carregar();
  }

  // --- apresentação

  protected emAndamento(d: DossieGerado): boolean {
    return EM_ANDAMENTO.includes(d.situacao);
  }

  /** "restam N min" só enquanto o pedido está vivo (em andamento, à espera de decisão ou pronto). */
  protected minutosRestantes(d: DossieGerado): number | null {
    this.agora();
    const vivo = this.emAndamento(d) || d.situacao === 'PENDENTE_DECISAO' || d.situacao === 'CONCLUIDO';
    return vivo ? minutosAte(d.expiraEm) : null;
  }

  // --- ações

  protected abrirRequerimento(d: DossieGerado): void {
    void this.router.navigate(['/consulta/requerimentos', d.idRequerimento]);
  }

  protected decidir(d: DossieGerado): void {
    this.acompanhamento.acompanhar(d.id, d.idRequerimento);
  }

  protected cancelar(d: DossieGerado): void {
    this.executar(d, this.api.cancelar(d.id), () => this.carregar());
  }

  protected previsualizar(d: DossieGerado): void {
    this.executar(d, this.api.arquivo(d.id, 'inline'), (resposta) => {
      this.descartarPrevia();
      const url = URL.createObjectURL(resposta.body as Blob);
      this.previa.set({ url, seguro: this.sanitizer.bypassSecurityTrustResourceUrl(url) });
    });
  }

  protected fecharPrevia(): void {
    this.descartarPrevia();
  }

  /** Mesmas regras da gaveta: Blob com o token no cabeçalho; acima do limite, ticket de uso único e download direto. */
  protected baixar(d: DossieGerado): void {
    if (d.entregaPorTicket) {
      this.executar(d, this.api.ticket(d.id), ({ ticket }) => {
        baixarDireto(this.api.urlDeDownloadDireto(d.id, ticket));
        this.carregar();
      }, true);
      return;
    }
    this.executar(d, this.api.arquivo(d.id, 'attachment'), (resposta) => {
      this.ocupado.set(d.id);
      void pdfRecebidoInteiro(resposta.body, resposta.headers.get('Content-Length')).then((inteiro) => {
        if (this.destruido) {
          return;
        }
        this.ocupado.set(null);
        if (!inteiro) {
          // Corpo truncado: nada vai para o navegador e a linha mostra o erro, nunca "baixado".
          this.mensagemDaAcao.set({ id: d.id, texto: MENSAGEM_PDF_INCOMPLETO, deDownload: true });
          return;
        }
        const nome = nomeDoContentDisposition(resposta.headers.get('Content-Disposition')) ?? 'dossie.pdf';
        baixarBlob(resposta.body as Blob, nome);
        // O servidor guarda o arquivo alguns minutos (D77): a linha passa a "Pronto · já baixado" e depois a "Arquivo baixado".
        this.descartarPrevia();
        this.carregar();
      });
    }, true);
  }

  /** "Descartar" depois de um erro: apaga o pedido no servidor (DELETE) e atualiza a lista. */
  protected descartar(d: DossieGerado): void {
    this.mensagemDaAcao.set(null);
    this.executar(d, this.api.cancelar(d.id), () => this.carregar());
  }

  private executar<T>(d: DossieGerado, chamada: Observable<T>, aoTerminar: (valor: T) => void, deDownload = false): void {
    if (this.ocupado()) {
      return;
    }
    this.acao?.unsubscribe();
    this.mensagemDaAcao.set(null);
    this.ocupado.set(d.id);
    this.acao = chamada.subscribe({
      next: (valor) => {
        this.ocupado.set(null);
        aoTerminar(valor);
      },
      error: (erro: unknown) => {
        this.ocupado.set(null);
        const texto =
          erro instanceof HttpErrorResponse ? mensagemRealDoErro(erro) : 'Não foi possível concluir a operação.';
        this.mensagemDaAcao.set({ id: d.id, texto, deDownload });
        const codigo = erro instanceof HttpErrorResponse ? (erro.error as { errorCode?: string } | null)?.errorCode : null;
        if (codigo && ARQUIVO_PERDIDO.includes(codigo)) {
          // O pedido mudou no servidor (FALHA ou expirado): a linha mostra a situação real.
          this.carregar();
        }
      },
    });
  }

  private descartarPrevia(): void {
    const atual = this.previa();
    if (atual) {
      URL.revokeObjectURL(atual.url);
      this.previa.set(null);
    }
  }
}
