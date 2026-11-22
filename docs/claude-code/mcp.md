# Servidor MCP do Rastro (S6, bloco 7)

O **MCP** (Model Context Protocol) deixa o Claude Code chamar ferramentas de um servidor local. O servidor `rastro` expõe **consultas somente leitura** ao Rastro local **em modo de demonstração**, para mostrar a integração sem tocar em nada real. Ele **nunca** fala com o SIGA, o SCORP, o Camunda nem a API de arquivos: fala só com o Rastro local, e só quando o Rastro se declara em demonstração, o que exige que nenhum desses sistemas esteja configurado.

| Item | Onde |
|---|---|
| Código (Node ESM, sem build) | [`tools/mcp-rastro/`](../../tools/mcp-rastro/): `servidor.mjs`, `cliente-rastro.mjs`, `ferramentas.mjs`, `mascara.mjs`, `rastro-simulado.mjs` |
| Registro no Claude Code | [`.mcp.json`](../../.mcp.json) na raiz (servidor `rastro`, por `stdio`) |
| Dependências (versão fixa, `package-lock.json` versionado) | `@modelcontextprotocol/sdk` 1.32.2 (MIT) e `zod` 4.6.7 (MIT); transitivas MIT, ISC, BSD-2 e BSD-3; `npm audit --omit=dev` em 0 (docs/13) |
| Lado do serviço | `GET /api/estado` (docs/07), com `RASTRO_MODO` (docs/12) |

## O que expõe

| Ferramenta | Entrada | Saída (texto curto) | Chama no Rastro |
|---|---|---|---|
| `buscar_requerimento` | `tipo` (`REQUERIMENTO` ou `PROCESSO`), `numero` (`N/AAAA`) | identificação e id, números do requerimento e do processo, situação, tipo, abertura. **Sem** CPF/CNPJ nem nome do interessado | `GET /api/estado`, `GET /api/busca` |
| `listar_documentos` | `idRequerimento` | para cada documento: título, nome do arquivo, tipo, origem, data, situação (vigente, substituído, indisponível), sigiloso (sim/não). **Sem** chave do arquivo e sem conteúdo | `GET /api/estado`, `GET /api/requerimentos/{id}/documentos` |
| `estado_do_servico` | — | se o Rastro local está no ar e o modo | `GET /api/estado` |
| Recurso `rastro://docs/indice` | — | títulos e caminhos de `docs/`, lidos da tabela de `docs/README.md` | nada (arquivo local) |

Todas as ferramentas são marcadas como somente leitura (`readOnlyHint`). Toda saída termina com `Mascarados: N`.

## O que NÃO faz

Não cria, não altera, não apaga, não gera dossiê, não baixa documento nem conteúdo, não lê o `.env.local` e não aceita destino vindo do pedido. O único pedido que não é `GET` é o login de demonstração (`POST /api/auth/login`), feito pelo próprio servidor.

## Segurança (testada)

- **Só local:** `RASTRO_MCP_URL` precisa ter host `localhost`, `127.0.0.1` ou `host.docker.internal` (http ou https, sem usuário, senha, consulta ou fragmento); qualquer outro é recusado, sem exceção por variável. Com URL recusada o servidor sobe e toda ferramenta responde com a recusa. Redirecionamento (3xx) é recusado, porque poderia levar a outro host.
- **Só demonstração:** antes de qualquer consulta de dados, `GET /api/estado`; se o modo não for `demonstracao`, recusa com a orientação de subir o ambiente de verificação. O serviço só se declara em demonstração com `RASTRO_MODO=demonstracao` **e** SIGA, API de arquivos e SCORP vazios.
- **Só `GET`** nas ferramentas; os testes conferem cada pedido feito ao Rastro simulado.
- **Mascaramento da saída:** CPF e CNPJ (com e sem máscara, válidos ou não), e-mail, token (JWT, Bearer), cookie e `token=`/`chave=`/`senha=` em texto viram `***`; no JSON, valores de chaves como `chave`, `token`, `cookie`, `senha`, `cpfCnpj` são trocados antes de montar o texto. Vale mesmo que a API devolva isso por engano (testado injetando esses dados na resposta simulada).
- **Erros** só com o status HTTP e o `errorCode` do `ProblemDetail`: sem URL, parâmetro, token nem corpo. Tempo limite (`RASTRO_MCP_TIMEOUT_MS`, 10 s) e tamanho máximo de resposta (1 MB).
- **Credencial:** usuário e senha de **demonstração** por `RASTRO_MCP_USUARIO` e `RASTRO_MCP_SENHA`, do **ambiente do processo** (nunca do `.env.local`; sem valor padrão no repositório). O token de sessão fica só em memória e é renovado perto de vencer ou depois de um 401.
- `stdout` só com o protocolo MCP; avisos em `stderr`, sem dado sensível.

