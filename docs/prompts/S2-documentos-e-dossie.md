# SIGA Vistas: Sprint 2, documentos e dossiê em PDF (com correções da S1.1)

## Contexto

A S0 (fundação), a S1 (consulta) e a S1.1 (ajustes com o mapeamento P01) estão prontas e foram homologadas pelo responsável com dados reais, em base de desenvolvimento. Esta entrega tem duas partes:

- **Parte A, correções** achadas na homologação da S1.1: o quadro do topo ainda muda de tamanho, a listagem de objetivos e finalidades fica confusa quando há mais de um item, os atalhos recentes se repetem, e há dados pessoais reais em arquivos de documentação.
- **Parte B, a S2 propriamente dita** (`docs/14-plano-de-sprints.md`): listar **todos** os documentos de um requerimento e gerar o dossiê em **PDF** dos documentos selecionados. Só PDFs entram no dossiê nesta sprint. Conversão de planilhas, textos e imagens, o modal de visualização e o endpoint de conteúdo ficam para a S3. Geometrias (SHP, KML, KMZ), SCORP, DAR, a tela "Dossiês gerados" e a auditoria ficam para S4 e S5.

O objetivo do protótipo é provar que os processos que hoje não geram vista passam a gerar. Por isso a S2 precisa funcionar de ponta a ponta com arquivos reais: o responsável a homologa manualmente, com o token dele.

## Segurança e processo (leia primeiro; vale para toda a sessão)

Na entrega anterior, a `api` foi subida com as variáveis `SIGA_DB_*` do `.env.local` preenchidas e leu dados reais do SIGA (nomes e CPFs). A causa foi uma checagem que não viu linhas com espaço no início. **Isso não pode se repetir.** Regras:

1. **Nunca abra, imprima ou copie o conteúdo do `.env.local`** (nada de `cat`, `Get-Content`, `type`, leitura por ferramenta). Se precisar saber se uma variável está preenchida, use só testes que não mostram o valor, e sempre tolerando espaços no início da linha.
2. **Crie `compose.verificacao.yaml`** (versionado, sem segredo) que esvazia, no serviço `api`, todas as variáveis que levam a sistemas externos: `SIGA_DB_URL`, `SIGA_DB_USER`, `SIGA_DB_PASSWORD`, `LICENCIAMENTO_API_URL`, `LICENCIAMENTO_API_TOKEN`, `LICENCIAMENTO_API_COOKIE_NAME`, `SCORP_API_URL` (e as que existirem com credencial). A chave `environment` tem precedência sobre o `env_file`.
3. **Todo `docker compose up` desta sprint usa os três arquivos:** `-f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml`. Nunca suba sem o terceiro.
4. **Antes de qualquer chamada à API**, confirme dentro do container, sem imprimir valores, que cada variável está vazia, por exemplo: `docker compose ... exec -T api sh -c '[ -z "$SIGA_DB_URL" ] && echo vazia || echo PREENCHIDA'`. Se alguma vier `PREENCHIDA`: pare, derrube os containers (sem `-v`), não faça chamada nenhuma e relate.
5. **Se alguma resposta trouxer dado real** (nome, CPF/CNPJ, número de processo que não seja de teste), pare, não copie esse dado para nenhum arquivo, mensagem ou log e relate o que aconteceu.
6. **Você não se conecta ao SIGA, à API de arquivos nem ao SCORP reais.** Testes usam portas falsas, Oracle 23 Free por Testcontainers e um servidor HTTP local de teste (`com.sun.net.httpserver.HttpServer`, sem dependência nova). Token real nunca entra em teste, exemplo, log ou documentação.
7. Registre essas regras em `CLAUDE.md` (seção própria, no topo), em `README.md` (problemas comuns: "linhas do `.env.local` não podem começar com espaço"), em `docs/padroes/ambientes-e-docker.md` (e no espelho em `.claude/skills/vistas-banco/references/`) e como decisão nova em `docs/09-decisoes.md` ("Verificação sempre sem sistemas externos").
8. Dados pessoais: **nenhum CPF, CNPJ ou nome de pessoa real** em documentação, testes, prompts, exemplos ou logs. Use dados fictícios evidentes (ex.: `12.345.678/0001-90`, "Associação Residencial Vale Verde"). Registre a regra em `CLAUDE.md`.

## Regras gerais (as mesmas das sprints anteriores)

- O banco do SIGA é **somente leitura**. Só `integration.siga` tem SQL, em arquivos `.sql` com parâmetros nomeados, sem `SELECT *` e compatível com **Oracle 19c**. Só `integration.licenciamento` fala HTTP com a API de arquivos.
- Nenhuma credencial, token ou URL institucional em arquivo versionado. Imagens Docker com tag fixa.
- Nunca registre CPF/CNPJ completo, token ou chave de arquivo em log.
- O termo "Camunda" não aparece fora de `integration.siga` nem na interface.
- Não execute nenhum comando git além de `git diff`. Não faça commit, push, branch nem stash. Nunca rode `docker compose down -v`.
- Atualize `docs/` **à medida que avança**. Alterações em `docs/padroes/` também vão para o espelho em `.claude/skills/vistas-banco/references/`.
- Se este prompt conflitar com a documentação ou o código, **pare e relate o conflito** em vez de escolher sozinho. Itens **NÃO CONFIRMADO** não podem ser inventados.

## Leitura obrigatória

