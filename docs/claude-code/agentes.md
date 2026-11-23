# Subagentes e o comando `/verificar-sprint`

Orquestração **mínima** do Claude Code no SIGRASTRO (S6, bloco 8): um comando dispara três subagentes em paralelo e consolida o resultado num relatório. Tudo é gratuito, é só Markdown versionado e não tem build. Os hooks de [hooks.md](hooks.md) valem também para os subagentes: eles não conseguem rodar `git` além de `git diff`, abrir `.env.local` nem derrubar volumes.

| Arquivo | Papel |
|---|---|
| `.claude/agents/revisor-seguranca.md` | Revisa o trabalho contra as regras permanentes (somente leitura) |
| `.claude/agents/executor-de-testes.md` | Roda os testes do jeito documentado e devolve só o resumo |
| `.claude/agents/atualizador-de-docs.md` | Confere `docs/` contra o código e corrige divergências só em `docs/` |
| `.claude/commands/verificar-sprint.md` | O orquestrador: `/verificar-sprint <número>` |

O cabeçalho de cada agente tem `name`, `description`, `tools` (lista que o agente pode usar) e `model`.

## Os três agentes

### `revisor-seguranca` (`Read`, `Grep`, `Glob`; modelo `opus`)

Só lê. Recebe a lista de arquivos alterados e o resumo da sprint e confere: SQL só `SELECT` e parametrizado; credencial, token, chave de arquivo e CPF fora de resposta, log, erro e auditoria (RNF09); sigilo e matriz de acesso (RN06, RN07, RN08); falha de auditoria sem efeito na operação; nenhum dado pessoal real em teste e documentação; nenhuma dependência paga; hooks e servidor MCP dentro do que prometem. Devolve `STATUS`, `ACHADOS` (arquivo, linha, gravidade `ALTA`/`MÉDIA`/`BAIXA`, correção) e `LIMITES`, **sem repetir valor sensível**.

Limite: não executa nada. O que só aparece rodando (um teste, uma tela) não é visto por ele.

### `executor-de-testes` (`Bash`, `Read`, `Grep`, `Glob`; modelo `sonnet`)

Descobre em `CLAUDE.md` e `docs/13` como rodar o ArchUnit, o back-end, o front, o e2e, os testes dos hooks e do servidor MCP, roda e devolve só o resumo: o que passou, o que falhou (nome do teste e causa provável), o que foi **pulado e por quê** (sem Docker, sem `soffice`, variável de fumaça ausente). Não corrige código. Só sobe o Compose se for indispensável, e então só com os três arquivos de composição, conferindo antes que as variáveis externas estão vazias.

Limite: sem Docker, os testes de Testcontainers ficam pulados; no GitHub Actions, a conversão com LibreOffice só roda na `main` (`docs/13`).

### `atualizador-de-docs` (`Read`, `Grep`, `Glob`, `Edit`; modelo `sonnet`)

Confere se `docs/02`, `03`, `07`, `08`, `09`, `10`, `12`, `13`, `14` e o progresso da sprint refletem o que o código mostra (rotas, variáveis, decisões, riscos, blocos marcados) e faz **edições mínimas** em `docs/`. Nunca edita código nem arquivo fora de `docs/`; o que não consegue confirmar no código fica como está, listado como dúvida. Lista o que mudou.

Limite: a restrição a `docs/` está na instrução do agente, não na ferramenta (`Edit` aceita qualquer caminho); por isso o relatório lista as alterações e elas aparecem no `git diff` para revisão antes do commit.

## O comando `/verificar-sprint <número>`

1. Lê `docs/progresso/s<N>.md` e `git diff --name-only` (com a árvore limpa, `git diff main...HEAD`; se a lista for grande demais, limita-se ao que o progresso da sprint cita).
2. Dispara os **três agentes em paralelo**, cada um com o resumo da sprint.
3. Consolida em `docs/progresso/verificacao-s<N>.md`: três seções (**Segurança**, **Testes**, **Documentação**), cada uma com status (`OK`, `ATENÇÃO`, `FALHA`), achados e próximo passo, mais os limites da verificação.
4. Responde com a tabela-resumo.

O comando **não** corrige código, **não** faz commit e **não** roda nada que as guardas bloqueiem. Um `FALHA` do revisor ou dos testes é um aviso para o responsável, não algo que o comando conserta.

