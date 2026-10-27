# Integrações externas

Toda dependência de sistema externo vive num pacote `integration.<sistema>`, com o nome do próprio sistema (dois níveis abaixo da raiz; `integration` sozinho não é módulo). O adapter converte o dado externo para um **contrato interno** antes de entregá-lo aos módulos de negócio. Mudança no sistema externo deve mudar só o adapter.

## `integration.siga` — banco do SIGA (somente leitura)

- Único pacote com SQL contra `SIGA2_LICENCIAMENTO`, `SIGA2_CAMUNDA`, `SIGA2_OUTORGA` e `SIGA2_ARRECADACAO` (só a tabela `ATIVIDADE`, D29), e com conhecimento de nomes de tabela/coluna e de enums do SIGA (`SITUACAO_PROCESSO`, `GRUPO_DOCUMENTO`, `TIPO`).
- Usa `NamedParameterJdbcTemplate` no datasource `siga` (somente leitura), configurado dentro do próprio `integration.siga`. **Sem JPA** para o SIGA: o modelo do SIGA não é nosso e mapeá-lo como entidade criaria acoplamento e risco de escrita.
- SQL em arquivos de recurso (`src/main/resources/sql/siga/*.sql`) ou constantes no adapter — nunca espalhado em módulos de negócio.
- Sempre com parâmetros nomeados; nunca concatenação de entrada do usuário.
- Converte para tipos internos: situação do documento, sigilo (RN06 — na dúvida, sigiloso), origem, data cronológica (RN03). Documentos: **uma consulta por origem** em `sql/siga/documentos/`, sem filtrar vigência; origem desligada (`vistas.documentos.origens.<origem>`) não é consultada; as origens são as seis da Vista do SIGA (S2.1, D41); nada de origem nova sem prova de que a Vista a lista.
- A chave do arquivo (Pandora) é lida aqui e entregue apenas ao adapter de arquivos; nunca sai para a API HTTP (RNF09).
- Tarefa atual: lida de `SIGA2_CAMUNDA`, mas o termo "Camunda" não aparece fora deste pacote nem na interface. Uma migração do SIGA para outro motor de fluxo muda só este adapter.
- **Falhas** (`SigaFalhas`): falha de conexão, timeout, `DataAccessResourceFailureException` e erros transitórios viram `SIGA_INDISPONIVEL` (503); coluna ou tabela inexistente e falta de grant (`BadSqlGrammarException`) e demais erros de dados viram erro interno (500). O detalhe técnico vai só para o log, com `correlationId`; nenhuma resposta traz SQL, tabela, URL ou mensagem do driver.
- **Tarefa atual:** falha ao lê-la não derruba o detalhe; o adapter devolve o estado `INDISPONIVEL`.
- **Restrição por interessado (RN07)** é decidida pelo domínio e aplicada no SQL (parâmetro opcional), para que total e paginação já venham filtrados.
- Definitivo: preferir **views** de leitura criadas pelo DBA (`VW_`), concedidas ao usuário de leitura, em vez de grants diretos nas tabelas. Isso isola o serviço de mudanças no modelo do SIGA.

## `integration.licenciamento` — API de arquivos

- Cliente `RestClient` para `GET {LICENCIAMENTO_API_URL}/api/arquivo/{chave}`; a chave vai como segmento de caminho codificado inteiro (inclusive `/`). Implementa a porta `ArquivosDoRepositorio` (de `dossie.application.port`).
- Streaming direto para arquivo temporário; nunca o arquivo inteiro em memória (RNF03); o parcial é apagado em qualquer falha.
- Conexão em 10 s; **inatividade** da leitura (sem receber bytes, vale também para o corpo) em `DOWNLOAD_TIMEOUT_SEGUNDOS`; teto total por documento, somando as novas tentativas, em `DOWNLOAD_TOTAL_MAXIMO_SEGUNDOS`. Lidos a cada download (a S5 troca pela configuração).
- Novas tentativas (S2.2): até 2 extras (espera de 2 s e 5 s) só para tempo de inatividade, 503 e queda de conexão; nunca para 404, 401 e 403. O adapter devolve o número de tentativas, sem chave nem URL.
- Dois modos de autenticação: `bearer` (`Authorization: Bearer <token>`, padrão) e `cookie` (`Cookie: <nome>=<token>`).
- URL ausente **ou vazia** = adapter "arquivos não configurados" (mesmo padrão do SIGA, `ConditionalOnExpression`): o documento falha com "API de arquivos não configurada"; a listagem não depende da API.
- Chave `"0"` (ou nula/vazia) nunca é requisitada.
- Tradução de erros: **401 e 403** → `CredencialArquivosRecusadaException` (interrompe toda a geração; `CREDENCIAL_ARQUIVOS_RECUSADA`, 502); 404, 5xx, tempo limite e conexão recusada → falha **do documento** (interrompe o dossiê, RN09). Nenhuma mensagem, log ou exceção traz URL, token ou chave.
- A identificação do tipo real é a assinatura `%PDF-` conferida pelo validador do dossiê.
- Credencial: protótipo usa token de sessão pessoal; definitivo usa credencial de serviço (pendente).

## `integration.scorp` — SCORP (documentos pessoais)

- Lista documentos por `PESSOA.ID_CORPORATIVO` de interessado, representantes e responsáveis técnicos.
- O pacote leva o nome do sistema (SCORP). Na lista de documentos do usuário, a origem é exibida como "Pessoal"; nas telas técnicas (conexões, auditoria de acessos) usa-se "SCORP".
- Falha nesta integração não impede a listagem dos demais documentos.

## `integration.libreoffice` — conversor de documentos (S3)

- Implementa `ConversorDeDocumentos` (de `documento.application.port`): textos (DOC, DOCX, ODT, RTF), HTML e planilhas para PDF. Imagens não passam por aqui.
- **Único** pacote que usa JODConverter e as bibliotecas UNO (`org.jodconverter..`, `com.sun.star..`) e a limpeza de HTML (`org.jsoup..`); o `ArchitectureTest` fiscaliza.
- Não é sistema externo de rede: é um processo local (`soffice`) que recebe documentos de terceiros, tratados como **hostis**. Macro nunca executada, vínculo nunca atualizado, rede bloqueada pelo perfil, HTML limpo antes, cópia de trabalho com nome gerado, timeout com encerramento forçado e nenhum processo órfão (detalhes em `docs/05`).
- Falha tipada (`FalhaDeConversaoException` com `Motivo`); mensagens em português, sem caminho interno. `soffice` ausente = conversor indisponível, e a aplicação sobe.

## `integration.arrecadacao` — taxas

- Desativado por configuração até a forma de obtenção ser confirmada. Não criar código especulativo.

## Regra geral

DTOs externos ficam em `integration.<sistema>..dto` e **não aparecem** em assinaturas de módulos de negócio, nem transitivamente. O `ArchitectureTest` deve verificar isso para todos os pacotes `integration.*`.
