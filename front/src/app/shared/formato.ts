/** Máscaras de digitação e formatação de exibição (pt-BR). */

/** Número no formato N/AAAA: até 6 dígitos, barra e ano de 4 dígitos. */
export function mascararNumero(texto: string): string {
  const digitos = texto.replace(/\D/g, '').slice(0, 10);
  return digitos.length > 4 ? `${digitos.slice(0, -4)}/${digitos.slice(-4)}` : digitos;
}

export function somenteDigitos(texto: string | null | undefined): string {
  return (texto ?? '').replace(/\D/g, '');
}

/** CPF (000.000.000-00) até 11 dígitos; acima disso, CNPJ (00.000.000/0000-00). */
export function mascararCpfCnpj(texto: string): string {
  const d = somenteDigitos(texto).slice(0, 14);
  if (d.length <= 11) {
    return d
      .replace(/^(\d{3})(\d)/, '$1.$2')
      .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
      .replace(/^(\d{3})\.(\d{3})\.(\d{3})(\d{1,2})$/, '$1.$2.$3-$4');
  }
  return d
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/^(\d{2})\.(\d{3})\.(\d{3})(\d)/, '$1.$2.$3/$4')
    .replace(/^(\d{2})\.(\d{3})\.(\d{3})\/(\d{4})(\d{1,2})$/, '$1.$2.$3/$4-$5');
}

/** Exibe CPF/CNPJ com máscara quando tem 11 ou 14 dígitos; qualquer outro valor segue como veio. */
export function formatarCpfCnpj(valor: string | null | undefined): string {
  const d = somenteDigitos(valor);
  return d.length === 11 || d.length === 14 ? mascararCpfCnpj(d) : (valor ?? '');
}

const DATA = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' });
const DATA_HORA = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** A API entrega data e hora sem fuso (horário de Cuiabá); o navegador mostra o mesmo relógio. */
export function formatarData(iso: string | null | undefined): string {
  const data = iso ? new Date(iso) : null;
  return data && !Number.isNaN(data.getTime()) ? DATA.format(data) : '—';
}

export function formatarDataHora(iso: string | null | undefined): string {
  const data = iso ? new Date(iso) : null;
  return data && !Number.isNaN(data.getTime()) ? DATA_HORA.format(data).replace(',', '') : '—';
}

/** Cor da tag por situação (o texto vem da API). */
export function tomDaSituacao(codigo: string): 'lime' | 'gray' | 'amber' | 'red' | 'dark' | 'blue' {
  if (codigo === 'EM_ANALISE' || codigo === 'EM_REVISAO' || codigo === 'EM_CORRECAO') return 'lime';
  if (codigo === 'CONCLUIDO') return 'dark';
  if (codigo.startsWith('CANCELADO') || codigo === 'CHECKLIST_REPROVADO') return 'red';
  if (codigo.startsWith('AGUARDANDO') || codigo === 'ENCAMINHADO_PARA_ARRECADACAO') return 'amber';
  return 'gray';
}

/**
 * Relógio de parede de Cuiabá como Date "local". A API manda datas sem fuso (horário de Cuiabá), e o navegador as
 * lê como relógio local; para calcular "quanto falta" as duas pontas precisam estar no mesmo relógio, qualquer que
 * seja o fuso do navegador.
 */
export function agoraEmCuiaba(): Date {
  return new Date(new Date().toLocaleString('en-US', { timeZone: 'America/Cuiaba' }));
}

/** Minutos até a data da API (relógio de Cuiabá); nunca negativo. */
export function minutosAte(iso: string | null | undefined): number | null {
  const alvo = iso ? new Date(iso) : null;
  if (!alvo || Number.isNaN(alvo.getTime())) {
    return null;
  }
  return Math.max(0, Math.ceil((alvo.getTime() - agoraEmCuiaba().getTime()) / 60000));
}
