# 12 — Configurações

Tela acessível apenas ao perfil Administrador. Toda alteração gera evento na auditoria de acessos ([11](11-auditoria.md)).

## Perfis e permissões

| Permissão | Operador | Gestor | Administrador |
|---|:---:|:---:|:---:|
| Consultar e visualizar documentos | Só como interessado | ✓ | ✓ |
| Ver e incluir documentos sigilosos | — | ✓ | ✓ |
| Gerar e baixar dossiê | Só como interessado | ✓ | ✓ |
| Auditoria de documentos | Só como interessado | ✓ | ✓ |
| Auditoria de acessos | — | — | ✓ |
| Configurações | — | — | ✓ |

"Só como interessado": processos e requerimentos em que o CPF/CNPJ do usuário é o do interessado (RN07). O Administrador tem acesso irrestrito.

**Regras aplicadas no back-end (S4, RNF10)**, todas decididas pela `PoliticaDeAcesso` (D60):

| Regra | Operador | Gestor | Administrador | Resposta quando nega |
|---|:---:|:---:|:---:|---|
| Requerimento ou processo de outro interessado | — | ✓ | ✓ | 403 `NAO_INTERESSADO` (inexistente: 404 `NAO_ENCONTRADO`) |
| Documento sigiloso: ver título, abrir no modal, incluir no dossiê | — | ✓ | ✓ | 403 `DOCUMENTO_SIGILOSO`; na lista, "Documento sigiloso" sem nome de arquivo nem extensão |
| Nome do responsável pela tarefa atual (RN12) | — | ✓ | ✓ | campo omitido |
| Pedido de dossiê criado pelo próprio usuário | ✓ (enquanto for o interessado gravado no pedido) | ✓ | ✓ | 404 `NAO_ENCONTRADO` |
| Pedido de dossiê de outro usuário | — | ✓ | ✓ | 404 `NAO_ENCONTRADO` (não revela que existe) |

A matriz completa, endpoint por endpoint, está em [07 — API](07-api.md#matriz-de-acesso-por-endpoint).

Códigos: `OPERADOR`, `OPERADOR_SIGILO`, `ADMIN`. Na versão definitiva, a identidade vem da autenticação institucional; o perfil é atribuído nesta tela. Itens de menu sem permissão não são exibidos (RN08).

## Abas

### Usuários e perfis

- Lista com nome, login, perfil, situação (ativo/inativo) e último acesso.
- Perfil e situação são alterados direto na lista e gravados na hora (evento `USUARIO`).
- Inclusão por gaveta lateral: nome, login, CPF/CNPJ e perfil. Login é único; CPF/CNPJ é obrigatório para o Operador.
- Usuário inativo não entra no sistema; o histórico de auditoria é mantido.

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

**No protótipo (S2)** os parâmetros vêm de variáveis de ambiente, atrás da porta `ParametrosDoDossie` (a S5 troca a implementação pela tela): `DOSSIE_TTL_MINUTOS` (30), `DOSSIE_THREADS` (2), `DOSSIE_SUMARIO`, `DOSSIE_MARCADORES` e `DOSSIE_NUMERACAO` (ligados), `DOWNLOAD_TIMEOUT_SEGUNDOS` (inatividade da leitura; 120 desde a S2.2, antes 60), `DOWNLOAD_TOTAL_MAXIMO_SEGUNDOS` (teto total por documento, com as novas tentativas; 900), `DOSSIE_ESPACO_MINIMO_MB` (espaço livre mínimo antes de cada download; 2048), `DOSSIE_BLOB_MAX_MB` (acima disto o PDF final é baixado direto pelo navegador, com ticket de uso único e sem pré-visualização; 150; D49) e `VISTAS_DOSSIE_DIRETORIO`. Detalhes de tempo e novas tentativas em docs/05.

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

Estado e teste de conexão de: banco do SIGA (somente leitura), arquivos do Licenciamento (com horas até expirar o token), SCORP e taxas (desativada até a forma de obtenção ser confirmada). Tempo limite de download: 60 s (10–300).

Credenciais ficam no arquivo de ambiente do servidor; a tela não as exibe nem edita.

### Auditoria

Retenção por grupo: 1, 2, 5 ou 10 anos. Valores de exemplo no protótipo: documentos 5 anos, acessos 1 ano.

## Salvamento

- Parâmetros alterados ficam pendentes numa barra fixa com a contagem de alterações, "Descartar" e "Salvar alterações".
- O salvamento gera um único evento `CONFIG` com a lista de mudanças (antes e depois).

## Persistência na versão definitiva

No schema próprio: tabela de usuários (login, nome, perfil, situação, último acesso) e tabela de parâmetros (chave, valor, data e usuário da última alteração). As variáveis do arquivo de ambiente continuam valendo como valor inicial e para credenciais.
