# Progresso: pipeline sem custo

Escopo: remover o CodeQL e tudo o que depende de plano pago (projeto pessoal sem orçamento, D52).

| Bloco | Título | Modelo | Estado |
|---|---|---|---|
| 1 | Remover o CodeQL e corrigir o selo do CI | SONNET | [x] concluído |
| 2 | Auditoria de custo da pipeline e dos docs | OPUS | [ ] pendente |

## Notas

- Bloco 1: `codeql.yml` apagado; textos de `docs/09` (D51 reescrita, D52 nova), `docs/13` e `docs/progresso/ci-github-actions.md` ajustados; regra "nada pago" no `CLAUDE.md`. O selo do CI do `README.md` já estava correto (só o selo, com `jvscherbach/siga-vistas`, sem linha de instrução e sem `<DONO>`). Os textos de `docs/13` e `docs/10` que citam `github/*` como actions permitidas (só o CodeQL as usava) ficam para o bloco 2.
