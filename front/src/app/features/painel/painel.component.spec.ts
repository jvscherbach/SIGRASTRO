import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { PainelApiService } from './painel-api.service';
import { INTERVALO_DA_RELEITURA_MS, MAXIMO_DE_RELEITURAS, PainelComponent } from './painel.component';
import { PainelResposta } from './painel.models';

/** Números fictícios do cenário PN01 a PN15 (S10 bloco 1): só rótulos e totais. */
function painel(sobre: Partial<PainelResposta> = {}): PainelResposta {
  return {
    estado: 'PRONTO',
    ano: 2026,
    geradoEm: '2026-12-31T11:30:00-04:00',
    falhouEm: null,
    proximaAtualizacaoEm: '2026-12-31T12:00:00-04:00',
    truncado: false,
    serie: [
      { mes: 1, protocolados: 10, concluidos: 4 },
      { mes: 2, protocolados: 6, concluidos: 5 },
      { mes: 4, protocolados: 3, concluidos: 0 },
    ],
    totalProtocolados: 19,
    totalConcluidos: 9,
    semDataDeProtocolo: 1,
    filaPorEtapa: [
      { rotulo: 'Hídrico · Cadastro do requerimento', total: 5 },
      { rotulo: 'Hídrico · Análise técnica', total: 3 },
      { rotulo: 'LAC · Cadastro do requerimento', total: 0 },
    ],
    totalNaFila: 7,
    foraDoCatalogo: 2,
    semInstancia: 1,
    etapaNaoMapeada: 1,
    cargaPorResponsavel: [
      { rotulo: 'Servidor Exemplo 01', total: 4, tipo: 'SERVIDOR' },
      { rotulo: 'Área técnica', total: 3, tipo: 'AREA' },
      { rotulo: 'Interessado (cadastrante)', total: 2, tipo: 'INTERESSADO' },
      { rotulo: 'Não atribuída', total: 1, tipo: 'NAO_ATRIBUIDA' },
      { rotulo: 'Outros (3 servidores)', total: 2, tipo: 'OUTROS' },
    ],
    nomesOcultos: false,
    avisos: [],
    ...sobre,
  };
}

