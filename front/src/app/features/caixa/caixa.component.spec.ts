import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { CaixaApiService } from './caixa-api.service';
import {
  CaixaComponent,
  ESPERA_DA_BUSCA_MS,
  INTERVALO_DA_RELEITURA_MS,
  horaDaAtualizacao,
  textoDosDias,
} from './caixa.component';
import { AvisosResposta, CaixaResposta } from './caixa.models';

/** Dados fictícios do cenário CX da S11: só nome do interessado, nunca CPF ou CNPJ. */
function caixa(sobre: Partial<CaixaResposta> = {}): CaixaResposta {
  return {
    estado: 'PRONTO',
    geradoEm: '2028-06-15T12:30:00-04:00',
    falhouEm: null,
    incompleta: false,
    semVinculo: false,
    mensagem: null,
    novos: 0,
    resumo: {
      total: 3,
      comInteressado: 1,
      porEtapa: [
        { id: 'analise_do_processo', rotulo: 'Análise do processo', quantidade: 1 },
        { id: 'revisao_do_parecer_tecnico', rotulo: 'Revisão do parecer técnico', quantidade: 1 },
      ],
    },
    linhas: [
      {
        id: 15001,
        numero: '1001/2028',
        interessado: 'Interessado Exemplo 01',
        tipo: 'Outorga',
        posse: 'COM_ANALISTA',
        etapaId: 'analise_do_processo',
        etapa: 'Análise do processo',
        diasNaEtapa: 20,
        novo: false,
      },
      {
        id: 15003,
        numero: '1003/2028',
        interessado: 'Interessado Exemplo LTDA',
        tipo: 'Outorga',
        posse: 'COM_ANALISTA',
        etapaId: 'revisao_do_parecer_tecnico',
        etapa: 'Revisão do parecer técnico',
        diasNaEtapa: null,
        novo: true,
      },
      {
        id: 15004,
        numero: '1004/2028',
        interessado: 'Cooperativa Exemplo 04',
        tipo: 'Outorga',
        posse: 'COM_INTERESSADO',
        etapaId: 'correcao_do_requerimento',
        etapa: 'Correção do requerimento',
        diasNaEtapa: 1,
        novo: false,
      },
    ],
    ...sobre,
  };
}

const AVISOS: AvisosResposta = {
  semVinculo: false,
  mensagem: null,
  quantidade: 1,
  avisos: [
    {
      id: 15005,
      numero: '1005/2028',
      interessado: 'Interessado Exemplo 03',
      tipo: 'Outorga',
      etapaId: 'analise_do_processo',
      etapa: 'Análise do processo',
      origemCodigo: 'VOLTOU_DA_CORRECAO',
      origem: 'Voltou da correção',
    },
  ],
};

