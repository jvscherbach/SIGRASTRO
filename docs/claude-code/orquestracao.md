# Commit automático e orquestração de blocos (A1)

Modo opcional em que **uma sessão do Claude Code executa um prompt inteiro, bloco a bloco, e commita cada bloco sozinha**, sem parar para o responsável. É uma **exceção deliberada e estreita** à regra "só o responsável commita" (D89, R77, R78): o único caminho de commit é o script `.claude/scripts/commit-bloco.mjs`, e só em branch `auto/*`. O prompt que construiu o modo é [A1](../prompts/A1-commit-automatico-orquestracao.md); o andamento está em [a1.md](../progresso/a1.md).

## O fluxo

```
/orquestrar docs/prompts/<prompt>.md [bloco-inicial] [bloco-final]
  sessão principal (Sonnet)
    confere: branch auto/*, árvore limpa, guardas ativas, tabela "Blocos e sessões"
    lê docs/progresso/<id>.md → primeiro bloco não concluído
    ┌─ para cada bloco ───────────────────────────────────────────┐
    │ executor-<haiku|sonnet|opus>  executa o bloco, roda os     │
    │     testes do bloco, grava .claude/tmp/commit-msg.txt e    │
    │     .claude/tmp/bloco-N.json, devolve o relatório          │
    │ (PARADA? o orquestrador para e avisa)                      │
    │ commitador  roda commit-bloco (confere tudo e commita)     │
    │ (FALHA DO COMMIT? o orquestrador para e avisa)             │
    └─────────────────────────────────────────────────────────────┘
    no fim: suíte completa uma vez (executor-de-testes) e tabela bloco → hash
```

## Como preparar

1. Crie a branch (comando do responsável): `git switch -c auto/<nome>`. O script recusa `main`, `master`, qualquer outra e HEAD solto.
2. Deixe a árvore **limpa** (commite ou descarte o que estiver pendente) e confira que `user.name` e `user.email` do Git estão configurados.
3. **Remova** o marcador `~/.rastro/liberar-construcao-claude`, se existir: ele só serve para construir as ferramentas do `.claude/` e não deve existir na execução automática (o orquestrador não consegue conferir).
4. Abra o Claude Code **em Sonnet**, numa sessão **nova** (agentes e comandos só carregam ao iniciar).

## Como rodar

`/orquestrar <caminho-do-prompt> [bloco-inicial] [bloco-final]`. O prompt precisa ter a tabela "Blocos e sessões" (Bloco, Título, Sessão) e um `docs/progresso/<id>.md`. Exemplo: `/orquestrar docs/prompts/R2-refatorar-front-sigrastro.md`. Para testar o modo sem risco: [roteiro A1](../roteiros/a1-homologacao.md).

## O que cada agente faz (e não faz)

| Agente | Faz | Não faz |
|---|---|---|
| Orquestrador (sessão principal, `/orquestrar`) | Confere pré-requisitos, chama os agentes na ordem, para na primeira falha, resume | Editar arquivo, rodar git, commitar |
| `executor-haiku` / `-sonnet` / `-opus` | Executa **um** bloco, roda só os testes ligados a ele, atualiza o progresso, grava a mensagem e a marca de testes | Commitar, editar `.claude/` (salvo `.claude/tmp/`), repetir a suíte completa, executar bloco de outro modelo |
| `commitador` | Roda **só** `node .claude/scripts/commit-bloco.mjs --bloco <N> --mensagem .claude/tmp/commit-msg.txt` | Editar arquivo, mexer na mensagem, tentar variações, usar `git` direto |
| `executor-de-testes` (já existente) | Roda a suíte completa no fim e relata | Corrigir código |

Detalhes das definições: [agentes.md](agentes.md). As 9 verificações do script: [hooks.md](hooks.md).

## O que o commit contém

- **Autor e committer:** a identidade do Git já configurada na máquina do responsável. O script nunca define identidade nem usa `--author`.
- **Mensagem:** Conventional Commits em português. Linhas de coautoria e de ferramenta (`Co-Authored-By`, `Claude-Session`, "Generated with", URLs da ferramenta) são **removidas**, e o `settings.json` também zera o atributo de commit. A mensagem não identifica a ferramenta por **decisão do responsável**, num projeto pessoal.
- **Arquivos:** tudo o que o bloco alterou (`git add -A`), depois de recusar arquivos de segredo, dado ou artefato e de varrer o diff com o detector da guarda `varrer-dados`.
- **Nunca há envio remoto.** O responsável revisa a branch e faz o envio e o PR à mão.

## Quando para

| Sinal | Quem dá | O que fazer |
|---|---|---|
| `PARADA: <motivo, arquivo e linha>` | Executor (teste falhou, bloco ambíguo, decisão necessária, guarda bloqueou algo legítimo) | Ler o motivo, resolver (na sessão manual, se preciso) e rodar o mesmo comando |
| `FALHA DO COMMIT: <erro>` | `commitador` (recusa do script: branch, identidade, marca, mensagem, arquivo proibido, dado real) | Corrigir a causa indicada pelo texto e rodar o mesmo comando |
| Suíte completa falhou | `executor-de-testes` no fim | Ver os testes que falharam e o bloco de origem; os commits ficam como estão |
| Guarda bloqueou | Hooks | Não contornar; seguir a orientação da mensagem |

## Como retomar

Rodar de novo o **mesmo comando**: ele lê o `docs/progresso/<id>.md` e continua do primeiro bloco não concluído. Um bloco que falhou **não é repetido sozinho**: o responsável decide.

## Como desfazer um bloco

**Só o responsável.** Revise com `git log --oneline` e `git show <hash>`; use o histórico para voltar ao estado desejado (por exemplo, descartar a branch `auto/*` e recomeçar, ou reverter o commit do bloco). Nenhum agente desfaz commit.

## Limites

- **Sem envio remoto:** nenhum agente publica a branch; o responsável faz.
- **Só branch `auto/*`;** `main` e outras são recusadas.
- **Uma tentativa por bloco;** a suíte completa roda no máximo **uma vez** por orquestração (ou no bloco que a pedir).
- **O script é a única trava** e fica protegido contra escrita dos agentes (R77); a revisão deixa de ser bloco a bloco e passa a ser **no PR** (R78).
- **Custo:** cada executor relê o prompt inteiro; prompts longos consomem mais tokens no modo automático (R78).
