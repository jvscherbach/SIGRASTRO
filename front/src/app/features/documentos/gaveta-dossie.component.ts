import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { baixarBlob, nomeDoContentDisposition } from '../../shared/arquivo-local';
import { agoraEmCuiaba, formatarDataHora, minutosAte } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { DossiesApiService } from './documentos-api.service';
import {
  AcaoDeContinuacao,
  AndamentoDossie,
  ErroDossie,
  ReferenciaDocumento,
  STATUS_FINAIS,
  SituacaoItemDossie,
  StatusDossie,
} from './documentos.models';

/** Consulta do andamento: começa em 1 s e cresce 50% a cada resposta, até 5 s. */
export const INTERVALO_INICIAL_MS = 1000;
export const INTERVALO_MAXIMO_MS = 5000;
const FATOR_DO_INTERVALO = 1.5;
const CONTAGEM_REGRESSIVA_MS = 30_000;

const CREDENCIAL_RECUSADA = 'CREDENCIAL_ARQUIVOS_RECUSADA';

type Fase = 'enviando' | 'acompanhando' | 'erro';

/**
 * Gaveta de geração do dossiê (RF16, RF17). Cria o pedido, acompanha o andamento por polling com intervalo crescente
 * e para ao chegar a um estado final ou quando a gaveta é fechada (sem inscrição nem timer pendurados). Pré-visualização
 * e download usam o HttpClient (token no cabeçalho) e Blob: o token nunca vai para a URL.
 */
