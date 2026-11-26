# 12 — Configurações

Tela acessível apenas ao perfil Administrador. Toda alteração gera evento na auditoria de acessos ([11](11-auditoria.md)).

## Perfis e permissões

| Permissão | Operador | Gestor | Analista (S11) | Administrador |
|---|:---:|:---:|:---:|:---:|
| Consultar e visualizar documentos | Só como interessado | ✓ | ✓ | ✓ |
| Ver e incluir documentos sigilosos | — | ✓ | — | ✓ |
| Gerar e baixar dossiê | Só como interessado | ✓ | ✓ | ✓ |
| Auditoria de documentos | Só como interessado | ✓ | — (403, bloco 10 da S11) | ✓ |
| Auditoria de acessos | — | — | — | ✓ |
| Configurações | — | — | — | ✓ |
| Painel do Gestor (`verPainel`) | — | ✓ | — | ✓ |
| Caixa de entrada (`verCaixa`) | — | — | ✓ | — |

"Só como interessado": processos e requerimentos em que o CPF/CNPJ do usuário é o do interessado (RN07). O Administrador tem acesso irrestrito. **Analista (D96):** só tem a permissão `verCaixa`; não é restrito ao interessado (abre os requerimentos da própria caixa), não vê sigilosos nem o responsável da tarefa. Gestor e Administrador **não** têm caixa: levam `403 PERFIL_SEM_PERMISSAO`. O CPF do Analista é o vínculo com o Camunda, opcional no cadastro, e nunca sai em resposta (nem mascarado, nem em `/auth/me`).

**Regras aplicadas no back-end (S4, RNF10)**, todas decididas pela `PoliticaDeAcesso` (D60):

| Regra | Operador | Gestor | Administrador | Resposta quando nega |
|---|:---:|:---:|:---:|---|
| Requerimento ou processo de outro interessado | — | ✓ | ✓ | 403 `NAO_INTERESSADO` (inexistente: 404 `NAO_ENCONTRADO`) |
| Documento sigiloso: ver título, abrir no modal, incluir no dossiê | — | ✓ | ✓ | 403 `DOCUMENTO_SIGILOSO`; na lista, "Documento sigiloso" sem nome de arquivo nem extensão |
| Nome do responsável pela tarefa atual (RN12) | — | ✓ | ✓ | campo omitido |
| Pedido de dossiê criado pelo próprio usuário | ✓ (enquanto for o interessado gravado no pedido e o pedido não tiver documento sigiloso) | ✓ | ✓ | 404 `NAO_ENCONTRADO` |
| Eventos de documentos na auditoria | só dos requerimentos em que é o interessado, de qualquer usuário, com sigiloso mascarado | todos | todos | evento alheio: 404 `NAO_ENCONTRADO`; `somenteSigilosos`: 400 `PARAMETRO_INVALIDO` |
| Eventos de acessos na auditoria e todas as configurações | — | — | ✓ | 403 `PERFIL_SEM_PERMISSAO` |
| Pedido de dossiê de outro usuário | — | ✓ | ✓ | 404 `NAO_ENCONTRADO` (não revela que existe) |
| Ver pedidos de outros usuários e "quem gerou" em "Dossiês gerados" | — | ✓ | ✓ | só os próprios; o campo `geradoPor` não existe |
| Geometria sigilosa (lista e ZIP) | — | ✓ | ✓ | omitida, só a contagem `geometriasOmitidas` |