describe('PainelComponent', () => {
  let api: {
    obter: ReturnType<typeof vi.fn>;
    atualizar: ReturnType<typeof vi.fn>;
    analistas: ReturnType<typeof vi.fn>;
    requerimentosDoAnalista: ReturnType<typeof vi.fn>;
  };
  let fixture: ComponentFixture<PainelComponent>;
  let raiz: HTMLElement;

  async function abrir(resposta: PainelResposta): Promise<void> {
    api.obter.mockReturnValue(of(resposta));
    fixture = TestBed.createComponent(PainelComponent);
    raiz = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    api = { obter: vi.fn(), atualizar: vi.fn(), analistas: vi.fn(), requerimentosDoAnalista: vi.fn() };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: PainelApiService, useValue: api }] });
  });

  afterEach(() => vi.useRealTimers());

  it('PRONTO: cabeçalho com o ano, a data dos dados e os três gráficos', async () => {
    await abrir(painel());
    const texto = raiz.textContent ?? '';
    expect(texto).toContain('Ano de 2026 (requerimentos protocolados no ano)');
    expect(texto).toContain('Dados de 31/12/2026 11:30');
    expect(texto).toContain('Concluídos (mês aproximado)');
    expect(raiz.querySelectorAll('[role="img"]')).toHaveLength(3);
    expect(raiz.querySelectorAll('svg rect.barra')).toHaveLength(24);
    expect(texto).toContain('Hídrico · Cadastro do requerimento');
    expect(texto).toContain('1 sem data de protocolo');
  });

  it('o gráfico 2 é uma pizza: legenda na ordem do catálogo com as zeradas e as linhas especiais', async () => {
    await abrir(painel());
    const itens = Array.from(raiz.querySelectorAll('.pizza-legenda li'));
    const rotulos = itens.map((e) => e.querySelector('.rotulo')?.textContent?.trim());
    expect(rotulos).toEqual([
      'Hídrico · Cadastro do requerimento',
      'Hídrico · Análise técnica',
      'LAC · Cadastro do requerimento',
      'Fora do catálogo',
      'Sem instância',
      'Etapa não mapeada',
    ]);
    // soma exibida = 5 + 3 + 0 + 2 + 1 + 1 = 12
    expect(itens[0].textContent).toContain('5');
    expect(itens[0].querySelector('.percentual')?.textContent?.trim()).toBe('41,7%');
    expect(itens[2].querySelector('.valor')?.textContent?.trim()).toBe('0');
    expect(itens[2].querySelector('.percentual')?.textContent?.trim()).toBe('0,0%');
    // etapa zerada não tem fatia: 5 fatias com valor
    expect(raiz.querySelectorAll('svg.pizza-svg path.fatia')).toHaveLength(5);
    expect(raiz.querySelectorAll('.barras-h')).toHaveLength(1);
    expect(raiz.textContent).toContain('participação na soma das etapas (12)');
  });

  it('percentuais da pizza somam 100 (dentro do arredondamento)', async () => {
    await abrir(painel());
    const soma = Array.from(raiz.querySelectorAll('.pizza-legenda .percentual'))
      .map((e) => Number((e.textContent ?? '').replace('%', '').replace(',', '.')))
      .reduce((s, v) => s + v, 0);
    expect(Math.abs(soma - 100)).toBeLessThan(0.5);
  });

  it('uma só etapa com valor preenche o círculo inteiro', async () => {
    await abrir(
      painel({
        filaPorEtapa: [
          { rotulo: 'Hídrico · Análise técnica', total: 4 },
          { rotulo: 'LAC · Cadastro do requerimento', total: 0 },
        ],
        totalNaFila: 4,
        foraDoCatalogo: 0,
        semInstancia: 0,
        etapaNaoMapeada: 0,
      }),
    );
    expect(raiz.querySelectorAll('svg.pizza-svg circle.fatia')).toHaveLength(1);
    expect(raiz.querySelectorAll('svg.pizza-svg path.fatia')).toHaveLength(0);
    expect(raiz.querySelector('.pizza-legenda li .percentual')?.textContent?.trim()).toBe('100,0%');
  });

  it('tudo zerado: mostra a mensagem e nenhuma pizza', async () => {
    await abrir(
      painel({
        filaPorEtapa: [{ rotulo: 'Hídrico · Análise técnica', total: 0 }],
        totalNaFila: 0,
        foraDoCatalogo: 0,
        semInstancia: 0,
        etapaNaoMapeada: 0,
      }),
    );
    expect(raiz.textContent).toContain('Nenhum requerimento em andamento');
    expect(raiz.querySelectorAll('svg.pizza-svg')).toHaveLength(0);
  });

  it('muitas etapas: todas na legenda, mesmo com as cores se repetindo', async () => {
    const etapas = Array.from({ length: 20 }, (_, i) => ({ rotulo: `Etapa ${i + 1}`, total: i + 1 }));
    await abrir(painel({ filaPorEtapa: etapas, totalNaFila: 210, foraDoCatalogo: 0, semInstancia: 0, etapaNaoMapeada: 0 }));
    expect(raiz.querySelectorAll('.pizza-legenda li')).toHaveLength(23);
    expect(raiz.querySelectorAll('svg.pizza-svg path.fatia')).toHaveLength(20);
  });

  it('o item da legenda recebe foco por teclado e destaca a fatia', async () => {
    await abrir(painel());
    const item = raiz.querySelector('.pizza-legenda li') as HTMLElement;
    expect(item.getAttribute('tabindex')).toBe('0');
    item.dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    expect(raiz.querySelector('svg.pizza-svg path.fatia.destaque')).not.toBeNull();
  });

  it('avisa quando a soma das fatias passa do total na fila', async () => {
    await abrir(painel());
    expect(raiz.textContent).toContain('A soma das fatias (8) passa do total na fila (7)');
  });

  it('sem soma maior que o total, a nota não aparece', async () => {
    await abrir(painel({ totalNaFila: 8 }));
    expect(raiz.textContent).not.toContain('passa do total');
  });

  it('a tabela alternativa traz os mesmos números do gráfico', async () => {
    await abrir(painel());
    const botoes = Array.from(raiz.querySelectorAll('button')).filter((b) => b.textContent?.includes('Ver como tabela'));
    expect(botoes).toHaveLength(3);
    botoes.forEach((b) => b.click());
    fixture.detectChanges();
    const tabelas = Array.from(raiz.querySelectorAll('table'));
    expect(tabelas).toHaveLength(3);
    const celulas = (t: Element) =>
      Array.from(t.querySelectorAll('tbody tr')).map((tr) =>
        Array.from(tr.querySelectorAll('th,td'))
          .map((c) => c.textContent?.trim())
          .join(' '),
      );
    expect(celulas(tabelas[0])[0]).toBe('jan 10 4');
    expect(celulas(tabelas[0])[2]).toBe('mar 0 0');
    expect(celulas(tabelas[0])[3]).toBe('abr 3 0');
    expect(
      Array.from(tabelas[0].querySelectorAll('tfoot th,tfoot td'))
        .map((c) => c.textContent?.trim())
        .join(' '),
    ).toBe('Total 19 9');
    expect(celulas(tabelas[1])).toContain('Hídrico · Análise técnica 3');
    expect(celulas(tabelas[1])).toContain('Fora do catálogo 2');
    expect(celulas(tabelas[2])[1]).toBe('Área técnica Área 3 Ver lista');
    expect(raiz.querySelectorAll('svg.painel-svg')).toHaveLength(0);
  });

  describe('aba lateral Processos por analista (S11, bloco 9)', () => {
    beforeEach(() => {
      api.analistas.mockReturnValue(
        of({
          estado: 'PRONTO', ano: 2026, geradoEm: null, falhouEm: null, truncado: false, nomesOcultos: false,
          analistas: [
            { analista: 'aaaaaaaaaaaaaaaa', rotulo: 'Servidor Exemplo 01', total: 4, tipo: 'SERVIDOR' },
            { analista: 'bbbbbbbbbbbbbbbb', rotulo: 'Área técnica', total: 3, tipo: 'AREA' },
          ],
          etapas: [], avisos: [],
        }),
      );
      api.requerimentosDoAnalista.mockReturnValue(
        of({
          estado: 'PRONTO', ano: 2026, geradoEm: null, truncado: false, analista: 'bbbbbbbbbbbbbbbb', rotulo: 'Área técnica',
          tipo: 'AREA', total: 3, quantidade: 3, limitada: false, requerimentos: [], avisos: [],
        }),
      );
    });

    it('fica fechada e separada do dashboard; o botão abre a lista no primeiro analista', async () => {
      await abrir(painel());
      expect(document.querySelector('app-painel-analistas')).toBeNull();
      const botao = Array.from(raiz.querySelectorAll('button')).find((b) => b.textContent?.includes('Processos por analista'));
      botao?.click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(document.querySelector('app-painel-analistas')).not.toBeNull();
      expect(api.requerimentosDoAnalista).toHaveBeenCalledWith('aaaaaaaaaaaaaaaa', '', '');
    });

    it('o clique na barra de um analista abre a aba nele (pelo rótulo e tipo da barra)', async () => {
      await abrir(painel());
      const barras = Array.from(raiz.querySelectorAll('.barra-h.clicavel'));
      expect(barras).toHaveLength(5);
      (barras[1] as HTMLElement).click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(api.requerimentosDoAnalista).toHaveBeenCalledWith('bbbbbbbbbbbbbbbb', '', '');
    });

    it('na tabela, cada linha tem o botão Ver lista e Fechar tira a aba da tela', async () => {
      await abrir(painel());
      Array.from(raiz.querySelectorAll('button')).filter((b) => b.textContent?.includes('Ver como tabela')).forEach((b) => b.click());
      fixture.detectChanges();
      const ver = Array.from(raiz.querySelectorAll('button')).filter((b) => b.textContent?.includes('Ver lista'));
      expect(ver).toHaveLength(5);
      ver[1].click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(api.requerimentosDoAnalista).toHaveBeenCalledWith('bbbbbbbbbbbbbbbb', '', '');
      (document.querySelector('app-painel-analistas footer button') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(document.querySelector('app-painel-analistas')).toBeNull();
    });
  });

  it('nomes ocultos: mostra a nota e nenhum nome de servidor', async () => {
    await abrir(
      painel({
        nomesOcultos: true,
        cargaPorResponsavel: [
          { rotulo: 'Área técnica', total: 3, tipo: 'AREA' },
          { rotulo: 'Não atribuída', total: 1, tipo: 'NAO_ATRIBUIDA' },
        ],
      }),
    );
    expect(raiz.textContent).toContain('Nomes ocultos por configuração');
    expect(raiz.textContent).not.toContain('Servidor Exemplo');
  });

  it('nunca mostra CPF nem id técnico', async () => {
    await abrir(painel());
    const texto = raiz.innerHTML;
    expect(texto).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}/);
    expect(texto).not.toMatch(/\b\d{11}\b/);
    expect(texto.toLowerCase()).not.toContain('assignee');
    expect(texto).not.toContain('Activity_');
  });

  it('PREPARANDO: selo, sem gráficos e nova leitura a cada 5 s, no máximo 2 minutos', async () => {
    await abrir(painel({ estado: 'PREPARANDO', geradoEm: null, serie: [], filaPorEtapa: [], cargaPorResponsavel: [] }));
    expect(raiz.textContent).toContain('Preparando o painel');
    expect(raiz.querySelectorAll('[role="img"]')).toHaveLength(0);
    expect(api.obter).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(INTERVALO_DA_RELEITURA_MS);
    expect(api.obter).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(INTERVALO_DA_RELEITURA_MS * (MAXIMO_DE_RELEITURAS + 5));
    expect(api.obter).toHaveBeenCalledTimes(MAXIMO_DE_RELEITURAS + 1);
    fixture.detectChanges();
    expect(raiz.textContent).toContain('A leitura está demorando');
  });

  it('PREPARANDO termina quando o painel fica PRONTO e a releitura para', async () => {
    await abrir(painel({ estado: 'PREPARANDO', geradoEm: null }));
    api.obter.mockReturnValue(of(painel()));
    await vi.advanceTimersByTimeAsync(INTERVALO_DA_RELEITURA_MS);
    fixture.detectChanges();
    expect(raiz.querySelectorAll('[role="img"]')).toHaveLength(3);
    await vi.advanceTimersByTimeAsync(INTERVALO_DA_RELEITURA_MS * 3);
    expect(api.obter).toHaveBeenCalledTimes(2);
  });

  it('DESATUALIZADO: avisa a falha e mostra o último bom com a data', async () => {
    await abrir(painel({ estado: 'DESATUALIZADO', falhouEm: '2026-12-31T11:45:00-04:00' }));
    const texto = raiz.textContent ?? '';
    expect(texto).toContain('Última leitura falhou em 31/12/2026 11:45; mostrando a de 31/12/2026 11:30');
    expect(raiz.querySelectorAll('[role="img"]')).toHaveLength(3);
  });

  it('INDISPONIVEL: selo de erro e nenhum gráfico', async () => {
    await abrir(painel({ estado: 'INDISPONIVEL', geradoEm: null, serie: [], filaPorEtapa: [], cargaPorResponsavel: [] }));
    expect(raiz.textContent).toContain('Painel indisponível');
    expect(raiz.querySelectorAll('[role="img"]')).toHaveLength(0);
  });

  it('truncado: mostra o aviso do servidor uma vez', async () => {
    await abrir(painel({ truncado: true, avisos: [{ codigo: 'TRUNCADO', texto: 'Mostrando os 3000 primeiros requerimentos do ano' }] }));
    const ocorrencias = (raiz.textContent ?? '').split('Mostrando os 3000 primeiros requerimentos do ano').length - 1;
    expect(ocorrencias).toBe(1);
  });

  it('Atualizar agora: pede, avisa e relê até chegar uma data nova', async () => {
    await abrir(painel());
    api.atualizar.mockReturnValue(of(null));
    const botao = Array.from(raiz.querySelectorAll('button')).find((b) => b.textContent?.includes('Atualizar agora'))!;
    botao.click();
    fixture.detectChanges();
    expect(api.atualizar).toHaveBeenCalledTimes(1);
    expect(raiz.textContent).toContain('Atualização pedida');

    await vi.advanceTimersByTimeAsync(INTERVALO_DA_RELEITURA_MS);
    expect(api.obter).toHaveBeenCalledTimes(2);
    api.obter.mockReturnValue(of(painel({ geradoEm: '2026-12-31T11:40:00-04:00' })));
    await vi.advanceTimersByTimeAsync(INTERVALO_DA_RELEITURA_MS);
    fixture.detectChanges();
    expect(raiz.textContent).toContain('Dados de 31/12/2026 11:40');
    await vi.advanceTimersByTimeAsync(INTERVALO_DA_RELEITURA_MS * 3);
    expect(api.obter).toHaveBeenCalledTimes(3);
  });

  it('429: mostra o tempo que falta', async () => {
    await abrir(painel());
    const erro = new HttpErrorResponse({ status: 429, error: { errorCode: 'ATUALIZACAO_RECENTE', tentarEmSegundos: 87 } });
    api.atualizar.mockReturnValue(throwError(() => erro));
    Array.from(raiz.querySelectorAll('button')).find((b) => b.textContent?.includes('Atualizar agora'))!.click();
    fixture.detectChanges();
    expect(raiz.textContent).toContain('Tente de novo em 87 segundos');
  });

  it('erro ao ler: mostra a mensagem no corpo da tela', async () => {
    api.obter.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    fixture = TestBed.createComponent(PainelComponent);
    raiz = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(raiz.querySelector('[role="alert"]')).not.toBeNull();
  });
});
