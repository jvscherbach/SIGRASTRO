import { HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../core/auth.service';
import { SEM_TOAST, tokenInterceptor } from '../../core/interceptors';
import { DocumentosApiService, DossiesApiService } from './documentos-api.service';

describe('serviços de documentos e dossiê', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([tokenInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token: () => 'TOKEN-DE-SESSAO', usuario: signal(null) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista os documentos do requerimento, sem toast (a seção mostra o erro)', () => {
    TestBed.inject(DocumentosApiService).listar(101).subscribe();

    const req = http.expectOne('/api/requerimentos/101/documentos');
    expect(req.request.method).toBe('GET');
    expect(req.request.context.get(SEM_TOAST)).toBe(true);
    req.flush({ itens: [], totais: { total: 0, vigentes: 0, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 0 } });
  });

  it('cria o pedido só com origem e id dos documentos', () => {
    TestBed.inject(DossiesApiService).criar(101, [{ origem: 'JUNTADA', id: '900118' }]).subscribe();

    const req = http.expectOne('/api/dossies');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ idRequerimento: 101, documentos: [{ origem: 'JUNTADA', id: '900118' }] });
    req.flush({ id: 'abc' });
  });

  it('consulta o andamento e cancela por id', () => {
    const api = TestBed.inject(DossiesApiService);
    api.andamento('a/b').subscribe();
    http.expectOne('/api/dossies/a%2Fb').flush({});
    api.cancelar('abc').subscribe();
    const req = http.expectOne('/api/dossies/abc');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('busca o PDF como Blob, com o token no cabeçalho e nunca na URL', () => {
    TestBed.inject(DossiesApiService).arquivo('abc', 'inline').subscribe();

    const req = http.expectOne((r) => r.url === '/api/dossies/abc/arquivo');
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('disposicao')).toBe('inline');
    expect(req.request.headers.get('Authorization')).toBe('Bearer TOKEN-DE-SESSAO');
    expect(req.request.urlWithParams).not.toContain('TOKEN-DE-SESSAO');
    req.flush(new Blob(['%PDF-']));
  });

  it('erro da API chega como Blob e volta a ser o ProblemDetail (errorCode utilizável)', async () => {
    let recebido: HttpErrorResponse | null = null;
    TestBed.inject(DossiesApiService)
      .arquivo('abc', 'attachment')
      .subscribe({ error: (e: HttpErrorResponse) => (recebido = e) });

    http
      .expectOne((r) => r.url === '/api/dossies/abc/arquivo')
      .flush(new Blob([JSON.stringify({ errorCode: 'DOSSIE_EXPIRADO', status: 410 })], { type: 'application/problem+json' }), {
        status: 410,
        statusText: 'Gone',
      });
    await vi.waitFor(() => expect(recebido).not.toBeNull());

    expect(recebido!.status).toBe(410);
    expect((recebido!.error as { errorCode: string }).errorCode).toBe('DOSSIE_EXPIRADO');
  });

  it('erro com corpo que não é JSON vira corpo nulo, sem estourar', async () => {
    let recebido: HttpErrorResponse | null = null;
    TestBed.inject(DossiesApiService)
      .arquivo('abc', 'inline')
      .subscribe({ error: (e: HttpErrorResponse) => (recebido = e) });

    http.expectOne((r) => r.url === '/api/dossies/abc/arquivo').flush(new Blob(['<html>erro</html>']), { status: 502, statusText: 'Bad Gateway' });
    await vi.waitFor(() => expect(recebido).not.toBeNull());

    expect(recebido!.status).toBe(502);
    expect(recebido!.error).toBeNull();
  });
});
