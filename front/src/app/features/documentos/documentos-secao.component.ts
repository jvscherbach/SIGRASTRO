import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';

import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarData } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { AcompanhamentoDeDossieService } from './acompanhamento-dossie.service';
import { DocumentosApiService } from './documentos-api.service';
import {
  CampoDeOrdem,
  ORDEM_PADRAO,
  Ordem,
  chaveDe,
  classeDaExtensao,
  nomeExibido,
  filtrar,
  ordenar,
  referenciaDe,
  rotuloDoBloqueio,
  selecaoPadrao,
  selecionavel,
  semConversao,
  seraConvertido,
  visualizavel,
} from './documentos.logic';
import { DocumentoItem, DocumentosResposta, ReferenciaDocumento } from './documentos.models';
import { GeometriasSecaoComponent } from './geometrias-secao.component';
import { VisualizadorDocumentoComponent } from './visualizador-documento.component';

/** Textos da faixa de aviso por código (S4, docs/08); código desconhecido usa a mensagem da API. */
const TEXTO_DO_AVISO: Record<string, string> = {
  SCORP_INDISPONIVEL:
    'Os documentos pessoais (SCORP) não puderam ser consultados agora. Os demais documentos foram listados. Atualize a página para tentar de novo.',
  SCORP_PARCIAL:
    'Parte dos documentos pessoais (SCORP) não pôde ser consultada agora: alguns podem estar faltando. Os demais documentos foram listados.',
};

/**
 * Seção Documentos do detalhe (RF06 a RF10, RF14, RF20): todos os documentos do requerimento, busca, filtro de origem,
 * ordenação, seleção, visualização individual (modal, S3) e geração do dossiê. O que pode ou não ser selecionado vem da
 * API (`bloqueio`); a interface só obedece, a regra é do back-end (RNF10).
 */
@Component({
  selector: 'app-documentos-secao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, GeometriasSecaoComponent, VisualizadorDocumentoComponent],
  templateUrl: './documentos-secao.component.html',
})
export class DocumentosSecaoComponent implements OnInit {
  private readonly api = inject(DocumentosApiService);
  private readonly acompanhamento = inject(AcompanhamentoDeDossieService);
  private readonly destroyRef = inject(DestroyRef);

  readonly idRequerimento = input.required<number>();

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly resposta = signal<DocumentosResposta | null>(null);
  protected readonly busca = signal('');
  protected readonly origem = signal('');
  protected readonly ordem = signal<Ordem>(ORDEM_PADRAO);
  protected readonly selecionados = signal<ReadonlySet<string>>(new Set());
  /** Documento aberto no modal de visualização. */
  protected readonly visualizando = signal<DocumentoItem | null>(null);

  protected readonly itens = computed(() => this.resposta()?.itens ?? []);
  protected readonly totais = computed(() => this.resposta()?.totais ?? null);
  /** Geometrias em lista própria (S4, RN10): nunca entram em itens, na seleção nem no dossiê. */
  protected readonly geometrias = computed(() => this.resposta()?.geometrias ?? []);
  protected readonly geometriasOmitidas = computed(() => this.resposta()?.totais.geometriasOmitidas ?? 0);
  /** Avisos do SCORP (S4): só da tela; não bloqueiam seleção nem geração. */
  protected readonly avisos = computed(() =>
    (this.resposta()?.avisos ?? []).map((a) => ({ codigo: a.codigo, texto: TEXTO_DO_AVISO[a.codigo] ?? a.mensagem })),
  );
  protected readonly reconsultando = signal(false);
  protected readonly origens = computed(() => {
    const vistas = new Map<string, string>();
    for (const d of this.itens()) {
      // SCORP traz o papel na descrição ("Pessoal · Interessado"); no filtro, uma opção para todos os papéis.
      vistas.set(d.origem, d.origem === 'SCORP' ? 'Pessoal' : d.origemDescricao);
    }
    return Array.from(vistas, ([codigo, descricao]) => ({ codigo, descricao })).sort((a, b) => a.descricao.localeCompare(b.descricao, 'pt-BR'));
  });
  protected readonly listados = computed(() => ordenar(filtrar(this.itens(), this.busca(), this.origem()), this.ordem()));
  protected readonly selecionaveisListados = computed(() => this.listados().filter(selecionavel));
  protected readonly todosMarcados = computed(
    () =>
      this.selecionaveisListados().length > 0 && this.selecionaveisListados().every((d) => this.selecionados().has(chaveDe(d))),
  );
  protected readonly quantidadeSelecionada = computed(() => this.selecionados().size);
  /** Não PDF que ficou sem conversão (aviso do topo); os convertíveis têm o próprio aviso (`totais.convertiveis`). */
  protected readonly quantidadeSemConversao = computed(() => this.itens().filter(semConversao).length);

