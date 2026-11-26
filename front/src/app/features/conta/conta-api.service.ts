import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';

export interface TrocaDeSenha {
  senhaAtual: string;
  novaSenha: string;
  confirmacao: string;
}

@Injectable({ providedIn: 'root' })
export class ContaApiService {
  private readonly http = inject(HttpClient);

  /** POST /api/auth/senha (204). Os valores só vão no corpo: nunca em URL, log ou armazenamento. */
  trocarSenha(troca: TrocaDeSenha): Observable<void> {
    return this.http.post<void>('/api/auth/senha', troca, { context: new HttpContext().set(SEM_TOAST, true) });
  }
}
