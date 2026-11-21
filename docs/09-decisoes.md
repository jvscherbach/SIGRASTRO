# 09 — Decisões

Registro das decisões técnicas. Cada decisão traz o contexto, o que foi decidido e o motivo.

## D01 — Serviço externo ao SIGA

**Contexto:** a correção oficial da Vista do Processo tem previsão longa.
**Decisão:** construir um serviço separado que apenas lê os dados do SIGA.
**Motivo:** não depende do ciclo de entrega do SIGA nem altera o sistema em produção.

## D02 — Leitura direta do banco

**Decisão:** consultar o Oracle diretamente, somente leitura, em vez de usar as APIs de negócio do SIGA.
**Motivo:** a listagem atual da Vista omite origens de documentos e não distingue documentos vigentes de substituídos. O banco permite controle total da regra.
**Consequência:** o serviço acopla-se ao modelo de dados do SIGA. Mudanças no esquema exigem ajuste.

## D03 — Arquivos pela API do Licenciamento (opção B) no protótipo

**Contexto:** o acesso ao Pandora exige liberação, e o contrato do cliente Pandora não está disponível.
**Decisão:** usar `GET /api/arquivo/{chave}` da API do Licenciamento com o token de sessão do usuário.
**Motivo:** permite validar a solução sem novos acessos.
**Consequência:** credencial pessoal e temporária, aceitável só para o protótipo.

## D04 — Java 21, Spring Boot 3 e Angular

**Decisão:** mesma linha tecnológica do SIGA.
**Motivo:** familiaridade de quem vai manter e possibilidade futura de usar o cliente Pandora em Java.
**Atenção:** o `pandora-client` provavelmente foi compilado para Spring Boot 2 (`javax.*`). Verificar a compatibilidade antes de adotar a opção A.

## D05 — PDFBox em vez de iText

**Decisão:** Apache PDFBox 3 para montagem do PDF.
**Motivo:** licença Apache, sem restrições de uso. iText 5 e 7 são AGPL ou comerciais. O PDFBox atende importação de páginas com preservação de tamanho e rotação, marcadores e numeração.

## D06 — LibreOffice para conversão

**Decisão:** LibreOffice headless, via JODConverter, no mesmo container do back-end.
**Motivo:** única opção de qualidade aceitável para planilhas e documentos de escritório sem custo de licença.
**Consequência:** imagem maior e consumo de memória da conversão. Controlado por timeout.

## D07 — Geração assíncrona em memória

**Decisão:** fila em memória com pool de threads, sem persistência.
**Motivo:** evita o timeout da requisição síncrona da Vista atual. A persistência não é necessária no protótipo.
**Consequência:** pedidos em andamento se perdem se o container reiniciar.

## D08 — Dossiê não persistido

**Decisão:** o PDF final existe apenas em disco temporário até o download ou a expiração.
**Motivo:** o objetivo é entregar o arquivo, não arquivá-lo.

## D09 — Sem banco próprio no protótipo

**Decisão:** nenhum schema próprio.
**Motivo:** sem usuários reais, auditoria ou fila durável, não há o que persistir.
**Revisão prevista:** a versão definitiva precisará de um schema para usuários e perfis, auditoria (principalmente de sigilosos) e fila durável. No protótipo, a auditoria funciona apenas em memória; o modelo lógico está em [11 — Auditoria](11-auditoria.md).

## D13 — Auditoria somente inclusão e dividida por perfil

**Decisão:** os eventos de auditoria são apenas incluídos, nunca alterados ou excluídos pela aplicação. A auditoria de documentos é vista por Gestor e Administrador (tudo) e pelo Operador (só vinculados); a de acessos, apenas pelo Administrador, e o item nem aparece para os demais.
**Motivo:** rastreabilidade de acessos a documentos, em especial sigilosos (LGPD), separando quem acompanha o uso dos documentos de quem administra o sistema.

## D14 — Sem integração de escrita com o SIGA

**Decisão:** o serviço não grava nada no SIGA, nem o evento `VISTAS_AO_PROCESSO`. Toda a auditoria fica no schema próprio.
**Motivo:** mantém o serviço desacoplado e somente leitura em relação ao SIGA (RNF01).

## D15 — Três perfis

**Decisão:**
- **Operador:** consulta, visualiza, gera e baixa dossiê apenas de processos e requerimentos em que é o interessado (por CPF/CNPJ); vê a auditoria de documentos desses processos; não vê sigilosos.
- **Gestor:** tudo em consulta, dossiê e auditoria de documentos, inclusive sigilosos, para todos os processos.
- **Administrador:** acesso irrestrito (inclui auditoria de acessos e configurações).

**Motivo:** regra definida pela área: o Operador acompanha o que é dele, o Gestor responde pelos documentos e o Administrador pelo sistema.

## D10 — Documentos referenciados por origem e id

**Decisão:** o front-end identifica documentos por `origem + id`. A chave do arquivo fica só no back-end.
**Motivo:** impede o acesso a arquivos arbitrários por chave e garante que a regra de sigilo seja aplicada no servidor.

## D11 — Lista completa com situação

**Decisão:** listar todos os documentos, inclusive substituídos e indisponíveis, com a situação calculada. Os vigentes vêm pré-selecionados (desde a S2.2, todos os selecionáveis: D47).
**Motivo:** o operador decide o conteúdo do dossiê com visibilidade total.

## D12 — Dossiê completo ou nenhum

**Decisão:** se qualquer documento selecionado falhar, a geração é interrompida com erro e nenhum arquivo é entregue. Não há página substituta nem relatório de geração no fim do dossiê. *(Substitui a decisão anterior de página substituta.)*
**Motivo:** o dossiê deve conter exatamente o que foi pedido; entregar parcialmente poderia ser tomado como completo.
**Consequência:** falhas transitórias (ex.: tempo limite) exigem nova tentativa; o usuário pode remover o documento por decisão explícita.
**Revista na S2.2 (D48):** o princípio (nada incompleto sem decisão) continua; a interrupção na primeira falha e o descarte de tudo deixaram de valer.

## D16 — Geometrias à parte e compactados desconsiderados

**Decisão:** SHP, KML e KMZ não entram no dossiê; são baixados por "Baixar geometrias". ZIP, RAR e 7Z são desconsiderados.
**Motivo:** geometrias não têm representação útil em PDF e são usadas em ferramentas de SIG; arquivos compactados não agregam ao dossiê.

## D17 — Monólito modular por domínio

**Decisão:** back-end único organizado por módulos de domínio (`acesso`, `consulta`, `documento`, `dossie`, `auditoria`, `configuracao`) e adapters `integration.*`, com regras de dependência fiscalizadas por ArchUnit. Mesmo padrão do SIGATIC-API, adaptado.
**Motivo:** um único deploy simples, com fronteiras claras que permitem trocar adapters (memória → banco próprio; token pessoal → credencial de serviço) sem mexer nas regras de negócio.

## D18 — Acesso ao SIGA isolado em `integration.siga`

**Decisão:** só `integration.siga` conhece tabelas, colunas, SQL e enums do SIGA, via JDBC em datasource somente leitura, sem JPA. Os módulos recebem contratos internos.
**Motivo:** o modelo do SIGA não é nosso; mudanças nele (ou no motor de fluxo) devem afetar um único pacote.

## D19 — Dois datasources

**Decisão:** datasource `siga` (leitura, JDBC, pool pequeno, sem Flyway) e, na versão definitiva, datasource `vistas` (schema próprio, JPA, Flyway, `ddl-auto=validate`). No protótipo, adapters em memória implementam as portas que o schema próprio atenderá depois.
**Motivo:** separar o que é só leitura de terceiros do que é nosso, e permitir evoluir do protótipo para o definitivo sem reescrever domínio e casos de uso.

## D20 — Erros com `ProblemDetail` e `errorCode`

**Decisão:** respostas de erro em `ProblemDetail` com `errorCode` estável e correlação por `X-Correlation-Id`. *(Substitui o corpo `{status, mensagem, detalhe}` definido antes.)*
**Motivo:** padrão já adotado no SIGATIC-API; o front decide mensagens pelo código, e o suporte cruza log e auditoria pela correlação.

## D21 — Schema próprio em Oracle

**Decisão:** o schema próprio será Oracle. Desenvolvimento em Oracle Database 23 Free (container Docker); todo DDL e SQL compatível com Oracle 19c, que será o ambiente final se o projeto for aprovado.
**Motivo:** mesmo SGBD do SIGA e da infraestrutura institucional da SEMA-MT.
**Consequência:** proibido usar recursos exclusivos do 23 (ver [`padroes/persistencia-e-migrations.md`](padroes/persistencia-e-migrations.md)).

## D22 — Agendador do Spring, sem ferramenta adicional

**Decisão:** tarefas periódicas com `@Scheduled` dentro da aplicação. Agora, apenas a limpeza de dossiês expirados e de temporários órfãos. Expurgo da auditoria e retomada da fila entram na versão definitiva; ShedLock só se houver mais de uma réplica.
**Motivo:** a expiração dos dossiês (RNF05) já exige uma tarefa periódica, e o escopo pequeno não justifica Quartz ou cron externo.

## D23 — Nome dos pacotes de integração pelo sistema externo

**Decisão:** os pacotes de integração levam o nome do sistema (`integration.scorp`, `integration.siga`, `integration.licenciamento`). *(Substitui `integration.pessoal`.)* Na lista de documentos do usuário a origem continua exibida como "Pessoal".
**Motivo:** fica intuitivo localizar o código de cada sistema.

## D24 — Vínculo com o interessado (RN07) aplicado já na S1, na consulta

**Contexto:** a S1 é a primeira sprint com dado real do SIGA; deixar a restrição do Operador para a S4 exporia qualquer processo a qualquer usuário.
**Decisão:** o back-end aplica a RN07 desde a S1. A decisão de restringir é do domínio (`acesso`: `RestricaoDeInteressado`), e o filtro vai **dentro do SQL** (parâmetro na própria consulta de lista e de contagem), nunca sobre a lista já lida. Busca por número ou detalhe de outro interessado: 403 `NAO_INTERESSADO`. Usuário restrito sem CPF/CNPJ não enxerga nada.
**Motivo:** total e paginação corretos e nenhum dado de outro interessado trafegando. A S4 mantém o restante (sigilo, geometrias, SCORP, tela "Dossiês gerados").

## D25 — A busca sempre devolve uma página

**Decisão:** `GET /api/busca` devolve sempre `PageResponse`; a busca só por número de requerimento devolve página com 1 item, e a só por número de processo devolve todos os requerimentos daquele número (D31); 404 se não existir. O front abre o detalhe direto quando há um único resultado.
**Motivo:** um contrato só para a tela de consulta, sem duas formas de resposta na mesma rota.

## D26 — A `api` sobe sem o SIGA; `minimum-idle: 0`; o SIGA não entra no health

**Decisão:** sem `SIGA_DB_URL` (ausente ou vazia) ou com o SIGA inacessível, a `api` inicia normalmente e as consultas respondem 503 `SIGA_INDISPONIVEL`. O pool do SIGA usa `minimum-idle: 0` e `initialization-fail-timeout: -1`, e o banco do SIGA não faz parte de `/actuator/health`.
**Motivo:** a indisponibilidade de um sistema externo não pode derrubar o serviço nem o front (que depende do health da `api`), e o pool não deve segurar sessões do SIGA sem uso nem reconectar em segundo plano.

