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
    permissoes: { verSigilosos: false, verAuditoriaAcessos: false, acessarConfiguracoes: false, restritoAoInteressado: true, verResponsavelTarefa: false, verPainel: false, verCaixa: false },
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

  it('guarda só id, rótulo, número do requerimento e situação, com o mais recente primeiro', () => {
    recentes.registrar({ id: 1, rotulo: 'Processo 2825/2025' });
    recentes.registrar({ id: 2, rotulo: 'Requerimento 1187/2026' });

    expect(recentes.listar()).toEqual([
      { id: 2, rotulo: 'Requerimento 1187/2026' },
      { id: 1, rotulo: 'Processo 2825/2025' },
    ]);
    expect(localStorage.getItem('rastro.recentes.operador.demo')).not.toContain('interessado');
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

  it('recentes guardados na chave antiga vistas.recentes.* continuam e passam para a nova (R1, D75)', () => {
    localStorage.setItem('vistas.recentes.operador.demo', JSON.stringify([{ id: 9, rotulo: 'Processo 2825/2025' }]));

    expect(recentes.listar()).toEqual([{ id: 9, rotulo: 'Processo 2825/2025' }]);
    recentes.registrar({ id: 10, rotulo: 'Requerimento 1187/2026' });

    expect(JSON.parse(localStorage.getItem('rastro.recentes.operador.demo')!)).toEqual([
      { id: 10, rotulo: 'Requerimento 1187/2026' },
      { id: 9, rotulo: 'Processo 2825/2025' },
    ]);
    expect(localStorage.getItem('vistas.recentes.operador.demo')).toBeNull();
  });

  it('ignora conteúdo corrompido ou de formato inesperado', () => {
    localStorage.setItem('rastro.recentes.operador.demo', '{nao-e-json');
    expect(recentes.listar()).toEqual([]);

    localStorage.setItem('rastro.recentes.operador.demo', JSON.stringify([{ id: 'x' }, { id: 3, rotulo: 'C' }, 7]));
    expect(recentes.listar()).toEqual([{ id: 3, rotulo: 'C' }]);
  });

  describe('desempate de atalhos com o mesmo rótulo', () => {
    it('dois requerimentos do mesmo processo ganham o número do requerimento e a situação', () => {
      recentes.registrar({ id: 1, rotulo: 'Processo 445/2022', numeroRequerimento: '3391/2022', situacao: 'Concluído' });
      recentes.registrar({ id: 2, rotulo: 'Processo 445/2022', numeroRequerimento: '3798/2023', situacao: 'Cancelado' });
      recentes.registrar({ id: 3, rotulo: 'Processo 2825/2025', numeroRequerimento: '3331/2025', situacao: 'Em análise' });

      expect(recentes.listarParaExibicao()).toEqual([
        { id: 3, titulo: 'Processo 2825/2025', situacao: null },
        { id: 2, titulo: 'Processo 445/2022 · Req. 3798/2023', situacao: 'Cancelado' },
        { id: 1, titulo: 'Processo 445/2022 · Req. 3391/2022', situacao: 'Concluído' },
      ]);
    });

    it('rótulo único não ganha nada além do próprio rótulo', () => {
      recentes.registrar({ id: 1, rotulo: 'Processo 2825/2025', numeroRequerimento: '3331/2025', situacao: 'Em análise' });

      expect(recentes.listarParaExibicao()).toEqual([{ id: 1, titulo: 'Processo 2825/2025', situacao: null }]);
    });

    it('atalho antigo (só id e rótulo) continua sendo lido e, repetido, não inventa o que não tem', () => {
      localStorage.setItem(
        'rastro.recentes.operador.demo',
        JSON.stringify([
          { id: 1, rotulo: 'Processo 445/2022' },
          { id: 2, rotulo: 'Processo 445/2022', numeroRequerimento: '3798/2023', situacao: 'Cancelado' },
        ]),
      );

      expect(recentes.listar()).toEqual([
        { id: 1, rotulo: 'Processo 445/2022' },
        { id: 2, rotulo: 'Processo 445/2022', numeroRequerimento: '3798/2023', situacao: 'Cancelado' },
      ]);
      expect(recentes.listarParaExibicao()).toEqual([
        { id: 1, titulo: 'Processo 445/2022', situacao: null },
        { id: 2, titulo: 'Processo 445/2022 · Req. 3798/2023', situacao: 'Cancelado' },
      ]);
    });

    it('campos novos com tipo errado no localStorage são descartados', () => {
      localStorage.setItem(
        'rastro.recentes.operador.demo',
        JSON.stringify([{ id: 1, rotulo: 'A', numeroRequerimento: 7, situacao: { x: 1 }, extra: '<script>' }]),
      );

      expect(recentes.listar()).toEqual([{ id: 1, rotulo: 'A' }]);
    });

    it('o limite de 6 vale com os campos novos', () => {
      for (let id = 1; id <= 8; id++) {
        recentes.registrar({ id, rotulo: 'Processo 445/2022', numeroRequerimento: `${id}/2022`, situacao: 'Concluído' });
      }

      expect(recentes.listarParaExibicao().map((r) => r.id)).toEqual([8, 7, 6, 5, 4, 3]);
    });
  });
});
