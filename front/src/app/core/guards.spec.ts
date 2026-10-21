import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of } from 'rxjs';

import { ToastService } from '../shared/toast.service';
import { AuthService } from './auth.service';
import { autenticadoGuard, perfilGuard, visitanteGuard } from './guards';
import { Perfil, Usuario } from './usuario.model';

function usuario(perfil: Perfil): Usuario {
  return {
    login: 'x',
    nome: 'X',
    perfil,
    perfilDescricao: perfil,
    cpfCnpj: null,
    permissoes: { verSigilosos: false, verAuditoriaAcessos: false, acessarConfiguracoes: false, restritoAoInteressado: false },
  };
}

describe('guards', () => {
  const atual = signal<Usuario | null>(null);
  let token: string | null;
  let restaurar: () => Observable<boolean>;
  let toast: { erro: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    atual.set(null);
    token = null;
    restaurar = () => of(false);
    toast = { erro: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { usuario: atual, token: () => token, restaurar: () => restaurar() } },
        { provide: ToastService, useValue: toast },
      ],
    });
  });

  async function executar(guard: CanActivateFn, data: Record<string, unknown> = {}): Promise<boolean | UrlTree> {
    const rota = { data } as unknown as ActivatedRouteSnapshot;
    const resultado = TestBed.runInInjectionContext(() => guard(rota, {} as RouterStateSnapshot));
    return (isObservable(resultado) ? firstValueFrom(resultado) : resultado) as Promise<boolean | UrlTree>;
  }

  const destino = (r: boolean | UrlTree) => TestBed.inject(Router).serializeUrl(r as UrlTree);

  describe('autenticadoGuard', () => {
    it('libera quando há usuário', async () => {
      atual.set(usuario('ADMIN'));
      expect(await executar(autenticadoGuard)).toBe(true);
    });

    it('manda ao login sem usuário e sem token', async () => {
      expect(destino(await executar(autenticadoGuard))).toBe('/login');
    });

    it('revalida o token guardado e libera se a sessão ainda vale', async () => {
      token = 'abc';
      restaurar = () => of(true);
      expect(await executar(autenticadoGuard)).toBe(true);
    });

    it('manda ao login se o token guardado não vale mais', async () => {
      token = 'velho';
      expect(destino(await executar(autenticadoGuard))).toBe('/login');
    });
  });

  describe('visitanteGuard', () => {
    it('libera o login sem sessão', async () => {
      expect(await executar(visitanteGuard)).toBe(true);
    });

    it('leva à consulta quem já tem sessão válida', async () => {
      token = 'abc';
      restaurar = () => of(true);
      expect(destino(await executar(visitanteGuard))).toBe('/consulta');
    });
  });

  describe('perfilGuard', () => {
    it('libera o perfil permitido', async () => {
      atual.set(usuario('ADMIN'));
      expect(await executar(perfilGuard, { perfis: ['ADMIN'] })).toBe(true);
    });

    it('bloqueia perfil sem permissão, avisa e volta à consulta', async () => {
      atual.set(usuario('OPERADOR_SIGILO'));

      const resultado = await executar(perfilGuard, { perfis: ['ADMIN'] });

      expect(destino(resultado)).toBe('/consulta');
      expect(toast.erro).toHaveBeenCalledOnce();
    });

    it('bloqueia sem usuário', async () => {
      expect(destino(await executar(perfilGuard, { perfis: ['ADMIN'] }))).toBe('/consulta');
    });
  });
});
