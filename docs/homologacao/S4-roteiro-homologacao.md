# Roteiro de homologação da S4 — regras de acesso, geometrias, documentos pessoais (SCORP) e "Dossiês gerados"

**Quem executa:** o responsável pelo projeto, manualmente, com dados reais de uma base de **desenvolvimento ou homologação**. O desenvolvimento (Claude Code) **nunca** se conecta ao banco real do SIGA, à API de arquivos nem ao SCORP (D27, D34). Tudo o que foi provado até aqui usa dados e arquivos **fictícios e sintéticos**: o SCORP e a API de arquivos foram simulados por um servidor HTTP local, e a matriz de perfis foi provada por MockMvc (`MatrizDeAcessoTest`, 153 cenários, R48). Este roteiro é a primeira prova com o SCORP e com geometrias **reais**.

**O que a S4 entrega e este roteiro confere:**

- menu e telas por perfil (RN08);
- matriz de acesso no back-end para os três perfis (RN07, RF20, RNF10);
- geometrias numa seção própria, com ZIP "tudo ou nada" e as sigilosas omitidas para o Operador (RN10);
- documentos pessoais do SCORP (origem "Pessoal") na lista e no dossiê, com falha tolerante;
- a tela "Dossiês gerados".

Este roteiro também fecha ou mantém as pendências **P06** (autenticação do SCORP), **P21** (diferença de documentos com a Vista do SIGA) e **P30** (sigilo padrão do SCORP).

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em "Validação da S4" de [`docs/14`](../14-plano-de-sprints.md), preencha [`docs/casos-de-teste.md`](../casos-de-teste.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que não bater.

> **Dados pessoais e arquivos reais:** neste roteiro, CPF, CNPJ, identificadores e números de processo ficam como marcadores (`<CPF_DE_TESTE>`, `<CNPJ_DE_TESTE>`, `<ID_DO_REQUERIMENTO>`). **Nenhum arquivo real, ZIP de geometrias ou PDF gerado a partir deles vai para o repositório.** Amostras locais, se precisar, ficam em `amostras-locais/` (no `.gitignore`). Antes de qualquer commit, rode `git status` e confira que nada dela aparece. Ao anotar resultados, registre **contagens e tipos**, nunca nome de pessoa, de arquivo ou chave.

> **Uso do `.env.local`:** os passos pedem para você **editar** o `.env.local` (variáveis `SCORP_*`). Faça isso você mesmo, sem colar o conteúdo do arquivo em lugar nenhum. Linhas do `.env.local` não podem começar com espaço. Os comandos abaixo só dizem se uma variável está "preenchida" ou "vazia", sem mostrar o valor.

## 1. Compose local e menu por perfil (RN08)

O menu não depende de sistema externo. Suba primeiro **com** o `compose.verificacao.yaml`, que esvazia as variáveis externas, e confira dentro do container que elas estão vazias:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d --build
```

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml exec -T api sh -c 'for v in SIGA_DB_URL LICENCIAMENTO_API_URL SCORP_API_URL SCORP_API_TOKEN_FILE; do eval "x=\${$v}"; [ -z "$x" ] && echo "$v vazia" || echo "$v PREENCHIDA"; done'
```

Resultado esperado: as quatro linhas com `vazia`. Abra `http://localhost:8080` e entre com cada usuário de demonstração (um clique na tela de login):

| Usuário | Itens do menu esperados | OK? |
|---|---|---|
| `operador.demo` (Operador) | Consulta, Dossiês gerados, Auditoria › Documentos | [ ] |
| `gestor.demo` (Gestor) | Consulta, Dossiês gerados, Auditoria › Documentos | [ ] |
| `admin.demo` (Administrador) | os anteriores mais Auditoria › Acessos e Configurações | [ ] |

Para cada um, confira também:

| Item | Esperado | OK? |
|---|---|---|
| Sem cadeado | nenhum item do menu tem cadeado; o que o perfil não usa simplesmente não aparece | [ ] |
| URL bloqueada | como Operador e como Gestor, digite `http://localhost:8080/configuracoes` e depois `/auditoria/acessos`: aparece "Seu perfil não tem permissão para acessar esta área." e a tela volta à Consulta. Em DevTools › Rede, nenhuma chamada a `/api` além de `/api/auth/me` | [ ] |
| Administrador | abre Configurações e Auditoria › Acessos, ainda "Em construção" (S5) | [ ] |
| Troca de usuário | **Sair** e entrar com outro usuário: o menu muda sem recarregar a página | [ ] |
| Sem sessão | em outra aba anônima, `http://localhost:8080/dossies` leva ao login | [ ] |
| Quadro do topo | a altura do quadro da Consulta é a mesma nos três perfis (o aviso do Operador ocupa espaço reservado) | [ ] |

Derrube os containers **sem** `-v` antes do passo 2:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml down
```

- [ ] OK / [ ] Falhou — observação:

## 2. SCORP: consulta de pessoas, teste de fumaça e autenticação (P06)

### 2.1 Validar a consulta de pessoas no banco real

A consulta nova (`api/src/main/resources/sql/siga/documentos/pessoas-do-requerimento.sql`) devolve, por requerimento, os `ID_CORPORATIVO` **distintos** do interessado, dos representantes legais e dos responsáveis técnicos, com o papel. Rode a mesma consulta no SQL Developer, numa sessão **somente leitura**, para um requerimento de teste que tenha representante legal e responsável técnico (substitua `<ID_DO_REQUERIMENTO>`, valor definido só por você):

```sql
SELECT 'INTERESSADO' AS PAPEL, p.ID_CORPORATIVO
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
JOIN   SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA
WHERE  r.ID = <ID_DO_REQUERIMENTO>
AND    p.ID_CORPORATIVO IS NOT NULL
UNION
SELECT 'REPRESENTANTE_LEGAL' AS PAPEL, p.ID_CORPORATIVO
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO_REPRESENTANTE_LEGAL rl
JOIN   SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = rl.ID_PESSOA
WHERE  rl.ID_REQUERIMENTO = <ID_DO_REQUERIMENTO>
AND    p.ID_CORPORATIVO IS NOT NULL
UNION
SELECT 'RESPONSAVEL_TECNICO' AS PAPEL, p.ID_CORPORATIVO
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO_RESPONSAVEL_TECNICO rt
JOIN   SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = rt.ID_PESSOA
WHERE  rt.ID_REQUERIMENTO = <ID_DO_REQUERIMENTO>
AND    p.ID_CORPORATIVO IS NOT NULL;
```

Resultado esperado:

- uma linha por **papel e pessoa**: o interessado uma vez, mais uma linha por representante legal e por responsável técnico;
- sem `ID_CORPORATIVO` nulo;
- a mesma pessoa em dois papéis aparece em duas linhas, e o serviço faz uma chamada só para ela.

Compare a quantidade de pessoas com o que a tela do SIGA mostra para o requerimento (interessado, representantes e responsáveis técnicos). Se houver pessoa cadastrada **sem** `ID_CORPORATIVO`, ela fica fora (anote quantas: é o caso do R22). Anote **só as contagens**, nunca o identificador.

- [ ] OK / [ ] Falhou — observação:

### 2.2 Teste de fumaça do SCORP (`ScorpFumacaTest`)

O teste lê as pessoas do requerimento no SIGA (somente leitura) e consulta o SCORP com a mesma configuração da aplicação. Ele imprime **só contagens**: pessoas por papel, quantas responderam, documentos por situação e por extensão. Nunca imprime identificador, nome, chave, nome de arquivo nem o token. Preencha as variáveis com os valores do seu `.env.local`, sem colá-los em outro lugar; `SCORP_API_TOKEN_FILE` é opcional (sem ela, vale o token da API de arquivos):

```bash
SCORP_SMOKE=true SCORP_SMOKE_REQUERIMENTO=<ID_DO_REQUERIMENTO> SIGA_DB_URL=... SIGA_DB_USER=... SIGA_DB_PASSWORD=... SCORP_API_URL=... LICENCIAMENTO_API_TOKEN_FILE=... ./mvnw -Dtest=ScorpFumacaTest test
```

Resultado esperado (linhas `[scorp-fumaca]` na saída):

| Item | Esperado | Anotado | OK? |
|---|---|---|---|
| Pessoas por papel | igual à consulta do 2.1 | | [ ] |
| Responderam / falharam | todas responderam, 0 falharam | | [ ] |
| Documentos por situação | maioria `VIGENTE`; `SUBSTITUIDO` com `dataFim`; `INDISPONIVEL` com chave `"0"` ou vazia | | [ ] |
| Documentos por extensão | PDFs e imagens; anote se aparecer extensão inesperada | | [ ] |

**Autenticação (P06):** se **todas** as pessoas falharem e o log mostrar HTTP 401 ou 403, o token da API de arquivos **não** vale no SCORP. Nesse caso:

1. confira no navegador, numa sessão do SIGA, qual credencial a tela do SCORP usa (cabeçalho `Authorization` ou cookie, em DevTools › Rede, **sem copiar o valor**);
2. ajuste no `.env.local` `SCORP_API_AUTH_MODE` (`bearer` ou `cookie`), `SCORP_API_COOKIE_NAME` (só o nome do cookie) e `SCORP_API_TOKEN_FILE` (um arquivo à parte com o token do SCORP, fora do repositório);
3. rode o teste de novo, acrescentando `SCORP_API_TOKEN_FILE=... SCORP_API_AUTH_MODE=...` ao comando.

Se **algumas** pessoas falharem com 404, é provável que o SCORP responda 404 para pessoa sem documentos (NÃO CONFIRMADO em docs/05). Confira essa pessoa na tela do SCORP do SIGA: se ela não tiver documentos lá, anote. Nesse caso, a tela do SIGA Vistas mostra sempre o aviso "parcial" para requerimentos com essa pessoa, e isso vira ajuste para uma próxima sprint.

| Resultado | Anote |
|---|---|
| Autenticação com o token da API de arquivos funciona? | [ ] sim / [ ] não, precisou de `SCORP_API_*`: modo ______ |
| Formato das datas reconhecido (nenhum documento com data vazia sem motivo)? | [ ] sim / [ ] não |
| 404 para pessoa sem documentos? | [ ] não acontece / [ ] acontece |

Registre a conclusão em [`docs/05`](../05-integracoes.md) (seção SCORP: troque **NÃO CONFIRMADO** pelo que foi visto) e em P06 de [`docs/10`](../10-pendencias-e-riscos.md) (resolvida, ou em andamento com o motivo).

- [ ] OK / [ ] Falhou — observação:

### 2.3 Subir com os sistemas reais

A partir daqui o roteiro **usa os sistemas reais** (SIGA, API de arquivos e SCORP de desenvolvimento). Suba **sem** o `compose.verificacao.yaml`, com o `.env.local` preenchido como na S2 (passo 4 do roteiro da S2), mais `SCORP_API_URL` e, se o 2.2 pediu, as variáveis `SCORP_API_*`:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --build
```

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml exec -T api sh -c '[ -n "$SCORP_API_URL" ] && echo "SCORP ligado" || echo "SCORP DESLIGADO"; echo "sigilo padrão: ${SCORP_SIGILO_PADRAO:-false}"'
```

Resultado esperado: `SCORP ligado` e `sigilo padrão: false` (o padrão, até o passo 8).

- [ ] OK / [ ] Falhou — observação:

## 3. Documentos pessoais na lista e diferença com a Vista do SIGA (P21)

Entre como `gestor.demo`. Abra o processo citado em P21 ([`docs/10`](../10-pendencias-e-riscos.md)), o outro processo de 2022 da mesma pendência e os 3 casos de [`casos-de-teste.md`](../casos-de-teste.md). Para cada um, abra também a **Vista do Processo** do SIGA (lista de documentos) para comparar.

| Item | Esperado | OK? |
|---|---|---|
| Origem "Pessoal" | documentos com origem "Pessoal · Interessado", "Pessoal · Representante legal" ou "Pessoal · Responsável técnico" na coluna Origem; no filtro de origem, uma opção única **Pessoal** | [ ] |
| Ordem | os pessoais entram na ordem cronológica pela data do SCORP; na mesma data, depois das origens do SIGA | [ ] |
| Seleção | os pessoais vigentes em PDF vêm selecionados como os demais (RN13) | [ ] |
| Sem aviso | com o SCORP respondendo, nenhuma faixa amarela de aviso no topo da seção | [ ] |
| Modal | **Visualizar** num documento pessoal mostra o PDF (isto confere se a chave do SCORP vale em `/api/arquivo`; anote no passo 4) | [ ] |

**Diferença de documentos (P21).** Anote, para cada processo, a contagem da Vista do SIGA, a do SIGA Vistas **antes** da S4 (a anotada no roteiro da S2) e a de agora:

| Processo | Vista do SIGA | SIGA Vistas (S2/S2.2) | SIGA Vistas (S4) | Pessoais (S4) | Diferença restante | Explicação |
|---|---|---|---|---|---|---|
| (processo do P21) | | | | | | |
| (processo de 2022 do P21) | | | | | | |
| Caso 1 | | | | | | |
| Caso 2 | | | | | | |
| Caso 3 | | | | | | |

Resultado esperado: a diferença **desaparece**, ou o que sobra se explica por regra conhecida:

- compactados não listados (RN11);
- geometrias, agora na seção própria (RN10), que não contam na tabela de Documentos;
- chaves repetidas entre origens, mostradas uma vez (RN05, que a Vista não aplica);
- documento de doença grave (RN02).

Para localizar um documento que ainda falte, use a consulta do passo 5.1 do roteiro da S2. Registre o resultado em P21 ([`docs/10`](../10-pendencias-e-riscos.md)): resolvida, ou a diferença restante com a explicação.

**SCORP indisponível (falha tolerante).** No `.env.local`, troque `SCORP_API_URL` por um endereço que não responde (por exemplo `http://localhost:9`) e recrie só a `api`:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --force-recreate api
```

| Item | Esperado | OK? |
|---|---|---|
| Lista | as origens do SIGA aparecem normalmente, sem os pessoais | [ ] |
| Faixa | "Os documentos pessoais (SCORP) não puderam ser consultados agora. Os demais documentos foram listados. Atualize a página para tentar de novo." | [ ] |
| Sem bloqueio | dá para selecionar e gerar o dossiê; nenhum toast de erro | [ ] |
| Consultar novamente | volte o `SCORP_API_URL` correto, recrie a `api` (comando acima), clique em **Consultar novamente**: os pessoais aparecem, a faixa some e a seleção feita antes é mantida | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 4. Dossiê com documentos pessoais

Num requerimento com documentos pessoais em PDF, como Gestor, gere um dossiê com a seleção padrão (pessoais incluídos).

| Item | Esperado | OK? |
|---|---|---|
| Chave do SCORP em `/api/arquivo` | os documentos pessoais são **obtidos** (situação "Obtido" na gaveta) | [ ] |
| Se não forem obtidos | o pedido fica em "Documentos com problema" com o motivo; **Remover e gerar** monta sem eles. Anote o motivo (por exemplo, "arquivo não encontrado no repositório"): significa que a chave do SCORP **não** vale no `/api/arquivo` | [ ] |
| Ordem no PDF | os pessoais na posição cronológica correta, pela data do SCORP | [ ] |
| Sumário e marcadores | a origem dos pessoais aparece como **"Pessoal"** | [ ] |
| Numeração | "Página X de Y" contínua, também nas páginas dos pessoais | [ ] |
| Capa | "N incluídos de M listados" conta os pessoais | [ ] |

Registre em [`docs/05`](../05-integracoes.md) (seção SCORP, "Arquivos") se a chave do SCORP vale em `/api/arquivo`, e em P06.

- [ ] OK / [ ] Falhou — observação:

## 5. Geometrias: ZIP e omissão de sigilosas

Escolha um requerimento real que tenha SHP, KML ou KMZ (o Gestor vê a seção **Geometrias** abaixo de Documentos). Se possível, inclua um shapefile.

| Item | Esperado | OK? |
|---|---|---|
| Fora da tabela | as geometrias **não** aparecem na tabela de Documentos, nos totais da seção nem na seleção | [ ] |
| Seção | nome, arquivo, origem e data de cada geometria; substituída e indisponível esmaecidas, "fora do ZIP" | [ ] |
| Baixar geometrias | "Preparando o ZIP…" e depois o download de `geometrias-processo-<numero>-<ano>.zip` (ou `-requerimento-`) | [ ] |
| Conteúdo do ZIP | abra o ZIP: um arquivo por geometria vigente, com o nome de origem; nomes repetidos com sufixo (`area-2.kml`) | [ ] |
| Abrir num visualizador | abra os arquivos num visualizador de SIG (QGIS, ou Google Earth para KML/KMZ): as feições aparecem no lugar esperado | [ ] |
| **Shapefile (R45)** | o `.shp` vem acompanhado de `.shx` e `.dbf` (e `.prj`)? Se vier sozinho, não abre no QGIS: anote em R45 | [ ] |
| Tempo (R46) | anote o tempo até o download começar e o tamanho do ZIP | [ ] |

Medição: tempo ______ s · tamanho ______ MB · arquivos ______.

**Sigilosas para o Operador.** Se o requerimento tiver geometria sigilosa, ou para testar com o perfil Operador, troque o CPF/CNPJ do `operador.demo` como no passo 6 (só na sua máquina, sem commitar) e confira:

| Item | Esperado | OK? |
|---|---|---|
| Aviso | "N geometria(s) sigilosa(s) não são exibidas para o seu perfil.", sem nome, data ou id | [ ] |
| ZIP | o ZIP baixado pelo Operador **não** contém a sigilosa | [ ] |
| Só sigilosas | se todas forem sigilosas, a seção mostra só o aviso, sem tabela e sem botão | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 6. Perfis contra a matriz

O `operador.demo` tem o CNPJ fictício `12.345.678/0001-90`. Para testar o Operador com um interessado real, **só na sua máquina e sem commitar**, troque o CPF/CNPJ dele em `api/src/main/java/br/gov/mt/sema/vistas/acesso/infrastructure/memoria/RepositorioUsuariosEmMemoria.java` pelo de um interessado de teste (`<CNPJ_DE_TESTE>`) e suba com `--build`. **Desfaça a troca no fim** e confira com `git diff` que o arquivo voltou ao original.

Use: um requerimento **do** interessado de teste (com um documento sigiloso, se houver) e um requerimento de **outro** interessado. Antes, como Gestor, gere um dossiê de qualquer requerimento, para ter um "dossiê de outro usuário". Confira cada resposta contra a tabela "Perfis e permissões" de [`docs/12`](../12-configuracoes.md) e a "Matriz de acesso por endpoint" de [`docs/07`](../07-api.md#matriz-de-acesso-por-endpoint). Para as linhas de API, abra a URL no navegador com a sessão do perfil, ou veja a resposta em DevTools › Rede.

| Ação | Operador do interessado | Operador de outro | Gestor | Administrador |
|---|---|---|---|---|
| Abrir o requerimento (`/consulta/requerimentos/{id}`) | abre [ ] | "Seu perfil consulta apenas processos e requerimentos em que você é o interessado." (403 `NAO_INTERESSADO`) [ ] | abre [ ] | abre [ ] |
| Buscar o número do requerimento de outro na Consulta | — | "… não tem você como interessado." [ ] | encontra [ ] | encontra [ ] |
| Documento sigiloso na lista | "Documento sigiloso", sem nome de arquivo, sem seleção [ ] | — | título real, selecionável [ ] | título real, selecionável [ ] |
| **Visualizar** o sigiloso | mensagem de sigilo, sem chamada a `/conteudo` [ ] | — | abre [ ] | abre [ ] |
| `GET /api/requerimentos/{id}/documentos` | 200 [ ] | 403 `NAO_INTERESSADO` [ ] | 200 [ ] | 200 [ ] |
| Dossiê de outro usuário: `GET /api/dossies/{id}` (id do pedido do Gestor, pela aba Rede) | 404 `NAO_ENCONTRADO` [ ] | 404 [ ] | 200 [ ] | 200 [ ] |
| Tela "Dossiês gerados" | só os próprios, sem "Gerado por" nem "Só os meus" [ ] | só os próprios [ ] | todos, com "Gerado por" e "Só os meus" [ ] | idem [ ] |
| PDF gerado pelo Operador | sumário e "Documentos não incluídos" com "Documento sigiloso" no lugar do título [ ] | — | — | — |

Nenhuma resposta pode trazer contagem, nome, data ou número de documento de outro interessado, nem título de sigiloso para o Operador.

- [ ] OK / [ ] Falhou — observação:

## 7. Tela "Dossiês gerados"

Como Gestor, gere três dossiês e deixe cada um numa situação diferente. Uma forma:

- deixe um concluir;
- cancele outro na gaveta;
- num terceiro, provoque falha como no passo 10.2 do roteiro da S2 (documento que falha) e feche a gaveta sem decidir.

Abra **Dossiês gerados** no menu.

| Item | Esperado | OK? |
|---|---|---|
| Situações | "Pronto", "Cancelado" e "Aguardando decisão" nas linhas certas, mais recentes primeiro | [ ] |
| Expira em | data e hora em horário de Cuiabá e "restam N min" nos pedidos vivos, igual ao da gaveta | [ ] |
| Documentos | "N de M" (incluídos de listados) e "N com problema" no pedido em decisão | [ ] |
| Gerado por | nome do usuário de demonstração (Gestor e Administrador) | [ ] |
| Pré-visualizar e Baixar PDF | no "Pronto": abre o PDF; o download baixa o arquivo e a linha passa a "Arquivo baixado" | [ ] |
| Decidir | no "Aguardando decisão": abre a gaveta de geração com o mesmo pedido; **Remover e gerar** conclui sem criar pedido novo | [ ] |
| Cancelar | num pedido em andamento (gere um grande e cancele pela tela): a linha passa a "Cancelado" | [ ] |
| Abrir requerimento | leva ao detalhe do requerimento | [ ] |
| Expirado | deixe um "Aguardando decisão" passar do prazo (`DOSSIE_TTL_MINUTOS`): "Expirado · nenhum arquivo gerado", sem download | [ ] |
| Só os meus | como Administrador, marque "Só os meus": somem os pedidos do Gestor | [ ] |
| Filtro por situação | filtra a página | [ ] |
| Atualização | a lista se atualiza sozinha a cada 30 s (DevTools › Rede: `GET /api/dossies`); com a aba em segundo plano, as chamadas param | [ ] |
| **Histórico em memória** | reinicie a `api` (`docker compose ... restart api`): a lista fica vazia, com "Você ainda não gerou dossiês nesta sessão do serviço" e a nota de que o histórico some ao reiniciar | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 8. Decidir `SCORP_SIGILO_PADRAO` com a área (P30)

O SCORP não informa sigilo. Hoje o padrão segue a Vista do SIGA, que mostra os documentos pessoais como não sigilosos (`false`). Com `true`, todos os pessoais são tratados como sigilosos: o Operador os vê como "Documento sigiloso", sem nome e sem poder incluí-los, e só Gestor e Administrador os abrem.

1. Mostre à área da SEMA a lista de um requerimento com documentos pessoais (tipos típicos: documentos de identificação, procurações, ARTs) e explique as duas opções.
2. Para demonstrar `true`, edite o `.env.local` (`SCORP_SIGILO_PADRAO=true`), recrie a `api` (`docker compose ... up -d --force-recreate api`) e mostre a lista como Operador (passo 6) e como Gestor.
3. **Escolha:** `SCORP_SIGILO_PADRAO=` ______ . **Quem decidiu e quando:** ______________________ . **Motivo (uma frase):** ______________________________ .
4. Deixe o `.env.local` com o valor escolhido e registre em P30 ([`docs/10`](../10-pendencias-e-riscos.md)), em D62 ([`docs/09`](../09-decisoes.md)) e em [`docs/12`](../12-configuracoes.md).

- [ ] OK / [ ] Falhou — observação:

## 9. Conferências finais

| Item | Esperado | OK? |
|---|---|---|
| Logs | `docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml logs api` sem token, cookie, URL da API de arquivos ou do SCORP, chave de arquivo, identificador da pessoa no SCORP nem nome de documento | [ ] |
| Tempo da lista (R47) | com o SCORP real, a seção Documentos abre em tempo aceitável num requerimento com várias pessoas (anote: pessoas ______, tempo ______ s) | [ ] |
| Operador desfeito | `git diff api/src/main/java/br/gov/mt/sema/vistas/acesso/infrastructure/memoria/RepositorioUsuariosEmMemoria.java` vazio | [ ] |
| Repositório | `git status` sem ZIP, PDF, amostra ou dado real | [ ] |
| Containers | `docker compose ... down` (**sem** `-v`) ao terminar | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 10. Registro do resultado

Preencha a seção **"Validação da S4"** de [`docs/14`](../14-plano-de-sprints.md), anote os casos em [`casos-de-teste.md`](../casos-de-teste.md) e atualize [`docs/10`](../10-pendencias-e-riscos.md):

- **P06**: autenticação do SCORP e validade da chave em `/api/arquivo` (passos 2.2 e 4);
- **P21**: diferença com a Vista do SIGA (passo 3);
- **P30**: sigilo padrão do SCORP (passo 8);
- **R45** (shapefile), **R46** (tempo do ZIP) e **R47** (tempo da lista com SCORP): medições dos passos 5 e 9.

Abra pendência nova para o que não bater.
