# Progresso: pipeline sem custo

Escopo: remover o CodeQL e tudo o que depende de plano pago (projeto pessoal sem orçamento, D52).

| Bloco | Título | Modelo | Estado |
|---|---|---|---|
| 1 | Remover o CodeQL e corrigir o selo do CI | SONNET | [x] concluído |
| 2 | Auditoria de custo da pipeline e dos docs | OPUS | [x] concluído |

## Notas

- Bloco 1: `codeql.yml` apagado; textos de `docs/09` (D51 reescrita, D52 nova), `docs/13` e `docs/progresso/ci-github-actions.md` ajustados; regra "nada pago" no `CLAUDE.md`. O selo do CI do `README.md` já estava correto (só o selo, com `jvscherbach/siga-vistas`, sem linha de instrução e sem `<DONO>`). Os textos de `docs/13` e `docs/10` que citam `github/*` como actions permitidas (só o CodeQL as usava) ficam para o bloco 2.
- Bloco 2 (auditoria de custo, conferida na documentação oficial do GitHub em 26/10/2026): tabela "Recurso × situação × decisão", estimativa de minutos e tutorial reescrito em `docs/13`; D53 em `docs/09`; P24 revista, P27 a P29 e R28 a R32 em `docs/10`; R27 passa a citar a varredura local com `gitleaks`. Pagos em privado no Free: proteção de branch, secret scanning e push protection, Environments, atestados. Gratuitos: Dependabot, configurações do Actions, cache até 10 GB, GHCR (por enquanto). Limitados: 2.000 min/mês em runner de 2 vCPU, 500 MB de artefatos e Packages. Reduções aplicadas: job `mudancas` no `ci.yml` (só documentação pula `api`, `front`, `e2e` e `docker` por `if`, sem `paths-ignore`) e Dependabot mensal. Não confirmado: rulesets no Free privado (P27), efeito de orçamento de US$ 0 com forma de pagamento (P28), versões sem tag no GHCR (P29), limites do Docker Hub (R31) e o consumo real de minutos (estimativa).

## Fechamento

1. Bloco 1 (SONNET): `codeql.yml` removido, selo do CI e regra "nada pago" no `CLAUDE.md` (D52).
2. Bloco 2 (OPUS): auditoria de custo, economia de minutos e tutorial só com recursos gratuitos (D53).

Depois do commit, o responsável segue o tutorial de `docs/13` e confere na primeira semana o uso real de minutos (R28).
