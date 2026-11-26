import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarDataHora } from '../../shared/formato';
import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';
import { PainelApiService } from './painel-api.service';
import { CargaDoPainel, PainelResposta, TipoDeCarga } from './painel.models';

/** Releitura enquanto o painel prepara (ou espera a atualização pedida): a cada 5 s, no máximo 2 minutos. */
export const INTERVALO_DA_RELEITURA_MS = 5_000;
export const MAXIMO_DE_RELEITURAS = 24;

export const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** Avisos que a tela já diz com texto próprio (nota do gráfico ou selo): não se repetem na lista geral. */
const AVISOS_COM_LUGAR_PROPRIO = ['SOMA_DAS_BARRAS_MAIOR_QUE_O_TOTAL', 'NOMES_OCULTOS', 'TRUNCADO'];

export const ROTULO_DO_TIPO: Record<TipoDeCarga, string> = {
  SERVIDOR: 'Servidor',
  AREA: 'Área',
  INTERESSADO: 'Interessado',
  NAO_ATRIBUIDA: 'Não atribuída',
  NAO_IDENTIFICADO: 'Não identificado',
  OUTROS: 'Outros',
};

const ICONE_DO_TIPO: Record<TipoDeCarga, string> = {
  SERVIDOR: 'user',
  AREA: 'building',
  INTERESSADO: 'file',
  NAO_ATRIBUIDA: 'alert',
  NAO_IDENTIFICADO: 'info',
  OUTROS: 'layers',
};

/** Categorias sem nome de pessoa: ganham ícone próprio no gráfico 3. */
const TIPOS_SEM_NOME: readonly TipoDeCarga[] = ['AREA', 'INTERESSADO', 'NAO_ATRIBUIDA', 'NAO_IDENTIFICADO'];

/** Barra do gráfico 1 pronta para o SVG (coordenadas no viewBox). */
export interface BarraDaSerie {
  x: number;
  y: number;
  largura: number;
  altura: number;
  valor: number;
  serie: 'protocolados' | 'concluidos';
}

/** Fatia da pizza da fila por etapa (coordenadas no viewBox 0 0 200 200). */
export interface FatiaDaPizza {
  indice: number;
  rotulo: string;
  total: number;
  /** Participação na soma das contagens exibidas, em %, com uma casa decimal. */
  percentual: number;
  /** Caminho do SVG; vazio quando a etapa está zerada ou quando uma só etapa preenche o círculo. */
  caminho: string;
  /** Classe de cor (cíclica, só tokens): a legenda sempre traz o número, então a cor não carrega a informação sozinha. */
  cor: string;
}

const CORES_DA_PIZZA = 8;
const CENTRO = 100;
const RAIO = 90;

function ponto(angulo: number): string {
  const x = CENTRO + RAIO * Math.sin(angulo);
  const y = CENTRO - RAIO * Math.cos(angulo);
  return `${x.toFixed(2)} ${y.toFixed(2)}`;
}

export function montarPizza(itens: { rotulo: string; total: number }[]): FatiaDaPizza[] {
  const soma = itens.reduce((s, i) => s + i.total, 0);
  let acumulado = 0;
  return itens.map((item, indice) => {
    const fracao = soma > 0 ? item.total / soma : 0;
    const inicio = acumulado * 2 * Math.PI;
    acumulado += fracao;
    const fim = acumulado * 2 * Math.PI;
    let caminho = '';
    if (item.total > 0 && fracao < 1) {
      const grande = fracao > 0.5 ? 1 : 0;
      caminho = `M ${CENTRO} ${CENTRO} L ${ponto(inicio)} A ${RAIO} ${RAIO} 0 ${grande} 1 ${ponto(fim)} Z`;
    }
    return {
      indice,
      rotulo: item.rotulo,
      total: item.total,
      percentual: Math.round(fracao * 1000) / 10,
      caminho,
      cor: 'cor-' + (indice % CORES_DA_PIZZA),
    };
  });
}

export const VB = { largura: 720, altura: 290, esquerda: 44, direita: 8, topo: 24, base: 32 };

/**
 * Painel do Gestor (S10, docs/08). Só lê o instantâneo da API (nunca o SIGA nem o Camunda). Gráficos em HTML, CSS e
 * SVG, cada um com `role="img"` e uma tabela equivalente; a informação nunca depende só de cor.
 */
