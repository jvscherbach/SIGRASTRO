import { lerComMigracao, removerComAntiga } from './armazenamento';

describe('armazenamento: chave antiga vistas.* como reserva (R1, D75)', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('sem chave antiga lê a nova', () => {
    sessionStorage.setItem('rastro.token', 'novo');
    expect(lerComMigracao(sessionStorage, 'token')).toBe('novo');
  });

  it('só com a antiga devolve o valor, migra para a nova e apaga a antiga', () => {
    sessionStorage.setItem('vistas.token', 'antigo');

    expect(lerComMigracao(sessionStorage, 'token')).toBe('antigo');
    expect(sessionStorage.getItem('rastro.token')).toBe('antigo');
    expect(sessionStorage.getItem('vistas.token')).toBeNull();
  });

  it('com as duas vale a nova e a antiga é apagada', () => {
    localStorage.setItem('vistas.recentes.u-teste', '[1]');
    localStorage.setItem('rastro.recentes.u-teste', '[2]');

    expect(lerComMigracao(localStorage, 'recentes.u-teste')).toBe('[2]');
    expect(localStorage.getItem('vistas.recentes.u-teste')).toBeNull();
  });

  it('sem nenhuma devolve null', () => {
    expect(lerComMigracao(localStorage, 'recentes.ninguem')).toBeNull();
  });

  it('se a cópia falhar (cota), devolve o antigo e não o apaga', () => {
    const memoria = new Map<string, string>([['vistas.token', 'antigo']]);
    const cheio = {
      getItem: (k: string) => memoria.get(k) ?? null,
      setItem: () => {
        throw new DOMException('cheio', 'QuotaExceededError');
      },
      removeItem: (k: string) => memoria.delete(k),
    } as unknown as Storage;

    expect(lerComMigracao(cheio, 'token')).toBe('antigo');
    expect(memoria.get('vistas.token')).toBe('antigo');
  });

  it('removerComAntiga apaga as duas', () => {
    sessionStorage.setItem('vistas.token', 'a');
    sessionStorage.setItem('rastro.token', 'b');

    removerComAntiga(sessionStorage, 'token');

    expect(sessionStorage.length).toBe(0);
  });
});
