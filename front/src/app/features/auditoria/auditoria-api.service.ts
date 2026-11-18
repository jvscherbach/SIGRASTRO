import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { nomeDoContentDisposition } from '../../shared/arquivo-local';
import { erroLegivel } from '../documentos/documentos-api.service';
import {
  CsvRecebido,
  EventoDetalhado,
  EventosResposta,
  FiltroDeAuditoria,
  GrupoDeAuditoria,
} from './auditoria.models';

/** A tela mostra o erro no próprio corpo, sem toast duplicado. */
const SEM_AVISO = () => new HttpContext().set(SEM_TOAST, true);

export const TAMANHO_DA_PAGINA = 20;

/** Parâmetros em comum da lista e do CSV. Filtro vazio não vai na URL; o token nunca vai (só o interceptor, no cabeçalho). */
function parametros(grupo: GrupoDeAuditoria, filtro: FiltroDeAuditoria): HttpParams {
  let params = new HttpParams().set('periodo', filtro.periodo);
  if (filtro.texto.trim()) {
    params = params.set('texto', filtro.texto.trim());
  }
  if (filtro.tipo) {
    params = params.set('tipo', filtro.tipo);
  }
  if (filtro.usuario.trim()) {
    params = params.set('usuario', filtro.usuario.trim());
  }
  if (grupo === 'documentos' && filtro.somenteSigilosos) {
    params = params.set('somenteSigilosos', 'true');
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class AuditoriaApiService {
  private readonly http = inject(HttpClient);

  listar(grupo: GrupoDeAuditoria, filtro: FiltroDeAuditoria): Observable<EventosResposta> {
    const params = parametros(grupo, filtro).set('pagina', String(filtro.pagina)).set('tamanho', String(TAMANHO_DA_PAGINA));
    return this.http.get<EventosResposta>(`/api/auditoria/${grupo}`, { params, context: SEM_AVISO() });
  }

  detalhe(grupo: GrupoDeAuditoria, id: number): Observable<EventoDetalhado> {
    return this.http.get<EventoDetalhado>(`/api/auditoria/${grupo}/${id}`, { context: SEM_AVISO() });
  }

  /** CSV dos eventos filtrados, como Blob (o token vai no cabeçalho, nunca na URL). */
  exportar(grupo: GrupoDeAuditoria, filtro: FiltroDeAuditoria): Observable<CsvRecebido> {
    return this.http
      .get(`/api/auditoria/${grupo}/exportacao`, {
        params: parametros(grupo, filtro),
        responseType: 'blob',
        observe: 'response',
        context: SEM_AVISO(),
      })
      .pipe(
        map((resposta) => ({
          blob: resposta.body as Blob,
          nome: nomeDoContentDisposition(resposta.headers.get('Content-Disposition')),
          truncado: resposta.headers.get('X-Vistas-Truncado') === 'true',
        })),
        catchError((erro: unknown) => erroLegivel(erro)),
      );
  }
}
