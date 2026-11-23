import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';

import { DetalheApiService } from './detalhe-api.service';
import { FinanceiroComponent } from './financeiro.component';
import { AvisoFinanceiro, FinanceiroDoRequerimento, LancamentoFinanceiro } from './financeiro.models';

/** Lançamento fictício: DAR evidente (`FICT-`), valores fictícios, datas puras como a API manda. */
function lancamento(ordem: number, sobre: Partial<LancamentoFinanceiro> = {}): LancamentoFinanceiro {
  return {
    ordem,
    rotulo: `${ordem}ª taxa`,
    situacao: 'PAGA',
    vencida: false,
    vencidaPorData: false,
    inconsistente: false,
    emitidaEm: '2026-03-04',
    venceEm: '2026-03-14',
    pagaEm: '2026-03-07',
    numeroDar: 'FICT-0001',
    valor: 980,
    desconto: 0,
    upf: 28,
    ...sobre,
  };
}

function financeiro(sobre: Partial<FinanceiroDoRequerimento> = {}): FinanceiroDoRequerimento {
  return {
    idRequerimento: 9001,
    numeroRequerimento: '9001/2026',
    numeroProcesso: '9900/2026',
    estado: 'ATIVO',
    resumo: 'QUITADO',
    mostraValores: true,
    lancamentos: [lancamento(1)],
    isencoes: [],
    avisos: [],
    ...sobre,
  };
}

const TAXA_VENCIDA: AvisoFinanceiro = {
  codigo: 'TAXA_VENCIDA',
  texto:
    'Taxa vencida em 19/03/2026. O SIGA ainda não marca a taxa como vencida; o Rastro compara a data de vencimento com hoje.',
  gravidade: 'ATENCAO',
};

const FIN01 = financeiro();
const FIN02 = financeiro({
  resumo: 'EM_ABERTO',
  lancamentos: [lancamento(1, { situacao: 'AGUARDANDO_PAGAMENTO', pagaEm: null, venceEm: '2026-03-21', numeroDar: 'FICT-0002' })],
});
const FIN03 = financeiro({
  resumo: 'VENCIDO',
  lancamentos: [
    lancamento(1, {
      situacao: 'AGUARDANDO_PAGAMENTO',
      pagaEm: null,
      venceEm: '2026-03-19',
      vencida: true,
      vencidaPorData: true,
      numeroDar: 'FICT-0003',
    }),
  ],
  avisos: [TAXA_VENCIDA],
});
const FIN04 = financeiro({
  resumo: 'VENCIDO',
  lancamentos: [lancamento(1, { situacao: 'VENCIDA', pagaEm: null, vencida: true, numeroDar: 'FICT-0004' })],
});
const FIN05 = financeiro({
  resumo: 'ISENTO',
  lancamentos: [lancamento(1, { rotulo: null, situacao: 'ISENTA', numeroDar: null, venceEm: null, pagaEm: null, valor: null, desconto: null })],
  isencoes: [{ texto: 'Isento de Taxa', ordem: 1, data: '2026-03-04', dataAConfirmar: false }],
});
const FIN06 = financeiro({
  resumo: 'ISENTO',
  lancamentos: [lancamento(1, { rotulo: null, situacao: 'ISENTA', numeroDar: null, venceEm: null, pagaEm: null, valor: null, desconto: null })],
  isencoes: [{ texto: 'Isento de Taxa', ordem: 1, data: null, dataAConfirmar: true }],
});
const FIN11 = financeiro({
  resumo: 'EM_ABERTO',
  lancamentos: [
    lancamento(1, { numeroDar: 'FICT-0011' }),
    lancamento(2, { rotulo: 'Taxa da correção', situacao: 'AGUARDANDO_PAGAMENTO', pagaEm: null, emitidaEm: '2026-03-16', venceEm: '2026-03-30', numeroDar: 'FICT-0111' }),
  ],
});
const FIN13 = financeiro({ resumo: 'SEM_TAXA', lancamentos: [] });

