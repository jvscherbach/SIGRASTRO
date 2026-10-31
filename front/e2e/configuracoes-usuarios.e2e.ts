import { Page, Route, expect, test } from '@playwright/test';

import { ADMIN, GESTOR, OPERADOR, UsuarioSimulado } from './api-simulada';

/** S5, bloco 9: Configurações › Usuários e perfis (só Administrador) com a API simulada, dados fictícios. */
interface UsuarioApi {
  id: string;
  nome: string;
  login: string;
  perfil: string;
  perfilRotulo: string;
  ativo: boolean;
  ultimoAcesso: string | null;
  cpfCnpj: string | null;
}

const ROTULOS: Record<string, string> = { OPERADOR: 'Operador', OPERADOR_SIGILO: 'Gestor', ADMIN: 'Administrador' };

function usuario(login: string, nome: string, perfil: string, sobre: Partial<UsuarioApi> = {}): UsuarioApi {
  return { id: login, nome, login, perfil, perfilRotulo: ROTULOS[perfil], ativo: true, ultimoAcesso: null, cpfCnpj: null, ...sobre };
}

interface Controle {
  usuarios: UsuarioApi[];
  patches: { id: string; corpo: Record<string, unknown> }[];
  posts: Record<string, unknown>[];
  chamadas: string[];
  recusar: { codigo: string; detalhe: string } | null;
}

