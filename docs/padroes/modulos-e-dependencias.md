# Módulos e regras de dependência

## Camadas dentro de um módulo

Cada módulo materializa **apenas** os pacotes que a funcionalidade usa.

- **`api`**: controllers REST e mapeadores HTTP. DTOs de entrada/saída ficam em `br.gov.mt.sema.rastro.<modulo>.api.dto`. O controller valida (Bean Validation), traduz HTTP e chama um caso de uso — sem regra de negócio.
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

Regras adicionadas na S4 (bloco 1, RNF10): (c) **só a `PoliticaDeAcesso` decide acesso**: fora de `acesso`, nenhuma classe depende de `Perfil` nem chama `Usuario.perfil()`, `Usuario.cpfCnpj()`, `RestricaoDeInteressado.de(...)` ou `RestricaoDeInteressado.permite(...)`; (d) **controllers não decidem acesso**: classes de `..api..` fora de `acesso` não dependem de `PoliticaDeAcesso`, `UsuarioAtual`, `RestricaoDeInteressado` nem `Usuario` (delegam ao caso de uso); (e) o módulo `acesso` não depende dos demais módulos de negócio nem de `integration.*`.

Contrato da auditoria (S5, blocos 2 e 3): os módulos usam só `auditoria.application.RegistrarEvento`, `auditoria.application.ContextoDoEvento` (retrato da requisição; nunca lança) e os tipos de `auditoria.domain` para montar o `NovoEvento` (montar nunca lança; a validação fica em `EventoDeAuditoria`, dentro de `registrar`, que nunca lança). `auditoria.application.port` (`ConsultarEventos`, `RepositorioDeEventos`, `RetencaoDeEventos`, `ParametrosDaAuditoria`) e `EventoDeAuditoria` são internos; só `config` liga `ParametrosDaAuditoria`.

Regras adicionadas na S5 (bloco 2): (f) `auditoria` não depende de `consulta`, `documento`, `dossie`, `configuracao` nem `integration.*`: os dados chegam no evento; (g) fora de `auditoria` e `config`, ninguém depende de `auditoria.application.port` nem de `EventoDeAuditoria`; (h) nenhum campo de instância de `auditoria.domain` tem no nome chave, token, cookie, senha, caminho, CPF ou CNPJ.

Regras adicionadas na S7 (bloco 1): (i) o módulo `tramitacao` entra na lista de módulos de negócio (todas as regras de camada valem para ele); (j) `tramitacao` não depende de `documento` nem de `dossie`; (k) `tramitacao.domain` não depende de `org.yaml..` nem de `org.springframework..` (o catálogo de etapas é lido em `tramitacao.infrastructure.catalogo`).

Regras adicionadas na S9 (bloco 2): (l) o módulo `financeiro` entra na lista de módulos de negócio; (m) `financeiro` não depende de `documento` nem de `dossie`; (n) `financeiro.domain` não depende de `org.springframework..`.

Regras adicionadas na S10 (bloco 2): (o) o módulo `painel` entra na lista de módulos de negócio; (p) `painel` não depende de `documento`, `dossie` nem `financeiro`; (q) `painel.domain` não depende de `org.springframework..`; (r) fora de `painel` e `integration.siga`, ninguém usa `painel.application.port` (só a atualização do instantâneo lê o SIGA).

Regras adicionadas na S11 (bloco 4): (s) o módulo `caixa` entra na lista de módulos de negócio; (t) `caixa` não depende de `documento`, `dossie`, `financeiro` nem `painel` (da `tramitacao`, só contratos publicados); (u) `caixa.domain` não depende de `org.springframework..`; (v) fora de `caixa` e `integration.siga`, ninguém usa `caixa.application.port` (só a atualização do instantâneo lê o SIGA).

Fiscalização por `ArchitectureTest` (ArchUnit): `./mvnw -Dtest=ArchitectureTest test` (não precisa de Docker). O teste ignora pacotes vazios e passa a inspecioná-los quando recebem classes.

## Contratos internos usados no SIGRASTRO

| Consumidor | Contrato publicado por | Exemplo |
|---|---|---|
| `dossie` | `documento` | caso de uso que entrega os documentos selecionados já validados (perfil, sigilo, vínculo) |
| `dossie`, `documento`, `consulta` | `auditoria` | porta `RegistrarEvento` (somente inclusão) |
| `configuracao` | `acesso` | `AdministrarUsuarios` (cadastro de usuários, S5; só `acesso` conhece `Perfil`) |\n| todos | `acesso` | usuário atual e a `PoliticaDeAcesso` (RN07, sigilo, responsável pela tarefa, dono do pedido; S4) |
| `dossie`, `documento`, `auditoria` | `configuracao`, por `config` | parâmetros vigentes: leem só as próprias portas (`ParametrosDoDossie`, `OrigensLigadas`, `RetratoDeParametros`...); as implementações em `config` usam `configuracao.ParametrosEmVigor` (S5) |\n| `configuracao` | `integration.*` | porta `VerificadorDeConexao` (teste de conexão, S5) |
| `documento`, `dossie` | portas implementadas em `integration.*` | obter binário do arquivo, listar documentos pessoais |

**Contratos internos entregues na S2:**

