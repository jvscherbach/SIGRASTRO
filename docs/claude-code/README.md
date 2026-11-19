# Ferramentas do Claude Code no Rastro

O repositório traz configuração do próprio Claude Code, versionada, que serve de **guarda de segurança** do projeto: as regras permanentes do [`CLAUDE.md`](../../CLAUDE.md) (só `git diff`, nada de `.env.local`, nada de `down -v`, nenhum dado pessoal real) deixam de depender só da instrução e passam a ser conferidas por código a cada ação. Tudo é **gratuito**, em Node.js (que o projeto já tem por causa do Angular) e sem build. Os hooks não têm dependência; o servidor MCP usa o SDK oficial e o `zod`, com versão fixa e licença MIT (docs/13).

| Ferramenta | Onde | O que faz | Estado |
|---|---|---|---|
| **Hooks (guardas)** | [`.claude/settings.json`](../../.claude/settings.json), [`.claude/hooks/`](../../.claude/hooks/) | Bloqueiam git além de `git diff`, `gh`, apagar volumes, ler arquivo de segredo, despejar variáveis de ambiente; avisam quando um arquivo gravado tem CPF/CNPJ válido, token ou chave privada | S6, bloco 6 e S6.1 (postura "na dúvida, bloqueia", camadas e corpus com isca) — [hooks.md](hooks.md), [segredos.md](segredos.md) |
| **Servidor MCP `rastro`** | [`.mcp.json`](../../.mcp.json), [`tools/mcp-rastro/`](../../tools/mcp-rastro/) | Consultas somente leitura ao Rastro local em modo de demonstração, com saída mascarada | S6, bloco 7 — [mcp.md](mcp.md) |
| **Subagentes e `/verificar-sprint`** | `.claude/agents/`, `.claude/commands/` | Revisão de segurança, execução de testes e conferência das docs, em paralelo; relatório em `docs/progresso/verificacao-s<N>.md` | S6, bloco 8 — [agentes.md](agentes.md) |

`.claude/settings.local.json` é a configuração **pessoal** de cada máquina e fica fora do Git (`.gitignore`). A configuração do projeto vale para qualquer sessão aberta nesta pasta, inclusive a dos subagentes.

## Como as guardas se comportam

- **Bloqueiam só o que casa com a regra.** O Claude recebe a mensagem (em português, com a regra e o que fazer) e escolhe outro caminho; nada é executado.
- **Erro interno não trava a sessão.** Se a própria guarda falhar (entrada inválida, exceção), ela escreve um aviso curto e deixa a ação seguir. Uma guarda quebrada nunca impede o trabalho, mas também deixa de proteger: o aviso aparece na tela.
- **Não substituem a revisão.** São uma rede a mais: o responsável continua revisando por `git diff` e commitando ele mesmo.

## Como desligar uma guarda temporariamente

Remova a entrada do hook em `.claude/settings.json` (o bloco com o `matcher` correspondente) e abra uma nova sessão. Para desligar só na sua máquina, sem mexer no arquivo versionado, use `"disableAllHooks": true` no seu `.claude/settings.local.json` (desliga todas). Volte a ligar assim que terminar: as regras do `CLAUDE.md` continuam valendo com ou sem guarda.

## Como rodar os testes

```bash
node --test .claude/hooks/guarda-bash.test.mjs .claude/hooks/guarda-arquivos.test.mjs .claude/hooks/varrer-dados.test.mjs .claude/hooks/vazamento.test.mjs
```

(No Node 22 também vale `node --test ".claude/hooks/*.test.mjs"`.) Os testes usam só entradas simuladas: nenhum commit é tentado e nenhum segredo é lido. O CI roda o mesmo comando no job `mudancas`, que sempre executa (docs/13).

## Como demonstrar (resumo)

O roteiro da sprint, [s6-homologacao.md](../roteiros/s6-homologacao.md), traz cada demonstração passo a passo: (1) pedir um commit e ver o bloqueio; (2) pedir para ler o arquivo de segredo local (bloqueado) e o arquivo de exemplo (passa); (3) gravar um arquivo com CPF válido e ver o aviso sem eco do valor; (4) chamar `buscar_requerimento` e `listar_documentos` pelo MCP com a saída mascarada; (5) rodar `/verificar-sprint 6` numa sessão nova e abrir o relatório.

## Limites conhecidos

- As guardas barram o que casa com a regra; não há garantia contra todo jeito de contorná-las. A verificação da S6 ([verificacao-s6.md](../progresso/verificacao-s6.md)) apontou duas lacunas do `guarda-bash` (busca recursiva a partir da raiz e despejo de variável por `node -e`/`python -c`), a decidir pelo responsável, e o teste do servidor MCP fora do CI.
- O `guarda-bash` já bloqueou por engano um texto que só **citava** um arquivo de segredo ou o GitHub CLI dentro de um `heredoc`; nesse caso o Claude deve gravar o arquivo com a ferramenta de escrita.
- Subagentes e comandos só carregam em sessão nova.
