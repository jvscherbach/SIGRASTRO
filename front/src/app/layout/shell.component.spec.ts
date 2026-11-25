import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

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
      verPainel: perfil !== 'OPERADOR',
    },
  };
}

describe('ShellComponent: barra superior e navegação por perfil (RN08)', () => {
  const atual = signal<Usuario | null>(null);

  beforeEach(() => {
    atual.set(null);
    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([{ path: '**', children: [] }]), { provide: AuthService, useValue: { usuario: atual } }],
    });
  });

  function itensDoMenu(raiz: HTMLElement): string[] {
    return Array.from(raiz.querySelectorAll('.top .nav a')).map((item) => item.textContent?.trim() ?? '');
  }

  it('Operador e Gestor não veem Configurações; o Administrador vê; Auditoria é item único', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    const raiz = fixture.nativeElement as HTMLElement;

    for (const perfil of ['OPERADOR', 'OPERADOR_SIGILO'] as Perfil[]) {
      atual.set(usuario(perfil));
      fixture.detectChanges();
      expect(itensDoMenu(raiz)).toEqual(
        perfil === 'OPERADOR' ? ['Consulta', 'Dossiês gerados', 'Auditoria'] : ['Consulta', 'Dossiês gerados', 'Painel', 'Auditoria'],
      );
    }

    atual.set(usuario('ADMIN'));
    fixture.detectChanges();
    expect(itensDoMenu(raiz)).toEqual(['Consulta', 'Dossiês gerados', 'Painel', 'Auditoria', 'Configurações']);
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
  });

  it('nenhum item do menu tem cadeado', () => {
    atual.set(usuario('OPERADOR'));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.top app-icone[nome="lock"], .top .nav-lock')).toBeNull();
  });

  it('a marca leva à Consulta e tem o nome acessível "SIGRASTRO, início"; não há menu lateral nem brasão', () => {
    atual.set(usuario('OPERADOR'));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const raiz = fixture.nativeElement as HTMLElement;
    const logo = raiz.querySelector('a.logo') as HTMLAnchorElement;
    expect(logo.getAttribute('aria-label')).toBe('SIGRASTRO, início');
    expect(logo.getAttribute('href')).toBe('/consulta');
    expect(raiz.querySelector('.sidebar, .watermark, .page-footer, .mobile-veil')).toBeNull();
    expect(raiz.querySelector('img[src*="brasao"], img[src*="logo-sema"]')).toBeNull();
  });

  it('mostra o selo do perfil, o nome, as iniciais e "Sair"', () => {
    atual.set(usuario('OPERADOR_SIGILO'));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const quem = (fixture.nativeElement as HTMLElement).querySelector('.who') as HTMLElement;
    expect(quem.querySelector('.perfil-chip')?.textContent?.trim()).toBe('OPERADOR_SIGILO');
    expect(quem.querySelector('.who-nome')?.textContent?.trim()).toBe('Usuário Fictício');
    expect(quem.querySelector('.avatar')?.textContent?.trim()).toBe('UF');
    expect(quem.querySelector('button')?.textContent?.trim()).toBe('Sair');
  });

  it('o item da rota atual leva aria-current="page" e os outros não', async () => {
    atual.set(usuario('ADMIN'));
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(ShellComponent);
    const raiz = fixture.nativeElement as HTMLElement;

    await router.navigateByUrl('/dossies');
    fixture.detectChanges();
    const ativos = () => Array.from(raiz.querySelectorAll('.top .nav a[aria-current="page"]')).map((a) => a.textContent?.trim());
    expect(ativos()).toEqual(['Dossiês gerados']);

    await router.navigateByUrl('/auditoria/acessos');
    fixture.detectChanges();
    expect(ativos()).toEqual(['Auditoria']);
  });
});