| Consumidor | Contrato | Publicado por |
|---|---|---|
| `documento` | `VerificarAcessoAoRequerimento` (existe + RN07) | `consulta.application` |
| `dossie` | `ObterRequerimento` (dados da capa, com RN07) | `consulta.application` |
| `consulta`, `documento`, `dossie` | `PoliticaDeAcesso` (S4, substitui `ObterPermissoesDoUsuario` e `ObterRestricaoDeInteressado`), `UsuarioAtual` | `acesso.application` |
| `dossie` | `SelecionarDocumentosParaDossie` (seleção validada, em ordem, com a chave resolvida) | `documento.application` |
| `integration.siga` | porta `DocumentosDoRequerimento` | `documento.application.port` |
| `tramitacao` (S7) | `VerificarAcessoAoRequerimento` (existe + RN07, antes da posição) | `consulta.application` |
| `integration.siga` (S7) | portas `PosicaoNoFluxo` e `IdentificacaoDosResponsaveis` (`CamundaPosicaoJdbc`) | `tramitacao.application.port` |
| `config` (S7) | `ParametrosDoRastro` (variáveis de ambiente; a tela vem no bloco 10) | `tramitacao.application.port` |
| `integration.siga` (S9) | porta `LancamentosDoRequerimento` (`LancamentosDoSigaJdbc`, único leitor de `LANCAMENTO_TAXA`) | `financeiro.application.port` |
| `config` (S9) | `ParametrosDoFinanceiro` (variáveis de ambiente; a tela vem no bloco 10) | `financeiro.application.port` |
| `financeiro.infrastructure.tramitacao` (S9, blocos 4 e 5) | caso de uso `LerTramitacao` (caminho exato, atrás da porta `CaminhoDaTramitacao`) | `tramitacao.application` |
| `financeiro.infrastructure.tramitacao` (S9, bloco 5) | implementa a porta `FinanceiroDoRequerimento` (aviso de taxa vencida e marcas nas passagens), chamada por `AbrirPosicao` e `AbrirTramitacao` | `tramitacao.application.port` |
| `integration.siga` (S10, bloco 2) | portas `ResumoDoAnoNoSiga` e `TarefasAtivasEmLote` (`PainelDoSigaJdbc`, único leitor do SIGA e do Camunda para o painel; sem SIGA, `ResumoDoAnoNaoConfigurado` e `TarefasEmLoteNaoConfiguradas`) | `painel.application.port` |
| `painel` (S10, bloco 2) | `LimiteDeLeiturasDoCamunda` (cada lote de tarefas ocupa uma vaga) | `tramitacao.application` |
| `integration.siga` (S11, bloco 4) | porta `PosseDoAnalistaEmLote` (`PosseDoAnalistaNoSigaJdbc`, único leitor do SIGA e do Camunda para a caixa; sem SIGA, `PosseNaoConfigurada`) | `caixa.application.port` |
| `caixa` (S11, bloco 4) | `LimiteDeLeiturasDoCamunda` (cada consulta de candidatos e cada lote de detalhe ocupa uma vaga), `CatalogoDeEtapas` e `IdentificadorDoResponsavel` (tipos de domínio) | `tramitacao.application`, `tramitacao.domain` |
| `integration.licenciamento` | porta `ArquivosDoRepositorio` | `dossie.application.port` |
| `dossie.infrastructure` | portas `ValidadorDePdf`, `MontadorDoDossie`, `RepositorioDePedidos`, `FilaDeGeracao` | `dossie.application.port` |
| `config` | `ParametrosDoDossie` (variáveis de ambiente; a S5 troca) | `dossie.application.port` |

Dependências previstas entre módulos de negócio: `documento` → `consulta`, `acesso`; `dossie` → `documento`, `consulta`, `acesso`. Não há ciclo (o `ArchitectureTest` fiscaliza). `consulta` não depende de `documento` nem de `dossie`. `tramitacao` (S7) → `consulta` e `acesso`, só por porta ou caso de uso publicado; nunca `documento` nem `dossie`. O SQL do Camunda e do Licenciamento que ela consome fica em `integration.siga`, atrás de portas de `tramitacao.application`. `financeiro` (S9) não depende de `documento` nem de `dossie`; da `tramitacao`, só por porta (S9, blocos 4 e 5); o SQL de `LANCAMENTO_TAXA` fica em `integration.siga`, atrás de `financeiro.application.port`. `painel` (S10) → `tramitacao` (e, a partir do bloco 3, `consulta`) só por contrato publicado; nunca `documento`, `dossie` nem `financeiro`; o SQL do painel (`sql/siga/painel/`) fica em `integration.siga`, atrás de `painel.application.port`. `caixa` (S11) → `tramitacao` só por contrato publicado; nunca `documento`, `dossie`, `financeiro` nem `painel`; o SQL da caixa (`sql/siga/caixa/`) fica em `integration.siga`, atrás de `caixa.application.port`.

**Nunca são contrato interno:** DTO HTTP de outro módulo, entidade JPA de outro módulo, repository concreto de outro módulo, DTO de integração externa.

## Limitações da fiscalização automática

- ArchUnit verifica bytecode e convenção de pacote, não semântica. Revisão continua necessária.
- DTO fora de `..api.dto..` com natureza de DTO não é detectado.
- Correção do mapeamento domínio↔entidade e das consultas ao SIGA é coberta por teste, não por ArchUnit.
