import { HttpErrorResponse } from '@angular/common/http';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { ProblemDetail } from '../../core/usuario.model';

/** Erros de proteção da administração: a mensagem em português vem do servidor (`detail`). */
const COM_MENSAGEM_DO_SERVIDOR = ['ULTIMO_ADMINISTRADOR', 'PROPRIO_USUARIO', 'LOGIN_JA_EXISTE'];

export function mensagemDaConfiguracao(erro: unknown): string {
  if (!(erro instanceof HttpErrorResponse)) {
    return 'Não foi possível concluir a operação. Tente novamente.';
  }
  const problema = erro.error as ProblemDetail | null;
  if (problema?.errorCode && COM_MENSAGEM_DO_SERVIDOR.includes(problema.errorCode) && problema.detail) {
    return problema.detail;
  }
  return mensagemDoErro(erro);
}

/** Mensagens por campo de um 400 PARAMETRO_INVALIDO (nome, login, cpfCnpj, perfil). */
export function camposInvalidos(erro: unknown): Record<string, string> {
  const problema = erro instanceof HttpErrorResponse ? (erro.error as ProblemDetail | null) : null;
  const resultado: Record<string, string> = {};
  for (const campo of problema?.campos ?? []) {
    resultado[campo.campo] = campo.mensagem;
  }
  return resultado;
}

/** O servidor recusou por causa de campos (e não por permissão ou indisponibilidade). */
export function recusaPorCampos(erro: unknown): boolean {
  return erro instanceof HttpErrorResponse && erro.status === 400 && Object.keys(camposInvalidos(erro)).length > 0;
}