## Como subir o ambiente de demonstração

1. Suba o Rastro **sem nenhum sistema externo** (as três camadas de sempre; o `compose.verificacao.yaml` esvazia os externos e liga `RASTRO_MODO=demonstracao`):

   ```bash
   docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d --build
   ```

   Confira, dentro do container e sem imprimir valores, que as variáveis externas estão vazias (`CLAUDE.md`).
2. No terminal onde você abre o Claude Code, defina o usuário de demonstração (a senha é a `VISTAS_DEMO_SENHA` do perfil local; **não** a coloque em arquivo versionado):

   ```bash
   export RASTRO_MCP_USUARIO=operador.demo
   export RASTRO_MCP_SENHA=...      # PowerShell: $env:RASTRO_MCP_SENHA = "..."
   ```
3. Abra o Claude Code na raiz do projeto. Na primeira vez ele pede **aprovação** para usar o servidor `rastro` do `.mcp.json` (servidores de projeto só rodam depois que você aprova; dá para rever em `/mcp`). Aprove e peça, por exemplo, "use o MCP do Rastro para ver o estado do serviço".

**Sem dados no ambiente de verificação.** O ambiente de verificação não tem SIGA: `buscar_requerimento` e `listar_documentos` respondem "HTTP 503 SIGA_INDISPONIVEL (... o SIGA não está ligado)", o que já demonstra o caminho seguro. Para demonstrar **com dados fictícios** (e o mascaramento), use o **Rastro simulado**, que só escuta em `127.0.0.1` e se declara em demonstração:

```bash
npm --prefix tools/mcp-rastro ci
npm --prefix tools/mcp-rastro run simulado        # porta 8099; VAZAR=1 injeta CPF/token/e-mail falsos para ver a máscara
export RASTRO_MCP_URL=http://127.0.0.1:8099 RASTRO_MCP_USUARIO=demo RASTRO_MCP_SENHA=demo
```

Os dados são evidentemente fictícios (processo 2825/2025, requerimento 3331/2025, id 101). **NÃO CONFIRMADO / pendente:** um Rastro de demonstração com um SIGA fictício (Oracle 23 Free com a base dos testes) daria dados ao ambiente de verificação; não foi feito nesta sprint (R66).

## Como testar

```bash
npm --prefix tools/mcp-rastro ci
npm --prefix tools/mcp-rastro test
```

20 testes com `node --test` (no CI desde a S8, bloco 6: o job `mudancas` roda `npm ci`, `npm test` e `npm audit --omit=dev --audit-level=high` em `tools/mcp-rastro`; o `dependabot.yml` cobre a pasta), contra o Rastro simulado da própria suíte: as três ferramentas e o recurso pelo protocolo MCP (cliente em memória e o processo real por stdio), formato do número, erro 404 sem detalhe, recusa de host remoto (também pelo protocolo), recusa fora da demonstração sem nenhuma consulta de dados, só `GET` (inspecionando cada pedido), mascaramento com dados injetados, login e renovação (vencimento e 401), sem credencial, erros sem segredo, tempo esgotado, resposta grande demais e redirecionamento. Os testes não rodam no CI (exigem `npm ci` da pasta); rode-os ao mexer em `tools/mcp-rastro/`.
