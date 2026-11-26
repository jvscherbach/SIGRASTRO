import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ESPERA_DA_BUSCA_MS, PainelAnalistasComponent } from './painel-analistas.component';
import { PainelApiService } from './painel-api.service';
import { AnalistasDoPainelResposta, RequerimentosDoAnalistaResposta } from './painel.models';

/** Dados fictícios: só rótulos, totais e identificadores opacos. */
function base(sobre: Partial<AnalistasDoPainelResposta> = {}): AnalistasDoPainelResposta {
  return {
    estado: 'PRONTO',
    ano: 2026,
    geradoEm: '2026-12-31T11:30:00-04:00',
    falhouEm: null,
    truncado: false,
    nomesOcultos: false,
    analistas: [
      { analista: 'aaaaaaaaaaaaaaaa', rotulo: 'Servidor Exemplo 01', total: 4, tipo: 'SERVIDOR' },
      { analista: 'bbbbbbbbbbbbbbbb', rotulo: 'Não atribuída', total: 1, tipo: 'NAO_ATRIBUIDA' },
      { analista: 'cccccccccccccccc', rotulo: 'Outros (3 servidores)', total: 2, tipo: 'OUTROS' },
    ],
    etapas: ['Hídrico · Análise do processo', 'Hídrico · Revisão do parecer técnico', 'Etapa não mapeada'],
    avisos: [],
    ...sobre,
  };
}

function lista(sobre: Partial<RequerimentosDoAnalistaResposta> = {}): RequerimentosDoAnalistaResposta {
  return {
    estado: 'PRONTO',
    ano: 2026,
    geradoEm: '2026-12-31T11:30:00-04:00',
    truncado: false,
    analista: 'aaaaaaaaaaaaaaaa',
    rotulo: 'Servidor Exemplo 01',
    tipo: 'SERVIDOR',
    total: 4,
    quantidade: 4,
    limitada: false,
    requerimentos: [
      { id: 1, numero: 'REQ-1', interessado: 'Interessado Exemplo LTDA', protocolo: '2026-02-10', etapas: ['Hídrico · Análise do processo'], responsavel: 'Servidor Exemplo 01' },
      { id: 2, numero: 'REQ-2', interessado: null, protocolo: null, etapas: ['Hídrico · Análise do processo', 'Hídrico · Revisão do parecer técnico'], responsavel: 'Servidor Exemplo 01' },
    ],
    avisos: [],
    ...sobre,
  };
}