1. `CLAUDE.md`, a skill `vistas-banco` e `docs/13-padroes-tecnicos.md` com `docs/padroes/` (em especial `modulos-e-dependencias.md`, `integracao-externa.md`, `agendamento.md`, `ambientes-e-docker.md`, `http-erros-openapi.md`, `checklist-verificacao.md`).
2. `docs/02-requisitos.md` (RF06 a RF18, RF20, RF30, RNF01 a RNF11, RN01 a RN12), `docs/04-modelo-de-dados.md` (Documentos, Consultas de referência, Privilégios), `docs/05-integracoes.md`, `docs/06-geracao-do-dossie.md`, `docs/07-api.md` (Documentos, Dossiê, Erros), `docs/08-telas.md` (seções 3 e 4), `docs/09-decisoes.md`, `docs/10-pendencias-e-riscos.md`, `docs/12-configuracoes.md` (Dossiê), `docs/14-plano-de-sprints.md`.
3. `docs/prototipo/siga-vistas-prototipo.html`: funções `secDocs`, `linhaDoc`, `criarJob`, `run`, `drawer`, `tentarNovamente`, `removerEGerar` e o CSS associado. O front da S2 reproduz essas telas com dados reais.
4. O código atual de `consulta`, `acesso`, `integration.siga`, `shared`, `front/src/app/features/detalhe` e `consulta`. Siga o estilo existente e não reescreva o que funciona.

---

# PARTE A: correções da S1.1

## A1. O quadro do topo ainda muda de tamanho (reincidente)

