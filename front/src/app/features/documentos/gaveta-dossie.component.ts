import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
} from '@angular/core';

import { AcompanhamentoDeDossieService, MENSAGEM_DE_TOKEN, ROTULO_DO_STATUS, TITULO_DO_STATUS, rotuloDaDecisao } from './acompanhamento-dossie.service';
import { FocoPresoDirective } from '../../shared/foco-preso.directive';
import { IconeComponent } from '../../shared/icone.component';
import { SituacaoItemDossie } from './documentos.models';

const ROTULO_DO_ITEM: Record<SituacaoItemDossie, string> = {
  AGUARDANDO: 'Aguardando',
  OBTENDO: 'Obtendo arquivo…',
  OK: 'Obtido',
  FALHA: 'Falhou',
  REMOVIDO: 'Removido',
};

/**
 * Gaveta de geração do dossiê (RF16, RF17): só a janela que mostra o estado do `AcompanhamentoDeDossieService`. Fechar
 * (fundo, Esc, "Fechar") minimiza e deixa a geração seguir no indicador da barra do topo; destruir a gaveta nunca cancela
 * o pedido nem o acompanhamento. Fica no corpo do documento (acima da barra do topo), hospedada pelo shell.
 */
@Component({
  selector: 'app-gaveta-dossie',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, FocoPresoDirective],
  templateUrl: './gaveta-dossie.component.html',
  host: { '(document:keydown.escape)': 'aoEsc()' },
})
export class GavetaDossieComponent implements OnInit {
  private readonly servico = inject(AcompanhamentoDeDossieService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  protected readonly fase = this.servico.fase;
  protected readonly mensagemDeErro = this.servico.mensagemDeErro;
  protected readonly mensagemDeToken = MENSAGEM_DE_TOKEN;
  protected readonly ocupado = this.servico.ocupado;
  protected readonly baixado = this.servico.baixado;
  protected readonly baixadoDireto = this.servico.baixadoDireto;
  protected readonly erroDeDownload = this.servico.erroDeDownload;
  protected readonly vencido = this.servico.vencido;
  protected readonly previa = this.servico.previa;
  protected readonly pedidoCriado = this.servico.pedidoCriado;
  protected readonly andamento = this.servico.andamento;
  protected readonly status = this.servico.status;
  protected readonly total = this.servico.total;
  protected readonly processados = this.servico.processados;
  protected readonly percentual = this.servico.percentual;
  protected readonly emAndamento = this.servico.emAndamento;
  protected readonly erroDoDossie = this.servico.erroDoDossie;
  protected readonly tokenRecusado = this.servico.tokenRecusado;
  protected readonly falhas = this.servico.falhas;
  protected readonly comProblema = this.servico.comProblema;
  protected readonly obtidos = this.servico.obtidos;
  protected readonly podeRemover = this.servico.podeRemover;
  protected readonly entregaPorTicket = this.servico.entregaPorTicket;
  protected readonly tamanhoEmMb = this.servico.tamanhoEmMb;
  protected readonly expiraAs = this.servico.expiraAs;
  protected readonly minutosRestantes = this.servico.minutosRestantes;
  protected readonly rotuloTentar = computed(() => rotuloDaDecisao('Tentar novamente', this.comProblema(), ''));
  protected readonly rotuloRemover = computed(() => rotuloDaDecisao('Remover', this.comProblema(), ' e gerar'));
  /** Pedido aberto pela tela Dossiês gerados: gerar de novo é no detalhe do requerimento. */
  protected readonly semGerarNovamente = computed(() => this.servico.referenciasEnviadas().length === 0);

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      // Só a janela some: o pedido e o polling seguem no serviço. A pré-visualização é da janela.
      this.servico.descartarPrevia();
      this.anfitriao.remove();
    });
  }

  ngOnInit(): void {
    // A gaveta fica sobre a barra do topo: sai da folha da tela (que cria um contexto de empilhamento abaixo da barra)
    // e passa a ser filha do body. A remoção ao destruir está no construtor.
    document.body.appendChild(this.anfitriao);
  }

  // --- ações: tudo vai para o serviço

  /** Fundo, Esc e "Fechar": minimiza. Nunca cancela. */
  protected minimizar(): void {
    this.servico.fecharGaveta();
  }

  /** Esc em qualquer lugar: fecha a pré-visualização aberta ou, sem ela, minimiza a gaveta. */
  protected aoEsc(): void {
    if (this.previa()) {
      this.fecharPrevia();
    } else {
      this.minimizar();
    }
  }

  protected aoClicarNoFundo(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.minimizar();
    }
  }

  protected atualizar(): void {
    this.servico.atualizar();
  }

  protected tentarNovamente(): void {
    this.servico.tentarNovamente();
  }

  protected removerEGerar(): void {
    this.servico.removerEGerar();
  }

  protected cancelarGeracao(): void {
    this.servico.cancelarGeracao();
  }

  protected previsualizar(): void {
    this.servico.previsualizar();
  }

  protected fecharPrevia(): void {
    this.servico.descartarPrevia();
  }

  protected baixar(): void {
    this.servico.baixar();
  }

  /** Repete um download que falhou antes de chegar inteiro (o servidor ainda tem o arquivo). */
  protected tentarDownloadDeNovo(): void {
    this.servico.tentarDownloadDeNovo();
  }

  /** Cria um pedido novo com a mesma seleção. */
  protected gerarNovamente(): void {
    this.servico.tentarNovamente();
  }

  /** Apaga o pedido no servidor e fecha. */
  protected descartar(): void {
    this.servico.descartar();
  }

  /** O arquivo ainda pode ser baixado de novo (pronto e dentro da validade). */
  protected podeBaixarDeNovo(): boolean {
    return this.status() === 'CONCLUIDO' && !this.vencido();
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
    return this.servico
      .referenciasEnviadas()
      .map((r) => ({ chave: `${r.origem}:${r.id}`, titulo: `Documento ${r.id}`, situacao: 'AGUARDANDO' }));
  }
}
