import { DocumentoItem } from './documentos.models';

/** Documento de teste (PDF vigente, sem bloqueio) com os campos sobrescritos. */
export function documento(sobre: Partial<DocumentoItem> = {}): DocumentoItem {
  return {
    origem: 'DOCUMENTO_PROCESSO',
    id: '1',
    idRequerimento: 101,
    titulo: 'Requerimento assinado',
    nomeArquivo: 'requerimento.pdf',
    origemDescricao: 'Documento do processo',
    data: '2025-04-01T10:00:00',
    situacao: 'VIGENTE',
    sigiloso: false,
    acessivel: true,
    extensao: 'pdf',
    tipoArquivo: 'PDF',
    selecionavelPorPadrao: true,
    bloqueio: null,
    ...sobre,
  };
}
