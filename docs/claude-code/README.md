# Ferramentas do Claude Code no Rastro

O repositório traz configuração do próprio Claude Code, versionada, que serve de **guarda de segurança** do projeto: as regras permanentes do [`CLAUDE.md`](../../CLAUDE.md) (só `git diff`, nada de `.env.local`, nada de `down -v`, nenhum dado pessoal real) deixam de depender só da instrução e passam a ser conferidas por código a cada ação. Tudo é **gratuito**, em Node.js (que o projeto já tem por causa do Angular), sem dependência e sem build.

| Ferramenta | Onde | O que faz | Estado |
|---|---|---|---|
| **Hooks (guardas)** | [`.claude/settings.json`](../../.claude/settings.json), [`.claude/hooks/`](../../.claude/hooks/) | Bloqueiam git além de `git diff`, `gh`, apagar volumes, ler arquivo de segredo, despejar variáveis de ambiente; avisam quando um arquivo gravado tem CPF/CNPJ válido, token ou chave privada | S6, bloco 6 — [hooks.md](hooks.md) |
| **Servidor MCP `rastro`** | `.mcp.json`, `tools/mcp-rastro/` | Consultas somente leitura ao Rastro local em modo de demonstração | S6, bloco 7 (a fazer) |
| **Subagentes e `/verificar-sprint`** | `.claude/agents/`, `.claude/commands/` | Revisão de segurança, execução de testes e conferência das docs, em paralelo | S6, bloco 8 (a fazer) |

`.claude/settings.local.json` é a configuração **pessoal** de cada máquina e fica fora do Git (`.gitignore`). A configuração do projeto vale para qualquer sessão aberta nesta pasta, inclusive a dos subagentes.

## Como as guardas se comportam

- **Bloqueiam só o que casa com a regra.** O Claude recebe a mensagem (em português, com a regra e o que fazer) e escolhe outro caminho; nada é executado.
- **Erro interno não trava a sessão.** Se a própria guarda falhar (entrada inválida, exceção), ela escreve um aviso curto e deixa a ação seguir. Uma guarda quebrada nunca impede o trabalho, mas também deixa de proteger: o aviso aparece na tela.
- **Não substituem a revisão.** São uma rede a mais: o responsável continua revisando por `git diff` e commitando ele mesmo.

## Como desligar uma guarda temporariamente

Remova a entrada do hook em `.claude/settings.json` (o bloco com o `matcher` correspondente) e abra uma nova sessão. Para desligar só na sua máquina, sem mexer no arquivo versionado, use `"disableAllHooks": true` no seu `.claude/settings.local.json` (desliga todas). Volte a ligar assim que terminar: as regras do `CLAUDE.md` continuam valendo com ou sem guarda.

## Como rodar os testes

```bash
node --test .claude/hooks/guarda-bash.test.mjs .claude/hooks/guarda-arquivos.test.mjs .claude/hooks/varrer-dados.test.mjs
```

(No Node 22 também vale `node --test ".claude/hooks/*.test.mjs"`.) Os testes usam só entradas simuladas: nenhum commit é tentado e nenhum segredo é lido. O CI roda o mesmo comando no job `mudancas`, que sempre executa (docs/13).
