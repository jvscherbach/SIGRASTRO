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

/** Toda data e hora da interface é mostrada no horário de Cuiabá, seja qual for o fuso do navegador (docs/09, docs/11). */
export const FUSO_DE_CUIABA = 'America/Cuiaba';

const DATA = new Intl.DateTimeFormat('pt-BR', {
  timeZone: FUSO_DE_CUIABA,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});
const DATA_HORA = new Intl.DateTimeFormat('pt-BR', {
  timeZone: FUSO_DE_CUIABA,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** Data e hora sem fuso, como a API entrega (relógio de Cuiabá): "2025-04-01T23:30" ou com segundos. */
const DATA_HORA_SEM_FUSO = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?)?$/;

/**
 * A API entrega data e hora de duas formas. **Sem fuso** ("2025-04-01T23:30", ou só a data): já é o relógio de Cuiabá e
 * o texto é lido campo a campo, sem converter, então uma data pura nunca muda de dia e um documento das 23:30 aparece no
 * mesmo dia em qualquer fuso (S2.2). **Com fuso** ("...-04:00" ou "...Z", como os eventos da auditoria e o último
 * acesso): o instante é convertido para Cuiabá, e não para o fuso do navegador (S5.1).
 */
export function formatarData(iso: string | null | undefined): string {
  const m = iso ? DATA_HORA_SEM_FUSO.exec(iso) : null;
  if (m) {
    return `${m[3]}/${m[2]}/${m[1]}`;
  }
  const data = iso ? new Date(iso) : null;
  return data && !Number.isNaN(data.getTime()) ? DATA.format(data) : '—';
}

export function formatarDataHora(iso: string | null | undefined): string {
  const m = iso ? DATA_HORA_SEM_FUSO.exec(iso) : null;
  if (m) {
    return `${m[3]}/${m[2]}/${m[1]} ${m[4] ?? '00'}:${m[5] ?? '00'}`;
  }
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
 * Relógio de parede de Cuiabá como Date "local" (só para exibir ou comparar com outro relógio de parede; para cálculo de
 * intervalos use {@link minutosAte}). A API manda datas sem fuso (horário de Cuiabá), e o navegador as
 * lê como relógio local; para calcular "quanto falta" as duas pontas precisam estar no mesmo relógio, qualquer que
 * seja o fuso do navegador.
 */
export function agoraEmCuiaba(): Date {
  return new Date(new Date().toLocaleString('en-US', { timeZone: 'America/Cuiaba' }));
}

/** Milissegundos de um relógio de parede, lido como se fosse UTC: dois relógios de parede se subtraem sem fuso nem horário de verão. */
function paredeEmMs(d: Date): number {
  return Date.UTC(d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds());
}

/**
 * Minutos até a data da API; nunca negativo. Data sem fuso é relógio de Cuiabá e se compara com o relógio de parede de
 * Cuiabá; data com fuso é um instante e se compara com agora. Em nenhum caso entra o fuso do navegador.
 */
export function minutosAte(iso: string | null | undefined): number | null {
  const m = iso ? DATA_HORA_SEM_FUSO.exec(iso) : null;
  let restanteMs: number;
  if (m) {
    const alvo = Date.UTC(+m[1], +m[2] - 1, +m[3], +(m[4] ?? 0), +(m[5] ?? 0), +(m[6] ?? 0));
    restanteMs = alvo - paredeEmMs(agoraEmCuiaba());
  } else {
    const alvo = iso ? new Date(iso) : null;
    if (!alvo || Number.isNaN(alvo.getTime())) {
      return null;
    }
    restanteMs = alvo.getTime() - Date.now();
  }
  return Math.max(0, Math.ceil(restanteMs / 60000));
}

/** "340 KB", "1,2 MB"; vazio quando o tamanho é desconhecido. */
export function formatarTamanho(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || bytes < 0) {
    return '';
  }
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`;
}
