import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of } from 'rxjs';

import { ToastService } from '../shared/toast.service';
import { AuthService } from './auth.service';
import { autenticadoGuard, permissaoGuard, visitanteGuard } from './guards';
import { Perfil, Usuario } from './usuario.model';

function usuario(perfil: Perfil): Usuario {
  return {
    login: 'x',
    nome: 'X',
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

  describe('permissaoGuard (RN08)', () => {
    const configuracoes = { permissao: 'acessarConfiguracoes' };
    const acessos = { permissao: 'verAuditoriaAcessos' };

    it('libera o Administrador nas telas restritas', async () => {
      atual.set(usuario('ADMIN'));
      expect(await executar(permissaoGuard, configuracoes)).toBe(true);
      expect(await executar(permissaoGuard, acessos)).toBe(true);
    });

    for (const perfil of ['OPERADOR', 'OPERADOR_SIGILO'] as Perfil[]) {
      it(`bloqueia ${perfil}, avisa uma vez e volta à consulta, sem chamar o servidor`, async () => {
        atual.set(usuario(perfil));
        restaurar = vi.fn(() => of(true));

        for (const data of [configuracoes, acessos]) {
          toast.erro.mockClear();
          expect(destino(await executar(permissaoGuard, data))).toBe('/consulta');
          expect(toast.erro).toHaveBeenCalledExactlyOnceWith('Seu perfil não tem permissão para acessar esta área.');
        }
        expect(restaurar).not.toHaveBeenCalled();
      });
    }

    it('decide pela permissão do back-end, não pelo nome do perfil', async () => {
      const gestor = usuario('OPERADOR_SIGILO');
      atual.set({ ...gestor, permissoes: { ...gestor.permissoes, acessarConfiguracoes: true } });
      expect(await executar(permissaoGuard, configuracoes)).toBe(true);
      expect(destino(await executar(permissaoGuard, acessos))).toBe('/consulta');
    });

    it('bloqueia rota sem permissão declarada (nada liberado por omissão)', async () => {
      atual.set(usuario('ADMIN'));
      expect(destino(await executar(permissaoGuard))).toBe('/consulta');
    });

    it('bloqueia sem usuário', async () => {
      expect(destino(await executar(permissaoGuard, configuracoes))).toBe('/consulta');
    });
  });
});