## D27 — Conexão e leitura reais do SIGA são homologadas manualmente

**Decisão:** o desenvolvimento nunca se conecta ao banco real do SIGA. O código é provado com portas falsas e com um Oracle 23 Free local (Testcontainers, recorte do modelo em `src/test`). A validação com dados reais segue o roteiro `docs/homologacao/S1-roteiro-homologacao.md`, executado pelo responsável do projeto.
**Motivo:** credenciais e acesso ao SIGA não passam por automação. **Limite:** os testes de integração provam o SQL no 23 Free, não no 19c.

## D28 — Dicionário de dados em HTML e MER em `docs/database/`

**Decisão:** a partir da primeira migration do schema próprio, toda entrega que alterar o banco atualiza `docs/database/` com o dicionário de dados em HTML e o MER (mapeado e conceitual), gerados a partir do Oracle local por script em `scripts/database/`. Detalhes em [`padroes/documentacao.md`](padroes/documentacao.md).
**Motivo:** o responsável pelo banco precisa de informação fiel e atualizada sobre o schema; gerar a partir do banco evita documentação desatualizada.

## D29 — Atividade da outorga lida de `SIGA2_ARRECADACAO.ATIVIDADE`

**Contexto:** a tela do SIGA busca a descrição da atividade de cada par finalidade/atividade pela API da Arrecadação.
**Decisão:** o SIGA Vistas lê a descrição direto da tabela `SIGA2_ARRECADACAO.ATIVIDADE` (por `FINALIDADE_ATIVIDADE.ID_ATIVIDADE_ARRECADACAO`), com `LEFT JOIN`: sem linha, a atividade aparece como "—".
**Motivo:** o texto é o mesmo (conferido no processo 6023/2025, atividade 4054), a leitura fica no mesmo datasource somente leitura e o `integration.arrecadacao` continua desativado.

## D30 — Prioridade de atendimento pelos critérios, não pelo `PESO_PRIORIDADE`

**Decisão:** a prioridade é binária e vem de `PRIORIDADE_PROCESSO_CRITERIO`: pelo menos um critério é "Atendimento prioritário"; nenhum, "Atendimento normal". O SQL devolve só os códigos; a tradução para texto (enum do SIGA, código desconhecido como "Critério N") fica no domínio (`PrioridadeAtendimento`, `CriterioPrioridade`).
**Motivo:** o cadastro manual de critérios no SIGA não atualiza `REQUERIMENTO.PESO_PRIORIDADE`, que por isso não é confiável.

## D31 — Número de processo tratado como 1:N; só a `REQUERIMENTO` importa

**Contexto:** `REQUERIMENTO.NUMERO_PROCESSO` não tem constraint nem índice, e o SIGA não impede repetição (havia um caso, `445/2022`, em 21/10/2026).
**Decisão:** a busca por número de processo devolve a página com todos os requerimentos daquele número (ordem: abertura decrescente e id). Com 1 resultado o front abre o detalhe; com mais, lista para o usuário escolher e mostra um aviso. A chave é sempre `REQUERIMENTO.ID`. A busca por número de requerimento continua exata. Nenhuma consulta lê a tabela `PROCESSO` (histórico do que foi protocolado), e não se filtra por `PROCESSO.ORIGEM`. RN07: a restrição do interessado vai no SQL; se o número existe só para outros interessados, 403 `NAO_INTERESSADO`.
**Motivo:** o dado real admite repetição; escolher um dos requerimentos sozinho esconderia o outro.

## D32 — Responsável pela tarefa: nome resolvido no SQL, visível só para Gestor e Administrador

**Decisão:** `ASSIGNEE_` guarda o CPF do servidor. O nome é resolvido **no próprio SQL** da tarefa atual: `SERVIDOR` (versão mais recente por CPF) e, se não houver, `PESSOA`. Só tenta resolver quando o valor tem 11 dígitos; a conversão de `NVARCHAR2` fica do lado de `ASSIGNEE_` (`TO_CHAR`), para não impedir índice nas tabelas de nome. O adapter devolve só a situação (`ATRIBUIDO`, `NAO_ATRIBUIDA`, `NAO_IDENTIFICADO`) e o nome: o CPF nunca sai do SQL. O caso de uso remove o responsável para quem não pode vê-lo (`Perfil.podeVerResponsavelTarefa()`, RN12): o Operador recebe `tarefaAtual` sem o campo `responsavel`.
**Motivo:** o CPF do servidor é dado pessoal e não serve ao usuário; o SIGA não tem regra própria de visibilidade, e o Operador (interessado) não precisa saber quem analisa o processo.

## D33 — Uma consulta SQL por origem de documento

**Contexto:** a primeira ideia era um `UNION ALL` único com uma parte por origem (docs/04).
**Decisão:** cada origem tem o próprio arquivo em `resources/sql/siga/documentos/` e é executada em sequência. Todas devolvem as mesmas colunas (`ORIGEM, ID, ID_REQUERIMENTO, CHAVE, TITULO, NOME_ARQUIVO, TIPO, DATA_DOC, DATA_FIM, SIGILO`) e **não filtram vigência**. Cada origem pode ser desligada por `vistas.documentos.origens.<origem>`; origem desligada não é consultada. Falha numa origem ligada derruba a listagem inteira (503 ou 500), sem estado parcial. (S2.1: as origens ligadas são as seis da Vista, D41.)
**Motivo:** isolar falhas (um grant ou uma coluna errada aponta a origem exata), poder desligar uma origem sem mexer em SQL e não esconder uma origem quebrada atrás de um `UNION`.

## D34 — Verificação sempre sem sistemas externos

**Contexto:** na entrega anterior a `api` foi subida com as variáveis `SIGA_DB_*` do `.env.local` preenchidas (a checagem não viu linhas com espaço no início) e leu dados reais do SIGA.
**Decisão:** todo `docker compose up` de verificação usa os três arquivos `-f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml`. O terceiro esvazia, no serviço `api`, todas as variáveis que levam a sistemas externos (SIGA, API de arquivos, SCORP, schema próprio); `environment` tem precedência sobre `env_file`. Antes de qualquer chamada, confirma-se **dentro do container** que cada variável está vazia, sem imprimir valores. O `.env.local` nunca é aberto nem impresso. Se algo vier preenchido ou se uma resposta trouxer dado real: parar, derrubar os containers (sem `-v`) e relatar.
**Motivo:** uma checagem que depende de formato de arquivo falha em silêncio; a que roda dentro do container sobre o ambiente efetivo, não. Dados pessoais reais nunca entram em arquivo, mensagem ou log.

## D35 — Playwright como dependência de desenvolvimento

**Exceção a "sem dependências novas" ([13](13-padroes-tecnicos.md)):** `@playwright/test` (versão fixa, `devDependencies`) para o teste e2e do quadro do topo e do fluxo de documentos. Não entra em imagem de produção (o Dockerfile só copia o `dist`), `npm audit` continua em 0 e a API é simulada por `page.route`: nenhum sistema real é tocado. O navegador (Chromium) é instalado fora do projeto, por `npx playwright install chromium`.
**Motivo:** o defeito do quadro do topo reapareceu duas vezes porque os testes só olhavam a estrutura do DOM; a altura renderizada só se mede num navegador de verdade.

## D36 — RN07 e sigilo antecipados da S4 para a S2

**Decisão:** o back-end aplica, já na S2, a RN07 em `GET /requerimentos/{id}/documentos` e `POST /dossies` (Operador de outro interessado: 403 `NAO_INTERESSADO`; inexistente: 404) e o sigilo (RF20): sigilosos aparecem para todos, mas para o Operador vêm **mascarados** (título "Documento sigiloso", sem nome de arquivo nem extensão), com `acessivel=false` e `bloqueio=SIGILOSO`, e o `POST /dossies` com um deles responde 403 `DOCUMENTO_SIGILOSO`. A matriz completa e a validação dela por `MockMvc` seguem na S4.
**Motivo:** não entregar um furo de sigilo na primeira sprint que lê arquivos reais.

## D37 — `id` de documento em texto em todo o contrato

**Decisão:** o `id` do documento passa a ser texto (`"804328"`) na lista, no `POST /dossies` e no andamento. A chave do arquivo continua interna (RNF09).
**Motivo:** acomodar o SCORP (ids que não são numéricos) sem quebrar o contrato depois.

## D38 — Geração: documentos em sequência, `DOSSIE_THREADS` gerações simultâneas

**Decisão:** dentro de um pedido os arquivos são obtidos um a um, na ordem cronológica; o executor limitado a `DOSSIE_THREADS` (padrão 2) roda pedidos diferentes ao mesmo tempo, então o total de downloads simultâneos nunca passa desse número. A primeira falha interrompe e descarta tudo (RN09). **Desde a S2.2 (D48):** a sequência continua, mas as falhas são coletadas e a obtenção segue até o fim; só a credencial recusada para na hora. Os PDFs são abertos com cache em arquivos temporários (PDFBox 3) e ficam abertos até o PDF final ser salvo, porque o PDFBox compartilha objetos entre origem e destino.
**Motivo:** a regra "completo ou nenhum" fica determinística (os seguintes nunca são processados) e a memória é limitada. Se o tempo de geração pesar, a S3 avalia obter em paralelo dentro do pedido.

## D39 — Pedidos em memória: o registro sobrevive um pouco ao vencimento

**Decisão:** o arquivo é apagado ao vencer (`DOSSIE_TTL_MINUTOS`) ou ao fim do download (`attachment`); o registro do pedido continua por 60 minutos, para o front mostrar "expirado" (410) em vez de "não encontrado" (404). A limpeza de pastas órfãs só apaga pastas com nome de UUID dentro de `VISTAS_DOSSIE_DIRETORIO`, para nunca tocar em arquivos que não são do dossiê, mesmo que a variável aponte para uma pasta com outro conteúdo.

## D40 — Servidor HTTP de teste sobre `ServerSocket`

**Contexto:** o prompt pedia `com.sun.net.httpserver.HttpServer`. No JDK 25 em Windows (a máquina de desenvolvimento) o `HttpServer` falha ao abrir o seletor (`Unable to establish loopback connection`), então qualquer teste com ele quebraria `./mvnw clean verify` ali.
**Decisão:** os testes de `integration.licenciamento` usam `ServidorHttpDeTeste`, um servidor HTTP/1.1 mínimo sobre `ServerSocket` bloqueante (sem dependência nova, sem seletor), com resposta com `Content-Length`, em partes (chunked) e atraso.
**Motivo:** manter o critério (nada de rede externa, sem dependência) sem depender de um comportamento do JDK que não funciona na máquina do responsável.

## D41 — Documentos: só as seis origens que a Vista lista

**Contexto:** o teste de fumaça da S2 falhou com `ORA-00904: "A"."NOME_ARQUIVO"` em `COMPLEMENTACAO_ITEM_ARQUIVO`: a S2 assumiu colunas por padrão de nome e ampliou as origens por suposição. A análise do código do SIGA Licenciamento mostrou que a seção "Arquivos do Processo" da Vista lê seis tabelas.
**Decisão:** a lista passa a ter exatamente as origens da Vista (`DOCUMENTO_PROCESSO`, `DOCUMENTO_REQUERIMENTO`, `COMPLEMENTACAO`, `IMOVEL`, `REPRESENTANTE_LEGAL`, `RESPONSAVEL_TECNICO`), com as colunas confirmadas no banco. Removidas: `PARECER_ANEXO`, `JUNTADA`, `TITULACAO`, `PRORROGACAO` e `EXTRATO_PUBLICACAO` (SQL, mapeamento, propriedades, testes e grants). Nada de origem nova sem prova de que a Vista a lista (docs/04, "Origens não listadas pela Vista"; P04).
**Motivo:** reproduzir a Vista e não depender de colunas não confirmadas.

