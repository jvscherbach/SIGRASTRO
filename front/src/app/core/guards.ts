import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { ToastService } from '../shared/toast.service';
import { AuthService } from './auth.service';
import { Perfil } from './usuario.model';

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

/** Bloqueia o acesso direto pela URL a rotas que o perfil não usa (data.perfis). O back-end valida de novo. */
export const perfilGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const permitidos = (route.data['perfis'] ?? []) as Perfil[];
  const usuario = auth.usuario();
  if (usuario && permitidos.includes(usuario.perfil)) {
    return true;
  }
  toast.erro('Seu perfil não tem permissão para acessar esta área.');
  return router.createUrlTree(['/consulta']);
};
