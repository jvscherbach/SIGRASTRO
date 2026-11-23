import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

/**
 * R2, bloco 14: contraste WCAG 2.x dos pares do padrão, calculado a partir dos valores reais de tokens.css.
 * Texto: 4,5:1 (AA). Elemento de interface (sublinhado ativo, anel de foco): 3:1.
 */
const css = readFileSync('src/styles/tokens.css', 'utf8');

type Rgb = [number, number, number];

function token(nome: string): string {
  const m = new RegExp(`${nome}\\s*:\\s*([^;]+);`).exec(css);
  if (!m) throw new Error(`token ${nome} ausente`);
  return m[1].trim();
}

/** Aceita `#RGB`, `#RRGGBB`, `var(--x)` ou `rgba(r,g,b,a)` (este composto sobre `fundo`). */
function cor(valor: string, fundo?: Rgb): Rgb {
  const v = valor.startsWith('--') ? token(valor) : valor;
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(v);
  if (hex) {
    const h = hex[1].length === 3 ? [...hex[1]].map((c) => c + c).join('') : hex[1];
    return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as Rgb;
  }
  const rgba = /^rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)$/.exec(v);
  if (rgba) {
    if (!fundo) throw new Error(`${valor} é translúcida: informe o fundo`);
    const a = Number(rgba[4]);
    return [1, 2, 3].map((i, k) => Math.round(Number(rgba[i]) * a + fundo[k] * (1 - a))) as Rgb;
  }
  throw new Error(`cor não reconhecida: ${valor}`);
}

function luminancia([r, g, b]: Rgb): number {
  const canal = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * canal(r) + 0.7152 * canal(g) + 0.0722 * canal(b);
}

function razao(a: Rgb, b: Rgb): number {
  const [x, y] = [luminancia(a), luminancia(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}

const TEXTO = 4.5;
const INTERFACE = 3;

/** [frente, fundo, mínimo, uso] */
const PARES: [string, string, number, string][] = [
  ['--ink', '--paper', TEXTO, 'texto sobre o fundo da página'],
  ['--ink', '--surface', TEXTO, 'texto sobre painel'],
  ['--slate', '--paper', TEXTO, 'texto secundário sobre a página'],
  ['--slate', '--surface', TEXTO, 'texto secundário sobre painel'],
  ['#FFFFFF', '--trail', TEXTO, 'botão principal'],
  ['--trail-deep', '--trail-tint', TEXTO, 'tag padrão e botão secundário em foco'],
  ['--ok', '--ok-tint', TEXTO, 'tag ok'],
  ['--warn', '--marker-tint', TEXTO, 'tag de alerta'],
  ['--bad', '--bad-tint', TEXTO, 'tag de erro'],
  ['#F1F4EA', '#12302B', TEXTO, 'marca e frase do login'],
  ['--marker', '--ink', INTERFACE, 'sublinhado do item ativo e anel de foco na barra'],
  // pares extras usados nas telas
  ['--on-ink', '--ink', TEXTO, 'texto da barra superior'],
  ['--on-ink', '--warn', TEXTO, 'indicador do dossiê: decisão'],
  ['--on-ink', '--bad', TEXTO, 'indicador do dossiê: erro e toast de erro'],
  ['--on-ink', '--ok', TEXTO, 'indicador do dossiê: pronto'],
  ['--warn', '--surface', TEXTO, 'cadeado de sigiloso'],
  ['--trail-deep', '--paper', TEXTO, 'links e sobretítulos'],
  ['--ink', '--marker', TEXTO, 'botão de destaque (.btn.lime)'],
  ['--ink', '--paper', INTERFACE, 'anel interno do foco sobre a página'],
];

describe('contraste dos tokens (WCAG AA)', () => {
  it.each(PARES)('%s sobre %s ≥ %s (%s)', (frente, fundo, minimo) => {
    const f = cor(fundo);
    expect(razao(cor(frente, f), f)).toBeGreaterThanOrEqual(minimo);
  });

  it('texto da navegação (--on-ink-soft composto sobre --ink) ≥ 4,5', () => {
    const fundo = cor('--ink');
    expect(token('--on-ink-soft')).toBe('rgba(255,255,255,.78)');
    expect(razao(cor('--on-ink-soft', fundo), fundo)).toBeGreaterThanOrEqual(TEXTO);
  });

  it('frase do login (rgba(241,244,234,.78) sobre #12302B) ≥ 4,5', () => {
    const fundo = cor('#12302B');
    expect(razao(cor('rgba(241,244,234,.78)', fundo), fundo)).toBeGreaterThanOrEqual(TEXTO);
  });

  it('o --marker sozinho não serve de foco em fundo claro (por isso o anel interno --ink)', () => {
    expect(razao(cor('--marker'), cor('--surface'))).toBeLessThan(INTERFACE);
    const base = readFileSync('src/styles/base.css', 'utf8');
    expect(base).toMatch(/:focus-visible:not\(\[appfocopreso\]\)\{outline:3px solid var\(--marker\)[^}]*box-shadow:0 0 0 2px var\(--ink\)/);
  });

  it('a fórmula confere com valores de referência (preto/branco = 21, cinza #767676/branco ≈ 4,54)', () => {
    expect(razao([0, 0, 0], [255, 255, 255])).toBeCloseTo(21, 5);
    expect(razao([0x76, 0x76, 0x76], [255, 255, 255])).toBeCloseTo(4.54, 2);
  });
});
