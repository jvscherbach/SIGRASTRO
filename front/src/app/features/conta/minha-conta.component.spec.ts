import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuthService } from '../../core/auth.service';
import { MinhaContaComponent } from './minha-conta.component';

/** L1, bloco 4: todo perfil troca a própria senha; a política aparece na tela e os valores nunca ficam na página. */
describe('MinhaContaComponent', () => {
  const ATUAL = 'senha-de-teste-123';
  const NOVA = 'outra-senha-de-teste-456';
  let fixture: ComponentFixture<MinhaContaComponent>;
  let raiz: HTMLElement;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { usuario: signal({ login: 'operador', perfilDescricao: 'Operador' }).asReadonly() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MinhaContaComponent);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  });

  const campo = (id: string) => raiz.querySelector<HTMLInputElement>(`#mc-${id}`)!;
  const digitar = (id: string, valor: string) => {
    campo(id).value = valor;
    campo(id).dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const enviar = () => {
    raiz.querySelector<HTMLButtonElement>('.mc-trocar')!.click();
    fixture.detectChanges();
  };
  const preencher = (atual: string, nova: string, confirmacao: string) => {
    digitar('senhaAtual', atual);
    digitar('novaSenha', nova);
    digitar('confirmacao', confirmacao);
  };

  it('mostra a política, o usuário e três campos de senha com rótulo', () => {
    expect(raiz.textContent).toContain('De 12 a 200 caracteres.');
    expect(raiz.textContent).toContain('operador');
    for (const id of ['senhaAtual', 'novaSenha', 'confirmacao']) {
      expect(campo(id).type).toBe('password');
      expect(raiz.querySelector(`label[for="mc-${id}"]`)).not.toBeNull();
    }
    expect(campo('novaSenha').autocomplete).toBe('new-password');
  });

  it('sem preencher, mostra os erros por campo e não chama a API', () => {
    enviar();
    expect(Array.from(raiz.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim())).toEqual([
      'Informe a senha atual.',
      'Informe a nova senha.',
      'Confirme a nova senha.',
    ]);
    http.expectNone('/api/auth/senha');
  });

  it('nova senha curta e confirmação diferente são recusadas antes de enviar', () => {
    preencher(ATUAL, 'curta', 'outra');
    enviar();
    const erros = Array.from(raiz.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim());
    expect(erros).toEqual(['A nova senha precisa ter pelo menos 12 caracteres.', 'A confirmação não confere com a nova senha.']);
    http.expectNone('/api/auth/senha');
  });

  it('troca válida: envia os três valores no corpo, limpa os campos e avisa', () => {
    preencher(ATUAL, NOVA, NOVA);
    enviar();

    const req = http.expectOne('/api/auth/senha');
    expect(req.request.method).toBe('POST');
    expect(req.request.url).not.toContain(NOVA);
    expect(req.request.body).toEqual({ senhaAtual: ATUAL, novaSenha: NOVA, confirmacao: NOVA });
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(raiz.querySelector('.mc-sucesso')!.textContent).toContain('Senha alterada');
    expect(['senhaAtual', 'novaSenha', 'confirmacao'].map((id) => campo(id).value)).toEqual(['', '', '']);
    expect(raiz.textContent).not.toContain(NOVA);
    expect(raiz.textContent).not.toContain(ATUAL);
  });

  it('senha atual errada (400 por campo) aparece no campo, sem alert genérico, e limpa os valores', () => {
    preencher(ATUAL, NOVA, NOVA);
    enviar();
    http.expectOne('/api/auth/senha').flush(
      { status: 400, errorCode: 'PARAMETRO_INVALIDO', campos: [{ campo: 'senhaAtual', mensagem: 'A senha atual não confere.' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(raiz.querySelector('#mc-senhaAtual-erro')!.textContent).toContain('A senha atual não confere.');
    expect(campo('senhaAtual').getAttribute('aria-invalid')).toBe('true');
    expect(campo('novaSenha').value).toBe('');
    expect(raiz.querySelector('.mc-sucesso')).toBeNull();
    expect(raiz.innerHTML).not.toContain(NOVA);
    expect(raiz.innerHTML).not.toContain(ATUAL);
  });

  it('bloqueio (429) mostra o tempo restante em minutos', () => {
    preencher(ATUAL, NOVA, NOVA);
    enviar();
    http.expectOne('/api/auth/senha').flush(
      { status: 429, errorCode: 'MUITAS_TENTATIVAS', segundosRestantes: 600 },
      { status: 429, statusText: 'Too Many Requests' },
    );
    fixture.detectChanges();
    expect(raiz.querySelector('.mc-erro-geral')!.textContent).toContain('Muitas tentativas. Tente de novo em 10 minutos.');
  });

  it('erro de rede mostra mensagem genérica sem repetir valores', () => {
    preencher(ATUAL, NOVA, NOVA);
    enviar();
    http.expectOne('/api/auth/senha').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(raiz.querySelector('.mc-erro-geral')).not.toBeNull();
    expect(raiz.textContent).not.toContain(NOVA);
    expect(new HttpErrorResponse({ status: 0 }).status).toBe(0);
  });
});
