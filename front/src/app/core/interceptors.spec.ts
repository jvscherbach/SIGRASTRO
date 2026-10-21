import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { ToastService } from '../shared/toast.service';
import { AuthService } from './auth.service';
import { SEM_TOAST, correlacaoInterceptor, erroInterceptor, tokenInterceptor } from './interceptors';

describe('interceptors', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let toast: { erro: ReturnType<typeof vi.fn>; info: ReturnType<typeof vi.fn> };
  let auth: { token: ReturnType<typeof vi.fn>; limparSessao: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    toast = { erro: vi.fn(), info: vi.fn() };
    auth = { token: vi.fn(() => 'tok'), limparSessao: vi.fn() };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([correlacaoInterceptor, tokenInterceptor, erroInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toast },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  describe('token e correlação', () => {
    it('envia Authorization e um X-Correlation-Id novo por requisição', () => {
      http.get('/api/x').subscribe();
      http.get('/api/y').subscribe();

      const a = controller.expectOne('/api/x');
      const b = controller.expectOne('/api/y');
      expect(a.request.headers.get('Authorization')).toBe('Bearer tok');
      expect(a.request.headers.get('X-Correlation-Id')).toBeTruthy();
      expect(a.request.headers.get('X-Correlation-Id')).not.toBe(b.request.headers.get('X-Correlation-Id'));
    });

    it('não envia o token no login', () => {
      http.post('/api/auth/login', {}).subscribe();

      const req = controller.expectOne('/api/auth/login');
      expect(req.request.headers.has('Authorization')).toBe(false);
      expect(req.request.headers.has('X-Correlation-Id')).toBe(true);
    });

    it('não mexe em chamadas que não são da API', () => {
      http.get('/assets/x.json').subscribe();

      const req = controller.expectOne('/assets/x.json');
      expect(req.request.headers.has('Authorization')).toBe(false);
      expect(req.request.headers.has('X-Correlation-Id')).toBe(false);
    });
  });

  describe('erroInterceptor', () => {
    function falhar(url: string, status: number, corpo: object) {
      http.get(url).subscribe({ error: () => undefined });
      controller.expectOne(url).flush(corpo, { status, statusText: 'x' });
    }

    it('mostra a mensagem amigável do errorCode, sem o detail técnico', () => {
      falhar('/api/x', 403, { errorCode: 'NAO_INTERESSADO', detail: 'detalhe tecnico interno' });

      expect(toast.erro).toHaveBeenCalledWith('Você não é o interessado deste processo ou requerimento.');
    });

    it('inclui o código de suporte nos erros 5xx', () => {
      falhar('/api/x', 500, { errorCode: 'ERRO_INTERNO', correlationId: 'abc-1' });

      expect(toast.erro).toHaveBeenCalledWith('Ocorreu um erro inesperado. Código de suporte: abc-1.');
    });

    it('trata falha de conexão', () => {
      http.get('/api/x').subscribe({ error: () => undefined });
      controller.expectOne('/api/x').error(new ProgressEvent('error'), { status: 0 });

      expect(toast.erro).toHaveBeenCalledWith(expect.stringContaining('conectar ao servidor'));
    });

    it('401 fora do login limpa a sessão, avisa e vai ao login', () => {
      falhar('/api/x', 401, { errorCode: 'NAO_AUTENTICADO' });

      expect(auth.limparSessao).toHaveBeenCalledOnce();
      expect(toast.erro).toHaveBeenCalledOnce();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });

    it('401 no próprio login não mostra toast nem navega (a tela mostra o erro)', () => {
      http.post('/api/auth/login', {}).subscribe({ error: () => undefined });
      controller.expectOne('/api/auth/login').flush({ errorCode: 'NAO_AUTENTICADO' }, { status: 401, statusText: 'x' });

      expect(toast.erro).not.toHaveBeenCalled();
      expect(auth.limparSessao).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('SEM_TOAST: a tela mostra o erro, então o toast não é repetido', () => {
      let status: number | undefined;
      http.get('/api/x', { context: new HttpContext().set(SEM_TOAST, true) }).subscribe({ error: (e) => (status = e.status) });
      controller.expectOne('/api/x').flush({ errorCode: 'SIGA_INDISPONIVEL' }, { status: 503, statusText: 'x' });

      expect(status).toBe(503);
      expect(toast.erro).not.toHaveBeenCalled();
    });

    it('SEM_TOAST não impede o tratamento do 401 (sessão expirada)', () => {
      http.get('/api/x', { context: new HttpContext().set(SEM_TOAST, true) }).subscribe({ error: () => undefined });
      controller.expectOne('/api/x').flush({ errorCode: 'NAO_AUTENTICADO' }, { status: 401, statusText: 'x' });

      expect(auth.limparSessao).toHaveBeenCalledOnce();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });

    it('repassa o erro original a quem chamou', () => {
      let status: number | undefined;
      http.get('/api/x').subscribe({ error: (e) => (status = e.status) });
      controller.expectOne('/api/x').flush({ errorCode: 'NAO_ENCONTRADO' }, { status: 404, statusText: 'x' });

      expect(status).toBe(404);
    });
  });
});
