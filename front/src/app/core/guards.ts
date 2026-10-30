import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { ToastService } from '../shared/toast.service';
import { AuthService } from './auth.service';
import { mensagemDoCodigo } from './mensagens-erro';
import { PermissaoDeTela } from './usuario.model';

/** Exige sessão; se houver token guardado, revalida no servidor antes de decidir. */
export const autenticadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.usuario()) {
    return true;
  }
  if (!auth.token()) {
    return router.createUrlTree(['/login']);
  }
  return auth.restaurar().pipe(map((ok) => (ok ? true : router.createUrlTree(['/login']))));
};

/** Quem já está autenticado não volta ao login. */
export const visitanteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.token()) {
    return true;
  }
  return auth.restaurar().pipe(map((ok) => (ok ? router.createUrlTree(['/consulta']) : true)));
};

/**
 * Bloqueia o acesso direto pela URL a telas que o perfil não usa (RN08): a rota declara `data.permissao`
 * e a decisão vem das `permissoes` de /auth/me, sem chamada nova ao servidor. O back-end valida de novo.
 */
export const permissaoGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const permissao = route.data['permissao'] as PermissaoDeTela | undefined;
  const usuario = auth.usuario();
  if (usuario && permissao && usuario.permissoes[permissao] === true) {
    return true;
  }
  toast.erro(mensagemDoCodigo('PERFIL_SEM_PERMISSAO'));
  return router.createUrlTree(['/consulta']);
};