@Component({
  selector: 'app-painel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QuadroTopoComponent, FolhaSobrepostaComponent, IconeComponent],
  templateUrl: './painel.component.html',
})
export class PainelComponent implements OnInit {
  private readonly api = inject(PainelApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly painel = signal<PainelResposta | null>(null);
  protected readonly atualizando = signal(false);
  protected readonly mensagem = signal('');
  /** A releitura automática esgotou os 2 minutos sem novidade. */
  protected readonly demorando = signal(false);
  protected readonly comoTabela = signal({ serie: false, fila: false, carga: false });

  protected readonly formatarDataHora = formatarDataHora;
  protected readonly meses = MESES;
  protected readonly rotuloDoTipo = ROTULO_DO_TIPO;
  protected readonly icone = (tipo: TipoDeCarga): string => ICONE_DO_TIPO[tipo];
  protected readonly semNome = (tipo: TipoDeCarga): boolean => TIPOS_SEM_NOME.includes(tipo);
  protected readonly vb = VB;

  protected readonly mostraGraficos = computed(() => {
    const estado = this.painel()?.estado;
    return estado === 'PRONTO' || estado === 'DESATUALIZADO';
  });

  protected readonly avisosGerais = computed(() =>
    (this.painel()?.avisos ?? []).filter((a) => !AVISOS_COM_LUGAR_PROPRIO.includes(a.codigo)),
  );
  protected readonly avisoDeTruncado = computed(
    () =>
      this.painel()?.avisos.find((a) => a.codigo === 'TRUNCADO')?.texto ??
      'Mostrando só os primeiros requerimentos do ano: os gráficos 2 e 3 estão parciais.',
  );

  // Gráfico 1
  protected readonly serieCompleta = computed(() => {
    const porMes = new Map((this.painel()?.serie ?? []).map((m) => [m.mes, m]));
    return MESES.map((nome, i) => ({
      nome,
      mes: i + 1,
      protocolados: porMes.get(i + 1)?.protocolados ?? 0,
      concluidos: porMes.get(i + 1)?.concluidos ?? 0,
    }));
  });
  protected readonly maximoDaSerie = computed(() =>
    Math.max(1, ...this.serieCompleta().flatMap((m) => [m.protocolados, m.concluidos])),
  );
  protected readonly barrasDaSerie = computed<BarraDaSerie[]>(() => {
    const util = VB.altura - VB.topo - VB.base;
    const grupo = (VB.largura - VB.esquerda - VB.direita) / 12;
    const largura = Math.min(20, grupo / 2 - 3);
    const max = this.maximoDaSerie();
    const barra = (i: number, lado: 0 | 1, valor: number, serie: BarraDaSerie['serie']): BarraDaSerie => {
      const altura = (valor / max) * util;
      return {
        x: VB.esquerda + grupo * i + grupo / 2 - largura - 1 + lado * (largura + 2),
        y: VB.altura - VB.base - altura,
        largura,
        altura,
        valor,
        serie,
      };
    };
    return this.serieCompleta().flatMap((m, i) => [
      barra(i, 0, m.protocolados, 'protocolados'),
      barra(i, 1, m.concluidos, 'concluidos'),
    ]);
  });
  protected readonly nomesDosMeses = computed(() => {
    const grupo = (VB.largura - VB.esquerda - VB.direita) / 12;
    return MESES.map((nome, i) => ({ nome, x: VB.esquerda + grupo * i + grupo / 2 }));
  });
  protected readonly resumoDaSerie = computed(() => {
    const p = this.painel();
    return (
      `Protocolados e concluídos por mês em ${p?.ano}: ${p?.totalProtocolados ?? 0} protocolados e ` +
      `${p?.totalConcluidos ?? 0} concluídos (mês aproximado).`
    );
  });

  // Gráfico 2
  protected readonly somaDasBarras = computed(() => (this.painel()?.filaPorEtapa ?? []).reduce((s, e) => s + e.total, 0));
  protected readonly somaPassaDoTotal = computed(() => this.somaDasBarras() > (this.painel()?.totalNaFila ?? 0));
  protected readonly linhasAParte = computed(() => {
    const p = this.painel();
    return [
      { rotulo: 'Fora do catálogo', total: p?.foraDoCatalogo ?? 0 },
      { rotulo: 'Sem instância', total: p?.semInstancia ?? 0 },
      { rotulo: 'Etapa não mapeada', total: p?.etapaNaoMapeada ?? 0 },
    ];
  });
  /** Soma das contagens exibidas (etapas e linhas especiais): a base do percentual da pizza. */
  protected readonly somaDaPizza = computed(
    () => this.somaDasBarras() + this.linhasAParte().reduce((s, l) => s + l.total, 0),
  );
  /** Etapa em destaque (foco ou ponteiro na legenda ou na fatia). */
  protected readonly destaque = signal<number | null>(null);
  /** Legenda: todas as etapas na ordem do catálogo (inclusive as zeradas) e depois as linhas especiais. */
  protected readonly fatias = computed<FatiaDaPizza[]>(() => {
    const itens = [...(this.painel()?.filaPorEtapa ?? []), ...this.linhasAParte()];
    return montarPizza(itens);
  });
  protected readonly fatiasComValor = computed(() => this.fatias().filter((f) => f.total > 0));
  protected readonly umaSoFatia = computed(() => this.fatiasComValor().length === 1);
  protected readonly resumoDaFila = computed(() => {
    const p = this.painel();
    const maior = [...(p?.filaPorEtapa ?? [])].sort((a, b) => b.total - a.total)[0];
    return (
      `Fila por etapa, em pizza: ${p?.totalNaFila ?? 0} requerimentos na fila em ${p?.filaPorEtapa.length ?? 0} etapas.` +
      (maior && maior.total > 0 ? ` Maior fila: ${maior.rotulo}, com ${maior.total}.` : '') +
      ' Os percentuais são a participação na soma das etapas. A legenda e a tabela trazem todos os números.'
    );
  });

  // Gráfico 3
  protected readonly maximoDaCarga = computed(() => Math.max(1, ...(this.painel()?.cargaPorResponsavel ?? []).map((c) => c.total)));
  protected readonly resumoDaCarga = computed(() => {
    const linhas = this.painel()?.cargaPorResponsavel ?? [];
    const maior = [...linhas].sort((a, b) => b.total - a.total)[0];
    return (
      `Carga por responsável: ${linhas.length} linhas.` +
      (maior && maior.total > 0 ? ` Maior carga: ${maior.rotulo}, com ${maior.total}.` : '')
    );
  });

  private assinatura: Subscription | null = null;
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private releituras = 0;
  /** `geradoEm` de quando se pediu a atualização: a releitura espera uma data diferente. */
  private esperandoDepoisDe: string | null | undefined = undefined;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.parar();
      this.assinatura?.unsubscribe();
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.assinatura?.unsubscribe();
    this.assinatura = this.api.obter().subscribe({
      next: (resposta) => {
        this.painel.set(resposta);
        this.erro.set('');
        this.carregando.set(false);
        this.decidirReleitura(resposta);
      },
      error: (e: HttpErrorResponse) => {
        this.erro.set(mensagemDoErro(e));
        this.carregando.set(false);
      },
    });
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
        this.mensagem.set('Atualização pedida. Os números mudam sozinhos quando a leitura terminar.');
        this.esperandoDepoisDe = this.painel()?.geradoEm ?? null;
        this.releituras = 0;
        this.demorando.set(false);
        this.agendar();
      },
      error: (e: HttpErrorResponse) => {
        this.atualizando.set(false);
        const espera = (e.error as { tentarEmSegundos?: number } | null)?.tentarEmSegundos;
        this.mensagem.set(
          e.status === 429 && typeof espera === 'number'
            ? `O painel foi atualizado há pouco. Tente de novo em ${espera} segundos.`
            : mensagemDoErro(e),
        );
      },
    });
  }

  protected alternar(grafico: 'serie' | 'fila' | 'carga'): void {
    this.comoTabela.update((atual) => ({ ...atual, [grafico]: !atual[grafico] }));
  }

  protected porcentagem(valor: number, maximo: number): number {
    return Math.round((valor / maximo) * 1000) / 10;
  }

  protected percentualEmTexto(valor: number): string {
    return valor.toFixed(1).replace('.', ',') + '%';
  }

  protected destacar(indice: number | null): void {
    this.destaque.set(indice);
  }

  protected deCarga(c: CargaDoPainel): string {
    return `${c.rotulo} · ${ROTULO_DO_TIPO[c.tipo]}`;
  }

  /** Preparando (ou à espera da atualização pedida): relê a cada 5 s por até 2 minutos. */
  private decidirReleitura(resposta: PainelResposta): void {
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
