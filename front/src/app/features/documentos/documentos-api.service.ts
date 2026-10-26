import { HttpClient, HttpContext, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { AndamentoDossie, DocumentosResposta, ReferenciaDocumento } from './documentos.models';

/** A tela mostra o erro no próprio corpo (seção ou gaveta), sem toast duplicado. */
const SEM_AVISO = () => new HttpContext().set(SEM_TOAST, true);

@Injectable({ providedIn: 'root' })
export class DocumentosApiService {
  private readonly http = inject(HttpClient);

  listar(idRequerimento: number): Observable<DocumentosResposta> {
    return this.http.get<DocumentosResposta>(`/api/requerimentos/${idRequerimento}/documentos`, { context: SEM_AVISO() });
  }
}

@Injectable({ providedIn: 'root' })
export class DossiesApiService {
  private readonly http = inject(HttpClient);

  criar(idRequerimento: number, documentos: ReferenciaDocumento[]): Observable<{ id: string }> {
    return this.http.post<{ id: string }>('/api/dossies', { idRequerimento, documentos }, { context: SEM_AVISO() });
  }

  andamento(id: string): Observable<AndamentoDossie> {
    return this.http.get<AndamentoDossie>(`/api/dossies/${encodeURIComponent(id)}`, { context: SEM_AVISO() });
  }

  /**
   * PDF final como Blob. O token vai no cabeçalho Authorization (interceptor), nunca na URL. Um erro da API chega como
   * Blob (responseType blob); aqui ele volta a ser o ProblemDetail para a tela poder usar o errorCode.
   */
  arquivo(id: string, disposicao: 'inline' | 'attachment'): Observable<HttpResponse<Blob>> {
    return this.http
      .get(`/api/dossies/${encodeURIComponent(id)}/arquivo`, {
        params: { disposicao },
        responseType: 'blob',
        observe: 'response',
        context: SEM_AVISO(),
      })
      .pipe(catchError((erro: unknown) => erroLegivel(erro)));
  }

  cancelar(id: string): Observable<void> {
    return this.http.delete<void>(`/api/dossies/${encodeURIComponent(id)}`, { context: SEM_AVISO() });
  }
}

function erroLegivel(erro: unknown): Observable<never> {
  if (!(erro instanceof HttpErrorResponse) || !(erro.error instanceof Blob)) {
    return throwError(() => erro);
  }
  return from(lerTexto(erro.error)).pipe(
    switchMap((texto) =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: erro.status,
            statusText: erro.statusText,
            url: erro.url ?? undefined,
            headers: erro.headers,
            error: paraJson(texto),
          }),
      ),
    ),
  );
}

function lerTexto(blob: Blob): Promise<string> {
  if (typeof blob.text === 'function') {
    return blob.text();
  }
  return new Promise((resolver, rejeitar) => {
    const leitor = new FileReader();
    leitor.onload = () => resolver(String(leitor.result ?? ''));
    leitor.onerror = () => rejeitar(leitor.error);
    leitor.readAsText(blob);
  });
}

function paraJson(texto: string): unknown {
  try {
    return JSON.parse(texto);
  } catch {
    return null;
  }
}
