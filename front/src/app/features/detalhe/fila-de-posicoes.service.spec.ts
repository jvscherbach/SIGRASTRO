import { TestBed } from '@angular/core/testing';
import { Observable, Subject, Subscription, of } from 'rxjs';

import { DetalheApiService } from './detalhe-api.service';
import { FilaDePosicoes, MAXIMO_DE_POSICOES_SIMULTANEAS } from './fila-de-posicoes.service';
import { PosicaoDoRequerimento } from './posicao.models';

describe('FilaDePosicoes', () => {
  let pedidos: Map<number, Subject<PosicaoDoRequerimento>>;
  let fila: FilaDePosicoes;
  /** Resposta de /api/tramitacao/parametros; sem valor, responde na hora com o padrão. */
  let parametros: Observable<{ posicoesSimultaneas: number }> | null;

  beforeEach(() => {
    pedidos = new Map();
    parametros = null;
    const api = {
      posicao: vi.fn((id: number) => {
        const s = new Subject<PosicaoDoRequerimento>();
        pedidos.set(id, s);
        return s;
      }),
      parametrosDeExibicao: vi.fn(() => parametros ?? of({ posicoesSimultaneas: MAXIMO_DE_POSICOES_SIMULTANEAS })),
    };
    TestBed.configureTestingModule({ providers: [{ provide: DetalheApiService, useValue: api }] });
    fila = TestBed.inject(FilaDePosicoes);
  });

  const resposta = (id: number) => ({ idRequerimento: id }) as PosicaoDoRequerimento;

  it(`nunca passa de ${MAXIMO_DE_POSICOES_SIMULTANEAS} ao mesmo tempo; o próximo começa quando um termina`, () => {
    const recebidos: number[] = [];
    for (let id = 1; id <= 10; id++) {
      fila.posicao(id).subscribe((p) => recebidos.push(p.idRequerimento));
    }
    expect(pedidos.size).toBe(MAXIMO_DE_POSICOES_SIMULTANEAS);
    expect(fila.ativos).toBe(MAXIMO_DE_POSICOES_SIMULTANEAS);

    pedidos.get(1)!.next(resposta(1));
    pedidos.get(1)!.complete();
    expect(recebidos).toEqual([1]);
    expect(pedidos.size).toBe(MAXIMO_DE_POSICOES_SIMULTANEAS + 1);
    expect(fila.ativos).toBe(MAXIMO_DE_POSICOES_SIMULTANEAS);
  });

  it('o limite vem das Configurações (S7, bloco 10): até a resposta, um por vez; depois, o valor do servidor', () => {
    const doServidor = new Subject<{ posicoesSimultaneas: number }>();
    parametros = doServidor;
    for (let id = 1; id <= 10; id++) {
      fila.posicao(id).subscribe();
    }
    expect(pedidos.size).toBe(1); // nunca ultrapassa um limite menor que o padrão

    doServidor.next({ posicoesSimultaneas: 2 });
    expect(fila.limite).toBe(2);
    expect(pedidos.size).toBe(2);
  });

  it('limite fora da faixa é ajustado e erro na leitura usa o padrão', () => {
    const doServidor = new Subject<{ posicoesSimultaneas: number }>();
    parametros = doServidor;
    fila.posicao(1).subscribe();
    doServidor.next({ posicoesSimultaneas: 99 });
    expect(fila.limite).toBe(20);

    TestBed.resetTestingModule();
    const comErro = new Subject<{ posicoesSimultaneas: number }>();
    TestBed.configureTestingModule({
      providers: [{ provide: DetalheApiService, useValue: { posicao: vi.fn(() => new Subject()), parametrosDeExibicao: () => comErro } }],
    });
    const outra = TestBed.inject(FilaDePosicoes);
    outra.posicao(1).subscribe();
    comErro.error(new Error('fora do ar'));
    expect(outra.limite).toBe(MAXIMO_DE_POSICOES_SIMULTANEAS);
  });

  it('erro libera a vez e não afeta os demais', () => {
    const erros: number[] = [];
    const recebidos: number[] = [];
    for (let id = 1; id <= 5; id++) {
      fila.posicao(id).subscribe({ next: (p) => recebidos.push(p.idRequerimento), error: () => erros.push(id) });
    }
    pedidos.get(2)!.error(new Error('falha'));
    expect(erros).toEqual([2]);
    expect(pedidos.has(5)).toBe(true);

    pedidos.get(5)!.next(resposta(5));
    expect(recebidos).toEqual([5]);
  });

  it('cancelar na fila não chega ao servidor; cancelar em andamento libera a vez', () => {
    const assinaturas: Subscription[] = [];
    for (let id = 1; id <= 6; id++) {
      assinaturas.push(fila.posicao(id).subscribe());
    }
    assinaturas[5].unsubscribe(); // id 6, ainda na fila
    assinaturas[0].unsubscribe(); // id 1, em andamento

    expect(pedidos.has(5)).toBe(true);
    expect(pedidos.has(6)).toBe(false);
    expect(fila.ativos).toBe(MAXIMO_DE_POSICOES_SIMULTANEAS);
  });
});
