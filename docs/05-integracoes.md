# 05 — Integrações

## API do Licenciamento — arquivos

| Item | Valor |
|---|---|
| Endpoint | `GET {LICENCIAMENTO_API_URL}/api/arquivo/{chave}` |
| Parâmetro opcional | `versao` (número da versão no repositório de arquivos) |
| Autenticação | Token de sessão de usuário, em um de dois modos: `bearer` (`Authorization: Bearer <token>`, padrão) ou `cookie` (`Cookie: <nome>=<token>`) |
| Resposta | Binário completo, com `Content-Type`, `Content-Length` e `Content-Disposition: inline; filename=...` |
| Erros | Falha no repositório de arquivos retorna HTTP 503; token ausente ou inválido, 401 |

Variáveis (todas no `.env.local`, nunca versionado; `.env.local.example` só traz os nomes, comentados):

| Variável | Uso |
|---|---|
| `LICENCIAMENTO_API_URL` | Base da API. **Ausente ou vazia = "arquivos não configurados"** (mesmo padrão do SIGA, D26): a `api` sobe, a lista funciona e a geração falha com o motivo "API de arquivos não configurada" |
| `LICENCIAMENTO_API_TOKEN` | Token capturado (sem o prefixo `Bearer`) |
| `LICENCIAMENTO_API_TOKEN_FILE` | Opcional (S2.2): caminho de um arquivo com o token, relido a cada pedido; tem precedência sobre `LICENCIAMENTO_API_TOKEN` (ver "Token por arquivo") |
| `LICENCIAMENTO_API_AUTH_MODE` | `bearer` (padrão; vazio também vale como bearer) ou `cookie` |
| `LICENCIAMENTO_API_COOKIE_NAME` | Nome do cookie, no modo `cookie` |
| `DOWNLOAD_TIMEOUT_SEGUNDOS` | Tempo máximo de **inatividade** da leitura (sem receber bytes), inclusive no meio do corpo (padrão 120; até a S2.1 era 60 e valia também para a conexão) |
| `DOWNLOAD_TOTAL_MAXIMO_SEGUNDOS` | Teto **total** para obter um documento, somando as novas tentativas (padrão 900) |
| `DOSSIE_ESPACO_MINIMO_MB` | Espaço livre mínimo no volume do diretório dos dossiês, conferido no `POST /dossies` (507) e antes de cada download (padrão 500 desde a S6, antes 2048; `0` desliga) |

A conexão tem tempo limite fixo de 10 s.

Comportamento (S2; tempos, novas tentativas e disco na S2.2):

- chave `"0"` (ou nula/vazia) **nunca é requisitada**: o documento é indisponível;
- **401 e 403** interrompem toda a geração, **sem nova tentativa**: o pedido fica `PENDENTE_DECISAO` (S2.2; antes `FALHA`) com `erro.codigo = CREDENCIAL_ARQUIVOS_RECUSADA` e a mensagem "Token da API de arquivos expirado ou recusado: capture um novo token." (nunca o valor do token); `CREDENCIAL_ARQUIVOS_RECUSADA` (502) é o código HTTP equivalente;
- **novas tentativas automáticas** (até 2 extras, com espera de 2 s e de 5 s) para **tempo limite de inatividade**, **503** e **queda ou recusa de conexão**. **404**, **401** e **403** nunca são repetidos; outros 5xx e demais status também não. Uma nova tentativa que não caiba no teto total não é feita. O número de pedidos feitos fica no item do pedido (`tentativas` no andamento, docs/07), sem chave nem URL; cada nova tentativa gera um log `INFO` só com o motivo;
- **teto total**: conferido a cada bloco gravado; estourado, o documento falha ("tempo total máximo excedido ao obter o arquivo") sem nova tentativa. Um corpo parado é cortado antes pela inatividade, então o tempo real fica no máximo em teto + inatividade;
- **disco** (S6, bloco 3): antes de cada download o gerador compara o espaço livre do diretório do pedido com `DOSSIE_ESPACO_MINIMO_MB`; abaixo dele, sem chamar a API, **o pedido inteiro** vai para `FALHA` (`ARMAZENAMENTO_INSUFICIENTE`), porque nenhuma decisão resolve falta de espaço. Falta de espaço **ao gravar** o arquivo baixado (mensagem do Linux "No space left on device"/"Disk quota exceeded" ou do Windows, em inglês ou português, em qualquer exceção encadeada) sai do cliente como `ArmazenamentoInsuficienteException`, sem caminho nem mensagem crua, e tem o mesmo efeito; o parcial é apagado. Nas geometrias (S4) a mesma exceção vira 507;
- **404**, **5xx**, tempo limite e conexão recusada falham **só aquele documento** ("arquivo não encontrado no repositório", "repositório de arquivos indisponível", "tempo limite excedido ao obter o arquivo", "tempo total máximo excedido ao obter o arquivo"); o pedido termina a obtenção dos demais e fica em `PENDENTE_DECISAO` (RN09, S2.2);
- o binário é gravado direto em arquivo temporário (streaming em blocos de 64 KB, RNF03), nunca em memória, e o temporário é apagado em qualquer falha, inclusive entre uma tentativa e a seguinte;
- o tipo real é conferido pela assinatura `%PDF-` do arquivo (o `Content-Type` do repositório não basta): conteúdo que não é PDF falha o documento ("o arquivo não é um PDF válido");
- **nenhuma resposta ou log traz URL institucional, token ou chave** (testado).

