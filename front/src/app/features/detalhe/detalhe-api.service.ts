import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { Requerimento } from './detalhe.models';

@Injectable({ providedIn: 'root' })
export class DetalheApiService {
  private readonly http = inject(HttpClient);

  obter(id: number): Observable<Requerimento> {
    // A tela mostra o erro no corpo da página (sem toast duplicado).
    return this.http.get<Requerimento>(`/api/requerimentos/${id}`, {
      context: new HttpContext().set(SEM_TOAST, true),
    });
  }
}
