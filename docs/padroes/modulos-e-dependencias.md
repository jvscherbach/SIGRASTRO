# Módulos e regras de dependência

## Camadas dentro de um módulo

Cada módulo materializa **apenas** os pacotes que a funcionalidade usa.

- **`api`**: controllers REST e mapeadores HTTP. DTOs de entrada/saída ficam em `br.gov.mt.sema.vistas.<modulo>.api.dto`. O controller valida (Bean Validation), traduz HTTP e chama um caso de uso — sem regra de negócio.
- **`application`**: casos de uso, comandos, consultas e portas.
- **`domain`**: modelos, regras, objetos de valor, exceções específicas. Ex.: `SituacaoDocumento` (vigente/substituído/indisponível), regra de ordem cronológica (RN03), regra "completo ou nenhum" (RN09), perfis e permissões.
- **`infrastructure`**: implementações das portas (persistência própria, adapters em memória do protótipo, PDFBox, LibreOffice, disco temporário).

## Regras de dependência

1. `api` pode depender de `application`.
2. `application` pode depender de `domain`.
3. `application` **não** depende de `api` nem de `infrastructure`.
4. `domain` **não** depende de `api`, `application`, `infrastructure`, Spring Web, `jakarta.persistence`, JDBC nem de tipos de `integration.*`.
5. `infrastructure` implementa portas de `domain` ou `application`.
6. Um módulo **não importa** controller, DTO HTTP, entidade JPA ou repository de outro módulo.
7. Regra de negócio fica no domínio — nunca em controller, configuração, `shared` ou adapter de integração.
8. Sem acesso direto ao repository de outro módulo e sem ciclos entre módulos.
9. **Somente `integration.siga` executa SQL contra o banco do SIGA.** Os módulos de negócio declaram portas (ex.: `documento.application.port.DocumentosDoRequerimento`) e recebem objetos internos.
10. Tipos de `integration.*` (DTOs externos, linhas do SIGA, enums do SIGA) não aparecem em assinaturas de nenhum módulo de negócio.

Regras adicionadas na S2 ao `ArchitectureTest`: (a) **HTTP de saída** (`RestClient`, `java.net.http`, `HttpURLConnection`) só em `integration.*`, e nenhum HTTP em `integration.siga`; (b) a **chave do arquivo** (`chaveDoArquivo()` de `Documento` e de `ItemDoPedido`) não é lida por classe de `..api..`, e nenhum campo de `..api.dto..` tem "chave" no nome.

Fiscalização por `ArchitectureTest` (ArchUnit): `./mvnw -Dtest=ArchitectureTest test` (não precisa de Docker). O teste ignora pacotes vazios e passa a inspecioná-los quando recebem classes.

## Contratos internos usados no Vistas

| Consumidor | Contrato publicado por | Exemplo |
|---|---|---|
| `dossie` | `documento` | caso de uso que entrega os documentos selecionados já validados (perfil, sigilo, vínculo) |
| `dossie`, `documento`, `consulta` | `auditoria` | porta `RegistrarEvento` (somente inclusão) |
| todos | `acesso` | usuário atual, perfil e verificação de vínculo (RN07) |
| `dossie`, `documento` | `configuracao` | parâmetros vigentes (expiração, origens, timeouts, modo das planilhas) |
| `documento`, `dossie` | portas implementadas em `integration.*` | obter binário do arquivo, listar documentos pessoais |

**Contratos internos entregues na S2:**

| Consumidor | Contrato | Publicado por |
|---|---|---|
| `documento` | `VerificarAcessoAoRequerimento` (existe + RN07) | `consulta.application` |
| `dossie` | `ObterRequerimento` (dados da capa, com RN07) | `consulta.application` |
| `documento`, `dossie` | `ObterPermissoesDoUsuario`, `UsuarioAtual` | `acesso.application` |
| `dossie` | `SelecionarDocumentosParaDossie` (seleção validada, em ordem, com a chave resolvida) | `documento.application` |
| `integration.siga` | porta `DocumentosDoRequerimento` | `documento.application.port` |
| `integration.licenciamento` | porta `ArquivosDoRepositorio` | `dossie.application.port` |
| `dossie.infrastructure` | portas `ValidadorDePdf`, `MontadorDoDossie`, `RepositorioDePedidos`, `FilaDeGeracao` | `dossie.application.port` |
| `config` | `ParametrosDoDossie` (variáveis de ambiente; a S5 troca) | `dossie.application.port` |

Dependências previstas entre módulos de negócio: `documento` → `consulta`, `acesso`; `dossie` → `documento`, `consulta`, `acesso`. Não há ciclo (o `ArchitectureTest` fiscaliza). `consulta` não depende de `documento` nem de `dossie`.

**Nunca são contrato interno:** DTO HTTP de outro módulo, entidade JPA de outro módulo, repository concreto de outro módulo, DTO de integração externa.

## Limitações da fiscalização automática

- ArchUnit verifica bytecode e convenção de pacote, não semântica. Revisão continua necessária.
- DTO fora de `..api.dto..` com natureza de DTO não é detectado.
- Correção do mapeamento domínio↔entidade e das consultas ao SIGA é coberta por teste, não por ArchUnit.
