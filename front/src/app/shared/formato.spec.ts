import { formatarCpfCnpj, formatarData, formatarDataHora, mascararCpfCnpj, mascararNumero, tomDaSituacao } from './formato';

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
