---
description: Verifica uma sprint do Rastro com três subagentes em paralelo (segurança, testes, documentação) e consolida o relatório em docs/progresso/verificacao-s<N>.md
argument-hint: <número da sprint>
---

Você é o orquestrador da verificação da **sprint $ARGUMENTS** do Rastro. Esta verificação **não corrige código, não faz commit e não roda nada fora das guardas dos hooks** (nenhum `git` além de `git diff`, nada de `.env.local`, nada de `down -v`). O único arquivo que você escreve é o relatório.

Se `$ARGUMENTS` estiver vazio ou não for um número, pare e peça o número da sprint.

## 1. Contexto

1. Leia `docs/progresso/s$ARGUMENTS.md` (tabela de blocos e notas). Se não existir, pare e diga que a sprint não tem progresso registrado.
2. Rode `git diff --name-only` e `git diff --stat`. Se a árvore estiver limpa (os blocos já commitados), rode `git diff main...HEAD --name-only` e `git diff main...HEAD --stat` para pegar o que a branch trouxe. Anote qual das duas bases usou. Se a lista contra a `main` passar de uns 150 arquivos (branch longa), não a passe inteira: limite o escopo às áreas que `docs/progresso/s$ARGUMENTS.md` cita como entregues e diga isso no relatório.
3. Monte um resumo curto: número da sprint, blocos concluídos, lista de arquivos alterados (nomes, sem o conteúdo).

## 2. Três subagentes em paralelo

Dispare **os três ao mesmo tempo**, numa única mensagem com três chamadas à ferramenta `Agent`, cada uma com o resumo do passo 1 no prompt:

- `revisor-seguranca`: revisar os arquivos alterados contra as regras permanentes.
- `executor-de-testes`: rodar os testes do jeito documentado e devolver só o resumo.
- `atualizador-de-docs`: conferir `docs/` contra o código e corrigir divergências só em `docs/`.

Cada prompt deve repetir: sprint, lista de arquivos, que as regras do projeto valem (só `git diff`, nada de `.env.local`) e o formato de resposta pedido na definição do agente. Aguarde os três.

## 3. Consolidar

Escreva `docs/progresso/verificacao-s$ARGUMENTS.md` em português, com:

- cabeçalho: sprint, data de hoje (fuso de Cuiabá), base do `git diff` usada, modelo de cada agente se souber;
- três seções, **Segurança**, **Testes** e **Documentação**, cada uma com **Status** (`OK`, `ATENÇÃO` ou `FALHA`, o do agente, sem suavizar), **Achados** (o que o agente relatou, resumido, sem copiar valor sensível) e **Próximo passo** (uma ação concreta e quem a faz);
- na seção Documentação, a lista do que o agente corrigiu em `docs/`;
- ao final, **Limites desta verificação**: o que nenhum agente conseguiu conferir (homologação com dados reais, CI no GitHub, etc.).

Não reescreva um achado para parecer melhor nem junte `FALHA` com `OK`. Se um agente falhar por erro de formato ou ferramenta, diga isso na seção dele com status `ATENÇÃO` e o motivo.

## 4. Resposta final

Responda só com uma tabela:

| Área | Status | Resumo | Próximo passo |
|---|---|---|---|

e o caminho do relatório. Se o `atualizador-de-docs` alterou arquivos, eles aparecem em `git diff` para o responsável revisar e commitar.
