import { Page, Route, expect, test } from '@playwright/test';

import { ADMIN } from './api-simulada';

/**
 * S6, bloco 2: geometria das tabelas de Usuários e perfis e da auditoria (Acessos e Documentos) em 1280, 1024, 768 e
 * 360 px, com muitos registros e textos longos. Não compara imagem: mede a posição horizontal do cabeçalho e das
 * células de cada coluna e a rolagem horizontal da página. Dados fictícios, API simulada.
 */
const LARGURAS = [1280, 1024, 768, 360];
const LONGO = 'Servidor Exemplo com nome bastante longo para forçar quebra de linha na célula da tabela ' + 'x'.repeat(40);
const ARQUIVO_LONGO = 'arquivo-com-nome-muito-longo-sem-espacos-' + 'abcdefghij'.repeat(8) + '.pdf';

const USUARIOS = Array.from({ length: 30 }, (_, i) => ({
  id: `u${i}`,
  nome: i % 3 === 0 ? `${LONGO} ${i}` : `Usuário Exemplo ${String(i).padStart(2, '0')}`,
  login: i % 3 === 0 ? `login.muito.longo.exemplo.${i}.${'y'.repeat(30)}` : `usuario.${i}`,
  perfil: i % 3 === 0 ? 'ADMIN' : i % 3 === 1 ? 'OPERADOR_SIGILO' : 'OPERADOR',
  perfilRotulo: 'x',
  ativo: i % 4 !== 0,
  ultimoAcesso: i % 2 ? '2026-10-21T14:00:00Z' : null,
  cpfCnpj: i % 2 ? '***.456.789-**' : null,
}));

function evento(id: number, sobre: object = {}) {
  return {
    id,
    dataHora: '2026-10-21T09:00:00-04:00',
    tipo: 'DOSSIE',
    rotulo: 'Dossiê gerado',
    usuario: { login: 'admin', nome: 'Administrador', perfil: 'ADMIN' },
    idRequerimento: 101,
    numeroProcesso: '1/2025',
    numeroRequerimento: '2/2025',
    sigiloso: id % 4 === 0,
    arquivo: id % 2 ? ARQUIVO_LONGO : 'curto.pdf',
    idPedido: null,
    detalhe: id % 3 === 0 ? 'Detalhe longo ' + 'palavra '.repeat(30) : id % 3 === 1 ? 'Curto' : null,
    ...sobre,
  };
}
const EVENTOS = Array.from({ length: 20 }, (_, i) => evento(i + 1));

async function simular(page: Page) {
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const caminho = new URL(route.request().url()).pathname;
    if (caminho === '/api/auth/me') return route.fulfill({ json: ADMIN });
    if (caminho === '/api/configuracoes/usuarios') {
      return route.fulfill({ json: { conteudo: USUARIOS, pagina: 0, tamanho: 30, totalElementos: 30, totalPaginas: 1 } });
    }
    if (caminho.startsWith('/api/auditoria/')) {
      return route.fulfill({
        json: { conteudo: EVENTOS, pagina: 0, tamanho: 20, totalElementos: 20, totalPaginas: 1, indicadores: { usuarios: 1 } },
      });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
}

/** Posição horizontal (esquerda) do cabeçalho e de cada célula da coluna `indice` de uma tabela. */
async function esquerdas(page: Page, tabela: string, indice: number) {
  return page.evaluate(
    ([seletor, i]) => {
      const t = document.querySelector(seletor as string)!;
      const celulas = [...t.querySelectorAll(`thead th:nth-child(${(i as number) + 1}), tbody tr:not([class*=cfg-]) td:nth-child(${(i as number) + 1})`)];
      return celulas.map((c) => Math.round(c.getBoundingClientRect().left));
    },
    [tabela, indice] as const,
  );
}

async function semRolagemHorizontal(page: Page) {
  const { rolagem, largura } = await page.evaluate(() => ({
    rolagem: document.documentElement.scrollWidth,
    largura: document.documentElement.clientWidth,
  }));
  expect(rolagem, 'a página não rola na horizontal').toBeLessThanOrEqual(largura);
}

for (const largura of LARGURAS) {
  test.describe(`${largura}px`, () => {
    test.use({ viewport: { width: largura, height: 900 } });

    test('Usuários e perfis: colunas alinhadas, ações visíveis, sem rolagem da página', async ({ page }) => {
      await simular(page);
      await page.goto('/configuracoes');
      const tabela = 'table.cfg-usuarios';
      await expect(page.locator(`${tabela} tbody tr`)).toHaveCount(30);
      if (largura > 768) {
        for (let coluna = 0; coluna < 6; coluna++) {
          const xs = await esquerdas(page, tabela, coluna);
          expect(new Set(xs).size, `coluna ${coluna} alinhada (${xs.slice(0, 4)})`).toBe(1);
        }
      } else {
        // cartões: a tabela inteira cabe na área visível, sem rolagem interna
        const { rolagem, visivel } = await page.evaluate((s) => {
          const w = document.querySelector(s)!.closest('.table-wrap')!;
          return { rolagem: w.scrollWidth, visivel: w.clientWidth };
        }, tabela);
        expect(rolagem).toBeLessThanOrEqual(visivel);
        // perfil e situação visíveis dentro da tela
        for (const seletor of ['select.cfg-perfil', 'select.cfg-situacao']) {
          const b = (await page.locator(`${tabela} tbody tr`).first().locator(seletor).boundingBox())!;
          expect(b.x + b.width).toBeLessThanOrEqual(largura);
        }
      }
      // perfil e situação: o controle cabe inteiro dentro da linha
      const caixa = await page.locator(`${tabela} tbody tr`).first().locator('select.cfg-perfil').boundingBox();
      expect(caixa!.width).toBeGreaterThan(60);
      await semRolagemHorizontal(page);
    });

    for (const rota of ['/auditoria/acessos', '/auditoria/documentos']) {
      test(`${rota}: cabeçalho e células de cada coluna alinhados (inclusive Detalhe)`, async ({ page }) => {
        await simular(page);
        await page.goto(rota);
        const tabela = 'table.aud-tabela';
        await expect(page.locator(`${tabela} tbody tr`)).toHaveCount(20);
        // toda célula segue como célula de tabela (display:flex no <td> a tirava do layout e desalinhava a coluna)
        const exibicoes = await page.evaluate(
          (s) => [...document.querySelectorAll(`${s} tbody td`)].map((c) => getComputedStyle(c).display),
          tabela,
        );
        expect(new Set(exibicoes)).toEqual(new Set(['table-cell']));
        const colunas = await page.locator(`${tabela} thead th`).count();
        for (let coluna = 0; coluna < colunas; coluna++) {
          const xs = await esquerdas(page, tabela, coluna);
          expect(new Set(xs).size, `coluna ${coluna} alinhada (${xs.slice(0, 4)})`).toBe(1);
        }
        await semRolagemHorizontal(page);
      });
    }
  });
}