## D42 — Data do documento é a da própria origem

**Decisão:** a data da ordem cronológica (RN03) é a coluna de data da própria origem. A regra "maior data de assinatura" e a tabela `DOCUMENTO_PROCESSO_SIGNATARIO` foram removidas: a Vista não usa assinatura. Data nula não derruba a listagem: o documento vai ao final; empate por origem e id.
**Motivo:** a mesma ordem inicial da Vista; menos uma tabela e um grant.

## D43 — Sigilo real em representante legal e responsável técnico (divergência consciente)

**Contexto:** a Vista do SIGA ignora a coluna `SIGILO` de `REQ_REP_LEGAL_DOCUMENTO` e `REQ_RESP_TECNICO_DOCUMENTO` e mostra sempre "Não" (R25).
**Decisão:** o SIGA Vistas usa a coluna real `SIGILO` nas duas origens, com a normalização tolerante e "na dúvida, sigiloso" (RN06). A regra é mais restritiva que a da Vista, de propósito.
**Motivo:** exibir como público um documento marcado como sigiloso é o erro mais caro.

## D44 — Extensão desconhecida: tipo `DESCONHECIDO`

**Contexto:** os nomes vêm sem tipo e sem extensão garantida (a complementação usa `TITULO`; o imóvel pode ter nome em branco).
**Decisão:** sem extensão ou com extensão não reconhecida o `tipoArquivo` é `DESCONHECIDO` (substitui `OUTRO`). O documento aparece, é selecionável (se vigente, com chave válida e acessível pelo perfil), fica fora da pré-seleção (desde a S2.2 entra na seleção inicial: D47), mostra a etiqueta "tipo não informado" e não recebe o bloqueio `NAO_PDF`. Ao baixar, o validador confere a assinatura `%PDF-`: PDF segue normal; senão a falha é do documento ("o arquivo não é um PDF (tipo detectado: X); a conversão chega na próxima etapa") e vale a RN09. Os totais ganham `tipoNaoInformado`.
**Motivo:** não esconder documento por falta de extensão nem deixar entrar no dossiê o que não é PDF.

## D45 — Pré-seleção é conveniência do SIGA Vistas

**Decisão:** a Vista não pré-seleciona nada; o SIGA Vistas mantém a pré-seleção de **PDF vigente, sem bloqueio**. **Substituída pela D47 (S2.2).**
**Motivo:** gerar o dossiê comum com um clique; o usuário ajusta a seleção.

## D46 — Deduplicação por chave diverge da Vista

**Decisão:** a Vista não deduplica; o SIGA Vistas sim (RN05), para não repetir páginas no dossiê. Precedência: a ordem das origens na tabela de docs/04. Chave `"0"` ou vazia nunca deduplica. RL e RT não duplicam por construção do SQL.
**Motivo:** o dossiê é um PDF único; repetir o mesmo arquivo só o infla.

## D47 — Seleção total dos selecionáveis (S2.2)

**Contexto:** na homologação da S2/S2.1, documentos de tipo não informado ficavam fora da primeira geração, e a tela do 769/2025 mostrava "145 documentos · 71 vigentes" com "144 selecionados", o que parecia não fechar. O dossiê responde a processos jurídicos: o padrão é incluir tudo o que pode entrar.
**Decisão:** `selecionavelPorPadrao` passa a significar "selecionável" (sem `bloqueio`): vigentes e sem efeito, de qualquer tipo, inclusive `DESCONHECIDO`, vêm selecionados (RN13). Continuam fora: indisponíveis, não PDF por extensão conhecida e sigilosos para o Operador. Os totais ganham `semEfeito`, e a tela mostra `N documentos · V vigentes · S sem efeito · I indisponíveis` (somam N). Substitui a D45 e o trecho "fica fora da pré-seleção" da D44.
**Motivo:** a Vista lista os "sem efeito" e permite incluí-los; deixar de fora por padrão já produziu dossiê incompleto. Com a soma explícita, o total de selecionados é conferível.

## D48 — Falhas coletadas e continuação sem reiniciar (S2.2)

**Contexto:** na homologação da S2/S2.1, com arquivos de ~50 MB, "Tentar novamente" e "Remover documento e gerar" reiniciavam o dossiê inteiro (novo `POST`), baixando de novo o que já tinha dado certo.
**Decisão:** a geração percorre todos os documentos e coleta as falhas; com falha, o pedido fica em `PENDENTE_DECISAO` com a lista completa e nada é entregue. Os arquivos obtidos ficam no diretório do pedido. A decisão é `POST /dossies/{id}/continuacao` (`TENTAR_NOVAMENTE` refaz só os com problema; `REMOVER_E_CONTINUAR` os marca `REMOVIDO` e monta). Credencial recusada para a obtenção na hora e também espera decisão. O estado `FALHA` deixa de existir; o TTL conta desde a última atividade, inclusive na espera. Só quem criou o pedido, Gestor e Administrador decidem (os demais recebem 404); fora de `PENDENTE_DECISAO`, 409 `DOSSIE_SEM_DECISAO_PENDENTE`. Revê a D12 (RN09 reescrita em docs/02).
**Motivo:** manter a regra "nada incompleto sem decisão explícita" sem pagar de novo o download do que já foi obtido; com credencial nova (token por arquivo), só o que falta é obtido.
**Consequência:** o disco guarda os documentos de um pedido parado até a decisão, o cancelamento ou o TTL (dimensionamento em docs/12). A lista de documentos não incluídos no PDF é do bloco 6; a interface completa da decisão, do bloco 7.

## D49 — Download de dossiê grande por ticket de uso único (S2.2)

**Contexto:** com arquivos de ~50 MB, um dossiê passa fácil de centenas de MB. Baixar por `Blob` (o front segura o arquivo inteiro na memória do navegador antes de salvar) e pré-visualizar num `iframe` deixam de ser viáveis. O token de sessão não pode ir para a URL.
**Decisão:** abaixo de `DOSSIE_BLOB_MAX_MB` (padrão 150) nada muda: Blob e pré-visualização. Acima, o andamento indica `entregaPorTicket`. O front pede `POST /dossies/{id}/arquivo/ticket` e abre `GET /dossies/{id}/arquivo?disposicao=attachment&ticket=...` direto, para o navegador baixar em streaming. O ticket tem 256 bits aleatórios, é de uso único, vale 60 s e fica preso ao pedido e ao usuário. O servidor guarda só o resumo SHA-256; ticket inválido dá 403 `TICKET_INVALIDO` sem dizer o motivo. A rota com ticket é a única que o filtro deixa passar sem Bearer; se vier Bearer, ele precisa ser do mesmo usuário. O ticket não vai para log: a aplicação não registra a query string, e o nginx desliga o log de acesso dessa rota e reduz o de erro a `crit`. A resposta leva `Referrer-Policy: no-referrer`. A pré-visualização some acima do limite, com aviso.
**Motivo:** download em streaming de arquivos grandes sem expor a sessão. Com uso único e 60 s, um ticket que fique no histórico do navegador não serve para nada.
**Consequência:** a página não vê o fim do download feito pelo navegador; ela avisa que o arquivo é descartado quando o download termina. O ticket fica na URL (histórico do navegador e eventuais proxies intermediários fora do nginx do projeto); a mitigação é o uso único e a validade curta.

## D50 — Front sem root, na porta 8080 do container (CI, bloco 5)

**Contexto:** a auditoria de segurança da pipeline encontrou o processo mestre do nginx do front rodando como root (padrão da imagem `nginx:alpine`). A imagem da `api` já roda como o usuário `vistas`.
**Decisão:** o front roda como o usuário `nginx` da própria imagem base. O `nginx.conf` passa a ouvir na **8080** (porta acima de 1024 dispensa privilégio), o Dockerfile dá ao usuário só o cache/temporários (`/var/cache/nginx`) e o PID, e o `HEALTHCHECK` usa a 8080. No Compose local o mapeamento passa de `8080:80` para `8080:8080` (o endereço no navegador não muda).
**Motivo:** menor privilégio sem trocar a imagem base (a `nginx-unprivileged` seria outra fonte a acompanhar) e sem depender de o runtime liberar portas baixas para usuário comum.
**Consequência:** o futuro manifesto de implantação (Kubernetes ou outro, P15) aponta para a porta 8080 do front. Conferido com a imagem construída: processos como `nginx` (uid 101), `healthy` e `GET /` 200.

## D51 — Pipeline só valida e publica imagens; deploy depende do servidor da SEMA (CI)

**Contexto:** o ambiente de execução (P15) ainda não existe.
**Decisão:** o GitHub Actions valida (`api`, `front`, `e2e`, `docker`, `compose`), mantém as dependências atualizadas (Dependabot) e publica imagens no GHCR só em tag `vX.Y.Z`, sem `latest`. Não há deploy nem credencial de sistema externo na pipeline.
**Motivo:** não fixar uma forma de implantação antes de o servidor existir, e manter os segredos do SIGA fora de qualquer serviço de CI.
**Consequência:** o deploy automatizado será um prompt futuro, depois da definição do ambiente (P26). A seção "Integração contínua" de [13](13-padroes-tecnicos.md) descreve os workflows.

## D52 — CodeQL removido: projeto pessoal sem orçamento, nada pago (CI)

**Contexto:** o SIGA Vistas é um projeto pessoal, em protótipo, sem orçamento. O repositório pode ser privado no plano gratuito do GitHub. O code scanning (CodeQL) em repositório privado exige o GitHub Code Security, que é pago.
**Decisão:** o `codeql.yml` foi removido. Nenhum recurso, serviço, licença ou ferramenta com cota paga entra no projeto; antes de usar qualquer recurso, confirma-se que é gratuito para repositório privado no plano gratuito, e na dúvida não se usa (regra permanente no `CLAUDE.md`).
**Motivo:** um workflow que falha ou que depende de plano pago não serve a um projeto sem orçamento.
**Consequência:** os checks obrigatórios da `main` são só `api`, `front`, `e2e`, `docker` e `compose`. A auditoria de custo do restante da pipeline está na D53.

## D53 — Pipeline ajustada a repositório privado no plano gratuito (CI, ci-sem-custo)

