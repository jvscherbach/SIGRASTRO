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

**Decisão:** listar todos os documentos, inclusive substituídos e indisponíveis, com a situação calculada. Os vigentes vêm pré-selecionados.
**Motivo:** o operador decide o conteúdo do dossiê com visibilidade total.

## D12 — Dossiê completo ou nenhum

**Decisão:** se qualquer documento selecionado falhar, a geração é interrompida com erro e nenhum arquivo é entregue. Não há página substituta nem relatório de geração no fim do dossiê. *(Substitui a decisão anterior de página substituta.)*
**Motivo:** o dossiê deve conter exatamente o que foi pedido; entregar parcialmente poderia ser tomado como completo.
**Consequência:** falhas transitórias (ex.: tempo limite) exigem nova tentativa; o usuário pode remover o documento por decisão explícita.

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
