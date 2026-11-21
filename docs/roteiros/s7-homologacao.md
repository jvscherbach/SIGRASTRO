# Roteiro de homologação da S7: tramitação, onde está e com quem

**Quem executa:** o responsável pelo projeto, manualmente. O desenvolvimento (Claude Code) **nunca** se conecta ao SIGA real, ao Camunda real, ao SCORP nem à API de arquivos (D27, D34). Os cenários H01 a H21, L01 a L07 e o Trifásico são **fictícios**, gravados no Oracle de teste (Testcontainers); a conferência com dados reais é a seção 3, fora do repositório.

**O que a S7 entrega e este roteiro confere:**

- o bloco **"Onde está agora"** no detalhe do requerimento: etapa, desde quando, com quem, selos de tipo, "+N em paralelo", complementação, x de y da multi-instância e avisos (blocos 3 a 5 e 8);
- o bloco **"Outros requerimentos deste processo"** e a coluna **"Etapa atual"** da busca (bloco 9);
- o **limite de posições simultâneas** pedidas pela tela (bloco 9 e 10);
- a aba **Rastro** em Configurações (bloco 10): desligar o nome do responsável, com o evento `CONFIG` na auditoria;
- o teste de conexão **"Fluxo (Camunda)"** (bloco 10);
- as pendências da S6 que entram na S7: `/verificar-sprint 7` e a P06 (seção 5).

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em [`docs/14`](../14-plano-de-sprints.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que falhar.

> **Dados pessoais e arquivos reais:** CPF, CNPJ, nomes e números de processo reais ficam como marcadores (`<CPF_DE_TESTE>`, `<CNPJ_DE_TESTE>`, `<ID_DO_REQUERIMENTO>`, `<PROCESSO_DE_TESTE>`). **Nenhuma captura de tela, exportação de auditoria ou consulta com dado real vai para o repositório**: guarde numa pasta local fora dele (`amostras-locais/` está no `.gitignore`) e apague depois.

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

Anote antes de começar (só os marcadores):

| Item | Anote |
|---|---|
| Um requerimento real em **etapa de tarefa humana** (para a seção 3) | `<ID_DO_REQUERIMENTO_1>` |
| Um requerimento real **encerrado** (para a seção 3) | `<ID_DO_REQUERIMENTO_2>` |
| Um processo real com **dois ou mais requerimentos** (para a seção 4.3) | `<PROCESSO_DE_TESTE>` |

- [ ] OK / [ ] Falhou — observação:

## 1. Cenários fictícios: o que a tela deve mostrar

**Onde abrir:** cada cenário é o requerimento fictício `<id>/2026` (id na coluna **Id**). Esses requerimentos existem **só no Oracle de teste** (R69). Para abri-los na tela é preciso um ambiente com SIGA fictício, que ainda **não existe** (R66, R69). Por isso a coluna **Prova automática** diz o que já foi provado pelos testes. Uma linha só ganha `OK` na coluna **Tela** quando você a conferir no ambiente que tiver; sem ele, marque `Não executado`.

**Como ler as colunas:** **Etapa esperada** é o rótulo do catálogo (vocabulário do usuário, nunca o id técnico) com o selo de tipo (Tarefa, Espera, Automática, Emissão). **Com quem** vale com o parâmetro "Mostrar o nome do responsável" **ligado**; com ele desligado, aparecem só a área e o papel, nunca o nome (seção 4.1). Nenhuma linha pode mostrar CPF nem id técnico (`task…`, `gtw…`, `Event_…`).

### 1.1 Hídrico

| Cenário | Id | Etapa esperada (selo) | Com quem | Encerramento, avisos e selos | Prova automática | Tela | Resultado |
|---|---|---|---|---|---|---|---|
| H01 | 7001 | Cadastro do requerimento (Tarefa) | Interessado (cadastrante); desde 02/03/2026 09:30 | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| H02 | 7002 | Conferência dos documentos (Tarefa) | Conferência de documentos (subterrânea) | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| H03 | 7003 | Aguardando pagamento (Espera) | Interessado (cadastrante); desde 04/03/2026 09:30 | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| H04 | 7004 | Isenção confirmada (Automática) | Sistema | nenhum; sem passagem pelo pagamento | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| H05 | 7005 | Atribuição ao analista (Tarefa) | Atribuição de requerimentos (superficial) | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| H06 | 7006 | Aceite pelo analista (Tarefa, multi-instância) | "2 de 3 concluídos"; participantes Servidor Exemplo 01 e 02 (concluíram) e 03 (pendente) | nenhum | `multi_instancia_x_de_y`, `h06_participantes_concluiu_e_pendente` | [ ] | [ ] OK / [ ] Falhou |
| H07 | 7007 | Análise do processo (Tarefa, multi-instância) | 3 analistas ativos, 0 concluídos; conferir o "x de y" na tela | nenhum | `multi_instancia_x_de_y` (3 ativas, 0 concluídas) | [ ] | [ ] OK / [ ] Falhou |
| H08 | 7008 | Revisão do parecer técnico (Tarefa) | Servidor Exemplo 04 (papel Chefia); com o parâmetro desligado, só "Chefia" | nenhum | `h08_servidor_com_papel_de_chefia` | [ ] | [ ] OK / [ ] Falhou |
| H09 | 7009 | Validação do parecer técnico (Tarefa, multi-instância) | 3 signatários (Servidores 05 a 07), cada um com o estado | nenhum | `multi_instancia_x_de_y` (3 tarefas com responsável) | [ ] | [ ] OK / [ ] Falhou |
| H10 | 7010 | Emissão do ato (Emissão) | Emissão de títulos (área das duas filhas) | selo "+1 em paralelo" (autorizações e portaria) | `h10_duas_filhas_em_paralelo`, `h10_signatarios_pela_area_do_grupo_em_cada_filha` | [ ] | [ ] OK / [ ] Falhou |
| H11 | 7011 | Registro do comprovante de publicação (Tarefa) | Registro de publicação (barramento) | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| H12 | 7012 | Verificação da revisão do processo (Tarefa) | Revisão da decisão (multidisciplinar) | aviso Informação: "Processo concluído; a verificação da revisão é só arquivamento." Selo "Processo concluído": **não existe na tela** (pendência P38) | `cada_aviso_no_seu_cenario` (CONCLUIDO_ANTES_DA_VERIFICACAO) | [ ] | [ ] OK / [ ] Falhou |
| H13 | 7013 | Correção do requerimento (Tarefa, ramo correção) | Interessado (cadastrante) | nenhum | `cenario_ativo_mostra_a_etapa_certa` | [ ] | [ ] OK / [ ] Falhou |
| H14 | 7014 | Aguardando pagamento (correção) (Espera) | Interessado (cadastrante) | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| H15 | 7015 | **Encerrado**: Concluído | — | final "Concluído", "Encerrado em" | `cenario_encerrado_mostra_o_final` | [ ] | [ ] OK / [ ] Falhou |
| H16 | 7016 | **Encerrado**: Cancelado pelo cadastrante | — | final "Cancelado pelo cadastrante" | `cenario_encerrado_mostra_o_final` | [ ] | [ ] OK / [ ] Falhou |
| H17 | 7017 | **Encerrado**: Expirado no cadastro | — | final "Expirado no cadastro" | `cenario_encerrado_mostra_o_final` | [ ] | [ ] OK / [ ] Falhou |
| H18 | 7018 | **Encerrado**: Encerrado por taxa não paga no prazo | — | aviso Atenção `FLUXO_ENCERRADO_SEM_STATUS`; a situação do SIGA ("aguardando pagamento") **continua a mesma**, sem troca | `cenario_encerrado_mostra_o_final`, `h18_mantem_o_final_e_a_situacao_do_siga`, `cada_aviso_no_seu_cenario` | [ ] | [ ] OK / [ ] Falhou |
| H19 | 7019 | Conferência dos documentos (última etapa real, Tarefa) | Equipe responsável; **sem data**: "Data não informada" | aviso Informação `EM_SUPORTE` ("Em atendimento de suporte técnico."); a tarefa de suporte não aparece como etapa | `h19_suporte_mostra_a_ultima_etapa_real`, `cada_aviso_no_seu_cenario` | [ ] | [ ] OK / [ ] Falhou |
| H20 | 7020 | Análise do processo (Tarefa) | Servidor Exemplo 08 (papel Analista) | complementação "em andamento" (1), **fora** do "+N em paralelo" | `complementacao_a_parte_e_colisao_de_ids` | [ ] | [ ] OK / [ ] Falhou |
| H21 | 7021 | **Sem histórico detalhado** (sem instância) | — | aviso `SEM_INSTANCIA`: "Histórico detalhado indisponível para este requerimento"; sem diagrama | `sem_instancia_e_nao_suportado`, `cada_aviso_no_seu_cenario` | [ ] | [ ] OK / [ ] Falhou |

### 1.2 LAC

| Cenário | Id | Etapa esperada (selo) | Com quem | Encerramento, avisos e selos | Prova automática | Tela | Resultado |
|---|---|---|---|---|---|---|---|
| L01 | 7051 | Conferência dos documentos (Tarefa) | Atendimento (LAC) | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| L02 | 7052 | Aguardando pagamento (Espera) | Interessado (cadastrante) | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| L03 | 7053 | Correção do requerimento (Tarefa, ramo correção) | Interessado (cadastrante) | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| L04 | 7054 | Envio do termo assinado (Tarefa) | Interessado (cadastrante) | nenhum | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| L05 | 7055 | **Encerrado**: Concluído | — | final "Concluído" | `cenario_encerrado_mostra_o_final` | [ ] | [ ] OK / [ ] Falhou |
| L06 | 7056 | **Encerrado**: Arquivado (termo não enviado no prazo) | — | final "Arquivado (termo não enviado no prazo)" | `cenario_encerrado_mostra_o_final` | [ ] | [ ] OK / [ ] Falhou |
| L07 | 7057 | **Encerrado**: Expirado na correção | — | aviso Informação `EXPIRADO_NA_CORRECAO_LAC`; a situação do SIGA é a do expirado no cadastro, **sem troca** | `cenario_encerrado_mostra_o_final`, `cada_aviso_no_seu_cenario` | [ ] | [ ] OK / [ ] Falhou |

### 1.3 Trifásico e fallback do responsável

| Cenário | Id | Resultado esperado na tela | Prova automática | Tela | Resultado |
|---|---|---|---|---|---|
| Trifásico | 7091 | **Fluxo ainda não suportado pelo Rastro**, sem erro (`NAO_SUPORTADO`) | `sem_instancia_e_nao_suportado` | [ ] | [ ] OK / [ ] Falhou |
| G01 (grupo desconhecido) | 7101 | Etapa Conferência dos documentos; com quem **Equipe responsável** | `cenario_ativo_mostra_a_etapa_certa`, `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| G02 (grupo `0`) | 7102 | Etapa Conferência dos documentos; com quem **Equipe responsável** | `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |
| G03 (CPF sem servidor) | 7103 | Etapa Revisão do parecer técnico; com quem **Responsável não identificado** (nunca o CPF) | `com_quem_esta` | [ ] | [ ] OK / [ ] Falhou |

- [ ] OK / [ ] Falhou — observação:

## 2. Indisponibilidade do Camunda (não derruba o detalhe)

A posição responde `INDISPONIVEL` (200), com o botão **"Tentar novamente"**, e o resto do detalhe do requerimento segue normal. A prova automática é `ResilienciaDaTramitacaoHttpTest` e `servidor_mudo_nao_segura_a_thread_alem_do_limite`. Na conferência com o sistema real, este passo só é feito se você conseguir reproduzir a falha de forma segura (por exemplo, com o tempo limite das consultas ao fluxo reduzido na aba Rastro, em ambiente de homologação); caso contrário, marque `Não executado`.

| Item | Esperado | OK? |
|---|---|---|
| Bloco "Onde está agora" com "Tentar novamente" | aparece; o restante do detalhe (objeto e documentos) carrega | [ ] |
| Demais seções do detalhe | nenhuma some nem fica em erro | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 3. Conferência com dados reais (feita pelo responsável, fora do repositório)

Escolha **5 requerimentos reais em etapas diferentes** (por exemplo: cadastro, conferência, atribuição, análise, encerrado). Para cada um, compare o Rastro com a tela do SIGA (Vista e situação) e com a consulta do Camunda que você fizer **fora do repositório**. Não copie nomes, CPFs, números nem telas para o repositório.

| Requerimento | Etapa no Rastro | Etapa no Camunda (sua consulta) | Situação no SIGA | Tela do SIGA | Confere? |
|---|---|---|---|---|---|
| `<ID_DO_REQUERIMENTO_A>` | | | | | [ ] Sim / [ ] Não |
| `<ID_DO_REQUERIMENTO_B>` | | | | | [ ] Sim / [ ] Não |
| `<ID_DO_REQUERIMENTO_C>` | | | | | [ ] Sim / [ ] Não |
| `<ID_DO_REQUERIMENTO_D>` | | | | | [ ] Sim / [ ] Não |
| `<ID_DO_REQUERIMENTO_E>` | | | | | [ ] Sim / [ ] Não |

Quando o Rastro e o Camunda divergirem, vale o Camunda (regra da S7). Quando a situação do SIGA não bater com a etapa, o aviso `SITUACAO_NAO_CONFERE` deve aparecer, **sem troca** da etapa. Se a tabela de compatibilidade (`confronto.yaml`, P37) estiver errada para um caso real, registre em `docs/10`.

- [ ] OK / [ ] Falhou — observação:

## 4. Aba Rastro, parâmetros e teste de conexão

Entre como `admin.demo` (só o Administrador vê a aba Rastro; `gestor.demo` e `operador.demo` recebem `PERFIL_SEM_PERMISSAO`).

### 4.1 Desligar o nome do responsável e ver o evento na auditoria

| Passo | Esperado | OK? |
|---|---|---|
| Configurações › **Rastro** › desligue **"Mostrar o nome do responsável"** › **Salvar alterações** | a barra fixa mostra o salvamento; a tela não exibe credenciais | [ ] |
| Abra um requerimento com tarefa de servidor (ou o `<ID_DO_REQUERIMENTO_1>`) em "Onde está agora" | com quem aparece **só a área ou o papel** ("Atribuição de requerimentos…", "Chefia", "Analista"); **nenhum nome de servidor** | [ ] |
| Mesma consulta como `gestor.demo` | também sem nome (o parâmetro vale para Gestor e Administrador) | [ ] |
| Auditoria › **Acessos** (Administrador): procure o evento **`CONFIG`** ("Configuração alterada") | um evento com a lista de mudanças **antes e depois** (o nome do responsável: `true` → `false`) | [ ] |
| Ligue o parâmetro de volta e salve | o nome volta a aparecer na próxima leitura, e um novo evento `CONFIG` registra a volta | [ ] |

Atenção: a auditoria mostra o evento com o valor, não com o nome do servidor. Não copie a tela de auditoria para o repositório.

- [ ] OK / [ ] Falhou — observação:

### 4.2 Teste de conexão "Fluxo (Camunda)"

Em Configurações › **Conexões**, clique em **Testar conexão** na linha **Fluxo (Camunda)**.

| Cenário | Esperado | OK? |
|---|---|---|
| Usuário de leitura com acesso às tabelas do Camunda | **OK**, com o texto "Leitura das tabelas do fluxo estabelecida." e o tempo medido | [ ] |
| Usuário sem o grant de leitura (só para provar o diagnóstico, se você tiver como simular com segurança) | **Falha** com a causa "sem acesso" (ORA-00942 ou ORA-01031), **sem** a mensagem do driver | [ ] |
| Resposta do teste | sem host, usuário, senha nem mensagem do driver; o teste não lê nenhum dado de processo | [ ] |

Na mesma aba, os testes das outras conexões (banco do SIGA, API de arquivos, SCORP) **não mudaram** nesta sprint; se algum deles der resultado diferente do roteiro da S6, registre em `docs/10`.

- [ ] OK / [ ] Falhou — observação:

**Divergência a decidir (P39):** a tela de Conexões mostra "Fluxo (Camunda)", e o `docs/08` diz que a interface não menciona o motor de fluxo. Anote se o nome deve mudar, e nada mais: a decisão é sua.

### 4.3 Limite de posições simultâneas

Com o `<PROCESSO_DE_TESTE>` (ou a busca com "Mostrar etapa atual" ligado):

1. Em Configurações › **Rastro**, ponha **"Posições consultadas ao mesmo tempo"** em **2** e salve.
2. Reabra o Rastro (o front lê o limite no primeiro pedido de cada carregamento da página).
3. Na aba **Rede** do navegador (ferramentas do desenvolvedor), filtre por `posicao` e carregue a busca ou um processo com três ou mais requerimentos.

| Esperado | OK? |
|---|---|
| No máximo **2** pedidos a `/posicao` em andamento ao mesmo tempo; os demais esperam a vez | [ ] |
| Os pedidos cancelados (ao sair da página antes de começar) não chegam ao servidor | [ ] |
| Ao voltar o limite para 4 e reabrir o Rastro, até 4 em andamento | [ ] |

Restaure o valor padrão (4) ao terminar.

- [ ] OK / [ ] Falhou — observação:

### 4.4 Outros requerimentos do processo e coluna "Etapa atual" na busca

| Passo | Esperado | OK? |
|---|---|---|
| Abra o detalhe de um requerimento de `<PROCESSO_DE_TESTE>` | a seção **"Outros requerimentos deste processo"** lista os demais (sem o próprio), cada um com a posição resumida | [ ] |
| Um dos requerimentos do processo falha (ou não tem histórico) | mostra "Não foi possível consultar" ou "Sem histórico detalhado" **só nele**; os demais seguem | [ ] |
| Como `operador.demo` com um processo que tem requerimento de outro interessado | o Operador só vê os dele (RN07), sem erro na tela | [ ] |
| Busca por processo com **"Mostrar etapa atual"** ligado | cada linha da página mostra a etapa; o botão fica "pressionado" (`aria-pressed`); desligado por padrão | [ ] |
| Teclado | o botão "Mostrar etapa atual" e o "Ver posição completa" são alcançados por Tab e acionados por Enter/Espaço | [ ] |
| Celular (modo dispositivo, 360 px) | sem rolagem horizontal da página | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 5. Pendências da S6 que entram na S7

### 5.1 `/verificar-sprint 7` em sessão nova

Os agentes do Claude Code só carregam ao iniciar. Abra uma **sessão nova** na raiz do projeto e digite `/verificar-sprint 7`.

| Esperado | OK? |
|---|---|
| Três agentes em paralelo (`revisor-seguranca`, `executor-de-testes`, `atualizador-de-docs`) e, ao final, uma tabela-resumo | [ ] |
| O relatório `docs/progresso/verificacao-s7.md` é gerado com as seções Segurança, Testes e Documentação | [ ] |
| O comando **não** corrige código nem faz commit; qualquer alteração em `docs/` aparece em `git diff` | [ ] |

Guarde o `docs/progresso/verificacao-s7.md` como evidência, como na S6 (`docs/progresso/verificacao-s6.md`). Leva alguns minutos, porque o executor roda a suíte inteira.

- [ ] OK / [ ] Falhou — observação:

### 5.2 P06: fechar o que ficou NÃO CONFIRMADO no SCORP

O que ficou em aberto (`docs/05` e `docs/10`, P06): **formato das datas do SCORP** e a **resposta 404 para pessoa sem documentos**. Confira com uma pessoa real **sem documentos** no SCORP (use o marcador `<CPF_DE_TESTE>` ou, se for uma pessoa real, consulte só o resultado, sem registrar o identificador).

| Item | Esperado | OK? |
|---|---|---|
| Resposta da consulta a pessoa sem documentos | registrar o código HTTP (404 ou outro) e o efeito na listagem (a origem "Pessoal" some ou vira aviso) | [ ] |
| Formato das datas devolvidas pelo SCORP | registrar o formato (sem o valor real) | [ ] |
| Resultado | atualizar `docs/05` (seção SCORP: troque **NÃO CONFIRMADO** pelo que foi visto) e P06 em `docs/10` (resolvida ou com o motivo) | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 6. Pontos NÃO CONFIRMADOS a conferir

Estes itens estão na documentação como pendência. Não os resolva no roteiro: registre o que você viu.

| Item | Onde está | O que conferir |
|---|---|---|
| Selo **"Processo concluído"** (regra 9) | P38, `docs/15` | H12: a tela mostra só o aviso Informação, sem selo. Registre se isso basta. |
| Nome "Fluxo (Camunda)" na tela de Conexões | P39, `docs/08` | Decida se o nome muda (a decisão é sua). |
| Tabela de compatibilidade situação × etapa | P37 | Casos reais da seção 3 com aviso `SITUACAO_NAO_CONFERE` que não deveriam aparecer, ou o contrário |
| Ids técnicos do catálogo (BPMN) | P33 | Qualquer "Etapa não mapeada" num requerimento real: anote o id **só para o Administrador** (nunca na tela de outro perfil) |
| Rótulos das áreas dos grupos | P36 | Se algum rótulo de área não é entendido pela equipe |
| Leitura das tabelas do Camunda | P35 | Se a posição de um requerimento real diverge do que o Camunda mostra (seção 3) |

- [ ] OK / [ ] Falhou — observação:

## 7. Varredura de dados pessoais

Antes de dar o fechamento como concluído, o responsável confere, no próprio repositório, que nenhum resultado de homologação foi gravado nele:

```powershell
git diff --stat
```

| Item | OK? |
|---|---|
| Nenhum CPF, CNPJ, nome real, número de processo real ou captura de tela com dados reais em `docs/` ou em qualquer arquivo alterado | [ ] |
| Os resultados ficaram fora do repositório (`amostras-locais/`, apagadas depois) | [ ] |

## 8. Fechamento

| Item | OK? |
|---|---|
| Todos os passos das seções 1 a 5 marcados (ou `Não executado`, com a razão) | [ ] |
| Falhas registradas em `docs/10` | [ ] |
| `docs/14` atualizado com o resultado da homologação da S7 | [ ] |
| `docs/progresso/s7.md` com a data e o resultado | [ ] |