### Token no protótipo

O protótipo usa o token da sessão de um usuário logado no SIGA, capturado no navegador:

1. Entrar no SIGA Licenciamento e abrir a Vista do Processo de um requerimento.
2. DevTools → aba **Network**, com **Preserve log** marcado.
3. Visualizar um documento, o que dispara `GET .../api/arquivo/<chave>`.
4. Em **Request Headers**, copiar o valor de `Authorization` (sem o prefixo `Bearer`) para `LICENCIAMENTO_API_TOKEN`. Se não houver esse header, copiar o cookie de autorização e configurar `LICENCIAMENTO_API_AUTH_MODE=cookie` e `LICENCIAMENTO_API_COOKIE_NAME`.
5. **Linhas do `.env.local` não podem começar com espaço.** Antes de subir, nunca imprima o arquivo; para verificar sem mostrar valores, teste dentro do container (ver [`padroes/ambientes-e-docker.md`](padroes/ambientes-e-docker.md)).

Restrições:

- o token identifica o usuário que o capturou, e todos os acessos ficam registrados em seu nome;
- uso exclusivamente local, sem versionamento;
- o token expira. Quando a API retornar 401, ele deve ser capturado novamente.

#### Token por arquivo (S2.2)

`LICENCIAMENTO_API_TOKEN_FILE` (opcional) é o caminho, **dentro do container**, de um arquivo só com o token:

- o arquivo é relido a cada pedido e a cada nova tentativa, com cache pela data de modificação e pelo tamanho. Trocar o conteúdo vale já na próxima chamada, **sem recriar o container**;
- quando definida, tem **precedência** sobre `LICENCIAMENTO_API_TOKEN`;
- espaços e a quebra de linha final são aparados;
- arquivo ausente, ilegível ou vazio: o documento falha com "API de arquivos não configurada", sem chamar a API (o token da variável **não** é usado como reserva);
- o valor e o caminho do arquivo nunca vão para log, erro ou resposta (testado percorrendo as saídas de todos os cenários do adapter).

Montagem local: a pasta `./.secrets/` (no `.gitignore`) é montada somente leitura em `/run/secrets-vistas` por uma linha comentada do `compose.local.yaml`, e o `.env.local` aponta `LICENCIAMENTO_API_TOKEN_FILE=/run/secrets-vistas/token-arquivos` (passo a passo no `README.md`). O `compose.verificacao.yaml` esvazia também essa variável.

**Versão definitiva:** credencial de serviço (API key emitida pelo serviço de identidade e validada em `/api/auth/verify-api-key`) ou acesso direto ao Pandora. Ver [10 — Pendências e riscos](10-pendencias-e-riscos.md).

## SCORP — documentos pessoais (`integration.scorp`)

