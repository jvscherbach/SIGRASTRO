import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { DetalheApiService } from '../detalhe/detalhe-api.service';
import { FinanceiroDoRequerimento } from '../detalhe/financeiro.models';
import { DiagnosticoFinanceiroComponent } from './diagnostico-financeiro.component';
import { diagnosticoDe } from './diagnostico-financeiro.models';

/** Resposta do financeiro para o Administrador (S9, bloco 6), com os campos de diagnóstico. */
function resposta(sobre: object = {}): FinanceiroDoRequerimento {
  return {
    idRequerimento: 9007,
    numeroRequerimento: '9007/2026',
    numeroProcesso: '9900/2026',
    estado: 'ATIVO',
    resumo: 'ISENTO',
    mostraValores: true,
    lancamentos: [
      { ordem: 1, rotulo: '1ª taxa', situacao: 'PAGA', vencida: false, vencidaPorData: false, inconsistente: false, emitidaEm: '2026-03-04', venceEm: null, pagaEm: null, numeroDar: 'FICT-0001', valor: 980 },
      { ordem: 2, rotulo: null, situacao: 'ISENTA', vencida: false, vencidaPorData: false, inconsistente: true, emitidaEm: null, venceEm: null, pagaEm: null, numeroDar: null },
    ],
    isencoes: [
      { texto: 'Isento de Taxa', ordem: 1, data: '2026-03-05', dataAConfirmar: true, origemDaData: 'FLUXO', naoProcessado: true },
      { texto: 'Isento de Taxa', ordem: 2, data: null, dataAConfirmar: true, origemDaData: 'AUSENTE', naoProcessado: false },
    ],
    avisos: [],
    ignoradosPorMotivo: { FALSO_SEM_DAR: 10, RUIDO_ISENTO_AGUARDANDO: 0 },
    ...sobre,
  } as unknown as FinanceiroDoRequerimento;
}

describe('diagnosticoDe', () => {
  it('conta mantidos, inconsistentes e ignorados por motivo, e traduz a origem da data', () => {
    const d = diagnosticoDe(resposta())!;

    expect(d.numeroRequerimento).toBe('9007/2026');
    expect(d.validos).toBe(2);
    expect(d.inconsistentes).toBe(1);
    expect(d.ignorados).toEqual([
      { motivo: 'FALSO_SEM_DAR', texto: expect.stringContaining('Cancelado sem número de DAR'), quantos: 10 },
      { motivo: 'RUIDO_ISENTO_AGUARDANDO', texto: expect.stringContaining('marcado como isento'), quantos: 0 },
    ]);
    expect(d.isencoes.map((i) => i.texto)).toEqual([
      expect.stringContaining('"Isenção confirmada" da tramitação'),
      expect.stringContaining('data não registrada'),
    ]);
    expect(d.isencoes[0].naoProcessado).toBe(true);
  });

  it('motivo novo aparece com o código, sem esconder a contagem; sem diagnóstico na resposta, nulo', () => {
    expect(diagnosticoDe(resposta({ ignoradosPorMotivo: { MOTIVO_NOVO: 3 } }))!.ignorados).toEqual([{ motivo: 'MOTIVO_NOVO', texto: 'MOTIVO_NOVO', quantos: 3 }]);
    expect(diagnosticoDe(resposta({ ignoradosPorMotivo: undefined }))!.ignorados).toBeNull();
  });

  it('INDISPONIVEL não tem diagnóstico', () => {
    expect(diagnosticoDe(resposta({ estado: 'INDISPONIVEL' }))).toBeNull();
  });
});

describe('DiagnosticoFinanceiroComponent', () => {
  let api: { financeiro: ReturnType<typeof vi.fn> };

  function criar(): { fixture: ReturnType<typeof TestBed.createComponent<DiagnosticoFinanceiroComponent>>; raiz: HTMLElement } {
    TestBed.configureTestingModule({
      imports: [DiagnosticoFinanceiroComponent],
      providers: [{ provide: DetalheApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(DiagnosticoFinanceiroComponent);
    fixture.detectChanges();
    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  const texto = (raiz: HTMLElement) => raiz.textContent!.replace(/\s+/g, ' ');

  function consultar(fixture: ReturnType<typeof criar>['fixture'], raiz: HTMLElement, id: string): void {
    const campo = raiz.querySelector<HTMLInputElement>('#diag-fin-id')!;
    campo.value = id;
    campo.dispatchEvent(new Event('input'));
    raiz.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    api = { financeiro: vi.fn() };
  });

  it('consulta o requerimento e mostra só contagens e a origem da data, sem valor, DAR nem id de lançamento', () => {
    api.financeiro.mockReturnValue(of(resposta()));
    const { fixture, raiz } = criar();

    consultar(fixture, raiz, '9007');

    expect(api.financeiro).toHaveBeenCalledWith(9007);
    const t = texto(raiz);
    expect(t).toContain('Requerimento 9007/2026');
    expect(t).toContain('Cancelado sem número de DAR');
    expect(t).toContain('1ª isenção: data da passagem "Isenção confirmada" da tramitação (a confirmar)');
    expect(t).toContain('não processado');
    expect(t).not.toMatch(/FICT-|R\$|980/);
    expect(t).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}|(?<!\d)\d{11}(?!\d)/);
  });

  it('identificador vazio ou inválido não consulta', () => {
    const { fixture, raiz } = criar();

    consultar(fixture, raiz, '');
    consultar(fixture, raiz, '0');

    expect(api.financeiro).not.toHaveBeenCalled();
    expect(raiz.querySelector('[role="alert"]')!.textContent).toContain('Informe o identificador do requerimento');
  });

  it('só dígitos entram no campo', () => {
    api.financeiro.mockReturnValue(of(resposta()));
    const { fixture, raiz } = criar();

    consultar(fixture, raiz, 'a9b0c0d7');

    expect(api.financeiro).toHaveBeenCalledWith(9007);
  });

  it('erro HTTP mostra a mensagem do erro, nunca o detalhe técnico', () => {
    api.financeiro.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, error: { errorCode: 'NAO_ENCONTRADO', detail: 'detalhe técnico' } })));
    const { fixture, raiz } = criar();

    consultar(fixture, raiz, '1234');

    expect(raiz.querySelector('[role="alert"]')).not.toBeNull();
    expect(texto(raiz)).not.toContain('detalhe técnico');
    expect(raiz.querySelector('.diag-resultado')).toBeNull();
  });

  it('financeiro INDISPONIVEL mostra aviso neutro e nenhum resultado', () => {
    api.financeiro.mockReturnValue(of(resposta({ estado: 'INDISPONIVEL', resumo: null, lancamentos: [], isencoes: [] })));
    const { fixture, raiz } = criar();

    consultar(fixture, raiz, '9500');

    expect(raiz.querySelector('[role="alert"]')!.textContent).toContain('Não foi possível consultar o financeiro agora');
    expect(raiz.querySelector('.diag-resultado')).toBeNull();
  });

  it('resposta sem o diagnóstico (perfil sem permissão) avisa em vez de mostrar zero', () => {
    api.financeiro.mockReturnValue(of(resposta({ ignoradosPorMotivo: undefined })));
    const { fixture, raiz } = criar();

    consultar(fixture, raiz, '9007');

    expect(texto(raiz)).toContain('A resposta não trouxe o diagnóstico (só o Administrador o recebe).');
  });
});