**Evidência da S6:** [../progresso/verificacao-s6.md](../progresso/verificacao-s6.md) (execução única, no estado do bloco 8).

## Agentes do modo automático (A1, bloco 3)

Quatro agentes usados pelo comando `/orquestrar` (ver D89): três **executores**, um por modelo, e o `commitador`.

| Arquivo | Modelo | Ferramentas | Papel |
|---|---|---|---|
| `.claude/agents/executor-haiku.md` | `haiku` | `Read, Write, Edit, Glob, Grep, Bash` | Executa **um** bloco atribuído a Haiku; não commita |
| `.claude/agents/executor-sonnet.md` | `sonnet` | idem | Executa **um** bloco atribuído a Sonnet; não commita |
| `.claude/agents/executor-opus.md` | `opus` | idem | Executa **um** bloco atribuído a Opus; não commita |
| `.claude/agents/commitador.md` | `haiku` | `Read, Bash` | Roda só o `commit-bloco` e devolve a saída |

**Executores.** O corpo é **idêntico** nos três (só `name`, `description` e `model` mudam; o teste confere). Recebem o caminho do prompt, o número do bloco, `SESSÃO: <X>` e `MODO: AUTOMÁTICO`. Leem o prompt inteiro e só o bloco pedido; se o bloco não for do modelo indicado ou já estiver concluído, devolvem `PARADA: bloco N não é meu`. Rodam **só os testes ligados ao bloco** (a suíte completa só se o bloco pedir, uma vez). Terminando verdes, gravam `.claude/tmp/commit-msg.txt` e `.claude/tmp/bloco-<N>.json` (`direcionados`, `completa` ou `nao-se-aplica`) e devolvem o relatório `BLOCO N CONCLUÍDO`; com falha ou dúvida, `PARADA: <motivo>` e **sem** marca de testes. Não editam `.claude/` (a guarda bloqueia subagentes ali, exceto `.claude/tmp/`).

**`commitador`.** Sem `Write` nem `Edit`. Roda **só** `node .claude/scripts/commit-bloco.mjs --bloco <N> --mensagem .claude/tmp/commit-msg.txt` (a única regra de `permissions.allow`). Se o script falhar, devolve `FALHA DO COMMIT:` com o texto do erro, sem variações e sem usar `git`.

**Teste automatizado:** `node --test .claude/hooks/agentes-orquestracao.test.mjs` confere nomes, `model`, `tools`, corpo idêntico dos três executores, ausência de ferramenta de escrita no `commitador` e que nenhum dos quatro cita publicação remota.

**Teste manual (o responsável roda no bloco 6):** com `docs/prompts/exemplo-orquestracao.md` e o roteiro `docs/roteiros/a1-homologacao.md`. Agentes novos só carregam em **sessão nova**.

## Como estender

- **Novo agente:** crie `.claude/agents/<nome>.md` com o cabeçalho (`name`, `description`, `tools` e `model`) e o corpo com papel, entrada, limites e **formato de resposta** fixo. Dê só as ferramentas de que ele precisa (menos é mais seguro) e repita as regras do projeto no corpo.
- **Nova etapa no comando:** acrescente o agente ao passo 2 de `verificar-sprint.md` e uma seção ao relatório do passo 3.
- **Outro comando:** `.claude/commands/<nome>.md`, com `description` e, se recebe argumento, `argument-hint` e `$ARGUMENTS` no texto.
- Subagentes e comandos novos só são lidos **ao iniciar uma sessão** do Claude Code; depois de criar ou editar um, abra uma sessão nova.

## Como testar e limites conhecidos

- Rode `/verificar-sprint <N>` numa sessão aberta **depois** de criados os arquivos. A S6 foi verificada numa sessão que criou os agentes: como eles ainda não estavam carregados, o orquestrador usou agentes gerais que **leram as definições** e seguiram as mesmas instruções e o mesmo modelo (a restrição de ferramentas valeu só por instrução). Numa sessão nova, o comando usa os agentes de verdade.
- O relatório é uma fotografia: roda os testes de novo a cada execução (minutos) e consome cota de modelo; use ao fechar um bloco ou sprint, não a cada edição.
- Com a árvore limpa e a branch longa, `git diff main...HEAD` traz centenas de arquivos; por isso o comando limita o escopo ao que o progresso da sprint cita.
