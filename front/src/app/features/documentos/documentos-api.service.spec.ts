import { HttpErrorResponse, HttpHeaderResponse, HttpHeaders, HttpResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../core/auth.service';
import { SEM_TOAST, tokenInterceptor } from '../../core/interceptors';
import { ConteudoApiService, DocumentosApiService, DossiesApiService, LIMITE_DO_BLOB_MB } from './documentos-api.service';
import { ConteudoRecebido } from './documentos.models';

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
    req.flush({ itens: [], totais: { total: 0, vigentes: 0, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 0, convertiveis: 0 } });
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

  it('pede o ticket do download direto por POST e monta a URL só com o ticket, nunca com o token de sessão', () => {
    const api = TestBed.inject(DossiesApiService);
    let recebido: { ticket: string } | undefined;
    api.ticket('a/b').subscribe((t) => (recebido = t));

    const req = http.expectOne('/api/dossies/a%2Fb/arquivo/ticket');
    expect(req.request.method).toBe('POST');
    req.flush({ ticket: 'TICKET-DE-TESTE', expiraEm: '2026-10-26T10:01:00' });
    expect(recebido?.ticket).toBe('TICKET-DE-TESTE');

    const url = api.urlDeDownloadDireto('a/b', 'T+1/=');
    expect(url).toBe('/api/dossies/a%2Fb/arquivo?disposicao=attachment&ticket=T%2B1%2F%3D');
    expect(url).not.toContain('TOKEN-DE-SESSAO');
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

  describe('conteúdo do documento (S3)', () => {
    const DOCUMENTO = { origem: 'DOCUMENTO_PROCESSO', id: '804328', idRequerimento: 101 };
    const MB = 1024 * 1024;
    const cabecalho = (tamanhoBytes: number) =>
      new HttpHeaderResponse({ status: 200, headers: new HttpHeaders({ 'Content-Length': String(tamanhoBytes) }) });

    it('pede origem, id, requerimento e formato; o token vai no cabeçalho e nunca na URL', () => {
      TestBed.inject(ConteudoApiService).conteudo(DOCUMENTO, 'pdf', LIMITE_DO_BLOB_MB).subscribe();

      const req = http.expectOne((r) => r.url === '/api/documentos/DOCUMENTO_PROCESSO/804328/conteudo');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('requerimento')).toBe('101');
      expect(req.request.params.get('formato')).toBe('pdf');
      expect(req.request.responseType).toBe('blob');
      expect(req.request.headers.get('Authorization')).toBe('Bearer TOKEN-DE-SESSAO');
      expect(req.request.urlWithParams).not.toContain('TOKEN-DE-SESSAO');
      expect(req.request.context.get(SEM_TOAST)).toBe(true);
      req.flush(new Blob(['%PDF-1.7']));
    });

    it('devolve o Blob com o formato de origem e o nome do Content-Disposition', () => {
      let recebido: ConteudoRecebido | null = null;
      TestBed.inject(ConteudoApiService).conteudo(DOCUMENTO, 'pdf', LIMITE_DO_BLOB_MB).subscribe((c) => (recebido = c));

      http.expectOne((r) => r.url.endsWith('/conteudo')).flush(new Blob(['%PDF-1.7']), {
        headers: { 'X-Vistas-Convertido-De': 'XLSX', 'Content-Disposition': 'inline; filename="calculo.pdf"' },
      });

      expect(recebido).toMatchObject({ tipo: 'blob', convertidoDe: 'XLSX', nome: 'calculo.pdf' });
      expect((recebido as unknown as { blob: Blob }).blob).toBeInstanceOf(Blob);
    });

    it('sem conversão, convertidoDe é nulo', () => {
      let recebido: ConteudoRecebido | null = null;
      TestBed.inject(ConteudoApiService).conteudo(DOCUMENTO, 'pdf', LIMITE_DO_BLOB_MB).subscribe((c) => (recebido = c));

      http.expectOne((r) => r.url.endsWith('/conteudo')).flush(new Blob(['%PDF-1.7']));

      expect(recebido).toMatchObject({ tipo: 'blob', convertidoDe: null });
    });

    it('arquivo acima do limite: avisa pelo cabeçalho e cancela a requisição, sem baixar o corpo', () => {
      const recebidos: ConteudoRecebido[] = [];
      let terminou = false;
      TestBed.inject(ConteudoApiService)
        .conteudo(DOCUMENTO, 'pdf', LIMITE_DO_BLOB_MB)
        .subscribe({ next: (c) => recebidos.push(c), complete: () => (terminou = true) });

      const req = http.expectOne((r) => r.url.endsWith('/conteudo'));
      req.event(cabecalho(200 * MB));

      expect(recebidos).toEqual([{ tipo: 'grande', megabytes: 200 }]);
      expect(terminou).toBe(true);
      expect(req.cancelled).toBe(true);
    });

    it('arquivo dentro do limite segue até o fim', () => {
      const recebidos: ConteudoRecebido[] = [];
      TestBed.inject(ConteudoApiService).conteudo(DOCUMENTO, 'pdf', LIMITE_DO_BLOB_MB).subscribe((c) => recebidos.push(c));

      const req = http.expectOne((r) => r.url.endsWith('/conteudo'));
      req.event(cabecalho(10 * MB));
      expect(recebidos).toEqual([]);
      req.event(new HttpResponse({ body: new Blob(['%PDF-1.7']), status: 200 }));

      expect(recebidos).toHaveLength(1);
      expect(recebidos[0].tipo).toBe('blob');
    });

    it('sem limite (original pedido pelo usuário) não corta arquivo grande', () => {
      const recebidos: ConteudoRecebido[] = [];
      TestBed.inject(ConteudoApiService).conteudo(DOCUMENTO, 'original', null).subscribe((c) => recebidos.push(c));

      const req = http.expectOne((r) => r.url.endsWith('/conteudo') && r.params.get('formato') === 'original');
      req.event(cabecalho(900 * MB));
      expect(req.cancelled).toBe(false);
      req.event(new HttpResponse({ body: new Blob(['x']), status: 200 }));

      expect(recebidos.map((c) => c.tipo)).toEqual(['blob']);
    });

    it('erro da API (corpo Blob) volta como ProblemDetail com o errorCode', async () => {
      let recebido: HttpErrorResponse | null = null;
      TestBed.inject(ConteudoApiService)
        .conteudo(DOCUMENTO, 'pdf', LIMITE_DO_BLOB_MB)
        .subscribe({ error: (e: HttpErrorResponse) => (recebido = e) });

      http
        .expectOne((r) => r.url.endsWith('/conteudo'))
        .flush(new Blob([JSON.stringify({ errorCode: 'DOCUMENTO_PROTEGIDO_POR_SENHA' })]), { status: 422, statusText: 'Unprocessable' });
      await vi.waitFor(() => expect(recebido).not.toBeNull());

      expect(recebido!.status).toBe(422);
      expect((recebido!.error as { errorCode: string }).errorCode).toBe('DOCUMENTO_PROTEGIDO_POR_SENHA');
    });
  });
});