**Contexto:** a regra "nada pago" (D52) exigia conferir o restante da pipeline e do tutorial na pior hipótese: repositório privado, conta pessoal, GitHub Free. A documentação oficial do GitHub (26/10/2026) confirma que nesse cenário a proteção de branch, o secret scanning e o push protection, os Environments e os atestados de artefato não estão disponíveis sem pagar, e que o Actions tem 2.000 min/mês em runner de 2 vCPU. Já são gratuitos o Dependabot, o cache do Actions até 10 GB e, por enquanto, o GHCR.
**Decisão:** (1) sem proteção de branch, a `main` é protegida pelo fluxo: sempre PR, merge só com os cinco checks verdes, nunca push direto; (2) o `ci.yml` ganha o job `mudancas`, e mudança só de documentação (`docs/`, `*.md`, `.claude/`) pula `api`, `front`, `e2e` e `docker` por `if` (não por `paths-ignore`, que deixaria check pendente se um dia houver proteção); (3) o Dependabot de versões passa a mensal (os alertas e as atualizações de segurança continuam imediatos); (4) o secret scanning é substituído por varredura local com `gitleaks`, fora da pipeline; (5) a retenção do GHCR é manual; (6) recurso cuja gratuidade não foi confirmada (rulesets, P27) não é usado. A tabela "Recurso × situação × decisão" fica em [13](13-padroes-tecnicos.md).
**Motivo:** manter a verificação de todo PR dentro da cota gratuita sem depender de recurso pago, e sem esconder o que a documentação não confirma.
**Consequência:** o GitHub não impede um merge com check vermelho (R32), e a cota pode acabar num mês de muitos PRs (R28). Tornar o repositório público tornaria gratuitos a proteção, o code scanning e o secret scanning, mas depende da varredura do histórico (R27) e de decisão do responsável; não é recomendado nem descartado aqui.

## D54 — Documento convertido identificado no dossiê (S3)

**Contexto:** o dossiê responde a processos jurídicos. Um PDF gerado a partir de uma planilha, de um texto ou de uma imagem não é o arquivo que o interessado juntou; quem lê o dossiê precisa saber disso sem abrir o sistema.
**Decisão:** o documento convertido aparece no **sumário** ("Título (convertido de XLSX)"), no **marcador** ("Título (convertido de XLSX)") e na **capa** ("N documentos incluídos de M listados; K convertidos para PDF"). O formato é o do tipo real (conteúdo), não o do nome. Planilha com aba em página única pela escala mínima ganha "*" e uma nota de rodapé no sumário. A nota fica na mesma linha do título (o título é cortado antes dela), sem mudar a paginação determinística. A conversão acontece na obtenção, antes da montagem, e o PDF convertido nunca é convertido de novo na continuação; o original é guardado só quando a conversão falha, para a nova tentativa não baixá-lo de novo. Falha de conversão é falha do documento (RN09, S2.2).
**Motivo:** transparência sobre o que foi transformado, sem alterar a ordem cronológica nem as páginas iniciais do sumário.
**Consequência:** a nota ocupa parte da largura do título no sumário (títulos longos de documentos convertidos saem mais cortados). O texto da faixa "Versão convertida" do modal (bloco 7) segue o mesmo formato.

## D55 — Conteúdo do modal pelo requerimento da lista, com cache sem atalho de acesso (S3)

**Contexto:** o modal pede o conteúdo por `origem` e `id` (a chave do arquivo nunca sai, RNF09). Os ids se repetem entre origens e o mesmo documento é exibido na lista de um requerimento, que é a unidade de acesso (D31, RN07). Achar o requerimento só a partir de `origem` + `id` exigiria uma consulta nova por origem no SIGA.
**Decisão:** a rota recebe `requerimento` (o `idRequerimento` do item da lista) e o servidor **relê a lista desse requerimento** a cada requisição: RN07 no requerimento, o documento tem de estar na lista (senão 404), sigilo e situação. O cache em disco guarda só bytes, por `(origem, id)`, e é consultado **depois** dessas checagens. O cliente não ganha nada informando outro requerimento: o documento não estará na lista dele. A conversão usa as mesmas portas e parâmetros do dossiê, mas não o `PreparadorDePdf` (que decide falha de documento do dossiê).
**Motivo:** reaproveita a leitura e as regras da lista, que já são testadas; nenhuma SQL nova contra o SIGA; o acesso não depende de estado guardado.
**Consequência:** cada abertura do modal relê a lista do requerimento no SIGA (a mesma leitura da tela). O front passa o `idRequerimento` do item. Se a leitura pesar, a medição do bloco 9 diz.

## D56 — Conversão pelo LibreOffice isolado, com chave de segurança (S3)

**Contexto:** o dossiê responde a processos jurídicos e converte documentos de terceiros (planilhas, textos, HTML) que podem trazer macro, vínculo externo, recurso remoto, ZIP "bomba" ou arquivo travado. A D06 escolheu o LibreOffice via JODConverter; faltava decidir como isolá-lo.
**Decisão:** o LibreOffice roda como processo externo, **só** em `integration.libreoffice` (JODConverter 4.4.9, módulo local sem starter), no usuário sem privilégios da imagem. Pool sob demanda de `CONVERSAO_PROCESSOS` instâncias, cada uma com perfil próprio e descartável, reiniciada a cada `CONVERSAO_TAREFAS_POR_PROCESSO` tarefas, com timeout de tarefa e encerramento forçado (sem `soffice` órfão ao parar nem na subida). Macro nunca executada, vínculo externo nunca atualizado, rede bloqueada pelo perfil, HTML limpo antes (jsoup), entrada copiada para diretório de trabalho próprio com nome gerado, descompactação em fluxo com limite de bytes e de entradas (ZIP "bomba"), saída validada (abre, tem páginas, tamanho e páginas máximos). Falha do processo (e não do documento) tem uma nova tentativa. A chave `CONVERSAO_HABILITADA` (padrão ligada) desliga tudo: com `false`, ou com o conversor indisponível, vale o comportamento da S2.2 (só PDF; o resto é falha do documento ou `NAO_PDF`). A aplicação sobe normalmente sem o LibreOffice.
**Motivo:** conversão fiel (tamanho e orientação do próprio documento, RNF06) sem componente comercial; o isolamento limita o dano de um arquivo hostil; a chave dá uma saída imediata se a conversão causar problema em produção.
**Consequência:** imagem da `api` de ~1,09 GB e memória do `soffice` fora do heap (R42); testes que exigem o LibreOffice rodam no CI com instalação própria (R40) e pulam com aviso na máquina sem ele. Riscos de processo externo em `docs/10` (R33, R34).

## D57 — Imagens para PDF por PDFBox, sem LibreOffice (S3)

**Contexto:** PNG e JPG também entram no dossiê. O LibreOffice converteria imagens, mas com página e escala próprias e pagando o custo do processo externo.
**Decisão:** as imagens passam por um conversor próprio (`ConversorDeImagens`, em `integration.imagem`), só com PDFBox e ImageIO: uma página A4 (retrato ou paisagem pela proporção, depois da orientação EXIF), margem de 18 pt, sem distorção e sem ampliar além de 150 dpi; PNG transparente sobre fundo branco; JPEG com os bytes originais. O limite `CONVERSAO_IMAGEM_MAX_MEGAPIXELS` (100) é decidido pelas dimensões, **antes** de decodificar.
**Motivo:** menos dependência do processo externo (a imagem converte mesmo com o LibreOffice indisponível), controle da orientação e da transparência, e proteção de memória contra imagem enorme.
**Consequência:** o formato vale pelo conteúdo, não pela extensão; imagem ilegível ou truncada é falha do documento (`CORROMPIDO`).

## D58 — Planilhas: largura por padrão, escala mínima e página única só quando cabe (S3)

**Contexto:** por padrão o LibreOffice divide planilhas largas em várias folhas e corta colunas; o motivo da S3 é nunca perder conteúdo. A medição do bloco 9 mostrou que a "página única" de uma aba muito alta (30 mil linhas) passava do limite de página do LibreOffice (~5 m) e as linhas de baixo sumiam sem erro (R41).
**Decisão:** modo padrão `LARGURA` (cada aba em 1 página de largura, paisagem quando mais larga que alta, abas visíveis, cada uma em página nova, ocultas fora). Se a escala estimada ficar abaixo de `CONVERSAO_PLANILHA_ESCALA_MINIMA` (40%), a aba vai para uma página única (`PAGINA_POR_ABA` só naquela aba) **apenas quando a página cabe no limite do LibreOffice**; senão é paginada na largura mesmo abaixo da escala mínima. Depois de exportar, o conversor confere que o texto da última linha visível de cada aba aparece no PDF; se não aparecer, o documento falha ("a planilha não coube inteira no PDF"; motivo `PLANILHA_INCOMPLETA`). Abas em página única viram nota no sumário ("*"). O modo `PAGINA_POR_ABA` fica como alternativa por configuração.
**Motivo:** a ordem de preferência é conteúdo completo, depois legibilidade; um erro claro é melhor que um PDF que parece completo e não é.
**Consequência:** planilhas muito largas e altas podem sair com texto pequeno (paginadas na largura). **A escolha final entre `LARGURA` e `PAGINA_POR_ABA` é da homologação com as planilhas reais** (roteiro da S3, passo 6): PENDENTE, a registrar aqui e em `docs/12`.

## D59 — Planilha convertida com o nome do documento no cabeçalho, sem rodapé do Calc e centralizada (S3.1)

**Contexto:** na homologação da S3, a planilha real convertida mostrava no topo o nome da aba ("Planilha1", cabeçalho padrão do Calc), duas numerações (o "Página 1" do Calc e o "Página X de Y" do dossiê) e a tabela encostada à esquerda.
**Decisão:** o cabeçalho de cada página de planilha convertida é o **nome do documento** como o dossiê o conhece (o texto do sumário e do marcador, sem "convertido de"), com `— <aba>` quando mais de uma aba entra no PDF; texto longo é cortado com reticências; sem título, não há cabeçalho. O **rodapé do Calc é removido** (a numeração oficial é a do dossiê, que passa ao **canto inferior direito**, alinhada à direita, em **todas** as páginas numeradas, também nos PDFs originais e nas seções geradas: bloco 2 da S3.1). A tabela fica **centralizada na horizontal**. O modo padrão continua `LARGURA` (A4). Documentos de texto não mudam.
**Motivo:** o leitor do dossiê precisa saber de que documento é a página; uma numeração só; layout de página impressa.
**Consequência:** a porta `ConversorDeDocumentos` passa a receber o título (dado não confiável, só texto, limpo e limitado). Conta como "mais de uma aba" quem entra no PDF: aba vazia não gera página e não conta. O cabeçalho ocupa 7 mm de altura em cada página da planilha. **S3.2:** o fundo branco que a S3.1 pôs atrás do número foi **removido a pedido** e o número passou a **negrito**; o número pode se sobrepor ao conteúdo do documento nessa região (margem inferior direita, a menos de ~30 pt das bordas), sem cobrir nem deslocar nada.

## D60 — Política de acesso única no back-end (S4, bloco 1)

