import { HttpClient, HttpContext, HttpErrorResponse, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, filter, from, map, switchMap, takeWhile, throwError } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { nomeDoContentDisposition } from '../../shared/arquivo-local';
import {
  AcaoDeContinuacao,
  AndamentoDossie,
  ConteudoRecebido,
  DocumentosResposta,
  FormatoDoConteudo,
  ReferenciaDocumento,
} from './documentos.models';

/** A tela mostra o erro no próprio corpo (seção ou gaveta), sem toast duplicado. */
const SEM_AVISO = () => new HttpContext().set(SEM_TOAST, true);

@Injectable({ providedIn: 'root' })
export class DocumentosApiService {
  private readonly http = inject(HttpClient);

  listar(idRequerimento: number): Observable<DocumentosResposta> {
    return this.http.get<DocumentosResposta>(`/api/requerimentos/${idRequerimento}/documentos`, { context: SEM_AVISO() });
  }

  /**
   * ZIP com as geometrias (S4, tudo ou nada) como Blob. O token vai no cabeçalho (interceptor), nunca na URL; o nome
   * vem do Content-Disposition. O erro volta como ProblemDetail (errorCode e, em GEOMETRIAS_INCOMPLETAS, `arquivos`).
   */
  baixarGeometrias(idRequerimento: number): Observable<HttpResponse<Blob>> {
    return this.http
      .get(`/api/requerimentos/${idRequerimento}/geometrias/arquivo`, {
        responseType: 'blob',
        observe: 'response',
        context: SEM_AVISO(),
      })
      .pipe(catchError((erro: unknown) => erroLegivel(erro)));
  }
}

/**
 * Acima disto (MB) o documento não é exibido por Blob: o front não o leva para a memória do navegador. Espelha o padrão
 * do servidor para DOSSIE_BLOB_MAX_MB (docs/08); o contrato do conteúdo não informa o tamanho antes do download, então
 * o critério é o Content-Length do cabeçalho da resposta.
 */
export const LIMITE_DO_BLOB_MB = 150;
const MB = 1024 * 1024;

/** O que o endpoint de conteúdo precisa para localizar o documento (o servidor revalida o pertencimento). */
export interface DocumentoDoConteudo {
  origem: string;
  id: string;
  idRequerimento: number;
}

@Injectable({ providedIn: 'root' })
export class ConteudoApiService {
  private readonly http = inject(HttpClient);

  /**
   * Conteúdo do documento como Blob (S3). O token vai no cabeçalho (interceptor), nunca na URL. Com `limiteMb`, a
   * requisição é abortada assim que o cabeçalho mostra um Content-Length acima do limite: nada é baixado e o resultado
   * é `grande`. Erros da API voltam como ProblemDetail (errorCode) para a tela.
   */
  conteudo(documento: DocumentoDoConteudo, formato: FormatoDoConteudo, limiteMb: number | null): Observable<ConteudoRecebido> {
    const url = `/api/documentos/${encodeURIComponent(documento.origem)}/${encodeURIComponent(documento.id)}/conteudo`;
    const limiteBytes = limiteMb === null ? Number.POSITIVE_INFINITY : limiteMb * MB;
    const tamanhoDe = (evento: HttpEvent<Blob>): number =>
      evento.type === HttpEventType.ResponseHeader ? Number(evento.headers.get('Content-Length')) : 0;
    const passaDoLimite = (evento: HttpEvent<Blob>): boolean => tamanhoDe(evento) > limiteBytes;
    return this.http
      .get(url, {
        params: { requerimento: String(documento.idRequerimento), formato },
        responseType: 'blob',
        observe: 'events',
        reportProgress: true,
        context: SEM_AVISO(),
      })
      .pipe(
        // Ao ver o cabeçalho de um arquivo grande o fluxo termina e a requisição é cancelada, sem baixar o corpo.
        takeWhile((evento) => !passaDoLimite(evento), true),
        filter((evento) => evento.type === HttpEventType.Response || passaDoLimite(evento)),
        map((evento): ConteudoRecebido => {
          if (evento.type === HttpEventType.Response) {
            return {
              tipo: 'blob',
              blob: evento.body as Blob,
              convertidoDe: evento.headers.get('X-Vistas-Convertido-De'),
              nome: nomeDoContentDisposition(evento.headers.get('Content-Disposition')),
            };
          }
          return { tipo: 'grande', megabytes: Math.ceil(tamanhoDe(evento) / MB) };
        }),
        catchError((erro: unknown) => erroLegivel(erro)),
      );
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

  /** Ticket de uso único para o download direto de um dossiê grande (D49). O valor não é guardado em lugar nenhum. */
  ticket(id: string): Observable<{ ticket: string; expiraEm: string }> {
    return this.http
      .post<{ ticket: string; expiraEm: string }>(`/api/dossies/${encodeURIComponent(id)}/arquivo/ticket`, null, {
        context: SEM_AVISO(),
      });
  }

  /** URL do download direto: o navegador baixa em streaming; só o ticket vai na URL, nunca o token de sessão. */
  urlDeDownloadDireto(id: string, ticket: string): string {
    const parametros = new URLSearchParams({ disposicao: 'attachment', ticket });
    return `/api/dossies/${encodeURIComponent(id)}/arquivo?${parametros.toString()}`;
  }

  /** Decisão sobre um pedido em PENDENTE_DECISAO (S2.2): nada já obtido é baixado de novo. */
  continuar(id: string, acao: AcaoDeContinuacao): Observable<void> {
    return this.http.post<void>(`/api/dossies/${encodeURIComponent(id)}/continuacao`, { acao }, { context: SEM_AVISO() });
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
