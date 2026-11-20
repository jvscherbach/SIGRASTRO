import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Subject, Subscription } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { mensagemRealDoErro } from '../../core/mensagens-erro';
import { baixarBlob, baixarDireto, nomeDoContentDisposition, pdfRecebidoInteiro } from '../../shared/arquivo-local';
import { agoraEmCuiaba, formatarDataHora, minutosAte } from '../../shared/formato';
import { ToastService } from '../../shared/toast.service';
import { DossiesApiService } from './documentos-api.service';
import {
  AcaoDeContinuacao,
  AndamentoDossie,
  ErroDossie,
  ReferenciaDocumento,
  STATUS_FINAIS,
  StatusDossie,
} from './documentos.models';

/** Consulta do andamento: começa em 1 s e cresce 50% a cada resposta, até 5 s. */
export const INTERVALO_INICIAL_MS = 1000;
export const INTERVALO_MAXIMO_MS = 5000;
const FATOR_DO_INTERVALO = 1.5;
const CONTAGEM_REGRESSIVA_MS = 30_000;

const CREDENCIAL_RECUSADA = 'CREDENCIAL_ARQUIVOS_RECUSADA';
const ARQUIVO_PERDIDO = ['DOSSIE_ARQUIVO_AUSENTE', 'DOSSIE_ARQUIVO_INVALIDO'];

/** O PDF chegou incompleto (S6, bloco 3): nada é entregue ao navegador e a ação é repetir o download. */
export const MENSAGEM_PDF_INCOMPLETO =
  'O arquivo recebido está incompleto ou não é um PDF, e nada foi salvo. Clique em Tentar novamente para baixar de novo.';

export type Fase = 'enviando' | 'acompanhando' | 'erro';

/** Situação resumida para o indicador da barra do topo. */
export interface Indicador {
  tipo: 'andamento' | 'decisao' | 'pronto' | 'erro' | 'encerrado';
  texto: string;
  /** Percentual 0-100 da barrinha; nulo quando não há progresso a mostrar. */
  percentual: number | null;
}

/**
 * Dono do pedido de dossiê ativo (RF16, RF17): envia o POST, acompanha por polling com intervalo crescente e guarda o
 * estado. A gaveta é só a janela que mostra esse estado: fechá-la (clique no fundo, Esc, "Fechar") **minimiza**, sem
 * cancelar o POST nem o polling. Só "Cancelar geração" chama DELETE; sair da aplicação ou trocar de usuário encerra o
 * acompanhamento. Um pedido ativo por vez (o servidor aceita várias gerações; a tela acompanha uma).
 *
 * Pré-visualização e download usam o HttpClient (token no cabeçalho) e Blob: o token nunca vai para a URL.
 */
