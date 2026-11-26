# S11: caixa de entrada do analista e lista de processos por analista no painel do Gestor

> **Forma de trabalho:** a sprint é dividida em **11 blocos**, cada um terminando em um **ponto de commit**. Sessões do Claude Code (**SONNET** e **HAIKU**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. **Não use Opus nesta sprint** (decisão do responsável até a apresentação); o que pediria Opus está listado no Bloco 11 como item da primeira sprint pós-apresentação. Pode ser rodada à mão (o responsável alterna as sessões, revisa e commita) ou pelo `/orquestrar` em branch `auto/*` (ver `docs/claude-code/orquestracao.md`).

## Contexto

A S10 deu ao Gestor a visão de conjunto. A S11 dá ao **analista** a visão do que é dele e melhora o painel do Gestor. O tema antigo da S11 ("pago que virou isento", destaque no financeiro) **sai do plano** e vai para a lista pós-apresentação (Bloco 11).

Entregas:

1. **Caixa de entrada do analista.** Ao entrar, o analista cai nela. Ela mostra os requerimentos que estão **na posse dele na fase de análise**, com resumo, visão **geral** e **por etapa**, busca e ordenação.
2. **Aviso de recebimento.** Uma caixinha destacada avisa, na primeira abertura depois da mudança, que um ou mais requerimentos **chegaram** ao analista; ao clicar, abre a lista com o que mudou. Some depois de vista.
3. **Nome do interessado** na listagem de requerimentos (consulta, caixa e lista do painel). O campo do SIGA é o mesmo para pessoa e para empresa.
4. **Painel do Gestor:** uma **aba lateral** (separada do dashboard) com a **lista dos requerimentos por analista**, cada um com a etapa, com filtros.
5. **Painel do Gestor:** **gráfico de pizza** na fila por etapa, no lugar das barras (correção da S10).

**Fonte e escopo dos dados:** a caixa do analista **não** usa a base do ano do painel (os requerimentos do analista podem ser de anos anteriores). Ela tem **instantâneo próprio em memória**, com o mesmo padrão da S10 (single flight, limites, estados `PREPARANDO`/`PRONTO`/`DESATUALIZADO`/`INDISPONIVEL`, nunca consulta Oracle nem Camunda dentro da requisição). A lista do painel usa **o mesmo instantâneo do painel da S10**, para os números da lista baterem com as barras.

Ficam **fora** (registre no Bloco 11): persistência do "visto" em banco (vai na **H1**), tarefas de grupo ainda não assumidas, aviso quando o requerimento **sai** da posse, notificação por e-mail ou fora do sistema, exportação, prazos e SLA e o antigo tema "pago que virou isento".

Leia antes de começar: `docs/02`, `docs/03`, `docs/04`, `docs/07`, `docs/08`, `docs/09`, `docs/10`, `docs/11`, `docs/12`, `docs/13`, `docs/14`, `docs/15`, `docs/design/sigrastro/LEIA-ME.md`, `docs/progresso/s10.md` e o código dos módulos `acesso`, `consulta`, `tramitacao`, `painel`, `auditoria` e `configuracao`. **Reuse** o catálogo de etapas, o catálogo de grupos, o limite de leituras ao Camunda, as fixtures da S10, o relógio injetável, a utilidade de data, o agendamento (`AgendamentoConfig`) e o padrão do instantâneo do painel.

## Decisões já tomadas (registre em `docs/09`; os números D, P e R seguem o próximo livre)

**Quem é o analista.** Novo perfil **Analista** (permissão `verCaixa`), ao lado de Operador, Gestor e Administrador. O vínculo com o Camunda é o **CPF já cadastrado no usuário** (`Usuario.cpfCnpj`), que é o `ASSIGNEE_`. Usuário Analista **sem CPF cadastrado** vê a caixa vazia com a mensagem "Seu usuário não está vinculado a um servidor. Peça ao Administrador." O CPF **nunca** sai em resposta, log, erro, auditoria ou tela, **nem mascarado**; internamente, o instantâneo guarda só uma **chave opaca** (hash com sal do processo) do responsável. O Administrador cadastra o perfil na tela de usuários já existente. Gestor e Administrador **não** têm caixa. Depois do login: Analista vai para a caixa; os demais perfis seguem como hoje.

**Posse do analista.** Um requerimento está **na posse** do analista quando:
- ele tem a **tarefa ativa** atribuída a ele em **Análise do processo**, **Revisão do parecer técnico** (e as etapas equivalentes do catálogo para essa fase); ou
- está em **Correção do requerimento** (com o interessado) e **a última tarefa de análise ou revisão foi dele** (a correção "continua sendo dele").

Quando o requerimento passa para **Validação do parecer técnico**, **Emissão do ato** ou qualquer etapa seguinte, ele **sai da caixa**. Os rótulos e ids vêm do catálogo de etapas; **não invente id**. Se uma etapa da lista acima não existir no catálogo, escreva **NÃO CONFIRMADO** em `docs/10` e no roteiro.

**Estados de posse** (guardados por requerimento): `COM_ANALISTA(etapa)`, `COM_INTERESSADO` (correção) e `FORA`.

**Aviso de recebimento.** O analista tem um "último visto" em memória: requerimento → estado de posse, gravado quando ele **abre a lista de mudanças**. Há aviso quando o estado **atual** é `COM_ANALISTA` e o estado no último visto era `FORA`, `COM_INTERESSADO` ou **inexistente**. Ou seja, vale para: processo novo distribuído, **volta da correção**, e **volta de etapa posterior** (validação ou emissão do ato, quando é preciso alterar o parecer; ele pode voltar para a **revisão** ou para a **análise**). Mudança de etapa **dentro** da posse (análise para revisão feita por ele) **não** gera aviso. **Saída da posse não gera aviso** (foi ele quem enviou). Primeiro acesso de um analista: tudo o que já está com ele entra como visto, **sem aviso** (senão a primeira caixinha seria enorme).

**Origem do retorno.** Para cada item avisado, a lista mostra de onde veio, pelo evento anterior do histórico da tramitação (S8): "Voltou da correção", "Voltou da validação do parecer", "Voltou da emissão do ato", "Voltou da revisão", "Novo na sua caixa". Se não for possível determinar: "Mudou de etapa" (nunca invente). A leitura do histórico é feita **só para os itens avisados**, com teto por atualização e pelo `LimiteDeLeiturasDoCamunda`.

**Quando some.** A caixinha some quando o analista **abre a lista de mudanças** (isso grava o "visto" daquele conjunto). Itens que chegarem depois geram nova caixinha. A caixinha mostra a quantidade ("3 requerimentos chegaram para você"), que é também o contador de "novos desde a última visita".

**Persistência.** O "visto" fica **em memória**, atrás de uma **porta** (interface) para a H1 trocar por banco sem mexer nas regras. Ele some ao reiniciar o sistema, e a tela e o roteiro dizem isso. Reinício = primeiro acesso (sem aviso retroativo).

**Atualização.** O instantâneo da caixa atualiza com a mesma periodicidade configurável do painel (nova propriedade própria `rastro.caixaAtualizacaoMinutos`, padrão 10, faixa 2 a 120; seção Rastro das Configurações, classe imediato). Há um botão "Atualizar" para o analista, limitado por intervalo mínimo próprio (`rastro.caixaIntervaloMinimoManualSegundos`, padrão 60, faixa 15 a 3600). A tela mostra "Atualizado às HH:MM". O aviso pode, portanto, demorar até o período de atualização.

**Nome do interessado.** Só o **nome** (pessoa) ou a **razão social** (empresa); **nunca** CPF ou CNPJ na listagem, mesmo mascarado. **Não existe requerimento sigiloso** (o sigilo é dos documentos, não do requerimento): nenhuma regra de sigilo de requerimento nem máscara de interessado nesta sprint. O sigilo dos documentos segue como está.

**Caixa: forma.** Cartão de resumo (total na posse; quantos por etapa; quantos com o interessado em correção) e lista abaixo. Duas visões: **Geral** (sem separar etapa) e **Por etapa** (abas ou filtro). Cada linha: número do requerimento, **interessado**, tipo/objeto, etapa atual, dias na etapa e a marca "Novo" quando for item avisado ainda não visto. Busca por **nome do interessado ou número**. Ordenação: padrão **mais tempo parado primeiro**; o analista pode trocar para mais recente. Dias na etapa vêm do início da tarefa atual (para correção, o início da tarefa de correção); sem dado, "sem informação".

**Painel do Gestor: aba lateral.** Separada do dashboard, abre ao clicar na carga de um analista ou no botão "Processos por analista". Lista **requerimentos por analista com a etapa**, com filtros: analista, etapa, busca por interessado ou número. Usa o **instantâneo do painel**, então o total do analista na lista **bate** com a barra, inclusive com os avisos de truncamento, "Fora do catálogo" etc. Identifica o analista pela **chave opaca**, nunca por CPF. Respeita `RASTRO_MOSTRAR_NOME_RESPONSAVEL` e a RN12: com o parâmetro desligado, a lista mostra só categorias sem nome e a nota "Nomes ocultos por configuração". Permissão: a mesma `verPainel` (403 antes de qualquer leitura).

**Independência:** a caixa e a lista não afetam nem são afetadas por consulta, tramitação, financeiro e documentos. Falha delas não derruba nada.

## Regras desta sprint

- **Você não executa nada contra o SIGA real, o SCORP real, o Camunda real nem a API de arquivos real.** Testes e demonstração com dados **fictícios e sintéticos** (nenhum CPF, CNPJ, nome, número de processo ou DAR real em teste, doc, exemplo, log ou repositório). CPF e CNPJ fictícios são **inválidos de propósito** (ex.: `000.000.000-01`); nomes são evidentes (`Analista Exemplo 01`, `Interessado Exemplo LTDA`).
- Nunca abra, imprima ou copie o `.env.local`.
- **Verificação com Docker:** sempre com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`; confirme dentro do container que as variáveis externas estão vazias. Nunca `docker compose down -v`.
- **Nada pago**; bibliotecas novas só gratuitas e de código aberto, com licença em `docs/13`. Telas sem biblioteca de gráficos.
- **Somente leitura no banco.** SQL novo é `SELECT`/`WITH`, com **parâmetros nomeados**, `FETCH FIRST n ROWS ONLY`, tempo limite, concentrado em `integration.siga` (e o equivalente do Camunda, onde já ficam as consultas de tarefa). Nada é gravado no Oracle nem no Camunda.
- **Módulos:** o módulo novo `caixa` entra no `ArchitectureTest` e em `docs/padroes/modulos-e-dependencias.md`, sem acesso direto a banco ou HTTP (só portas) e **sem ciclo**. Depois de editar `docs/padroes/modulos-e-dependencias.md`, **copie o arquivo** para `.claude/skills/vistas-banco/references/modulos-e-dependencias.md` (espelho) no mesmo bloco.
- **Testes por bloco:** só os testes ligados ao bloco. A suíte completa roda **uma vez no fim** (Bloco 11), por quem comanda; no modo automático, pelo `/orquestrar`.
- **Git:** no modo manual, nenhum comando além de `git diff`. No modo automático, o commit é só pelo `commit-bloco`. **Nunca push.**
- Datas e horas no fuso de **Cuiabá**, pela utilidade única; tudo que depende de "agora" usa o `Clock` do projeto.
- Número errado é pior do que número ausente. Onde a regra é provisória, a tela **diz** (nota ou selo).

## Protocolo das sessões

1. **Identifique a sessão:** a primeira mensagem traz `SESSÃO: SONNET` ou `SESSÃO: HAIKU`. Sem essa linha, pergunte e espere. `SESSÃO: OPUS` não é usada nesta sprint: responda que não há bloco para ela.
2. **Descubra onde parou:** leia `docs/progresso/s11.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes ligados a ele verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/s11.md` (crie no bloco 1, com a tabela dos 11 blocos); a marcação entra no mesmo commit;
   - rode `git diff --stat` e responda:

```
BLOCO N CONCLUÍDO (sessão <SONNET|HAIKU>): <título>
Testes: <rodaram / falharam / pulados>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto: o que muda e por quê>
PRÓXIMO BLOCO: N+1 (<título>), sessão <SONNET|HAIKU>.
```

5. **Pare.** No modo manual, mesma sessão no próximo bloco: espere "continuar"; outra sessão: a atual termina ali.

**Blocos da sessão HAIKU (mecânicos).** Só recebe blocos cujo conteúdo já está decidido neste prompt ou em outros arquivos. **Não invente** cenário, número, regra nem nome; o que não achar, escreva como **NÃO CONFIRMADO**; nenhum dado real; rode a varredura de dados pessoais ao final. Se o bloco exigir decisão de projeto ou de código, **pare** e responda `Este bloco precisa da sessão SONNET.`

## Blocos e sessões

| Bloco | Título | Sessão |
|---|---|---|
| 1 | Fixtures: analistas, interessados e cenários de posse e retorno | SONNET |
| 2 | Perfil Analista, vínculo com o responsável e destino após o login | SONNET |
| 3 | Nome do interessado nas listagens | SONNET |
| 4 | Leitura em lote da posse do analista (SIGA e Camunda) | SONNET |
| 5 | Regras da caixa, instantâneo e aviso de recebimento | SONNET |
| 6 | API da caixa, auditoria e parâmetros | SONNET |
| 7 | Front: caixa de entrada e caixinha de aviso | SONNET |
| 8 | Painel do Gestor: pizza na fila por etapa (correção da S10) | SONNET |
| 9 | Painel do Gestor: lista por analista (API e aba lateral) | SONNET |
| 10 | Acesso, vazamento e resiliência (testes) | SONNET |
| 11 | Roteiro de homologação, fechamento e registro pós-apresentação | HAIKU |

---

## Bloco 1 (SONNET): fixtures

Crie os dados sintéticos (reuse o construtor de fixtures da S10) com **cenário fechado**:
- 3 analistas fictícios (CPF inválido de propósito) e 1 sem CPF cadastrado.
- Requerimentos em: Análise, Revisão, Correção (com o interessado, última análise de A1), Validação, Emissão do ato e Concluído.
- **Retornos:** correção que voltou para A1; processo que foi para a emissão do ato e voltou para a **revisão**; outro que voltou para a **análise**; processo novo distribuído; processo que mudou de etapa **dentro** da posse (sem aviso); processo que **saiu** (sem aviso).
- Interessados: pessoa, empresa (razão social); dois requerimentos com o mesmo interessado.
- Um requerimento de ano anterior na posse de A1 (a caixa não usa o recorte do ano).
- Histórico (S8) coerente com cada retorno, para a origem ("Voltou da ...").

Tabela do cenário em `docs/progresso/s11.md` (id, estado esperado, aviso sim/não, origem). **Ponto de parada.** Commit sugerido: `test(caixa): fixtures de analistas, posse e retornos`

## Bloco 2 (SONNET): perfil Analista e login

- Perfil **Analista** com `verCaixa` (e nenhuma outra permissão especial). Matriz de `docs/12` atualizada.
- Tela de usuários (Configurações) permite escolher o perfil Analista; CPF segue como campo existente.
- Depois do login, o Analista vai para a caixa (rota nova, ainda com tela provisória "Em construção"); os outros perfis não mudam. Item de menu "Minha caixa" só para o Analista.
- Permissão `verCaixa` na API: Operador, Gestor e Administrador recebem `403 PERFIL_SEM_PERMISSAO`.

**Testes:** matriz de perfis, destino do login por perfil, e2e do redirecionamento. **Ponto de parada.** Commit sugerido: `feat(acesso): perfil Analista e destino da caixa após o login`

## Bloco 3 (SONNET): nome do interessado nas listagens

- Inclua o **nome do interessado** nos resultados da consulta (lista de requerimentos) e no resumo do requerimento usado nas listas. Descubra, em `integration.siga`, de qual coluna ele vem hoje (o mesmo campo guarda pessoa e empresa) e reuse a leitura existente; **sem consulta por linha** (junção ou lote).
- Regras da seção "Nome do interessado" acima (nunca CPF/CNPJ).
- Front: coluna "Interessado" na lista da consulta, legível em 360 px (quebra de linha, sem cortar sem aviso).

**Testes:** back (pessoa e empresa; nunca CPF/CNPJ) e componente. **Ponto de parada.** Commit sugerido: `feat(consulta): nome do interessado na listagem de requerimentos`

## Bloco 4 (SONNET): leitura em lote da posse

- Porta `PosseDoAnalistaEmLote` e adaptador (SIGA/Camunda), em lotes de até 500, **sem laço por linha**, passando cada lote pelo `LimiteDeLeiturasDoCamunda`.
- Entrada: as chaves dos usuários Analista cadastrados. Saída, por requerimento: id, tarefa ativa (id da etapa, início, responsável como chave opaca), **última tarefa de análise ou revisão** (responsável e fim) para decidir a posse na correção, nome do interessado, tipo/objeto.
- **Reuse** as convenções do `tarefa-atual.sql` e do `painel`. A consulta do histórico de tarefas pode exigir tabelas do Camunda que a conta atual não lê: **se não for possível confirmar com o código e as fixtures**, escreva **NÃO CONFIRMADO** em `docs/10`, entregue a versão que usa só tarefas ativas (correção entra como `FORA` e a lista diz "Correções em andamento: não consideradas") e deixe o ponto para o responsável validar na homologação. **Não invente** nome de tabela ou coluna.
- CPF nunca em log, erro ou retorno. Teto de requerimentos e tempo limite com parâmetros (reuse os do painel; não crie parâmetro novo aqui).

**Testes:** com as fixtures do Bloco 1 (lote de 1.200 para provar os lotes). **Ponto de parada.** Commit sugerido: `feat(caixa): leitura em lote da posse dos analistas`

## Bloco 5 (SONNET): regras, instantâneo e aviso

- Módulo `caixa` (domínio puro): decide o **estado de posse** de cada requerimento; calcula resumo por etapa, itens "com o interessado" e dias na etapa.
- **Instantâneo em memória** imutável (estados da S10, single flight, falha mantém o último bom). A primeira leitura é disparada na primeira requisição de um Analista, em segundo plano.
- **Aviso de recebimento** conforme as decisões acima, com a porta `VistoDoAnalista` (implementação em memória) e a **origem do retorno** pelo histórico da tramitação, só para os itens avisados, com teto.
- Primeiro acesso sem aviso; reinício sem aviso retroativo.

**Testes:** uma tabela de casos, um por linha do cenário do Bloco 1 (aviso sim/não e origem), mais: duas atualizações seguidas com o mesmo item (aviso uma vez), abrir a lista grava o visto, item que chega depois do visto avisa de novo, analista sem CPF, falha de leitura mantendo o último bom. **Ponto de parada.** Commit sugerido: `feat(caixa): regras de posse, instantâneo e aviso de recebimento`

## Bloco 6 (SONNET): API, auditoria e parâmetros

- `GET /api/caixa` (resumo + lista, com `etapa`, `busca`, `ordem`), `GET /api/caixa/avisos`, `POST /api/caixa/avisos/visto` (grava o visto do conjunto devolvido), `POST /api/caixa/atualizar` (limitado). `403` **antes** de qualquer leitura para perfis sem `verCaixa`; o analista só vê **a própria** caixa (a identidade vem da sessão, nunca de parâmetro).
- Contrato no OpenAPI e em `docs/07`/`docs/08`, erros sem detalhe interno.
- Auditoria: abrir a caixa e abrir a lista de mudanças (usuário, quando, quantidade; **sem** CPF e **sem** lista de processos).
- Parâmetros `rastro.caixaAtualizacaoMinutos` e `rastro.caixaIntervaloMinimoManualSegundos` no catálogo de parâmetros e na tela de Configurações, com as propriedades e variáveis `RASTRO_CAIXA_*`.

**Testes:** contrato, 403 por perfil, tentativa de ler a caixa de outro usuário, auditoria sem dado pessoal. **Ponto de parada.** Commit sugerido: `feat(caixa): API da caixa do analista, auditoria e parâmetros`

## Bloco 7 (SONNET): front da caixa

- Tela "Minha caixa" no padrão visual vigente (`docs/design/sigrastro/LEIA-ME.md`): cartão de resumo; abas **Geral** e por etapa; lista com número, **interessado**, objeto, etapa, dias na etapa e a marca "Novo"; busca por interessado ou número; ordenação (mais parado primeiro por padrão); "Atualizado às HH:MM" e botão "Atualizar"; estados `PREPARANDO`, `DESATUALIZADO` e `INDISPONIVEL` com mensagem clara.
- **Caixinha de aviso** no topo, destacada, com a quantidade; ao clicar abre a **lista de mudanças** (processo, interessado, de onde veio) e grava o visto; depois some. Clicar em um item abre o requerimento.
- Aviso de que o "visto" zera quando o sistema reinicia, em texto discreto de ajuda.
- Acessibilidade (a caixinha anunciada ao leitor de tela, foco ao abrir a lista, teclado), 360 px sem rolagem horizontal, sem biblioteca nova.

**Testes:** componente, e2e (Playwright, API simulada) dos cenários: caixa vazia, com itens, aviso e depois sem aviso, busca, ordenação, por etapa, sem CPF vinculado. **Ponto de parada.** Commit sugerido: `feat(front): caixa de entrada do analista e caixinha de aviso`

## Bloco 8 (SONNET): pizza na fila por etapa (correção da S10)

Decisão do responsável: no painel do Gestor, a **fila por etapa** passa de barras para **gráfico de pizza**. Os outros dois gráficos (protocolados × concluídos e carga por responsável) **não mudam**.

- **Sem biblioteca**: SVG e CSS simples, como o fluxograma da S8. Cores do tema vigente, **sem depender só da cor** (cada fatia tem rótulo ou legenda com o número e o percentual).
- **Legenda com todas as etapas**, na ordem do catálogo, **inclusive as zeradas** (o gestor vê a etapa vazia, como nas barras). Etapa zerada não tem fatia, mas aparece na legenda com "0".
- Linhas especiais da S10 continuam: "Fora do catálogo", "Sem instância" e "Etapa não mapeada" aparecem na legenda (e como fatia quando > 0), nunca somem em silêncio.
- **Atenção à regra das etapas paralelas** (D92): um requerimento em etapas paralelas conta uma vez em cada, então a soma das fatias pode passar do total de requerimentos. O percentual é **sobre a soma das contagens exibidas**, e a nota que explica isso (que já existe) continua visível junto do gráfico. Número errado é pior que ausente: o rótulo diz "participação na soma das etapas", nunca "% dos requerimentos".
- Casos de borda: tudo zerado (mostra "Nenhum requerimento em andamento", sem pizza vazia), uma só etapa com valor (círculo inteiro), muitas etapas (legenda sem cortar), instantâneo `PREPARANDO`/`DESATUALIZADO`/`INDISPONIVEL` com o comportamento atual.
- Acessibilidade: alternativa em tabela para leitor de tela, 360 px sem rolagem horizontal, foco e teclado nas fatias, `prefers-reduced-motion` respeitado.
- Registre em `docs/09` a decisão (pizza na fila por etapa, legenda com todas as etapas) e atualize `docs/15`, o roteiro da S10 e a imagem de referência, se houver.

**Testes:** componente (soma, percentuais, zeros na legenda, tudo zerado, uma etapa), e2e do painel com a API simulada, ajuste dos testes de gráfico da S10 que dependiam de barras. **Ponto de parada.** Commit sugerido: `feat(painel): pizza na fila por etapa com legenda de todas as etapas`

## Bloco 9 (SONNET): painel do Gestor, lista por analista

- `GET /api/painel/analistas` (lista de analistas com contagem; **mesmos números das barras**) e `GET /api/painel/analistas/{chave}/requerimentos` (requerimento, interessado, etapa, protocolo; filtros `etapa` e `busca`), a partir do **instantâneo do painel**; estenda-o apenas com o que faltar (por requerimento: etapa, chave do responsável, nome do interessado), mantendo o limite de memória e o teto da S10. Permissão `verPainel`, 403 antes de ler; respeita `RASTRO_MOSTRAR_NOME_RESPONSAVEL`.
- Front: **aba lateral** (separada do dashboard) aberta pelo clique na barra de um analista ou por "Processos por analista": quadro com filtros (analista, etapa, busca), contagem que bate com a barra e o selo de truncamento quando o instantâneo for parcial. Fechar devolve o foco ao dashboard. 360 px.
- Atualize `docs/15` e o roteiro do painel (a lista por barra saiu do "fora de escopo" da S10).

**Testes:** soma da lista = barra (incluindo "Outros", "Fora do catálogo" e truncado), nomes ocultos, 403 por perfil, componente e e2e. **Ponto de parada.** Commit sugerido: `feat(painel): aba lateral com requerimentos por analista`

## Bloco 10 (SONNET): acesso, vazamento e resiliência

- **Matriz de acesso** de todos os endpoints novos por perfil e por sessão (incluindo sem sessão).
- **Varredura de vazamento:** CPF/CNPJ (inclusive mascarado) e chave de responsável em respostas, logs, auditoria, erros e DOM do front.
- **Resiliência:** Camunda e SIGA fora do ar, resposta lenta, lote parcial, analista sem CPF, reinício do sistema (visto zera), duas abas abertas ao mesmo tempo (o visto de uma não quebra a outra).
- Isolamento: a falha da caixa não afeta consulta, tramitação, financeiro nem documentos.

Corrija o que a varredura achar **neste bloco**, sem reduzir cobertura. **Ponto de parada.** Commit sugerido: `test(caixa): acesso, vazamento e resiliência da caixa e da lista do painel`

## Bloco 11 (HAIKU): roteiro, fechamento e pós-apresentação

- **Roteiro de homologação** `docs/roteiros/s11-homologacao.md`: cenários a partir da tabela do Bloco 1 (aviso sim/não e origem esperada), caixa por etapa, busca, ordenação, caixinha e visto, lista do painel batendo com a barra, nomes ocultos, pizza da fila por etapa, analista sem CPF. Coluna "Resultado" como `Não executado`. Itens **NÃO CONFIRMADOS** destacados: posse na correção (histórico do Camunda), nomes e ids de etapas, coluna do interessado.
- Atualize `docs/14` (S11 = caixa do analista e lista por analista, 11 blocos; o antigo tema "pago que virou isento" fora da numeração, na lista pós-apresentação), `docs/10` (pontos provisórios e riscos), `docs/09` (decisões), `docs/15`, `docs/prompts/INDICE.md` (linha da S11) e `docs/progresso/s11.md` completo.
- **Registre uma seção "Primeira sprint pós-apresentação" em `docs/14`**, sem escrever o prompt, com estes itens (cada um com uma linha de motivo):
  1. **H1** (identidade e persistência), incluindo a persistência do "visto" da caixa (hoje em memória, atrás da porta `VistoDoAnalista`).
  2. **Revisão com Opus** das partes sensíveis da S10 e da S11: concorrência do instantâneo (painel e caixa), vazamento de CPF e chave opaca, limites de proteção do SIGA/Camunda e leitura em lote da posse.
  3. **Validação com dados reais** da posse na correção (histórico do Camunda) e das origens "Voltou da ...".
  4. Tarefas de **grupo ainda não assumidas** na caixa.
  5. **Aviso de saída** da posse (decisão atual: não avisar) e histórico das mudanças vistas.
  6. **"Pago que virou isento"** (destaque e explicação), o antigo escopo da S11.
  7. **Documentos sigilosos divergentes** entre SIGA e Rastro (4 casos encontrados na verificação com dados reais).
  8. Conversão de **planilhas sem tipo** no dossiê, se ainda não entregue.
- Rode a **suíte completa uma vez** (back-end, front, e2e e hooks) e anote os números reais em `docs/progresso/s11.md`. Rode a varredura de dados pessoais e confirme que o espelho `.claude/skills/vistas-banco/references/modulos-e-dependencias.md` é igual à fonte em `docs/padroes/`.

**Ponto de parada.** Commit sugerido: `docs(s11): roteiro de homologação, fechamento e registro pós-apresentação`
