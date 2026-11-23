import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const css = readFileSync('src/styles/tokens.css', 'utf8');

const obrigatorios: Record<string, string> = {
  '--ok': '#1C7A4D', '--ok-tint': '#DDF1E6', '--warn': '#8A5A00', '--bad': '#B3261E', '--bad-tint': '#FBE4E2',
  '--r': '10px',
  '--paper': '#EDECE5', '--surface': '#FFFFFF', '--ink': '#2A1C12', '--slate': '#665A4E',
  '--line': '#D6D1C6', '--line-strong': '#B9AFA0',
  '--trail': '#A8431A', '--trail-deep': '#7F300F', '--trail-tint': '#F6E3D8',
  '--marker': '#E8B712', '--marker-tint': '#FBF0C6',
  '--sky1': '#F4E3B8', '--sky2': '#FBF4DC', '--l1': '#D9B77A', '--l2': '#B9803F', '--l3': '#8A4A1F',
  '--sun': '#E8B712', '--contour': '#A8431A',
};

function valorDe(nome: string): string | undefined {
  const m = new RegExp(`${nome}\\s*:\\s*([^;]+);`).exec(css);
  return m?.[1].trim();
}

describe('tokens do Cerrado', () => {
  it.each(Object.entries(obrigatorios))('%s = %s', (nome, valor) => {
    expect(valorDe(nome)).toBe(valor);
  });

  it('define as pilhas de fontes com reserva', () => {
    expect(valorDe('--display')).toBe("'Bricolage Grotesque','Segoe UI',system-ui,sans-serif");
    expect(valorDe('--body')).toBe("'Instrument Sans','Segoe UI',system-ui,sans-serif");
  });

  it('define as curvas de nível como data URI com a cor do contorno', () => {
    const img = valorDe('--contour-img') ?? '';
    expect(img).toContain('data:image/svg+xml');
    expect(img).toContain('%23A8431A');
    expect(img).toContain("stroke-opacity='.08'");
  });

  it('aplica as curvas ao body, fixas ao rolar, sem tema por atributo', () => {
    expect(css).toContain('background-image:var(--contour-img)');
    expect(css).toContain('background-attachment:fixed');
    expect(css).not.toContain('data-bioma');
  });
});