Implementado na S4 (bloco 5) e testado só com um servidor HTTP local; **nada foi chamado no SCORP real**. O que segue NÃO CONFIRMADO é conferido no roteiro de homologação da S4 (`ScorpFumacaTest`).

| Item | Valor |
|---|---|
| Endpoint | `GET {SCORP_API_URL}/documentos/pessoa-licenciamento/{idCorporativo}` (o mesmo que o SIGA usa; o identificador vai codificado num só segmento) |
| Base | **NÃO CONFIRMADO** (`SCORP_API_URL`; vazia = desligado, sem chamada e sem aviso) |
| Autenticação | **NÃO CONFIRMADO (P06).** Mesmo esquema da API de arquivos: `bearer` ou `cookie`. Variáveis próprias opcionais `SCORP_API_TOKEN_FILE`, `SCORP_API_AUTH_MODE` e `SCORP_API_COOKIE_NAME`; sem elas, valem `LICENCIAMENTO_API_TOKEN_FILE` (ou `LICENCIAMENTO_API_TOKEN`), `LICENCIAMENTO_API_AUTH_MODE` e `LICENCIAMENTO_API_COOKIE_NAME`. O arquivo do token é relido a cada chamada; ausente ou vazio, a chamada falha sem ir ao SCORP |
| Retorno | Lista JSON de objetos com `chave`, `nomeArquivo`, `descricao`, `dataInicio`, `dataFim` (campos desconhecidos são ignorados). Qualquer outra coisa (objeto, HTML, item que não é objeto, mais de 5 MB) é "resposta inválida". **Formato das datas NÃO CONFIRMADO:** aceita ISO (com ou sem hora e fuso), `dd/MM/yyyy[ HH:mm[:ss]]` e epoch em milissegundos; outro formato vira data nula (o documento vai para o fim, RN03) |
| Pessoas consultadas | Interessado, representantes legais e responsáveis técnicos do requerimento (`PESSOA.ID_CORPORATIVO`, docs/04 "Pessoas do requerimento para o SCORP"); **uma chamada por pessoa** |
| Tempos | Conexão e leitura limitadas a `SCORP_TIMEOUT_SEGUNDOS` (15); **no máximo 1 nova tentativa** (após 1 s), só em tempo limite, queda de conexão e 5xx. 401, 403, 404, outros status e resposta inválida falham na hora |
| Paralelismo | Até `SCORP_CHAMADAS_SIMULTANEAS` (4) chamadas ao mesmo tempo, em todo o serviço |
| Sigilo | O SCORP não informa. `SCORP_SIGILO_PADRAO` (`false`, segue a Vista do SIGA, R25; `true` trata todos como sigilosos). Decisão pendente com a SEMA (P30) |
| Segredos | Token, cookie, caminho do arquivo do token, URL, chave e o identificador da pessoa nunca vão para log, erro ou resposta (testado percorrendo as saídas) |

**Falha tolerante.** O SCORP fora do ar, credencial recusada, 404, tempo limite ou resposta inválida **não impedem a listagem**: `GET /requerimentos/{id}/documentos` traz as demais origens e um aviso (`SCORP_INDISPONIVEL` se nenhuma pessoa respondeu, `SCORP_PARCIAL` se parte), sem detalhe técnico. **NÃO CONFIRMADO:** se o SCORP responde 404 (em vez de lista vazia) para pessoa sem documentos; se responder, o aviso aparece sempre para essa pessoa.

**Documento do domínio (origem `SCORP`, rótulo "Pessoal").** `id` = 16 primeiros hexadecimais do SHA-256 de `idCorporativo|chave` (estável e sem expor a chave); título = `descricao` (ou `nomeArquivo`); data = `dataInicio`; vigente com `dataFim` nula, sem efeito com ela preenchida, indisponível com chave vazia ou `"0"` (RN01); tipo pela extensão do `nomeArquivo`. Deduplicação por chave com as seis origens do SIGA, que têm precedência (RN05), e o SCORP vem por último no empate da ordem cronológica (RN03). Na lista, `origemDescricao` = "Pessoal · Interessado", "Pessoal · Representante legal" ou "Pessoal · Responsável técnico"; no sumário do dossiê, "Pessoal".

