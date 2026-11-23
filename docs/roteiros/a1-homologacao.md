# Roteiro de homologação do modo automático (A1)

> Roteiro **para o responsável** rodar. A sessão do Claude Code que escreveu este arquivo **não** executa nenhum passo daqui (não roda `git` além de `git diff`, não cria branch e não chama `/orquestrar`).

Pré-requisito: o bloco 6 da A1 está commitado (ou revisado) e o prompt de exemplo está em `docs/prompts/exemplo-orquestracao.md`. O marcador `~/.rastro/liberar-construcao-claude` precisa estar **ausente** (ele só existe para construir as ferramentas do `.claude/`).

## Passo 1: criar a branch de teste (comando seu)

```
git switch -c auto/teste-orquestracao
```

Confira que a árvore está limpa (`git status`). Se houver mudança pendente, commite ou descarte antes.

## Passo 2: rodar a orquestração em Sonnet

Abra o Claude Code **em Sonnet**, na raiz do repositório, e envie:

```
/orquestrar docs/prompts/exemplo-orquestracao.md
```

Resultado esperado: a orquestração executa o bloco 1 (HAIKU), o bloco 2 (SONNET) e o bloco 3 (OPUS), chamando o `commitador` depois de cada um. Não há suíte completa (o exemplo só tem testes "não se aplica"), e a orquestração termina com a tabela `bloco | título | modelo | hash | situação`.

## Passo 3: conferir o resultado

- [ ] **3 commits** na branch (`git log --oneline main..HEAD`), um por bloco, com a mensagem `chore(exemplo): ...`.
- [ ] Cada commit tem **só os arquivos do seu bloco**:
  - bloco 1: `tools/exemplo/ola-haiku.txt` e `docs/progresso/ex.md`;
  - bloco 2: `tools/exemplo/ola-sonnet.txt` (e a marcação em `docs/progresso/ex.md`);
  - bloco 3: `tools/exemplo/resumo.md` (e a marcação em `docs/progresso/ex.md`).
- [ ] `git log --format='%an <%ae>%n%B'` mostra **a sua identidade** (nome e e-mail do Git configurado na máquina) e **nenhuma** linha `Co-Authored-By`, `Claude-Session`, `Signed-off-by` com Claude, "Generated with" nem menção ao Claude.
- [ ] A orquestração informou os modelos usados: **Haiku, Sonnet e Opus**, nessa ordem.
- [ ] **Nenhum push** foi feito: `git status` mostra a branch sem `ahead` de remoto; você não rodou `git push`, e o `commit-bloco` não contém `push`.
- [ ] `.claude/tmp/commit-msg.txt` e `.claude/tmp/bloco-N.json` foram apagados pelo script depois de cada commit.

Depois de conferido, você pode apagar `tools/exemplo/` (é só exemplo) junto com a branch, no passo 5.

## Passo 4: testes de recusa

Faça cada teste **numa branch de teste**, e volte a `auto/teste-orquestracao` ou `main` conforme o caso.

**(a) Recusa em `main`.** Com `git switch main`, rode `/orquestrar docs/prompts/exemplo-orquestracao.md`.
- Esperado: a orquestração **recusa** na pré-checagem da branch, não chama nenhum executor e diz como criar a branch (`git switch -c auto/<nome>`). Nada é criado nem commitado.

**(b) Recusa de arquivo de segredo.** Em `auto/teste-orquestracao`, crie um `.env.local` **falso** na raiz (conteúdo só de teste, sem valor real; nunca o abra depois) e force a inclusão, como você faria à mão:

```
git add -f .env.local
```

- Faça isso **antes** de qualquer bloco do exemplo ter sido commitado (por exemplo, numa branch nova a partir de `main`, sem `docs/progresso/ex.md`). Rode `/orquestrar` em seguida.
- Esperado: o bloco 1 é executado, e o `commit-bloco` **recusa** com a verificação 6 (arquivo de segredo) e **não commita**. Depois, `git rm --cached .env.local` e apague o arquivo.
- **Atenção (achado desta sprint):** `.env.local` está no `.gitignore`. Um arquivo **ignorado** não aparece em `git status --porcelain`, então o script **não** o vê, e o commit segue sem ele. Isso é seguro (o arquivo ignorado não entra no commit), mas **não é a recusa** que o prompt da A1 descreve. A recusa só acontece quando o arquivo está **não rastreado ou alterado** (como no `git add -f` acima). Confirme o comportamento real neste passo e registre o resultado em `docs/progresso/a1.md`.

**(c) Parada de bloco (falha forçada).** Antes de rodar, crie o arquivo `.claude/tmp/parar-exemplo` (vazio; a pasta `.claude/tmp/` é ignorada pelo Git, então a árvore continua limpa). Em `auto/teste-orquestracao`, rode `/orquestrar docs/prompts/exemplo-orquestracao.md`.
- Esperado: o bloco 1 (HAIKU) devolve `PARADA: arquivo .claude/tmp/parar-exemplo presente`; a orquestração **para** sem chamar o `commitador`; **nenhum commit** é feito; nenhum arquivo do bloco é criado.
- Depois: apague `.claude/tmp/parar-exemplo` e rode o mesmo comando de novo. A retomada deve começar no bloco 1 (que ainda não foi concluído), sem repetir nada.
- Observação: o teste (c) força a parada pelo caminho de `PARADA` do executor, que é a falha que o orquestrador trata. Um teste de **teste direcionado que falha** de verdade exigiria um prompt com código; fica para uma sprint futura.

## Passo 5: limpeza (comandos seus)

```
git switch main
git branch -D auto/teste-orquestracao
```

Se você criou branches extras nos testes de recusa, apague-as do mesmo jeito. Não há push a desfazer, porque nada foi publicado. Se quiser apagar o exemplo do repositório, faça isso num commit seu, depois de revisar.

## Critérios para dar a A1 como homologada

- Passo 3 inteiro conferido.
- Os três testes de recusa (a), (b) e (c) se comportaram como descrito, com o resultado de (b) registrado em `docs/progresso/a1.md`.
- Nenhum push feito.
