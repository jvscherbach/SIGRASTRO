import { describe, expect, it } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';

/**
 * L1, bloco 4: nenhuma credencial de demonstração no código-fonte do front. Vale como a verificação de `contraste` e
 * `cores-fixas`: falha se achar `vistas-demo`, um login `*.demo` ou o texto "perfis de demonstração" em `src`.
 * (Este arquivo monta os termos por partes para não conter o próprio texto proibido.)
 */
const PROIBIDOS: { nome: string; regex: RegExp }[] = [
  { nome: 'senha antiga', regex: new RegExp('vistas' + '-demo', 'i') },
  { nome: 'login de demonstração', regex: new RegExp('\\.' + 'demo\\b', 'i') },
  { nome: 'perfis de demonstração', regex: new RegExp('perfis de ' + 'demonstra', 'i') },
  { nome: 'senha de demonstração', regex: new RegExp('SENHA_' + 'DEMO|USUARIOS_' + 'DEMO|entrarComo' + 'Demo') },
  { nome: 'aviso de acesso simulado', regex: new RegExp('autentica' + 'ção real', 'i') },
];

function arquivos(pasta: string): string[] {
  return readdirSync(pasta, { recursive: true, encoding: 'utf8' })
    .map((r) => `${pasta}/${r.replace(/\\/g, '/')}`)
    .filter((c) => /\.(ts|html|css|json|svg)$/.test(c) && !c.endsWith('sem-demonstracao.spec.ts'));
}

describe('front sem usuários nem senha de demonstração', () => {
  it('nenhum arquivo de src tem credencial de demonstração', () => {
    const lista = arquivos('src');
    expect(lista.length).toBeGreaterThan(50);
    const achados: string[] = [];
    for (const caminho of lista) {
      const texto = readFileSync(caminho, 'utf8');
      for (const p of PROIBIDOS) {
        if (p.regex.test(texto)) achados.push(`${caminho}: ${p.nome}`);
      }
    }
    expect(achados).toEqual([]);
  });
});
