// Declaração mínima só para os testes de estilo lerem CSS e templates como texto (sem adicionar @types/node).
declare module 'node:fs' {
  export function readFileSync(caminho: string, codificacao: 'utf8'): string;
  export function readdirSync(caminho: string, opcoes: { recursive: true; encoding: 'utf8' }): string[];
}
