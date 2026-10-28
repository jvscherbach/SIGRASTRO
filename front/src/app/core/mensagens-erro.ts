import { HttpErrorResponse } from '@angular/common/http';

import { ProblemDetail } from './usuario.model';

const POR_CODIGO: Record<string, string> = {
  PARAMETRO_INVALIDO: 'Verifique os dados informados e tente novamente.',
  NAO_AUTENTICADO: 'Sua sessão expirou ou é inválida. Entre novamente.',
  PERFIL_SEM_PERMISSAO: 'Seu perfil não tem permissão para acessar esta área.',
  DOCUMENTO_SIGILOSO: 'Este documento é sigiloso e não está disponível para o seu perfil.',
  NAO_INTERESSADO: 'Seu perfil consulta apenas processos e requerimentos em que você é o interessado.',
  NAO_ENCONTRADO: 'O item solicitado não foi encontrado.',
  DOSSIE_SEM_ARQUIVO: 'Não há arquivo: o dossiê aguarda uma decisão, foi cancelado ou ainda está sendo gerado.',
  DOSSIE_SEM_DECISAO_PENDENTE: 'O dossiê não está mais aguardando decisão. Atualize para ver a situação.',
  DOSSIE_EXPIRADO: 'O dossiê expirou. Gere-o novamente.',
  CREDENCIAL_ARQUIVOS_RECUSADA:
    'A credencial de acesso aos arquivos foi recusada. Capture um novo token, atualize o arquivo do token e clique em Tentar novamente.',
  FORMATO_NAO_SUPORTADO: 'Este formato não tem conversão para PDF. Baixe o original para abri-lo.',
  DOCUMENTO_PROTEGIDO_POR_SENHA: 'O documento é protegido por senha e não pode ser convertido. Baixe o original.',
  DOCUMENTO_CORROMPIDO: 'O arquivo está corrompido ou não pôde ser lido pelo conversor.',
  CONVERSAO_RECUSADA: 'A conversão foi recusada por exceder um limite (páginas, tamanho ou resolução) ou por não ter conteúdo.',
  MUITAS_CONVERSOES: 'Há muitas conversões em andamento. Tente novamente em instantes.',
  REPOSITORIO_DE_ARQUIVOS_INDISPONIVEL: 'O repositório de arquivos está indisponível no momento. Tente novamente em instantes.',
  CONVERSOR_INDISPONIVEL: 'O conversor de documentos está indisponível no momento.',
  TEMPO_LIMITE_EXCEDIDO: 'O tempo limite foi excedido. Tente novamente.',
  SIGA_INDISPONIVEL: 'O banco do SIGA está indisponível no momento. Tente novamente em instantes.',
  GEOMETRIAS_INCOMPLETAS: 'Nenhum arquivo foi baixado: algumas geometrias não puderam ser obtidas.',
  GEOMETRIAS_GRANDES_DEMAIS:
    'As geometrias passam do limite de tamanho ou de quantidade para um único download. Nenhum arquivo foi baixado.',
  ERRO_INTERNO: 'Ocorreu um erro inesperado.',
};

/** Texto fixo de um errorCode, para quem decide sem chamar o servidor (guarda de rota, sigiloso no modal). */
export function mensagemDoCodigo(codigo: 'PERFIL_SEM_PERMISSAO' | 'DOCUMENTO_SIGILOSO' | 'NAO_INTERESSADO'): string {
  return POR_CODIGO[codigo];
}

const SEM_CONEXAO = 'Não foi possível conectar ao servidor. Verifique a conexão e tente novamente.';
const GENERICA = 'Não foi possível concluir a operação.';

/** Mensagem amigável a partir do errorCode do ProblemDetail; nunca exibe o detail técnico cru. */
export function mensagemDoErro(erro: HttpErrorResponse): string {
  if (erro.status === 0) {
    return SEM_CONEXAO;
  }
  const problema = erro.error as ProblemDetail | null;
  const codigo = problema?.errorCode;
  const mensagem = (codigo && POR_CODIGO[codigo]) || (erro.status >= 500 ? POR_CODIGO['ERRO_INTERNO'] : GENERICA);
  const suporte = erro.status >= 500 && problema?.correlationId ? ` Código de suporte: ${problema.correlationId}.` : '';
  return mensagem + suporte;
}
