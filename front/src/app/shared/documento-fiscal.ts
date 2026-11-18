/** Validação de CPF e CNPJ pelos dígitos verificadores (só dígitos; o back-end repete a conferência). */

function todosIguais(digitos: string): boolean {
  return /^(\d)\1+$/.test(digitos);
}

function digito(digitos: string, tamanho: number, pesos: number[]): number {
  let soma = 0;
  for (let i = 0; i < tamanho; i++) {
    soma += Number(digitos[i]) * pesos[i];
  }
  const resto = soma % 11;
  return resto < 2 ? 0 : 11 - resto;
}

export function cpfValido(digitos: string): boolean {
  if (!/^\d{11}$/.test(digitos) || todosIguais(digitos)) {
    return false;
  }
  const d1 = digito(digitos, 9, [10, 9, 8, 7, 6, 5, 4, 3, 2]);
  const d2 = digito(digitos, 10, [11, 10, 9, 8, 7, 6, 5, 4, 3, 2]);
  return d1 === Number(digitos[9]) && d2 === Number(digitos[10]);
}

export function cnpjValido(digitos: string): boolean {
  if (!/^\d{14}$/.test(digitos) || todosIguais(digitos)) {
    return false;
  }
  const d1 = digito(digitos, 12, [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
  const d2 = digito(digitos, 13, [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
  return d1 === Number(digitos[12]) && d2 === Number(digitos[13]);
}

/** CPF (11 dígitos) ou CNPJ (14 dígitos) com dígitos verificadores corretos. */
export function cpfCnpjValido(texto: string): boolean {
  const digitos = texto.replace(/\D/g, '');
  return digitos.length === 11 ? cpfValido(digitos) : digitos.length === 14 ? cnpjValido(digitos) : false;
}