**Sintoma, confirmado em vídeo pelo responsável:** na tela de Consulta, quando a busca falha (critério ausente, 404, 403, 503, erro de rede), o alerta vermelho aparece dentro do cartão de busca. O cartão cresce, o **quadro inteiro do topo cresce em torno de 11 px** (viewport 1280×720) e a **ilustração da cachoeira muda de escala** junto (a queda d'água e a linha de árvores se deslocam). Ao limpar, volta. A correção anterior só garantiu uma estrutura de DOM e não resolveu, porque o quadro continua dimensionado pelo conteúdo.

**O que fazer:**

1. **Reproduza e meça antes de corrigir.** Descubra qual regra de CSS faz a altura do quadro depender do conteúdo (o provável: `min-height` no quadro mais a altura natural do cartão de busca, e a ilustração em posição absoluta com `preserveAspectRatio="slice"` herdando a altura). Registre no relatório a causa real, com o arquivo e a regra.
2. **Corrija a causa:**
   - a altura do quadro **não pode depender do estado** (alerta, resultado vazio, carregando). A área do alerta é um espaço **sempre reservado** (altura fixa suficiente para o alerta de até duas linhas, com `line-clamp: 2`; vazio fica invisível, não colapsado);
   - a ilustração ocupa o quadro com `position: absolute; inset: 0`, `preserveAspectRatio` coerente e `overflow: hidden` no quadro; como a altura não muda, a escala não muda;
   - a regra vale em todos os breakpoints, inclusive celular (390 px de largura);
   - o mesmo vale para o quadro do detalhe e do login, se compartilharem o componente.
3. **Teste de verdade, não de estrutura.** Meça a altura renderizada do quadro (`getBoundingClientRect().height`) nos estados inicial, carregando, 400, 403, 404, 503 e erro de rede, em 1280×720, 1920×1080 e 390×844. A diferença entre qualquer estado e o inicial tem de ser **0 px**.
   - Preferência: teste e2e com `@playwright/test` como **devDependency** (versão fixa, fora das imagens de produção, `npm audit` continua em 0), com a API simulada por `page.route`, em `front/e2e/`, e script `npm run e2e`. Registre a exceção a "sem dependências novas" em `docs/13` e em `docs/09`.
   - Se não for possível instalar o navegador nesta máquina, relate isso e entregue, em `docs/homologacao/S2-roteiro-homologacao.md`, um script de console (colar no DevTools) que mede a altura do quadro, com os seletores reais, e o passo para o responsável provocar cada estado e anotar a altura. **Não declare o problema resolvido sem medição.**
4. Registre a regra "o quadro do topo tem altura estável em todos os estados" em `docs/08-telas.md` (já deve existir: reescreva com a regra do espaço reservado).

## A2. Listagem de objetivos, finalidades e atividades com mais de um item

Hoje, com 2 ou mais itens, os textos ficam colados, em negrito e caixa alta, sem separação (ex.: processo de outorga com 2 objetivos e o bloco "Tipo | Objetivo" repetindo os mesmos 2). Reformule a seção "Objeto do requerimento" **sem mudar o contrato da API**:

- **Um cartão "Objetivos"**, unindo os blocos "Objetivos" e "Tipo | Objetivo" (são a mesma lista; o SIGA os mostra duplicados). Cada objetivo é uma linha: etiqueta pequena do tipo (Superficial, Subterrânea, Barramento; a partir de `tiposObjetivo`, ou sem etiqueta se só houver `objetivos`) e o texto do objetivo em peso normal (não negrito), separadas por divisor fino. Com mais de 1 item, um contador no cabeçalho do cartão ("2"). Com mais de 5, mostra 5 e um botão "Mostrar todos (N)".
- **Um cartão "Finalidade e atividade"**, agrupado por finalidade: o nome da finalidade como subtítulo e, abaixo, as atividades daquela finalidade em lista. Finalidades repetidas não se repetem na tela.
- **Licenciamento:** o cartão "Atividade" mostra `atividade` como texto principal e, só se o grupo for diferente (compare sem acento e sem diferenciar maiúsculas), o grupo como legenda "Grupo: ...". Hoje aparece "FABRICAÇÃO DE ARTEFATOS DE CIMENTO · Fabricação de artefatos de cimento para uso na construção", e a tela atual do SIGA mostra só a atividade.
- **Só renderize cartões com dados.** Se não houver nenhum dado de objeto, um aviso único: "Sem objeto cadastrado para este requerimento". Não mostre três cartões com "—".
- Layout: `grid` com colunas `auto-fit`, cartões com a mesma altura na linha, **sem altura fixa** (crescem com o conteúdo) e sem estourar no celular. Textos vêm do SIGA e são exibidos como estão (sem `text-transform`).
- Testes (Vitest): 1, 2 e 6 objetivos (com e sem "Mostrar todos"), finalidade repetida agrupando atividades, grupo igual e diferente da atividade, sem nenhum dado, outorga e licenciamento.

## A3. Atalhos "Consultados recentemente" repetidos

Abrir os dois requerimentos do processo <REQ-TESTE-06> gera dois atalhos com o mesmo rótulo "Processo <REQ-TESTE-06>". Quando dois atalhos tiverem o mesmo rótulo, acrescente o desempate: "Processo <REQ-TESTE-06> · Req. <REQ-TESTE-07>" (número do requerimento) e a situação em texto pequeno. O atalho guarda id, rótulo, número do requerimento e situação (migre a leitura de atalhos antigos sem quebrar). Teste: dois atalhos de mesmo processo, atalho antigo sem os campos novos, limite de 6.

## A4. Dados pessoais em arquivos versionados

O `docs/homologacao/S1.1-roteiro-homologacao.md` contém um CPF e um CNPJ reais (nas tabelas de busca e nos SQL de conferência). Corrija:

1. Varra `docs/`, `README.md`, `CLAUDE.md`, `api/src/test`, `front/src` e `docs/prompts/` por CPF/CNPJ (com e sem máscara) e por nomes de pessoas reais. Troque por marcadores como `<CPF_DE_TESTE>` e `<CNPJ_DE_TESTE>` nos roteiros, e por dados fictícios nos testes e exemplos. Não substitua os dados fictícios que já existem.
2. Nos roteiros, em vez de totais fixos ("9 resultados"), diga: "compare o total da tela com o `SELECT COUNT(*)` abaixo, na mesma base". A base de desenvolvimento tem dados de abril e maio de 2026 e o total muda de uma base para outra. Em todo SQL de conferência da busca por CPF/CNPJ do roteiro da S1.1 (passo 4):

```sql
SELECT COUNT(*)
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
JOIN   SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA
WHERE  p.CPF_CNPJ = '<SOMENTE_DIGITOS>';
```

3. Deixe no relatório o resultado da varredura (arquivos alterados e o padrão usado) e **não repita os valores encontrados**.

## A5. Registro da homologação da S1.1 e pendências

A homologação da S1.1 foi concluída pelo responsável em 21/10/2026, **em base de desenvolvimento**. Registre:

**`docs/14`, seção "Validação da S1.1"** (substitua o "PENDENTE" por este resultado):

| Item | Resultado |
|---|---|
| Grants | `SELECT` confirmado nas 19 tabelas lidas (`SIGA2_LICENCIAMENTO`, `SIGA2_OUTORGA`, `SIGA2_ARRECADACAO.ATIVIDADE` e 3 do motor de fluxo) |
| Colunas de ligação | Confirmadas em `ALL_TAB_COLUMNS`: `FINALIDADE_ATIVIDADE.ID_FINALIDADE`, `OBJETIVO_REQUERIMENTO_OUTORGA.ID_OBJETIVO_OUTORGA`, `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID_FINALIDADE_ATIVIDADE` |
| Tipos | `ACT_RU_TASK.ASSIGNEE_` é `NVARCHAR2(510)`; `PESSOA.CPF_CNPJ` é `VARCHAR2(20)`; `SERVIDOR.CPF` é `VARCHAR2(11)` |
| Outorga (<REQ-TESTE-05>) | Tipo do requerimento, objetivo, tipo, finalidade e atividade conferem com o SIGA; prioridade "Atendimento normal" confere; responsável aparece **só com o nome** |
| Outorga com prioridade (<REQ-TESTE-09>) | 2 objetivos, finalidade/atividade e o critério "Pessoa com idade igual ou superior a 60 anos" conferem com o SIGA |
| Licenciamento (<REQ-TESTE-10>, LAC) | Atividade confere; **diverge no Objetivo:** o SIGA mostra "LAC" e o SIGA Vistas mostra "—" (ver P20); o grupo da atividade aparecia junto da atividade (corrigido em A2) |
| Número repetido (<REQ-TESTE-06>) | Lista os 2 requerimentos com o aviso |
| Busca por CPF/CNPJ | Totais da tela iguais ao `SELECT COUNT(*)` na base de desenvolvimento (8 e 2) |
| Quadro do topo | **Ainda muda de tamanho** (corrigido nesta sprint, A1) |

**Índices** (consulta do roteiro, passo 2): existem `IDX_PRIORIDADE_PROC_REQ` (`PRIORIDADE_PROCESSO.ID_REQUERIMENTO`) e as chaves únicas de `REQUERIMENTO_LICENCIAMENTO.ID_REQUERIMENTO` e `REQUERIMENTO_OUTORGA.ID_REQUERIMENTO`. **Não retornaram índice:** `PESSOA.CPF_CNPJ`, `SERVIDOR.CPF` e `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID_REQUERIMENTO`.

**`docs/10`:**
- **P19:** fechar o item das colunas de ligação (confirmadas). Mantê-lo apenas para as descrições oficiais dos critérios de prioridade e para as tarefas atribuídas a grupo.
- **P20 (nova):** o SIGA mostra "LAC" como Objetivo em processos de licenciamento por adesão sem objetivos cadastrados. A regra de origem (no código do SIGA, aparentemente pela instância do requerimento) não foi levantada e a coluna não consta da lista de colunas do `REQUERIMENTO`. **Não implemente por inferência**; o responsável fará um levantamento no repositório do SIGA.
- **R17 (novo):** sem índice em `PESSOA.CPF_CNPJ` e `SERVIDOR.CPF`, a busca por CPF/CNPJ e a resolução do nome do responsável leem a tabela inteira. Impacto baixo na base de desenvolvimento; medir em homologação. O SIGA Vistas não cria objeto no SIGA; sugerir o índice à equipe do SIGA. Ajuste também o R09 e o R14 se for o caso.
- Atualize o roteiro da S1.1 com o passo concluído e os totais por `SELECT`.

---

# PARTE B: S2, documentos e dossiê em PDF

## Pré-requisitos

| # | Pré-requisito | Onde estará | Se não estiver pronto |
|---|---|---|---|
| P08 | Token de sessão e forma de envio (Bearer ou cookie), capturado pelo responsável | `.env.local` do responsável (nunca versionado). Procedimento em `docs/05` | Implementar os dois modos (`bearer` e `cookie`) e testar com o servidor HTTP local. Homologação real fica **pendente** |
| P09 | Lista de processos de teste (que falham hoje e com planilhas) | `docs/casos-de-teste.md`, preenchido pelo responsável | Crie o arquivo com o modelo (tabela vazia: requerimento/processo, motivo da falha atual, tem planilha?, resultado, tempo, páginas) e relate |
| P04 | Origens "não exibidas hoje" duplicam `DOCUMENTO_PROCESSO`? Vínculo de `EXTRATO_PUBLICACAO` | Consulta do roteiro, executada pelo responsável | `EXTRATO_PUBLICACAO` fica **desligada** por padrão; as demais entram e a deduplicação por chave (RN05) resolve duplicidades |
| P05 | Tipos reais das colunas de sigilo | Consulta ao dicionário, pelo responsável | Normalização tolerante (item B2) e registro do risco |
| P10 | `SELECT` do usuário de leitura nas tabelas de documentos | Script de grants do roteiro da S2 | Homologação real fica pendente |

**Você não executa nenhuma consulta no SIGA real.** Tudo que depende do banco real vai para o roteiro de homologação da S2.

## B1. Back-end: documentos (`documento` + `integration.siga`)

**SQL: um arquivo por origem** em `resources/sql/siga/documentos/` (em vez de um `UNION ALL` único, para isolar falhas e permitir desligar uma origem). Atualize `docs/04` e registre em `docs/09`. Cada consulta recebe `:idRequerimento` e devolve `ORIGEM, ID, ID_REQUERIMENTO, CHAVE, TITULO, NOME_ARQUIVO, TIPO, DATA_DOC, DATA_FIM, SIGILO`. **Não filtra vigência.** Use **somente** tabelas e colunas do inventário "Documentos" de `docs/04`:

| Origem (código) | Descrição na tela | Observação |
|---|---|---|
| `DOCUMENTO_PROCESSO` | Documento do processo | Data = maior `DATA_ASSINATURA` de `DOCUMENTO_PROCESSO_SIGNATARIO` (com `DATA_FIM IS NULL`), senão `DATA_CRIACAO` |
| `DOCUMENTO_REQUERIMENTO` | Documento do requerimento | |
| `COMPLEMENTACAO` | Complementação | Vínculo por `COMPLEMENTACAO_ITEM` |
| `IMOVEL` | Imóvel | |
| `REPRESENTANTE_LEGAL` | Representante legal | Coluna de sigilo `SIGILO` |
| `RESPONSAVEL_TECNICO` | Responsável técnico | Coluna de sigilo `SIGILO` |
| `PARECER_ANEXO` | Parecer técnico (anexo) | Vigência pela coluna `DATA_EXCLUSAO`; sem sigilo |
| `JUNTADA` | Juntada | `ID_REQUERIMENTO_PRINCIPAL`; sigilo `IND_SIGILOSO` |
| `TITULACAO` | Titulação | Sem vigência e sem sigilo |
| `PRORROGACAO` | Prorrogação de prazo | Sem vigência e sem sigilo |
| `EXTRATO_PUBLICACAO` | Extrato de publicação | **Desligada por padrão** (vínculo NÃO CONFIRMADO, P04) |

- **Nunca** liste `PESSOA_DOCUMENTO_DOENCA_GRAVE` (RN02). `SCORP` e `DAR` ficam fora (S4 e desativado).
- Origens ligadas/desligadas por propriedade (`vistas.documentos.origens.<origem>=true|false`), com os padrões acima. Uma origem **desligada** não é consultada. Falha numa origem **ligada** segue a tabela de exceções da S1 (503 ou 500), sem estado parcial.
- Os nomes de coluna do inventário não foram todos confirmados no banco: marque cada origem como "NÃO CONFIRMADO no banco" em `docs/04` até a homologação e deixe o teste de fumaça (`SigaFumacaTest`) conferir tabela e coluna de cada origem (você escreve, **não executa**).
- Documentos de um requerimento são os do **requerimento** (a chave é o requerimento, D31). Reescreva o RF06 em `docs/02` removendo "todos os requerimentos do processo".

**Domínio (`documento.domain`):**

- `Documento`: `origem`, `id` (texto), `idRequerimento`, `titulo` (título; se vazio, o nome do arquivo), `nomeArquivo`, `extensao` (minúscula, do nome), `tipoArquivo` (`PDF`, `PLANILHA`, `TEXTO`, `IMAGEM`, `GEOMETRIA`, `COMPACTADO`, `OUTRO`), `data`, `situacao`, `sigiloso`, e a **chave do arquivo, que é interna** (nunca vai para DTO, log nem resposta; RNF09).
- **Situação (RN01):** `INDISPONIVEL` se a chave for nula, vazia ou `"0"` (tem precedência); `SUBSTITUIDO` se a coluna de fim da vigência estiver preenchida; senão `VIGENTE`. Origem sem coluna de vigência: vigente se a chave for válida.
- **Sigilo (RN06):** normalização tolerante, porque o tipo real é NÃO CONFIRMADO (P05). Verdadeiro: número diferente de 0, ou texto `S`, `SIM`, `Y`, `YES`, `T`, `TRUE`, `1`. Falso: nulo, número 0, ou `N`, `NAO`, `NÃO`, `NO`, `F`, `FALSE`, `0`. **Qualquer outro valor não nulo: sigiloso** ("na dúvida"). Origem sem coluna de sigilo: não sigiloso. Registre esse risco no `docs/10` (R18).
- **Deduplicação (RN05):** documentos com a **mesma chave válida** (diferente de `"0"`) em mais de uma origem aparecem uma vez; vence a primeira origem na ordem da tabela acima. Chave `"0"` nunca deduplica.
- **Ordem (RN03):** por data crescente; sem data vai ao final; empate por origem e id.
- **Tipo de arquivo** pela extensão: PDF; PLANILHA (xls, xlsx, ods); TEXTO (doc, docx, odt, rtf, html, txt); IMAGEM (png, jpg, jpeg); GEOMETRIA (shp, kml, kmz); COMPACTADO (zip, rar, 7z).
- **Compactados (RN11) não aparecem.** **Geometrias não aparecem na lista nesta sprint** (a seção própria e o "Baixar geometrias" são da S4); mantenha-as classificadas no domínio para a S4.
- **Seleção (regra de domínio):** só `PDF` e `VIGENTE` entram na pré-seleção. Documentos não PDF aparecem na lista, **bloqueados**, com a indicação "conversão disponível em breve". Indisponíveis ficam bloqueados ("arquivo indisponível"). Substituídos aparecem, sem pré-seleção, e podem ser marcados manualmente (se forem PDF).

**Aplicação:** porta `DocumentosDoRequerimento` (implementada por `integration.siga`) e caso de uso `ListarDocumentos`, que aplica as regras acima e as de acesso (próximo item). Resposta: `{ "itens": [...], "totais": { "total", "vigentes", "naoPdf", "indisponiveis", "sigilosos" } }`. Atualize o exemplo de `docs/07`: o `id` do documento passa a ser **texto** em todo o contrato (`"804328"`), para acomodar o SCORP depois; o item ganha `tipoArquivo`, `selecionavelPorPadrao` e `bloqueio` (`null`, `NAO_PDF`, `INDISPONIVEL`, `SIGILOSO`).

**Acesso já nesta sprint (antecipação de S4, registre em `docs/09`, `docs/14` e `docs/07`):** o back-end aplica a regra de sigilo desde já, para não entregar um furo na S2.

- `GET /requerimentos/{id}/documentos` aplica a RN07 (Operador de outro interessado: 403 `NAO_INTERESSADO`; requerimento inexistente: 404).
- Sigilosos aparecem na lista para todos os perfis, com `acessivel=false` para o Operador (Gestor e Administrador: `true`; RF20). O nome de um documento sigiloso para o Operador é **mascarado** (o título vira "Documento sigiloso"; o nome do arquivo e a extensão não saem).
- Sigiloso não é selecionável para o Operador e é rejeitado no `POST /dossies` (403 `DOCUMENTO_SIGILOSO`).
- A matriz completa e a validação dela por `MockMvc` continuam na S4.

## B2. Back-end: arquivos (`integration.licenciamento`)

- Porta `ArquivosDoRepositorio` (em `dossie.application.port` ou `documento.application.port`, conforme `modulos-e-dependencias.md`) e adapter com `RestClient` em `integration.licenciamento` (único lugar com HTTP para esse sistema, `ArchitectureTest` verificando).
- `GET {LICENCIAMENTO_API_URL}/api/arquivo/{chave}`. Variáveis: `LICENCIAMENTO_API_URL`, `LICENCIAMENTO_API_TOKEN`, `LICENCIAMENTO_API_AUTH_MODE` (`bearer` por padrão, ou `cookie`) e `LICENCIAMENTO_API_COOKIE_NAME`. Modo `bearer`: `Authorization: Bearer <token>`. Modo `cookie`: `Cookie: <nome>=<token>`. Documente em `.env.local.example` (comentadas, sem valores) e em `docs/05`. Variável de URL ausente **ou vazia** → adapter "arquivos não configurados" (mesmo padrão do SIGA): o documento falha com o motivo "API de arquivos não configurada" e a listagem não depende dela.
- **Streaming direto para arquivo temporário** (RNF03), com timeout de conexão e de leitura (`DOWNLOAD_TIMEOUT_SEGUNDOS`), sem carregar o binário em memória. Apague o temporário em qualquer falha.
- Chave `"0"` nunca é requisitada.
- **Tradução de erros:** `401`/`403` → exceção que interrompe **toda** a geração com `CREDENCIAL_ARQUIVOS_RECUSADA` (502) e a mensagem "Token da API de arquivos expirado ou recusado: capture um novo token" (sem o valor do token). `404`, `503` e timeout → falha **daquele documento** ("arquivo não encontrado no repositório", "repositório de arquivos indisponível", "tempo limite excedido ao obter o arquivo"). Nenhuma resposta ou log traz URL institucional, token ou chave.
- **Identificação do tipo real:** `Content-Type` e assinatura do arquivo (`%PDF-` nos primeiros bytes). Um documento que a lista marcou como PDF, mas cujo conteúdo não é PDF, é falha do documento ("o arquivo não é um PDF válido").

## B3. Back-end: dossiê (`dossie`)

**API** (atualize `docs/07`):

| Método | Rota | Comportamento |
|---|---|---|
| `POST` | `/api/dossies` | Corpo `{ idRequerimento, documentos: [{ origem, id }] }`. Responde `202 { id }`. Valida: requerimento existe; RN07; lista não vazia; cada `{origem,id}` **pertence** ao requerimento (releia a lista no servidor, nunca confie no cliente); documento indisponível, substituído-sem-PDF ou não PDF é rejeitado com 400 `PARAMETRO_INVALIDO` indicando qual; sigiloso sem perfil: 403 `DOCUMENTO_SIGILOSO`. Resolve as chaves **uma vez**, guarda o instantâneo no pedido e **libera a conexão do SIGA** antes de baixar qualquer arquivo |
| `GET` | `/api/dossies/{id}` | Andamento. Acrescente `itens: [{ origem, id, titulo, situacao }]` com `situacao` `AGUARDANDO`, `OBTENDO`, `OK` ou `FALHA` (RF16) |
| `GET` | `/api/dossies/{id}/arquivo?disposicao=inline\|attachment` | PDF final. `409` se não houver arquivo (falha, em andamento); `410` se expirado; `Content-Disposition` com o nome do RF18 (`dossie-processo-<numero>-<ano>.pdf` ou `dossie-requerimento-<numero>-<ano>.pdf`) |
| `DELETE` | `/api/dossies/{id}` | Cancela o pedido ou descarta o arquivo |

Só quem criou o pedido (e Gestor/Administrador) acessa o `{id}`; os outros recebem 404. O id é um UUID aleatório.

**Pipeline** conforme `docs/06`, sem a conversão (S3): ordenar (RN03) → obter cada PDF (limite de paralelismo `DOSSIE_THREADS`, padrão 2) → validar (abre? senha? tem páginas?) → capa → sumário → montagem → marcadores e numeração.

- **Completo ou nenhum (RN09, RNF04):** a primeira falha interrompe, descarta **todos** os temporários e deixa o pedido em `FALHA` com `erro { origem, id, titulo, motivo }`; nenhum PDF é entregue. Motivos: tempo limite, arquivo não encontrado, protegido por senha, corrompido, sem páginas, não é PDF. "Tentar novamente" e "Remover documento e gerar" são **novos `POST`** (o front envia a seleção de novo).
- **PDFBox 3**, arquivos em disco (`Loader.loadPDF` sobre `File` com cache em arquivos temporários, nunca tudo em memória). Páginas importadas com `importPage` (tamanho, rotação, anotações e links preservados); formulários interativos achatados com tratamento de erro; **não force A4** (RNF06).
- **Capa** (RF12): "Dossiê do Requerimento N/AAAA" ou "Dossiê do Processo N/AAAA" (RN04), interessado, CPF/CNPJ, tipo de processo, situação, data de abertura, objetivos e atividades, quantidade de documentos, data e hora de geração e usuário que gerou. Fonte padrão do PDFBox (WinAnsi): troque caracteres fora desse conjunto por `?` e teste com acentos (ç, ã, é, º).
- **Sumário:** uma linha por documento (data, título, origem, página inicial), com paginação própria calculada (linhas por página fixas), de modo que a página inicial de cada documento seja determinística. **Marcadores** (`PDDocumentOutline`) por documento. **Numeração** "Página X de Y" no rodapé, a partir da primeira página após a capa, posicionada pela caixa e pela rotação de cada página (não por coordenada fixa). Os três respeitam os parâmetros ligado/desligado.
- **Parâmetros:** porta `ParametrosDoDossie` na aplicação, implementada em `config` com variáveis de ambiente (`DOSSIE_TTL_MINUTOS` padrão 30, `DOSSIE_THREADS` padrão 2, `DOWNLOAD_TIMEOUT_SEGUNDOS`, sumário/marcadores/numeração ligados). A S5 troca a implementação.
- **Fila e estado em memória**, executor limitado, `@Scheduled` de limpeza a cada 5 min (D22, `agendamento.md`): remove pedidos e arquivos expirados. O arquivo é apagado após o download (`attachment`) concluído ou ao expirar (RNF05); na subida, esvazie o diretório (`VISTAS_DOSSIE_DIRETORIO`, volume `dossies-tmp`), pois a fila em memória se perde.
- **Nenhuma conexão do SIGA aberta durante download e montagem** (critério de aceite). O pedido só carrega o instantâneo em memória.
- **Capa e módulos:** se o módulo `dossie` precisar dos dados do requerimento (capa), use o caso de uso público de `consulta` conforme `modulos-e-dependencias.md`; se a dependência não estiver prevista, atualize o documento e o `ArchitectureTest` e registre em `docs/09`.
- **Auditoria** é da S5: não implemente `RegistrarEvento` agora.

## B4. Front-end (`front/`)

Sem dependências novas de produção. Substitui o aviso "disponível na Sprint 2" na seção Documentos do detalhe.

- **Seção Documentos** (`features/detalhe`, subcomponentes): tabela com seleção, documento (título, id e nome do arquivo), origem (descrição), data e sigilo. **Busca por texto, filtro por origem, ordenação por documento, origem e data** (padrão: data crescente), seleção individual e em massa, contador "N selecionados", ação "Seleção padrão" (pré-seleção do domínio). Indisponíveis esmaecidos, sem seleção, com "arquivo indisponível" junto ao nome. Não PDF: bloqueados, com "conversão disponível em breve" e um aviso no topo da seção ("N documentos não são PDF e entram no dossiê a partir da próxima etapa"). Sigilosos sem perfil: visíveis (nome mascarado), sem seleção e com "documento sigiloso". Sem ação "Visualizar" (S3).
- **Barra de seleção** e ação **Gerar dossiê** (habilitada com pelo menos 1 documento).
- **Gaveta de geração** (reproduza `drawer` do protótipo, usando `FolhaSobreposta` se couber): progresso `processados/total` e a situação de cada documento (`itens`); **polling** com intervalo crescente e parada ao chegar em estado final ou ao sair da tela (sem vazamento de subscription); estados: pendente, processando, concluído, **interrompido** (documento e motivo, "nenhum arquivo foi gerado", botões **Tentar novamente** e **Remover documento e gerar**), cancelado e expirado; token recusado com a mensagem "Capture um novo token" (sem o valor).
- Ao concluir: **Pré-visualizar** (o PDF é buscado pelo `HttpClient` com o token, vira `Blob` e abre em modal ou nova aba por URL de objeto, revogada ao fechar; **nunca** coloque o token na URL), **Baixar** (também por `Blob`, com o nome do `Content-Disposition`) e o tempo restante até expirar.
- O quadro do topo continua estável (A1) durante tudo isso.
- Erros: mensagem inline ou na gaveta, sem toast duplicado; acrescente os textos novos ao mapa de mensagens (`DOCUMENTO_SIGILOSO`, `CREDENCIAL_ARQUIVOS_RECUSADA`, `DOSSIE_SEM_ARQUIVO`, `DOSSIE_EXPIRADO`).
- **Testes (Vitest):** filtro, busca, ordenação, pré-seleção, bloqueios, seleção em massa, gaveta nos estados (progresso, concluído, interrompido, token recusado, expirado), polling (parada e limpeza), pré-visualização e download por `Blob`, perfil Operador com sigiloso.

## B5. Testes do back-end

- `ArchitectureTest` verde, cobrindo `documento`, `dossie`, `integration.siga` e `integration.licenciamento` (HTTP só em `integration.licenciamento`; chave de arquivo não aparece fora do domínio e dos adapters).
- **Unitários de domínio:** situação (RN01), sigilo (todas as variações da normalização, incluindo valor desconhecido e nulo), deduplicação (RN05, inclusive chave `"0"`), ordem (RN03, sem data, empate), tipo de arquivo, compactados e geometrias fora da lista, seleção por padrão.
- **Casos de uso** com portas falsas: acesso (RN07, sigiloso), pertencimento dos documentos ao requerimento, rejeição de não PDF e de indisponível, instantâneo sem reconsulta.
- **Integração do adapter** (Testcontainers Oracle 23 Free, container compartilhado): recorte de cada origem **só com as colunas usadas**; vínculo correto, vigência, chave `"0"`, `DATA_EXCLUSAO` no parecer, data de assinatura, sigilo como `NUMBER` e como texto, documento duplicado entre origens, requerimento sem nenhum documento, origem desligada não consultada. Registre quantos testes rodaram e quantos foram pulados.
- **`integration.licenciamento`** com `HttpServer` local: `200` em streaming (arquivo grande sem estourar memória), `401`, `403`, `404`, `503`, timeout de leitura, modo `bearer` e modo `cookie` (cabeçalho correto, token nunca no log), chave `"0"` não requisitada, URL vazia.
- **Dossiê** com PDFs gerados pelo PDFBox nos testes: páginas de tamanhos e rotações diferentes preservadas, contagem de páginas, capa, sumário, marcadores, numeração, PDF com senha, PDF corrompido, PDF sem páginas, conteúdo que não é PDF, falha no meio (nenhum arquivo, temporários removidos, os seguintes não processados), `FALHA` com documento e motivo, expiração com relógio controlado, limpeza agendada, limite de paralelismo, cancelamento, 401 interrompendo tudo.
- **Conexão do SIGA liberada:** teste de integração que, durante o download simulado, confirma `getActiveConnections() == 0` no pool do SIGA.
- **`MockMvc`:** `POST /dossies` (202, 400, 403 `DOCUMENTO_SIGILOSO`, 403 `NAO_INTERESSADO`, 404), `GET /dossies/{id}` (cada status; outro usuário: 404), `GET .../arquivo` (200 `inline` e `attachment`, nome do RF18, 409, 410), `DELETE`, sem token (401), `ProblemDetail`, `errorCode` e `X-Correlation-Id`. **Teste que percorre as respostas de documentos e dossiês e garante que a chave do arquivo e o token não aparecem em nenhuma delas.**
- Com `SIGA_DB_URL` e `LICENCIAMENTO_API_URL` vazias: a `api` sobe, `health` `UP`, lista e dossiê respondem 503 ou falha explícita, sem derrubar nada.
- **Teste de fumaça contra o SIGA real e contra a API de arquivos real** (desligados por padrão, ativados por variável de ambiente, só leitura): você escreve, **não executa**.

## B6. Roteiro de homologação

Crie `docs/homologacao/S2-roteiro-homologacao.md`, para o **responsável executar**, no mesmo formato dos anteriores (checklist, comando ou SQL exato, resultado esperado, `[ ] OK / [ ] Falhou: observação`):

1. **Grants:** script de referência para o DBA conceder `SELECT` nas tabelas de documentos (lista tirada do SQL realmente implementado), mais `SIGA2_OUTORGA.TITULO_LEGADO_ANEXO` só se for preciso, e a verificação com `SELECT COUNT(*) ... WHERE ROWNUM = 1` por tabela.
2. **Dicionário:** consulta a `ALL_TAB_COLUMNS` para cada tabela e coluna das 10 origens ligadas, e tabela "esperado × encontrado" (inclui os tipos das colunas de sigilo, para fechar P05).
3. **Duplicidade entre origens (P04):** consultas que contam chaves repetidas entre `DOCUMENTO_PROCESSO` e as demais, e o vínculo de `EXTRATO_PUBLICACAO` com o requerimento.
4. **Token (P08):** como capturar, onde colocar (`.env.local`, sem espaço no início da linha, nunca versionado), como testar a API de arquivos de fora do SIGA Vistas (comando que **não** imprime o token), e o que fazer ao expirar. Aviso: os acessos ficam registrados no nome de quem capturou.
5. **Conferência na tela:** para 3 requerimentos do `docs/casos-de-teste.md` (um deles que hoje falha na Vista do SIGA), tabela "Vista do SIGA × SIGA Vistas × Bate?" com: documentos listados, vigentes, indisponíveis, sigilosos, não PDF, e o resultado do dossiê.
6. **Geração real:** gerar o dossiê, abrir o PDF e conferir capa, sumário, marcadores, numeração, ordem cronológica, tamanho e rotação das páginas, tamanho do arquivo e tempo; anotar em `docs/casos-de-teste.md`.
7. **Falhas:** token vazio ou expirado (mensagem de token), `DOWNLOAD_TIMEOUT_SEGUNDOS=1` (falha por tempo limite sem arquivo, depois "Tentar novamente"), e documento removido por "Remover documento e gerar".
8. **Acesso:** Operador com o CPF/CNPJ ajustado localmente para o de um interessado real (sem commit) lista só os dele e não seleciona sigilosos; Gestor e Administrador veem tudo.
9. **Quadro do topo:** as medições de A1 (se não houve teste e2e).
10. **Registro do resultado** na seção "Validação da S2" de `docs/14`, criada como **pendente**, e a abertura de pendência em `docs/10` para o que não bater.

## Documentação (atualizar durante a sprint)

| Arquivo | O que atualizar |
|---|---|
| `CLAUDE.md` | Regras de segurança (início do prompt), estado do repositório (S2), dados pessoais |
| `README.md` | Seção do dossiê (como gerar, onde colocar o token, `compose.verificacao.yaml`), problemas comuns, tutorial de uso da lista de documentos |
| `docs/02-requisitos.md` | RF06 reescrito; sigilo antecipado (RF20) |
| `docs/04-modelo-de-dados.md` | Uma consulta por origem, situação NÃO CONFIRMADO por origem, tipos confirmados da S1.1, privilégios |
| `docs/05-integracoes.md` | Modos `bearer` e `cookie`, variáveis, erros, URL vazia |
| `docs/06-geracao-do-dossie.md` | Pipeline sem conversão na S2, falhas, sumário determinístico, expiração |
| `docs/07-api.md` | Documentos (objeto com `itens` e `totais`, `id` em texto, bloqueios) e Dossiê (`itens`, 409, 410, 404 de outro usuário) |
| `docs/08-telas.md` | Seção Documentos, gaveta, quadro estável, objeto do requerimento (A2), recentes (A3) |
| `docs/09-decisoes.md` | Verificação sem sistemas externos; uma consulta por origem; sigilo antecipado na S2; Playwright só em desenvolvimento; `id` de documento em texto |
| `docs/10-pendencias-e-riscos.md` | P04, P05, P08, P09, P19, P20, R17, R18 e os riscos de tempo de geração e de token |
| `docs/14-plano-de-sprints.md` | Entrega da S2, antecipação do sigilo, "Validação da S1.1" preenchida e "Validação da S2" pendente |
| `docs/13-padroes-tecnicos.md` e `docs/padroes/` (+ espelho em `.claude/skills/vistas-banco/references/`) | Compose de verificação, variáveis novas, PDFBox em disco, exceção do Playwright |
| `docs/casos-de-teste.md` e `docs/README.md` | Modelo de casos de teste e links para os roteiros |

## Verificação

Rode e reporte:

```bash
cd api && ./mvnw -Dtest=ArchitectureTest test && ./mvnw clean verify && cd ..
npm --prefix front ci
npm --prefix front run build
npm --prefix front test -- --watch=false
npm --prefix front run e2e        # se o Playwright foi instalado (A1)
docker compose --env-file .env.local.example -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml config --services
git diff --check
```

Para subir os containers, **só** com o `compose.verificacao.yaml` e **só depois** de confirmar dentro do container que as variáveis estão vazias (regra 4 do início):

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d --build
```

Com os containers no ar: `health` `UP`; o front responde; os três perfis entram; lista de documentos, busca e `POST /dossies` respondem de forma explícita (503 ou falha com motivo "não configurada"); o quadro do topo permanece estável nos estados de erro. Se o Docker não estiver rodando, **diga isso** e não declare o passo concluído. Ao final, `docker compose ... down` (nunca `down -v`).

**Validação com dados reais não é feita por você.** Fica pendente no roteiro da S2.

## Relatório final

1. Dependências acrescentadas (inclusive `@playwright/test`, se houver) e tamanho das imagens se mudou.
2. Árvore de pastas criada ou alterada (até 4 níveis).
3. Resultado de cada comando, com a contagem de testes executados e pulados (back, Testcontainers, front e e2e).
4. **Confirmação, com a saída das checagens do item 4 das regras de segurança, de que nenhuma conexão a sistema externo real foi feita.**
5. A1: causa real do quadro, a correção e as medições (ou o motivo de não ter sido possível medir).
6. Resultado da varredura de dados pessoais (A4), sem repetir os valores.
7. Contrato final dos endpoints de documentos e dossiê, com JSON de exemplo.
8. Origens implementadas, desligadas por padrão e marcadas NÃO CONFIRMADO.
9. Desvios em relação a este prompt e conflitos encontrados.
10. Documentação atualizada e pendências que sobraram.
11. Riscos para a S3 (conversão) e para a S4.