**Contexto:** até a S3, a RN07, o sigilo (RF20), o responsável pela tarefa (RN12) e o dono do pedido de dossiê eram decididos em sete casos de uso diferentes (`consulta`, `documento`, `dossie`), por dois serviços do `acesso` (`ObterRestricaoDeInteressado`, `ObterPermissoesDoUsuario`) e por uma comparação de perfil no próprio `LocalizarPedido`. O dossiê responde a processos jurídicos: uma regra esquecida num endpoint novo é o pior erro possível da S4.
**Decisão:** `acesso.application.PoliticaDeAcesso` é o **único** lugar que decide acesso: `podeAcessarRequerimento`, `podeVerDocumentoSigiloso`/`podeAcessarDocumento`, `podeVerResponsavelTarefa`, `podeAcessarPedido` (com `PedidoDeUsuario`, para o `acesso` não depender do `dossie`), `mascararTitulo`/`mascararDadoDoArquivo` e `restricaoDoUsuarioAtual` (filtro das buscas em lote, dentro do SQL como em D24). Os `exigir...` lançam as exceções de sempre (403 `NAO_INTERESSADO`, 403 `DOCUMENTO_SIGILOSO`); o 404 de recurso inexistente ou alheio continua com o caso de uso, que é quem sabe se o recurso existe. Os dois serviços antigos foram removidos. O `ArchitectureTest` proíbe, fora do `acesso`, depender de `Perfil` ou ler `Usuario.perfil()`/`Usuario.cpfCnpj()` e aplicar `RestricaoDeInteressado` por conta própria; proíbe controllers de depender da política, do usuário atual ou da restrição; e proíbe o `acesso` de depender dos demais módulos. A matriz por endpoint está em `docs/07` ("Matriz de acesso por endpoint") e o resumo por perfil em `docs/12`.
**Reforços (sem mudança para os usuários de hoje):** (1) pedido de **outro usuário** só para perfil que acessa qualquer requerimento **e** qualquer sigiloso (Gestor e Administrador), porque o pedido pode conter sigilosos; (2) o **dono** Operador só acessa o pedido enquanto o interessado gravado no pedido (o da capa) continuar sendo o dele.
**Consequência:** um endpoint novo precisa de linha na matriz do `docs/07` e de uma pergunta à política no caso de uso; o `MatrizDeAcessoTest` (bloco 9) prova a matriz por `MockMvc`. A perda de vínculo **no SIGA** depois de criado o pedido não é reconferida no andamento nem no arquivo (seria uma leitura do SIGA a cada consulta de andamento): ver R44 e o bloco 7.

## D61 — Geometrias em lista própria e ZIP tudo ou nada (S4, bloco 3)

**Contexto:** até a S3 as geometrias (SHP, KML, KMZ) eram descartadas da lista (D16). A S4 as devolve numa seção própria, com "Baixar geometrias".
**Decisão:** `GET /requerimentos/{id}/documentos` traz `geometrias` à parte (fora de `itens`, dos totais, da seleção e do dossiê), com `totais.geometrias` e `totais.geometriasOmitidas`. Para o Operador a geometria sigilosa **não aparece** (nem nome, nem data, nem id): só a contagem. `GET /requerimentos/{id}/geometrias/arquivo` entrega um ZIP (`java.util.zip` do JDK) com as geometrias **vigentes** acessíveis. A montagem é **tudo ou nada** (coerente com a RN09): todos os arquivos são obtidos antes, com o mesmo cliente do dossiê, e qualquer falha impede o ZIP (502, ou 504 se todas por tempo limite, `GEOMETRIAS_INCOMPLETAS` com a lista de nome e motivo). Limites `GEOMETRIAS_MAX_MB` (500) e `GEOMETRIAS_MAX_ARQUIVOS` (200), 413 `GEOMETRIAS_GRANDES_DEMAIS`. Nomes no ZIP seguros e únicos (`NomesNoZip`, no domínio). Sem geometria acessível: 404 com o mesmo texto, haja ou não sigilosas escondidas.
**Motivo:** um ZIP que parece completo e não está é pior que um erro claro; o Operador não pode descobrir que existe geometria sigilosa além da contagem já decidida.
**Consequência:** o módulo `documento` ganha `infrastructure/disco` (porta `EspacoLivreEmDisco`, a mesma checagem do dossiê sem depender do `dossie`) e uma limpeza agendada das pastas temporárias. O ZIP fica em disco até a resposta terminar (pico de espaço ≈ uma vez a soma dos arquivos). Num shapefile, os arquivos auxiliares (`.shx`, `.dbf`, `.prj`) só vão no ZIP se o SIGA os guardar como documentos do requerimento; se guarda só o `.shp` é NÃO CONFIRMADO (R45).

## D62 — Documentos pessoais do SCORP na lista, com falha tolerante e sigilo por padrão (S4, bloco 5)

**Contexto:** a diferença de documentos entre a Vista do SIGA e o SIGA Vistas (P21: 29 contra 35 no `445/2022`, 92 contra 104 num processo de 2022) são os documentos pessoais do SCORP, que a Vista busca por API para o interessado, os representantes legais e os responsáveis técnicos. Base, autenticação e validade da chave do SCORP no `/api/arquivo` não estão confirmadas (P06).
**Decisão:** origem `SCORP` ("Pessoal") na mesma lista e no mesmo dossiê das seis origens, por último na precedência (RN05, RN03). As pessoas vêm de uma consulta nova ao SIGA (`pessoas-do-requerimento.sql`, colunas conferidas na P31); cada pessoa é consultada uma vez, em paralelo limitado, com no máximo 1 nova tentativa. **Falha do SCORP nunca derruba a lista**: vira aviso (`SCORP_INDISPONIVEL` ou `SCORP_PARCIAL`) na resposta. `id` estável que não expõe a chave (SHA-256 de `idCorporativo|chave`, 16 hexadecimais). O SCORP não informa sigilo: **por padrão segue a Vista do SIGA (não sigiloso, R25)**, com a chave `SCORP_SIGILO_PADRAO` (`true` trata todos como sigilosos), pendente de confirmação com a SEMA (P30). Autenticação igual à da API de arquivos, com variáveis próprias opcionais. Sem `SCORP_API_URL`, nada é chamado e não há aviso.
**Motivo:** fechar a diferença com a Vista sem tornar o SCORP, ainda não confirmado, um ponto único de falha da listagem.
**Consequência:** cada listagem (e cada abertura do modal ou pedido de dossiê, que relêem a lista no servidor) chama o SCORP uma vez por pessoa do requerimento (R47). Se a chave do SCORP não valer no `/api/arquivo`, os documentos pessoais aparecem, mas falham ao obter. Com o SCORP fora do ar na hora de gerar, um documento pessoal escolhido é recusado com 400 e o motivo.

## D63 — "Dossiês gerados" por perfil, com histórico em memória (S4, bloco 7)

**Contexto:** a S4 traz a tela "Dossiês gerados". Os pedidos já ficam em memória (D09); a limpeza esquecia o registro 60 minutos depois do vencimento.
**Decisão:** `GET /dossies` lista os pedidos que a política de acesso libera (`podeAcessarPedido`, a mesma do `GET /dossies/{id}`): o **Operador vê só os seus**, e só enquanto for o interessado gravado no pedido (o vínculo não é relido no SIGA a cada listagem, R44); **Gestor e Administrador veem todos**, com `somenteMeus=true` para os próprios. `geradoPor` só para quem vê pedidos de outros (o campo não existe para o Operador). Nenhum dado de documento na lista. Encerrados ficam **`DOSSIE_HISTORICO_HORAS` (24) depois do vencimento**, sem arquivo (a limpeza passou a usar esse prazo, não mais 60 min); a lista já os esconde antes de a limpeza passar. Situação `BAIXADO` separada de `EXPIRADO` por uma marca no pedido (o andamento continua `EXPIRADO`, sem quebrar a gaveta). O "na fila" do prompt é o `AGUARDANDO` de sempre e não existe "falhou" terminal (D48). Ações pelos endpoints existentes; nenhum endpoint novo de arquivo.
**Motivo:** um vocabulário de situação só para o andamento e a lista; nada da lista revela pedido, documento ou nome de outro usuário ao Operador.
**Consequência:** o histórico some num reinício (a tela diz isso); pedidos encerrados ocupam memória por até 24 h (só metadados: o arquivo já foi apagado). O registro permanente é a auditoria da S5.

## D64 — Front da S4: menu pelas permissões, sem cadeado, e a gaveta reaproveitada em "Dossiês gerados" (S4, blocos 2, 4, 6 e 8)

**Contexto:** até a S3, o menu escondia itens pelo **nome do perfil**, e o protótipo navegável mostrava o item "Acessos" com cadeado só para demonstração (RN08). A S4 traz telas novas que repetem ações da gaveta de geração (pré-visualizar, baixar, decidir, cancelar).
**Decisão:**

- **Menu e guarda de rota pelas `permissoes` de `/auth/me`:** `verAuditoriaAcessos` e `acessarConfiguracoes`, não o nome do perfil. A RN08 vale desde a S4: item sem permissão não é renderizado, sem cadeado nem no protótipo. A guarda decide com as permissões já carregadas, sem chamada ao servidor (aviso e volta à Consulta), e o back-end valida de novo (D60).
- **Textos de acesso num só lugar:** `mensagens-erro.ts`, nunca o `detail` técnico.
- **Avisos da lista com texto da tela:** os avisos do SCORP usam texto próprio por código, e a `mensagem` da API só vale para código desconhecido.
- **Geometrias e documentos pessoais na mesma resposta da lista:** sem chamada extra. As geometrias numa seção própria, sem seleção; os pessoais com uma opção única "Pessoal" no filtro.
- **Gaveta no modo "pedido existente" para "Decidir" em "Dossiês gerados":** a lógica de decisão continua num lugar só. Pré-visualizar e Baixar ficam na linha, com as mesmas regras da gaveta (Blob com o token no cabeçalho; ticket para dossiê grande).
- **Atualização automática da lista a cada 30 s:** para fora da tela e com a aba oculta.

**Motivo:** a permissão é o contrato que o back-end já publica e muda junto com ele; um perfil futuro não exige mexer no menu. Reaproveitar a gaveta evita duas implementações da decisão da RN09.
**Consequência:** o aviso do Operador na Consulta ocupa um espaço reservado em todos os perfis, para a altura do quadro do topo não mudar (cerca de 42 px vazios para Gestor e Administrador). O filtro por situação de "Dossiês gerados" age só na página carregada, porque o `GET /dossies` não filtra situação.

## D65 — Auditoria em memória, somente inclusão, com retrato limpo (S5, bloco 2)

**Contexto:** a S5 traz a rastreabilidade ainda sem schema próprio (D09). O dossiê é usado em processos jurídicos: a auditoria não pode ser alterada pela aplicação, não pode vazar sigilo nem credencial e não pode derrubar a operação.
**Decisão:** `EventoDeAuditoria` imutável, numerado e datado pelo repositório (fuso de Cuiabá), com retrato do usuário, dos números e dos documentos. `RepositorioDeEventos` só inclui e lê; o descarte fica à parte, em `RetencaoDeEventos` (retenção por grupo) e no teto `AUDITORIA_MAX_EVENTOS`. `RegistrarEvento` nunca lança, e montar um `NovoEvento` também não: a validação roda dentro de `registrar`. Texto longo é cortado, não recusado. Todo texto passa pela limpeza de `TextoDoEvento`. O grupo decorre do tipo. Incluído o tipo `PERFIL` de docs/11 (troca de perfil simulado, bloco 6).
**Motivo:** um evento perdido por um título comprido seria pior do que um título cortado. A limpeza é a segunda barreira: os módulos já não mandam chave nem token, mas o motivo de falha e os títulos são texto livre. Os dígitos do interessado ficam em `VinculoDoInteressado`, sem leitura pública, porque o filtro do Operador precisa deles e nenhum DTO, CSV ou log pode recebê-los por acidente.
**Consequência:** tudo some ao reiniciar (R50). Um título legítimo que pareça dado sensível (por exemplo, com 11 dígitos seguidos) aparece com `[removido]` na auditoria; o documento em si não muda. Quem lê os dígitos do vínculo é só a regra passada a `atende`.

## D66 — Eventos de documentos sem mudar respostas, com retrato guardado para a geração (S5, bloco 3)

