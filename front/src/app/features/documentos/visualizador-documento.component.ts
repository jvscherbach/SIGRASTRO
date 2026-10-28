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

import { mensagemDoCodigo, mensagemDoErro } from '../../core/mensagens-erro';
import { baixarBlob } from '../../shared/arquivo-local';
import { IconeComponent } from '../../shared/icone.component';
import { ConteudoApiService, LIMITE_DO_BLOB_MB } from './documentos-api.service';
import { classeDaExtensao, selecionavel } from './documentos.logic';
import { DocumentoItem } from './documentos.models';

type Fase = 'carregando' | 'pronto' | 'erro' | 'sem-conversao' | 'grande' | 'sigiloso';

/** Erros em que a conversão não vai dar certo de novo: o caminho é o original. */
const CODIGOS_SEM_CONVERSAO = new Set([
  'FORMATO_NAO_SUPORTADO',
  'DOCUMENTO_PROTEGIDO_POR_SENHA',
  'DOCUMENTO_CORROMPIDO',
  'CONVERSAO_RECUSADA',
]);


/**
 * Modal de visualização de um documento (S3, RF14). Busca o PDF pelo HttpClient (token no cabeçalho, nunca na URL),
 * exibe por URL de objeto e a revoga ao fechar. O acesso e o sigilo são do back-end a cada requisição; aqui o sigiloso
 * sem perfil nem chama o servidor, só mostra o motivo. O modal também seleciona ou desmarca o documento na tabela.
 */
@Component({
  selector: 'app-visualizador-documento',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  templateUrl: './visualizador-documento.component.html',
})
export class VisualizadorDocumentoComponent implements OnInit {
  private readonly api = inject(ConteudoApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  readonly documento = input.required<DocumentoItem>();
  /** O documento está marcado na tabela? */
  readonly selecionado = input(false);

  readonly alternarSelecao = output<void>();
  readonly fechar = output<void>();

  protected readonly fase = signal<Fase>('carregando');
  protected readonly mensagem = signal('');
  protected readonly pdf = signal<{ url: string; seguro: SafeResourceUrl } | null>(null);
  protected readonly convertidoDe = signal<string | null>(null);
  protected readonly tamanhoEmMb = signal(0);
  protected readonly baixando = signal(false);
  protected readonly erroDoDownload = signal('');
  /** O erro permite oferecer o original (a conversão não vai funcionar de novo). */
  protected readonly podeBaixarOriginal = signal(false);

  private assinatura: Subscription | null = null;
  private download: Subscription | null = null;

  protected readonly podeSelecionar = computed(() => selecionavel(this.documento()));
  protected readonly nome = computed(() => this.documento().titulo || `Documento ${this.documento().id}`);
  protected readonly tipo = computed(() => (this.documento().extensao ?? '').toUpperCase() || 'tipo não informado');
  protected readonly classeDaExtensao = classeDaExtensao;
  protected readonly convertivel = computed(() => {
    const t = this.documento().tipoArquivo;
    return t === 'PLANILHA' || t === 'TEXTO' || t === 'IMAGEM';
  });
  protected readonly textoDeCarga = computed(() =>
    this.convertivel() ? 'Convertendo para PDF…' : 'Carregando o documento…',
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.encerrar());
  }

  ngOnInit(): void {
    // Como a gaveta: o modal sai da folha do detalhe e fica sobre a barra do topo (filho do body).
    document.body.appendChild(this.anfitriao);
    this.carregar();
  }

  protected carregar(): void {
    this.assinatura?.unsubscribe();
    this.descartarPdf();
    this.mensagem.set('');
    this.convertidoDe.set(null);
    this.podeBaixarOriginal.set(false);
    const documento = this.documento();
    if (documento.bloqueio === 'SIGILOSO') {
      this.fase.set('sigiloso');
      this.mensagem.set(mensagemDoCodigo('DOCUMENTO_SIGILOSO'));
      return;
    }
    this.fase.set('carregando');
    this.assinatura = this.api.conteudo(documento, 'pdf', LIMITE_DO_BLOB_MB).subscribe({
      next: (conteudo) => {
        if (conteudo.tipo === 'grande') {
          this.tamanhoEmMb.set(conteudo.megabytes);
          this.fase.set('grande');
          return;
        }
        const url = URL.createObjectURL(conteudo.blob);
        this.pdf.set({ url, seguro: this.sanitizer.bypassSecurityTrustResourceUrl(url) });
        this.convertidoDe.set(conteudo.convertidoDe);
        this.fase.set('pronto');
      },
      error: (erro: unknown) => this.aoFalhar(erro),
    });
  }

  private aoFalhar(erro: unknown): void {
    const codigo = erro instanceof HttpErrorResponse ? (erro.error as { errorCode?: string } | null)?.errorCode : undefined;
    this.mensagem.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível carregar o documento.');
    const semConversao = !!codigo && CODIGOS_SEM_CONVERSAO.has(codigo);
    this.podeBaixarOriginal.set(semConversao);
    this.fase.set(codigo === 'FORMATO_NAO_SUPORTADO' ? 'sem-conversao' : 'erro');
  }

  /** O original, como veio. Sem limite de tamanho: o usuário escolheu baixá-lo. */
  protected baixarOriginal(): void {
    this.download?.unsubscribe();
    this.baixando.set(true);
    this.erroDoDownload.set('');
    this.download = this.api.conteudo(this.documento(), 'original', null).subscribe({
      next: (conteudo) => {
        this.baixando.set(false);
        if (conteudo.tipo === 'blob') {
          baixarBlob(conteudo.blob, conteudo.nome ?? this.documento().nomeArquivo ?? `documento-${this.documento().id}`);
        }
      },
      error: (erro: unknown) => {
        this.baixando.set(false);
        this.erroDoDownload.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível baixar o original.');
      },
    });
  }

  protected aoClicarNoFundo(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.fechar.emit();
    }
  }

  private descartarPdf(): void {
    const atual = this.pdf();
    if (atual) {
      URL.revokeObjectURL(atual.url);
      this.pdf.set(null);
    }
  }

  private encerrar(): void {
    this.assinatura?.unsubscribe();
    this.download?.unsubscribe();
    this.descartarPdf();
    this.anfitriao.remove();
  }
}
