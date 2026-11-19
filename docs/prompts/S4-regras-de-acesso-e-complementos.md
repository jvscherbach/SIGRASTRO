# S4: regras de acesso, geometrias, documentos pessoais (SCORP) e tela "Dossiês gerados"

> **Forma de trabalho:** a sprint é dividida em **10 blocos**, cada um terminando em um **ponto de commit**. Duas sessões do Claude Code (**OPUS** e **SONNET**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

## Contexto

A S4 fecha o fluxo do usuário (`docs/14`, seção S4). Parte do que ela previa **já foi entregue antes**: o vínculo do Operador com o interessado (RN07) na consulta, na lista e na geração (D24, D36) e o sigilo mascarado na lista, na geração e no modal (S3). O que a S4 entrega agora:

1. **Matriz de acesso completa e validada no back-end** (RNF10) para os três perfis, em **todos** os endpoints, inclusive os novos.
2. **Menu e telas por perfil** (RN08).
3. **Geometrias** (SHP, KML, KMZ) em seção própria, com **"Baixar geometrias"** (RN10).
4. **Documentos pessoais do SCORP** na lista e no dossiê (origem "Pessoal"): eles explicam a diferença de documentos entre a Vista do SIGA e o SIGA Vistas (P21: 29 contra 35 no `<REQ-TESTE-06>`, 92 contra 104 num processo de 2022).
5. **Tela "Dossiês gerados"** com situação e expiração.

O dossiê responde a **processos jurídicos**: acesso indevido a documento sigiloso ou de outro interessado é o pior erro possível desta sprint; completude e rastreabilidade continuam valendo.

Leia antes de começar: `docs/02` (RF15, RF19, RF20, RF30, RN06, RN07, RN08, RN10, RN11, RNF10), `docs/04`, `docs/05` (seções SCORP e licenciamento), `docs/06`, `docs/07`, `docs/08`, `docs/09`, `docs/10` (P06, P12, P21, R25), `docs/12` (perfis), `docs/13` e `docs/14`.

## Decisões já tomadas (registre em `docs/09`)

- **Perfis** (`docs/12`): `OPERADOR` só como interessado (RN07), sem sigilosos; `OPERADOR_SIGILO` (Gestor) todos os processos e sigilosos; `ADMIN` irrestrito. A identidade continua simulada (usuários fixos); não crie autenticação nova.
- **Documento ou processo fora do alcance do Operador:** 403 `NAO_INTERESSADO` quando o requerimento existe mas não é dele, e 404 `NAO_ENCONTRADO` quando o recurso não existe ou o id não pertence ao requerimento informado. Sigiloso sem perfil: 403 `DOCUMENTO_SIGILOSO`. Nada da resposta pode revelar a existência de dado alheio (sem contagem, nome ou data).
- **Geometrias:** saem da lista de documentos e do dossiê (RN10); vão numa lista própria (`geometrias`) do requerimento, com download em **ZIP**. Geometria **sigilosa** não é entregue ao Operador (omitida do ZIP e da lista, com a **contagem** de omitidas visível). A montagem do ZIP é **tudo ou nada** (coerente com a RN09): se algum arquivo falhar, nenhum ZIP é entregue e o erro lista os arquivos com problema.
- **SCORP (origem `SCORP`, rótulo "Pessoal"):** documentos do interessado, dos representantes legais e dos responsáveis técnicos do requerimento (`PESSOA.ID_CORPORATIVO`). **Falha do SCORP não impede a listagem** (aviso na resposta, nunca erro). O SCORP não informa sigilo: o padrão **segue a Vista do SIGA** (tratar como não sigiloso, R25), com a chave `SCORP_SIGILO_PADRAO` (padrão `false`; `true` trata todos como sigilosos, a opção mais conservadora para quando a SEMA decidir). Registrar essa escolha como decisão pendente de confirmação (P30).
- **Dossiês gerados:** o **Operador vê só os seus pedidos**; Gestor e Administrador veem **todos** (com filtro "só os meus"). O histórico é **em memória** (some ao reiniciar; o registro permanente é a auditoria da S5). Pedido vencido continua na lista por `DOSSIE_HISTORICO_HORAS` (padrão 24) como "Expirado", sem arquivo.
- A **auditoria** é da S5: nesta sprint **não registre eventos**, mas deixe em cada ponto novo um comentário `// S5: auditoria` curto indicando o evento previsto (`docs/11`), sem inventar infraestrutura.

## Regras desta sprint

- **Você não executa nada contra o SIGA real, o SCORP real nem a API de arquivos real.** Testes com dados e arquivos **fictícios e sintéticos** (servidor HTTP local de teste para o SCORP e para os arquivos; geometrias geradas por código; nenhum CPF, CNPJ, nome ou número de processo real em teste, doc ou repositório).
- Nunca abra, imprima ou copie o `.env.local`.
- **Consultas SQL novas:** escreva em `resources/sql/siga/...` com os nomes e colunas **conferidos em `docs/04`**. Nada de coluna ou tabela por suposição: se faltar informação, pare, relate e registre em `docs/10`. O responsável valida a consulta no banco real depois (acrescente a consulta e o resultado esperado ao roteiro de homologação do bloco 10). O teste de fumaça segue o padrão da S2.1 (variável de ambiente que o liga, pulado por padrão).
- **Verificação com Docker:** sempre com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`; confirme dentro do container que as variáveis externas (`SIGA_DB_*`, `LICENCIAMENTO_API_*`, `SCORP_API_URL` e as novas desta sprint) estão vazias (só "vazia" ou "preenchida"). Nunca `docker compose down -v`.
- **Nada pago** (regra permanente do `CLAUDE.md`): bibliotecas novas só gratuitas e de código aberto, com licença registrada em `docs/13`. Para o ZIP das geometrias use o `java.util.zip` do JDK.
- A chave do arquivo, tokens e cookies **nunca** saem em resposta, log ou erro (RNF09).
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.

## Protocolo das duas sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS` ou `SESSÃO: SONNET`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/s4.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/s4.md` (crie no bloco 1, com a tabela dos 10 blocos); a marcação entra no mesmo commit;
   - rode `git diff --stat` e responda:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET>): <título>
Testes: <rodaram / falharam / pulados>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto: o que muda e por quê>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET>.
```

5. **Pare.** Mesma sessão no próximo bloco: espere "continuar". Outra sessão: a atual termina ali.

Tipos de commit permitidos: `feat`, `fix`, `refactor`, `test`, `docs`, `perf`, `chore`, `ci`.

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Política de acesso central no back-end e inventário dos endpoints | **OPUS** | Segurança: uma única regra, sem lacunas |
| 2 | Front: menu e telas por perfil (RN08) | **SONNET** | Interface a partir de contrato pronto |
| 3 | Geometrias no back-end: lista própria e ZIP | **OPUS** | Acesso, sigilo, tudo ou nada, arquivos de terceiros |
| 4 | Front: seção de geometrias e "Baixar geometrias" | **SONNET** | Interface |
| 5 | SCORP no back-end: documentos pessoais | **OPUS** | Integração externa não confirmada, falha tolerante, credencial |
| 6 | Front: origem "Pessoal" e aviso do SCORP | **SONNET** | Interface |
| 7 | Dossiês gerados no back-end: listagem com regras de acesso | **OPUS** | Acesso a pedidos de outros usuários |
| 8 | Front: tela "Dossiês gerados" | **SONNET** | Interface |
| 9 | Matriz de perfis completa e varredura de vazamento | **OPUS** | Prova final das regras de segurança |
| 10 | Roteiro de homologação e fechamento | **SONNET** | Documentação |

---

## Bloco 1 (OPUS): política de acesso central

**Commit sugerido:** `refactor(acesso): política de acesso única no back-end e inventário dos endpoints`

- **Inventário:** liste **todos** os endpoints (`docs/07` e controllers) e, para cada um, quem pode chamar, qual regra de vínculo (RN07), de sigilo (RF20) e de dono do recurso (pedido de dossiê) se aplica. Registre a tabela em `docs/07` (seção "Matriz de acesso por endpoint"). **Lacuna encontrada = corrija neste bloco** e registre.
- **Política única** no módulo `acesso` (porta de aplicação, por exemplo `PoliticaDeAcesso`), usada pelos casos de uso de consulta, documento, dossiê e (nos próximos blocos) geometrias e dossiês gerados, no lugar de verificações espalhadas: `podeAcessarRequerimento(usuario, interessado)`, `podeVerDocumentoSigiloso(usuario)`, `podeAcessarPedido(usuario, pedido)`, `mascarar(...)` quando couber. As exceções e os `errorCode` existentes (`NAO_INTERESSADO`, `DOCUMENTO_SIGILOSO`, `NAO_ENCONTRADO`) **não mudam de contrato**; só passam a sair de um lugar.
- **ArchUnit:** regra nova garantindo que controllers e casos de uso **não decidam acesso por conta própria** (por exemplo, não comparam perfil ou CPF/CNPJ fora da política) e que o módulo `acesso` não dependa dos demais.
- Sem mudança de comportamento visível, exceto lacunas corrigidas. Cada lacuna vira teste.
- Docs: `docs/07`, `docs/12` (matriz), `docs/09` (decisão), `docs/progresso/s4.md`.
- Testes: os existentes continuam verdes; testes unitários da política (cada perfil × interessado ou não × sigiloso ou não × dono do pedido ou não) e o teste de ArchUnit.

## Bloco 2 (SONNET): menu e telas por perfil

**Commit sugerido:** `feat(front): menu e rotas por perfil (RN08)`

- Menu pelos `permissoes` já devolvidos pelo back-end (`GET /auth/permissoes` ou equivalente em `AuthController`): **Operador e Gestor não veem "Acessos" nem "Configurações"**; Operador não vê itens de auditoria de acessos; itens futuros (Auditoria, Configurações) continuam como "em construção" para quem tem permissão. **Retire o cadeado de demonstração do item "Acessos"** (decisão: a RN08 vale desde a S4).
- **Guardas de rota:** digitar a URL de tela sem permissão mostra aviso e volta à Consulta (como já descrito em `docs/08`); sem permissão não existe chamada ao servidor.
- Troca de perfil/usuário no login atualiza o menu sem recarregar; sem token, volta ao login.
- Mensagens de `NAO_INTERESSADO` e `DOCUMENTO_SIGILOSO` revisadas em `mensagens-erro.ts` (texto claro, sem vazar dado) e verificadas em cada ponto de uso (consulta, lista, modal, gaveta).
- Docs: `docs/08` (menu por perfil), `docs/progresso/s4.md`.
- Testes: Vitest dos guards, do menu e das mensagens para os três perfis; e2e com API simulada: cada perfil vê os itens esperados, URL bloqueada volta à Consulta, quadro do topo com diferença de 0 px.

## Bloco 3 (OPUS): geometrias no back-end

**Commit sugerido:** `feat(documentos): geometrias em lista própria e download em ZIP (RN10)`

- **Lista:** `GET /requerimentos/{id}/documentos` deixa de descartar geometrias e passa a devolver `geometrias` (mesmo formato dos documentos, mais `tamanho` quando conhecido), **fora** de `documentos`, de `totais` das seleções e do dossiê, com `totais.geometrias`. Compactados continuam fora (RN11). Aplica RN07 e sigilo: para o Operador, geometria sigilosa **não aparece**, e `totais.geometriasOmitidas` informa só a contagem.
- **Download:** `GET /requerimentos/{id}/geometrias/arquivo` devolve **um ZIP** com todas as geometrias a que o usuário tem acesso. Sem chave nem token na resposta; `Content-Disposition` com nome `geometrias-processo-<numero>-<ano>.zip` ou `geometrias-requerimento-<numero>-<ano>.zip` (mesma regra do RF18); `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`.
- **Montagem tudo ou nada:** obtém **todos** os arquivos (mesmo cliente, timeouts, novas tentativas e checagem de disco do dossiê) para um diretório temporário do pedido, só então monta o ZIP; se **algum** falhar, **nenhum** ZIP é entregue: 502/504 com `errorCode` (`GEOMETRIAS_INCOMPLETAS`) e a **lista dos arquivos com problema** (nome e motivo), sem a chave. Nomes dentro do ZIP: seguros (sem caminho, sem caractere de controle, sem `..`), **únicos** (sufixo numérico em colisão), nome de origem preservado quando possível. Limite de tamanho total `GEOMETRIAS_MAX_MB` (padrão 500, 413 `GEOMETRIAS_GRANDES_DEMAIS`) e limite de arquivos. Streaming para o cliente, apagando temporários ao fim ou em erro.
- Sem geometria acessível: 404 `NAO_ENCONTRADO` com mensagem "Este requerimento não tem geometrias" (para o Operador, o mesmo texto vale quando só havia sigilosas, sem revelar a diferença).
- `// S5: auditoria` com o evento de download de geometrias previsto em `docs/11`.
- Docs: `docs/02` (RF15 e RN10 com o comportamento tudo ou nada), `docs/06`, `docs/07`, `docs/12` (`GEOMETRIAS_MAX_MB`), `docs/09`, `docs/10`.
- Testes (`MockMvc`, arquivos sintéticos por código, servidor HTTP local para os arquivos): lista com e sem geometrias, sigilosas para os três perfis, ZIP com 3 arquivos e nomes iguais, falha de um arquivo (nenhum ZIP, lista de falhas), limite de tamanho, Operador de outro interessado (403), id de outro requerimento (404), nome hostil (`../x`, controle, muito longo), e **varredura de corpo e cabeçalhos de todas as respostas** atrás da chave e do token. O dossiê **continua sem geometrias**.

## Bloco 4 (SONNET): front das geometrias

**Commit sugerido:** `feat(front): seção de geometrias e ação Baixar geometrias`

- Seção **Geometrias** no detalhe do requerimento (`docs/08`, "Geometrias"), exibida só quando houver itens: nome, arquivo, origem, data; ação **Baixar geometrias** (por `HttpClient` e `Blob`, token no cabeçalho, nunca na URL; nome do `Content-Disposition`); carregando, sucesso e erro por `errorCode` (`GEOMETRIAS_INCOMPLETAS` lista os arquivos com problema e oferece "Tentar novamente"; 413 com texto próprio).
- Operador: aviso "N geometria(s) sigilosa(s) não são exibidas para o seu perfil" a partir de `totais.geometriasOmitidas`, sem nomes.
- As geometrias **não** entram na tabela de seleção, nos totais da seção Documentos nem no dossiê; o aviso das geometrias não aparece como documento "indisponível".
- Docs: `docs/08`.
- Testes (Vitest e e2e com API simulada): com e sem geometrias, download com token no cabeçalho, erro com lista de falhas e nova tentativa, Operador com sigilosas omitidas, ausência na seleção e no `POST /dossies`, quadro do topo com 0 px.

## Bloco 5 (OPUS): SCORP no back-end

**Commit sugerido:** `feat(scorp): documentos pessoais do interessado, representantes e responsáveis técnicos`

- **Pessoas do requerimento:** consulta nova em `resources/sql/siga/documentos/` que devolve, por requerimento, os `ID_CORPORATIVO` **distintos** do interessado, dos representantes legais e dos responsáveis técnicos, com o **papel** (`INTERESSADO`, `REPRESENTANTE_LEGAL`, `RESPONSAVEL_TECNICO`). Use as tabelas e colunas de `docs/04` (as mesmas ligações das origens `REPRESENTANTE_LEGAL` e `RESPONSAVEL_TECNICO` da S2.1); só `SELECT`; `ID_CORPORATIVO` nulo é ignorado; pessoa repetida em vários papéis vira **uma** chamada.
- **Adapter `integration.scorp`** (único lugar que fala com o SCORP; porta `DocumentosPessoais` na aplicação do módulo `documento`): `GET {SCORP_API_URL}/documentos/pessoa-licenciamento/{idCorporativo}` conforme `docs/05`. Resposta: `chave`, `nomeArquivo`, `descricao`, `dataInicio`, `dataFim`. Autenticação **NÃO CONFIRMADA (P06)**: reaproveite o mesmo esquema da API de arquivos (modos `bearer` e `cookie`, token por arquivo recarregado a cada chamada) com variáveis **próprias opcionais** (`SCORP_API_AUTH_MODE`, `SCORP_API_COOKIE_NAME`, `SCORP_API_TOKEN_FILE`; sem elas, usa as do Licenciamento). Timeouts curtos (`SCORP_TIMEOUT_SEGUNDOS`, padrão 15), no máximo 1 nova tentativa, **chamadas em paralelo limitadas** (`SCORP_CHAMADAS_SIMULTANEAS`, padrão 4). Valor do token, cookie e caminho **nunca** em log, erro ou resposta (teste percorrendo as saídas, como no adapter do Licenciamento).
- **Origem `SCORP`** ("Pessoal"): vira documento do domínio com `id` **estável e que não expõe a chave** (por exemplo, SHA-256 de `idCorporativo|chave`, 16 caracteres hexadecimais), título = `descricao` (ou `nomeArquivo`), origem do papel ("Pessoal · Interessado" etc.) para a lista, data cronológica = `dataInicio`, vigente = `dataFim` nula (RN01), tipo pela extensão do `nomeArquivo` (`DESCONHECIDO` quando não houver, como nas demais origens), **sigilo = `SCORP_SIGILO_PADRAO`** (padrão `false`, P30). Deduplicação por chave com as outras origens (RN05). Ordem de desempate da RN03: a origem `SCORP` vem **depois** das seis existentes. Ligada pelo parâmetro de origens (`vistas.documentos.origens.scorp`, padrão `true`; **desligada automaticamente** quando `SCORP_API_URL` estiver vazia, sem aviso de erro).
- **Falha tolerante:** SCORP fora do ar, 401/403/404, timeout ou resposta inválida **não impedem a listagem**: a resposta de `GET /requerimentos/{id}/documentos` ganha `avisos` (`[{ "codigo": "SCORP_INDISPONIVEL", "mensagem": "..." }]`, uma vez, sem detalhe técnico) e a lista traz as demais origens. Falha **parcial** (uma pessoa responde e outra não) lista o que veio e avisa com `SCORP_PARCIAL`. Documentos com `chave` vazia ou `"0"` são indisponíveis (RN01).
- **Conteúdo e dossiê:** o arquivo vem do mesmo endpoint `/api/arquivo/{chave}` (**NÃO CONFIRMADO** que a chave do SCORP vale nele): reutilize o cliente de arquivos; se falhar, é falha de documento como qualquer outra (decisão na gaveta, sem reiniciar). Visualização (`conteudo`), seleção, conversão (S3), sumário e marcadores funcionam como nas outras origens, e a origem aparece no sumário como "Pessoal".
- **Teste de fumaça** `SCORP_SMOKE`, desligado por padrão, para o responsável rodar com dados reais: lista as pessoas de um requerimento informado e imprime só contagens e tipos de arquivo (nada de nome, chave ou valor de token).
- `// S5: auditoria` onde couber. Docs: `docs/05` (SCORP; atualizar o que ficou confirmado no código e deixar **NÃO CONFIRMADO** o que continua), `docs/04` (consulta nova), `docs/07` (`avisos`, origem), `docs/12` (variáveis), `.env.local.example` (comentadas), `docs/09`, `docs/10` (P06 e P21 em andamento, P30 novo, R25 com a decisão).
- Testes (servidor HTTP local de teste para o SCORP e para os arquivos; SQL com `SELECT` sobre o Oracle do Testcontainers quando houver Docker, como nas demais consultas): pessoas distintas e papéis; SCORP com 3 pessoas e documentos repetidos entre pessoas e outras origens; lista com SCORP fora do ar (aviso, demais origens intactas) e parcial; `dataFim` preenchida (sem efeito), chave `"0"` (indisponível); `SCORP_API_URL` vazia (sem chamada e sem aviso); id estável entre chamadas e **sem a chave**; sigilo com `SCORP_SIGILO_PADRAO` verdadeiro e falso, para os três perfis; RN07 (Operador de outro interessado não chega ao SCORP); chamadas paralelas limitadas; varredura de saídas atrás do token e da chave; dossiê com documento do SCORP (obtido e com falha).

## Bloco 6 (SONNET): front da origem "Pessoal"

**Commit sugerido:** `feat(front): origem Pessoal e aviso de indisponibilidade do SCORP`

- A origem "Pessoal" aparece na coluna de origem, no filtro de origem (se existir) e no modal, com o papel ("Pessoal · Representante legal"); contagem de origens nos totais atualizada.
- **Avisos da lista** (`avisos` do contrato): faixa discreta "Os documentos pessoais (SCORP) não puderam ser consultados agora. Os demais documentos foram listados. Atualize a página para tentar de novo" para `SCORP_INDISPONIVEL`; texto próprio para `SCORP_PARCIAL`. Botão "Consultar novamente" que refaz só a lista. O aviso **não** impede selecionar nem gerar, e o dossiê não diz que o SCORP falhou (a capa continua "N incluídos de M listados"; o aviso é da tela).
- Seleção total (RN13) vale também para os pessoais; sigiloso (se `SCORP_SIGILO_PADRAO=true`) segue as regras de mascaramento já existentes.
- Docs: `docs/08`.
- Testes (Vitest e e2e com API simulada): lista com origem Pessoal, aviso total e parcial, "Consultar novamente", seleção e `POST /dossies` com um documento Pessoal, mascaramento, quadro do topo com 0 px.

## Bloco 7 (OPUS): "Dossiês gerados" no back-end

**Commit sugerido:** `feat(dossie): listagem de dossiês gerados com regras de acesso`

- `GET /dossies` (`docs/07`): lista os pedidos conforme o perfil. **Operador: só os seus** (login do criador); Gestor e Administrador: **todos**, com `?somenteMeus=true`. Cada item: `id`, número do processo ou requerimento e o ano, identificação como no RF18, situação (`NA_FILA`, `OBTENDO`, `PENDENTE_DECISAO`, `MONTANDO`, `CONCLUIDO`, `CANCELADO`, `EXPIRADO`, `BAIXADO`, `FALHOU`), criado em, expira em, quantidade de documentos incluídos e de falhas, tamanho do PDF quando existir, quem gerou (**só para Gestor e Administrador**; Operador nunca recebe o campo) e se há arquivo para baixar. Ordem: mais recente primeiro; paginação simples (`pagina`, `tamanho`, máximo 50).
- **Sem dado de documento:** a lista não traz nome de arquivo, título de documento nem chave. Pedido de requerimento cujo vínculo o Operador perdeu não aparece (a política do bloco 1 decide).
- **Histórico em memória** (D09): pedidos vencidos permanecem por `DOSSIE_HISTORICO_HORAS` (padrão 24), conferindo a limpeza existente (`LimparDossies`, que hoje esquece pedidos antigos) para que o prazo seja esse e não outro. `EXPIRADO` sem arquivo aparece como "Expirado · nenhum arquivo gerado" quando expirou à espera de decisão (`docs/08`). `BAIXADO` = arquivo entregue e apagado (RNF05).
- Ações pelos endpoints existentes (`/dossies/{id}/arquivo`, ticket, `DELETE`), que continuam com as regras de dono e de Gestor/Administrador; **nenhum endpoint novo de arquivo**.
- Não expor a existência de pedidos alheios: `GET /dossies/{id}` de outro usuário (Operador) continua 404.
- Docs: `docs/07`, `docs/08`, `docs/12` (`DOSSIE_HISTORICO_HORAS`), `.env.local.example`, `docs/09`.
- Testes (`MockMvc`): Operador vê só os seus; Gestor e Administrador veem todos e filtram com `somenteMeus`; campo "quem gerou" ausente para o Operador; todas as situações; histórico com relógio controlado (expira e some depois de `DOSSIE_HISTORICO_HORAS`); paginação e ordem; pedido alheio por id continua 404; varredura de respostas atrás de nome de arquivo, chave e token.

## Bloco 8 (SONNET): tela "Dossiês gerados"

**Commit sugerido:** `feat(front): tela Dossiês gerados com situação e expiração`

- Item de menu **Dossiês gerados** (todos os perfis), tela com tabela: identificação, situação (etiqueta), criado em, **expira em com tempo restante** (relógio de Cuiabá, como na gaveta), documentos incluídos e falhas, tamanho, e **quem gerou** (só quando a API enviar). Filtro por situação; Gestor e Administrador têm o filtro "só os meus". Atualização manual e **polling leve** (intervalo longo, para ao sair da tela ou com a aba oculta; sem timer pendurado).
- Ações por linha, conforme a situação: **Pré-visualizar** e **Baixar PDF** (mesmas regras da gaveta, `Blob` com token no cabeçalho, dossiê grande por ticket), **Cancelar** (em andamento), **Abrir requerimento** (vai ao detalhe), **Decidir** para `PENDENTE_DECISAO` (abre a gaveta de geração já existente, sem duplicar a lógica). `EXPIRADO` sem arquivo: "Expirado · nenhum arquivo gerado", sem download; `BAIXADO`: "Arquivo baixado".
- Estados vazio ("Você ainda não gerou dossiês nesta sessão do serviço"; o histórico é em memória e some ao reiniciar o serviço: diga isso na tela), carregando e erro com "Tentar novamente".
- Docs: `docs/08` (tela e textos).
- Testes (Vitest e e2e com API simulada): cada situação e suas ações, filtro, tempo restante, perfil Operador (sem "quem gerou" e sem filtro "só os meus"), perfil Gestor, histórico vazio, polling parado ao sair da tela, ações reaproveitando a gaveta, quadro do topo com 0 px.

## Bloco 9 (OPUS): matriz de perfis completa e varredura de vazamento

**Commit sugerido:** `test(acesso): matriz de perfis completa por MockMvc e varredura de vazamento`

- **`MatrizDeAcessoTest`**: gera, a partir da tabela do bloco 1 (`docs/07`), **todas** as combinações **endpoint × perfil (Operador, Gestor, Administrador) × interessado ou não × documento sigiloso ou não × dono do pedido ou não**, com o resultado esperado de cada uma (200, 403 `NAO_INTERESSADO`, 403 `DOCUMENTO_SIGILOSO`, 404). Cubra: consulta por número, requerimento e interessado, lista de documentos (com SCORP e geometrias), `conteudo` (original e pdf), `POST /dossies`, andamento, continuação, arquivo, ticket, cancelamento, `GET /dossies`, geometrias. **Um endpoint novo sem linha na matriz faz o teste falhar** (o teste varre os mapeamentos do Spring e compara com a tabela).
- **Mascaramento:** para o Operador, o documento sigiloso vem com título "Documento sigiloso", sem nome de arquivo nem extensão, **em todas as respostas** (lista, modal, dossiê, falhas, sumário, "Documentos não incluídos", lista de dossiês).
- **Varredura:** percorrer **todas as respostas** (corpo e cabeçalhos) de todos os cenários da matriz atrás de: chave de arquivo, valores de token e cookie, CPF do servidor, caminho de diretório interno, e (para o Operador) nome de documento sigiloso ou número de documento de outro interessado. Nada pode aparecer.
- Corrija o que a matriz achar (e registre em `docs/10`). Atualize `docs/12` (matriz) e `docs/02` (RF19, RF20, RF30, RNF10) para o que está implementado.
- Docs: `docs/10`, `docs/progresso/s4.md`.

## Bloco 10 (SONNET): roteiro de homologação e fechamento

**Commit sugerido:** `docs(homologacao): roteiro da S4 e registro das decisões`

- `docs/homologacao/S4-roteiro-homologacao.md`, para o **responsável** executar, no formato dos anteriores (comando ou SQL exato, resultado esperado, `[ ] OK / [ ] Falhou: observação`):
  1. subir o Compose local e entrar com cada perfil; conferir o menu (RN08);
  2. **SCORP:** validar a consulta de pessoas no banco real (SQL e resultado esperado), rodar `SCORP_SMOKE` com um requerimento real e **registrar se a autenticação do SCORP funciona com o token** (fecha ou mantém **P06**) e se a chave do SCORP vale em `/api/arquivo` (resultado em `docs/05`);
  3. abrir o `<REQ-TESTE-06>` e os outros casos de teste e **conferir que a diferença de documentos com a Vista do SIGA desaparece** (29 → 35; 92 → 104) ou explicar o resto (fecha **P21**);
  4. gerar um dossiê com documentos pessoais e conferir ordem, origem "Pessoal" no sumário e numeração;
  5. **geometrias:** baixar o ZIP de um requerimento real, abrir os arquivos num visualizador, e conferir a omissão de sigilosas para o Operador;
  6. **perfis:** como Operador (do interessado e de outro), Gestor e Administrador, tentar abrir documento sigiloso, requerimento alheio, dossiê de outro usuário e a tela "Dossiês gerados"; conferir cada resposta contra a matriz do `docs/12`;
  7. tela **Dossiês gerados:** situações, expiração, ações e histórico em memória;
  8. decidir **`SCORP_SIGILO_PADRAO`** (P30) com a área e anotar a escolha.
- `docs/14`: registre a S4 e o que cada bloco entregou, com a tabela "Validação da S4" (pendente); `docs/09`: decisões (política única, geometrias tudo ou nada, SCORP com sigilo padrão, histórico em memória, dossiês por perfil); `docs/10`: riscos e pendências novas (P30; autenticação do SCORP, se ainda aberta; limite de pessoas por requerimento); coerência final entre `docs/02`, `04`, `05`, `06`, `07`, `08`, `12` e `13`; `README.md`.
- **Verificação final completa:** `./mvnw -B clean verify`, front (build, Vitest, e2e), `ArchitectureTest`, Compose com os três arquivos e variáveis vazias dentro do container, YAMLs válidos, `git diff --check` limpo, varredura do diff atrás de credencial, URL institucional e dado real.

---

## Critérios de aceite (ao final do bloco 10)

1. Os três perfis se comportam conforme a matriz do `docs/12`, **testados por `MockMvc`** em todos os endpoints, e um endpoint novo sem linha na matriz quebra o teste.
2. Menu e rotas por perfil (RN08); nada de dado alheio ou sigiloso em resposta, log ou tela.
3. Geometrias em seção própria, fora do dossiê, com ZIP tudo ou nada; sigilosas omitidas para o Operador.
4. Origem "Pessoal" (SCORP) listada e utilizável no dossiê; falha do SCORP **não** impede a listagem e vira aviso; a autenticação do SCORP está **confirmada** na homologação ou registrada como pendente (P06).
5. Tela "Dossiês gerados" com situação, expiração e ações, por perfil.
6. Tudo gratuito, sem credencial ou dado real no repositório; consultas SQL novas validadas pelo responsável.
7. Cada bloco foi executado pela sessão atribuída e marcado em `docs/progresso/s4.md`.
8. Nenhum comando git além de `git diff`.