**Contexto:** o bloco 3 instrumenta fluxos sensíveis. A geração roda sem usuário na requisição, e o download por ticket pode vir sem sessão.
**Decisão:** `AutoriaDoPedido` guarda no pedido o retrato do usuário, os números, a origem e a correlação; `DOSSIE` e `FALHA` usam esse retrato. `ItemDoPedido` guarda nome do arquivo e sigilo da lista no momento do pedido. `TicketDeDownload` guarda o retrato de quem o pediu, e `DOWNLOAD` sai em nome de quem baixou, que pode não ser quem gerou (Gestor baixando pedido de Operador). `ContextoDoEvento` lê usuário, origem e correlação sem lançar. `AbrirRequerimento` registra `CONSULTA` à parte de `ObterRequerimento`, que a criação do dossiê também usa. `DOWNLOAD` e `GEOMETRIAS` saem só com o arquivo lido por inteiro. Interrupção causada pelo cancelamento não gera `FALHA`.
**Motivo:** o contexto da requisição não existe na thread de geração; e registrar `DOWNLOAD` antes do fim contaria download interrompido, que nem apaga o dossiê.
**Consequência:** decisão de "tentar novamente" tomada por outro usuário gera `FALHA` e `DOSSIE` em nome de quem criou o pedido. `auditoria` passa a depender de `acesso` (usuário atual e, no bloco 4, a política); por isso `acesso` não pode chamar `RegistrarEvento` direto (ciclo e regra do `ArchitectureTest`). O bloco 6 precisa de uma porta declarada em `acesso` e implementada em `auditoria` para `LOGIN` e `PERFIL`.

## D67 — Consulta da auditoria: mascarar antes de filtrar (S5, bloco 4)

**Contexto:** o Operador vê eventos de outros usuários sobre os processos dele, e esses eventos podem citar documento sigiloso. A busca por texto é o caminho mais fácil para descobrir o nome de um documento por tentativa.
**Decisão:** `ConsultarAuditoria` aplica, nesta ordem e nos três caminhos (lista, detalhe e CSV): permissão do grupo, vínculo do interessado, mascaramento e só então os filtros. A regra do vínculo e o sigilo vêm da `PoliticaDeAcesso` (`regraDoInteressadoDoUsuarioAtual`, `exigirAuditoriaDeAcessos`). Evento que o usuário não vê é 404 no detalhe. Em evento sigiloso, `detalhe` e `arquivo` saem por inteiro para quem não acessa sigilosos, salvo em `DOSSIE`, `DOWNLOAD` e `GEOMETRIAS`, cujo texto é montado pelo serviço e não cita documento. A marca `sigiloso` e os indicadores continuam visíveis ao Operador.
**Motivo:** filtrar sobre o evento já mascarado torna impossível a correspondência com o que o usuário não pode ler, sem regra especial na busca. Trocar só o título dentro do motivo de falha seria frágil: o motivo é texto livre.
**Consequência:** o Operador sabe que houve evento sigiloso no processo dele, como já sabe na lista de documentos, mas não qual. Cada consulta percorre os eventos do período (até o teto de 50.000); para o CSV são duas passagens, uma para saber se trunca antes de escrever o cabeçalho. A versão definitiva troca isso por consulta no banco.

## D68 — Usuários e perfis: regras em `acesso`, tela em `configuracao` (S5, bloco 7)

**Contexto:** a administração precisa decidir por perfil (último Administrador, Operador exige CPF/CNPJ), mas só `acesso` pode conhecer `Perfil` (D60, `ArchitectureTest`), e `acesso` não pode depender de `auditoria`.
**Decisão:** `acesso.application.AdministrarUsuarios` guarda as regras do cadastro e devolve `UsuarioAdministrado`, com CPF/CNPJ só mascarado. `configuracao.application.GerirUsuarios` confere a permissão na política, chama esse caso de uso e registra o evento `USUARIO`. A escrita fica em `CadastroDeUsuarios`, interna a `acesso`. O id da rota é o login, que é único e não muda. Inclusão e alteração são serializadas. Um PATCH gera um evento, com uma linha por campo alterado; sem mudança real, nenhum.
**Motivo:** mantém a regra da S4 sem exceção e não cria um segundo lugar que conheça perfis. As sessões abertas não precisaram de mudança: `ObterUsuarioAtual` relê o usuário do cadastro a cada requisição.
**Consequência:** mudar um usuário sem documento para Operador é 400, porque o PATCH só recebe perfil e situação (a inclusão exige CPF/CNPJ). Usuários incluídos usam a senha única do protótipo (R54).

## D69 — Parâmetros em tempo de execução por sobreposição e retrato do pedido (S5, bloco 8)

**Contexto:** o Administrador altera parâmetros sem reiniciar, mas um pedido em andamento não pode mudar de comportamento no meio, e o contrato das portas não pode mudar. Os valores são lidos em muitos lugares, inclusive nos adapters de conversão e de download.
**Decisão:** `CatalogoDeParametros` lista tudo o que é editável; o que não está lá é recusado. As implementações das portas em `config` leem `ParametrosEmVigor`, passando o valor do ambiente como padrão. As sobreposições são trocadas de uma vez (mapa imutável), e salvar valida tudo antes. Na criação, o pedido guarda um retrato dos parâmetros de classe "próxima geração"; a geração e a continuação rodam dentro de `RetratoDeParametros.comRetrato`, em que a thread lê do retrato. Parâmetro "exige reinício" fica só como pendência: as portas continuam com o valor do ambiente. Origem de documento passou a ser lida a cada consulta (`OrigensLigadas`).
**Motivo:** o retrato na thread evita levar cada parâmetro por todas as chamadas até os adapters, e cobre também o que for lido no futuro. As conversões rodam na thread da geração.
**Consequência:** se um adapter passar a ler parâmetro em outra thread, ele escapa do retrato. "Exige reinício" só vale de fato pela variável de ambiente (R56). O catálogo repete o nome da propriedade e o padrão de `application.yml`.

## D70 — Acesso conferido na entrega, com o perfil vigente (S5, bloco 11)

**Decisão:** o ticket de download confere o usuário e a política de acesso também ao ser usado, com o perfil de agora, e não só ao ser emitido. A política passa a considerar o sigilo do pedido para o próprio criador: quem o gerou com documento sigiloso e foi rebaixado a Operador perde o pedido, ainda que seja o interessado.

**Motivo:** a alteração de usuário vale na requisição seguinte (RF26); ticket e autoria não podem ser uma exceção a essa regra. A recusa do ticket continua sem dizer o motivo (403 `TICKET_INVALIDO`) e o pedido perdido responde 404, como qualquer pedido que o usuário não acessa.

**Consequência:** o módulo `acesso` publica `UsuariosAtivos`, a leitura do usuário vigente por login. A geração já em andamento não é interrompida pela mudança de perfil: o que é bloqueado é a entrega. Um download já em andamento na hora da alteração também termina.

## D71 — Geração em segundo plano: o serviço é dono do pedido, a gaveta é só a janela (S5, bloco 1)

**Contexto:** na homologação da S4, clicar fora da gaveta durante a geração fechava a gaveta e parava o acompanhamento; com o `POST` ainda em envio, a requisição era cancelada e a geração nem nascia.
**Decisão:** `AcompanhamentoDeDossieService` (singleton) é dono do pedido ativo, do `POST`, do polling (mesmo intervalo crescente), da decisão, da pré-visualização e do download. A gaveta, hospedada no shell, só mostra o estado. Fundo, Esc e "Fechar" **minimizam**; só "Cancelar geração" faz `DELETE`. Um indicador na barra do topo mostra o andamento em todas as telas e um aviso com a ação "Abrir" sai ao concluir ou parar à espera de decisão com a gaveta fechada. A tela acompanha **um pedido por vez** (o servidor aceita várias gerações); sair da aplicação ou trocar de usuário encerra o acompanhamento.
**Motivo:** o acompanhamento não pode depender de um componente que o usuário fecha; o indicador fica dentro da barra fixa, então nenhum quadro de tela muda de altura.
**Consequência:** o usuário que sai com um pedido ativo perde só o acompanhamento, não o pedido (ele continua em "Dossiês gerados" até expirar). `documentoRemovido$` e `gavetaFechada$` ligam a gaveta às telas de documentos e de "Dossiês gerados".

## D72 — Front da S5: uma tela de auditoria, estado de parâmetros compartilhado e CSV por Blob (S5, blocos 5, 6, 9 e 10)

**Decisão:**
- **Auditoria:** um só `AuditoriaComponent` atende `documentos` e `acessos` (`data.grupo` da rota). O que cada perfil vê é decidido pelo servidor (D67); a tela só esconde o que o perfil não pode usar ("somente sigilosos" sem `verSigilosos`). O CSV vem como `Blob` com o token no cabeçalho e o nome do `Content-Disposition`, com aviso quando `X-Vistas-Truncado`. A proteção contra injeção de fórmula é do servidor (D67); a tela não reescreve o arquivo.
- **Configurações:** abas navegáveis por teclado; usuários e perfis gravam **na hora**, com uma confirmação curta na linha e a mensagem do servidor nas recusas de proteção (último Administrador, próprio usuário, login repetido; D68). Os parâmetros ficam **pendentes** num `ParametrosStore` compartilhado pelas abas e saem num **único `PUT`** pela barra fixa, o que casa com o evento `CONFIG` único (D69). Sair com pendência pede confirmação.
- **Credenciais:** nenhuma é exibida nem editável (RNF09); o teste de conexão mostra só estado, tempo e a mensagem do servidor.
- **Carga sob demanda:** `AuditoriaComponent` e `ConfiguracoesComponent` usam `loadComponent`, porque o bundle inicial passou do orçamento de 500 kB.
**Motivo:** reaproveitar a tela evita duas implementações das mesmas regras de mascaramento; as pendências em um lugar só permitem a barra única e o evento único.
**Consequência:** a tela de Acessos e a de Documentos mudam juntas. O CPF/CNPJ de usuário nunca volta completo da API, então a tela de usuários não consegue editá-lo (só na inclusão).

## D73 — Renomeação do projeto para Rastro, em duas fases (R1)

**Contexto:** o projeto nasceu como "SIGA Vistas" (dossiê da Vista do Processo) e passa a ser um complemento de consulta ao SIGA Licenciamento: onde o processo está, por onde passou, histórico financeiro e, mais adiante, painel do Gestor. A Vista do Processo continua como um dos módulos.
**Decisão:** o nome oficial é **Rastro** ("Rastro, complemento de consulta ao SIGA Licenciamento (protótipo)"). Rastro é o produto; **"Vistas" é o módulo** do dossiê (os próximos serão "Tramitação", "Taxas" e "Painel"). Não usar "SIGA Rastro", "Rastro SIGA" nem o brasão da SEMA como marca do Rastro (a capa do dossiê é assunto da S6). A renomeação tem **duas fases**: a Fase 1 (nome visível e documentação, blocos 1 a 4) pode ser feita antes da apresentação; a Fase 2 (pacote Java, artefatos, containers, chaves de armazenamento, CI, blocos 5 a 7) só depois da apresentação e da marca de versão `vistas-1.0`, criada pelo responsável. Rotas `/api`, SQL, schemas `SIGA2_*`, variáveis de ambiente e regras de negócio **não mudam**. Propriedades `vistas.*` e chaves de armazenamento do navegador passam a ter a nova forma **com a antiga como alias** (a antiga vence; aviso de depreciação sem valor). O repositório e a pasta `siga-vistas` são renomeados pelo responsável, à mão. Documentos históricos (prompts, progresso de sprints passadas, roteiros homologados) não são reescritos.
**Motivo:** o nome visível passa a refletir o escopo ampliado sem arriscar a apresentação; a parte técnica tem risco real (importações, volumes, sessões) e fica para depois.
**Consequência:** o inventário `docs/rastro/inventario-renomeacao.md` é a base da sprint; o nome antigo fica só como histórico e nos aliases documentados.