describe('PainelAnalistasComponent', () => {
  let api: { analistas: ReturnType<typeof vi.fn>; requerimentosDoAnalista: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<PainelAnalistasComponent>;
  let fechou: number;

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function abrir(escolhido: { rotulo: string; tipo: 'SERVIDOR' | 'NAO_ATRIBUIDA' | 'OUTROS' } | null = null): Promise<void> {
    fixture = TestBed.createComponent(PainelAnalistasComponent);
    if (escolhido) {
      fixture.componentRef.setInput('escolhido', escolhido);
    }
    fixture.componentInstance.fechar.subscribe(() => (fechou += 1));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    fechou = 0;
    api = { analistas: vi.fn().mockReturnValue(of(base())), requerimentosDoAnalista: vi.fn().mockReturnValue(of(lista())) };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: PainelApiService, useValue: api }] });
  });

  afterEach(() => {
    fixture?.destroy();
    vi.useRealTimers();
  });

  it('abre no analista da barra clicada e mostra o total da barra, o interessado e as etapas', async () => {
    await abrir({ rotulo: 'Não atribuída', tipo: 'NAO_ATRIBUIDA' });
    expect(api.requerimentosDoAnalista).toHaveBeenCalledWith('bbbbbbbbbbbbbbbb', '', '');
    expect(texto()).toContain('4 requerimentos (o total da barra)');
    expect(texto()).toContain('Interessado Exemplo LTDA');
    expect(texto()).toContain('Interessado não informado');
    expect(texto()).toContain('Hídrico · Análise do processo · Hídrico · Revisão do parecer técnico');
    expect(texto()).toContain('Protocolo: 10/02/2026');
    expect(texto()).toContain('Protocolo: sem data');
  });

  it('sem barra escolhida abre no primeiro analista e os filtros mandam etapa e busca ao servidor', async () => {
    await abrir();
    expect(api.requerimentosDoAnalista).toHaveBeenCalledWith('aaaaaaaaaaaaaaaa', '', '');

    const el = fixture.nativeElement as HTMLElement;
    const etapa = el.querySelector('select[aria-label="Etapa"]') as HTMLSelectElement;
    etapa.value = 'Etapa não mapeada';
    etapa.dispatchEvent(new Event('change'));
    expect(api.requerimentosDoAnalista).toHaveBeenLastCalledWith('aaaaaaaaaaaaaaaa', 'Etapa não mapeada', '');

    const campo = el.querySelector('input[type="search"]') as HTMLInputElement;
    campo.value = 'acai';
    campo.dispatchEvent(new Event('input'));
    expect(api.requerimentosDoAnalista).toHaveBeenCalledTimes(2);
    vi.advanceTimersByTime(ESPERA_DA_BUSCA_MS);
    expect(api.requerimentosDoAnalista).toHaveBeenLastCalledWith('aaaaaaaaaaaaaaaa', 'Etapa não mapeada', 'acai');

    const analista = el.querySelector('select[aria-label="Analista"]') as HTMLSelectElement;
    analista.value = 'cccccccccccccccc';
    analista.dispatchEvent(new Event('change'));
    expect(api.requerimentosDoAnalista).toHaveBeenLastCalledWith('cccccccccccccccc', 'Etapa não mapeada', 'acai');
  });

  it('mostra com os filtros quantos sobraram e o selo de lista limitada', async () => {
    api.requerimentosDoAnalista.mockReturnValue(of(lista({ quantidade: 2, total: 4, limitada: true })));
    await abrir();
    expect(texto()).toContain('2 com os filtros');
    expect(texto()).toContain('Mostrando os 1000 primeiros');
  });

  it('truncado: mostra o selo de lista parcial (o instantâneo do painel foi parcial)', async () => {
    api.analistas.mockReturnValue(of(base({ truncado: true, avisos: [{ codigo: 'TRUNCADO', texto: 'Mostrando os 20000 primeiros requerimentos do ano.' }] })));
    await abrir();
    expect(texto()).toContain('Mostrando os 20000 primeiros requerimentos do ano.');
  });

  it('nomes ocultos: diz a nota e só traz categorias sem nome', async () => {
    api.analistas.mockReturnValue(
      of(base({ nomesOcultos: true, analistas: [{ analista: 'bbbbbbbbbbbbbbbb', rotulo: 'Não atribuída', total: 1, tipo: 'NAO_ATRIBUIDA' }] })),
    );
    api.requerimentosDoAnalista.mockReturnValue(of(lista({ rotulo: 'Não atribuída', tipo: 'NAO_ATRIBUIDA', total: 1, quantidade: 1 })));
    await abrir();
    expect(texto()).toContain('Nomes ocultos por configuração');
    expect(texto()).not.toContain('Servidor Exemplo');
  });

  it('PREPARANDO e INDISPONIVEL dizem o estado e não chamam a lista', async () => {
    api.analistas.mockReturnValue(of(base({ estado: 'PREPARANDO', analistas: [], etapas: [] })));
    await abrir();
    expect(texto()).toContain('preparando a primeira leitura');
    expect(api.requerimentosDoAnalista).not.toHaveBeenCalled();
    fixture.destroy();

    api.analistas.mockReturnValue(of(base({ estado: 'INDISPONIVEL', analistas: [], etapas: [] })));
    await abrir();
    expect(texto()).toContain('Painel indisponível');
  });

  it('DESATUALIZADO mostra o selo com a data da última leitura boa', async () => {
    api.analistas.mockReturnValue(of(base({ estado: 'DESATUALIZADO', falhouEm: '2026-12-31T11:40:00-04:00' })));
    await abrir();
    expect(texto()).toContain('Última leitura falhou em 31/12/2026 11:40');
  });

  it('erro ao carregar os analistas mostra a mensagem e oferece tentar de novo', async () => {
    api.analistas.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    await abrir();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
    expect(texto()).toContain('Tentar novamente');
  });

  it('404 na lista (instantâneo trocado) relê os analistas uma vez só', async () => {
    api.requerimentosDoAnalista.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    await abrir();
    expect(api.analistas).toHaveBeenCalledTimes(2);
    expect(api.requerimentosDoAnalista).toHaveBeenCalledTimes(2);
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
  });

  it('é um diálogo com foco preso; Esc, o fundo e os botões Fechar fecham', async () => {
    await abrir();
    const el = fixture.nativeElement as HTMLElement;
    const dialogo = el.querySelector('section[role="dialog"]') as HTMLElement;
    expect(dialogo.getAttribute('aria-modal')).toBe('true');
    expect(dialogo.getAttribute('aria-labelledby')).toBe('painel-analistas-titulo');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(fechou).toBe(1);
    (el.querySelector('.scrim') as HTMLElement).click();
    expect(fechou).toBe(2);
    (el.querySelector('footer button') as HTMLButtonElement).click();
    expect(fechou).toBe(3);
  });

  it('o DOM nunca traz CPF nem CNPJ', async () => {
    await abrir();
    expect(texto()).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}/);
    expect(texto()).not.toMatch(/\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2}/);
    expect(texto()).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
  });
});
