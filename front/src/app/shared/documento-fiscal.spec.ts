import { cnpjValido, cpfCnpjValido, cpfValido } from './documento-fiscal';

/** Números de teste de gerador (sem pessoa real): só servem para provar os dígitos verificadores. */
describe('documento fiscal', () => {
  it('CPF com dígitos verificadores corretos', () => {
    expect(cpfValido('11144477735')).toBe(true);
    expect(cpfCnpjValido('111.444.777-35')).toBe(true);
  });

  it('CPF com dígito errado, repetido ou de tamanho errado é inválido', () => {
    expect(cpfValido('11144477736')).toBe(false);
    expect(cpfValido('11111111111')).toBe(false);
    expect(cpfValido('1114447773')).toBe(false);
    expect(cpfCnpjValido('123')).toBe(false);
  });

  it('CNPJ com dígitos verificadores corretos e inválidos', () => {
    expect(cnpjValido('11222333000181')).toBe(true);
    expect(cpfCnpjValido('11.222.333/0001-81')).toBe(true);
    expect(cnpjValido('11222333000182')).toBe(false);
    expect(cnpjValido('00000000000000')).toBe(false);
  });
});
