import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { mensagemDoBloqueio } from '../../core/mensagens-erro';
import { LoginComponent } from './login.component';

/** L1, bloco 4: o login é só usuário e senha (a varredura de texto proibido fica em core/sem-demonstracao.spec.ts). */
describe('LoginComponent', () => {
  function montar() {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    return fixture;
  }
  const criar = (): HTMLElement => montar().nativeElement as HTMLElement;

  function entrarCom(fixture: ReturnType<typeof montar>, usuario: string, senha: string): void {
    const el = fixture.nativeElement as HTMLElement;
    const campoUsuario = el.querySelector('#login-usuario') as HTMLInputElement;
    const campoSenha = el.querySelector('#login-senha') as HTMLInputElement;
    campoUsuario.value = usuario;
    campoUsuario.dispatchEvent(new Event('input'));
    campoSenha.value = senha;
    campoSenha.dispatchEvent(new Event('input'));
    (el.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('mostra a marca SIGRASTRO e a frase de apoio, sem brasão nem texto antigo', () => {
    const el = criar();
    expect(el.querySelector('app-marca .wm')?.textContent).toBe('SIGRASTRO');
    expect(el.querySelector('app-marca img')?.getAttribute('src')).toBe('assets/img/sigrastro-escudo.svg');
    expect(el.querySelector('.marca p')?.textContent).toBe('Acompanhamento de processos de licenciamento ambiental');
    expect(el.querySelector('img[src*="brasao"], img[src*="logo-sema"], .scene, .login-art')).toBeNull();
    expect(el.textContent).not.toContain('consulta ao SIGA Licenciamento');
    expect(el.textContent).not.toContain('(protótipo)');
  });

  it('é só o formulário de usuário e senha, sem botões de entrada rápida nem aviso de acesso simulado', () => {
    const el = criar();
    expect(el.querySelector('#login-usuario')).not.toBeNull();
    expect(el.querySelector('#login-senha')).not.toBeNull();
    expect(el.querySelector('button[type="submit"]')?.textContent).toContain('Entrar');
    expect(el.querySelectorAll('button').length).toBe(1);
    expect(el.textContent).not.toContain('Acesso simulado');
    expect((el.querySelector('#login-usuario') as HTMLInputElement).value).toBe('');
    expect((el.querySelector('#login-senha') as HTMLInputElement).value).toBe('');
  });

  it('formulário vazio mostra o erro de validação', () => {
    const fixture = montar();
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Informe o usuário e a senha.');
  });

  it('credencial inválida (401) mostra a mensagem única e limpa a senha', () => {
    const fixture = montar();
    const http = TestBed.inject(HttpTestingController);
    entrarCom(fixture, 'alguem', 'senha-de-teste-123');
    http.expectOne('/api/auth/login').flush({ status: 401, errorCode: 'NAO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Usuário ou senha inválidos.');
    expect((el.querySelector('#login-senha') as HTMLInputElement).value).toBe('');
    // a senha digitada nunca fica na página (nem em atributo), e o usuário não é repetido na mensagem
    expect(el.innerHTML).not.toContain('senha-de-teste-123');
    expect(el.querySelector('[role="alert"]')?.textContent).not.toContain('alguem');
  });

  it('bloqueio temporário (429) mostra o tempo restante em minutos', () => {
    const fixture = montar();
    const http = TestBed.inject(HttpTestingController);
    entrarCom(fixture, 'alguem', 'senha-de-teste-123');
    http
      .expectOne('/api/auth/login')
      .flush({ status: 429, errorCode: 'MUITAS_TENTATIVAS', segundosRestantes: 840 }, { status: 429, statusText: 'Too Many Requests' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Muitas tentativas. Tente de novo em 14 minutos.');
  });

  it('mensagemDoBloqueio arredonda para cima e trata a ausência do tempo', () => {
    expect(mensagemDoBloqueio(1)).toBe('Muitas tentativas. Tente de novo em 1 minuto.');
    expect(mensagemDoBloqueio(61)).toBe('Muitas tentativas. Tente de novo em 2 minutos.');
    expect(mensagemDoBloqueio(undefined)).toBe('Muitas tentativas. Tente de novo em instantes.');
    expect(new HttpErrorResponse({ status: 429 }).status).toBe(429);
  });
});