**Arquivos.** O binário vem do mesmo endpoint `/api/arquivo/{chave}` da API de arquivos, pelo mesmo cliente do dossiê. **NÃO CONFIRMADO** que a chave do SCORP vale nele: se não valer, é falha de documento como qualquer outra (gaveta de decisão no dossiê; 404 ou 502 no modal). Com o SCORP fora do ar na hora de gerar, um documento pessoal escolhido não pode ser conferido: `POST /dossies` responde 400 dizendo que o SCORP não respondeu.

## LibreOffice — conversor de documentos (`integration.libreoffice`, S3)

Não é um sistema de rede: é o LibreOffice headless instalado na imagem da `api`, controlado pelo JODConverter 4.4.9 (módulo local, sem o starter do Spring). Converte textos (DOC, DOCX, ODT, RTF), HTML e planilhas para PDF pela porta `ConversorDeDocumentos` (de `documento.application.port`), usada pelo dossiê e pela visualização com os mesmos parâmetros. Desde a S3.1 a porta recebe também o **título do documento** (`converter(origem, tipo, destino, titulo)`; a forma sem título continua, com cabeçalho vazio), que o dossiê (`PreparadorDePdf`, título do item) e a visualização (`ObterConteudoDoDocumento`, título da lista) passam para o cabeçalho das planilhas; documentos de texto o ignoram (docs/06, "Planilhas"). Imagens não passam por aqui (PDFBox, bloco 4 da S3). Os documentos são de terceiros e tratados como **hostis**.

**Processos**

- Pool de `CONVERSAO_PROCESSOS` instâncias do `soffice`, em portas livres do loopback (`127.0.0.1`), sobe na **primeira conversão**: instâncias da aplicação que nunca convertem (como os contextos de teste) não abrem processo.
- Cada instância tem **perfil próprio e descartável**, cópia do perfil-modelo seguro, dentro do diretório de trabalho da aplicação (`<java.io.tmpdir>/vistas-conversor-<id>`), apagado ao parar. Na imagem, `java.io.tmpdir` é `/tmp` e o usuário é `vistas` (sem root), com `HOME` gravável.
- Reinício a cada `CONVERSAO_TAREFAS_POR_PROCESSO` tarefas. Tarefa acima de `CONVERSAO_TIMEOUT_SEGUNDOS` falha com "tempo limite" e o processo é encerrado à força e reiniciado; espera na fila por um processo livre acima do mesmo tempo (no mínimo 30 s, para cobrir o reinício de um processo) também é "tempo limite".
- **Sem órfãos:** ao parar (inclusive ao desligar a aplicação), os processos que restarem são encerrados à força. O diretório de trabalho guarda o processo dono (pid e instante de início); na subida do pool, os processos e diretórios de uma execução anterior cujo dono morreu são encerrados e apagados.
- Processo morto no meio de uma tarefa: a tarefa falha e o pool sobe outro processo; a próxima funciona.
- **Conexão perdida não é documento corrompido (bloco 8):** depois de um tempo limite o JODConverter reinicia o processo duas vezes (pelo tempo limite e pela conexão perdida), e a tarefa seguinte era cancelada nessa reinicialização ("Task was cancelled") ou caía no processo que ela derruba, falhando como "corrompido" um documento bom (reproduzido em 2 de 3 rodadas; é a causa provável do erro intermitente visto no bloco 5). Quando a falha vem do processo (tarefa cancelada pelo JODConverter, `DisposedException`, `com.sun.star.io.IOException`, "EOF reached"), a tarefa é repetida **uma vez**; documento que o LibreOffice não abre não é repetido, e o cancelamento pedido pelo Rastro continua "interrompida".

**Segurança**

