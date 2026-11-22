# Roteiro de homologação da S8: histórico, com quem esteve e fluxograma simplificado

**Quem executa:** o responsável pelo projeto, manualmente. O desenvolvimento (Claude Code) **nunca** se conecta ao SIGA real, ao Camunda real, ao SCORP nem à API de arquivos (D27, D34). Os cenários HC01 a HC15, LC01 a LC03, HF01 a HF04, LF01 a LF05 e PB01 a PB03 são **fictícios**, gravados no Oracle de teste (Testcontainers) pelo `CamundaFicticioBuilder`; a conferência com dados reais é a seção 6, fora do repositório.

**O que a S8 entrega e este roteiro confere:**

- a seção **Tramitação** no detalhe do requerimento: a **linha do tempo** com entrada, saída, duração, com quem esteve em cada etapa, as voltas que de fato aconteceram e o motivo de cada uma (blocos 3, 6 e 8);
- o **fluxograma simplificado**, em linha reta, só com o que já passou e a etapa atual, com o final como bola preta rotulada e a "Verificação da revisão do processo" como caixa própria; impressão em preto e branco (bloco 9);
- o **plano B** (histórico aproximado, sem fluxograma) e os **estados de erro** (`INDISPONIVEL`, `NAO_SUPORTADO`, `HISTORICO_TRUNCADO`) (bloco 10);
- as correções herdadas da S7: **P38** (verificação da revisão = "Concluído" na posição) e **P39** ("Camunda" fora das telas de consulta, para todos os perfis) (bloco 8);
- a matriz de acesso e a varredura de vazamento da tramitação (bloco 11).

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em [`docs/14`](../14-plano-de-sprints.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que falhar.

> **Dados pessoais e arquivos reais:** CPF, CNPJ, nomes e números de processo reais ficam como marcadores (`<CPF_DE_TESTE>`, `<CNPJ_DE_TESTE>`, `<ID_HIDRICO_1>`, `<ID_LAC_1>`, `<PROCESSO_DE_TESTE>`). **Nenhuma captura de tela, exportação de auditoria ou consulta com dado real vai para o repositório**: guarde numa pasta local fora dele (`amostras-locais/` está no `.gitignore`) e apague depois.

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

Resultado esperado: `api` e `front` em execução; abra `http://localhost:8080`. Usuários de demonstração na tela de login: `operador.demo`, `gestor.demo` e `admin.demo`.

- [ ] OK / [ ] Falhou — observação:

**Situação do ambiente de tela:** os requerimentos fictícios existem **só no Oracle de teste** (R69), e o ambiente com SIGA fictício para abri-los na tela ainda **não existe** (R66). Por isso a coluna **Tela** fica `Não executado` até haver esse ambiente; a coluna **Prova automática** diz o que os testes já provam. Quem marcar `OK` na tela precisa tê-la aberto de fato.

## 1. Como ler as tabelas

- **Sequência** é a ordem da linha do tempo, da mais antiga para a mais recente. **(2ª passagem)**, **(3ª passagem)** = a etapa voltou; a passagem repetida mostra o **motivo** (ou nada, quando não há motivo determinável). **(atual)** = etapa em andamento.
- **Multi-instância** (aceite, análise, validação): uma caixa com o número de participantes (ex.: "Aceite pelo analista (3)"); na linha do tempo, cada participante com concluiu, pendente ou encerrada sem ação.
- **Ramo da correção:** "Correção do requerimento" e suas caixas de taxa e pagamento aparecem em faixa própria, com a identificação em texto.
- **Fluxograma:** só as caixas das etapas do usuário (tarefas humanas e a espera "Aguardando pagamento"; a taxa e a isenção também viram caixa). Os gateways não aparecem como losango. A **"Conclusão"** do LAC **não vira caixa**: a bola preta com "Concluído" já diz o final.
- **Final:** bola preta com o rótulo escrito em cima. **Sem bola** = em andamento. Na verificação da revisão ativa, a caixa tem o selo "Processo concluído", **sem** bola (o evento de fim não existe).
- **Nenhuma tela mostra** CPF, id técnico (`task…`, `gtw…`, `Event_…`, `evt…`), chave, token, nome de grupo técnico nem a palavra "Camunda" (P39). Para o Administrador, os ids técnicos só aparecem no campo de diagnóstico da resposta, nunca em texto da tela para os demais perfis.
- **Com quem:** com o parâmetro "Mostrar o nome do responsável" ligado, aparece o nome; desligado, aparecem só a área e o papel (nunca o nome).

## 2. Cenários do Hídrico (HC01 a HC15)

Legenda das etapas: **Cadastro** = Cadastro do requerimento; **Conferência** = Conferência dos documentos; **Taxa** = Emissão da taxa; **Pagamento** = Aguardando pagamento; **Atribuição** = Atribuição ao analista; **Aceite** = Aceite pelo analista; **Análise** = Análise do processo; **Revisão** = Revisão do parecer técnico; **Validação** = Validação do parecer técnico; **Emissão** = Emissão do ato; **Publicação** = Registro do comprovante de publicação; **Verificação** = Verificação da revisão do processo; **Correção** = Correção do requerimento.

| Cenário | Id | Sequência esperada na linha do tempo | Fluxograma e final ou estado | Tela | Resultado |
|---|---|---|---|---|---|
| HC01 | 8001 | Cadastro → Conferência → Taxa → Pagamento → Atribuição → Aceite (3) → Análise (3) → Revisão → Validação (3) → Emissão (autorização e portaria) → Publicação → Verificação (atual) | Sem bola. Verificação atual com o selo "Processo concluído"; posição "Concluído" (P38) | `Não executado` | [ ] OK / [ ] Falhou |
| HC02 | 8002 | Cadastro → Conferência → Isenção confirmada → Atribuição → Aceite (3) → Análise (atual, pendentes) | Sem bola. Sem Taxa nem Pagamento | `Não executado` | [ ] OK / [ ] Falhou |
| HC03 | 8003 | Cadastro → Conferência → Cadastro (2ª passagem: reprovado na conferência) → Conferência (2ª passagem, sem motivo) → Taxa → Pagamento (atual) | Duas caixas de cadastro e duas de conferência; depois Taxa e Pagamento | `Não executado` | [ ] OK / [ ] Falhou |
| HC04 | 8004 | Cadastro → Conferência → Taxa → Pagamento → Atribuição → Aceite (3) → Análise → Revisão → Validação → Análise (2ª: devolvido na validação para análise) → Revisão (2ª) → Validação (2ª) → Revisão (3ª: devolvido na validação para revisão) → Validação (3ª, atual) | Sem bola; a sequência inteira em linha reta, sem seta de retorno | `Não executado` | [ ] OK / [ ] Falhou |
| HC05 | 8005 | Cadastro → Conferência → Taxa → Pagamento → Atribuição → Aceite (3) → Análise → Revisão → Análise (2ª: troca de analista) → Revisão (2ª) → Validação → Análise (3ª: troca de analista) → Revisão (3ª, atual) | Sem bola; a troca de analista sempre volta à Análise | `Não executado` | [ ] OK / [ ] Falhou |
| HC06 | 8006 | Cadastro → Conferência → Taxa → Pagamento → Atribuição → Aceite (3) → Atribuição (2ª: recusa no aceite) → Aceite (2ª) → Análise → Atribuição (3ª: redistribuição) → Aceite (3ª, atual) | Sem bola; a recusa e a redistribuição são caixas próprias | `Não executado` | [ ] OK / [ ] Falhou |
| HC07 | 8007 | Cadastro → Conferência → Taxa → Pagamento → Atribuição → Aceite (3) → Análise → Correção → Taxa (correção) → Pagamento (correção) → Análise (2ª, atual, motivo: correção do requerimento) | Faixa "Correção do requerimento" com as caixas de taxa e pagamento da correção; sem bola | `Não executado` | [ ] OK / [ ] Falhou |
| HC08 | 8008 | Cadastro → Conferência → Taxa → Pagamento → Atribuição → Aceite (3) → Análise → Correção → Análise (2ª, atual, motivo: correção do requerimento) | Faixa "Correção do requerimento" **sem** caixas de taxa | `Não executado` | [ ] OK / [ ] Falhou |
| HC09 | 8009 | Igual ao HC08: Cadastro → … → Análise → Correção → Análise (2ª, atual) | Sem bola. A correção expirada não aparece como etapa percorrida (tarefa encerrada sem ação); segue para a análise (P40) | `Não executado` | [ ] OK / [ ] Falhou |
| HC10 | 8010 | Cadastro → Conferência → Taxa → Pagamento → Atribuição → Aceite (3) → Análise (3 participantes: 1 concluiu e 2 encerrados sem ação) → Correção (atual) | Análise sem as tarefas encerradas sem ação contadas como percorridas | `Não executado` | [ ] OK / [ ] Falhou |
| HC11 | 8011 | Cadastro → Conferência → Taxa (as repetições do script viram uma caixa) → Pagamento (atual) | Sem Suporte, sem "Report Kafka" e sem repetição por erro de API (ruído). Para o Administrador, esses ids aparecem só no diagnóstico | `Não executado` | [ ] OK / [ ] Falhou |
| HC12 | 8012 | Cadastro → Conferência → Taxa → Pagamento → Atribuição → Aceite (3) → Análise → Revisão → Validação → Emissão (1ª: 3 atos, 2 ocorreram) → Revisão (2ª: devolvido na emissão) → Validação (2ª) → Emissão (2ª: 3 atos, todos ocorreram) → Publicação (atual) | A Emissão é **uma** caixa por passagem, com só os atos que ocorreram; sem paralelo | `Não executado` | [ ] OK / [ ] Falhou |
| HC13 | 8013 | Cadastro → … → Validação → Emissão → Publicação → Verificação → Emissão (2ª: revisar na verificação) → Publicação (2ª, atual) | Sem bola; a verificação é caixa própria, seguida de nova emissão | `Não executado` | [ ] OK / [ ] Falhou |
| HC14 | 8014 | Cadastro → … → Aceite (3) → Análise (em paralelo com a complementação, concluída durante a análise) → Revisão (atual) | A complementação aparece ao lado, como concluída; a Análise é marcada "em paralelo" | `Não executado` | [ ] OK / [ ] Falhou |
| HC15 | 8015 | Cadastro → Conferência → Taxa (3.500 repetições em uma caixa) → Pagamento (atual) | Não aparece como truncado: o colapso acontece antes do limite de linhas | `Não executado` | [ ] OK / [ ] Falhou |

**Pontos de "com quem" para conferir (fixtures):** no HC01, o aceite tem os três analistas `Servidor Exemplo 05`, `Servidor Exemplo 06` e `Servidor Exemplo 07`; no HC10, a análise tem 1 participante que concluiu e 2 encerrados sem ação; no HC02, a análise atual tem só participantes pendentes; no HC12, a emissão tem o papel "Signatário".

**Provas automáticas (Oracle de teste):** `ReconstruirCaminhoComFixturesTest` (sequência exata de HC01 a HC15, motivos das voltas, ruído, emissão, correção, taxa, complementação, limite e o truncado com o limite de 3), `ReconstruirCaminhoTest` (regras sem Docker), `TramitacaoHttpTest` e `TramitacaoCompletaHttpTest` (contrato da API por perfil). Front: `front/e2e/tramitacao.e2e.ts` (HC01, HC02, HC03, HC07, HC10, HC12, HC15, LC03 e a dica por teclado; impressão em preto e branco).

## 3. Finais e cenários do LAC

### 3.1 Hídrico, finais com histórico (HF01 a HF04)

| Cenário | Id | Sequência esperada na linha do tempo | Final e aviso esperados | Tela | Resultado |
|---|---|---|---|---|---|
| HF01 | 8021 | Início a conferir contra o HC01 até a Validação (**NÃO CONFIRMADO** nesta redação: não foi lido o helper `validacaoAteAEmissao`); Emissão; Publicação; Verificação (encerrada, sem atual) | Bola preta **Concluído** (evento `Event_1wiuc48`, depois da verificação) | `Não executado` | [ ] OK / [ ] Falhou |
| HF02 | 8022 | Cadastro → Conferência | Bola preta **Cancelado pelo cadastrante** (`Event_1kq2k0z`) | `Não executado` | [ ] OK / [ ] Falhou |
| HF03 | 8023 | Nenhuma caixa: a tarefa de cadastro expirou sem ação | Bola preta **Expirado no cadastro** (`Event_0abm55w`). Como a tela desenha uma linha sem caixas: **NÃO CONFIRMADO**, conferir na homologação | `Não executado` | [ ] OK / [ ] Falhou |
| HF04 | 8024 | Cadastro → Conferência → Taxa → Pagamento (passou, sem atual) | Bola preta **Encerrado por taxa não paga no prazo** (`Event_0h1oige`); aviso Atenção `FLUXO_ENCERRADO_SEM_STATUS`; a situação "Aguardando pagamento de taxas" continua a mesma, sem troca | `Não executado` | [ ] OK / [ ] Falhou |

### 3.2 LAC, feliz, isento e reprovado (LC01 a LC03)

| Cenário | Id | Sequência esperada na linha do tempo | Fluxograma e final ou estado | Tela | Resultado |
|---|---|---|---|---|---|
| LC01 | 8051 | Cadastro → Conferência → Taxa → Pagamento → Termo de adesão gerado → Envio do termo assinado → Conclusão | Bola preta **Concluído** (`evtProcessoConcluido`). A "Conclusão" é só a bola (não vira caixa) | `Não executado` | [ ] OK / [ ] Falhou |
| LC02 | 8052 | Cadastro → Conferência → Isenção confirmada → Termo de adesão gerado → Envio do termo assinado (atual) | Sem bola. Sem Taxa nem Pagamento | `Não executado` | [ ] OK / [ ] Falhou |
| LC03 | 8053 | Cadastro → Conferência → Correção → Conferência (2ª, motivo: correção do requerimento) → Taxa → Pagamento (atual) | Sem bola. **Sem** "Taxa (correção)" nem "Pagamento (correção)": a correção volta direto à conferência | `Não executado` | [ ] OK / [ ] Falhou |

### 3.3 LAC, finais (LF01 a LF05)

| Cenário | Id | Sequência esperada na linha do tempo | Final esperado | Tela | Resultado |
|---|---|---|---|---|---|
| LF01 | 8061 | Cadastro | Bola preta **Cancelado pelo cadastrante** (`evtFimRequerimentoAPedidoCadastrante`) | `Não executado` | [ ] OK / [ ] Falhou |
| LF02 | 8062 | Nenhuma caixa: a tarefa de cadastro expirou sem ação | Bola preta **Expirado no cadastro** (`evtEncerrarRequerimentoPrazoCadExpirado`). Como a tela desenha uma linha sem caixas: **NÃO CONFIRMADO**, conferir na homologação | `Não executado` | [ ] OK / [ ] Falhou |
| LF03 | 8063 | Cadastro → Conferência (a correção expirada não aparece como etapa percorrida) | Bola preta **Expirado na correção** (`evtEncerrarProcessoPrazoCorrecaoExpirado`), com a **mesma situação** do LF02. O rótulo vem do evento, não da situação | `Não executado` | [ ] OK / [ ] Falhou |
| LF04 | 8064 | Cadastro → Conferência → Taxa → Pagamento | Bola preta **Cancelado por falta de pagamento** (`evtEncerrarProcessoFaltaPagto`) | `Não executado` | [ ] OK / [ ] Falhou |
| LF05 | 8065 | Cadastro → Conferência → Isenção confirmada → Termo de adesão gerado → Envio do termo assinado | Bola preta **Arquivado (termo não enviado no prazo)** (`Event_0x7hz1p`); no fluxograma, a forma curta "Arquivado" (bloco 9) | `Não executado` | [ ] OK / [ ] Falhou |

## 4. Plano B: requerimento sem instância no Camunda (PB01 a PB03)

Selo fixo em **todos** os três: **"Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo"**. Sem fluxograma, sem duração, sem "com quem", sem filtro, sem id técnico.

| Cenário | Id | O que a linha do tempo mostra | Situação e marcos | Tela | Resultado |
|---|---|---|---|---|---|
| PB01 | 8091 | Etapas aproximadas, em ordem, sem ciclo nem motivo: Cadastro do requerimento → Conferência dos documentos → Aguardando pagamento → Análise do processo → Emissão do ato → Registro do comprovante de publicação. Cada etapa com a data (sem hora) | Situação "Concluído". Marcos: Abertura do requerimento, Protocolo, Conclusão | `Não executado` | [ ] OK / [ ] Falhou |
| PB02 | 8092 | Cadastro do requerimento → Conferência dos documentos (saída aproximada na data de expiração). Um evento não reconhecido é **contado**, sem o nome cru ("1 evento não reconhecido") | Situação "Cancelado por expiração de prazo de cadastro". Marco: Expiração do requerimento | `Não executado` | [ ] OK / [ ] Falhou |
| PB03 | 8093 | Nenhuma etapa: **"Sem detalhamento disponível"** | Situação "Em análise". Marco: Abertura do requerimento | `Não executado` | [ ] OK / [ ] Falhou |

**Provas automáticas:** `PlanoBComFixturesTest` (PB01 a PB03, duas consultas, nenhum CPF, SQL sem `SERVIDOR`, `PESSOA` nem `ASSIGNEE_`), `PlanoBSemResponsavelTest` (o SQL emitido ao Oracle, na montagem real), `MontarTramitacaoTest` (Camunda fora ou lento = `INDISPONIVEL`, sem plano B no lugar) e `VarreduraDaTramitacaoHttpTest` (os três cenários × três perfis).

> As fixtures do e2e de front (`tramitacao.e2e.ts`) simulam a resposta da API e **não** substituem estes cenários do Oracle: a referência é a tabela acima, que segue `CenariosFicticiosDaS8` e `PlanoBComFixturesTest`.

## 5. Estados de erro, perfis e acesso

| Verificação | Como conferir | Prova automática | Tela | Resultado |
|---|---|---|---|---|
| `INDISPONIVEL`: aviso neutro "Não foi possível consultar a tramitação agora. O restante da página não foi afetado.", botão **Tentar novamente**, sem selo de histórico aproximado e **sem** plano B no lugar | Derrubar o acesso ao Camunda fictício (só no ambiente de teste) | `MontarTramitacaoTest`, `LerTramitacaoTest` | `Não executado` | [ ] OK / [ ] Falhou |
| `NAO_SUPORTADO` (Trifásico ou LAS): "Fluxo ainda não suportado pelo Rastro." | Abrir o requerimento de tipo fora do catálogo | `TramitacaoCompletaHttpTest` | `Não executado` | [ ] OK / [ ] Falhou |
| `HISTORICO_TRUNCADO`: o que coube, com o aviso de atenção | Instância acima do limite (3.000 linhas) | `ReconstruirCaminhoComFixturesTest` (limite de 3 linhas) | `Não executado` | [ ] OK / [ ] Falhou |
| Falha da tramitação **não** derruba "Onde está agora" nem os documentos, e o inverso | Repetir com a tramitação indisponível | `TramitacaoHttpTest`, testes de independência do front | `Não executado` | [ ] OK / [ ] Falhou |
| Operador sem nome do responsável em passagens, participantes e fluxograma; papel continua | Entrar como `operador.demo` | `VarreduraDaTramitacaoHttpTest`, `VarreduraSemNomeDoResponsavelHttpTest`, `tramitacao.e2e.ts` (P39 por perfil) | `Não executado` | [ ] OK / [ ] Falhou |
| Administrador vê ids técnicos e o diagnóstico; Gestor e Operador não | Entrar como `admin.demo` e comparar com `gestor.demo` | `TramitacaoCompletaHttpTest`, `VarreduraDaTramitacaoHttpTest` | `Não executado` | [ ] OK / [ ] Falhou |
| Nenhuma tela de consulta mostra a palavra "Camunda" (P39), para nenhum perfil | Percorrer busca e detalhe com cada perfil e procurar o termo (a aba Conexões é só do Administrador) | `tramitacao.e2e.ts` (P39 por perfil), `TramitacaoHttpTest` | `Não executado` | [ ] OK / [ ] Falhou |
| P38: a posição de H12 mostra "Concluído" com o selo "Processo concluído" e "Concluído desde …", sem a etapa "Verificação da revisão do processo" como em andamento | Abrir H12 (linha H12 da seção 1.1 do [roteiro da S7](s7-homologacao.md)) | `TramitacaoHttpTest` (P38 por perfil), `PosicoesDeTeste.naVerificacaoDaRevisao` | `Não executado` | [ ] OK / [ ] Falhou |
| Impressão em preto e branco (botão "Imprimir tramitação") | Imprimir um HC com a tela em escala de cinza | `tramitacao.e2e.ts` (impressão por `emulateMedia`) | `Não executado` | [ ] OK / [ ] Falhou |
| Celular de 360 px: sem rolagem horizontal, linha do fluxograma quebra em linhas | Abrir HC01 em 360 px | `tramitacao.e2e.ts` (360 px) | `Não executado` | [ ] OK / [ ] Falhou |

## 6. Conferência com dados reais (responsável, fora do repositório)

**Não há número real neste arquivo.** Use marcadores, guarde as anotações em `amostras-locais/` (fora do Git) e apague depois. Compare o fluxograma e a linha do tempo com o que o **Camunda** e o **SIGA** mostram para o mesmo requerimento, pela sua própria consulta, fora do Rastro.

Amostra pedida: **5 requerimentos do Hídrico** (um com **correção** e um com **devolução**) e **2 do LAC**.

| Marcador | Fluxo e característica | Fluxograma confere com o Camunda? | Linha do tempo confere (sequência, voltas, com quem)? | Final confere com o evento de fim e a situação? | Observação |
|---|---|---|---|---|---|
| `<ID_HIDRICO_1>` | Hídrico, em etapa humana | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não / [ ] Não se aplica | |
| `<ID_HIDRICO_2>` | Hídrico, **com correção** | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não / [ ] Não se aplica | |
| `<ID_HIDRICO_3>` | Hídrico, **com devolução** (validação `RA` ou `RP`) | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não / [ ] Não se aplica | |
| `<ID_HIDRICO_4>` | Hídrico, encerrado | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não / [ ] Não se aplica | |
| `<ID_HIDRICO_5>` | Hídrico, verificação da revisão ou concluído | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não / [ ] Não se aplica | |
| `<ID_LAC_1>` | LAC, em etapa humana | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não / [ ] Não se aplica | |
| `<ID_LAC_2>` | LAC, encerrado ou concluído | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não | [ ] Sim / [ ] Não / [ ] Não se aplica | |

Se algum requerimento real não bater com o Camunda, **não** corrija o fluxograma às pressas: registre em `docs/10` a diferença, com o marcador (nunca o número) e a etapa, e trate como pendência.

## 7. Critérios de aceite da S8

1. Para HC, LC e PB, a linha do tempo mostra as etapas percorridas, as voltas que ocorreram, com quem esteve e o final correto (seções 2 a 4).
2. O fluxograma é em linha reta, só com o que passou, sem losango, com o final como bola preta rotulada e a verificação da revisão como caixa própria; imprime em preto e branco (seção 5).
3. Plano B sempre rotulado como aproximado; nunca usado quando o Camunda falha (seções 4 e 5).
4. Nenhum CPF, id técnico (para não Administrador), chave ou token em resposta, tela, log ou auditoria (seção 5 e varredura do bloco 11).
5. Camunda lento ou fora não derruba o detalhe; consultas limitadas, com tempo limite e cache; nada gravado no Oracle (seção 5).
6. Testes verdes, ArchUnit verde, `docs/` e `docs/progresso/s8.md` atualizados.

- [ ] OK / [ ] Falhou — observação:

## 8. Pendências que a homologação precisa fechar

- **P40 (NÃO CONFIRMADO):** valores das variáveis da lista branca (`RA`, `RP`, `isRevisaoAnalise`, `isRevisaoParecer`, `REVISAR`), gravação da correção expirada, recusa no aceite e ids de "Report Kafka" e de repetição por erro. Confirme no BPMN ou no histórico real (seção 6); valor diferente = motivo vazio, sem erro.
- **P41 (NÃO CONFIRMADO):** mapa de eventos do plano B (`eventos-historico.yaml`), pelo nome do evento. Confirme com um requerimento de 2021/2022 sem instância.
- **P42:** índices recomendados ao SIGA, só documento (decisão da equipe do SIGA).
- **R73 e R74:** consulta do plano B sem índice conhecido (R73) e cache e leituras por instância (R74). Ver [`docs/10`](../10-pendencias-e-riscos.md).
- **R66:** aceito (D84). A tela desta homologação depende de um ambiente com SIGA fictício, que ainda não existe; enquanto isso, a coluna **Tela** fica `Não executado`.

Registre o resultado em [`docs/14`](../14-plano-de-sprints.md). Cada falha vira pendência em [`docs/10`](../10-pendencias-e-riscos.md).