describe('CaixaComponent', () => {
  let api: {
    obter: ReturnType<typeof vi.fn>;
    avisos: ReturnType<typeof vi.fn>;
    marcarVisto: ReturnType<typeof vi.fn>;
    atualizar: ReturnType<typeof vi.fn>;
  };
  let fixture: ComponentFixture<CaixaComponent>;
  let raiz: HTMLElement;

  async function abrir(resposta: CaixaResposta): Promise<void> {
    api.obter.mockReturnValue(of(resposta));
    fixture = TestBed.createComponent(CaixaComponent);
    raiz = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function estabilizar(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    api = { obter: vi.fn(), avisos: vi.fn(), marcarVisto: vi.fn(), atualizar: vi.fn() };
    api.avisos.mockReturnValue(of(AVISOS));
    api.marcarVisto.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({ providers: [{ provide: CaixaApiService, useValue: api }, provideRouter([])] });
  });

  afterEach(() => {
    fixture?.destroy();
    vi.useRealTimers();
  });

  it('mostra o resumo, a lista com o interessado, os dias e a marca "Novo"', async () => {
    await abrir(caixa());
    const texto = raiz.textContent ?? '';
    expect(texto).toContain('Atualizado às');
    expect(raiz.querySelector('[data-resumo="total"]')?.textContent).toContain('3');
    expect(raiz.querySelector('[data-resumo="correcao"]')?.textContent).toContain('1');
    expect(raiz.querySelectorAll('.caixa-lista li')).toHaveLength(3);
    expect(texto).toContain('Interessado Exemplo LTDA');
    expect(texto).toContain('20 dias na etapa');
    expect(texto).toContain('sem informação na etapa');
    expect(texto).toContain('1 dia na etapa');
    expect(raiz.querySelectorAll('.caixa-novo')).toHaveLength(1);
    expect(raiz.querySelector('.caixa-lista .tag.amber')?.textContent).toContain('Com o interessado');
  });

  it('o texto de ajuda diz que o visto some ao reiniciar', async () => {
    await abrir(caixa());
    expect(raiz.querySelector('.caixa-ajuda')?.textContent).toContain('ao reiniciar o sistema');
  });

  it('a caixa vazia diz que não há requerimentos', async () => {
    await abrir(caixa({ linhas: [], resumo: { total: 0, comInteressado: 0, porEtapa: [] } }));
    expect(raiz.textContent).toContain('Nenhum requerimento na sua caixa.');
  });

  it('sem CPF vinculado mostra a mensagem e nenhuma lista', async () => {
    await abrir(
      caixa({ semVinculo: true, mensagem: 'Seu usuário não está vinculado a um servidor. Peça ao Administrador.', linhas: [] }),
    );
    expect(raiz.textContent).toContain('Seu usuário não está vinculado a um servidor. Peça ao Administrador.');
    expect(raiz.querySelector('.caixa-lista')).toBeNull();
    expect(raiz.querySelector('.caixa-aviso')).toBeNull();
  });

  it('PREPARANDO avisa e relê a cada 5 segundos até ficar pronta', async () => {
    api.obter.mockReturnValueOnce(of(caixa({ estado: 'PREPARANDO', geradoEm: null, linhas: [] }))).mockReturnValue(of(caixa()));
    fixture = TestBed.createComponent(CaixaComponent);
    raiz = fixture.nativeElement as HTMLElement;
    await estabilizar();
    expect(raiz.textContent).toContain('Preparando a sua caixa');
    vi.advanceTimersByTime(INTERVALO_DA_RELEITURA_MS);
    await estabilizar();
    expect(api.obter).toHaveBeenCalledTimes(2);
    expect(raiz.querySelectorAll('.caixa-lista li')).toHaveLength(3);
  });

  it('DESATUALIZADO mantém a lista e avisa; INDISPONIVEL avisa sem lista', async () => {
    await abrir(caixa({ estado: 'DESATUALIZADO' }));
    expect(raiz.textContent).toContain('A última leitura falhou');
    expect(raiz.querySelectorAll('.caixa-lista li')).toHaveLength(3);
    fixture.destroy();

    await abrir(caixa({ estado: 'INDISPONIVEL', geradoEm: null, linhas: [] }));
    expect(raiz.textContent).toContain('A caixa está indisponível');
    expect(raiz.querySelector('.caixa-lista')).toBeNull();
  });

  it('leitura parcial é avisada', async () => {
    await abrir(caixa({ incompleta: true }));
    expect(raiz.textContent).toContain('A leitura foi parcial');
  });

  it('erro de rede mostra mensagem no corpo', async () => {
    api.obter.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fixture = TestBed.createComponent(CaixaComponent);
    raiz = fixture.nativeElement as HTMLElement;
    await estabilizar();
    expect(raiz.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('as abas por etapa pedem a etapa ao servidor', async () => {
    await abrir(caixa());
    const botoes = Array.from(raiz.querySelectorAll<HTMLButtonElement>('.caixa-visoes button'));
    expect(botoes.map((b) => b.textContent?.trim())).toEqual([
      'Geral',
      'Análise do processo (1)',
      'Revisão do parecer técnico (1)',
      'Com o interessado (1)',
    ]);
    botoes[1].click();
    await estabilizar();
    expect(api.obter).toHaveBeenLastCalledWith({ etapa: 'analise_do_processo', busca: '', ordem: 'MAIS_PARADO' });
    expect(raiz.querySelectorAll<HTMLButtonElement>('.caixa-visoes button')[1].getAttribute('aria-pressed')).toBe('true');
  });

  it('a busca espera a digitação e a ordenação troca para mais recente', async () => {
    await abrir(caixa());
    const campo = raiz.querySelector<HTMLInputElement>('input[type="search"]')!;
    campo.value = 'Exemplo LTDA';
    campo.dispatchEvent(new Event('input'));
    expect(api.obter).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(ESPERA_DA_BUSCA_MS);
    expect(api.obter).toHaveBeenLastCalledWith({ etapa: '', busca: 'Exemplo LTDA', ordem: 'MAIS_PARADO' });

    const ordem = raiz.querySelector<HTMLSelectElement>('select')!;
    ordem.value = 'MAIS_RECENTE';
    ordem.dispatchEvent(new Event('change'));
    expect(api.obter).toHaveBeenLastCalledWith({ etapa: '', busca: 'Exemplo LTDA', ordem: 'MAIS_RECENTE' });
  });

  it('sem resultado na busca, diz que nenhum corresponde aos filtros', async () => {
    await abrir(caixa({ linhas: [] }));
    const campo = raiz.querySelector<HTMLInputElement>('input[type="search"]')!;
    campo.value = 'zzz';
    campo.dispatchEvent(new Event('input'));
    await estabilizar();
    expect(raiz.textContent).toContain('Nenhum requerimento corresponde aos filtros.');
  });

  describe('caixinha de aviso', () => {
    it('sem novos, não aparece', async () => {
      await abrir(caixa({ novos: 0 }));
      expect(raiz.querySelector('.caixa-aviso')).toBeNull();
      expect(raiz.querySelector('[aria-live="polite"]')).not.toBeNull();
    });

    it('mostra a quantidade no singular e no plural', async () => {
      await abrir(caixa({ novos: 1 }));
      expect(raiz.querySelector('.caixa-aviso')?.textContent).toContain('1 requerimento chegou para você');
      fixture.destroy();
      await abrir(caixa({ novos: 3 }));
      expect(raiz.querySelector('.caixa-aviso')?.textContent).toContain('3 requerimentos chegaram para você');
    });

    it('ao clicar abre a lista de mudanças com a origem e grava o visto do conjunto; depois some', async () => {
      await abrir(caixa({ novos: 1 }));
      api.obter.mockReturnValue(of(caixa({ novos: 0 })));
      raiz.querySelector<HTMLButtonElement>('.caixa-aviso')!.click();
      await estabilizar();

      const dialogo = document.body.querySelector('[role="dialog"]')!;
      expect(dialogo).not.toBeNull();
      expect(dialogo.textContent).toContain('1005/2028');
      expect(dialogo.textContent).toContain('Interessado Exemplo 03');
      expect(dialogo.textContent).toContain('Voltou da correção');
      expect(api.marcarVisto).toHaveBeenCalledWith([15005]);
      expect(raiz.querySelector('.caixa-aviso')).toBeNull();
    });

    it('fechar a lista tira o diálogo e devolve o foco ao título quando a caixinha some', async () => {
      await abrir(caixa({ novos: 1 }));
      api.obter.mockReturnValue(of(caixa({ novos: 0 })));
      raiz.querySelector<HTMLButtonElement>('.caixa-aviso')!.click();
      await estabilizar();
      document.body.querySelector<HTMLButtonElement>('[role="dialog"] footer button')!.click();
      await estabilizar();
      await Promise.resolve();
      expect(document.body.querySelector('[role="dialog"]')).toBeNull();
      expect(document.activeElement).toBe(raiz.querySelector('h2'));
    });

    it('lista de mudanças vazia diz que nada mudou e não grava visto', async () => {
      api.avisos.mockReturnValue(of({ semVinculo: false, mensagem: null, quantidade: 0, avisos: [] }));
      await abrir(caixa({ novos: 2 }));
      raiz.querySelector<HTMLButtonElement>('.caixa-aviso')!.click();
      await estabilizar();
      expect(document.body.querySelector('[role="dialog"]')?.textContent).toContain('Nenhuma mudança nova.');
      expect(api.marcarVisto).not.toHaveBeenCalled();
    });

    it('falha ao ler as mudanças mostra o erro e deixa tentar de novo', async () => {
      api.avisos.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
      await abrir(caixa({ novos: 2 }));
      raiz.querySelector<HTMLButtonElement>('.caixa-aviso')!.click();
      await estabilizar();
      expect(document.body.querySelector('[role="dialog"] [role="alert"]')).not.toBeNull();
      expect(api.marcarVisto).not.toHaveBeenCalled();
    });
  });

  describe('atualizar', () => {
    it('pede a atualização e espera uma data nova', async () => {
      await abrir(caixa());
      api.atualizar.mockReturnValue(of(undefined));
      raiz.querySelector<HTMLButtonElement>('.dash-sec-head button')!.click();
      await estabilizar();
      expect(raiz.textContent).toContain('Atualização pedida');
      api.obter.mockReturnValue(of(caixa({ geradoEm: '2028-06-15T12:40:00-04:00' })));
      vi.advanceTimersByTime(INTERVALO_DA_RELEITURA_MS);
      await estabilizar();
      expect(raiz.textContent).toContain('Atualizado às 12:40');
    });

    it('429 mostra a espera em segundos', async () => {
      await abrir(caixa());
      api.atualizar.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 429, error: { tentarEmSegundos: 42 } })),
      );
      raiz.querySelector<HTMLButtonElement>('.dash-sec-head button')!.click();
      await estabilizar();
      expect(raiz.textContent).toContain('Tente de novo em 42 segundos.');
    });
  });

  it('nenhum CPF ou CNPJ aparece no texto da tela', async () => {
    await abrir(caixa({ novos: 1 }));
    expect(raiz.textContent).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}|\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2}/);
  });
});

describe('utilidades da caixa', () => {
  it('textoDosDias não inventa dado', () => {
    expect(textoDosDias(null)).toBe('sem informação');
    expect(textoDosDias(0)).toBe('hoje');
    expect(textoDosDias(1)).toBe('1 dia');
    expect(textoDosDias(400)).toBe('400 dias');
  });

  it('horaDaAtualizacao usa o horário de Cuiabá', () => {
    expect(horaDaAtualizacao('2028-06-15T12:30:00-04:00')).toBe('12:30');
    expect(horaDaAtualizacao('2028-06-15T16:30:00Z')).toBe('12:30');
    expect(horaDaAtualizacao(null)).toBe('');
  });
});
