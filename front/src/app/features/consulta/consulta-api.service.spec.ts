import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SEM_TOAST } from '../../core/interceptors';
import { ConsultaApiService } from './consulta-api.service';

describe('ConsultaApiService', () => {
  let api: ConsultaApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ConsultaApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function parametros(criterio: Parameters<ConsultaApiService['buscar']>[0]) {
    api.buscar(criterio).subscribe();
    const req = http.expectOne((r) => r.url === '/api/busca');
    req.flush({ conteudo: [], pagina: 0, tamanho: 20, totalElementos: 0, totalPaginas: 0 });
    return req.request;
  }

  it('envia tipo e número juntos e a primeira página', () => {
    const req = parametros({ tipo: 'PROCESSO', numero: '2825/2025', pagina: 0 });

    expect(req.method).toBe('GET');
    expect(req.params.get('tipo')).toBe('PROCESSO');
    expect(req.params.get('numero')).toBe('2825/2025');
    expect(req.params.get('pagina')).toBe('0');
    expect(req.params.has('cpfCnpj')).toBe(false);
    expect(req.params.has('nome')).toBe(false);
  });

  it('assume REQUERIMENTO quando o tipo não vem e há número', () => {
    expect(parametros({ numero: '1/2025', pagina: 0 }).params.get('tipo')).toBe('REQUERIMENTO');
  });

  it('só envia os critérios preenchidos, sem tipo quando não há número', () => {
    const req = parametros({ tipo: 'PROCESSO', cpfCnpj: '12345678000190', nome: 'vale', pagina: 2, tamanho: 50 });

    expect(req.params.has('tipo')).toBe(false);
    expect(req.params.has('numero')).toBe(false);
    expect(req.params.get('cpfCnpj')).toBe('12345678000190');
    expect(req.params.get('nome')).toBe('vale');
    expect(req.params.get('pagina')).toBe('2');
    expect(req.params.get('tamanho')).toBe('50');
  });

  it('pede ao interceptor para não repetir o erro em toast', () => {
    expect(parametros({ nome: 'vale', pagina: 0 }).context.get(SEM_TOAST)).toBe(true);
  });
});