## D74 — Renomeação técnica do back-end e alias das propriedades `vistas.*` (R1, bloco 5)

**Contexto:** a Fase 2 da R1 (D73) renomeia os nomes técnicos do back-end. A configuração em uso (arquivos externos, argumentos, testes) não pode quebrar.
**Decisão:**
- Pacote raiz `br.gov.mt.sema.vistas` → `br.gov.mt.sema.rastro` (arquivos movidos, estrutura interna igual); classe principal `RastroApplication`; exceção base `RastroException`; artefato, `name`, `finalName` e JAR `rastro-api`; `spring.application.name` `rastro-api` (aparece no log); prefixo dos temporários do conversor `rastro-conversor-`.
- Propriedades `vistas.*` → `rastro.*`. O prefixo antigo continua aceito por `config.AliasDasPropriedadesAntigas` (`EnvironmentPostProcessor` registrado em `META-INF/spring.factories`): `vistas.x` responde por `rastro.x`; **com as duas, vale a antiga**; cada nome antigo em uso gera um aviso de depreciação no log, **só com os nomes**. O alias ignora as variáveis de ambiente: `VISTAS_DEMO_SENHA` e `VISTAS_DOSSIE_DIRETORIO` continuam entrando pelos placeholders do `application.yml` e não geram aviso falso.
- **Não mudam:** variáveis de ambiente (`VISTAS_*`, D73), rotas `/api`, SQL, cabeçalhos HTTP `X-Vistas-Truncado` e `X-Vistas-Convertido-De` (contrato com o front; decisão do responsável), o usuário `vistas` e o diretório `/var/tmp/vistas` do container (ligados ao volume `dossies-tmp`, bloco 6), o nome interno `VistasAba` das abas da planilha convertida (mudaria a saída da conversão) e o formato e os níveis do log.
**Motivo:** renomear sem mudar comportamento e sem exigir que o responsável altere configuração já em uso no mesmo dia.
**Consequência:** o alias é temporário. A remoção (apagar a classe e a linha do `spring.factories`) fica para depois de a configuração externa usar só `rastro.*`; até lá, o aviso no log mostra o que falta trocar.

## D75 — Renomeação técnica do front e da infraestrutura, sem perder sessão nem volume (R1, bloco 6)

**Contexto:** a Fase 2 da R1 (D73) também troca os nomes técnicos do front, do Compose e do CI. Não pode deslogar ninguém, apagar preferências nem perder o volume do responsável.
**Decisão:**
- **Navegador:** `vistas.token` (sessionStorage) → `rastro.token` e `vistas.recentes.<login>` (localStorage) → `rastro.recentes.<login>`. Por esta versão a chave antiga é **reserva**: sem a nova, o valor da antiga é copiado para a nova e a antiga é apagada; com as duas, vale a nova (`core/armazenamento.ts`). O logout apaga as duas.
- **Compose:** projeto `siga-vistas` → `rastro` (containers, rede e imagens locais passam a `rastro-*`). Os serviços `api` e `front` e a rede `interna` já não tinham o nome antigo. O **volume mantém o nome** `siga-vistas_dossies-tmp` (`name:` fixo em `compose.local.yaml`); a renomeação manual está em [13](13-padroes-tecnicos.md), "Renomeação do Rastro".
- **CI:** só as tags locais das imagens do job `docker` mudam (`rastro-api:ci`, `rastro-front:ci`). Os workflows não têm segredo nem variável próprios; nomes de workflow, de job e de artefato não tinham o nome antigo.
- **Não mudam:** `name` do `package.json` e projeto do `angular.json` (`front`, sem o nome antigo; `outputPath` e o `Dockerfile` do front ficam intactos), `nginx.conf` (upstream `api`, sem nome antigo), rotas `/api`, os cabeçalhos `X-Vistas-*` (D74), o usuário `vistas`, o diretório `/var/tmp/vistas` e o caminho de montagem `/run/secrets-vistas` (citado no `.env.local` do responsável), e a senha de demonstração `vistas-demo` (é um valor, igual ao padrão do back-end).
**Motivo:** trocar o nome sem efeito para quem usa: sessão, recentes e temporários continuam.
**Consequência:** o fallback das chaves e o nome fixo do volume são temporários (R62). Renomear o repositório, a pasta, o remoto e os pacotes do GHCR fica com o responsável ([13](13-padroes-tecnicos.md), lista manual).

## D76 — Nome do arquivo na coluna do nome; título é o tipo (S6, bloco 1)

**Contexto:** a homologação da S5 comparou com a Vista do SIGA: o Rastro mostrava o `TITULO` (tipo/descrição, ex.: "Teste de bombeamento.") onde o SIGA mostra o nome do arquivo, e o cabeçalho da planilha convertida repetia o tipo. O mapeamento do SQL (`TITULO`, `NOME_ARQUIVO`) já estava certo; o erro era de exibição.
**Decisão:** o nome exibido é o nome do arquivo (`Documento.nome()`), com o título como linha secundária; o cabeçalho da planilha é o nome do arquivo sem extensão. O DTO e o formato dos eventos de auditoria não mudam.
**Motivo:** reproduzir a Vista do SIGA; o sumário, o modal e a planilha falam do mesmo nome.
**Consequência:** `ItemDoPedido.titulo()` passou a guardar o nome exibido; `descricao()` guarda o título/tipo (retrato da auditoria) e `tituloDeCabecalho()` o texto da planilha.

## D77 — Disco cheio: FALHA clara e nunca sucesso falso (S6, bloco 3)

**Contexto:** na homologação da S5, com o disco do computador do responsável cheio (o Docker grava nele), baixar um dossiê mostrou mensagem de sucesso e só "gerar novamente"/"descartar"; depois de liberar espaço, funcionou. Causas encontradas: (1) no servidor, falta de espaço virava falha de **documento** (`PENDENTE_DECISAO`, que nenhuma decisão resolve) ou "falha inesperada", e o adapter da API de arquivos descartava a causa; o PDF final não era forçado ao disco, então no Docker a falta de espaço podia aparecer só depois, com o pedido `CONCLUIDO`; o download não conferia o arquivo (sumido dava 500 genérico) e contava como completo quem só tinha **lido** todos os bytes; (2) no front, a gaveta e "Dossiês gerados" marcavam "Arquivo baixado" (nota verde) assim que a resposta chegava, sem conferir o corpo, e a página não tem como saber se o navegador conseguiu gravar; como o servidor apagava o arquivo no fim do envio, não havia como tentar de novo.
**Decisão:**
- Situação nova **`FALHA`** do pedido, só para o que nenhuma decisão resolve: sem espaço (`ARMAZENAMENTO_INSUFICIENTE`) e PDF final perdido depois de concluído (`DOSSIE_ARQUIVO_AUSENTE` 410, `DOSSIE_ARQUIVO_INVALIDO` 500). RN09 continua valendo para as falhas de documento.
- Falta de espaço reconhecida pela cadeia de exceções (mensagens do Linux e do Windows) e, para o LibreOffice, por menos de 16 MB livres depois de uma falha; o diretório do pedido é apagado e sai um único evento `FALHA` com o código, sem caminho.
- `CONCLUIDO` só depois de `fsync` do PDF final e com tamanho maior que zero. `POST /dossies` com 507 sem criar pedido abaixo de `DOSSIE_ESPACO_MINIMO_MB`, cujo padrão passa de 2048 para **500** (prompt da S6; `0` desliga).
- Download: conferência do arquivo antes de entregar; o aviso de download completo só depois de o servidor ver o fim do arquivo. **O arquivo continua sendo apagado assim que o download termina (RNF05 sem mudança).** Uma janela de 5 minutos para baixar de novo chegou a ser implementada e foi **recusada pelo responsável** ("apagar na hora").
- Front: confere o PDF recebido (tamanho e `%PDF-`) antes de entregar ao navegador; mostra o erro real do `ProblemDetail` e as ações **Tentar novamente** (só quando o download falhou antes de chegar inteiro), **Gerar novamente** e **Descartar**, cada uma com explicação; depois do download, nota neutra ("entregue ao navegador... o arquivo foi descartado do servidor: se o navegador não conseguiu salvar, libere espaço e gere o dossiê novamente"), nunca "sucesso", com **Gerar novamente**.
**Motivo:** nunca dizer que deu certo sem um arquivo inteiro, e dar ao usuário a ação certa (liberar espaço e gerar de novo).
**Consequência:** se o navegador não conseguir gravar um PDF que chegou inteiro (disco cheio no computador do usuário), a página não tem como saber e o arquivo já saiu do servidor: o usuário gera de novo (R63). A simulação de disco cheio nos testes é feita pela porta `EspacoEmDisco` e por exceções injetadas; nenhum teste enche disco.

## D78 — Teste de conexão: alcançável não é funcional, e o mesmo cliente do fluxo real (S6, bloco 4)

**Contexto:** na homologação da S5 o teste da API de arquivos (HTTP 503) e o do SCORP (HTTP 400), além de tempos de cerca de 5 s, apontavam falha, mas o consumo real funcionava (falso alarme). Causa no código: cliente próprio do teste com 5 s fixos de conexão e leitura (o fluxo real usa 10 s + 120 s de inatividade nos arquivos e 15 s no SCORP), sem repetir o 503 (o download real repete), e só 2xx ou 404 contavam como resposta; URL e autenticação montadas em código duplicado.
**Decisão:** a montagem do acesso (base, caminho, autenticação, cliente) fica numa classe só por integração (`AcessoAApiDeArquivos`, `AcessoAoScorp`), usada pelo fluxo real e pelo teste, que muda só o recurso. O resultado tem quatro classificações: **OK**, **Alcançável com ressalva** (4xx esperado), **Falha de autenticação** (401/403, token vencido) e **Falha** (DNS, recusa, TLS, tempo, 5xx persistente); 503 isolado ganha uma nova tentativa. Tempo limite em `CONEXAO_TESTE_TIMEOUT_SEGUNDOS` (15), mostrado na tela. Cada teste vira evento `CONEXAO` (tipo novo, grupo Acessos) só com conexão e resultado.
**Motivo:** um serviço que responde está no ar; "falha de rede" só quando não há resposta. Se a URL do fluxo real mudar, o teste muda junto (teste de regressão compara as duas requisições).
**Consequência:** `sucesso` passa a ser verdadeiro também na ressalva; o teste pode demorar até ~35 s com o serviço mudo (R64). Se a API de arquivos real responder 503 persistente à chave fictícia, o teste ainda mostra "Falha" (NÃO CONFIRMADO, R55).

## D79 — Guardas do Claude Code: as regras permanentes viram hooks (S6, bloco 6)

