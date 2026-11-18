import { HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../core/auth.service';
import { SEM_TOAST, tokenInterceptor } from '../../core/interceptors';
import { AuditoriaApiService } from './auditoria-api.service';
import { FiltroDeAuditoria } from './auditoria.models';

const FILTRO: FiltroDeAuditoria = { periodo: '30d', texto: ' ofício ', tipo: 'FALHA', usuario: 'maria.teste', somenteSigilosos: true, pagina: 2 };

describe('AuditoriaApiService', () => {
  let http: HttpTestingController;
  let api: AuditoriaApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([tokenInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token: () => 'TOKEN-DE-SESSAO', usuario: signal(null) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(AuditoriaApiService);
  });

  afterEach(() => http.verify());

  it('lista com período, filtros e página; token só no cabeçalho, nunca na URL', () => {
    api.listar('documentos', FILTRO).subscribe();

    const req = http.expectOne((r) => r.url === '/api/auditoria/documentos');
    expect(req.request.params.get('periodo')).toBe('30d');
    expect(req.request.params.get('texto')).toBe('ofício');
    expect(req.request.params.get('tipo')).toBe('FALHA');
    expect(req.request.params.get('usuario')).toBe('maria.teste');
    expect(req.request.params.get('somenteSigilosos')).toBe('true');
    expect(req.request.params.get('pagina')).toBe('2');
    expect(req.request.params.get('tamanho')).toBe('20');
    expect(req.request.context.get(SEM_TOAST)).toBe(true);
    expect(req.request.headers.get('Authorization')).toBe('Bearer TOKEN-DE-SESSAO');
    expect(req.request.urlWithParams).not.toContain('TOKEN-DE-SESSAO');
    req.flush({});
  });

  it('filtro vazio não vai na URL e "somente sigilosos" nunca vai em acessos', () => {
    api.listar('acessos', { ...FILTRO, texto: '', tipo: '', usuario: '' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/auditoria/acessos');
    expect(req.request.params.keys().sort()).toEqual(['pagina', 'periodo', 'tamanho']);
    req.flush({});
  });

  it('detalhe do evento pelo grupo e id', () => {
    api.detalhe('acessos', 7).subscribe();

    http.expectOne('/api/auditoria/acessos/7').flush({});
  });

  it('exporta o CSV como Blob com o mesmo filtro (sem página), o nome do servidor e o aviso de corte', () => {
    let recebido: { nome: string | null; truncado: boolean } | null = null;
    api.exportar('documentos', FILTRO).subscribe((csv) => (recebido = { nome: csv.nome, truncado: csv.truncado }));

    const req = http.expectOne((r) => r.url === '/api/auditoria/documentos/exportacao');
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.has('pagina')).toBe(false);
    expect(req.request.params.get('periodo')).toBe('30d');
    expect(req.request.headers.get('Authorization')).toBe('Bearer TOKEN-DE-SESSAO');
    expect(req.request.urlWithParams).not.toContain('TOKEN-DE-SESSAO');
    req.flush(new Blob(['x']), {
      headers: new HttpHeaders({
        'Content-Disposition': 'attachment; filename="auditoria-documentos-20261021.csv"',
        'X-Vistas-Truncado': 'true',
      }),
    });
    expect(recebido).toEqual({ nome: 'auditoria-documentos-20261021.csv', truncado: true });
  });
});
