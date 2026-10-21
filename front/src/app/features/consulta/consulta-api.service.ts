import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { PageResponse } from '../../shared/api-tipos';
import { CriterioBusca, ResultadoBusca } from './consulta.models';

@Injectable({ providedIn: 'root' })
export class ConsultaApiService {
  private readonly http = inject(HttpClient);

  buscar(criterio: CriterioBusca): Observable<PageResponse<ResultadoBusca>> {
    let params = new HttpParams();
    if (criterio.numero) {
      // tipo e número andam juntos: a API rejeita um sem o outro.
      params = params.set('tipo', criterio.tipo ?? 'REQUERIMENTO').set('numero', criterio.numero);
    }
    if (criterio.cpfCnpj) {
      params = params.set('cpfCnpj', criterio.cpfCnpj);
    }
    if (criterio.nome) {
      params = params.set('nome', criterio.nome);
    }
    params = params.set('pagina', criterio.pagina);
    if (criterio.tamanho) {
      params = params.set('tamanho', criterio.tamanho);
    }
    // A tela mostra o erro da busca no próprio formulário.
    return this.http.get<PageResponse<ResultadoBusca>>('/api/busca', {
      params,
      context: new HttpContext().set(SEM_TOAST, true),
    });
  }
}
