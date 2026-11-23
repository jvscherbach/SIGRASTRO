import { describe, expect, it } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';

/**
 * R2, bloco 14: cor só por token. Nenhum hex nem rgb()/rgba() em CSS ou componente fora das exceções do padrão:
 * - tokens.css (onde as cores são definidas);
 * - login.css (fundo #12302B e textos #F1F4EA, exceção do prompt da R2);
 * - impressao.css e o bloco de impressão do fluxograma (impressão em preto e branco, S8/S9);
 * - sombras (`box-shadow`): preto ou --ink translúcido, sem papel de cor de texto ou de fundo;
 * - SVGs de marca e paisagem (não são lidos aqui: só .css, .ts e .html).
 */
const HEX = /(?<!&)#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})(?![-\w])/gi;
const RGB = /rgba?\(/gi;

const ARQUIVOS_EXCETUADOS = ['tokens.css', 'login.css', 'impressao.css'];

/** Trechos exatos permitidos fora dos arquivos excetuados, com o motivo. */
const TRECHOS_PERMITIDOS: { arquivo: string; trecho: string; motivo: string }[] = [
  { arquivo: 'fluxograma-simplificado.component.ts', trecho: '<circle cx="14" cy="14" r="12" fill="#000" />', motivo: 'marcador final em preto (regra de impressão P&B, S9)' },
  { arquivo: 'fluxograma-simplificado.component.ts', trecho: '.caixa { break-inside: avoid; background: #fff !important; }', motivo: '@media print' },
  { arquivo: 'fluxograma-simplificado.component.ts', trecho: '.caixa-vencida { border-color: #000; color: #000; border-style: double; border-width: 4px; }', motivo: '@media print' },
  { arquivo: 'fluxograma-simplificado.component.ts', trecho: '.bola circle { fill: #000; }', motivo: '@media print' },
];

function arquivos(pasta: string, extensoes: string[]): string[] {
  return readdirSync(pasta, { recursive: true, encoding: 'utf8' })
    .map((r) => `${pasta}/${r.replace(/\\/g, '/')}`)
    .filter((c) => extensoes.some((e) => c.endsWith(e)) && !c.endsWith('.spec.ts') && !c.endsWith('.d.ts'));
}

function ocorrencias(caminho: string): string[] {
  const nome = caminho.split('/').pop()!;
  if (ARQUIVOS_EXCETUADOS.includes(nome)) return [];
  let texto = readFileSync(caminho, 'utf8');
  for (const p of TRECHOS_PERMITIDOS.filter((t) => t.arquivo === nome)) texto = texto.split(p.trecho).join('');
  texto = texto.replace(/box-shadow\s*:[^;}]*/g, '');
  const achados: string[] = [];
  texto.split('\n').forEach((linha, i) => {
    if ((linha.match(HEX) ?? []).length || (linha.match(RGB) ?? []).length) achados.push(`${caminho}:${i + 1}: ${linha.trim().slice(0, 120)}`);
  });
  return achados;
}

describe('cor só por token', () => {
  it('nenhum CSS global usa cor fixa fora das exceções', () => {
    const lista = arquivos('src/styles', ['.css']);
    expect(lista.length).toBeGreaterThan(5);
    expect(lista.flatMap(ocorrencias)).toEqual([]);
  });

  it('nenhum componente (.ts e .html) usa cor fixa fora das exceções', () => {
    const lista = arquivos('src/app', ['.ts', '.html', '.css']);
    expect(lista.length).toBeGreaterThan(20);
    expect(lista.flatMap(ocorrencias)).toEqual([]);
  });

  it('os trechos permitidos ainda existem (exceção sem uso deve sair da lista)', () => {
    for (const p of TRECHOS_PERMITIDOS) {
      const caminho = arquivos('src/app', ['.ts']).find((c) => c.endsWith(`/${p.arquivo}`))!;
      expect(readFileSync(caminho, 'utf8'), p.motivo).toContain(p.trecho);
    }
  });

  it('o detector reconhece hex e rgba e ignora entidade HTML e seletor de id', () => {
    expect('color:#fff'.match(HEX)).toHaveLength(1);
    expect('background:#12302B;'.match(HEX)).toHaveLength(1);
    expect('&#10003; #aba-dossie #caixa'.match(HEX)).toBeNull();
    expect('rgba(0,0,0,.5)'.match(RGB)).toHaveLength(1);
  });
});
