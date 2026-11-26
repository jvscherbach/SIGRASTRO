# Roteiro de homologação da S9: financeiro (taxas, DAR e pagamentos)

**Quem executa:** o responsável pelo projeto, manualmente. O desenvolvimento (Claude Code) **nunca** se conecta ao SIGA real, ao Camunda real, ao SCORP nem à API de arquivos (D27, D34). Os cenários FIN01 a FIN16 e VENCE_ONTEM, VENCE_HOJE e VENCE_AMANHA são **fictícios**, gravados no Oracle de teste (Testcontainers) pelo `CamundaFicticioBuilder` e pelo `CenariosFicticiosDaS9` (ids 9001 a 9199). A conferência com dados reais é a seção 5, **fora do repositório**.

**O que a S9 entrega e este roteiro confere:**

- o histórico financeiro do requerimento no bloco **"Taxas e pagamentos"** do detalhe: resumo, lançamentos válidos em ordem, DAR, vencimento, pagamento, valor e desconto (blocos 1 a 8);
- a regra de validade: **falsos** (`CANCELADA` sem DAR) e o **ruído** (`AGUARDANDO_EMISSAO` com `TAXA_ISENTA = 1`) são ignorados (bloco 2);
- **"Taxa vencida"** calculada **por data**, no fuso de Cuiabá, sem trocar a situação do Licenciamento (bloco 3);
- **"Isento de Taxa"** com data, ou "data a confirmar" / "data não registrada" (bloco 4);
- o aviso "Taxa vencida" na posição e as **marcas financeiras** na linha do tempo e no fluxograma da tramitação (bloco 5 e 9);
- a API, a matriz de acesso, a varredura de vazamento e a independência do financeiro (blocos 6 e 7);
- os parâmetros do financeiro na aba **Rastro** das Configurações e o diagnóstico do Administrador (bloco 10).

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em [`docs/14`](../14-plano-de-sprints.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que falhar.

> **Dados pessoais e arquivos reais:** CPF, CNPJ, nomes, números de processo, números de DAR e ids reais ficam como marcadores (`<ID_REQUERIMENTO>`, `<NUMERO_DO_DAR>`, `<CPF_DE_TESTE>`). **Nenhuma captura de tela, exportação de auditoria ou consulta com dado real vai para o repositório**: guarde numa pasta local fora dele (`amostras-locais/` está no `.gitignore`) e apague depois.

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

Resultado esperado: `api` e `front` em execução; abra `http://localhost:8080`. Usuários de demonstração anteriores à L1 (hoje o login é `operador`, `analista` ou `admin`, com senhas em variáveis; o Gestor é criado pelo `admin`).

- [ ] OK / [ ] Falhou — observação:

**Situação do ambiente de tela:** os requerimentos fictícios existem **só no Oracle de teste** (R69), e o ambiente com SIGA fictício para abri-los na tela ainda **não existe** (R66). Por isso a coluna **Tela** fica `Não executado` até haver esse ambiente; a coluna **Prova automática** diz o que os testes já provam. Quem marcar `OK` na tela precisa tê-la aberto de fato.

**Atenção às datas (NÃO CONFIRMADO como o ambiente fictício vai fixar o tempo):** as datas dos cenários são **fixas**. `dias(n)` é 02/03/2026 às 08h + n dias, e `HOJE` dos testes é **20/03/2026** (Cuiabá). Os vencimentos "ontem", "hoje" e "amanhã" são 19/03, 20/03 e 21/03/2026. Se o ambiente usar o relógio **real**, todo vencimento anterior ao dia de hoje aparece como **vencido** (por exemplo, FIN02 e VENCE_AMANHA ficariam "Vencido"). As expectativas da seção 2 valem para **20/03/2026** (relógio de teste). Fora disso, leia "vencido" como "vencido em relação ao dia de hoje" e não marque como falha.

## 1. Como ler as tabelas

- **Resumo** (selo "Situação financeira") é o de maior precedência entre os lançamentos válidos: **Vencido** > **Em aberto** > **Quitado** > **Isento de taxa** > **Sem taxa**. Lançamento cancelado ou ignorado não pesa.
- **Situação** de cada lançamento é a do Licenciamento, **como está**. A seção nunca troca a situação: "Taxa vencida" é um **selo ao lado**, só quando a taxa venceu por data.
- **Datas** são as da tela, no formato dd/mm/aaaa (fuso de Cuiabá, data pura, nunca desloca o dia). Data ausente aparece como "—".
- **Valor** ausente aparece como "Não informado", **nunca** R$ 0,00. Com o parâmetro `RASTRO_MOSTRAR_VALORES` desligado, as colunas Valor e Desconto **não existem**; o DAR continua.
- **Taxa**: "1ª taxa", "2ª taxa" (por ordem de emissão) e "Taxa da correção" (só quando a tramitação mostra o ramo de correção e a emissão é posterior à entrada nele). A isenção aparece como "Isenção" na coluna Taxa.
- **Nunca** aparecem id de lançamento, diagnóstico, CPF, `CHAVE_GED` nem chave de arquivo (para não Administrador).

## 2. Cenários FIN01 a FIN16

| Cenário | Id | O que abrir | O que a tela deve mostrar | Prova automática (docs/progresso/s9.md) | Tela | Resultado |
|---|---|---|---|---|---|---|
| FIN01 | 9001 | Detalhe do requerimento 9001 | Resumo **Quitado**. Uma linha: 1ª taxa, **Paga**, emitida 04/03/2026, vence 14/03/2026, paga 07/03/2026, DAR `FICT-0001`, valor R$ 1.250,50, desconto R$ 0,00. Sem aviso de taxa vencida. Tramitação: análise (como o HC01 até ali) | `AvaliarSituacaoTest`, `LancamentosDoSigaJdbcTest`, `financeiro.e2e.ts` (FIN01) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN02 | 9002 | Detalhe do 9002 | Resumo **Em aberto**. Uma linha: 1ª taxa, **Aguardando pagamento**, emitida 04/03/2026, vence 21/03/2026, sem pagamento ("—"), DAR `FICT-0002`, valor R$ 980,00. **Sem** aviso e **sem** selo "Taxa vencida" (vence amanhã). Tramitação: espera de pagamento | `AvaliarSituacaoTest`, `financeiro.component.spec.ts` (FIN02) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN03 | 9003 | Detalhe do 9003 e "Onde está agora" | Resumo **Vencido**. Situação continua **Aguardando pagamento** (não "Vencida"), com o selo vermelho **"Taxa vencida"** ao lado. Aviso em destaque: **"Taxa vencida em 19/03/2026."** com a explicação "O SIGA ainda não marca a taxa como vencida; o Rastro compara a data de vencimento com hoje." Na tramitação, a espera mostra "Vence em 19/03/2026" e "Taxa vencida em 19/03/2026". "Onde está agora" mostra o aviso e a linha "Situação no Licenciamento" com a situação do SIGA, sem troca | `AvaliarSituacaoTest`, `FinanceiroNaTramitacaoHttpTest`, `financeiro.e2e.ts` (FIN03), `financeiro.component.spec.ts` | `Não executado` | [ ] OK / [ ] Falhou |
| FIN04 | 9004 | Detalhe do 9004 | Resumo **Vencido**. Situação **Vencida** (marcada pelo SIGA), vence 14/03/2026. **Sem** a marca "Taxa vencida" por data e **sem** o aviso acrescentado pelo Rastro (a marca do SIGA vale como está) | `AvaliarSituacaoTest`, `financeiro.component.spec.ts` (FIN04) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN05 | 9005 | Detalhe do 9005 (isento, atribuição) | Selo **Isento de taxa**. Uma linha: "Isenção", **Isenta**, emitida 04/03/2026, sem DAR ("—"), valor "Não informado". Seção "Isenções": **"Isento de Taxa em 04/03/2026"**, **sem** nota "data a confirmar" (a data é a do lançamento). Tramitação: "Isenção confirmada" com "Isento de Taxa em 04/03/2026" | `DataDaIsencaoTest`, `financeiro.e2e.ts` (FIN05) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN06 | 9006 | Detalhe do 9006 (isento, processado N) | Igual ao FIN05 na tela: "Isento de Taxa em 04/03/2026", sem nota. O `STATUS_PROCESSADO = N` **não aparece** na tela (só no diagnóstico do Administrador). **Divergência registrada:** o e2e de front (`financeiro.e2e.ts`) simula o FIN06 **sem data** ("data não registrada"), mas o cenário do Oracle grava `DATA_EMISSAO` e a regra (docs/15) dá a data do lançamento. Conferir qual é o comportamento real da tela; **NÃO CONFIRMADO** qual dos dois está certo | `DataDaIsencaoTest`, `financeiro.e2e.ts` (FIN06, **com a divergência acima**) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN07 | 9007 | Detalhe do 9007 | Resumo **Em aberto**. **Uma** linha: a 1ª taxa, **Aguardando pagamento**, DAR `FICT-0007`, emitida 04/03/2026, vence 21/03/2026, valor R$ 980,00. **A cancelada sem DAR não aparece** (é falsa, ignorada) | `ClassificarValidadeTest`, `LancamentosDoSigaJdbcTest` (FIN07) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN08 | 9008 | Detalhe do 9008 | Uma linha: **Cancelada**, DAR `FICT-0008`, emitida 04/03/2026, vence 25/03/2026, **fim** 08/03/2026 (encerrada). Resumo: **Sem taxa**, porque cancelada não pesa (regra de docs/15). **Ponto a conferir:** a tela mostra uma guia cancelada com resumo "Sem taxa"; a regra está documentada, mas a apresentação é **NÃO CONFIRMADO** como a decisão final | `ClassificarValidadeTest`, `LancamentosDoSigaJdbcTest` (FIN08) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN09 | 9009 | Detalhe do 9009 | Resumo **Em aberto** (emitindo pesa como em aberto). Uma linha: **Aguardando emissão** (rótulo da tabela de docs/08), sem DAR, sem datas de emissão, pagamento ou vencimento ("—") | `ClassificarValidadeTest`, `LancamentosDoSigaJdbcTest` (FIN09) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN10 | 9010 | Detalhe do 9010 (ruído) | **Nenhuma linha** de taxa (o `AGUARDANDO_EMISSAO` com `TAXA_ISENTA = 1` é ignorado). Resumo **Sem taxa**, com a frase "Este requerimento não tem taxa registrada.", **mesmo com** a tramitação mostrando a espera de pagamento. Ponto a conferir com o responsável: é o comportamento que a regra pede (ruído ignorado por ora, P43 b), não um erro de tela | `ClassificarValidadeTest`, `LancamentosDoSigaJdbcTest` (FIN10) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN11 | 9011 | Detalhe do 9011 e tramitação (HC07 parado na espera da correção) | Resumo **Em aberto**. Duas linhas, em ordem: **1ª taxa**, **Paga**, emitida 04/03/2026, vence 14/03/2026, paga 07/03/2026, DAR `FICT-0011`, R$ 1.250,50 / R$ 0,00; e **Taxa da correção**, **Aguardando pagamento**, emitida 16/03/2026, vence 30/03/2026, DAR `FICT-0111`, R$ 310,25 / R$ 0,00. **Sem** aviso. Tramitação: "Emissão da taxa" com "Emitida em" e "DAR"; na espera da correção, "Vence em 30/03/2026" e "da correção" | `AvaliarSituacaoTest`, `FinanceiroNaTramitacaoHttpTest`, `financeiro-tramitacao.e2e.ts` (HC07 + FIN11) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN12 | 9012 | Detalhe do 9012 | Resumo **Quitado**. Duas linhas: **1ª taxa**, **Paga** (DAR `FICT-0012`, R$ 1.250,50, emitida 04/03/2026, vence 14/03/2026, paga 07/03/2026), e a **"Isenção"**, **Isenta**, emitida 16/03/2026, sem DAR. Seção "Isenções": **"Isento de Taxa em 16/03/2026"**. **Os dois aparecem, em ordem, sem destaque** (o destaque de "pago que virou isento" é da S11) | `DataDaIsencaoTest`, `AvaliarSituacaoTest` | `Não executado` | [ ] OK / [ ] Falhou |
| FIN13 | 9013 | Detalhe do 9013 | Selo **Sem taxa** e a frase "Este requerimento não tem taxa registrada.", **sem tabela** | `financeiro.e2e.ts` (FIN13), `financeiro.component.spec.ts` (FIN13) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN14 | 9014 | Detalhe do 9014 | Resumo **Em aberto**. **Uma** linha: 1ª taxa, **Aguardando pagamento**, DAR `FICT-0014`, emitida 05/03/2026, vence 21/03/2026, R$ 980,00. **Os 10 falsos não aparecem** | `LancamentosDoSigaJdbcTest` (FIN14) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN15 | 9015 | Detalhe do 9015 e "Onde está agora" (H18 da S7: encerrada em `Event_0h1oige`) | Resumo **Vencido**. Situação do SIGA sem troca (código `AGUARDANDO_PAGAMENTO_DE_TAXAS`, como o texto da tela da S7 mostra) com o selo "Taxa vencida"; aviso **"Taxa vencida em 19/03/2026."**. Em "Onde está agora", **os dois avisos** aparecem: o de fluxo encerrado sem status (`FLUXO_ENCERRADO_SEM_STATUS`) e o de taxa vencida. Final da tramitação: **"Encerrado por taxa não paga no prazo"**, sem alterar a situação | `FinanceiroNaTramitacaoHttpTest`, `financeiro.e2e.ts` (FIN15), `financeiro-tramitacao.e2e.ts` (H18 + FIN15) | `Não executado` | [ ] OK / [ ] Falhou |
| FIN16 | 9016 | Detalhe do 9016 (**sem instância** no Camunda, plano B) | Resumo **Quitado**. Uma linha: 1ª taxa, **Paga**, DAR `FICT-0016`, emitida 04/03/2026, vence 14/03/2026, paga 07/03/2026, R$ 1.250,50. A seção "Taxas e pagamentos" está **completa**. Tramitação: selo "Histórico aproximado" e **sem marcas** financeiras, sem erro | `FinanceiroNaTramitacaoHttpTest`, `financeiro-tramitacao.e2e.ts` (plano B + FIN16) | `Não executado` | [ ] OK / [ ] Falhou |

**Observações sobre a tabela:**

- As datas são as dos cenários do Oracle de teste (seção 0, "Atenção às datas"). A emissão dos lançamentos iniciais segue `dias(2)` = 04/03/2026; a isenção do FIN12 e a taxa da correção do FIN11 seguem `dias(14)` = 16/03/2026.
- **Provas automáticas:** os nomes vêm do registro de cada bloco em `docs/progresso/s9.md`. A coluna diz o que os testes **já** provam; ela não substitui a conferência na tela.

## 3. Cenários de apoio (testes de data)

| Cenário | Id | O que abrir | O que a tela deve mostrar | Prova automática | Tela | Resultado |
|---|---|---|---|---|---|---|
| VENCE_ONTEM | 9101 | Detalhe do 9101 | Resumo **Vencido**, situação **Aguardando pagamento**, selo "Taxa vencida", aviso **"Taxa vencida em 19/03/2026."** | `LancamentosDoSigaJdbcTest` (ontem), `AvaliarSituacaoTest` | `Não executado` | [ ] OK / [ ] Falhou |
| VENCE_HOJE | 9102 | Detalhe do 9102 | Resumo **Em aberto**, vence **20/03/2026**, **sem** aviso e **sem** selo (vencer hoje ainda não é vencida) | `LancamentosDoSigaJdbcTest` (hoje), `AvaliarSituacaoTest` | `Não executado` | [ ] OK / [ ] Falhou |
| VENCE_AMANHA | 9103 | Detalhe do 9103 | Resumo **Em aberto**, vence **21/03/2026**, **sem** aviso e **sem** selo | `LancamentosDoSigaJdbcTest` (amanhã), `AvaliarSituacaoTest` | `Não executado` | [ ] OK / [ ] Falhou |

## 4. Perfis, parâmetros e estados

| Verificação | Como conferir | Prova automática | Tela | Resultado |
|---|---|---|---|---|
| Operador vê a mesma seção, **sem** campos de Administrador (id de lançamento, diagnóstico, status de processamento) | Entrar como `operador`, abrir o 9011 | `financeiro.e2e.ts` (Operador), `VarreduraDoFinanceiroHttpTest` | `Não executado` | [ ] OK / [ ] Falhou |
| Parâmetro de valores desligado (`RASTRO_MOSTRAR_VALORES=false`): sem colunas Valor e Desconto, sem "R$" na seção, DAR continua | Abrir o 9001 com o parâmetro desligado (só no ambiente de teste) | `financeiro.e2e.ts` (valores desligados), `VarreduraSemValoresHttpTest`, `FinanceiroResponseTest` | `Não executado` | [ ] OK / [ ] Falhou |
| `INDISPONIVEL`: aviso neutro "Não foi possível consultar as taxas agora. O restante da página não foi afetado.", botão **Tentar novamente** (que lê de novo) | Derrubar o banco de leitura no ambiente de teste | `financeiro.e2e.ts` (INDISPONIVEL), `IndependenciaDoFinanceiroHttpTest` | `Não executado` | [ ] OK / [ ] Falhou |
| Independência: financeiro fora não afeta tramitação nem documentos, e o inverso | Repetir com a tramitação indisponível e com o financeiro indisponível | `financeiro.e2e.ts` (independência), `IndependenciaDoFinanceiroHttpTest` | `Não executado` | [ ] OK / [ ] Falhou |
| Aba **Rastro** das Configurações: três parâmetros (mostrar valores, limite de lançamentos, tempo limite), com faixa e padrão, **sem credencial** | Entrar como `admin` e abrir Configurações, aba Rastro | `configuracoes-financeiro.e2e.ts` (aba Rastro), `ParametrosHttpTest` | `Não executado` | [ ] OK / [ ] Falhou |
| Alterar um parâmetro gera **um** evento `CONFIG` com antes e depois | Salvar uma alteração e conferir a auditoria (sem valor de DAR) | `configuracoes-financeiro.e2e.ts` (PUT único), `ParametrosHttpTest` | `Não executado` | [ ] OK / [ ] Falhou |
| Diagnóstico do Administrador (somente leitura): ignorados por motivo e origem da data da isenção, sem valor, DAR, id de lançamento nem CPF | Informar o id interno do 9005 (ou do 9006) no diagnóstico | `configuracoes-financeiro.e2e.ts` (diagnóstico), `diagnostico-financeiro.component.spec.ts` | `Não executado` | [ ] OK / [ ] Falhou |
| Operador e Gestor sem a seção de parâmetros (403 `PERFIL_SEM_PERMISSAO`) | Entrar como `operador` e como o Gestor e abrir a aba Rastro | `configuracoes-financeiro.e2e.ts` (Operador), `ParametrosHttpTest` | `Não executado` | [ ] OK / [ ] Falhou |
| Impressão em preto e branco: o selo "Taxa vencida" se distingue por borda e texto | Imprimir o 9015 com a tela em escala de cinza | `financeiro-tramitacao.e2e.ts` (impressão) | `Não executado` | [ ] OK / [ ] Falhou |
| Celular de 360 px: lista em cartões, sem rolagem horizontal da página, com e sem aviso | Abrir o 9011 e o 9003 em 360 px | `financeiro.e2e.ts` (360 px), `financeiro-tramitacao.e2e.ts` (360 px), `configuracoes-financeiro.e2e.ts` (360 px) | `Não executado` | [ ] OK / [ ] Falhou |
| Nenhum CPF, `CHAVE_GED`, token nem id interno (para não Administrador) em tela, resposta, log ou auditoria | Percorrer as telas dos três perfis e procurar os termos | `VarreduraDoFinanceiroHttpTest`, `financeiro.component.spec.ts` | `Não executado` | [ ] OK / [ ] Falhou |

## 5. Conferência com dados reais (responsável, fora do repositório)

**Não há número real neste arquivo.** Use os marcadores, guarde as anotações em `amostras-locais/` (fora do Git) e apague depois. Compare o Rastro com o **SIGA** (tela de consulta de taxas do próprio SIGA) e com a tabela `LANCAMENTO_TAXA` pela sua própria consulta, **fora do Rastro**. O Rastro só faz `SELECT` nessa tabela; as consultas abaixo são para a sua conferência.

### 5.1 Amostra

Amostra pedida: **5 requerimentos com taxa paga**, **3 isentos**, **3 com taxa vencida por data** e **1 com taxa inicial e da correção**.

| Marcador | Grupo | Rastro: resumo e lançamentos | SIGA: situação e DAR | `LANCAMENTO_TAXA` confere? | Observação |
|---|---|---|---|---|---|
| `<ID_REQUERIMENTO_PAGO_1>` | Taxa paga | [ ] Quitado [ ] Outro: ___ | [ ] Confere [ ] Não confere | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_PAGO_2>` | Taxa paga | [ ] Quitado [ ] Outro: ___ | [ ] Confere [ ] Não confere | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_PAGO_3>` | Taxa paga | [ ] Quitado [ ] Outro: ___ | [ ] Confere [ ] Não confere | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_PAGO_4>` | Taxa paga | [ ] Quitado [ ] Outro: ___ | [ ] Confere [ ] Não confere | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_PAGO_5>` | Taxa paga | [ ] Quitado [ ] Outro: ___ | [ ] Confere [ ] Não confere | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_ISENTO_1>` | Isento | [ ] Isento de taxa [ ] Outro: ___ | [ ] Confere [ ] Não confere | [ ] Sim [ ] Não | Ver 5.3 |
| `<ID_REQUERIMENTO_ISENTO_2>` | Isento | [ ] Isento de taxa [ ] Outro: ___ | [ ] Confere [ ] Não confere | [ ] Sim [ ] Não | Ver 5.3 |
| `<ID_REQUERIMENTO_ISENTO_3>` | Isento | [ ] Isento de taxa [ ] Outro: ___ | [ ] Confere [ ] Não confere | [ ] Sim [ ] Não | Ver 5.3 |
| `<ID_REQUERIMENTO_VENCIDO_1>` | Vencida por data | [ ] Vencido com selo e aviso [ ] Outro: ___ | Situação do SIGA sem troca: [ ] Sim [ ] Não | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_VENCIDO_2>` | Vencida por data | [ ] Vencido com selo e aviso [ ] Outro: ___ | Situação do SIGA sem troca: [ ] Sim [ ] Não | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_VENCIDO_3>` | Vencida por data | [ ] Vencido com selo e aviso [ ] Outro: ___ | Situação do SIGA sem troca: [ ] Sim [ ] Não | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_CORRECAO_1>` | Taxa inicial e da correção | [ ] "1ª taxa" e "Taxa da correção" na ordem certa [ ] Outro: ___ | [ ] Confere [ ] Não confere | [ ] Sim [ ] Não | Ver 5.4 |

Se algum requerimento real não bater com o SIGA ou com `LANCAMENTO_TAXA`, **não** corrija a tela às pressas: registre em `docs/10` a diferença, com o marcador (nunca o número), o campo e a regra, e trate como pendência.

### 5.2 Consultas de conferência (somente `SELECT`)

Use o usuário de consulta do seu ambiente, fora do Rastro. Troque o marcador pelo id real **só no seu terminal**.

Lançamentos de um requerimento (sem `CHAVE_GED`, `MENSAGEM` nem `ID_TITULACAO`, que o Rastro não lê):

```sql
SELECT ID, STATUS, TAXA_ISENTA, STATUS_PROCESSADO, NUM_DAR, DATA_EMISSAO, DATA_VENCIMENTO,
       DATA_PAGAMENTO, DATA_FIM, PASSOU_POR_REAPROV_TAXA_CORREC
  FROM SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA
 WHERE ID_REQUERIMENTO = <ID_REQUERIMENTO>
 ORDER BY DATA_EMISSAO, ID
 FETCH FIRST 50 ROWS ONLY;
```

Tipos reais das colunas (P43 a, NÃO CONFIRMADO até esta consulta):

```sql
SELECT COLUMN_NAME, DATA_TYPE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER = 'SIGA2_LICENCIAMENTO' AND TABLE_NAME = 'LANCAMENTO_TAXA'
 ORDER BY COLUMN_NAME;
```

Índices existentes (P42 b e R75):

```sql
SELECT INDEX_NAME, COLUMN_NAME, COLUMN_POSITION
  FROM ALL_IND_COLUMNS
 WHERE TABLE_OWNER = 'SIGA2_LICENCIAMENTO' AND TABLE_NAME = 'LANCAMENTO_TAXA'
 ORDER BY INDEX_NAME, COLUMN_POSITION;
```

Status fora do enum e valores de `TAXA_ISENTA` e `STATUS_PROCESSADO` (P43 a, e):

```sql
SELECT DISTINCT STATUS, TAXA_ISENTA, STATUS_PROCESSADO
  FROM SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA;
```

Quantos lançamentos sem DAR existem por status (P43 c, a regra de validade):

```sql
SELECT STATUS, COUNT(*) AS QUANTIDADE
  FROM SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA
 WHERE NUM_DAR IS NULL
 GROUP BY STATUS;
```

Anote **só contagens e enums**, nunca os números de DAR nem os ids.

### 5.3 Validação da data do "Isento de Taxa" (fecha a decisão provisória do bloco 4, P44)

Para cada isento real (`<ID_REQUERIMENTO_ISENTO_1>` a `_3`), anote qual coluna vem preenchida e se a data coincide com a data da isenção que o SIGA mostra:

| Marcador | `DATA_EMISSAO` preenchida? | `DATA_PAGAMENTO` | `DATA_VENCIMENTO` | Data da isenção no SIGA (dd/mm/aaaa) | `DATA_EMISSAO` coincide? | Data da "Isenção confirmada" na tramitação, se houver |
|---|---|---|---|---|---|---|
| `<ID_REQUERIMENTO_ISENTO_1>` | [ ] Sim [ ] Não | [ ] Sim [ ] Não | [ ] Sim [ ] Não | | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_ISENTO_2>` | [ ] Sim [ ] Não | [ ] Sim [ ] Não | [ ] Sim [ ] Não | | [ ] Sim [ ] Não | |
| `<ID_REQUERIMENTO_ISENTO_3>` | [ ] Sim [ ] Não | [ ] Sim [ ] Não | [ ] Sim [ ] Não | | [ ] Sim [ ] Não | |

**Como fechar:**

- Se `DATA_EMISSAO` coincide com a data da isenção nos três, a cadeia `LANCAMENTO` → `FLUXO` → `AUSENTE` fica com o primeiro elo confirmado. Registre em `docs/10` (P44) e a nota "data a confirmar" deixa de aparecer para esses casos.
- Se a data da isenção aparece em **outra coluna** ou em **nenhuma**, registre em `docs/10` (P44) qual coluna, e **não mude o código** nesta sessão: a mudança da cadeia (`DataDaIsencao.CADEIA`) é trabalho de código e de outra sessão.
- Se um isento real vier **sem** `DATA_EMISSAO`, a tela cai no elo `FLUXO` (passagem "Isenção confirmada") com a nota "data a confirmar", ou em "data não registrada". Anote qual dos dois aconteceu.

### 5.4 Pontos a conferir na amostra

- **Taxa inicial e da correção** (`<ID_REQUERIMENTO_CORRECAO_1>`): a guia da correção foi emitida **depois** da entrada no ramo de correção? A etiqueta "Taxa da correção" aparece só nela? (P45 a, d.) Em duas emissões no mesmo dia, a mais recente é a que fica (P45 b).
- **`PASSOU_POR_REAPROV_TAXA_CORREC`** aparece só no diagnóstico do Administrador, **nunca como regra**. Confira o que o campo significa com o responsável do SIGA (P43 d). Se nada for confirmado, o campo continua só de diagnóstico.
- **Hora de `DATA_VENCIMENTO`** (P43 g): o SIGA grava a hora? Se o vencimento estiver em instante UTC, a data pode ficar um dia adiante; confira com um vencimento real.
- **`PAGA` com `DATA_FIM`** (P43 h): o Rastro conta como quitado. Confirme se existe no SIGA e o que significa.
- **Status fora do enum** (P43 e): se a consulta de 5.2 trouxer status diferente dos seis do enum, o Rastro mostra "Situação não reconhecida" e o resumo `INDETERMINADO` quando todos forem desconhecidos (P43 j). Anote.
- **Lançamentos sem DAR fora de `CANCELADA`** (P43 c): quantos existem. Pela regra, `AGUARDANDO_PAGAMENTO`, `PAGA` e `VENCIDA` sem DAR ficam **mantidos** com o aviso `LANCAMENTO_INCONSISTENTE`. Se o responsável decidir ignorá-los, é uma linha em `ClassificarValidade` (decisão de código, outra sessão).
- **Ruído** `AGUARDANDO_EMISSAO` com `TAXA_ISENTA = 1` (P43 b): quantos existem; continuam ignorados por ora.
- **Índice em `ID_REQUERIMENTO`** (P42 b, R75): se a consulta de 5.2 não encontrar índice, registre em `docs/10` como recomendação à equipe do SIGA. O Rastro não cria índice (somente leitura).

## 6. Verificação da sprint (`/verificar-sprint 9`)

Passo final, para o responsável, **numa sessão nova** do Claude Code (os agentes e o comando só carregam em sessão nova; `docs/claude-code/agentes.md`):

1. Abra uma sessão nova no repositório.
2. Rode `/verificar-sprint 9`. O comando dispara `revisor-seguranca`, `executor-de-testes` e `atualizador-de-docs` em paralelo.
3. Confira que o relatório foi gravado em `docs/progresso/verificacao-s9.md` e que nenhum código foi corrigido nem commitado pelo comando.
4. Os achados do relatório viram pendência em `docs/10` ou correção em sessão própria.

- [ ] OK / [ ] Falhou — observação:

## 7. Pendências que a homologação precisa fechar

- **P43 (NÃO CONFIRMADO):** tipos das colunas, lançamentos sem DAR, ruído, `PASSOU_POR_REAPROV_TAXA_CORREC`, hora de `DATA_VENCIMENTO`, `PAGA` com `DATA_FIM` e o resumo `INDETERMINADO`. Seção 5.2 e 5.4.
- **P44 (PROVISÓRIO):** coluna que dá a data do "Isento de Taxa" e a cadeia `DataDaIsencao.CADEIA`. Seção 5.3 fecha.
- **P45 (NÃO CONFIRMADO):** casamento guia × passagem por data, duas emissões no mesmo dia, ids de etapa fixos em `CaminhoDoFluxo`, "da correção" na primeira passagem do ramo. Seção 5.4.
- **P42 (b) e R75:** índice de `LANCAMENTO_TAXA(ID_REQUERIMENTO)`, recomendação à equipe do SIGA. Seção 5.2.
- **R76 (NÃO CONFIRMADO no DOM):** a tabela de riscos registra a varredura do DOM como pendente do bloco 8; `docs/08` registra a checagem de CPF e `CHAVE_GED` no DOM no bloco 8. Conferir se a varredura cobre todos os cenários da S9.
- **Divergência de FIN06** entre o e2e de front (sem data) e o cenário do Oracle (data da emissão). Seção 2, FIN06.
- **FIN08 e FIN10** (apresentação de guia cancelada com resumo "Sem taxa" e de ruído com "Sem taxa" durante a espera): regra documentada, apresentação a confirmar.
- **R66:** o ambiente com SIGA fictício não existe; a coluna **Tela** fica `Não executado`.

Registre o resultado em [`docs/14`](../14-plano-de-sprints.md). Cada falha vira pendência em [`docs/10`](../10-pendencias-e-riscos.md).

## 8. Critérios de aceite da S9

1. Para FIN01 a FIN16, a tela mostra os lançamentos **válidos**, ignora os falsos e o ruído, e o resumo está correto (seção 2).
2. **"Taxa vencida"** aparece por **data**, no fuso de Cuiabá, **sem trocar** a situação do Licenciamento, e some sozinha quando o lançamento for pago ou quando o SIGA passar a marcar `VENCIDA` (FIN03, FIN04, VENCE_ONTEM, VENCE_HOJE).
3. **"Isento de Taxa"** com data (ou "data a confirmar"), uma entrada por isenção (FIN05, FIN06, FIN12).
4. Nenhum `CHAVE_GED`, CPF, token ou id interno (para não Administrador) em resposta, tela, log ou auditoria; valores somem com o parâmetro desligado (seção 4).
5. O financeiro funciona **sem Camunda** (FIN16, plano B, e Camunda fora) e não afeta nem é afetado pelos outros blocos (seção 4).
6. Todo SQL novo é `SELECT`, parametrizado, com limite e tempo limite, num único componente; nada gravado no Oracle.
7. Testes verdes, ArchUnit verde, `docs/` e `docs/progresso/s9.md` atualizados; `/verificar-sprint 9` guardado (seção 6).

- [ ] OK / [ ] Falhou — observação:
