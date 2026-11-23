import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LoginComponent } from './login.component';

/** R2, bloco 8: a tela de login mantém o fluxo e troca só a aparência (marca, frase e caixa central). */
describe('LoginComponent', () => {
  function montar() {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    return fixture;
  }
  const criar = (): HTMLElement => montar().nativeElement as HTMLElement;

  it('mostra a marca SIGRASTRO e a frase de apoio, sem brasão nem texto antigo', () => {
    const el = criar();
    expect(el.querySelector('app-marca .wm')?.textContent).toBe('SIGRASTRO');
    expect(el.querySelector('app-marca img')?.getAttribute('src')).toBe('assets/img/sigrastro-escudo.svg');
    expect(el.querySelector('.marca p')?.textContent).toBe('Acompanhamento de processos de licenciamento ambiental');
    expect(el.querySelector('img[src*="brasao"], img[src*="logo-sema"], .scene, .login-art')).toBeNull();
    expect(el.textContent).not.toContain('consulta ao SIGA Licenciamento');
    expect(el.textContent).not.toContain('(protótipo)');
  });

  it('mantém o formulário, o aviso de acesso simulado e os três perfis de demonstração', () => {
    const el = criar();
    expect(el.querySelector('.eyebrow')?.textContent).toBe('Acesso simulado');
    expect(el.querySelector('#login-usuario')).not.toBeNull();
    expect(el.querySelector('#login-senha')).not.toBeNull();
    expect(el.querySelector('button[type="submit"]')?.textContent).toContain('Entrar');
    expect(el.querySelectorAll('.demo-user').length).toBe(3);
    expect(el.querySelector('.login-card .note')?.textContent).toContain('credencial institucional');
  });

  it('formulário vazio mostra o erro de validação', () => {
    const fixture = montar();
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Informe o usuário e a senha.');
  });
});
