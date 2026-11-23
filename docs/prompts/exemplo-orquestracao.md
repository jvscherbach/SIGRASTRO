# Exemplo de orquestração: três blocos triviais (A1)

> **Forma de trabalho:** prompt **só para testar** o modo automático (A1, D89). Cada bloco cria um arquivo de texto inofensivo em `tools/exemplo/` e termina num ponto de commit. Sessões do Claude Code (**OPUS**, **SONNET** e **HAIKU**) recebem **este mesmo prompt**; cada uma executa **somente o bloco atribuído ao seu modelo**. Num teste real, quem roda é o `/orquestrar`, com o responsável na sessão em Sonnet.

> **Não é uma sprint do projeto.** Não entra em `docs/14`. O progresso fica em `docs/progresso/ex.md`, criado pelo bloco 1.

## Contexto

O objetivo é provar, de ponta a ponta, que o modo automático:

- roda os três modelos, cada um no seu bloco;
- faz um commit por bloco, com os arquivos do bloco, na identidade do Git do responsável, sem coautoria;
- para na primeira falha e retoma de onde parou;
- nunca faz `push`.

Os arquivos criados não têm dado real, não são código e não entram no dossiê. Podem ser apagados depois do teste.

## Regras

- **Nenhum dado real** (CPF, CNPJ, nome de pessoa, número de processo) em nenhum arquivo deste exemplo.
- **Nenhuma dependência nova.** Não instale nada.
- **Não rode `git`** (nem `commit`, `add`, `push`, `branch` ou `stash`). Só `git diff` é permitido, e este bloco não precisa dele.
- **Não edite `.claude/`** (exceto gravar em `.claude/tmp/`).
- Os testes deste exemplo são **não se aplica**: não há código a testar. A marca é `"nao-se-aplica"`.

## Protocolo das três sessões

1. **Identifique a sessão:** a primeira mensagem traz `SESSÃO: HAIKU`, `SESSÃO: SONNET` ou `SESSÃO: OPUS`. Sem essa linha, pare e pergunte.
2. **Descubra onde parou:** leia `docs/progresso/ex.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda:
   `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - crie ou atualize `docs/progresso/ex.md` (no bloco 1, crie com a tabela dos 3 blocos; nos seguintes, marque o bloco como concluído);
   - grave `.claude/tmp/commit-msg.txt` com só a mensagem de commit sugerida do bloco, **sem nenhuma linha de coautoria ou de ferramenta**;
   - grave `.claude/tmp/bloco-<N>.json` com `{"bloco": N, "testes": "nao-se-aplica", "resumo": "<uma frase>"}`;
   - rode `git diff --stat` (só essa vez, para o relatório) e responda:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET|HAIKU>): <título>
Testes: não se aplica
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <mensagem do bloco>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET|HAIKU>.
```

5. **Pare.** Não faça mais nada depois do relatório.

**Parada:** se o bloco não puder ser feito (por exemplo, o arquivo que ele confere não existe, ou existe o arquivo `.claude/tmp/parar-exemplo`), devolva `PARADA: <motivo, arquivo>`, não grave nenhuma marca e não crie nenhum arquivo do bloco.

Tipos de commit usados: `chore` (este exemplo não altera código).

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Cria `ola-haiku.txt` | **HAIKU** | Arquivo pequeno e mecânico; abre o progresso |
| 2 | Cria `ola-sonnet.txt` | **SONNET** | Confere o bloco 1 antes de seguir |
| 3 | Cria `resumo.md` | **OPUS** | Confere os dois blocos anteriores e lista os arquivos |

---

## Bloco 1 (HAIKU): cria `ola-haiku.txt`

**Commit sugerido:** `chore(exemplo): cria ola-haiku.txt do exemplo de orquestração`

1. Se existir o arquivo `.claude/tmp/parar-exemplo`, **pare** com `PARADA: arquivo .claude/tmp/parar-exemplo presente (roteiro de homologação, teste de parada)`, sem criar nada.
2. Crie a pasta `tools/exemplo/` (se não existir) e o arquivo `tools/exemplo/ola-haiku.txt` com **uma frase** de cumprimento, por exemplo: `Olá, este arquivo foi criado pelo bloco 1 (HAIKU).`
3. Crie `docs/progresso/ex.md` com esta tabela e marque o bloco 1:

```
# Progresso do exemplo de orquestração (ex)

| Bloco | Título | Sessão | Estado |
|---|---|---|---|
| 1 | Cria ola-haiku.txt | HAIKU | [x] concluído |
| 2 | Cria ola-sonnet.txt | SONNET | [ ] pendente |
| 3 | Cria resumo.md | OPUS | [ ] pendente |
```

4. Siga o protocolo (passo 4 e 5).

## Bloco 2 (SONNET): cria `ola-sonnet.txt`

**Commit sugerido:** `chore(exemplo): cria ola-sonnet.txt do exemplo de orquestração`

1. Confira que `tools/exemplo/ola-haiku.txt` existe e tem conteúdo. Se não, **pare** com `PARADA: tools/exemplo/ola-haiku.txt ausente (bloco 1 não concluído)`.
2. Crie `tools/exemplo/ola-sonnet.txt` com **uma frase** de cumprimento, por exemplo: `Olá, este arquivo foi criado pelo bloco 2 (SONNET).`
3. Em `docs/progresso/ex.md`, marque o bloco 2 como `[x] concluído`.
4. Siga o protocolo (passo 4 e 5).

## Bloco 3 (OPUS): cria `resumo.md`

**Commit sugerido:** `chore(exemplo): cria resumo.md listando os arquivos do exemplo`

1. Confira que `tools/exemplo/ola-haiku.txt` e `tools/exemplo/ola-sonnet.txt` existem. Se algum faltar, **pare** com `PARADA: <arquivo> ausente`.
2. Crie `tools/exemplo/resumo.md` com um título e uma lista com os dois nomes de arquivo (`ola-haiku.txt` e `ola-sonnet.txt`), sem nenhum outro conteúdo.
3. Em `docs/progresso/ex.md`, marque o bloco 3 como `[x] concluído`.
4. Siga o protocolo (passo 4 e 5). Como é o último bloco, o relatório também diz: `FIM DO EXEMPLO: os três blocos estão concluídos.` no lugar da linha `PRÓXIMO BLOCO`.
