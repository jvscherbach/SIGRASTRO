import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { ToastService } from '../shared/toast.service';
import { AuthService } from './auth.service';
import { mensagemDoErro } from './mensagens-erro';

const URL_LOGIN = '/api/auth/login';

const ehApi = (url: string) => url.startsWith('/api/');

/** Envia o token do login simulado em toda chamada à API (menos no login). */
export const tokenInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token();
  if (!token || !ehApi(req.url) || req.url === URL_LOGIN) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

/** Um X-Correlation-Id novo por requisição, para cruzar a chamada com o log do servidor. */
export const correlacaoInterceptor: HttpInterceptorFn = (req, next) => {
  if (!ehApi(req.url)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-Correlation-Id': novoCorrelationId() } }));
};

/** Traduz o errorCode do ProblemDetail em toast; 401 fora do login encerra a sessão. */
export const erroInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse) || !ehApi(req.url)) {
        return throwError(() => erro);
      }
      const loginRecusado = req.url === URL_LOGIN && erro.status === 401;
      if (erro.status === 401 && !loginRecusado) {
        auth.limparSessao();
        toast.erro(mensagemDoErro(erro));
        void router.navigate(['/login']);
      } else if (!loginRecusado) {
        toast.erro(mensagemDoErro(erro));
      }
      return throwError(() => erro);
    }),
  );
};

function novoCorrelationId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
