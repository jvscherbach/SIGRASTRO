import {
  formatarCpfCnpj,
  formatarData,
  formatarDataHora,
  mascararCpfCnpj,
  mascararNumero,
  minutosAte,
  tomDaSituacao,
} from './formato';

describe('mascararNumero', () => {
  it('monta N/AAAA enquanto digita', () => {
    expect(mascararNumero('')).toBe('');
    expect(mascararNumero('3331')).toBe('3331');
    expect(mascararNumero('33312')).toBe('3/3312');
    expect(mascararNumero('33312025')).toBe('3331/2025');
    expect(mascararNumero('3331/2025')).toBe('3331/2025');
  });

  it('descarta letras e limita a 6 dígitos de número mais 4 do ano', () => {
    expect(mascararNumero('ab12cd/20x25')).toBe('12/2025');
    expect(mascararNumero('12345678901234')).toBe('123456/7890');
  });
});

describe('mascararCpfCnpj', () => {
  it('formata CPF até 11 dígitos', () => {
    expect(mascararCpfCnpj('123')).toBe('123');
    expect(mascararCpfCnpj('1234')).toBe('123.4');
    expect(mascararCpfCnpj('1234567')).toBe('123.456.7');
    expect(mascararCpfCnpj('12345678909')).toBe('123.456.789-09');
  });

  it('formata CNPJ acima de 11 dígitos e limita a 14', () => {
    expect(mascararCpfCnpj('123456789012')).toBe('12.345.678/9012');
    expect(mascararCpfCnpj('12345678000190')).toBe('12.345.678/0001-90');
    expect(mascararCpfCnpj('12345678000190999')).toBe('12.345.678/0001-90');
  });

  it('aceita o valor já mascarado', () => {
    expect(mascararCpfCnpj('12.345.678/0001-90')).toBe('12.345.678/0001-90');
    expect(mascararCpfCnpj('123.456.789-09')).toBe('123.456.789-09');
  });
});

describe('formatarCpfCnpj', () => {
  it('mascara valores com 11 ou 14 dígitos e preserva o resto', () => {
    expect(formatarCpfCnpj('45678901000123')).toBe('45.678.901/0001-23');
    expect(formatarCpfCnpj('12345678909')).toBe('123.456.789-09');
    expect(formatarCpfCnpj('123')).toBe('123');
    expect(formatarCpfCnpj(null)).toBe('');
  });
});

describe('datas', () => {
  it('formata em pt-BR e usa traço quando não há data', () => {
    expect(formatarData('2025-03-31T10:12:00')).toBe('31/03/2025');
    expect(formatarDataHora('2025-04-29T09:14:00')).toBe('29/04/2025 09:14');
    expect(formatarData(null)).toBe('—');
    expect(formatarData('')).toBe('—');
    expect(formatarDataHora('lixo')).toBe('—');
  });

  it('horários de borda não mudam de dia, qualquer que seja o fuso do navegador', () => {
    expect(formatarData('2025-04-01T23:30:00')).toBe('01/04/2025');
    expect(formatarData('2025-04-01T23:30')).toBe('01/04/2025');
    expect(formatarData('2025-04-02T00:30:00')).toBe('02/04/2025');
    expect(formatarDataHora('2025-04-01T23:30:00')).toBe('01/04/2025 23:30');
    expect(formatarDataHora('2025-04-02T00:30')).toBe('02/04/2025 00:30');
  });
});

/**
 * S5.1: a interface mostra sempre o horário de Cuiabá (UTC-4, sem horário de verão), seja qual for o fuso do navegador ou
 * do runner. Estes testes valem igual com TZ=UTC, TZ=America/Cuiaba e TZ=Asia/Tokyo (o CI roda os três).
 */
describe('fuso de Cuiabá', () => {
  it('instante com fuso (Z ou deslocamento) é convertido para Cuiabá, não para o fuso da máquina', () => {
    expect(formatarDataHora('2026-10-21T14:30:00Z')).toBe('21/10/2026 10:30');
    expect(formatarDataHora('2026-10-21T10:30:00-04:00')).toBe('21/10/2026 10:30');
    expect(formatarDataHora('2026-10-21T10:30:00.123456789-04:00')).toBe('21/10/2026 10:30');
    expect(formatarDataHora('2026-10-21T15:30:00+01:00')).toBe('21/10/2026 10:30');
  });

  it('instante que cruza a meia-noite em UTC volta para o dia anterior em Cuiabá', () => {
    expect(formatarDataHora('2026-10-22T02:00:00Z')).toBe('21/10/2026 22:00');
    expect(formatarData('2026-10-22T02:00:00Z')).toBe('21/10/2026');
    expect(formatarDataHora('2026-01-01T03:59:00Z')).toBe('31/12/2025 23:59');
  });

  it('data pura e data e hora sem fuso já são de Cuiabá e nunca mudam de dia', () => {
    expect(formatarData('2022-04-20')).toBe('20/04/2022');
    expect(formatarData('2022-04-20T00:00:00')).toBe('20/04/2022');
    expect(formatarDataHora('2022-04-20')).toBe('20/04/2022 00:00');
    expect(formatarData('2022-12-31T23:59:59')).toBe('31/12/2022');
    expect(formatarData('2022-01-01T00:00:00.000')).toBe('01/01/2022');
  });

  describe('minutos restantes', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-10-21T15:00:00Z')); // 11:00 em Cuiabá
    });

    afterEach(() => vi.useRealTimers());

    it('data sem fuso é o relógio de Cuiabá; data com fuso é um instante', () => {
      expect(minutosAte('2026-10-21T11:30:00')).toBe(30);
      expect(minutosAte('2026-10-21T15:30:00Z')).toBe(30);
      expect(minutosAte('2026-10-21T11:30:00-04:00')).toBe(30);
      expect(minutosAte('2026-10-21T10:59:00-04:00')).toBe(0);
    });

    it('passa de meia-noite sem erro', () => {
      expect(minutosAte('2026-10-22T00:00:00')).toBe(780);
    });
  });
});

describe('tomDaSituacao', () => {
  it('mapeia o código da situação para a cor da tag', () => {
    expect(tomDaSituacao('EM_ANALISE')).toBe('lime');
    expect(tomDaSituacao('CONCLUIDO')).toBe('dark');
    expect(tomDaSituacao('CANCELADO_POR_FALTA_DE_PAGAMENTO')).toBe('red');
    expect(tomDaSituacao('AGUARDANDO_PAGAMENTO_DE_TAXAS')).toBe('amber');
    expect(tomDaSituacao('EM_CADASTRO')).toBe('gray');
    expect(tomDaSituacao('SITUACAO_NOVA')).toBe('gray');
  });
});
