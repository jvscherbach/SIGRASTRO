import { DocumentoItem, ReferenciaDocumento } from './documentos.models';

export type CampoDeOrdem = 'titulo' | 'origem' | 'data';
export interface Ordem {
  campo: CampoDeOrdem;
  direcao: 1 | -1;
}

/** Ordem inicial: data crescente (a mesma do dossiê, RN03). */
export const ORDEM_PADRAO: Ordem = { campo: 'data', direcao: 1 };

export const chaveDe = (d: Pick<DocumentoItem, 'origem' | 'id'>): string => `${d.origem}:${d.id}`;

export const referenciaDe = (d: Pick<DocumentoItem, 'origem' | 'id'>): ReferenciaDocumento => ({ origem: d.origem, id: d.id });

/** Sem acento e sem diferenciar maiúsculas: "relatorio" encontra "Relatório". */
export function normalizar(texto: string | null | undefined): string {
  return (texto ?? '')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}

/** Busca por título, id e nome do arquivo, mais o filtro de origem. Documento sigiloso mascarado só casa pelo id. */
export function filtrar(itens: DocumentoItem[], busca: string, origem: string): DocumentoItem[] {
  const termo = normalizar(busca);
  return itens.filter((d) => {
    if (origem && d.origem !== origem) {
      return false;
    }
    return !termo || [d.titulo, d.id, d.nomeArquivo].some((campo) => normalizar(campo).includes(termo));
  });
}

/** Ordem das origens no empate de data: a mesma do servidor (tabela do docs/04, enum `OrigemDocumento`). */
export const ORDEM_DAS_ORIGENS = [
  'DOCUMENTO_PROCESSO',
  'DOCUMENTO_REQUERIMENTO',
  'COMPLEMENTACAO',
  'IMOVEL',
  'REPRESENTANTE_LEGAL',
  'RESPONSAVEL_TECNICO',
  // S4: documentos pessoais do SCORP vêm depois das seis origens da Vista no empate de data (RN03).
  'SCORP',
];

const posicaoDaOrigem = (origem: string): number => {
  const i = ORDEM_DAS_ORIGENS.indexOf(origem);
  return i < 0 ? ORDEM_DAS_ORIGENS.length : i;
};

/**
 * Documento e origem por texto (pt-BR); data cronológica pelo timestamp completo, sem data sempre ao final. Na data,
 * o empate segue o critério do servidor (origem, depois id numérico); nos demais campos, o id numérico.
 */
export function ordenar(itens: DocumentoItem[], ordem: Ordem): DocumentoItem[] {
  const texto = (a: string | null, b: string | null) => normalizar(a).localeCompare(normalizar(b), 'pt-BR');
  const id = (a: DocumentoItem, b: DocumentoItem) => a.id.length - b.id.length || a.id.localeCompare(b.id);
  const origem = (a: DocumentoItem, b: DocumentoItem) =>
    ordem.campo === 'data' ? posicaoDaOrigem(a.origem) - posicaoDaOrigem(b.origem) : 0;
  return [...itens].sort((a, b) => {
    let c: number;
    if (ordem.campo === 'data') {
      if (!a.data && !b.data) {
        c = 0;
      } else if (!a.data) {
        return 1;
      } else if (!b.data) {
        return -1;
      } else {
        c = a.data.localeCompare(b.data);
      }
    } else if (ordem.campo === 'origem') {
      c = texto(a.origemDescricao, b.origemDescricao);
    } else {
      c = texto(a.titulo, b.titulo);
    }
    return c * ordem.direcao || origem(a, b) || id(a, b);
  });
}

export const selecionavel = (d: DocumentoItem): boolean => d.bloqueio === null;

/** Seleção inicial definida pelo domínio: todo documento selecionável (S2.2). */
export const selecaoPadrao = (itens: DocumentoItem[]): Set<string> =>
  new Set(itens.filter((d) => d.selecionavelPorPadrao).map(chaveDe));

export function rotuloDoBloqueio(d: DocumentoItem): string | null {
  switch (d.bloqueio) {
    case 'INDISPONIVEL':
      return 'arquivo indisponível';
    case 'SIGILOSO':
      return 'documento sigiloso';
    case 'NAO_PDF':
      return 'conversão indisponível';
    default:
      return null;
  }
}

/** Classe do selo de extensão (mesmas cores do protótipo). */
export function classeDaExtensao(d: DocumentoItem): string {
  switch (d.tipoArquivo) {
    case 'PDF':
      return 'pdf';
    case 'PLANILHA':
      return 'xls';
    case 'TEXTO':
      return 'doc';
    case 'IMAGEM':
      return 'img';
    case 'COMPACTADO':
      return 'zip';
    case 'GEOMETRIA':
      return 'geo';
    default:
      return 'off';
  }
}

/** Planilha, texto ou imagem selecionável: entra no dossiê convertida para PDF (S3). */
export const seraConvertido = (d: DocumentoItem): boolean =>
  d.bloqueio === null && (d.tipoArquivo === 'PLANILHA' || d.tipoArquivo === 'TEXTO' || d.tipoArquivo === 'IMAGEM');

/** Não PDF que continua bloqueado (sem conversão para o formato, ou conversão desligada). */
export const semConversao = (d: DocumentoItem): boolean => d.bloqueio === 'NAO_PDF';

/** Tudo exceto o indisponível pode ser aberto no modal (o sigiloso sem perfil abre só com o motivo). */
export const visualizavel = (d: DocumentoItem): boolean => d.bloqueio !== 'INDISPONIVEL';
