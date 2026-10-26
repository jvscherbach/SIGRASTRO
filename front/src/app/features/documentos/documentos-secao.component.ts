import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarData } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { DocumentosApiService } from './documentos-api.service';
import {
  CampoDeOrdem,
  ORDEM_PADRAO,
  Ordem,
  chaveDe,
  classeDaExtensao,
  filtrar,
  ordenar,
  referenciaDe,
  rotuloDoBloqueio,
  selecaoPadrao,
  selecionavel,
} from './documentos.logic';
import { DocumentoItem, DocumentosResposta, ReferenciaDocumento } from './documentos.models';
import { GavetaDossieComponent } from './gaveta-dossie.component';

/**
 * Seção Documentos do detalhe (RF06 a RF10, RF20): todos os documentos do requerimento, busca, filtro de origem,
 * ordenação, seleção e geração do dossiê. A visualização individual é da S3. O que pode ou não ser selecionado vem da
 * API (`bloqueio`); a interface só obedece, a regra é do back-end (RNF10).
 */
@Component({
  selector: 'app-documentos-secao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, GavetaDossieComponent],
  templateUrl: './documentos-secao.component.html',
})
export class DocumentosSecaoComponent implements OnInit {
  private readonly api = inject(DocumentosApiService);

  readonly idRequerimento = input.required<number>();

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly resposta = signal<DocumentosResposta | null>(null);
  protected readonly busca = signal('');
  protected readonly origem = signal('');
  protected readonly ordem = signal<Ordem>(ORDEM_PADRAO);
  protected readonly selecionados = signal<ReadonlySet<string>>(new Set());
  protected readonly gaveta = signal<ReferenciaDocumento[] | null>(null);

  protected readonly itens = computed(() => this.resposta()?.itens ?? []);
  protected readonly totais = computed(() => this.resposta()?.totais ?? null);
  protected readonly origens = computed(() => {
    const vistas = new Map<string, string>();
    for (const d of this.itens()) {
      vistas.set(d.origem, d.origemDescricao);
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

  protected readonly formatarData = formatarData;
  protected readonly chaveDe = chaveDe;
  protected readonly classeDaExtensao = classeDaExtensao;
  protected readonly rotuloDoBloqueio = rotuloDoBloqueio;
  protected readonly selecionavel = selecionavel;

  ngOnInit(): void {
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
      this.gaveta.set(escolhidos.map(referenciaDe));
    }
  }

  protected fecharGaveta(): void {
    this.gaveta.set(null);
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
