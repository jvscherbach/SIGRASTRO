import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from './auth.service';
import { LoginResposta, Usuario } from './usuario.model';

const USUARIO: Usuario = {
  login: 'gestor.demo',
  nome: 'Gestor Demo',
  perfil: 'OPERADOR_SIGILO',
  perfilDescricao: 'Gestor',
  cpfCnpj: null,
  permissoes: { verSigilosos: true, verAuditoriaAcessos: false, acessarConfiguracoes: false, restritoAoInteressado: false, verResponsavelTarefa: false, verPainel: false, verCaixa: false },
};

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('login guarda o token na sessionStorage e expõe o usuário', () => {
    let recebido: Usuario | undefined;
    auth.login('gestor.demo', 'senha').subscribe((u) => (recebido = u));

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ usuario: 'gestor.demo', senha: 'senha' });
    req.flush({ token: 'abc', tipo: 'Bearer', expiraEm: '2026-10-20T20:00:00-04:00', usuario: USUARIO } satisfies LoginResposta);

    expect(recebido).toEqual(USUARIO);
    expect(auth.usuario()).toEqual(USUARIO);
    expect(auth.autenticado()).toBe(true);
    expect(auth.token()).toBe('abc');
    expect(sessionStorage.getItem('rastro.token')).toBe('abc');
  });

  it('restaurar sem token emite false sem chamar a API', () => {
    let resultado: boolean | undefined;
    auth.restaurar().subscribe((ok) => (resultado = ok));

    expect(resultado).toBe(false);
    http.expectNone('/api/auth/me');
  });

  it('restaurar com token válido carrega o usuário', () => {
    sessionStorage.setItem('rastro.token', 'abc');
    let resultado: boolean | undefined;
    auth.restaurar().subscribe((ok) => (resultado = ok));

    http.expectOne('/api/auth/me').flush(USUARIO);

    expect(resultado).toBe(true);
    expect(auth.usuario()).toEqual(USUARIO);
  });

  it('restaurar com o token na chave antiga vistas.token não desloga e migra a chave (R1, D75)', () => {
    sessionStorage.setItem('vistas.token', 'abc');
    let resultado: boolean | undefined;
    auth.restaurar().subscribe((ok) => (resultado = ok));

    http.expectOne('/api/auth/me').flush(USUARIO);

    expect(resultado).toBe(true);
    expect(auth.token()).toBe('abc');
    expect(sessionStorage.getItem('rastro.token')).toBe('abc');
    expect(sessionStorage.getItem('vistas.token')).toBeNull();
  });

  it('restaurar com token recusado limpa a sessão', () => {
    sessionStorage.setItem('rastro.token', 'velho');
    let resultado: boolean | undefined;
    auth.restaurar().subscribe((ok) => (resultado = ok));

    http.expectOne('/api/auth/me').flush({ errorCode: 'NAO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });

    expect(resultado).toBe(false);
    expect(auth.token()).toBeNull();
    expect(auth.usuario()).toBeNull();
  });

  it('logout chama a API e limpa a sessão mesmo se a API falhar', () => {
    sessionStorage.setItem('rastro.token', 'abc');
    let concluiu = false;
    auth.logout().subscribe(() => (concluiu = true));

    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Erro' });

    expect(concluiu).toBe(true);
    expect(auth.token()).toBeNull();
    expect(auth.usuario()).toBeNull();
  });
});