- Propriedades de carga: oculto, só leitura, `MacroExecutionMode = NEVER_EXECUTE`, `UpdateDocMode = NO_UPDATE`. Planilhas (XLS, XLSX, ODS) carregam sem "só leitura", porque o Calc recusa mostrar aba oculta (`CONVERSAO_ABAS_OCULTAS`) em documento só leitura; a cópia de trabalho nunca é gravada, só exportada. O único filtro é o de planilhas (`FiltroDePlanilha`, ver [06](06-geracao-do-dossie.md#planilhas)); o `RefreshFilter` padrão do JODConverter fica desligado.
- Perfil-modelo: segurança de macro no nível máximo, macros e conteúdo ativo desligados, vínculos nunca atualizados (Calc e Writer), vínculos de documento não confiável bloqueados (`BlockUntrustedRefererLinks`) e **proxy manual para uma porta fechada do próprio container** (`127.0.0.1:9`, sem exceções), local pt-BR.
- **HTML:** limpo antes da conversão (jsoup, lista permitida): saem `script`, `iframe`, `object`, `embed`, `link`, `base`, `meta` e formulários; imagem só embutida (`data:`); estilo com `url(`, `@import` ou escape é descartado. Referência remota vira conteúdo ausente, não requisição. HTML acima de 20 MB é recusado ("arquivo grande demais para a conversão").
- As duas camadas foram conferidas separadamente: o LibreOffice padrão **busca** `<img>` e `<link>` remotos ao converter HTML; com o perfil seguro e sem a limpeza, e com a limpeza e sem o perfil, o servidor de teste não recebe nada.
- A entrada é copiada para um diretório novo ao lado do destino (no diretório do pedido), com nome gerado (`entrada.<extensão do tipo detectado>`); nunca se converte do caminho de origem nem se usa o nome do usuário. O diretório é apagado em sucesso e em falha.
- **Antes do LibreOffice (bloco 8):** arquivo de 0 byte falha como "corrompido" (o LibreOffice abriria um documento em branco e geraria uma página vazia); entrada ZIP (DOCX, XLSX, ODT, ODS) é descompactada em fluxo, sem guardar nada, contando os bytes reais (o tamanho declarado pode mentir): acima de 1 GB descompactado ou de 20.000 entradas, "arquivo grande demais para a conversão" (ZIP bomba); ZIP ilegível, "corrompido".
- **XLSX em ZIP64 não abre** no LibreOffice (falha rápida como "corrompido"): é o formato padrão do Apache POI no modo streaming (R39).
- Saída validada: tamanho até `CONVERSAO_SAIDA_MAX_MB`, abre no PDFBox, tem páginas e não passa de `CONVERSAO_PAGINAS_MAXIMAS`. A porta devolve `ResultadoDaConversao` (páginas e abas de planilha que foram para página única).

**Falhas tipadas** (`FalhaDeConversaoException.Motivo`; mensagem em português, sem caminho, nome de arquivo nem dado do documento): protegido por senha, corrompido, tempo limite, conversor indisponível, saída vazia, saída grande demais, entrada grande demais, planilha sem conteúdo imprimível, planilha incompleta (última linha ausente no PDF, R41), páginas demais, interrompida, formato sem conversão. DOCX/XLSX criptografados são reconhecidos pelo contêiner OLE2 com `EncryptedPackage` antes de chegar ao LibreOffice (que abortaria a detecção de tipo e pareceria arquivo corrompido); DOC/XLS antigos e ODF com senha são reconhecidos pelo próprio LibreOffice.

**Disponibilidade:** `soffice` ausente ou sem permissão de execução (procurado nos caminhos padrão, ou em `CONVERSAO_OFFICE_HOME`), `CONVERSAO_HABILITADA=false` ou falha ao subir o pool: "conversor indisponível", e a aplicação sobe normalmente. Depois de uma falha ao subir, a próxima tentativa acontece após 1 minuto.

## Arrecadação — DAR

**NÃO CONFIRMADO.** O SIGA gera um ZIP de DARs por requerimento a partir do sistema de Arrecadação. Antes de incluir no protótipo, é preciso identificar:

- o endpoint e a autenticação;
- se o DAR é arquivo armazenado (com chave) ou gerado sob demanda.

Até a confirmação, a origem `DAR` fica desativada por configuração.

## Camunda

Leitura direta das tabelas `SIGA2_CAMUNDA` (ver [04 — Modelo de dados](04-modelo-de-dados.md)). O BPMS do SIGA não é chamado.

Risco: se o SIGA migrar para Camunda 8, essas tabelas deixam de existir.
