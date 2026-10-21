# Integrações externas

Toda dependência de sistema externo vive num pacote `integration.<sistema>`, com o nome do próprio sistema (dois níveis abaixo da raiz; `integration` sozinho não é módulo). O adapter converte o dado externo para um **contrato interno** antes de entregá-lo aos módulos de negócio. Mudança no sistema externo deve mudar só o adapter.

## `integration.siga` — banco do SIGA (somente leitura)

- Único pacote com SQL contra `SIGA2_LICENCIAMENTO`, `SIGA2_CAMUNDA` e `SIGA2_OUTORGA`, e com conhecimento de nomes de tabela/coluna e de enums do SIGA (`SITUACAO_PROCESSO`, `GRUPO_DOCUMENTO`, `TIPO`).
- Usa `NamedParameterJdbcTemplate` no datasource `siga` (somente leitura). **Sem JPA** para o SIGA: o modelo do SIGA não é nosso e mapeá-lo como entidade criaria acoplamento e risco de escrita.
- SQL em arquivos de recurso (`src/main/resources/sql/siga/*.sql`) ou constantes no adapter — nunca espalhado em módulos de negócio.
- Sempre com parâmetros nomeados; nunca concatenação de entrada do usuário.
- Converte para tipos internos: situação do documento, sigilo (RN06 — na dúvida, sigiloso), origem, data cronológica (RN03).
- A chave do arquivo (Pandora) é lida aqui e entregue apenas ao adapter de arquivos; nunca sai para a API HTTP (RNF09).
- Tarefa atual: lida de `SIGA2_CAMUNDA`, mas o termo "Camunda" não aparece fora deste pacote nem na interface. Uma migração do SIGA para outro motor de fluxo muda só este adapter.
- Definitivo: preferir **views** de leitura criadas pelo DBA (`VW_`), concedidas ao usuário de leitura, em vez de grants diretos nas tabelas. Isso isola o serviço de mudanças no modelo do SIGA.

## `integration.licenciamento` — API de arquivos

- Cliente `RestClient` para `GET {LICENCIAMENTO_API_URL}/api/arquivo/{chave}` (e `versao` opcional).
- Streaming direto para arquivo temporário; nunca o arquivo inteiro em memória (RNF03).
- Timeout configurável (`DOWNLOAD_TIMEOUT_SEGUNDOS`/parâmetro de configuração).
- Tradução de erros para exceções internas: 401 → credencial de arquivos expirada (502 na nossa API); 404/503/timeout → falha do documento (interrompe o dossiê, RN09).
- Credencial: protótipo usa token de sessão pessoal; definitivo usa credencial de serviço (pendente).

## `integration.scorp` — SCORP (documentos pessoais)

- Lista documentos por `PESSOA.ID_CORPORATIVO` de interessado, representantes e responsáveis técnicos.
- O pacote leva o nome do sistema (SCORP). Na lista de documentos do usuário, a origem é exibida como "Pessoal"; nas telas técnicas (conexões, auditoria de acessos) usa-se "SCORP".
- Falha nesta integração não impede a listagem dos demais documentos.

## `integration.arrecadacao` — taxas

- Desativado por configuração até a forma de obtenção ser confirmada. Não criar código especulativo.

## Regra geral

DTOs externos ficam em `integration.<sistema>..dto` e **não aparecem** em assinaturas de módulos de negócio, nem transitivamente. O `ArchitectureTest` deve verificar isso para todos os pacotes `integration.*`.
