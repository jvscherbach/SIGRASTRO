# S9: financeiro: taxas, DAR e pagamentos

> **Forma de trabalho:** a sprint é dividida em **11 blocos**, cada um terminando em um **ponto de commit**. Três sessões do Claude Code (**OPUS**, **SONNET** e **HAIKU**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

## Contexto

A S7 mostra **onde o processo está** e a S8 **por onde passou**. A S9 mostra o **histórico financeiro do requerimento**: as taxas emitidas, o número do DAR, o vencimento, o pagamento, a isenção ("Isento de Taxa" com data) e o aviso **"Taxa vencida"**, que o SIGA não mostra hoje por causa de um bug de origem (o sistema não marca a taxa como vencida quando o prazo passa, e o Hídrico encerra o fluxo por "Pagamento Vencido 60 dias" sem atualizar a situação).

A fonte é a tabela `SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA`, com **regras de validade** (abaixo). O módulo `financeiro` **não depende do Camunda**: funciona também para requerimentos **sem instância** (plano B da S8). Ele **conversa com a tramitação** só para (a) rotular taxa inicial × taxa da correção, (b) usar como data de apoio a passagem "Isenção confirmada" e (c) mostrar marcas financeiras na linha do tempo e no fluxograma.

Ficam **fora**: o **destaque de "pago que virou isento"** (S11; os dados desta sprint já o permitem, mas o destaque e a explicação são da S11) e o painel do Gestor (S10). **Sem PDF do DAR** nem ligação com a Arrecadação (origem do DAR não confirmada, P07): só número, valor e situação.

O serviço é usado em **processos jurídicos e envolve dinheiro público**: dado errado é pior do que dado ausente. Onde a regra é provisória, o Rastro **marca** (selo ou nota), em vez de afirmar.

Leia antes de começar: `docs/02`, `docs/03`, `docs/04`, `docs/07`, `docs/08`, `docs/09`, `docs/10`, `docs/11`, `docs/12`, `docs/13`, `docs/14`, **`docs/15`** (S7 e S8), `docs/progresso/s8.md` e o código dos módulos `tramitacao` e `auditoria`. Reuse o construtor de fixtures, os avisos, o parâmetro de nome e a utilidade de data.

## Decisões já tomadas (registre em `docs/09`)

**Colunas lidas de `LANCAMENTO_TAXA`** (somente estas): `ID`, `ID_REQUERIMENTO`, `STATUS`, `TAXA_ISENTA`, `STATUS_PROCESSADO`, `NUM_DAR`, `DATA_EMISSAO`, `DATA_VENCIMENTO`, `DATA_PAGAMENTO`, `DATA_FIM`, `VALOR_REAL`, `VALOR_DESCONTO`, `QTDE_TOTAL_UPF`, `PASSOU_POR_REAPROV_TAXA_CORREC`. **Nunca** `CHAVE_GED` (chave de arquivo), `MENSAGEM` nem `ID_TITULACAO`.

**`STATUS` (enum de guia):** `AGUARDANDO_EMISSAO`, `AGUARDANDO_PAGAMENTO`, `PAGA`, `VENCIDA`, `ISENTA`, `CANCELADA`. `DATA_FIM` nula = lançamento **ativo**; `STATUS_PROCESSADO` é `N`/`P`.

**Regra de validade do lançamento** (definida pelo responsável): fora `AGUARDANDO_EMISSAO` e `ISENTA`, o lançamento **só é válido com `NUM_DAR`**. `CANCELADA` **sem DAR** é registro **"falso"** (criado e descartado pelo sistema): o Rastro **ignora**. `CANCELADA` **com DAR** é válida (foi cancelada de verdade). `ISENTA` **sem DAR** é válida (isenção não gera DAR). `AGUARDANDO_EMISSAO` com `TAXA_ISENTA = 1` é **ruído**: **ignorar por ora** (registre em `docs/10`).

**"Isento de Taxa":** cada vez que o requerimento passou por um ponto de verificação de isenção/taxa (onde deveria emitir DAR) e o resultado foi isenção, o Rastro mostra **"Isento de Taxa"** com a data. **Qual coluna dá a data é decisão provisória:** cadeia de apoio `DATA_EMISSAO` → entrada da passagem "Isenção confirmada" da tramitação (S8) → sem data ("data não registrada"). A cadeia é **configurável em um só lugar**, marcada como **a validar com dados reais** no roteiro de homologação, e documentada em `docs/10`.

**"Taxa vencida"** (cálculo **por data**, porque o SIGA não atualiza o status): lançamento **válido e ativo** (`DATA_FIM` nula), sem `DATA_PAGAMENTO`, em `AGUARDANDO_PAGAMENTO` com `DATA_VENCIMENTO` **anterior a hoje** (comparação só de **data**, no fuso de Cuiabá; vencer "hoje" ainda não é vencida), **ou** com `STATUS = VENCIDA`. O Rastro **não troca** a situação do Licenciamento: mostra a situação como está e **acrescenta** o aviso "Taxa vencida em dd/mm/aaaa". Quando o bug de origem for corrigido, o aviso some sozinho, sem mudança no Rastro.

**Taxa inicial × taxa da correção:** rotule por **ordem de emissão**; "da correção" só quando a tramitação (S8) mostra o ramo de correção e a emissão é **posterior** à entrada nele; senão "1ª taxa", "2ª taxa". **Não use `PASSOU_POR_REAPROV_TAXA_CORREC` como regra** (semântica não confirmada): exiba-o só no diagnóstico do Administrador e registre em `docs/10`.

**Valores** (`VALOR_REAL`, `VALOR_DESCONTO`, `QTDE_TOTAL_UPF`): exibidos conforme o parâmetro `RASTRO_MOSTRAR_VALORES` (padrão ligado no protótipo). Dinheiro público de pessoas e empresas: a regra definitiva depende da SEMA (`docs/10`).

**Desempenho:** a tabela tem cerca de 9 mil linhas e provavelmente **sem índice** em `ID_REQUERIMENTO`. Consulta **por requerimento** (uma) e **em lote** (até 500 ids) para listas; **sem laço por linha**, com limite e tempo limite. Recomende o índice em `docs/10` (o usuário é somente leitura).

**Independência:** o módulo funciona com o Camunda fora do ar (só perde o rótulo "da correção" e a data de apoio da isenção). Falha do financeiro **não** afeta a tramitação nem os documentos, e o inverso.

## Regras desta sprint

- **Você não executa nada contra o SIGA real, o SCORP real, o Camunda real nem a API de arquivos real.** Testes e demonstração com dados **fictícios e sintéticos** (nenhum CPF, CNPJ, nome, número de processo ou número de DAR real em teste, doc, exemplo, log ou repositório). CPF fictício é sempre **inválido de propósito** (ex.: `000.000.000-01`) e nomes são evidentes (`Servidor Exemplo 01`).
- Nunca abra, imprima ou copie o `.env.local`.
- **Verificação com Docker:** sempre com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`; confirme dentro do container que as variáveis externas (`SIGA_DB_*`, `LICENCIAMENTO_API_*`, `SCORP_*`) estão vazias (só "vazia" ou "preenchida"). Nunca `docker compose down -v`.
- **Nada pago** (regra permanente do `CLAUDE.md`): bibliotecas novas só gratuitas e de código aberto, com licença registrada em `docs/13`. Prefira o que o projeto já tem; para diagramas, **sem biblioteca**: HTML, CSS e SVG simples.
- **Somente leitura no banco.** O usuário do banco só tem `SELECT`. Todo SQL novo é `SELECT`/`WITH`, com **parâmetros nomeados** (nunca concatenar valor), `FETCH FIRST n ROWS ONLY`, tempo limite de consulta e concentrado **num único componente de acesso** (risco R04 de `docs/10`). Nada é gravado no Oracle.
- **Dados pessoais:** `ASSIGNEE_`, vínculos de identidade e variáveis do Camunda guardam **CPF e nome**. O CPF **nunca** sai em resposta, log, erro, evento de auditoria, CSV ou tela, **nem parcialmente mascarado**. Variáveis do Camunda só podem ser lidas de uma **lista fixa** (lista branca) sem dado pessoal. A chave do arquivo, tokens e cookies também nunca saem (RNF09).
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.
- Datas e horas sempre no fuso de **Cuiabá (`America/Cuiaba`)**, pela utilidade única já existente (S5.1). Data pura (sem hora) nunca muda de dia na conversão.
- **Relógio injetável:** toda regra que depende de "hoje" usa um `Clock` do projeto (testes com data fixa, inclusive virada do dia e fuso UTC × Cuiabá).
- Dinheiro: use `BigDecimal` (nunca `double`) e formate em reais pela utilidade de formatação do front.

## Protocolo das três sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS`, `SESSÃO: SONNET` ou `SESSÃO: HAIKU`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/s9.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/s9.md` (crie no bloco 1, com a tabela dos 11 blocos); a marcação entra no mesmo commit;
   - rode `git diff --stat` e responda:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET|HAIKU>): <título>
Testes: <rodaram / falharam / pulados>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto: o que muda e por quê>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET|HAIKU>.
```

5. **Pare.** Mesma sessão no próximo bloco: espere "continuar". Outra sessão: a atual termina ali.

**Blocos da sessão HAIKU (documentação mecânica, para economizar tokens).** O Haiku só recebe blocos de documentação cujo conteúdo já está decidido e escrito em outros arquivos (cenários, tabelas, planos). Nesses blocos: **não invente** cenário, número, regra nem nome; copie e organize só o que existe em `docs/`, nos testes e nas fixtures (leia-os antes); o que não achar, escreva como **NÃO CONFIRMADO** em vez de preencher; **nenhum dado real**; rode a varredura de dados pessoais ao final. Se o bloco exigir uma decisão de projeto ou de código, **pare** e responda `Este bloco precisa da sessão SONNET.` O responsável revisa o `git diff` do bloco com mais atenção do que nos demais.

Tipos de commit permitidos: `feat`, `fix`, `refactor`, `test`, `docs`, `perf`, `chore`, `ci`. A mensagem **não** começa com espaço.

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Fixtures financeiras | **SONNET** | Dados sintéticos com cenário fechado |
| 2 | Leitura dos lançamentos e regra de validade | **OPUS** | Regra de negócio que decide o que é "falso" |
| 3 | Situação financeira, vencimento por data e ciclos | **OPUS** | Data, fuso e a correção do bug de origem |
| 4 | "Isento de Taxa" por passagem e a data | **OPUS** | Regra provisória, honestidade do dado |
| 5 | Integração com a tramitação: aviso e marcas | **OPUS** | Cruzamento de fontes sem alterar a posição |
| 6 | API do financeiro e auditoria | **SONNET** | Contrato pronto |
| 7 | Acesso, auditoria e varredura de vazamento | **OPUS** | Prova das regras de segurança |
| 8 | Front: bloco "Taxas e pagamentos" | **SONNET** | Interface |
| 9 | Front: marcas financeiras na linha do tempo e no fluxograma | **SONNET** | Interface |
| 10 | Configurações: parâmetros do financeiro | **SONNET** | Reuso da tela de Configurações |
| 11 | Roteiro de homologação e fechamento | **HAIKU** | Documentação a partir de cenários já definidos |

---

## Bloco 1 (SONNET): fixtures financeiras

**Commit sugerido:** `test(financeiro): fixtures fictícias de lançamentos de taxa para verificação e testes`

Estenda o ambiente de verificação e o construtor de fixtures da S7/S8 (sem criar outro mecanismo) com `LANCAMENTO_TAXA` no `SIGA2_LICENCIAMENTO` (DDL mínimo das colunas lidas, mais as ignoradas como nulas) e uma API fluente (`.comLancamento(PAGA).comDar(...).vencendoEm(...)`).

Cenários (números `N/2026` evidentes, dados fictícios; NUM_DAR fictício evidente):
- **FIN01** taxa **paga** (`PAGA`, DAR, emissão, vencimento, pagamento); **FIN02** `AGUARDANDO_PAGAMENTO` **no prazo**; **FIN03** `AGUARDANDO_PAGAMENTO` **vencido por data** (status ainda aguardando, vencimento ontem); **FIN04** `VENCIDA`; **FIN05** **isento** (`ISENTA` sem DAR, `TAXA_ISENTA = 1`, processado `P`); **FIN06** `ISENTA` com processado `N`; **FIN07** `CANCELADA` **sem DAR** ao lado de um lançamento válido; **FIN08** `CANCELADA` **com DAR** e `DATA_FIM`; **FIN09** `AGUARDANDO_EMISSAO` sem DAR (válido); **FIN10** ruído: `AGUARDANDO_EMISSAO` com `TAXA_ISENTA = 1`; **FIN11** taxa inicial **paga** e taxa da correção **aguardando**; **FIN12** **pago que virou isento** na correção (taxa paga e depois `ISENTA`); **FIN13** requerimento **sem** lançamento; **FIN14** volume: 10 cancelados sem DAR e 1 válido; **FIN15** o cenário **H18** da S7 (instância encerrada em `Event_0h1oige`, situação `AGUARDANDO_PAGAMENTO_DE_TAXAS`) com taxa **vencida por data**; **FIN16** requerimento **sem instância** (plano B) com taxa paga.
- **Vencimentos em torno de "hoje"** (ontem, hoje, amanhã) para os testes de data, relativos ao `Clock` de teste.
- Testes: o construtor gera exatamente o descrito; nenhum dado real; documente em `docs/13`.

## Bloco 2 (OPUS): leitura dos lançamentos e regra de validade

**Commit sugerido:** `feat(financeiro): leitura dos lançamentos de taxa e regra de validade`

- Novo módulo `financeiro` (domain, application, api, infrastructure), seguindo `docs/13` e o ArchUnit: depende **só por porta** de `tramitacao` (blocos 4 e 5) e de `auditoria`; não depende de `documento` nem de `dossie`.
- Porta `LancamentosDoRequerimento` e adaptador JDBC **único** (`SELECT` por `ID_REQUERIMENTO`, colunas da lista acima, ordenado por `DATA_EMISSAO` e `ID`, com limite e tempo limite) e versão **em lote** (`IN` de até 500, particionado).
- **Domínio:** `Lancamento` (imutável) e `ClassificarValidade` com a **regra de validade** exatamente como na decisão: válido = `AGUARDANDO_EMISSAO` (exceto o ruído `TAXA_ISENTA = 1`), `ISENTA`, ou qualquer outro com `NUM_DAR`; ignorados = `CANCELADA` sem DAR e o ruído. Cada lançamento ignorado conta em `ignorados` com o **motivo** (`FALSO_SEM_DAR`, `RUIDO_ISENTO_AGUARDANDO`), só no diagnóstico do Administrador.
- Status desconhecido (fora do enum) ou combinação inesperada (`PAGA` sem `DATA_PAGAMENTO`, `PAGA` sem DAR): lançamento **mantido** com o aviso `LANCAMENTO_INCONSISTENTE`, nunca descartado em silêncio.
- Nunca ler `CHAVE_GED`, `MENSAGEM` nem `ID_TITULACAO` (teste que inspeciona o SQL gerado).
- Testes (FIN01, FIN05 a FIN10, FIN13, FIN14): cada regra, contagem de ignorados, status desconhecido; **uma** consulta por requerimento e **uma por lote**.

## Bloco 3 (OPUS): situação financeira, vencimento por data e ciclos

**Commit sugerido:** `feat(financeiro): situação financeira, taxa vencida por data e ciclos da taxa`

- `SituacaoFinanceira` por lançamento: `EMITINDO` (aguardando emissão), `AGUARDANDO_PAGAMENTO`, `PAGA`, `VENCIDA`, `ISENTA`, `CANCELADA`, e o resumo do requerimento: `SEM_TAXA`, `ISENTO`, `EM_ABERTO`, `QUITADO`, `VENCIDO` (precedência documentada: `VENCIDO` > `EM_ABERTO` > `QUITADO` > `ISENTO`; vários lançamentos válidos são avaliados **um a um** e o resumo é derivado, sem somar o que não deve).
- **Taxa vencida** exatamente como na decisão, com `Clock` e `America/Cuiaba`, comparando **datas** (não instantes). Testes de virada do dia e de fuso (UTC × Cuiabá): vencimento **hoje** = não vencida; **ontem** = vencida; a conversão de `TIMESTAMP` do Oracle para data **não desloca o dia**.
- **Ciclos:** rotule por ordem de emissão ("1ª taxa", "2ª taxa"); a etiqueta "da correção" é do bloco 5 (precisa da tramitação).
- Valores em `BigDecimal`; valor ausente = "não informado", nunca zero.
- Testes: FIN01 a FIN04, FIN11, FIN12 e a precedência do resumo; propriedade: nenhum lançamento ignorado entra no resumo.

## Bloco 4 (OPUS): "Isento de Taxa" por passagem e a data

**Commit sugerido:** `feat(financeiro): registro de isenção por passagem com data de apoio provisória`

- Para cada lançamento válido `ISENTA`, uma entrada **"Isento de Taxa"** com a data pela **cadeia de apoio** (decisão), implementada em **uma única classe** `DataDaIsencao` com a ordem configurável por constante documentada: `DATA_EMISSAO` → entrada da passagem "Isenção confirmada" (S8, por porta; pode faltar) → vazia. Cada entrada traz `origemDaData` (`LANCAMENTO`, `FLUXO`, `AUSENTE`) para o diagnóstico e para a nota **"data a confirmar"** na tela quando não for a do lançamento.
- Requerimento isento **mais de uma vez** (cadastro e correção): várias entradas, em ordem.
- **Não** trate aqui "pago que virou isento" (S11): apenas **não esconda** nada que o permita (lançamento `PAGA` com DAR seguido de `ISENTA` aparecem os dois, em ordem).
- `STATUS_PROCESSADO = N` em `ISENTA`: sem tratamento especial; apenas nota no diagnóstico e em `docs/10`.
- Testes: FIN05, FIN06, FIN12; cada elo da cadeia; data ausente; várias isenções.
- Docs: `docs/15` (cadeia de apoio, **provisória**) e `docs/10` (a validar com dados reais na homologação).

## Bloco 5 (OPUS): integração com a tramitação: aviso e marcas

**Commit sugerido:** `feat(financeiro): aviso de taxa vencida na posição e marcas financeiras na tramitação`

- **Aviso `TAXA_VENCIDA`** (catálogo de avisos da S7): quando o resumo é `VENCIDO`, a posição ganha o aviso "Taxa vencida em dd/mm/aaaa" (gravidade `ATENCAO`), **sem alterar** etapa nem situação. Combinação com `FLUXO_ENCERRADO_SEM_STATUS` (S7/S8): os dois avisos aparecem, em linguagem que explica a causa ("o fluxo foi encerrado por taxa não paga no prazo; o SIGA ainda mostra *aguardando pagamento*").
- **Marcas financeiras nas passagens** da S8 (por porta, sem acoplar): em "Emissão da taxa" a data de emissão e o número do DAR; em "Aguardando pagamento" o vencimento e, se houve, a data do pagamento; em "Isenção confirmada" o "Isento de Taxa" do bloco 4; na **correção**, a etiqueta "da correção" para a taxa emitida após a entrada no ramo. Se a tramitação estiver `INDISPONIVEL` ou for plano B, as marcas **não são inventadas**: o financeiro segue completo por conta própria.
- **Lista da busca:** indicador financeiro opcional (em lote, bloco 2), com o limite de simultaneidade da S7.
- Testes (FIN03, FIN11, FIN15, FIN16): aviso sem alterar posição; marcas nas passagens certas; plano B e Camunda fora sem erro; **a posição devolvida é idêntica com e sem o financeiro**.

## Bloco 6 (SONNET): API do financeiro e auditoria

**Commit sugerido:** `feat(financeiro): endpoint do histórico financeiro e evento de auditoria`

- `GET /api/requerimentos/{id}/financeiro`: `resumo` (`SEM_TAXA`, `ISENTO`, `EM_ABERTO`, `QUITADO`, `VENCIDO`), `lancamentos[]` (ordem, rótulo "1ª taxa"/"da correção", situação, `emitidaEm`, `venceEm`, `pagaEm`, `numeroDar`, `valor`, `desconto`, `upf` conforme o parâmetro), `isencoes[]` ("Isento de Taxa", data e `dataAConfirmar`), `avisos[]`. **Nunca** `CHAVE_GED`, `MENSAGEM`, ids internos de lançamento para quem não é Administrador, nem diagnóstico de ignorados.
- `GET /api/processos/{numero}/financeiro`: um objeto por requerimento.
- Erros em `ProblemDetail` (400, 401, 403, 404); falha do banco → 200 com `estado = INDISPONIVEL`, sem 5xx.
- **Auditoria:** tipo `FINANCEIRO` (grupo `DOCUMENTOS`), com retrato do usuário e números, **sem** valores nem DAR no evento. Atualize `docs/11` e o filtro da tela de auditoria.
- Docs: `docs/07` (rotas e matriz), `docs/02` (próximos RF livres: histórico financeiro, taxa vencida, isenção; RN de validade do lançamento e de vencimento por data).
- Testes de contrato por perfil e cenário.

## Bloco 7 (OPUS): acesso, auditoria e varredura de vazamento

**Commit sugerido:** `test(financeiro): matriz de acesso e varredura de vazamento do financeiro`

- Estenda a matriz de acesso (`OPERADOR`, `OPERADOR_SIGILO`, `ADMINISTRADOR`, sem login) às rotas novas, com a mesma regra de consulta (RN07).
- **Varredura de vazamento** em respostas, logs, auditoria e DOM: `CHAVE_GED`, CPF (formas e 11 dígitos soltos), token, ids técnicos de lançamento (para não Administrador). Zero ocorrências em todos os cenários.
- Teste de negação: `RASTRO_MOSTRAR_VALORES` desligado remove valores, desconto e UPF de **todas** as respostas; o DAR continua.
- **Resiliência:** banco lento ou fora → `INDISPONIVEL` sem 5xx; **independência** (financeiro fora não afeta tramitação e documentos, e o inverso).
- Docs: `docs/10`, `docs/12`.

## Bloco 8 (SONNET): front, "Taxas e pagamentos"

**Commit sugerido:** `feat(front): bloco "Taxas e pagamentos" no detalhe do requerimento`

- No detalhe do requerimento, seção **Taxas e pagamentos** (recolhível): **resumo** com selo de texto e ícone (Sem taxa, Isento de taxa, Em aberto, Quitado, Vencido) e a lista de lançamentos em ordem: rótulo (1ª taxa, da correção), situação, emitida em, **vence em**, **paga em**, número do DAR, valor e desconto (conforme o parâmetro).
- **"Isento de Taxa em dd/mm/aaaa"** por isenção, com a nota "data a confirmar" quando for o caso. **Aviso "Taxa vencida em dd/mm/aaaa"** em destaque neutro, com a explicação curta ("O SIGA ainda não marca a taxa como vencida; o Rastro compara a data de vencimento com hoje").
- Estados: carregando, `SEM_TAXA`, `INDISPONIVEL` com "Tentar novamente" (o resto da tela segue). Valores em reais pela utilidade de formatação; datas pela utilidade única (Cuiabá, data pura sem deslocar o dia).
- Acessibilidade: tabela semântica, contraste AA, foco, celular sem rolagem horizontal (lista em cartões).
- Testes (Vitest e Playwright com API simulada): FIN01 a FIN06, FIN11, FIN13, FIN15; parâmetro de valores; celular; nenhum CPF/`CHAVE_GED` no DOM.
- Docs: `docs/08`.

## Bloco 9 (SONNET): front, marcas financeiras na linha do tempo e no fluxograma

**Commit sugerido:** `feat(front): marcas financeiras na linha do tempo e no fluxograma simplificado`

- Na **linha do tempo** (S8), cada passagem de taxa mostra suas marcas (emitida em, DAR, vence em, paga em, "Isento de Taxa em"), com a etiqueta "da correção" no ramo de correção.
- No **fluxograma**, a caixa "Aguardando pagamento" mostra a data do pagamento (ou o vencimento) e o selo **"Taxa vencida"** quando for o caso; "Isenção confirmada" mostra o "Isento de Taxa em". **Nenhuma caixa nova**, **nenhuma etapa futura**, sem losango, sem alterar a regra visual da S8.
- Sem Camunda (`INDISPONIVEL` ou plano B): as marcas **não aparecem** nas passagens e o bloco "Taxas e pagamentos" segue completo.
- No **bloco "Onde está agora"** (S7), o aviso "Taxa vencida" aparece junto com a situação do SIGA, sem trocar o texto da situação.
- Testes: HC07 + FIN11; H18/FIN15 (aviso e final "Encerrado por taxa não paga no prazo"); plano B + FIN16; P&B na impressão; 360 px.

## Bloco 10 (SONNET): configurações do financeiro

**Commit sugerido:** `feat(config): parâmetros do financeiro nas Configurações`

- Na seção **Rastro** das Configurações (só Administrador; evento único com antes e depois): **mostrar valores** (imediato), **limite de lançamentos por requerimento** (próxima leitura) e **tempo limite da consulta financeira** (próxima leitura). Credenciais não aparecem.
- Diagnóstico do Administrador (somente leitura, sem dado pessoal): contagem de lançamentos **ignorados por motivo** de um requerimento consultado (`FALSO_SEM_DAR`, `RUIDO_ISENTO_AGUARDANDO`) e a **origem da data** da isenção.
- Testes: alteração gera um evento com antes e depois; o Operador não vê a seção (403 `PERFIL_SEM_PERMISSAO`).
- Docs: `docs/12`.

## Bloco 11 (HAIKU): roteiro de homologação e fechamento

**Commit sugerido:** `docs(financeiro): roteiro de homologação e fechamento da S9`

- `docs/roteiros/s9-homologacao.md`: uma linha por cenário FIN01 a FIN16 (o que abrir, o que a tela deve mostrar, "Resultado"). Inclua a **conferência com dados reais (feita pelo responsável, fora do repositório)**: 5 requerimentos com taxa **paga**, 3 **isentos**, 3 com taxa **vencida por data**, 1 com taxa inicial e da correção, comparando o Rastro com o SIGA e com `LANCAMENTO_TAXA`; e a **validação da data do "Isento de Taxa"** (qual coluna vem preenchida nos isentos reais), que **fecha** a decisão provisória do bloco 4. **Nenhum número real entra no repositório.**
- Atualize `docs/14` (S10 painel do Gestor; S11 pago que virou isento, que usa os dados já lidos) e `docs/progresso/s9.md`; `docs/10` revisado (regras provisórias, índice recomendado, `PASSOU_POR_REAPROV_TAXA_CORREC`, `STATUS_PROCESSADO`).
- Varredura final de dados reais e `git diff --stat`.
- **Pendências da S6 que entram aqui:** (a) o roteiro inclui um passo para o responsável **rodar `/verificar-sprint 9` numa sessão nova** e guardar `docs/progresso/verificacao-s9.md`; (b) em `docs/10`, **reordene** as linhas de risco que ficaram fora de sequência (R-números), **sem renumerar** nem mudar o texto, e confira que nenhum R-número se repete; (c) em `docs/14`, deixe a seção antiga "S6 — Validação e apresentação" marcada como **decisão do responsável** (ele decide em qual sprint entra o material de apresentação), sem reescrevê-la.

---

## Critérios de aceite (ao final do bloco 11)

1. Para FIN01 a FIN16, o bloco mostra os lançamentos **válidos**, ignora os "falsos" e o ruído, e o resumo está correto.
2. **"Taxa vencida"** aparece por **data**, no fuso de Cuiabá, **sem trocar** a situação do Licenciamento, e some sozinha quando o lançamento for pago ou quando o SIGA passar a marcar `VENCIDA`.
3. **"Isento de Taxa"** com data (ou "data a confirmar"), uma entrada por isenção.
4. Nenhum `CHAVE_GED`, CPF, token ou id interno (para não Administrador) em resposta, tela, log ou auditoria; valores some com o parâmetro desligado.
5. O financeiro funciona **sem Camunda** (plano B e Camunda fora) e não afeta nem é afetado pelos outros blocos.
6. Todo SQL novo é `SELECT`, parametrizado, com limite e tempo limite, num único componente; nada gravado no Oracle.
7. Testes verdes, ArchUnit verde, `docs/` e `docs/progresso/s9.md` atualizados.
