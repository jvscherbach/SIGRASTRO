import { CanDeactivateFn } from '@angular/router';

/** Tela que sabe dizer se tem alterações ainda não salvas. */
export interface ComPendencias {
  temPendencia(): boolean;
}

/** Sair da tela com alterações de parâmetros pendentes pede confirmação (RF28). */
export const MENSAGEM_DE_SAIDA = 'Há alterações de parâmetros que ainda não foram salvas. Sair da tela e descartá-las?';

export const pendenciasGuard: CanDeactivateFn<ComPendencias> = (tela) =>
  !tela.temPendencia() || window.confirm(MENSAGEM_DE_SAIDA);