A matriz completa, endpoint por endpoint, está em [07 — API](07-api.md#matriz-de-acesso-por-endpoint).

**Testado (S4, bloco 9):** `MatrizDeAcessoTest` prova a matriz por MockMvc em todos os endpoints e varre o corpo e os cabeçalhos de todas as respostas: nunca aparecem chave de arquivo, token da API de arquivos ou do SCORP, URL do SCORP, identificador da pessoa no SCORP nem o caminho do diretório temporário; para o Operador, também nunca aparecem título ou nome de arquivo de documento sigiloso (inclusive o pessoal e a geometria), documento ou nome de outro interessado, nem o responsável pela tarefa (RN12). O texto do PDF do Operador é extraído e conferido (sumário e "Documentos não incluídos" mascarados). O `instance`/`path` do `ProblemDetail` ecoa a URL que o próprio cliente pediu e não conta como vazamento.

**Testado (S5, bloco 11):** a matriz cobre `/auditoria/**` e `/configuracoes/**` por perfil, vínculo e sigilo. `VarreduraDaAuditoriaHttpTest` injeta token, cookie, URL, caminho e CPF/CNPJ no motivo de uma falha, no título e no nome de um documento e varre lista, detalhe de todos os eventos, CSV, usuários, parâmetros e conexões. `AcessoRevogadoHttpTest` prova que usuário desativado ou rebaixado perde o acesso na requisição seguinte, com sessão aberta, com ticket já emitido e com o pedido que ele mesmo gerou (D70). `ImutabilidadeDaAuditoriaTest` prova que nenhuma rota nem método público altera ou apaga evento.

Códigos: `OPERADOR`, `OPERADOR_SIGILO`, `ANALISTA`, `ADMIN`. Na versão definitiva, a identidade vem da autenticação institucional; o perfil é atribuído nesta tela. Itens de menu sem permissão não são exibidos (RN08).

## Abas

### Usuários e perfis

- Lista com nome, login, perfil, situação (ativo/inativo) e último acesso.
- Perfil e situação são alterados direto na lista e gravados na hora (evento `USUARIO`).
- Inclusão por gaveta lateral: nome, login, CPF/CNPJ e perfil. Login é único; CPF/CNPJ é obrigatório para o Operador.
- Usuário inativo não entra no sistema; o histórico de auditoria é mantido.
- **Regras no back-end (S5, bloco 7, D68):** login de 3 a 60 caracteres (letras, números, ponto, hífen ou sublinhado), único e normalizado; CPF/CNPJ conferido pelos dígitos verificadores, guardado só como dígitos e devolvido só mascarado; perfil `OPERADOR`, `OPERADOR_SIGILO`, `ANALISTA` ou `ADMIN` (o CPF/CNPJ é opcional para o Analista e, informado, é conferido; a lista não o devolve, nem mascarado). Não é possível desativar nem rebaixar o último Administrador ativo, nem desativar o próprio usuário. Mudar para Operador exige CPF/CNPJ já cadastrado: a alteração não recebe documento, então quem não o tem precisa ser incluído de novo com outro login.
- **Senha:** os usuários incluídos entram com a senha única do protótipo (`VISTAS_DEMO_SENHA`); não há autenticação nova (D09).
- **Memória:** incluídos e alterados somem ao reiniciar, e o serviço volta aos três usuários de demonstração. O último acesso também.

### Dossiê

| Parâmetro | Padrão | Faixa |
|---|---|---|
| Expiração do dossiê | 30 min | 5–240 min |
| Gerações simultâneas | 2 | 1–8 |
| Sumário | ligado | — |
| Marcadores no PDF | ligado | — |
| Numeração de páginas | ligado | — |
| Origens dos documentos | todas ligadas | uma chave por origem |

Origem desligada some da lista de documentos e não entra no dossiê. A regra de ordem cronológica é fixa (RN03) e só é exibida.

**No protótipo (S2)** os parâmetros vêm de variáveis de ambiente, atrás da porta `ParametrosDoDossie` (a S5 passou a ler a sobreposição da tela atrás da mesma porta; o ambiente continua sendo o padrão, D69): `DOSSIE_TTL_MINUTOS` (30), `DOSSIE_THREADS` (2), `DOSSIE_SUMARIO`, `DOSSIE_MARCADORES` e `DOSSIE_NUMERACAO` (ligados), `DOWNLOAD_TIMEOUT_SEGUNDOS` (inatividade da leitura; 120 desde a S2.2, antes 60), `DOWNLOAD_TOTAL_MAXIMO_SEGUNDOS` (teto total por documento, com as novas tentativas; 900), `DOSSIE_ESPACO_MINIMO_MB` (espaço livre mínimo no diretório dos dossiês, conferido no `POST /dossies`, que responde 507 sem criar pedido, e antes de cada download, que encerra o pedido em `FALHA`; **500** desde a S6, antes 2048; `0` desliga; S6, bloco 3), `DOSSIE_BLOB_MAX_MB` (acima disto o PDF final é baixado direto pelo navegador, com ticket de uso único e sem pré-visualização; 150; D49), `DOSSIE_HISTORICO_HORAS` (S4: horas que um pedido expirado, baixado ou cancelado continua em "Dossiês gerados" depois do vencimento, sem arquivo; 24; em memória, some num reinício) e `VISTAS_DOSSIE_DIRETORIO`. Detalhes de tempo e novas tentativas em docs/05.

**Dimensionamento do volume `dossies-tmp` (S2.2).** Cada pedido guarda no volume os documentos obtidos e, ao final, o PDF montado (os documentos são apagados depois da montagem). Regra prática por pedido: **soma dos documentos + o PDF final** (o final é quase a soma, então ≈ 2 × a soma). Some os pedidos que podem estar em andamento ao mesmo tempo (`DOSSIE_THREADS`) e os concluídos que ainda não expiraram (`DOSSIE_TTL_MINUTOS`), e acrescente `DOSSIE_ESPACO_MINIMO_MB` de folga. Exemplo: arquivos de ~50 MB, dossiê de 40 documentos ≈ 2 GB de documentos + 2 GB de PDF final ≈ 4 GB por pedido; com 2 threads, ≈ 8 GB + 2 GB de folga = **10 GB**. No Compose local o volume usa o disco do Docker, sem cota; em homologação/produção o tamanho do volume é **NÃO CONFIRMADO** (depende da infraestrutura, docs/13). A medição real é a da homologação (docs/casos-de-teste.md).

**Memória (S2.2).** A montagem usa cache de fluxo em arquivo temporário (no diretório temporário da JVM, `java.io.tmpdir`, não no volume `dossies-tmp`) e não carrega os documentos em memória. Na medição com 20 PDFs de ~48 MB (docs/10, R26), o pico de heap foi de 128 MB com `-Xmx1024m` e de 138 MB com `-Xmx256m`. O container usa `-XX:MaxRAMPercentage=75`: com um limite de 1 GB, o heap fica em ~768 MB, folgado para `DOSSIE_THREADS=2`. Os temporários do PDFBox no `java.io.tmpdir` devem ser pequenos, porque guardam só o conteúdo novo (capa, sumário, seção) e o conteúdo dos documentos é copiado direto dos arquivos do pedido para o PDF final. O tamanho deles **não foi medido**; confira na homologação. Para repetir a medição: `./mvnw -Dtestes.excluidos= -Dgroups=desempenho -Dtest=DesempenhoDaMontagemTest -DargLine=-Xmx1024m test` (gera ~2 GB em disco e os apaga ao fim). As origens são ligadas por `vistas.documentos.origens.<origem>` (`true`/`false`; `EXTRATO_PUBLICACAO` vem desligada, P04).

### Conversão

| Parâmetro | Padrão |
|---|---|
| Conversão habilitada | ligada (chave de segurança; desligada, só PDF entra, como na S2.2) |
| Planilhas | Ajustar à largura (alternativa: uma página por aba) |
| Geometrias (SHP, KML, KMZ) | Fora do dossiê, download à parte em ZIP (tudo ou nada) |
| Compactados (ZIP, RAR, 7Z) | Desconsiderados |
| Orientação automática | ligado |
| Incluir abas ocultas | desligado |
| Tempo limite de conversão | 300 s |
| Instâncias do conversor | 2 |
| Tarefas por instância antes de reiniciar | 50 |
| Imagem: máximo de megapixels | 100 |
| Planilha: escala mínima no ajuste à largura (abaixo, a aba vai para página única se ela couber no limite do LibreOffice; senão, é paginada na largura, R41) | 40% |
| Máximo de páginas de uma conversão | 2000 |
| Tamanho máximo do PDF de uma conversão | 500 MB |

Variáveis de ambiente do protótipo (todas opcionais; `.env.local.example`): `CONVERSAO_HABILITADA` (`true`), `CONVERSAO_TIMEOUT_SEGUNDOS` (300), `CONVERSAO_PROCESSOS` (2), `CONVERSAO_TAREFAS_POR_PROCESSO` (50), `CONVERSAO_PLANILHA_MODO` (`LARGURA` ou `PAGINA_POR_ABA`; valor desconhecido cai em `LARGURA`), `CONVERSAO_ABAS_OCULTAS` (`false`), `CONVERSAO_IMAGEM_MAX_MEGAPIXELS` (100), `CONVERSAO_PLANILHA_ESCALA_MINIMA` (40, em %), `CONVERSAO_PAGINAS_MAXIMAS` (2000) e `CONVERSAO_SAIDA_MAX_MB` (500). Técnica, fora da tela de configurações: `CONVERSAO_OFFICE_HOME` (pasta do LibreOffice, a que contém `program/soffice`; vazia = caminhos padrão). Os valores padrão serão justificados pela medição de desempenho da S3 (bloco 9).

**Por que estes valores (medição do bloco 9, docs/10):** `CONVERSAO_TIMEOUT_SEGUNDOS=300` fica muito acima do medido (a maior conversão levou 16,3 s; DOCX de 200 páginas, 1,3 s; JPG de 20 MP, 0,3 s) e protege contra documento que trava; só deve cair se a homologação com arquivos reais mostrar que nada passa de 1 minuto. `CONVERSAO_PROCESSOS=2` combina com `DOSSIE_THREADS=2` (cada geração usa uma instância); cada instância pesou ~300 a 350 MB de memória residente, então o valor é limitado pela memória do container, não pelo tempo. `CONVERSAO_TAREFAS_POR_PROCESSO=50` reinicia o processo antes que ele acumule memória: não foi medido o crescimento em centenas de tarefas, o valor segue o do prompt. `CONVERSAO_IMAGEM_MAX_MEGAPIXELS=100` deixa passar um JPG de 20 MP com 162 MB de heap (21% de 768 MB); uma imagem de 100 MP decodificada pesa ~400 MB, perto do limite de 70% de um heap de 768 MB (R42). **Memória do container:** com o conversor, o limite de 1 GB suposto na S2.2 não basta (heap + `soffice` ~700 MB); veja R42. **Planilhas grandes:** `CONVERSAO_PLANILHA_ESCALA_MINIMA=40` e a página única já cobrem a planilha de 30 mil linhas sem perder conteúdo (R41, resolvido: 177 páginas em 16 s, última linha conferida); os valores finais de planilha dependem da escolha entre `LARGURA` e `PAGINA_POR_ABA` na homologação (D58).

### Visualização (modal)

| Parâmetro | Padrão |
|---|---|
| Tempo de vida do cache da versão em PDF | 10 min |
| Teto de espaço do cache (acima, saem os mais antigos) | 500 MB |
| Conversões do modal em paralelo | 2 |
| Conversões do modal à espera (fila cheia: 429) | 4 |

**SCORP (S4):** `SCORP_API_URL` (vazia = desligado), `SCORP_API_TOKEN_FILE`, `SCORP_API_AUTH_MODE` e `SCORP_API_COOKIE_NAME` (opcionais; sem elas, valem as da API de arquivos), `SCORP_TIMEOUT_SEGUNDOS` (15), `SCORP_CHAMADAS_SIMULTANEAS` (4) e `SCORP_SIGILO_PADRAO` (`false`; `true` trata todos os documentos pessoais como sigilosos; decisão pendente, P30). A origem também pode ser desligada por `vistas.documentos.origens.scorp=false`. Detalhes em docs/05.

**Geometrias (S4):** `GEOMETRIAS_MAX_MB` (500; soma dos arquivos de um ZIP) e `GEOMETRIAS_MAX_ARQUIVOS` (200); acima, 413 `GEOMETRIAS_GRANDES_DEMAIS`. Usam `DOSSIE_ESPACO_MINIMO_MB`, os tempos de download do dossiê e a pasta `VISTAS_DOSSIE_DIRETORIO/geometrias` (cada download apaga a sua; o pico de espaço é perto de uma vez a soma dos arquivos).

Variáveis de ambiente do protótipo: `VISUALIZACAO_TTL_MINUTOS` (10), `VISUALIZACAO_CACHE_MAX_MB` (500), `VISUALIZACAO_CONVERSOES_SIMULTANEAS` (2) e `VISUALIZACAO_FILA_CONVERSOES` (4). O cache fica em `VISTAS_DOSSIE_DIRETORIO/visualizacao`. A conversão usa os parâmetros de Conversão acima, os mesmos do dossiê; as conversões do modal disputam as mesmas instâncias do LibreOffice que a geração (S3, bloco 6).

### Conexões

Estado e teste de conexão de: banco do SIGA (somente leitura), arquivos do Licenciamento (com horas até expirar o token), SCORP, taxas (desativada até a forma de obtenção ser confirmada) e, desde a S7 (bloco 10), **fluxo (Camunda)**. Tempo limite de download: 120 s (10–300).

**Teste do Camunda (S7, bloco 10), separado do banco do SIGA e do Licenciamento** (`integration.siga.VerificadorDoCamunda`, nome `camunda`): pelo mesmo datasource somente leitura, lê uma linha de `SIGA2_CAMUNDA.ACT_RE_PROCDEF` trazendo só a constante 1 (`sql/siga/verificacao-camunda.sql`), **sem nenhum dado**. Prova o que o teste do banco do SIGA (`SELECT 1 FROM DUAL`) não prova: o grant de `SELECT` nas tabelas do Camunda. Tempo limite = `RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS` (o das consultas da posição). Diagnóstico por causa, sem a mensagem do driver: **OK** ("Leitura das tabelas do fluxo estabelecida."); **sem acesso** (ORA-00942/01031: "o usuário de leitura não tem acesso às tabelas do fluxo; peça ao DBA o SELECT no SIGA2_CAMUNDA"); **tempo limite excedido**; **indisponível**. É JDBC, sem HTTP: os falsos alarmes conhecidos das conexões HTTP (503 isolado, 400 à chave fictícia, os antigos 5 s) não se aplicam a ele, e os testes da API de arquivos e do SCORP (S6) não mudaram.

Credenciais ficam no arquivo de ambiente do servidor; a tela não as exibe nem edita.

**Testes (S5, bloco 8; S6, bloco 4):** banco do SIGA, `SELECT 1 FROM DUAL` pelo datasource somente leitura (`sql/siga/verificacao.sql`); API de arquivos, `GET` numa chave fictícia, sem ler o corpo; SCORP, `GET` para uma pessoa fictícia. Desde a S6 as duas APIs usam **o mesmo cliente, a mesma base de URL e a mesma autenticação do consumo real**, e o resultado é **classificado**: **OK** (2xx), **Alcançável com ressalva** (4xx esperado para a chamada de teste, ex.: 400/404, com o código e a explicação), **Falha de autenticação** (401/403 ou token vencido: renovar o token) e **Falha** (DNS, recusa, TLS, tempo esgotado, 5xx persistente; um 503 isolado ganha uma nova tentativa). Como ler cada um: docs/05, "Teste de conexão". Só a data de expiração do token é lida, sem validar a assinatura; token vencido nem é enviado.

**`CONEXAO_TESTE_TIMEOUT_SEGUNDOS`** (S6, padrão **15**; antes 5 s fixos): tempo limite de conexão e de leitura de cada chamada do teste, mostrado na aba antes de testar e no resultado ("120 ms (limite 15 s)"). O teste inteiro tem teto de 2 × o valor + 5 s (duas chamadas e a espera). Variável de ambiente, fora da tela.

**`RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS`** (S7, bloco 3, padrão **10**; propriedade `rastro.tramitacao.camunda-timeout-segundos`): tempo limite de **cada** consulta da posição atual ao Camunda (até 7 por requerimento, docs/15). Estourado, a posição responde `INDISPONIVEL` e o resto do detalhe segue. Vale só para a tramitação; as demais consultas do SIGA continuam só com `SIGA_DB_READ_TIMEOUT_MS`. Desde o bloco 10, editável em Configurações › SIGRASTRO (ver abaixo).

**Responsável e cache da posição** (S7, bloco 4; propriedades `rastro.tramitacao.*`, lidas a cada consulta, classe "imediato"; docs/15, "Responsável"):

| Variável | Padrão | Faixa | Efeito |
|---|---|---|---|
| `RASTRO_MOSTRAR_NOME_RESPONSAVEL` | `true` (protótipo) | `true`/`false` | desligado, só a área e o papel, sem nome do servidor (nunca o CPF, em nenhum caso). Vale também na tramitação completa (S8): passagens, participantes da multi-instância, signatários da emissão e, portanto, a linha do tempo e o fluxograma, que se desenham a partir das passagens; aplicado depois do cache (próxima leitura). Provado pela varredura `VarreduraSemNomeDoResponsavelHttpTest` (S8, bloco 11, todos os cenários e perfis). Regra definitiva depende da SEMA (P32) |
| `RASTRO_MAX_NOMES_POR_EQUIPE` | 10 | 1 a 100 | participantes listados numa equipe; o resto aparece como "e mais N" |
| `RASTRO_POSICAO_CACHE_SEGUNDOS` | 30 | 0 a 3600 | validade do cache da posição em memória; 0 desliga |

Valor fora da faixa cai no padrão, com aviso no log.

**Desempenho da tramitação** (S8, blocos 2 e 7; propriedades `rastro.tramitacao.*`; docs/15, "Desempenho, limites, cache e degradação"). **Só do ambiente**: valem a partir da subida e não estão na tela de Configurações (decisão do bloco 7: são proteções do Camunda de produção, ajustadas por quem opera o servidor, não pela tela).

| Variável | Padrão | Faixa | Efeito |
|---|---|---|---|
| `RASTRO_TRAMITACAO_LIMITE_LINHAS` | 3000 | — | atividades do histórico lidas **por instância**, contadas depois do colapso das repetições; passando disso, a instância vem truncada (as primeiras) e sai o aviso `HISTORICO_TRUNCADO` |
| `RASTRO_TRAMITACAO_CACHE_SEGUNDOS` | 60 | 0 a 3600 | validade da tramitação em cache (chave = requerimento + versão do catálogo); 0 desliga. `INDISPONIVEL` nunca entra |
| `RASTRO_TRAMITACAO_TIMEOUT_SEGUNDOS` | 20 | 1 a 120 | tempo **total** de uma leitura da tramitação, contando a espera por vaga; estourado, `INDISPONIVEL` |
| `RASTRO_CAMUNDA_LEITURAS_SIMULTANEAS` | 4 | 1 a 20 | leituras ao Camunda ao mesmo tempo nesta instância do SIGRASTRO, **somando posição e tramitação**; as demais esperam a vez (a posição, até o tempo limite de uma consulta) |

O tempo limite de **cada** consulta continua sendo `RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS`.

**Leitura do financeiro** (S9, bloco 2; propriedades `rastro.financeiro.*`; `config.ParametrosDoFinanceiroDoAmbiente`). O padrão vem do ambiente (valor fora da faixa cai no padrão, com aviso no log); **desde o bloco 10 da S9 os três parâmetros do financeiro são editáveis na aba SIGRASTRO** (ver "Financeiro na aba SIGRASTRO", abaixo).

| Variável | Padrão | Faixa | Efeito |
|---|---|---|---|
| `RASTRO_FINANCEIRO_LIMITE_LANCAMENTOS` | 200 | 1 a 1000 | lançamentos de taxa lidos **por requerimento** (em ordem de emissão); passando disso, os mais recentes ficam de fora e sai o aviso `LANCAMENTOS_ALEM_DO_LIMITE` |
| `RASTRO_FINANCEIRO_TIMEOUT_SEGUNDOS` | 10 | 1 a 120 | tempo limite de **cada** consulta a `LANCAMENTO_TAXA` (uma por requerimento; no lote, uma a cada 500), aplicado em cada `Statement` (`LancamentosDoSigaJdbc`), separado do da tramitação |
| `RASTRO_MOSTRAR_VALORES` (`rastro.financeiro.mostrar-valores`) | `true` | `true`/`false` | **valor, desconto e UPF** nas respostas do financeiro (S9, bloco 6), lido a cada consulta e válido para todos os perfis; desligado, os três **não existem** em nenhuma resposta e o número do DAR continua. Dinheiro público de pessoas e empresas: a regra definitiva depende da SEMA (docs/10). Editável na aba Rastro desde o bloco 10 (efeito imediato, ver "Financeiro na aba Rastro"). **Bloco 7:** a variável passou a ser ligada à propriedade no `application.yml` (antes só a propriedade funcionava; a variável era ignorada), e a negação é provada pela variável em todos os cenários e perfis (`VarreduraSemValoresHttpTest`) |

### Aba Rastro (S7, bloco 10)

Aba **Rastro** das Configurações (só Administrador; RN08, `PERFIL_SEM_PERMISSAO` para os demais), com os parâmetros da tramitação. Como nas demais abas, editar não grava: as alterações saem no "Salvar alterações", num **único evento `CONFIG` com antes e depois**. O padrão vem da variável de ambiente; a tela sobrepõe (em memória, some ao reiniciar). **Credenciais não aparecem** na aba.

| Parâmetro (nome na API) | Variável de ambiente (padrão) | Faixa | Classe | Efeito |
|---|---|---|---|---|
| Mostrar o nome do responsável (`rastro.mostrarNomeResponsavel`) | `RASTRO_MOSTRAR_NOME_RESPONSAVEL` (`true`) | `true`/`false` | imediato | desligado, só área e papel (P32; o Operador nunca vê o nome, RN12) |
| Validade do cache da posição (`rastro.posicaoCacheSegundos`) | `RASTRO_POSICAO_CACHE_SEGUNDOS` (30) | 0 a 3600 | imediato | 0 desliga |
| Máximo de nomes por equipe (`rastro.maxNomesPorEquipe`) | `RASTRO_MAX_NOMES_POR_EQUIPE` (10) | 1 a 100 | imediato | o resto vira "e mais N" |
| Posições consultadas ao mesmo tempo (`rastro.posicoesSimultaneas`) | `RASTRO_POSICOES_SIMULTANEAS` (4) | 1 a 20 | imediato (no front: ao abrir o SIGRASTRO de novo) | limite da fila de posições da tela (busca e processo) |
| Tempo limite das consultas ao fluxo (`rastro.camundaTimeoutSegundos`) | `RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS` (10) | 1 a 120 | imediato (próxima leitura) | cada consulta da posição ao Camunda e o teste de conexão do Camunda |

- **"Imediato" vale na próxima leitura:** o back-end lê cada valor a cada consulta (`ParametrosDoRastroDoAmbiente` sobre `ParametrosEmVigor`); o tempo limite do Camunda é aplicado em cada `Statement` (`CamundaPosicaoJdbc`), sem reiniciar.
- **Posições simultâneas no front:** a tela lê o valor em `GET /api/tramitacao/parametros` (qualquer perfil autenticado; só esse número) no primeiro pedido de posição de cada carregamento da página. Até a resposta, a fila libera **um pedido por vez** (um limite menor nunca é ultrapassado); se a leitura falhar, usa 4.

### Financeiro na aba Rastro (S9, bloco 10)

Os parâmetros do financeiro entram na aba **Rastro** (só Administrador; Operador e Gestor recebem 403 `PERFIL_SEM_PERMISSAO`, e a tela os leva de volta à consulta), com o mesmo comportamento da tramitação: editar não grava, o "Salvar alterações" envia **um único evento `CONFIG` com antes e depois**, o padrão vem da variável de ambiente e a tela sobrepõe (em memória, some ao reiniciar). **Credenciais não aparecem.**

| Parâmetro (nome na API) | Variável de ambiente (padrão) | Faixa | Classe | Efeito |
|---|---|---|---|---|
| Mostrar valores das taxas (valor, desconto e UPF) (`rastro.mostrarValores`) | `RASTRO_MOSTRAR_VALORES` (`true`) | `true`/`false` | imediato | desligado, valor, desconto e UPF **não existem** em nenhuma resposta nem tela, para nenhum perfil; o DAR continua (dinheiro público de pessoas e empresas: regra definitiva da SEMA, docs/10) |
| Limite de lançamentos de taxa por requerimento (`rastro.financeiroLimiteLancamentos`) | `RASTRO_FINANCEIRO_LIMITE_LANCAMENTOS` (200) | 1 a 1000 | imediato (próxima leitura) | passando disso, os mais recentes ficam de fora e sai o aviso `LANCAMENTOS_ALEM_DO_LIMITE` |
| Tempo limite da consulta financeira (`rastro.financeiroTimeoutSegundos`) | `RASTRO_FINANCEIRO_TIMEOUT_SEGUNDOS` (10) | 1 a 120 | imediato (próxima leitura) | cada consulta a `LANCAMENTO_TAXA`, aplicado em cada `Statement` |

- **"Imediato" vale na próxima leitura:** `ParametrosDoFinanceiroDoAmbiente` lê cada valor a cada consulta (sobre `ParametrosEmVigor`), então nada exige reiniciar. Os valores **antigos já em cache não existem** (o financeiro não tem cache): a próxima resposta já sai com o valor novo.
- **Diagnóstico do financeiro** (somente leitura, na mesma aba, abaixo dos parâmetros): o Administrador informa o identificador de um requerimento (o número do endereço da tela de detalhe) e vê **quantos lançamentos a regra de validade ignorou, por motivo** (`FALSO_SEM_DAR`, `RUIDO_ISENTO_AGUARDANDO`), quantos foram mantidos (e quantos com dados incompletos) e **de onde veio a data de cada "Isento de Taxa"** (`LANCAMENTO`, `FLUXO` ou `AUSENTE`; com a nota "não processado" quando `STATUS_PROCESSADO = N`). **Só contagens e a origem da data:** nenhum dado pessoal, id de lançamento, DAR nem valor. Reaproveita `GET /requerimentos/{id}/financeiro` (que só entrega esses campos ao Administrador), então a consulta **gera o evento `FINANCEIRO`** na auditoria, como a de quem abre o requerimento.

**Resiliência e variáveis (S7, bloco 7).** O tempo de resposta da posição no pior caso é limitado por três variáveis já existentes: `SIGA_DB_CONN_TIMEOUT_MS` (5000; espera por uma conexão do pool, inclusive com o banco mudo), `RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS` (10; cada uma das até 8 consultas) e `SIGA_DB_READ_TIMEOUT_MS` (30000; leitura do socket, por baixo). Estourar qualquer uma delas devolve a posição `INDISPONIVEL` (200), nunca 5xx; o detalhe do requerimento não depende da posição. Com o desligamento do nome (`RASTRO_MOSTRAR_NOME_RESPONSAVEL=false`), nenhuma resposta da posição traz nome de servidor, provado por varredura em todos os cenários (docs/15).

**Posições simultâneas pedidas pela tela (S7, bloco 9): N = 4.** A coluna opcional "Etapa atual" da busca e os "Outros requerimentos deste processo" do detalhe pedem a posição de vários requerimentos; o front passa todos esses pedidos por uma fila (`FilaDePosicoes`) com **no máximo 4 em andamento** (`MAXIMO_DE_POSICOES_SIMULTANEAS` em `front/src/app/features/detalhe/fila-de-posicoes.service.ts`); os demais esperam a vez, e um pedido cancelado antes de começar não chega ao servidor. Só as linhas da página exibida (até 20) pedem posição, e a coluna vem desligada. Desde o bloco 10 o valor vem de Configurações › Rastro (`RASTRO_POSICOES_SIMULTANEAS`, padrão 4); a constante do front é só o padrão de reserva.

### Modo de demonstração e servidor MCP (S6, bloco 7)

Variáveis de ambiente, fora da tela:

| Variável | Onde | Padrão | O que faz |
|---|---|---|---|
| `RASTRO_MODO` | serviço (`api`) | `padrao` | `demonstracao` declara o modo em `GET /api/estado`, **só** se SIGA, API de arquivos e SCORP estiverem vazios. O `compose.verificacao.yaml` liga |
| `RASTRO_MCP_URL` | servidor MCP (ambiente do processo do Claude Code) | `http://localhost:8080` (porta do front no `compose.local.yaml`) | base do SIGRASTRO local; host diferente de `localhost`, `127.0.0.1` e `host.docker.internal` é recusado |
| `RASTRO_MCP_USUARIO` / `RASTRO_MCP_SENHA` | servidor MCP | sem padrão | usuário de demonstração; nunca lidos do `.env.local` |
| `RASTRO_MCP_TIMEOUT_MS` | servidor MCP | 10000 | tempo limite de cada pedido ao SIGRASTRO |

### Auditoria

Retenção por grupo: 1, 2, 5 ou 10 anos. Valores de exemplo no protótipo: documentos 5 anos, acessos 1 ano.

Variáveis de ambiente do protótipo (S5): `AUDITORIA_RETENCAO_DOCUMENTOS_ANOS` (5), `AUDITORIA_RETENCAO_ACESSOS_ANOS` (1) e `AUDITORIA_MAX_EVENTOS` (50.000; teto em memória somando os dois grupos, acima dele saem os mais antigos) e `AUDITORIA_CSV_MAX_LINHAS` (50.000; limite do CSV, com aviso no cabeçalho `X-Vistas-Truncado`). Valor fora da faixa cai no padrão, com aviso no log. A retenção roda no intervalo de `vistas.agendamento.limpeza-orfaos` (1 h) e `AGENDAMENTO_ENABLED=false` a desliga.

## Parâmetros editáveis e quando valem (S5, bloco 8)

Cada parâmetro tem uma **classe de aplicação** e uma faixa. O valor do ambiente continua sendo o padrão e a fonte ao reiniciar; a tela só guarda sobreposições em memória (D69). As faixas sem definição anterior foram propostas no bloco 8 (**a revisar na homologação**).

| Parâmetro (`nome`) | Variável | Faixa | Classe |
|---|---|---|---|
| `dossie.ttlMinutos` | `DOSSIE_TTL_MINUTOS` | 5 a 240 | próxima geração |
| `dossie.threads` | `DOSSIE_THREADS` | 1 a 8 | exige reinício |
| `dossie.sumario`, `dossie.marcadores`, `dossie.numeracao` | `DOSSIE_SUMARIO`, `DOSSIE_MARCADORES`, `DOSSIE_NUMERACAO` | ligado ou desligado | próxima geração |
| `dossie.origem.<origem>` (7 origens) | `vistas.documentos.origens.<origem>` | ligado ou desligado | imediato |
| `conexoes.downloadTimeoutSegundos` | `DOWNLOAD_TIMEOUT_SEGUNDOS` | 10 a 300 | próxima geração |
| `conversao.habilitada`, `conversao.abasOcultas` | `CONVERSAO_HABILITADA`, `CONVERSAO_ABAS_OCULTAS` | ligado ou desligado | próxima geração |
| `conversao.planilhaModo` | `CONVERSAO_PLANILHA_MODO` | `LARGURA` ou `PAGINA_POR_ABA` | próxima geração |
| `conversao.imagemMaxMegapixels` | `CONVERSAO_IMAGEM_MAX_MEGAPIXELS` | 1 a 500 | próxima geração |
| `conversao.planilhaEscalaMinima` | `CONVERSAO_PLANILHA_ESCALA_MINIMA` | 10 a 100 | próxima geração |
| `conversao.paginasMaximas` | `CONVERSAO_PAGINAS_MAXIMAS` | 1 a 10000 | próxima geração |
| `conversao.saidaMaxMb` | `CONVERSAO_SAIDA_MAX_MB` | 1 a 2000 | próxima geração |
| `conversao.timeoutSegundos` | `CONVERSAO_TIMEOUT_SEGUNDOS` | 10 a 1800 | exige reinício |
| `conversao.processos` | `CONVERSAO_PROCESSOS` | 1 a 8 | exige reinício |
| `conversao.tarefasPorProcesso` | `CONVERSAO_TAREFAS_POR_PROCESSO` | 1 a 500 | exige reinício |
| `visualizacao.ttlMinutos` | `VISUALIZACAO_TTL_MINUTOS` | 1 a 240 | imediato |
| `visualizacao.cacheMaxMb` | `VISUALIZACAO_CACHE_MAX_MB` | 10 a 10000 | imediato |
| `visualizacao.conversoesSimultaneas` | `VISUALIZACAO_CONVERSOES_SIMULTANEAS` | 1 a 8 | exige reinício |
| `visualizacao.filaDeConversoes` | `VISUALIZACAO_FILA_CONVERSOES` | 0 a 32 | exige reinício |
| `auditoria.retencaoDocumentosAnos`, `auditoria.retencaoAcessosAnos` | `AUDITORIA_RETENCAO_DOCUMENTOS_ANOS`, `AUDITORIA_RETENCAO_ACESSOS_ANOS` | 1, 2, 5 ou 10 | imediato |

- **Próxima geração:** o pedido guarda um retrato destes valores na criação e toda a geração roda com ele, inclusive a conversão, o download e uma continuação depois da decisão. Fora de um pedido (por exemplo, o modal), o valor novo vale na próxima leitura.
- **Exige reinício:** o valor é lido só na subida. Fica pendente e registrado no evento, mas o valor em vigor não muda. **Como as sobreposições somem ao reiniciar, para valer de fato é preciso alterar a variável de ambiente** (R56).
- **Fora da tela:** credenciais, URLs, diretórios, `SCORP_SIGILO_PADRAO` (decisão da área, P30), `DOSSIE_ESPACO_MINIMO_MB` e os demais limites técnicos. "Orientação automática" está na tabela de Conversão, mas não existe como parâmetro no código.

## Parâmetros do painel (S10, blocos 4 e 5)

Parâmetros `rastro.painel*` (classe **imediato**: lidos a cada ciclo de atualização, valem na próxima). O padrão vem do ambiente (`RASTRO_PAINEL_*`; fora da faixa cai no padrão, com aviso no log). **Desde o bloco 5** os cinco estão no `CatalogoDeParametros`, na seção **Rastro** das Configurações (só Administrador; Operador e Gestor recebem 403 `PERFIL_SEM_PERMISSAO`): editar gera **um único evento `CONFIG` com antes e depois**, e `ParametrosDoPainelDoAmbiente` lê o valor a cada ciclo (sobre `ParametrosEmVigor`). O ano aceita `0` ou 2000 a 2100 (1 a 1999 é recusado com 400).

| Parâmetro | Variável | Faixa | Padrão |
|---|---|---|---|
| `rastro.painelAno` | `RASTRO_PAINEL_ANO` | 0 (ano corrente em Cuiabá) ou 2000 a 2100 | 0 |
| `rastro.painelAtualizacaoMinutos` | `RASTRO_PAINEL_ATUALIZACAO_MINUTOS` | 5 a 1440 | 30 |
| `rastro.painelLimiteRequerimentos` | `RASTRO_PAINEL_LIMITE_REQUERIMENTOS` | 100 a 20000 | 3000 |
| `rastro.painelTimeoutSegundos` | `RASTRO_PAINEL_TIMEOUT_SEGUNDOS` | 10 a 300 | 60 |
| `rastro.painelIntervaloMinimoManualSegundos` | `RASTRO_PAINEL_INTERVALO_MINIMO_MANUAL_SEGUNDOS` | 30 a 3600 | 120 |

**Provas do bloco 6:** mudar `rastro.painelAno` (ex.: 2025) vale na **próxima atualização**, e o recorte do outro ano nunca aparece; desligar o nome do responsável tira todo nome do painel e da auditoria na hora (RN12). Ver `VarreduraDoPainelHttpTest` e `VarreduraDoPainelSemNomeHttpTest`.

**Texto de apoio na aba Rastro (S10, bloco 8):** os cinco campos aparecem com o rótulo "Painel: …", a faixa, o padrão do ambiente e a etiqueta "Vale já" (a tela é guiada pelo catálogo; nenhuma regra nova). Abaixo da lista, a tela explica cada um e diz que `0` no ano é o ano corrente em Cuiabá. Não há validação nova: a faixa e o erro continuam como nos demais parâmetros. Testes: `parametros-config.spec.ts` (campos do painel, faixas, etiqueta e texto de apoio) e `front/e2e/configuracoes-financeiro.e2e.ts` (aba Rastro).

## Parâmetros da caixa do analista (S11, bloco 6)

Parâmetros `rastro.caixa*` (classe **imediato**: lidos a cada ciclo, valem na próxima atualização), na seção **Rastro** das Configurações. O padrão vem do ambiente (`RASTRO_CAIXA_*`; fora da faixa cai no padrão, com aviso no log). O teto de requerimentos e o tempo limite da leitura são os do painel (`rastro.painelLimiteRequerimentos` e `rastro.painelTimeoutSegundos`): a caixa não cria parâmetro para eles.

| Parâmetro | Variável | Faixa | Padrão |
|---|---|---|---|
| `rastro.caixaAtualizacaoMinutos` | `RASTRO_CAIXA_ATUALIZACAO_MINUTOS` | 2 a 120 | 10 |
| `rastro.caixaIntervaloMinimoManualSegundos` | `RASTRO_CAIXA_INTERVALO_MINIMO_MANUAL_SEGUNDOS` | 15 a 3600 | 60 |

Os dois campos aparecem na aba Rastro com o rótulo "Caixa do analista: …" (a tela é guiada pelo catálogo; nenhuma regra nova no front). Prova: `ParametrosHttpTest` (padrão, classe imediato, faixa).

## Salvamento

- Parâmetros alterados ficam pendentes numa barra fixa com a contagem de alterações, "Descartar" e "Salvar alterações".
- O salvamento gera um único evento `CONFIG` com a lista de mudanças (antes e depois).

## Persistência na versão definitiva

No schema próprio: tabela de usuários (login, nome, perfil, situação, último acesso) e tabela de parâmetros (chave, valor, data e usuário da última alteração). As variáveis do arquivo de ambiente continuam valendo como valor inicial e para credenciais.
