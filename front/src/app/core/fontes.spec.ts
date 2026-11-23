import { describe, expect, it } from 'vitest';
import pkg from '../../../package.json';

describe('fontes locais', () => {
  const dependencias = pkg.dependencies as Record<string, string>;

  it.each(['@fontsource/bricolage-grotesque', '@fontsource/instrument-sans'])(
    '%s tem versão fixa',
    (nome) => {
      const versao = dependencias[nome];
      expect(versao).toBeTruthy();
      expect(versao).toMatch(/^\d+\.\d+\.\d+$/);
    },
  );
});