describe('FinanceiroComponent (S9, bloco 8)', () => {
  let api: { financeiro: ReturnType<typeof vi.fn> };

  function criar(): { fixture: ComponentFixture<FinanceiroComponent>; raiz: HTMLElement } {
    TestBed.configureTestingModule({
      imports: [FinanceiroComponent],
      providers: [{ provide: DetalheApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(FinanceiroComponent);
    fixture.componentRef.setInput('idRequerimento', 9001);
    fixture.detectChanges();
    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  const texto = (raiz: HTMLElement) => raiz.textContent!.replace(/\s+/g, ' ');
  const linhas = (raiz: HTMLElement) => Array.from(raiz.querySelectorAll('tbody tr'));
  const celulas = (linha: Element) => Array.from(linha.querySelectorAll('th, td')).map((c) => c.textContent!.replace(/\s+/g, ' ').trim());
  const cabecalhos = (raiz: HTMLElement) => Array.from(raiz.querySelectorAll('thead th')).map((c) => c.textContent!.trim());

  function semChaveNemCpf(raiz: HTMLElement): void {
    const t = raiz.textContent!;
    expect(t).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}/);
    expect(t).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
    expect(t).not.toMatch(/chave|ged|idTecnico|titula|mensagem/i);
    expect(t).not.toMatch(/FALSO_SEM_DAR|RUIDO_ISENTO|ignorad/i);
  }

  beforeEach(() => {
    api = { financeiro: vi.fn() };
  });

  describe('lançamentos', () => {
    it('FIN01: taxa paga mostra emitida, vence, paga, DAR, valor e desconto em reais', () => {
      api.financeiro.mockReturnValue(of(FIN01));
      const { raiz } = criar();

      expect(texto(raiz)).toContain('Taxas e pagamentos');
      expect(texto(raiz)).toContain('Quitado');
      expect(cabecalhos(raiz)).toEqual(['Taxa', 'Situação', 'Emitida em', 'Vence em', 'Paga em', 'Nº do DAR', 'Valor', 'Desconto']);
      expect(linhas(raiz)).toHaveLength(1);
      expect(celulas(linhas(raiz)[0])).toEqual(['1ª taxa', 'Paga', '04/03/2026', '14/03/2026', '07/03/2026', 'FICT-0001', 'R$ 980,00', 'R$ 0,00']);
      semChaveNemCpf(raiz);
    });

    it('FIN02: aguardando pagamento no prazo, em aberto, sem aviso e sem data de pagamento', () => {
      api.financeiro.mockReturnValue(of(FIN02));
      const { raiz } = criar();

      expect(texto(raiz)).toContain('Em aberto');
      expect(celulas(linhas(raiz)[0]).slice(0, 5)).toEqual(['1ª taxa', 'Aguardando pagamento', '04/03/2026', '21/03/2026', '—']);
      expect(raiz.querySelector('.fin-avisos')).toBeNull();
      expect(texto(raiz)).not.toContain('Taxa vencida');
    });

    it('FIN03: vencida por data: a situação continua "Aguardando pagamento" e a marca e o aviso aparecem', () => {
      api.financeiro.mockReturnValue(of(FIN03));
      const { raiz } = criar();

      const linha = linhas(raiz)[0];
      expect(celulas(linha)[1]).toContain('Aguardando pagamento');
      expect(celulas(linha)[1]).toContain('Taxa vencida');
      expect(texto(raiz)).toContain('Vencido');
      const aviso = raiz.querySelector('.fin-avisos li')!;
      expect(aviso.textContent).toContain('Taxa vencida em 19/03/2026');
      expect(aviso.textContent).toContain('O SIGA ainda não marca a taxa como vencida');
      expect(raiz.querySelector('.fin-avisos li.atencao')).not.toBeNull();
    });

    it('FIN04: já marcada vencida pelo SIGA mostra "Vencida", sem a marca da data', () => {
      api.financeiro.mockReturnValue(of(FIN04));
      const { raiz } = criar();

      expect(celulas(linhas(raiz)[0])[1]).toBe('Vencida');
      expect(texto(raiz)).toContain('Vencido');
    });

    it('FIN11: a taxa inicial e a da correção, na ordem de emissão', () => {
      api.financeiro.mockReturnValue(of(FIN11));
      const { raiz } = criar();

      expect(linhas(raiz).map((l) => celulas(l)[0])).toEqual(['1ª taxa', 'Taxa da correção']);
      expect(celulas(linhas(raiz)[1])[1]).toBe('Aguardando pagamento');
    });

    it('valor ausente é "Não informado", nunca R$ 0,00', () => {
      api.financeiro.mockReturnValue(of(financeiro({ lancamentos: [lancamento(1, { valor: null, desconto: undefined })] })));
      const { raiz } = criar();

      const c = celulas(linhas(raiz)[0]);
      expect(c[6]).toBe('Não informado');
      expect(c[7]).toBe('Não informado');
    });

    it('lançamento inconsistente leva a nota "Dados incompletos"', () => {
      api.financeiro.mockReturnValue(of(financeiro({ lancamentos: [lancamento(1, { inconsistente: true, numeroDar: null })] })));
      const { raiz } = criar();

      expect(celulas(linhas(raiz)[0])[0]).toContain('Dados incompletos');
      expect(celulas(linhas(raiz)[0])[5]).toBe('—');
    });
  });

  describe('parâmetro de valores', () => {
    it('desligado, as colunas Valor e Desconto não existem e nenhum número em reais aparece; o DAR continua', () => {
      api.financeiro.mockReturnValue(of(financeiro({ mostraValores: false, lancamentos: [lancamento(1, { valor: undefined, desconto: undefined, upf: undefined })] })));
      const { raiz } = criar();

      expect(cabecalhos(raiz)).toEqual(['Taxa', 'Situação', 'Emitida em', 'Vence em', 'Paga em', 'Nº do DAR']);
      expect(texto(raiz)).not.toMatch(/R\$/);
      expect(texto(raiz)).not.toContain('Não informado');
      expect(texto(raiz)).toContain('FICT-0001');
    });
  });

  describe('isenção', () => {
    it('FIN05: "Isento de Taxa em dd/mm/aaaa", sem nota quando a data é do lançamento, e o selo "Isento de taxa"', () => {
      api.financeiro.mockReturnValue(of(FIN05));
      const { raiz } = criar();

      const isencao = raiz.querySelector('.fin-isencoes li')!.textContent!.replace(/\s+/g, ' ');
      expect(isencao).toContain('Isento de Taxa em 04/03/2026');
      expect(isencao).not.toContain('data a confirmar');
      expect(texto(raiz)).toContain('Isento de taxa');
      const c = celulas(linhas(raiz)[0]);
      expect(c[0]).toBe('Isenção');
      expect(c[1]).toBe('Isenta');
      expect(c[5]).toBe('—');
    });

    it('FIN06: sem data, "data não registrada"', () => {
      api.financeiro.mockReturnValue(of(FIN06));
      const { raiz } = criar();

      expect(raiz.querySelector('.fin-isencoes li')!.textContent).toContain('data não registrada');
    });

    it('data que não é a do lançamento leva a nota "data a confirmar"', () => {
      api.financeiro.mockReturnValue(of(financeiro({ resumo: 'ISENTO', isencoes: [{ texto: 'Isento de Taxa', ordem: 1, data: '2026-03-05', dataAConfirmar: true }] })));
      const { raiz } = criar();

      const isencao = raiz.querySelector('.fin-isencoes li')!.textContent!.replace(/\s+/g, ' ');
      expect(isencao).toContain('Isento de Taxa em 05/03/2026');
      expect(isencao).toContain('data a confirmar');
    });

    it('várias isenções aparecem em ordem', () => {
      api.financeiro.mockReturnValue(
        of(
          financeiro({
            resumo: 'ISENTO',
            isencoes: [
              { texto: 'Isento de Taxa', ordem: 1, data: '2026-03-02', dataAConfirmar: false },
              { texto: 'Isento de Taxa', ordem: 2, data: '2026-03-16', dataAConfirmar: false },
            ],
          }),
        ),
      );
      const { raiz } = criar();

      expect(Array.from(raiz.querySelectorAll('.fin-isencoes li')).map((li) => li.textContent!.replace(/\s+/g, ' '))).toEqual([
        expect.stringContaining('02/03/2026'),
        expect.stringContaining('16/03/2026'),
      ]);
    });
  });

  describe('resumo', () => {
    it('cada resumo tem selo com texto e ícone', () => {
      const casos: [FinanceiroDoRequerimento['resumo'], string][] = [
        ['SEM_TAXA', 'Sem taxa'],
        ['ISENTO', 'Isento de taxa'],
        ['EM_ABERTO', 'Em aberto'],
        ['QUITADO', 'Quitado'],
        ['VENCIDO', 'Vencido'],
      ];
      for (const [resumo, rotulo] of casos) {
        TestBed.resetTestingModule();
        api.financeiro.mockReturnValue(of(financeiro({ resumo, lancamentos: [] })));
        const { raiz } = criar();
        const selo = raiz.querySelector('.fin-resumo .tag')!;
        expect(selo.textContent!.trim()).toBe(rotulo);
        expect(selo.querySelector('svg')).not.toBeNull();
      }
    });

    it('FIN13: sem taxa mostra o selo e uma frase, sem tabela', () => {
      api.financeiro.mockReturnValue(of(FIN13));
      const { raiz } = criar();

      expect(texto(raiz)).toContain('Sem taxa');
      expect(texto(raiz)).toContain('Este requerimento não tem taxa registrada.');
      expect(raiz.querySelector('table')).toBeNull();
    });
  });

  describe('estados', () => {
    it('carregando mostra o esqueleto e avisa o leitor de tela', () => {
      api.financeiro.mockReturnValue(NEVER);
      const { raiz } = criar();

      expect(raiz.querySelector('section')!.getAttribute('aria-busy')).toBe('true');
      expect(texto(raiz)).toContain('Carregando as taxas do requerimento');
    });

    it('INDISPONIVEL: aviso neutro, sem selo, "Tentar novamente" e o resto da tela segue', () => {
      api.financeiro.mockReturnValue(of(financeiro({ estado: 'INDISPONIVEL', resumo: null, lancamentos: [] })));
      const { fixture, raiz } = criar();

      expect(texto(raiz)).toContain('Não foi possível consultar as taxas agora. O restante da página não foi afetado.');
      expect(raiz.querySelector('.fin-resumo')).toBeNull();

      api.financeiro.mockReturnValue(of(FIN01));
      raiz.querySelector<HTMLButtonElement>('.fin-aviso-geral button')!.click();
      fixture.detectChanges();
      expect(linhas(raiz)).toHaveLength(1);
    });

    it('erro HTTP mostra a mensagem e tenta de novo', () => {
      api.financeiro.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 403, error: { errorCode: 'NAO_INTERESSADO' } })));
      const { fixture, raiz } = criar();

      expect(raiz.querySelector('[role="alert"]')).not.toBeNull();
      api.financeiro.mockReturnValue(of(FIN01));
      raiz.querySelector<HTMLButtonElement>('.fin-aviso-geral button')!.click();
      fixture.detectChanges();

      expect(linhas(raiz)).toHaveLength(1);
      expect(raiz.querySelector('[role="alert"]')).toBeNull();
    });

    it('recolher e expandir pelo botão, com aria-expanded', () => {
      api.financeiro.mockReturnValue(of(FIN01));
      const { fixture, raiz } = criar();
      const botao = raiz.querySelector<HTMLButtonElement>('.fin-head button')!;

      expect(botao.getAttribute('aria-expanded')).toBe('true');
      botao.click();
      fixture.detectChanges();
      expect(botao.getAttribute('aria-expanded')).toBe('false');
      expect(raiz.querySelector('table')).toBeNull();
      botao.click();
      fixture.detectChanges();
      expect(raiz.querySelector('table')).not.toBeNull();
    });
  });

  describe('acessibilidade e vazamento', () => {
    it('tabela semântica: legenda, cabeçalhos de coluna e a taxa como cabeçalho da linha', () => {
      api.financeiro.mockReturnValue(of(FIN11));
      const { raiz } = criar();

      expect(raiz.querySelector('table caption')).not.toBeNull();
      expect(raiz.querySelectorAll('thead th[scope="col"]').length).toBe(8);
      expect(raiz.querySelectorAll('tbody th[scope="row"]').length).toBe(2);
      // Em tela estreita cada célula se identifica pelo rótulo (cartões).
      expect(raiz.querySelector('tbody td')!.getAttribute('data-label')).toBe('Situação');
    });

    it('nenhum CPF, chave, id técnico nem diagnóstico, mesmo se a API mandasse campos a mais', () => {
      const comExtras = {
        ...FIN03,
        ignoradosPorMotivo: { FALSO_SEM_DAR: 1 },
        lancamentos: [{ ...FIN03.lancamentos[0], idTecnico: 9003, statusProcessado: 'N', inconsistencias: ['SEM_DAR'] }],
      } as unknown as FinanceiroDoRequerimento;
      api.financeiro.mockReturnValue(of(comExtras));
      const { raiz } = criar();

      semChaveNemCpf(raiz);
      expect(texto(raiz)).not.toContain('9003');
    });
  });
});
