# Roteiro de homologação da S11: caixa do analista e lista por analista

**Quem executa:** o responsável pelo projeto, manualmente. O desenvolvimento (Claude Code) **nunca** se conecta ao SIGA real, ao Camunda real, ao SCORP nem à API de arquivos (D27, D34). Os cenários CX01 a CX17 são **fictícios**: a população da caixa (`CenariosFicticiosDaS11`, ids 15001 a 15099 e 16001 a 17200, com datas de 2027 e 2028) é gravada **só no Oracle de teste** (Testcontainers). A conferência com dados reais é a seção 8, **fora do repositório**.

**Estado deste roteiro:** a coluna **Resultado** de todos os cenários está `Não executado`. Nenhum item foi marcado como validado com dados reais.

**O que a S11 entrega e este roteiro confere:**

- a **caixa de entrada do analista** ("Minha caixa", rota `/caixa`), com cartão de resumo, visões **Geral**, uma por etapa e **Com o interessado**, busca por nome do interessado ou número e ordenação (mais parado primeiro, o padrão, ou mais recente) (blocos 5 a 7);
- a **posse** do requerimento (análise e revisão com o analista; correção com o interessado quando a última análise ou revisão foi dele; fora da caixa nas etapas seguintes) (blocos 4 e 5);
- o **aviso de recebimento**: a caixinha com a quantidade, a lista de mudanças com a **origem do retorno**, o visto gravado ao abrir a lista e o aviso que some depois de visto (blocos 5 a 7);
- o **nome do interessado** (pessoa ou razão social) nas listagens da consulta, da caixa e da lista do painel, **nunca** CPF ou CNPJ (blocos 3, 7 e 9);
- o **perfil Analista** (`verCaixa`), o destino do login e a tela de usuários (bloco 2);
- a **aba lateral "Processos por analista"** no painel do Gestor, com filtros e contagem que bate com a barra (bloco 9);
- a **pizza da fila por etapa**, com a legenda de todas as etapas (bloco 8, correção da S10);
- a permissão: `403 PERFIL_SEM_PERMISSAO` antes de qualquer leitura para perfil sem `verCaixa` (caixa) e sem `verPainel` (painel); a auditoria `CAIXA` sem CPF e sem lista de processos (bloco 6);
- o isolamento: falha da caixa não afeta consulta, tramitação, financeiro nem documentos (bloco 10).

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em [`docs/14`](../14-plano-de-sprints.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que falhar.

> **Dados pessoais e arquivos reais:** CPF, CNPJ, nomes, números de processo, números de requerimento e ids reais ficam como marcadores (`<ID_REQUERIMENTO>`, `<CPF_DO_SERVIDOR>`, `<CPF_DE_TESTE>`, `<CNPJ_DE_TESTE>`). **Nenhuma captura de tela, exportação de auditoria ou consulta com dado real vai para o repositório**: guarde numa pasta local fora dele (`amostras-locais/` está no `.gitignore`) e apague depois.

> **Uso do `.env.local`:** você edita o `.env.local` sozinho, sem colar o conteúdo em lugar nenhum. Os comandos abaixo só dizem **se** uma variável está preenchida, nunca o valor.

## 0. Preparação

Suba **sempre** com os três arquivos de Compose (o terceiro esvazia, no serviço `api`, as variáveis que levam a sistemas externos):

```powershell
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d --build
```

```powershell
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml ps
```

Confira, **dentro do container** e sem imprimir valores, que as variáveis externas estão vazias. Se alguma vier `preenchida`, derrube os containers (sem `-v`) e não siga:

```powershell
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml exec -T api sh -c '[ -z "$SIGA_DB_URL" ] && echo vazia || echo preenchida'
```

Repita o comando trocando `SIGA_DB_URL` por `LICENCIAMENTO_API_URL` e `SCORP_API_URL`.

- [ ] OK / [ ] Falhou — observação:

Resultado esperado: `api` e `front` em execução; abra `http://localhost:8080`. Usuários de demonstração anteriores à L1 (hoje o login é `operador`, `analista` ou `admin`, com senhas em variáveis; o Gestor é criado pelo `admin`) (o back-end **não** traz usuário de demonstração Analista; ver seção 2).

**Situação do ambiente de tela (NÃO CONFIRMADO):** a população fictícia da caixa existe **só no Oracle de teste**. Não há, documentado, um ambiente com SIGA fictício que a mostre na tela. Por isso a coluna **Resultado** fica `Não executado` até haver esse ambiente; a prova automática de cada linha está na tabela da seção 3 (`CaixaDoAnalistaTest`, `CaixaHttpTest` e o e2e). Quem marcar `OK` precisa ter visto o resultado na tela de fato.

**Atenção ao ano:** a caixa **não** usa o ano do painel; ela mostra o que está na posse, de qualquer ano (CX14 é de 2027). O painel do Gestor, ao contrário, usa o ano de `rastro.painelAno` (0 = ano corrente de Cuiabá). Para conferir a lista por analista com a população fictícia, ajuste o ano do painel para 2027 ou 2028, senão a lista não mostra esses requerimentos. Confira o ano que a tela mostra no cabeçalho antes de comparar os números.

## 1. Como ler a tela

- **Caixa ("Minha caixa"):** cabeçalho com "Atualizado às HH:MM" (horário de Cuiabá) e o botão **Atualizar**. A atualização automática segue o período de `rastro.caixaAtualizacaoMinutos` (padrão 10); o botão tem intervalo mínimo `rastro.caixaIntervaloMinimoManualSegundos` (padrão 60) e responde `429` com "Tente de novo em N segundos". **O aviso pode demorar até o período de atualização para aparecer.**
- **Resumo:** total na caixa, uma contagem por etapa de posse (análise e revisão, inclusive as zeradas) e "Com o interessado (correção)".
- **Dias na etapa:** dias completos desde o início da tarefa atual (para a correção, o início da tarefa de correção). Sem dado, "sem informação"; nunca um número inventado.
- **Caixinha de aviso:** botão destacado "N requerimentos chegaram para você". Ao abrir a lista de mudanças, cada item mostra de onde veio. O visto daquele conjunto é gravado **ao receber a lista**; depois, a caixinha e as marcas "Novo" somem.
- **Visto em memória:** o visto **zera quando o sistema reinicia**, e o reinício vale como primeiro acesso (**sem aviso retroativo**). A tela diz isso em texto discreto de ajuda.
- **Origem do retorno** (quando não dá para determinar, a tela diz **"Mudou de etapa"**, nunca uma origem inventada): "Voltou da correção", "Voltou da validação do parecer", "Voltou da emissão do ato", "Voltou da revisão" e "Novo na sua caixa".
- **Sem vínculo:** o Analista sem CPF cadastrado vê "Seu usuário não está vinculado a um servidor. Peça ao Administrador.", sem lista e sem caixinha.
- **Lista por analista (painel):** ver a seção 5.
- **Nunca aparecem:** CPF ou CNPJ (nem mascarado), chave opaca do analista, id técnico do Camunda, chave de arquivo. Para qualquer perfil.

## 2. Analista de demonstração (passo de preparação)

O back-end não traz usuário de demonstração Analista (desvio do bloco 2, registrado em [`docs/progresso/s11.md`](../progresso/s11.md)). Para homologar a caixa:

1. Entre como `admin`, abra **Configurações** › **Usuários e perfis** e cadastre um usuário com o perfil **Analista**.
2. No campo CPF, use o **CPF do servidor** que aparece no Camunda real, ou deixe em branco para testar o caso sem vínculo. O CPF nunca sai em tela, log, erro ou auditoria; se ele aparecer, pare e registre.
3. Entre com esse usuário: o login leva para **Minha caixa**; os demais perfis continuam na Consulta.

- [ ] OK / [ ] Falhou — observação:

Teste também o **sem vínculo**: Analista sem CPF cadastrado vê a mensagem da seção 1 e nenhuma lista.

## 3. Cenários da caixa (população fictícia, uma linha por requerimento)

Cada cenário é um requerimento da população do Oracle de teste (`CenariosFicticiosDaS11`, tabela em [`docs/progresso/s11.md`](../progresso/s11.md) e [`docs/15`](../15-tramitacao.md)). "Analista" é o dono da posse na tabela; o interessado é fictício ("Interessado Exemplo 01" a "04" e "Cooperativa Exemplo 04").

Em cada linha, o **Aviso esperado** vale quando o analista **já tiver aberto a caixa antes** e o item tiver mudado depois; no primeiro acesso, nada é aviso. A **Origem esperada** é o texto da lista de mudanças.

| Código | Analista | Situação do requerimento | Estado esperado (etapa na caixa) | Aviso esperado | Origem esperada | Dias na etapa | Resultado |
|---|---|---|---|---|---|---|---|
| CX01 | A1 | Análise de A1, sem mudança | Análise do processo | Não | — | 20 | `Não executado` |
| CX02 | A2 | Revisão do parecer com A2 | Revisão do parecer técnico | Não | — | 8 | `Não executado` |
| CX03 | A1 | Mudou de etapa **dentro** da posse (análise para revisão, ambas de A1) | Revisão do parecer técnico | **Não** (mudança dentro da posse) | — | 6 | `Não executado` |
| CX04 | A1 | Correção com o interessado; a última análise foi de A1 (**saiu** da análise) | Com o interessado (correção) | **Não** (saída da posse não avisa; a correção continua na caixa de A1) | — | 12 | `Não executado` |
| CX05 | A1 | A correção **voltou** para a análise de A1 | Análise do processo | **Sim** | Voltou da correção | 10 | `Não executado` |
| CX06 | A1 | Foi à emissão do ato (filha devolvida) e voltou para a **revisão** de A1 | Revisão do parecer técnico | **Sim** | Voltou da emissão do ato | 4 | `Não executado` |
| CX07 | A1 | A validação devolveu (RA) para a **análise** de A1 | Análise do processo | **Sim** | Voltou da validação do parecer | 9 | `Não executado` |
| CX08 | A1 | A revisão da chefia devolveu para a **análise** de A1 | Análise do processo | **Sim** | Voltou da revisão | 7 | `Não executado` |
| CX09 | A1 | Processo **novo** distribuído a A1 (aceite há 2 dias) | Análise do processo | **Sim** | Novo na sua caixa | 2 | `Não executado` |
| CX10 | A1 | Na **validação**, com a tarefa atribuída a A1 (**saiu** da posse) | fora da caixa | **Não** (saída da posse) | — | — | `Não executado` |
| CX11 | A1 | Na **emissão do ato** (filhas ativas) | fora da caixa | Não | — | — | `Não executado` |
| CX12 | A1 | **Concluído** (final do catálogo) | fora da caixa | Não | — | — | `Não executado` |
| CX13 | A2 | Correção; a última análise foi de A2 (só A2 a vê; A1 não) | Com o interessado (correção) | Não | — | 9 | `Não executado` |
| CX14 | A1 | Análise de A1 de **ano anterior** (2027): a caixa não usa o recorte do ano | Análise do processo | Não | — | 400 | `Não executado` |
| CX15 | sem analista | Correção **sem nenhuma análise** antes (ainda no cadastro) | fora da caixa | Não | — | — | `Não executado` |
| CX16 | A3 | Análise de A3, do mesmo interessado do CX01 | Análise do processo | Não | — | 15 | `Não executado` |
| CX17 | A1 | A emissão do ato devolveu (filha devolvida) para a **análise** de A1 | Análise do processo | **Sim** | Voltou da emissão do ato | 3 | `Não executado` |

> **Leitura da tabela:** "Fora da caixa" significa que o requerimento **não** deve aparecer na caixa de nenhum analista (CX15 não tem analista na última análise; CX10, CX11 e CX12 estão além da posse). O ramo "sem informação" nos dias não tem fixture no Oracle de teste: é coberto pelo teste de regras, com dado montado em memória.

**Contagens esperadas por analista (conferir no resumo e na lista):**

- **A1:** 10 itens na caixa (CX01, CX03, CX04, CX05, CX06, CX07, CX08, CX09, CX14 e CX17); na posse: 7 em análise (CX01, CX05, CX07, CX08, CX09, CX14, CX17) e 2 em revisão (CX03, CX06); 1 com o interessado (CX04). **Avisos: 6** (CX05, CX06, CX07, CX08, CX09 e CX17).
- **A2:** 2 itens (CX02 em revisão; CX13 com o interessado). Sem aviso.
- **A3:** 1 item (CX16 em análise). Sem aviso.
- **Analista sem CPF:** caixa vazia, com a mensagem de vínculo da seção 1.

- [ ] A1: 10 itens, 6 avisos — [ ] OK / [ ] Falhou — observação:
- [ ] A2: 2 itens — [ ] OK / [ ] Falhou — observação:
- [ ] A3: 1 item — [ ] OK / [ ] Falhou — observação:
- [ ] Sem CPF: vazia e com a mensagem — [ ] OK / [ ] Falhou — observação:

## 4. Caixa: visões, busca, ordenação, caixinha e visto

Use o analista A1 da seção 3 (ou o de sua preferência, com os cenários equivalentes).

- [ ] **Visão Geral:** lista todos os itens da posse, com "Com o interessado" marcado como tag. OK / Falhou — observação:
- [ ] **Visão por etapa:** clique em **Análise do processo**, depois **Revisão do parecer técnico**, depois **Com o interessado**. O `aria-pressed` acompanha a visão ativa, e o resumo **não** muda com o filtro (ele vem do servidor). OK / Falhou — observação:
- [ ] **Busca por nome:** digite "Exemplo 01" (ou parte do nome do interessado). A lista filtra depois de cerca de 300 ms. OK / Falhou — observação:
- [ ] **Busca por número:** digite parte do número do requerimento (`<ID_REQUERIMENTO>` ou o número da fixture). OK / Falhou — observação:
- [ ] **Busca sem acento e sem caixa:** digite o nome sem acento e em minúsculas. OK / Falhou — observação:
- [ ] **Ordenação padrão:** "mais tempo parado primeiro" (os dias maiores no topo; CX14, com 400 dias, deve estar primeiro entre os da visão Geral). OK / Falhou — observação:
- [ ] **Ordenação "mais recente":** a troca inverte a ordem pelo tempo na etapa. OK / Falhou — observação:
- [ ] **Dias na etapa:** "hoje", "1 dia", "N dias" e "sem informação" aparecem onde couberem (nunca número negativo). OK / Falhou — observação:
- [ ] **Caixinha:** com itens avisados (CX05, CX06, CX07, CX08, CX09, CX17), aparece "6 requerimentos chegaram para você" (a quantidade do conjunto de avisos). A caixinha é anunciada ao leitor de tela (região viva `role="status"`). OK / Falhou — observação:
- [ ] **Lista de mudanças:** ao abrir, cada item mostra processo, interessado, etapa e **Origem esperada** da seção 3. Para um item sem origem determinável (se ocorrer), a tela diz "Mudou de etapa", nunca outra coisa. OK / Falhou — observação:
- [ ] **Visto:** ao receber a lista, a caixinha **some** e a marca "Novo" também. Recarregue a página: a caixa abre sem caixinha (o visto está gravado em memória). OK / Falhou — observação:
- [ ] **Item que chega depois:** depois do visto, um novo processo (ou um retorno, em nova atualização) gera nova caixinha, com a contagem só dos novos. OK / Falhou — observação:
- [ ] **Fechar a lista:** Esc, clique fora e o botão Fechar devolvem o foco (ao botão ou, se ele sumiu, ao título da lista). OK / Falhou — observação:
- [ ] **Item leva ao requerimento:** clicar em um item abre o detalhe do requerimento correspondente. OK / Falhou — observação:
- [ ] **Atualizar:** o botão mostra "Atualizado às HH:MM" novo; um segundo clique antes do intervalo mínimo mostra "Tente de novo em N segundos" (429). OK / Falhou — observação:
- [ ] **Reinício zera o visto (aviso de ajuda):** após reiniciar a `api`, a caixinha **não** volta para os itens já vistos (reinício = primeiro acesso, sem aviso retroativo). OK / Falhou — observação:
- [ ] **Duas abas:** o visto de uma aba não apaga o da outra (cada aba grava o seu conjunto). OK / Falhou — observação:
- [ ] **360 px:** a caixa, a lista de mudanças e a caixinha ficam sem rolagem horizontal (cada linha vira um cartão). OK / Falhou — observação:

## 5. Painel do Gestor: aba lateral "Processos por analista"

Entre como o Gestor (ou `admin`). Ajuste o ano do painel para 2027 ou 2028 (seção 0).

- [ ] **Abrir a aba:** pelo botão **Processos por analista** ou pelo botão **Ver lista** da tabela da carga por responsável; o clique na barra de um analista também abre (só mouse; o teclado usa o botão). OK / Falhou — observação:
- [ ] **Soma bate com a barra:** para cada analista, o "N requerimentos (o total da barra)" é igual ao total da barra do gráfico 3 (a tabela equivalente, "Ver como tabela", serve de conferência). OK / Falhou — observação:
- [ ] **Outros:** "Outros (N servidores)" tem lista (a união dos servidores fora dos 15 maiores), e o total bate com a barra. OK / Falhou — observação:
- [ ] **Fora do catálogo e Sem instância:** não aparecem em nenhuma lista de analista (não têm responsável). OK / Falhou — observação:
- [ ] **Filtro por analista:** o seletor mostra o total da barra entre parênteses; a lista troca para o analista escolhido. OK / Falhou — observação:
- [ ] **Filtro por etapa:** a lista mostra só a etapa escolhida, e a contagem "· M com os filtros" muda. OK / Falhou — observação:
- [ ] **Busca por interessado ou número:** a busca filtra depois de cerca de 300 ms. OK / Falhou — observação:
- [ ] **Cada linha:** número (link para o requerimento), interessado (**nome**, nunca CPF ou CNPJ), etapas e protocolo. OK / Falhou — observação:
- [ ] **Selo de truncamento:** quando o instantâneo do painel estiver parcial, a aba mostra o selo de leitura parcial e a contagem continua batendo com a barra. OK / Falhou — observação:
- [ ] **Nomes ocultos:** com `RASTRO_MOSTRAR_NOME_RESPONSAVEL` **desligado**, a aba mostra só as categorias sem nome e a nota "Nomes ocultos por configuração"; a chave de um servidor devolve 404. OK / Falhou — observação:
- [ ] **Fechar:** Esc, o fundo e o botão Fechar fecham a aba e o foco volta ao botão ou à barra que a abriu. OK / Falhou — observação:
- [ ] **360 px:** a aba lateral fica sem rolagem horizontal. OK / Falhou — observação:

## 6. Pizza da fila por etapa (correção da S10)

- [ ] **Legenda com todas as etapas**, na ordem do catálogo, **inclusive as zeradas** (com "0" e sem fatia). OK / Falhou — observação:
- [ ] **Linhas especiais** "Fora do catálogo", "Sem instância" e "Etapa não mapeada" aparecem na legenda (fatia quando o valor é maior que zero), nunca somem. OK / Falhou — observação:
- [ ] **Percentual:** o rótulo diz "participação na soma das etapas", **nunca** "% dos requerimentos"; a soma dos percentuais dá 100. OK / Falhou — observação:
- [ ] **Nota de soma:** quando a soma das fatias passa do total de requerimentos (etapas paralelas, D92), a nota explica, junto do gráfico. OK / Falhou — observação:
- [ ] **Tudo zerado:** aparece "Nenhum requerimento em andamento", sem pizza vazia. OK / Falhou — observação:
- [ ] **Uma só etapa com valor:** a pizza vira um círculo inteiro, com legenda. OK / Falhou — observação:
- [ ] **Teclado e leitor de tela:** o foco vai aos itens da legenda (que destacam a fatia) e a alternativa em tabela existe. OK / Falhou — observação:
- [ ] **Movimento reduzido:** com `prefers-reduced-motion` ligado, não há animação. OK / Falhou — observação:
- [ ] **360 px:** o gráfico e a legenda ficam sem rolagem horizontal. OK / Falhou — observação:

## 7. Acesso, nomes e isolamento

- [ ] **Operador:** não vê **Minha caixa** nem **Painel**; digitando `/caixa` ou `/painel` volta à Consulta com o aviso de perfil, e a API responde `403 PERFIL_SEM_PERMISSAO` para `/api/caixa` e `/api/painel/analistas`. OK / Falhou — observação:
- [ ] **Gestor e Administrador:** não têm caixa (`/api/caixa` responde 403); veem o painel e a lista por analista. OK / Falhou — observação:
- [ ] **Analista:** não vê **Painel** nem **Auditoria** (o item some do menu; a rota direta mostra o 403 da API, D102); o painel, a auditoria, os usuários, os parâmetros e as conexões respondem 403. OK / Falhou — observação:
- [ ] **Nome do interessado na consulta:** a coluna **Interessado** da lista de requerimentos mostra o nome (pessoa ou razão social), quebra de linha em 360 px, e nunca CPF ou CNPJ. OK / Falhou — observação:
- [ ] **Auditoria `CAIXA`:** em Auditoria, o filtro "Caixa do analista" mostra os eventos de abrir a caixa ("caixa consulta total N PRONTO") e de abrir a lista de mudanças ("caixa avisos N"), **só** com a quantidade: sem CPF, sem chave e sem lista de processos. OK / Falhou — observação:
- [ ] **Isolamento:** com o Camunda ou o SIGA fora do ar (quando o ambiente permitir), a caixa mostra `INDISPONIVEL` ou `DESATUALIZADO`, e consulta, tramitação, financeiro e documentos seguem funcionando. OK / Falhou — observação:

## 8. Pontos provisórios e NÃO CONFIRMADOS (verificar antes de dar a S11 por homologada)

Cada item está registrado em [`docs/10`](../10-pendencias-e-riscos.md) e em [`docs/09`](../09-decisoes.md). Aqui, o que conferir com dados reais, **fora do repositório**:

- [ ] **Posse na correção (NÃO CONFIRMADO, P47a e P48a):** a correção fica com o analista pela última tarefa de análise ou revisão **concluída** em `ACT_HI_TASKINST`. Como o Camunda real grava o pedido de correção (motivo da conclusão, se a tarefa fica `completed`) e se a conta de leitura enxerga esse histórico **não foi confirmado**. Conferir uma correção que voltou para o analista e uma que nunca foi analisada. Se o histórico não for legível, a contingência (só tarefas ativas) mostra a correção como `FORA` e a tela diz "Correções em andamento: não consideradas".
- [ ] **Nomes e ids das etapas (NÃO CONFIRMADO):** os ids vêm do catálogo de etapas (`docs/15`), sem invenção. Conferir com o fluxo real que os rótulos "Análise do processo", "Revisão do parecer técnico", "Correção do requerimento" e as demais etapas citadas na posse batem. Se alguma etapa da regra de posse não existir no catálogo, o ponto fica em aberto (NÃO CONFIRMADO) e deve ser registrado em `docs/10`.
- [ ] **Aceite não é posse (P47b, decisão provisória):** a tarefa de aceite não conta como posse; confirmar com o fluxo real se o analista tem a análise logo após o aceite.
- [ ] **Coluna do nome do interessado (NÃO CONFIRMADO, P49c):** o nome vem de `PESSOA.NOME` por `REQUERIMENTO.ID_PESSOA`, a mesma coluna da consulta, para pessoa e empresa. Conferir o nome de uma **empresa** com razão social em coluna distinta: se houver outra coluna, o nome sai errado. Não há requerimento sigiloso nesta sprint.
- [ ] **Tipo e objeto (P47c):** a caixa mostra só o tipo de processo (`TIPO_PROCESSO.DESCRICAO`); o objeto de licenciamento é 1:N e não entra no lote. Conferir se o tipo basta para o analista.
- [ ] **Origem do retorno (P48a):** conferir, em cada retorno do roteiro da seção 3 (correção, validação, emissão do ato, revisão e novo), se a passagem anterior deduzida do histórico bate com a real. Quando não der para determinar, a tela diz "Mudou de etapa", nunca uma origem inventada.
- [ ] **Teto de leituras (P48a):** a origem é lida só dos itens avisados, no máximo 100 por atualização; o restante sai como "Mudou de etapa".
- [ ] **Chave do analista muda a cada subida (P48c):** o visto em memória zera no reinício e a chave opaca não serve de chave estável; a H1 (pós-apresentação) resolve isso.
- [ ] **Analista cadastrado depois (P48d):** um analista cadastrado depois da última atualização só aparece na leitura seguinte (até o período de atualização).
- [ ] **Tempo da consulta de candidatos (P47d):** a consulta de candidatos varre as tarefas ativas dos modelos do catálogo (`ASSIGNEE_` sem índice usado por causa do `TO_CHAR`). Medir o tempo com a equipe do SIGA; a consulta roda sob o `LimiteDeLeiturasDoCamunda` e o tempo limite do painel.
- [ ] **Nome do interessado de empresa na lista por analista (NÃO CONFIRMADO, P49c):** mesma verificação do nome na caixa.
- [ ] **Total da lista por analista (P49a, b e e):** o analista é identificado pelo **rótulo da barra**, então dois servidores com o mesmo nome contam juntos (como a barra); "Fora do catálogo", "Sem instância" e requerimento sem tarefa de etapa não aparecem; a lista traz no máximo 1000 linhas, com o total exato. Conferir o total de um servidor com a barra, com "Outros" e com os nomes ocultos.
- [ ] **Desvios de decisão (D96 a D102):** (D96) perfil Analista com só `verCaixa`, sem `restritoAoInteressado` e sem CPF exibido; (D97) nome do interessado sem CPF/CNPJ na lista; (D98) instantâneo próprio da caixa, aviso e visto atrás de porta; (D99) `GET /avisos` não grava o visto e o front grava com os ids recebidos; (D100) pizza com legenda de todas as etapas; (D101) o identificador do analista no JSON é `analista` (não `chave`), e "Fora do catálogo" e "Sem instância" não têm lista; (D102) Analista não vê a auditoria de documentos (403). Cada um é uma decisão provisória, a validar com o responsável.
- [ ] **Espelho do `modulos-e-dependencias.md` (R87):** o arquivo `.claude/skills/vistas-banco/references/modulos-e-dependencias.md` ainda **não** é igual a `docs/padroes/modulos-e-dependencias.md` (o modo automático não grava em `.claude/`; o `CLAUDE.md` pede o mesmo conteúdo). Copiar à mão, pelo responsável.

## 9. Fora desta sprint (lista pós-apresentação)

Não há cenário neste roteiro para: persistência do visto em banco (H1), tarefas de grupo ainda não assumidas, aviso quando o requerimento **sai** da posse, notificação por e-mail ou fora do sistema, exportação, prazos e SLA, e o tema "pago que virou isento". Estão listados em [`docs/14`](../14-plano-de-sprints.md), seção "Primeira sprint pós-apresentação".

## 10. Resultado

| Item | Resultado |
|---|---|
| Preparação e variáveis externas vazias (seção 0) | `Não executado` |
| Analista de demonstração e sem vínculo (seção 2) | `Não executado` |
| Cenários CX01 a CX17 (seção 3) | `Não executado` |
| Caixa: visões, busca, ordenação, caixinha e visto (seção 4) | `Não executado` |
| Painel: aba lateral "Processos por analista" (seção 5) | `Não executado` |
| Pizza da fila por etapa (seção 6) | `Não executado` |
| Acesso, nomes e isolamento (seção 7) | `Não executado` |
| Pontos provisórios com dados reais (seção 8) | `Não executado` |

**Testes automatizados:** os números reais da suíte completa (back-end, front, e2e e hooks) serão preenchidos pelo orquestrador no fechamento da sprint, em [`docs/progresso/s11.md`](../progresso/s11.md) (ver relatório final).
