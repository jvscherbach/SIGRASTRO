// Declaração mínima só para o teste dos tokens ler tokens.css como texto (sem adicionar @types/node).
declare module 'node:fs' {
  export function readFileSync(caminho: string, codificacao: 'utf8'): string;
}
