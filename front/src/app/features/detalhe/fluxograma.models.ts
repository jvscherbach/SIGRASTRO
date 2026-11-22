import { duracaoLegivel, formatarData, formatarDataHora } from '../../shared/formato';
import { PassagemDaTramitacao, TramitacaoDoRequerimento } from './tramitacao.models';

/**
 * Modelo puro do fluxograma simplificado (S8, bloco 9; docs/08): transforma as `passagens` da API em caixas, em linha
 * reta. Só existe caixa para o que **já aconteceu** (mais a etapa atual destacada): nunca etapa futura, nunca o mapa
 * completo do fluxo. Sem decisão (losango) e sem seta de retorno: uma volta é uma nova caixa na linha ("2ª passagem").
 */
export type EstadoDaCaixa = 'concluida' | 'atual' | 'aguardando';

export interface CaixaDoFluxograma {
  rotulo: string;
  /** "3 analistas" (concluída), "2 de 3 concluídos" (ativa) ou os atos emitidos; vazio se não houver. */
  detalhe: string | null;
  /** "2ª passagem" a partir do 2º ciclo. */
  passagem: string | null;
  estado: EstadoDaCaixa;
  /** Texto do estado (além da cor e do ícone). */
  textoDoEstado: string;
  /** Data de entrada, sob o rótulo. */
  entrada: string;
  /** "Processo concluído": verificação da revisão do processo (arquivamento). */
  concluido: boolean;
  /** Linhas do tooltip (também lidas por teclado): entrada, saída, duração e com quem. */
  dica: string[];
}

export type ItemDoFluxograma =
  | { tipo: 'caixa'; caixa: CaixaDoFluxograma }
  | { tipo: 'faixa'; rotulo: string; caixas: CaixaDoFluxograma[] };

export interface FinalDoFluxograma {
  /** Rótulo curto, escrito em cima da bola preta. */
  rotulo: string;
  data: string | null;
}

export interface Fluxograma {
  itens: ItemDoFluxograma[];
  final: FinalDoFluxograma | null;
  /** Resumo para o `aria-label`: o equivalente textual completo é a lista da seção. */
  resumo: string;
}

/**
 * Automáticas que o usuário reconhece e que entram: a taxa, a isenção e o termo gerado (LAC). As demais automáticas
 * (conclusão, scripts) não viram caixa: a bola preta e o próprio caminho já dizem o que importa.
 */
const AUTOMATICAS_QUE_ENTRAM = new Set(['emissao_da_taxa', 'emissao_da_taxa_correcao', 'isencao_confirmada', 'termo_de_adesao_gerado']);

/** Só etapas do usuário: tarefas humanas, esperas, emissão, as automáticas acima e a etapa não mapeada (aconteceu). */
export function entraNoFluxograma(p: PassagemDaTramitacao): boolean {
  const tipo = p.etapa.tipo;
  if (tipo === null) return true;
  if (tipo === 'AUTOMATICA') return !!p.etapa.id && AUTOMATICAS_QUE_ENTRAM.has(p.etapa.id);
  return true;
}

/** "Arquivado (termo não enviado no prazo)" vira "Arquivado": a bola preta leva o rótulo curto. */
export function rotuloCurtoDoFinal(rotulo: string): string {
  return rotulo.replace(/\s*\(.*\)\s*$/, '').trim() || rotulo;
}

function plural(papel: string, quantos: number): string {
  const base = papel.toLowerCase();
  if (base === 'analista') return `${quantos} ${quantos === 1 ? 'analista' : 'analistas'}`;
  if (base === 'signatário') return `${quantos} ${quantos === 1 ? 'signatário' : 'signatários'}`;
  return `${quantos} ${quantos === 1 ? 'participante' : 'participantes'}`;
}

function detalheDe(p: PassagemDaTramitacao): string | null {
  if (p.participantes.length) {
    const concluidos = p.participantes.filter((x) => x.estado === 'CONCLUIU').length;
    return p.atual ? `${concluidos} de ${p.participantes.length} concluídos` : plural(p.participantes[0].papel, p.participantes.length);
  }
  if (p.atos.length) {
    const emitidos = p.atos.filter((a) => a.ocorreu).map((a) => a.rotulo);
    return emitidos.length ? emitidos.join(', ') : null;
  }
  return null;
}

function quemDe(p: PassagemDaTramitacao): string | null {
  const pessoas = p.participantes.length ? p.participantes : p.responsaveis;
  if (!pessoas.length) return null;
  return pessoas.map((r) => (r.nome ? `${r.nome} · ${r.papel}` : r.papel)).join('; ');
}

export function caixaDe(p: PassagemDaTramitacao): CaixaDoFluxograma {
  const espera = p.etapa.tipo === 'ESPERA';
  const estado: EstadoDaCaixa = p.atual ? (espera ? 'aguardando' : 'atual') : 'concluida';
  const dica = [`Entrada: ${formatarDataHora(p.entrada)}`];
  if (p.saida) {
    dica.push(`Saída: ${formatarDataHora(p.saida)}`);
    const duracao = duracaoLegivel(p.duracaoSegundos);
    if (duracao) dica.push(`Duração: ${duracao}`);
  } else {
    dica.push('Em andamento');
  }
  const quem = quemDe(p);
  if (quem) dica.push(`Com quem: ${quem}`);
  return {
    rotulo: p.etapa.rotulo,
    detalhe: detalheDe(p),
    passagem: p.ciclo > 1 ? `${p.ciclo}ª passagem` : null,
    estado,
    textoDoEstado: estado === 'atual' ? 'Etapa atual' : estado === 'aguardando' ? 'Aguardando' : 'Concluída',
    entrada: formatarData(p.entrada),
    concluido: p.processoConcluido,
    dica,
  };
}

/** Monta o fluxograma; vazio (sem itens) quando o estado não tem caminho (plano B, não suportado, indisponível). */
export function montarFluxograma(t: TramitacaoDoRequerimento): Fluxograma {
  const temCaminho = (t.estado === 'ATIVO' || t.estado === 'ENCERRADO') && t.planoB === null;
  const itens: ItemDoFluxograma[] = [];
  if (temCaminho) {
    for (const p of t.passagens.filter(entraNoFluxograma)) {
      const caixa = caixaDe(p);
      const ultimo = itens[itens.length - 1];
      if (p.ramo === 'CORRECAO') {
        if (ultimo?.tipo === 'faixa') ultimo.caixas.push(caixa);
        else itens.push({ tipo: 'faixa', rotulo: 'Correção do requerimento', caixas: [caixa] });
      } else {
        itens.push({ tipo: 'caixa', caixa });
      }
    }
  }
  const final: FinalDoFluxograma | null =
    temCaminho && t.final?.tipo === 'FINAL' ? { rotulo: rotuloCurtoDoFinal(t.final.rotulo), data: t.final.encerradoEm ? formatarData(t.final.encerradoEm) : null }
      : null;
  const caixas = itens.flatMap((i) => (i.tipo === 'caixa' ? [i.caixa] : i.caixas));
  const atual = caixas.find((c) => c.estado !== 'concluida');
  const resumo =
    `Fluxograma simplificado: ${caixas.length} ${caixas.length === 1 ? 'etapa percorrida' : 'etapas percorridas'}` +
    (atual ? `; etapa atual: ${atual.rotulo}` : '') +
    (final ? `; final: ${final.rotulo}` : '') +
    '. A lista da seção Tramitação traz o mesmo conteúdo em texto.';
  return { itens, final, resumo };
}