@Injectable({ providedIn: 'root' })
export class AcompanhamentoDeDossieService {
  private readonly api = inject(DossiesApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  /** A gaveta está aberta (a janela). O acompanhamento independe disso. */
  readonly gavetaAberta = signal(false);
  readonly idRequerimento = signal<number | null>(null);
  readonly fase = signal<Fase>('enviando');
  readonly andamento = signal<AndamentoDossie | null>(null);
  readonly mensagemDeErro = signal('');
  readonly ocupado = signal(false);
  readonly baixado = signal(false);
  /** O download foi entregue ao navegador (dossiê grande): o término não é visto daqui. */
  readonly baixadoDireto = signal(false);
  /**
   * O último download ou pré-visualização falhou (S6, bloco 3): a gaveta mostra o erro real e as ações "Tentar
   * novamente", "Gerar novamente" e "Descartar", nunca o estado de sucesso.
   */
  readonly erroDeDownload = signal(false);
  readonly vencido = signal(false);
  readonly previa = signal<{ url: string; seguro: SafeResourceUrl } | null>(null);
  readonly agora = signal(agoraEmCuiaba());
  readonly pedidoCriado = signal(false);
  /** Há um pedido sendo mostrado/acompanhado (gaveta aberta ou minimizada no indicador). */
  readonly ativo = signal(false);

  /** Pedido em que "Remover documento e gerar" tirou um documento da seleção (a tela de documentos o desmarca). */
  readonly documentoRemovido$ = new Subject<ReferenciaDocumento>();
  /** A gaveta foi fechada (a tela "Dossiês gerados" atualiza a lista). */
  readonly gavetaFechada$ = new Subject<void>();

  /** A seleção corrente: "Remover documento e gerar" a reduz e o próximo pedido usa a lista nova. */
  private selecao: ReferenciaDocumento[] = [];
  private pedido: string | null = null;
  private intervalo = INTERVALO_INICIAL_MS;
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private assinatura: Subscription | null = null;
  private acao: Subscription | null = null;
  private relogio: ReturnType<typeof setInterval> | null = null;
  private dono: string | null = null;
  private avisadoComoFinal = false;

  readonly pedidoId = signal<string | null>(null);
  readonly status = computed<StatusDossie | null>(() => this.andamento()?.status ?? null);
  readonly total = signal(0);
  readonly processados = computed(() => this.andamento()?.processados ?? 0);
  readonly percentual = computed(() => {
    const total = this.total();
    return total ? Math.round((this.processados() / total) * 100) : 0;
  });
  readonly emAndamento = computed(
    () => this.fase() === 'enviando' || (this.status() !== null && !STATUS_FINAIS.includes(this.status()!)),
  );
  readonly erroDoDossie = computed<ErroDossie | null>(() => this.andamento()?.erro ?? null);
  readonly tokenRecusado = computed(() => this.erroDoDossie()?.codigo === CREDENCIAL_RECUSADA);
  readonly falhas = computed(() => this.andamento()?.falhas ?? []);
  /**
   * Itens que a decisão vai refazer ou remover: os não resolvidos (em falha e, com a credencial recusada, também os
   * que nem chegaram a ser tentados). É o N dos botões.
   */
  readonly comProblema = computed(() =>
    this.status() === 'PENDENTE_DECISAO'
      ? (this.andamento()?.itens ?? []).filter((i) => i.situacao !== 'OK' && i.situacao !== 'REMOVIDO').length
      : 0,
  );
  readonly obtidos = computed(() => (this.andamento()?.itens ?? []).filter((i) => i.situacao === 'OK').length);
  /** Remover só faz sentido com algum documento já obtido para montar (o servidor recusa o contrário). */
  readonly podeRemover = computed(() => this.comProblema() > 0 && this.obtidos() > 0);
  /** Acima de DOSSIE_BLOB_MAX_MB: sem pré-visualização, download direto por ticket (D49). */
  readonly entregaPorTicket = computed(() => !!this.andamento()?.entregaPorTicket);
  readonly tamanhoEmMb = computed(() => Math.round((this.andamento()?.tamanhoBytes ?? 0) / (1024 * 1024)));
  readonly expiraAs = computed(() => formatarDataHora(this.andamento()?.expiraEm));
  readonly minutosRestantes = computed(() => {
    this.agora();
    return minutosAte(this.andamento()?.expiraEm);
  });
  /** Já existe uma geração que o usuário ainda precisa acompanhar ou decidir: bloqueia um segundo pedido. */
  readonly bloqueando = computed(() => this.ativo() && (this.emAndamento() || this.status() === 'PENDENTE_DECISAO'));

  readonly indicador = computed<Indicador | null>(() => {
    if (!this.ativo()) {
      return null;
    }
    const status = this.status();
    const percentual = this.percentual();
    if (this.fase() === 'erro') {
      return { tipo: 'erro', texto: 'Erro ao gerar o dossiê', percentual: null };
    }
    if (status === 'FALHA') {
      return { tipo: 'erro', texto: 'Falha ao gerar o dossiê', percentual: null };
    }
    if (this.fase() === 'enviando') {
      return { tipo: 'andamento', texto: 'Gerando dossiê…', percentual: 0 };
    }
    if (status === 'PENDENTE_DECISAO') {
      return { tipo: 'decisao', texto: 'Dossiê precisa da sua decisão', percentual };
    }
    if (status === 'CONCLUIDO' && !this.vencido()) {
      return { tipo: 'pronto', texto: 'Dossiê pronto', percentual: 100 };
    }
    if (status === 'CANCELADO' || status === 'EXPIRADO' || this.vencido()) {
      return { tipo: 'encerrado', texto: status === 'CANCELADO' ? 'Geração cancelada' : 'Dossiê expirado', percentual: null };
    }
    const total = this.total();
    const base = status === 'MONTANDO' ? 'Montando o PDF…' : 'Gerando dossiê…';
    return { tipo: 'andamento', texto: total ? `${base} ${this.processados()} de ${total}` : base, percentual };
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.encerrar());
    // Sair da aplicação ou trocar de usuário encerra o acompanhamento (e limpa timers e URL de objeto).
    effect(() => {
      const login = this.auth.usuario()?.login ?? null;
      if (this.ativo() && login !== this.dono) {
        this.encerrar();
      }
    });
  }

  // --- início e acompanhamento

  /**
   * Cria um pedido novo e abre a gaveta. Com outro pedido ainda a acompanhar, só avisa e oferece abri-lo.
   * @returns se o pedido foi iniciado
   */
  gerar(idRequerimento: number, referencias: ReferenciaDocumento[]): boolean {
    if (!this.liberarParaNovo()) {
      return false;
    }
    this.encerrar();
    this.dono = this.auth.usuario()?.login ?? null;
    this.idRequerimento.set(idRequerimento);
    this.selecao = [...referencias];
    this.total.set(referencias.length);
    this.ativo.set(true);
    this.gavetaAberta.set(true);
    this.iniciar();
    return true;
  }

  /** Acompanha um pedido que já existe (tela "Dossiês gerados"). Com o mesmo pedido ativo, só reabre a gaveta. */
  acompanhar(id: string, idRequerimento: number): boolean {
    if (this.ativo() && this.pedido === id) {
      this.abrirGaveta();
      return true;
    }
    if (!this.liberarParaNovo()) {
      return false;
    }
    this.encerrar();
    this.dono = this.auth.usuario()?.login ?? null;
    this.idRequerimento.set(idRequerimento);
    this.selecao = [];
    this.total.set(0);
    this.ativo.set(true);
    this.gavetaAberta.set(true);
    this.pedido = id;
    this.pedidoId.set(id);
    this.pedidoCriado.set(true);
    this.fase.set('acompanhando');
    this.consultar();
    return true;
  }

  private liberarParaNovo(): boolean {
    if (!this.bloqueando()) {
      return true;
    }
    this.toast.info('Já existe uma geração em andamento.', { rotulo: 'Abrir', aoClicar: () => this.abrirGaveta() });
    return false;
  }

  abrirGaveta(): void {
    if (this.ativo()) {
      this.gavetaAberta.set(true);
      // Se o pedido já parou, o usuário viu o resultado: o aviso de término não se repete.
      if (this.status() !== null && STATUS_FINAIS.includes(this.status()!)) {
        this.avisadoComoFinal = true;
      }
    }
  }

  /** Documentos enviados no pedido, para listar antes da primeira resposta do servidor. */
  referenciasEnviadas(): readonly ReferenciaDocumento[] {
    return this.selecao;
  }

  /** Minimiza: o pedido segue sendo acompanhado e fica no indicador. Pedido encerrado e visto sai do indicador. */
  fecharGaveta(): void {
    if (!this.gavetaAberta()) {
      return;
    }
    this.gavetaAberta.set(false);
    this.descartarPrevia();
    const status = this.status();
    const terminado =
      status === 'CANCELADO' ||
      status === 'EXPIRADO' ||
      status === 'FALHA' ||
      this.vencido() ||
      (status === 'CONCLUIDO' && this.baixado()) ||
      (this.fase() === 'erro' && !this.pedidoCriado());
    if (terminado) {
      this.dispensar();
    }
    this.gavetaFechada$.next();
  }

  /** Tira o pedido do indicador (concluído ou à espera de decisão que o usuário não quer abrir agora). */
  dispensar(): void {
    this.gavetaAberta.set(false);
    this.encerrar();
  }

  private iniciar(): void {
    this.parar();
    this.andamento.set(null);
    this.mensagemDeErro.set('');
    this.baixado.set(false);
    this.baixadoDireto.set(false);
    this.erroDeDownload.set(false);
    this.vencido.set(false);
    this.avisadoComoFinal = false;
    this.descartarPrevia();
    this.pedido = null;
    this.pedidoId.set(null);
    this.pedidoCriado.set(false);
    this.fase.set('enviando');
    this.intervalo = INTERVALO_INICIAL_MS;
    this.assinatura = this.api.criar(this.idRequerimento()!, this.selecao).subscribe({
      next: (resposta) => {
        this.pedido = resposta.id;
        this.pedidoId.set(resposta.id);
        this.pedidoCriado.set(true);
        this.fase.set('acompanhando');
        this.consultar();
      },
      error: (erro: unknown) => {
        this.fase.set('erro');
        this.mensagemDeErro.set(mensagemDaGaveta(erro));
        this.avisarTermino();
      },
    });
  }

  private consultar(): void {
    if (!this.pedido) {
      return;
    }
    this.assinatura = this.api.andamento(this.pedido).subscribe({
      next: (andamento) => {
        this.andamento.set(andamento);
        this.total.set(andamento.total);
        if (STATUS_FINAIS.includes(andamento.status)) {
          if (andamento.status === 'CONCLUIDO') {
            this.iniciarContagem();
          }
          this.avisarTermino();
          return;
        }
        this.agendar();
      },
      error: (erro: unknown) => {
        this.fase.set('erro');
        this.mensagemDeErro.set(mensagemDaGaveta(erro));
        this.avisarTermino();
      },
    });
  }

  /** Com a gaveta fechada, concluir, pedir decisão ou falhar vira um aviso na tela com a ação "Abrir". */
  private avisarTermino(): void {
    if (this.gavetaAberta() || this.avisadoComoFinal) {
      return;
    }
    const status = this.status();
    let texto: string | null = null;
    if (this.fase() === 'erro') {
      texto = 'Não foi possível acompanhar a geração do dossiê.';
    } else if (status === 'FALHA') {
      texto = this.erroDoDossie()?.motivo ?? 'Não foi possível gerar o dossiê.';
    } else if (status === 'CONCLUIDO') {
      texto = 'Dossiê pronto para baixar.';
    } else if (status === 'PENDENTE_DECISAO') {
      texto = 'A geração do dossiê precisa da sua decisão.';
    }
    if (texto) {
      this.avisadoComoFinal = true;
      this.toast.info(texto, { rotulo: 'Abrir', aoClicar: () => this.abrirGaveta() });
    }
  }

  private agendar(): void {
    this.temporizador = setTimeout(() => this.consultar(), this.intervalo);
    this.intervalo = Math.min(Math.round(this.intervalo * FATOR_DO_INTERVALO), INTERVALO_MAXIMO_MS);
  }

  /** O prazo de validade passa em silêncio: um relógio leve atualiza o "tempo restante" e marca o vencimento. */
  private iniciarContagem(): void {
    this.pararContagem();
    this.atualizarContagem();
    this.relogio = setInterval(() => this.atualizarContagem(), CONTAGEM_REGRESSIVA_MS);
  }

  private atualizarContagem(): void {
    this.agora.set(agoraEmCuiaba());
    if (this.minutosRestantes() === 0) {
      this.vencido.set(true);
      this.pararContagem();
    }
  }

  private parar(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
    this.assinatura?.unsubscribe();
    this.assinatura = null;
    this.pararContagem();
  }

  private pararContagem(): void {
    if (this.relogio !== null) {
      clearInterval(this.relogio);
      this.relogio = null;
    }
  }

  /** Para tudo (timer, inscrições, URL de objeto) e esquece o pedido. O servidor não é avisado: isso não cancela. */
  private encerrar(): void {
    this.parar();
    this.acao?.unsubscribe();
    this.acao = null;
    this.descartarPrevia();
    this.pedido = null;
    this.pedidoId.set(null);
    this.pedidoCriado.set(false);
    this.dono = null;
    this.selecao = [];
    this.andamento.set(null);
    this.mensagemDeErro.set('');
    this.ocupado.set(false);
    this.baixado.set(false);
    this.baixadoDireto.set(false);
    this.erroDeDownload.set(false);
    this.vencido.set(false);
    this.idRequerimento.set(null);
    this.total.set(0);
    this.fase.set('enviando');
    this.ativo.set(false);
    this.gavetaAberta.set(false);
  }

  // --- ações do usuário

  atualizar(): void {
    this.fase.set('acompanhando');
    this.mensagemDeErro.set('');
    this.consultar();
  }

  /**
   * Pedido à espera de decisão: continua o mesmo pedido, refazendo só os documentos com problema (S2.2). Sem pedido
   * (o POST falhou), envia um novo.
   */
  tentarNovamente(): void {
    if (this.pedido && this.status() === 'PENDENTE_DECISAO') {
      this.decidir('TENTAR_NOVAMENTE');
    } else if (this.idRequerimento() !== null) {
      this.iniciar();
    }
  }

  /** Remove os documentos com problema e monta com os já obtidos; a tabela deixa de selecioná-los. */
  removerEGerar(): void {
    const removidos = this.falhas().map((f) => ({ origem: f.origem, id: f.id }));
    this.selecao = this.selecao.filter((r) => !removidos.some((x) => x.origem === r.origem && x.id === r.id));
    removidos.forEach((removido) => this.documentoRemovido$.next(removido));
    this.decidir('REMOVER_E_CONTINUAR');
  }

  private decidir(acao: AcaoDeContinuacao): void {
    if (!this.pedido) {
      return;
    }
    this.parar();
    this.acao?.unsubscribe();
    this.mensagemDeErro.set('');
    this.ocupado.set(true);
    this.avisadoComoFinal = false;
    this.acao = this.api.continuar(this.pedido, acao).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.intervalo = INTERVALO_INICIAL_MS;
        this.consultar();
      },
      error: (erro: unknown) => {
        this.ocupado.set(false);
        this.mensagemDeErro.set(mensagemDaGaveta(erro));
      },
    });
  }

  /** Único caminho que cancela a geração no servidor (DELETE). */
  cancelarGeracao(): void {
    if (!this.pedido) {
      return;
    }
    this.parar();
    this.ocupado.set(true);
    this.acao = this.api.cancelar(this.pedido).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.consultar();
      },
      error: (erro: unknown) => {
        this.ocupado.set(false);
        this.fase.set('erro');
        this.mensagemDeErro.set(mensagemDaGaveta(erro));
      },
    });
  }

  previsualizar(): void {
    if (!this.pedido) {
      return;
    }
    this.limparErroDeArquivo();
    this.ocupado.set(true);
    this.acao = this.api.arquivo(this.pedido, 'inline').subscribe({
      next: (resposta) => {
        this.ocupado.set(false);
        this.descartarPrevia();
        const url = URL.createObjectURL(resposta.body as Blob);
        this.previa.set({ url, seguro: this.sanitizer.bypassSecurityTrustResourceUrl(url) });
      },
      error: (erro: unknown) => this.falhaDeArquivo(erro),
    });
  }

  /**
   * Baixa o PDF por Blob. Só depois de conferir que o corpo chegou inteiro (tamanho e assinatura %PDF-) o arquivo é
   * entregue ao navegador e a gaveta diz "entregue" (nota neutra: a página não sabe se o navegador conseguiu gravar).
   * O servidor apaga o arquivo ao fim do download (RNF05, D77): baixar outra vez exige gerar de novo.
   */
  baixar(): void {
    const pedido = this.pedido;
    if (!pedido) {
      return;
    }
    this.limparErroDeArquivo();
    if (this.entregaPorTicket()) {
      this.baixarDireto(pedido);
      return;
    }
    this.ocupado.set(true);
    this.acao?.unsubscribe();
    this.acao = this.api.arquivo(pedido, 'attachment').subscribe({
      next: (resposta) => {
        void pdfRecebidoInteiro(resposta.body, resposta.headers.get('Content-Length')).then((inteiro) => {
          if (this.pedido !== pedido) {
            return;
          }
          this.ocupado.set(false);
          if (!inteiro) {
            this.erroDeDownload.set(true);
            this.mensagemDeErro.set(MENSAGEM_PDF_INCOMPLETO);
            return;
          }
          const nome = nomeDoContentDisposition(resposta.headers.get('Content-Disposition')) ?? 'dossie.pdf';
          baixarBlob(resposta.body as Blob, nome);
          this.baixado.set(true);
          this.descartarPrevia();
        });
      },
      error: (erro: unknown) => this.falhaDeArquivo(erro),
    });
  }

  /** "Tentar novamente" depois de um download que falhou antes de chegar inteiro: repete o download. */
  tentarDownloadDeNovo(): void {
    this.baixado.set(false);
    this.baixadoDireto.set(false);
    this.baixar();
  }

  /** "Descartar": apaga o pedido no servidor (e o arquivo, se houver) e tira a gaveta e o indicador. */
  descartar(): void {
    const pedido = this.pedido;
    if (!pedido) {
      this.dispensar();
      return;
    }
    this.parar();
    this.ocupado.set(true);
    this.acao?.unsubscribe();
    this.acao = this.api.cancelar(pedido).subscribe({
      next: () => this.dispensar(),
      // Sem conexão ou já esquecido no servidor: some daqui do mesmo jeito; a limpeza do servidor cuida do resto.
      error: () => this.dispensar(),
    });
  }

  private limparErroDeArquivo(): void {
    this.erroDeDownload.set(false);
    this.mensagemDeErro.set('');
  }

  /**
   * Dossiê grande (D49): pede um ticket de uso único e deixa o navegador baixar em streaming, sem Blob. O servidor
   * apaga o arquivo quando o download termina por inteiro.
   */
  private baixarDireto(pedido: string): void {
    this.ocupado.set(true);
    this.acao?.unsubscribe();
    this.acao = this.api.ticket(pedido).subscribe({
      next: ({ ticket }) => {
        this.ocupado.set(false);
        baixarDireto(this.api.urlDeDownloadDireto(pedido, ticket));
        this.baixadoDireto.set(true);
        this.baixado.set(true);
      },
      error: (erro: unknown) => this.falhaDeArquivo(erro),
    });
  }

  /** Erro real do servidor na tela, nunca sucesso (S6, bloco 3). Arquivo perdido: o andamento passa a FALHA. */
  private falhaDeArquivo(erro: unknown): void {
    this.ocupado.set(false);
    this.erroDeDownload.set(true);
    this.mensagemDeErro.set(mensagemDaGaveta(erro));
    const codigo = erro instanceof HttpErrorResponse ? (erro.error as { errorCode?: string } | null)?.errorCode : null;
    if (codigo && ARQUIVO_PERDIDO.includes(codigo)) {
      this.consultar();
    } else if (erro instanceof HttpErrorResponse && erro.status === 410) {
      this.vencido.set(true);
    }
  }

  descartarPrevia(): void {
    const atual = this.previa();
    if (atual) {
      URL.revokeObjectURL(atual.url);
      this.previa.set(null);
    }
  }
}

