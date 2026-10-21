import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../core/auth.service';
import { Usuario } from '../core/usuario.model';
import { RecentesService } from './recentes.service';

function usuario(login: string): Usuario {
  return {
    login,
    nome: login,
    perfil: 'OPERADOR',
    perfilDescricao: 'Operador',
    cpfCnpj: null,
    permissoes: { verSigilosos: false, verAuditoriaAcessos: false, acessarConfiguracoes: false, restritoAoInteressado: true, verResponsavelTarefa: false },
  };
}

describe('RecentesService', () => {
  const atual = signal<Usuario | null>(null);
  let recentes: RecentesService;

  beforeEach(() => {
    localStorage.clear();
    atual.set(usuario('operador.demo'));
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: { usuario: atual } }] });
    recentes = TestBed.inject(RecentesService);
  });

  afterEach(() => localStorage.clear());

  it('começa vazio', () => {
    expect(recentes.listar()).toEqual([]);
  });

  it('guarda só id e rótulo, com o mais recente primeiro', () => {
    recentes.registrar({ id: 1, rotulo: 'Processo 2825/2025' });
    recentes.registrar({ id: 2, rotulo: 'Requerimento 1187/2026' });

    expect(recentes.listar()).toEqual([
      { id: 2, rotulo: 'Requerimento 1187/2026' },
      { id: 1, rotulo: 'Processo 2825/2025' },
    ]);
    expect(localStorage.getItem('vistas.recentes.operador.demo')).not.toContain('interessado');
  });

  it('não duplica: reabrir move para o início', () => {
    recentes.registrar({ id: 1, rotulo: 'A' });
    recentes.registrar({ id: 2, rotulo: 'B' });
    recentes.registrar({ id: 1, rotulo: 'A' });

    expect(recentes.listar().map((r) => r.id)).toEqual([1, 2]);
  });

  it('guarda no máximo 6', () => {
    for (let id = 1; id <= 9; id++) {
      recentes.registrar({ id, rotulo: `R${id}` });
    }

    expect(recentes.listar().map((r) => r.id)).toEqual([9, 8, 7, 6, 5, 4]);
  });

  it('separa por login', () => {
    recentes.registrar({ id: 1, rotulo: 'A' });
    atual.set(usuario('gestor.demo'));

    expect(recentes.listar()).toEqual([]);
    recentes.registrar({ id: 2, rotulo: 'B' });
    atual.set(usuario('operador.demo'));
    expect(recentes.listar()).toEqual([{ id: 1, rotulo: 'A' }]);
  });

  it('sem usuário não lê nem grava', () => {
    atual.set(null);

    recentes.registrar({ id: 1, rotulo: 'A' });

    expect(recentes.listar()).toEqual([]);
    expect(localStorage.length).toBe(0);
  });

  it('ignora conteúdo corrompido ou de formato inesperado', () => {
    localStorage.setItem('vistas.recentes.operador.demo', '{nao-e-json');
    expect(recentes.listar()).toEqual([]);

    localStorage.setItem('vistas.recentes.operador.demo', JSON.stringify([{ id: 'x' }, { id: 3, rotulo: 'C' }, 7]));
    expect(recentes.listar()).toEqual([{ id: 3, rotulo: 'C' }]);
  });
});
