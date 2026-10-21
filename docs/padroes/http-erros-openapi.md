# HTTP, erros, paginação e OpenAPI

## Erros com `ProblemDetail`

- Handler transversal em `shared.api.error`.
- Exceções genéricas e seguras em `shared.application.exception`; exceções de negócio ficam no módulo dono e recebem tradução HTTP explícita.
- Todo erro serializado traz: `status`, `title`, `detail` (seguro), `errorCode` estável, `timestamp`, `path`, `correlationId`. Validação traz também os campos inválidos.
- **Nunca** serializar stack trace, SQL, nomes de tabela do SIGA, chave de arquivo, token ou credencial.

### Códigos de erro do Vistas

| HTTP | `errorCode` | Situação |
|---|---|---|
| 400 | `PARAMETRO_INVALIDO` | Parâmetros inválidos (ex.: número fora do formato `N/AAAA`) |
| 401 | `NAO_AUTENTICADO` | Login ausente ou inválido |
| 403 | `PERFIL_SEM_PERMISSAO` | Função restrita ao perfil (auditoria de acessos, configurações) |
| 403 | `DOCUMENTO_SIGILOSO` | Documento sigiloso para perfil não autorizado |
| 403 | `NAO_INTERESSADO` | Operador sem vínculo com o processo/requerimento (RN07) |
| 404 | `NAO_ENCONTRADO` | Requerimento, processo, documento, dossiê ou evento inexistente |
| 409 | `DOSSIE_SEM_ARQUIVO` | Download de dossiê cuja geração falhou (RN09) |
| 410 | `DOSSIE_EXPIRADO` | Dossiê expirado |
| 502 | `CREDENCIAL_ARQUIVOS_RECUSADA` | API de arquivos recusou a credencial (token expirado) |
| 503 | `SIGA_INDISPONIVEL` | Banco do SIGA indisponível |
| 500 | `ERRO_INTERNO` | Demais erros |

`405` (método não permitido) e `415` (tipo de conteúdo não suportado) usam o `errorCode` `PARAMETRO_INVALIDO` com o status HTTP correspondente (decisão da S0, até haver código próprio). Erro de validação traz a lista `campos` (`campo` + `mensagem`); parâmetro inválido apontado pelo domínio (`ParametroInvalidoException` com campo) também.

A falha de um documento durante a geração **não** é erro HTTP da requisição de geração (que é assíncrona): fica no status `FALHA` do pedido, com `erro` (documento e motivo).

## Correlação

- Filtro aceita `X-Correlation-Id` (até 64 caracteres seguros) ou gera UUID; devolve no header; coloca no MDC e limpa ao final.
- Roda antes da cadeia de segurança, para que 401/403 também tenham correlação.
- Controllers não manipulam o MDC.
- O `correlationId` também é gravado nos eventos de auditoria (definitivo), para cruzar log e auditoria.

## Paginação

Contrato público `PageResponse<T>` (`conteudo`, `pagina` a partir de 0, `tamanho`, `totalElementos`, `totalPaginas`), criado por `PageResponse.of(...)` (sem Spring Data no protótipo; `from(page)` só entra com o Spring Data). Nunca expor `Page<T>`. Usado em busca por interessado e na auditoria.

## OpenAPI (springdoc)

- Documentar só endpoints reais, com `responseCode` explícito em cada `@ApiResponse` referenciado:

```java
@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
```

- `bearerAuth` é o esquema do contrato. No protótipo o token é do login simulado; isso **não** é autenticação real.
- Habilitação: local habilitado; homologação desabilitado por padrão (`OPENAPI_ENABLED`); produção sempre desabilitado.

## Front-end (Angular)

- Um interceptor HTTP lê `errorCode` e decide a mensagem; nunca exibe `detail` técnico cru nem confia só no status.
- O front envia `X-Correlation-Id` gerado por requisição quando útil para suporte.
- Regras de perfil, sigilo e vínculo são sempre validadas no back-end (RNF10); o front só esconde o que o perfil não usa (RN08).