async function abrir(page: Page, perfil: UsuarioSimulado, rota = '/configuracoes', sobre: Partial<Controle> = {}): Promise<Controle> {
  const c: Controle = {
    usuarios: [
      usuario('admin.demo', 'Admin Demo', 'ADMIN', { ultimoAcesso: '2026-10-21T14:00:00Z' }),
      usuario('gestor.demo', 'Gestor Demo', 'OPERADOR_SIGILO'),
      usuario('operador.demo', 'Operador Demo', 'OPERADOR', { cpfCnpj: '***.456.789-**' }),
    ],
    patches: [],
    posts: [],
    chamadas: [],
    recusar: null,
    ...sobre,
  };
  await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();
    if (caminho.startsWith('/api/configuracoes')) c.chamadas.push(`${metodo} ${caminho}`);
    if (caminho === '/api/auth/me') return route.fulfill({ json: perfil });
    if (caminho === '/api/configuracoes/usuarios' && metodo === 'GET') {
      return route.fulfill({ json: { conteudo: c.usuarios, pagina: 0, tamanho: c.usuarios.length, totalElementos: c.usuarios.length, totalPaginas: 1 } });
    }
    if (caminho === '/api/configuracoes/usuarios' && metodo === 'POST') {
      const corpo = route.request().postDataJSON() as Record<string, string>;
      c.posts.push(corpo);
      const digitos = corpo['cpfCnpj'];
      const novo = usuario(corpo['login'], corpo['nome'], corpo['perfil'], { cpfCnpj: digitos ? `***.${digitos.slice(3, 6)}.${digitos.slice(6, 9)}-**` : null });
      c.usuarios.push(novo);
      return route.fulfill({ status: 201, json: novo });
    }
    if (caminho.startsWith('/api/configuracoes/usuarios/') && metodo === 'PATCH') {
      const id = decodeURIComponent(caminho.split('/').pop()!);
      const corpo = route.request().postDataJSON() as Record<string, unknown>;
      c.patches.push({ id, corpo });
      if (c.recusar) {
        return route.fulfill({ status: 409, json: { status: 409, errorCode: c.recusar.codigo, detail: c.recusar.detalhe } });
      }
      const alvo = c.usuarios.find((u) => u.id === id)!;
      if (typeof corpo['perfil'] === 'string') {
        alvo.perfil = corpo['perfil'];
        alvo.perfilRotulo = ROTULOS[alvo.perfil];
      }
      if (typeof corpo['ativo'] === 'boolean') alvo.ativo = corpo['ativo'];
      return route.fulfill({ json: alvo });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  await page.goto(rota);
  return c;
}

test.use({ viewport: { width: 1280, height: 900 } });

const linha = (page: Page, login: string) => page.locator(`tr[data-login="${login}"]`);

test('Administrador abre Configurações pelo menu: abas, faixa de memória e lista de usuários', async ({ page }) => {
  await abrir(page, ADMIN, '/consulta');
  await page.locator('.sidebar .nav-item', { hasText: 'Configurações' }).click();

  await expect(page).toHaveURL(/\/configuracoes$/);
  await expect(page.getByRole('tab')).toHaveText(['Usuários e perfis', 'Dossiê', 'Conversão', 'Visualização', 'Conexões', 'Auditoria']);
  await expect(page.getByRole('tab', { name: 'Usuários e perfis' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('.cfg-memoria')).toContainText('somem ao reiniciar o serviço');
  await expect(page.locator('tbody tr')).toHaveCount(3);
  await expect(linha(page, 'operador.demo')).toContainText('***.456.789-**');
  await expect(linha(page, 'admin.demo')).toContainText('você');
});

test('abas navegáveis por teclado com foco visível', async ({ page }) => {
  await abrir(page, ADMIN);
  await page.getByRole('tab', { name: 'Usuários e perfis' }).focus();

  await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('tab', { name: 'Dossiê' })).toBeFocused();
  await expect(page.getByRole('tab', { name: 'Dossiê' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('.cfg-painel')).toContainText('Dossiê');
  await page.keyboard.press('End');
  await expect(page.getByRole('tab', { name: 'Auditoria' })).toBeFocused();
  await page.keyboard.press('Home');
  await expect(page.getByRole('tab', { name: 'Usuários e perfis' })).toBeFocused();
  await expect(page.locator('tbody tr')).toHaveCount(3);
});

for (const [nome, perfil] of [
  ['Operador', OPERADOR],
  ['Gestor', GESTOR],
] as const) {
  test(`${nome}: digitar /configuracoes volta à Consulta sem chamar a API de configurações`, async ({ page }) => {
    const c = await abrir(page, perfil, '/consulta');
    await expect(page.locator('.sidebar .nav-item', { hasText: 'Configurações' })).toHaveCount(0);

    await page.evaluate(() => {
      history.pushState({}, '', '/configuracoes');
      dispatchEvent(new PopStateEvent('popstate'));
    });

    await expect(page).toHaveURL(/\/consulta$/);
    await expect(page.locator('.toast.erro')).toContainText('Seu perfil não tem permissão');
    expect(c.chamadas).toEqual([]);
  });
}

test('mudar o perfil: confirmação, PATCH só com o perfil e lista atualizada', async ({ page }) => {
  const c = await abrir(page, ADMIN);

  await linha(page, 'operador.demo').getByLabel('Perfil de Operador Demo').selectOption('OPERADOR_SIGILO');
  await expect(page.locator('.cfg-confirma')).toContainText('Mudar o perfil de Operador Demo para Gestor?');
  expect(c.patches).toEqual([]);
  await page.getByRole('button', { name: 'Confirmar' }).click();

  await expect(page.locator('.toast')).toContainText('Operador Demo atualizado');
  expect(c.patches).toEqual([{ id: 'operador.demo', corpo: { perfil: 'OPERADOR_SIGILO' } }]);
  await expect(linha(page, 'operador.demo').getByLabel('Perfil de Operador Demo')).toHaveValue('OPERADOR_SIGILO');
});

test('desativar fica esmaecido; cancelar a confirmação não grava', async ({ page }) => {
  const c = await abrir(page, ADMIN);
  const situacao = linha(page, 'gestor.demo').getByLabel('Situação de Gestor Demo');

  await situacao.selectOption('inativo');
  await page.getByRole('button', { name: 'Cancelar' }).click();
  await expect(situacao).toHaveValue('ativo');
  expect(c.patches).toEqual([]);

  await situacao.selectOption('inativo');
  await page.getByRole('button', { name: 'Confirmar' }).click();
  await expect(linha(page, 'gestor.demo')).toHaveClass(/inativo/);
  expect(c.patches).toEqual([{ id: 'gestor.demo', corpo: { ativo: false } }]);
});

for (const [codigo, detalhe] of [
  ['ULTIMO_ADMINISTRADOR', 'Não é possível desativar nem rebaixar o último Administrador ativo.'],
  ['PROPRIO_USUARIO', 'Não é possível desativar o próprio usuário.'],
]) {
  test(`${codigo}: mostra a mensagem do servidor e mantém o valor`, async ({ page }) => {
    await abrir(page, ADMIN, '/configuracoes', { recusar: { codigo, detalhe } });
    const situacao = linha(page, 'admin.demo').getByLabel('Situação de Admin Demo');

    await situacao.selectOption('inativo');
    await page.getByRole('button', { name: 'Confirmar' }).click();

    await expect(page.locator('.cfg-erro-linha')).toContainText(detalhe);
    await expect(situacao).toHaveValue('ativo');
  });
}

test('inclusão: validação local, envio só com dígitos e CPF/CNPJ mascarado na lista', async ({ page }) => {
  const c = await abrir(page, ADMIN);
  await page.getByRole('button', { name: 'Incluir usuário' }).first().click();
  const gaveta = page.getByRole('dialog', { name: 'Incluir usuário' });

  await gaveta.getByRole('button', { name: 'Incluir usuário' }).click();
  await expect(gaveta.locator('.cfg-campo-erro')).toHaveText(['Informe o nome.', 'Informe o login.', 'O CPF/CNPJ é obrigatório para o Operador.']);

  await gaveta.getByLabel('Nome', { exact: true }).fill('Ana de Teste');
  await gaveta.getByLabel('Login').fill('admin.demo');
  await gaveta.getByLabel(/CPF\/CNPJ/).fill('11144477736');
  await expect(gaveta.locator('.cfg-campo-erro')).toHaveText(['Já existe um usuário com este login.', 'CPF/CNPJ inválido.']);

  await gaveta.getByLabel('Login').fill('ana.teste');
  await gaveta.getByLabel(/CPF\/CNPJ/).fill('11144477735');
  await expect(gaveta.getByLabel(/CPF\/CNPJ/)).toHaveValue('111.444.777-35');
  await gaveta.getByRole('button', { name: 'Incluir usuário' }).click();

  await expect(gaveta).toHaveCount(0);
  expect(c.posts).toEqual([{ nome: 'Ana de Teste', login: 'ana.teste', cpfCnpj: '11144477735', perfil: 'OPERADOR' }]);
  await expect(linha(page, 'ana.teste')).toContainText('***.444.777-**');
  await expect(page.locator('tbody')).not.toContainText('11144477735');
  await expect(page.locator('.toast')).toContainText('Ana de Teste incluído');
});

test('quadro do topo com a mesma altura na lista e nas demais abas; celular sem rolagem horizontal', async ({ page }) => {
  await abrir(page, ADMIN);
  await expect(page.locator('tbody tr')).toHaveCount(3);
  const altura = () => page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);
  const inicial = await altura();

  await page.getByRole('tab', { name: 'Conexões' }).click();
  expect(await altura()).toBe(inicial);
  await page.getByRole('tab', { name: 'Usuários e perfis' }).click();
  await expect(page.locator('tbody tr')).toHaveCount(3);

  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0);
  await page.getByRole('button', { name: 'Incluir usuário' }).first().click();
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0);
});
