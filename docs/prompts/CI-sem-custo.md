# Pipeline sem custo: remover o CodeQL e tudo o que depende de plano pago

> **Forma de trabalho:** 2 blocos, cada um terminando em um **ponto de commit**. Duas sessões do Claude Code (**OPUS** e **SONNET**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**.

## Contexto

O SIGA Vistas é um **projeto pessoal do responsável, em fase de protótipo, sem orçamento e sem vínculo institucional por enquanto**. **Nada pago pode entrar no projeto**: nem recurso do GitHub que exija plano pago, nem serviço, nem licença, nem ferramenta com cota paga. O repositório pode ser **privado no plano gratuito** do GitHub; trabalhe com essa hipótese pior.

O `codeql.yml` (bloco 4 do prompt da pipeline) usa code scanning, que em repositório privado exige GitHub Code Security (pago). Ele deve sair. Esta tarefa também confere se mais alguma coisa depende de plano pago.

**Regra permanente (registre em `CLAUDE.md`):** projeto pessoal sem orçamento; antes de propor ou usar qualquer recurso, confirme que ele é gratuito para repositório privado no plano gratuito e, se não tiver certeza, **não use** e registre a dúvida em `docs/10`.

## Regras

- **Você não executa nada contra o SIGA real nem contra a API de arquivos real.** Nenhuma credencial na pipeline.
- Nunca abra, imprima ou copie o `.env.local`.
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.
- Sem URL institucional, nome de servidor ou dado real em arquivos.

## Protocolo das duas sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS` ou `SESSÃO: SONNET`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/ci-sem-custo.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:** deixe o repositório íntegro; marque o bloco em `docs/progresso/ci-sem-custo.md` (crie no bloco 1, com a tabela dos 2 blocos); rode `git diff --stat`; responda:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET>): <título>
Validação: <o que rodou e o resultado>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET>  (ou "tarefa concluída").
```

5. **Pare.** Mesma sessão no próximo bloco: espere "continuar". Outra sessão: a atual termina.

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Remover o CodeQL e corrigir o selo do CI | **SONNET** | Remoção localizada |
| 2 | Auditoria de custo da pipeline e dos docs | **OPUS** | Exige julgar o que é gratuito para repositório privado |

---

## Bloco 1 (SONNET): remover o CodeQL e corrigir o selo

**Commit sugerido:** `ci: remove o CodeQL, que exige plano pago em repositório privado`

- Apague `.github/workflows/codeql.yml`.
- Em tudo o que o citava, remova ou reescreva: `docs/13-padroes-tecnicos.md` (seção "Integração contínua", lista de workflows, tutorial do GitHub e a nota sobre o GitHub Code Security), `docs/09` (D51), `docs/10` (pendências e riscos ligados a ele), `docs/progresso/ci-github-actions.md` (acrescente uma nota "CodeQL removido na tarefa ci-sem-custo; motivo: plano pago") e `README.md`.
- Os checks obrigatórios da `main` passam a ser **só**: `api`, `front`, `e2e`, `docker` e `compose`. Garanta que nenhum texto mencione CodeQL como check.
- **README:** o selo do CI está parcialmente descomentado e deixou uma linha de instrução solta no topo ("Selo do CI: descomente e troque …"). Apague essa linha e deixe **apenas** o selo, com `jvscherbach/siga-vistas`: `[![CI](https://github.com/jvscherbach/siga-vistas/actions/workflows/ci.yml/badge.svg)](https://github.com/jvscherbach/siga-vistas/actions/workflows/ci.yml)`.
- `CLAUDE.md`: acrescente a regra permanente "projeto pessoal sem orçamento; nada pago" (texto da seção Contexto).
- **Validação:** `grep -ri codeql` em `.github`, `docs`, `README.md` e `CLAUDE.md` só pode achar a nota histórica no arquivo de progresso e uma decisão em `docs/09` explicando a remoção; YAMLs restantes válidos; o texto do README não tem mais `<DONO>`.

## Bloco 2 (OPUS): auditoria de custo

**Commit sugerido:** `docs(ci): alinha pipeline e tutorial a repositório privado no plano gratuito`

Revise `.github/`, os Dockerfiles, `docs/13` (tutorial do GitHub), `docs/09` e `docs/10` partindo de **repositório privado no plano GitHub Free** e responda, para cada item, "gratuito", "limitado" ou "pago", **sem inventar**: se não tiver certeza, trate como "não confirmado" e verifique na documentação oficial do GitHub (se não conseguir, registre a dúvida).

Itens a conferir (no mínimo):

- **Regras de proteção da `main` e rulesets em repositório privado** (o tutorial manda exigir checks e revisão): confirme se existem no plano gratuito privado. Se não existirem, reescreva o tutorial: sem proteção, a disciplina é o fluxo (sempre PR, nunca push direto na `main`), e os checks continuam rodando e visíveis no PR. Registre a alternativa de tornar o repositório público (CodeQL e proteção passam a ser gratuitos), **como opção que depende do R27 (varredura do histórico)**, sem recomendá-la.
- **Minutos do GitHub Actions** em repositório privado: confirme a cota gratuita e estime o consumo da pipeline (jobs `api` com Testcontainers, `e2e`, `docker`, em PR e na `main`). Se o consumo por execução for alto, proponha e aplique reduções **gratuitas** (por exemplo cancelar execuções antigas, não rodar `docker` em PR que só muda `docs/`, com `paths-ignore`, mantendo os checks obrigatórios coerentes: check que não roda por filtro de caminho trava o PR, então só use filtro se resolver isso e explique).
- **Cache do Actions** (`type=gha`): confirme o limite gratuito e relate.
- **GHCR em repositório privado:** armazenamento e transferência gratuitos e seus limites; relate o risco de estourar com `sbom` e `provenance` por tag e proponha a retenção (por exemplo apagar versões antigas) como passo manual do tutorial, sem custo.
- **Dependabot:** confirme que alertas e atualizações de versão são gratuitos em repositório privado.
- **Secret scanning e push protection** em repositório privado: confirme o que é gratuito. Se for pago, remova do tutorial e coloque no lugar uma alternativa **gratuita e local**, por exemplo documentar uma varredura manual do repositório com ferramenta de código aberto (como `gitleaks`) antes de tornar algo público; **não adicione a ferramenta à pipeline**, só descreva o comando no tutorial.
- **Environments com aprovação manual** em repositório privado: confirme; o `release.yml` não usa Environment hoje, então só relate.
- **Ferramentas no `ci.yml`, `release.yml` e Dockerfiles:** confirme que todas são de código aberto e sem cota paga (`actions/*`, `docker/*`, `npm audit`, Maven).

**Entregas:**

1. Tabela "Recurso × situação no plano gratuito privado × decisão" em `docs/13`, e o tutorial reescrito apenas com o que é gratuito.
2. `docs/10`: riscos ou pendências novas (limites de minutos, de cache e do GHCR; ausência de proteção da `main`, se confirmada; secret scanning).
3. `docs/09`: decisão "projeto pessoal sem orçamento: nada pago".
4. Diga no resumo final o que **não** foi possível confirmar.

**Validação:** YAMLs válidos; nenhum recurso pago restou nos workflows e nos docs; tutorial executável no plano gratuito.

## Critérios de aceite

1. `codeql.yml` removido; nenhum texto o trata como check.
2. Selo do CI correto e sem linha de instrução solta no `README.md`.
3. `CLAUDE.md` com a regra "nada pago".
4. Tutorial do GitHub e `docs/13` só com o que é gratuito para repositório privado no plano gratuito, com as dúvidas registradas.
5. Cada bloco foi executado pela sessão atribuída e marcado em `docs/progresso/ci-sem-custo.md`.
6. Nenhum comando git além de `git diff`; nenhum dado real no repositório.