// --- mapa de mensagens da gaveta (S2.2)

/** Título da gaveta por situação do pedido. */
export const TITULO_DO_STATUS: Record<StatusDossie, string> = {
  AGUARDANDO: 'Gerando dossiê',
  OBTENDO: 'Gerando dossiê',
  MONTANDO: 'Gerando dossiê',
  PENDENTE_DECISAO: 'Documentos com problema',
  CONCLUIDO: 'Dossiê pronto',
  CANCELADO: 'Geração cancelada',
  EXPIRADO: 'Dossiê expirado',
  FALHA: 'Não foi possível gerar o dossiê',
};

/** Rótulo da barra de progresso por situação do pedido. */
export const ROTULO_DO_STATUS: Record<StatusDossie, string> = {
  AGUARDANDO: 'Na fila',
  OBTENDO: 'Obtendo documentos',
  MONTANDO: 'Montando o PDF',
  PENDENTE_DECISAO: 'Aguardando sua decisão',
  CONCLUIDO: 'Concluído',
  CANCELADO: 'Cancelado',
  EXPIRADO: 'Expirado',
  FALHA: 'Falhou',
};

/** Orientação quando a API de arquivos recusa o token (sem citar valor algum). */
export const MENSAGEM_DE_TOKEN =
  'Capture um novo token, atualize o arquivo do token e clique em Tentar novamente.';

/** "Tentar novamente os 2 com problema", "Remover o 1 com problema e gerar". */
export function rotuloDaDecisao(acao: string, quantidade: number, final: string): string {
  return `${acao} ${quantidade === 1 ? 'o 1' : `os ${quantidade}`} com problema${final}`;
}

/** Mensagem da gaveta a partir do errorCode (token recusado orienta a capturar outro, sem citar valor algum). */
export function mensagemDaGaveta(erro: unknown): string {
  if (!(erro instanceof HttpErrorResponse)) {
    return 'Não foi possível concluir a operação. Tente novamente.';
  }
  const problema = erro.error as { errorCode?: string; detail?: string } | null;
  if (problema?.errorCode === 'PARAMETRO_INVALIDO' && problema.detail) {
    // O detalhe de seleção inválida é escrito para o usuário ("O documento X está indisponível..."), sem dado técnico.
    return problema.detail;
  }
  return mensagemRealDoErro(erro);
}