@Component({
  selector: 'app-gaveta-dossie',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  templateUrl: './gaveta-dossie.component.html',
})
export class GavetaDossieComponent implements OnInit {
  private readonly api = inject(DossiesApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  readonly idRequerimento = input.required<number>();
  readonly referencias = input.required<ReferenciaDocumento[]>();

  /** O documento tirado da seleção por "Remover documento e gerar". */
  readonly documentoRemovido = output<ReferenciaDocumento>();
  readonly fechar = output<void>();

  protected readonly fase = signal<Fase>('enviando');
  protected readonly andamento = signal<AndamentoDossie | null>(null);
  protected readonly mensagemDeErro = signal('');
  protected readonly mensagemDeToken = MENSAGEM_DE_TOKEN;
  protected readonly ocupado = signal(false);
  protected readonly baixado = signal(false);
  protected readonly vencido = signal(false);
  protected readonly previa = signal<{ url: string; seguro: SafeResourceUrl } | null>(null);
  protected readonly agora = signal(agoraEmCuiaba());

  /** A seleção corrente: "Remover documento e gerar" a reduz e o próximo pedido usa a lista nova. */
  private selecao: ReferenciaDocumento[] = [];
  private pedido: string | null = null;
  private intervalo = INTERVALO_INICIAL_MS;
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private assinatura: Subscription | null = null;
  private acao: Subscription | null = null;
  private relogio: ReturnType<typeof setInterval> | null = null;

  protected readonly status = computed(() => this.andamento()?.status ?? null);
  protected readonly pedidoCriado = signal(false);
  protected readonly total = computed(() => this.andamento()?.total ?? this.selecao.length);
  protected readonly processados = computed(() => this.andamento()?.processados ?? 0);
  protected readonly percentual = computed(() => {
    const total = this.total();
    return total ? Math.round((this.processados() / total) * 100) : 0;
  });
  protected readonly emAndamento = computed(
    () => this.fase() === 'enviando' || (this.status() !== null && !STATUS_FINAIS.includes(this.status()!)),
  );
  protected readonly erroDoDossie = computed<ErroDossie | null>(() => this.andamento()?.erro ?? null);
  protected readonly tokenRecusado = computed(() => this.erroDoDossie()?.codigo === CREDENCIAL_RECUSADA);
  protected readonly falhas = computed(() => this.andamento()?.falhas ?? []);
  /**
   * Itens que a decisão vai refazer ou remover: os não resolvidos (em falha e, com a credencial recusada, também os
   * que nem chegaram a ser tentados). É o N dos botões.
   */
  protected readonly comProblema = computed(() =>
    this.status() === 'PENDENTE_DECISAO'
      ? (this.andamento()?.itens ?? []).filter((i) => i.situacao !== 'OK' && i.situacao !== 'REMOVIDO').length
      : 0,
  );
  protected readonly obtidos = computed(() => (this.andamento()?.itens ?? []).filter((i) => i.situacao === 'OK').length);
  /** Remover só faz sentido com algum documento já obtido para montar (o servidor recusa o contrário). */
  protected readonly podeRemover = computed(() => this.comProblema() > 0 && this.obtidos() > 0);
  protected readonly rotuloTentar = computed(() => rotuloDaDecisao('Tentar novamente', this.comProblema(), ''));
  protected readonly rotuloRemover = computed(() => rotuloDaDecisao('Remover', this.comProblema(), ' e gerar'));
  protected readonly expiraAs = computed(() => formatarDataHora(this.andamento()?.expiraEm));
  protected readonly minutosRestantes = computed(() => {
    this.agora();
    return minutosAte(this.andamento()?.expiraEm);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.encerrar());
  }

  ngOnInit(): void {
    // A gaveta fica sobre a barra do topo: sai da folha do detalhe (que cria um contexto de empilhamento abaixo da barra)
    // e passa a ser filha do body. A remoção ao destruir está em encerrar().
    document.body.appendChild(this.anfitriao);
    this.selecao = [...this.referencias()];
    this.iniciar();
  }

  // --- pedido e acompanhamento

  private iniciar(): void {
    this.parar();
    this.andamento.set(null);
    this.mensagemDeErro.set('');
    this.baixado.set(false);
    this.vencido.set(false);
    this.descartarPrevia();
    this.pedidoCriado.set(false);
    this.fase.set('enviando');
    this.intervalo = INTERVALO_INICIAL_MS;
    this.assinatura = this.api.criar(this.idRequerimento(), this.selecao).subscribe({
      next: (resposta) => {
        this.pedido = resposta.id;
        this.pedidoCriado.set(true);
        this.fase.set('acompanhando');
        this.consultar();
      },
      error: (erro: unknown) => {
        this.fase.set('erro');
        this.mensagemDeErro.set(mensagemDaGaveta(erro));
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
        if (STATUS_FINAIS.includes(andamento.status)) {
          if (andamento.status === 'CONCLUIDO') {
            this.iniciarContagem();
          }
          return;
        }
        this.agendar();
      },
      error: (erro: unknown) => {
        this.fase.set('erro');
        this.mensagemDeErro.set(mensagemDaGaveta(erro));
      },
    });
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

  private encerrar(): void {
    this.parar();
    this.acao?.unsubscribe();
    this.acao = null;
    this.descartarPrevia();
    this.anfitriao.remove();
  }

  // --- ações do usuário

  protected aoClicarNoFundo(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.fechar.emit();
    }
  }

  protected atualizar(): void {
    this.fase.set('acompanhando');
    this.mensagemDeErro.set('');
    this.consultar();
  }

  /**
   * Pedido à espera de decisão: continua o mesmo pedido, refazendo só os documentos com problema (S2.2). Sem pedido
   * (o POST falhou), envia um novo.
   */
  protected tentarNovamente(): void {
    if (this.pedido && this.status() === 'PENDENTE_DECISAO') {
      this.decidir('TENTAR_NOVAMENTE');
    } else {
      this.iniciar();
    }
  }

  /** Remove os documentos com problema e monta com os já obtidos; a tabela deixa de selecioná-los. */
  protected removerEGerar(): void {
    const removidos = this.falhas().map((f) => ({ origem: f.origem, id: f.id }));
    this.selecao = this.selecao.filter((r) => !removidos.some((x) => x.origem === r.origem && x.id === r.id));
    removidos.forEach((removido) => this.documentoRemovido.emit(removido));
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

  protected cancelarGeracao(): void {
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

  protected previsualizar(): void {
    if (!this.pedido) {
      return;
    }
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

  protected fecharPrevia(): void {
    this.descartarPrevia();
  }

  protected baixar(): void {
    if (!this.pedido) {
      return;
    }
    this.ocupado.set(true);
    this.acao = this.api.arquivo(this.pedido, 'attachment').subscribe({
      next: (resposta) => {
        this.ocupado.set(false);
        const nome = nomeDoContentDisposition(resposta.headers.get('Content-Disposition')) ?? 'dossie.pdf';
        baixarBlob(resposta.body as Blob, nome);
        // O servidor apaga o arquivo ao fim do download (RNF05): nova pré-visualização ou novo download exigem gerar de novo.
        this.baixado.set(true);
        this.descartarPrevia();
      },
      error: (erro: unknown) => this.falhaDeArquivo(erro),
    });
  }

  private falhaDeArquivo(erro: unknown): void {
    this.ocupado.set(false);
    this.mensagemDeErro.set(mensagemDaGaveta(erro));
    if (erro instanceof HttpErrorResponse && erro.status === 410) {
      this.vencido.set(true);
    }
  }

  private descartarPrevia(): void {
    const atual = this.previa();
    if (atual) {
      URL.revokeObjectURL(atual.url);
      this.previa.set(null);
    }
  }

  // --- apresentação

  protected iconeDoItem(situacao: SituacaoItemDossie): string {
    return { AGUARDANDO: 'clock', OBTENDO: 'refresh', OK: 'check', FALHA: 'alert', REMOVIDO: 'close' }[situacao];
  }

  protected classeDoItem(situacao: SituacaoItemDossie): string {
    return { AGUARDANDO: '', OBTENDO: 'run', OK: 'ok', FALHA: 'err', REMOVIDO: 'off' }[situacao];
  }

  /** Situação do item, com o motivo quando falhou ou foi removido. */
  protected rotuloDoItem(situacao: SituacaoItemDossie, motivo?: string | null): string {
    const rotulo = ROTULO_DO_ITEM[situacao];
    return motivo && (situacao === 'FALHA' || situacao === 'REMOVIDO') ? `${rotulo}: ${motivo}` : rotulo;
  }

  protected titulo(): string {
    if (this.fase() === 'erro') {
      return 'Não foi possível gerar';
    }
    if (this.vencido()) {
      return TITULO_DO_STATUS.EXPIRADO;
    }
    const status = this.status();
    return status ? TITULO_DO_STATUS[status] : TITULO_DO_STATUS.AGUARDANDO;
  }

  /** Rótulo da barra de progresso. */
  protected rotuloDoStatus(): string {
    if (this.vencido()) {
      return ROTULO_DO_STATUS.EXPIRADO;
    }
    const status = this.status();
    return status ? ROTULO_DO_STATUS[status] : ROTULO_DO_STATUS.AGUARDANDO;
  }

  /** Itens da lista: os do andamento, ou — antes da primeira resposta — a seleção enviada. */
  protected itens(): { chave: string; titulo: string; situacao: SituacaoItemDossie; motivo?: string }[] {
    const andamento = this.andamento();
    if (andamento) {
      return andamento.itens.map((i) => ({
        chave: `${i.origem}:${i.id}`,
        titulo: i.titulo,
        situacao: i.situacao,
        motivo: i.motivo,
      }));
    }
    return this.selecao.map((r) => ({ chave: `${r.origem}:${r.id}`, titulo: `Documento ${r.id}`, situacao: 'AGUARDANDO' }));
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
};

export const ROTULO_DO_ITEM: Record<SituacaoItemDossie, string> = {
  AGUARDANDO: 'Aguardando',
  OBTENDO: 'Obtendo arquivo…',
  OK: 'Obtido',
  FALHA: 'Falhou',
  REMOVIDO: 'Removido',
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
  return mensagemDoErro(erro);
}