  protected readonly formatarData = formatarData;
  protected readonly chaveDe = chaveDe;
  protected readonly classeDaExtensao = classeDaExtensao;
  protected nome(d: DocumentoItem): string {
    return nomeExibido(d, `Documento ${d.id}`);
  }
  protected readonly rotuloDoBloqueio = rotuloDoBloqueio;
  protected readonly selecionavel = selecionavel;
  protected readonly visualizavel = visualizavel;
  protected readonly seraConvertido = seraConvertido;

  ngOnInit(): void {
    // "Remover documento e gerar" (gaveta, no shell) tira o documento também da seleção desta tela.
    this.acompanhamento.documentoRemovido$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((r) => this.tirarDaSelecao(r));
    this.carregar();
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set('');
    this.api.listar(this.idRequerimento()).subscribe({
      next: (resposta) => {
        this.resposta.set(resposta);
        this.selecionados.set(selecaoPadrao(resposta.itens));
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.carregando.set(false);
        this.erro.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível carregar os documentos.');
      },
    });
  }

  /**
   * "Consultar novamente" (aviso do SCORP): refaz só a lista, sem esconder a tabela. A seleção feita é mantida; itens
   * novos (os pessoais que voltaram) entram pela seleção padrão.
   */
  protected consultarNovamente(): void {
    if (this.reconsultando()) {
      return;
    }
    const antes = new Set(this.itens().map(chaveDe));
    this.reconsultando.set(true);
    this.api.listar(this.idRequerimento()).subscribe({
      next: (resposta) => {
        const padrao = selecaoPadrao(resposta.itens);
        const atual = this.selecionados();
        const nova = new Set<string>();
        for (const d of resposta.itens) {
          const chave = chaveDe(d);
          if (selecionavel(d) && (antes.has(chave) ? atual.has(chave) : padrao.has(chave))) {
            nova.add(chave);
          }
        }
        this.resposta.set(resposta);
        this.selecionados.set(nova);
        this.reconsultando.set(false);
      },
      error: () => this.reconsultando.set(false),
    });
  }

  protected digitarBusca(evento: Event): void {
    this.busca.set((evento.target as HTMLInputElement).value);
  }

  protected escolherOrigem(evento: Event): void {
    this.origem.set((evento.target as HTMLSelectElement).value);
  }

  protected ordenarPor(campo: CampoDeOrdem): void {
    this.ordem.update((atual) => ({ campo, direcao: atual.campo === campo ? (atual.direcao === 1 ? -1 : 1) : 1 }));
  }

  protected ariaSort(campo: CampoDeOrdem): 'ascending' | 'descending' | 'none' {
    const { campo: atual, direcao } = this.ordem();
    return atual !== campo ? 'none' : direcao === 1 ? 'ascending' : 'descending';
  }

  protected alternar(documento: DocumentoItem): void {
    if (!selecionavel(documento)) {
      return;
    }
    const chave = chaveDe(documento);
    this.selecionados.update((atual) => {
      const novo = new Set(atual);
      if (!novo.delete(chave)) {
        novo.add(chave);
      }
      return novo;
    });
  }

  /** Marca ou desmarca todos os listados (já filtrados) que podem ser selecionados. */
  protected alternarTodos(marcar: boolean): void {
    this.selecionados.update((atual) => {
      const novo = new Set(atual);
      for (const d of this.selecionaveisListados()) {
        if (marcar) {
          novo.add(chaveDe(d));
        } else {
          novo.delete(chaveDe(d));
        }
      }
      return novo;
    });
  }

  protected usarSelecaoPadrao(): void {
    this.selecionados.set(selecaoPadrao(this.itens()));
  }

  protected limparSelecao(): void {
    this.selecionados.set(new Set());
  }

  protected gerar(): void {
    const escolhidos = this.itens().filter((d) => this.selecionados().has(chaveDe(d)) && selecionavel(d));
    if (escolhidos.length) {
      this.acompanhamento.gerar(this.idRequerimento(), escolhidos.map(referenciaDe));
    }
  }

  protected visualizar(documento: DocumentoItem): void {
    if (visualizavel(documento)) {
      this.visualizando.set(documento);
    }
  }

  protected fecharVisualizacao(): void {
    this.visualizando.set(null);
  }

  /** "Remover documento e gerar": o documento sai também da seleção da tela. */
  protected tirarDaSelecao(removido: ReferenciaDocumento): void {
    this.selecionados.update((atual) => {
      const novo = new Set(atual);
      novo.delete(chaveDe(removido));
      return novo;
    });
  }
}
