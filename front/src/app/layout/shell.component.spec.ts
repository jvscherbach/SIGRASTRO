import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { Perfil, Usuario } from '../core/usuario.model';
import { ShellComponent } from './shell.component';

function usuario(perfil: Perfil): Usuario {
  return {
    login: perfil.toLowerCase(),
    nome: 'Usuário Fictício',
    perfil,
    perfilDescricao: perfil,
    cpfCnpj: null,
    permissoes: {
      verSigilosos: perfil !== 'OPERADOR',
      verAuditoriaAcessos: perfil === 'ADMIN',
      acessarConfiguracoes: perfil === 'ADMIN',
      restritoAoInteressado: perfil === 'OPERADOR',
      verResponsavelTarefa: perfil !== 'OPERADOR',
    },
  };
}

describe('ShellComponent: menu por perfil (RN08)', () => {
  const atual = signal<Usuario | null>(null);

  beforeEach(() => {
    atual.set(null);
    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: { usuario: atual } }],
    });
  });

  function itensDoMenu(raiz: HTMLElement): string[] {
    return Array.from(raiz.querySelectorAll('.sidebar .nav-item')).map((item) => item.textContent?.trim() ?? '');
  }

  it('Operador e Gestor não veem Acessos nem Configurações; o Administrador vê', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    const raiz = fixture.nativeElement as HTMLElement;

    for (const perfil of ['OPERADOR', 'OPERADOR_SIGILO'] as Perfil[]) {
      atual.set(usuario(perfil));
      fixture.detectChanges();
      expect(itensDoMenu(raiz)).toEqual(['Consulta', 'Dossiês gerados', 'Auditoria', 'Documentos']);
    }

    atual.set(usuario('ADMIN'));
    fixture.detectChanges();
    expect(itensDoMenu(raiz)).toEqual(['Consulta', 'Dossiês gerados', 'Auditoria', 'Documentos', 'Acessos', 'Configurações']);
  });

  it('troca de usuário atualiza o menu sem recriar a tela', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    const raiz = fixture.nativeElement as HTMLElement;

    atual.set(usuario('ADMIN'));
    fixture.detectChanges();
    expect(itensDoMenu(raiz)).toContain('Configurações');

    atual.set(usuario('OPERADOR'));
    fixture.detectChanges();
    expect(itensDoMenu(raiz)).not.toContain('Configurações');
    expect(itensDoMenu(raiz)).not.toContain('Acessos');
  });

  it('nenhum item do menu tem cadeado', () => {
    atual.set(usuario('OPERADOR'));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.sidebar app-icone[nome="lock"], .sidebar .nav-lock')).toBeNull();
  });
});