**Contexto:** as regras do `CLAUDE.md` (só `git diff`, nunca abrir o `.env.local`, nunca `down -v`, nenhum dado pessoal real) dependiam só de a sessão segui-las. Na S1.1 uma verificação chegou a ler dados reais (D34).
**Decisão:** três hooks versionados em `.claude/settings.json`, em Node sem dependências (`.claude/hooks/`): `guarda-bash` (`PreToolUse`, `Bash|PowerShell`), `guarda-arquivos` (`PreToolUse`, ferramentas de arquivo) e `varrer-dados` (`PostToolUse`, gravações). Bloqueio só com código 2 e só no que casa com a regra; erro interno sai com código 1 (não bloqueante) e um aviso curto. Reforço em `permissions.deny` onde o formato permite sem pegar os `*.example`. Testes com `node --test` no job `mudancas` do CI. Além do pedido, a guarda de ambiente também barra `docker inspect` sem `--format` e `docker compose config` com valores (os dois imprimem as variáveis do `.env.local`), e a de git barra os comandos só de leitura (`git status`, `git log`), como diz a regra.
**Motivo:** transformar regra em proteção automática, verificável por teste e demonstrável.
**Consequência:** a varredura aceita, além do CNPJ de exemplo, três números didáticos válidos que os testes já usavam (lista `FICTICIOS`, docs/claude-code/hooks.md); citar o nome `.env.local` num comando, mesmo para excluí-lo, é bloqueado. No Windows sem Git Bash os hooks ficam desligados (erro não bloqueante): conferir na homologação.

## D80 — Servidor MCP do Rastro: só local, só demonstração, só leitura (S6, bloco 7)

**Contexto:** a S6 pede um servidor MCP para mostrar a integração do Claude Code com o Rastro sem tocar em nada real. O serviço não tinha como declarar que está em demonstração, e o ambiente de verificação não tem dados (sem SIGA).
**Decisão:** servidor em Node (`tools/mcp-rastro/`, SDK oficial com versão fixa) com três ferramentas `GET` e um recurso, registrado em `.mcp.json`. O serviço ganhou `GET /api/estado` (autenticado) com `modo`: `demonstracao` exige `RASTRO_MODO=demonstracao` **e** nenhum sistema externo configurado, para que a variável sozinha não baste. O servidor recusa host não local, redirecionamento e operar fora da demonstração; mascara a saída por padrão (CPF/CNPJ válidos ou não, e-mail, token, cookie, chave). Para demonstrar com dados fictícios, um **Rastro simulado** local (`rastro-simulado.mjs`, só em `127.0.0.1`), o mesmo dos testes.
**Motivo:** a superfície nova de acesso não pode virar caminho para dado real; a recusa fica no código e é testada.
**Consequência:** no ambiente de verificação as consultas respondem 503 (sem SIGA); dados só pelo Rastro simulado. Um Rastro de demonstração com SIGA fictício fica pendente (R66).

## D81 — Guardas: na dúvida, bloqueia; defesa em camadas (S6.1)

**Contexto:** a verificação da S6 (`docs/progresso/verificacao-s6.md`) achou que a `guarda-bash` só barrava o arquivo de segredo citado pelo nome: uma busca recursiva da raiz imprimia o `.env.local`, e `node -e`, `python -c` ou variável solta no PowerShell despejavam variáveis. Exigência do responsável: nada pode vazar.
**Decisão:** (1) **na dúvida, bloqueia**: comando que pode ler ou imprimir segredo e que a análise estática não prova inofensivo é bloqueado, com a regra e o jeito permitido; falso bloqueio é preferível a vazamento, e o custo é medido e documentado (docs/claude-code/hooks.md). (2) **Defesa em camadas, nenhuma sozinha:** guardas (`guarda-bash`, `guarda-arquivos`), `permissions.deny`, isolamento do sistema se a documentação confirmar que vale para Windows com Git Bash e PowerShell, e o segredo fora da vista do agente (bloco 2). (3) **Prova por corpus** com um segredo-isca fictício em pasta temporária de teste, rodando no CI (bloco 3). Nenhum segredo real é usado em teste.
**Motivo:** uma lista de padrões nunca prova "nenhum vazamento"; camadas independentes e um corpus grande reduzem o que sobra e deixam o limite à vista.
**Consequência (bloco 1):** a `guarda-bash` foi reescrita (palavras com marca de expansão e curinga, heredoc e here-string como código, busca recursiva conferida no disco, interpretador inline, executores, cano do PowerShell, credenciais do usuário); 124 rotas sondadas barradas, 66 comandos do dia a dia sem falso bloqueio, 260 casos bloqueados no teste. Nenhuma regra antiga foi afrouxada. O que continua limite está no R65.
**Consequência (bloco 2):** a `guarda-arquivos` normaliza o caminho (inclusive destino de link simbólico) e confere Grep/Glob sobre pasta no disco (Grep respeita o `.gitignore`, Glob não, provado com iscas); o `permissions.deny` cobre `Read`/`Edit` de `.env*` (com negação dos exemplos), `.secrets`, `.rastro` e credenciais do usuário; o isolamento do sistema **não está disponível** no Windows nativo (só macOS, Linux e WSL2) e ficou documentado, sem configuração; o segredo fora da pasta do projeto (`~/.rastro/env.local`) é passo manual do responsável em `docs/claude-code/segredos.md`.
**Consequência (bloco 3):** `vazamento.test.mjs` é um corpus **independente** dos testes de cada guarda (546 tentativas com uma isca fictícia, 40 comandos do dia a dia, e o hook rodado em processo separado), no CI; a tentativa que passar derruba o teste com o nome dela. Na primeira execução ele achou uma rota que os 260 casos do bloco 1 não tinham, `echo ${VAR_SENSIVEL}` sem aspas, e a `guarda-bash` foi apertada (lê `${...}` como uma palavra só). A correção é só endurecimento e entrou no mesmo commit do corpus, embora o bloco 3 seja de testes e documentação: a regra "nada pode vazar" prevaleceu sobre a divisão de escopo, e fica registrado para o responsável conferir no `git diff`.

## D82 — Tramitação: posição pelo Camunda, catálogo de etapas em arquivo e o Rastro diz que não sabe (S7, bloco 1)

**Contexto:** a S7 abre o módulo `tramitacao`: mostrar em que etapa o requerimento está, desde quando e com quem, sem exigir conhecer o Camunda. A situação do Licenciamento (`SITUACAO_PROCESSO`) é grosseira e diverge do fluxo em casos conhecidos, e o serviço é usado em processos jurídicos (dado cruzado errado é pior do que dado ausente). Regras técnicas completas em [15](15-tramitacao.md).
**Decisão:** (1) **fonte da posição = Camunda**; a situação só confirma e alimenta o plano B (S8), e divergências viram selos de aviso, nunca correção silenciosa. (2) **Catálogo de etapas em YAML versionado** (`api/src/main/resources/rastro/etapas/`, um arquivo por modelo), no vocabulário do usuário, **validado na subida** (id técnico em no máximo um lugar, ordem única por ramo, final com rótulo, modelo sem etapa é erro; campo desconhecido também); id técnico desconhecido **não** derruba a consulta: vira "Etapa não mapeada", com o id só para o Administrador; chave desconhecida (Trifásico, LAS) vira "Fluxo ainda não suportado pelo Rastro". Ids ignorados (suporte, ruído, ramo inativo) ficam em lista própria e nunca viram etapa. Etapa com condição de percurso não atendida (isenção depois de pagamento) também vira "não mapeada", em vez de um rótulo inventado. (3) Etapa atual pode ser mais de uma: a **principal** é a de menor ordem entre as ativas do ramo principal, com "+N em paralelo"; a complementação à parte. (4) Responsável sem CPF (servidor, área, interessado ou sistema), com o nome conforme `RASTRO_MOSTRAR_NOME_RESPONSAVEL` (ligado no protótipo; regra definitiva com a SEMA, P32). (5) "Verificação da revisão" é concluído/arquivado. (6) Leitura direta por `PROC_INST_ID_`, cache curto em memória (`RASTRO_POSICAO_CACHE_SEGUNDOS`, 30; 0 desliga), sem tabela de apoio. (7) Camunda fora do ar responde `INDISPONIVEL` sem derrubar o detalhe. (8) Nada gravado no Oracle.
**Motivo:** o vocabulário e os ids mudam com o BPMN e com a área, não com o código; validar na subida evita que um erro de digitação contamine todas as posições; dizer "não sei" é mais seguro que adivinhar.
**Consequência (bloco 1):** domínio em `tramitacao.domain` (`Etapa`, `ModeloDeFluxo`, `ValidadorDeModelo`, `ResolucaoDeEtapa`, porta `CatalogoDeEtapas`) sem YAML nem Spring (ArchUnit); leitura em `tramitacao.infrastructure.catalogo` com o construtor seguro do SnakeYAML (já vem com o Spring Boot; nenhuma dependência nova). `tramitacao` entrou no `ArchitectureTest` e não depende de `documento` nem de `dossie`. `docs/referencias/bpmn/` não existe: os ids não foram conferidos contra o BPMN (R68). "Report Kafka" e as repetições por erro ainda não têm id técnico (NÃO CONFIRMADO, docs/15).
**Consequência (bloco 3):** a leitura é dividida em duas partes: `integration.siga.CamundaPosicaoJdbc` (único componente com o SQL da posição, `sql/siga/tramitacao/`) só traz **fatos crus** em no máximo 7 consultas fixas por requerimento, todas por `PROC_INST_ID_` ou chave, com limite de linhas e tempo limite próprio (`RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS`); e `tramitacao.domain.MontadorDaPosicao`, função pura, resolve etapa, principal, paralelos, multi-instância, suporte e finais pelo catálogo. Motivo: as armadilhas do Camunda ficam testáveis sem banco, e o SQL não carrega regra de negócio. O caso de uso confere o acesso pelo contrato de `consulta` antes de tudo e transforma qualquer falha posterior em `INDISPONIVEL`. Escolhas do bloco: o `TO_CHAR` fica do lado do valor nas comparações com `BUSINESS_KEY_` (preserva o índice; igualdade não gera `ORA-12704`); filhas pelo `SUPER_EXEC_` (só as ativas); espera sem assinatura de evento e condição de percurso não atendida viram "Etapa não mapeada"; o "y" da multi-instância só existe com o corpo `#multiInstanceBody` aberto; o CPF de `ASSIGNEE_` só circula como `IdentificadorDoResponsavel` (texto protegido). Hipóteses sobre o Camunda real em P35; limites de linhas em R70.
**Consequência (bloco 4):** com quem está cada ponto é decidido por `tramitacao.domain.RegraDoResponsavel` (função pura): `ASSIGNEE_` do interessado → "Interessado (cadastrante)"; CPF em `SERVIDOR` → nome, com papel e área; sem correspondência → "Responsável não identificado"; tarefa de grupo → área por `resources/rastro/grupos.yaml` (validado na subida), desconhecido ou `0` → "Equipe responsável"; multi-instância → participantes concluiu/pendente; sem tarefa → o papel do catálogo (a espera de pagamento é do interessado, não "Sistema"). Uma consulta por requerimento (`responsaveis.sql`, teto da leitura passa a 8). Os identificadores (CPF) saem da posição logo depois da resolução, então cache, resposta e log nunca os recebem; o cache (`RASTRO_POSICAO_CACHE_SEGUNDOS`) usa só id + versão do catálogo, é consultado depois da checagem de acesso e guarda a posição sem CPF; nome (`RASTRO_MOSTRAR_NOME_RESPONSAVEL`) e limite de nomes (`RASTRO_MAX_NOMES_POR_EQUIPE`) são aplicados depois do cache, valendo na próxima leitura. Falha na identificação não derruba a posição. Rótulos dos grupos a confirmar (P36).
