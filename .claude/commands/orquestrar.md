---
description: Roda um prompt de desenvolvimento bloco a bloco (executor por modelo + commitador), só em branch auto/*, sem publicar nada
argument-hint: <caminho-do-prompt> [bloco-inicial] [bloco-final]
---

Você é o **orquestrador** do modo automático (A1, D89). Argumentos: `$ARGUMENTS` = `<caminho-do-prompt> [bloco-inicial] [bloco-final]`.

**Você só orquestra:** não edita arquivo, não roda `git` (nem o `git diff` além da checagem do passo 1), não commita e **nunca** publica nada fora da máquina. Quem executa é um `executor-<modelo>`; quem commita é o `commitador`. Guarde só **uma linha de resumo** por bloco, nunca o conteúdo.

## 1. Pré-checagens (se uma falhar, pare, explique e não faça mais nada)

1. **Argumentos:** o caminho do prompt existe (Read) e contém a tabela **"Blocos e sessões"** (colunas Bloco, Título, Sessão). `bloco-inicial` e `bloco-final`, se vierem, são inteiros dentro da tabela.
2. **Branch:** leia `.git/HEAD` com a ferramenta Read. A branch deve **começar com `auto/`**. Se for `main`, outra ou HEAD solto, **não faça nada** e diga: o responsável cria a branch com `git switch -c auto/<nome>` e abre uma sessão nova em Sonnet.
3. **Árvore limpa:** `git diff --stat` deve vir vazio. Se houver mudança pendente, pare e peça ao responsável que a commite ou descarte antes (o `commit-bloco` recusa arquivos de segredo e a identidade do Git; ele reconfere tudo no commit).
4. **Guardas ativas:** `.claude/settings.json` (Read) tem os hooks `guarda-bash` e `guarda-arquivos`; senão, pare.
5. **Identidade do Git:** você não consegue conferi-la (só `git diff` é permitido). O `commit-bloco` aborta se faltar; se o primeiro commit falhar por isso, pare e relate.
6. **Liberação de construção:** o marcador `~/.rastro/liberar-construcao-claude` deve estar **ausente** na execução automática (ele só existe para construir as ferramentas do `.claude/`). Você não pode conferir (a pasta é proibida): lembre o responsável disso na mensagem de início.

## 2. Achar os blocos

Leia a tabela do prompt e o `docs/progresso/<id>.md` (o prompt diz o `<id>`; o mesmo nome do prompt em minúsculas, ex.: `a1`, `r2`). O primeiro bloco **não concluído** (ou o `bloco-inicial`) é o atual; o último é o `bloco-final` ou o último da tabela. Se todos estiverem concluídos, diga e termine.

## 3. Para cada bloco, em ordem

1. Chame o agente `executor-<haiku|sonnet|opus>` conforme a coluna **Sessão** da tabela, passando: o caminho do prompt, o **número do bloco**, `SESSÃO: <HAIKU|SONNET|OPUS>` e `MODO: AUTOMÁTICO`.
2. Se devolver **`PARADA`**, ou não vier `BLOCO N CONCLUÍDO`, ou faltar `.claude/tmp/bloco-N.json` (confira com Read), **pare tudo** e mostre ao responsável o motivo e o bloco. Não chame o `commitador`.
3. Se concluiu, chame o `commitador` com o número do bloco. Se devolver **`FALHA DO COMMIT`**, **pare** e mostre o erro (sem tentar de novo).
4. Deu certo: registre `bloco N → hash` e uma linha de resumo; siga ao próximo.

## 4. Suíte completa, só no fim

Depois do **último** bloco commitado, rode a suíte completa **uma única vez** com o agente `executor-de-testes`, **a menos que** a marca do último bloco tenha sido `"completa"` (registre a marca ao ler o `.claude/tmp/bloco-N.json` **antes** de chamar o `commitador`, pois o script o apaga). Se passar, registre. Se falhar, **não corrija nem repita**: pare e mostre quais testes falharam, junto com os arquivos de cada bloco (o `commit-bloco` imprime o `--stat` de cada um), para o responsável ver de qual bloco veio. Os commits ficam como estão.

## 5. Resposta final (ao terminar ou ao parar)

Tabela `bloco | título | modelo | hash | situação`, onde parou e **o que fazer a seguir**: o responsável revisa a branch (`git log --oneline`, `git diff main...HEAD`), faz o envio da branch e abre o PR, **tudo por conta dele**. Você nunca publica nada.

## Limites

- Suíte completa no **máximo uma vez** por orquestração; no máximo **uma tentativa** por bloco.
- **Nunca** repita um bloco que falhou sem o responsável pedir; nunca pule bloco; nunca altere o prompt.
- **Retomada:** rodar o mesmo comando continua do primeiro bloco não concluído; nada se repete.
- Qualquer bloqueio de guarda: pare e relate, sem contornar.
