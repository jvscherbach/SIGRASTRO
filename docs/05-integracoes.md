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
| `DOSSIE_ESPACO_MINIMO_MB` | Espaço livre mínimo no volume do diretório do pedido, conferido antes de cada download (padrão 2048; `0` desliga) |

A conexão tem tempo limite fixo de 10 s.

Comportamento (S2; tempos, novas tentativas e disco na S2.2):

- chave `"0"` (ou nula/vazia) **nunca é requisitada**: o documento é indisponível;
- **401 e 403** interrompem toda a geração, **sem nova tentativa**: o pedido fica `PENDENTE_DECISAO` (S2.2; antes `FALHA`) com `erro.codigo = CREDENCIAL_ARQUIVOS_RECUSADA` e a mensagem "Token da API de arquivos expirado ou recusado: capture um novo token." (nunca o valor do token); `CREDENCIAL_ARQUIVOS_RECUSADA` (502) é o código HTTP equivalente;
- **novas tentativas automáticas** (até 2 extras, com espera de 2 s e de 5 s) para **tempo limite de inatividade**, **503** e **queda ou recusa de conexão**. **404**, **401** e **403** nunca são repetidos; outros 5xx e demais status também não. Uma nova tentativa que não caiba no teto total não é feita. O número de pedidos feitos fica no item do pedido (`tentativas` no andamento, docs/07), sem chave nem URL; cada nova tentativa gera um log `INFO` só com o motivo;
- **teto total**: conferido a cada bloco gravado; estourado, o documento falha ("tempo total máximo excedido ao obter o arquivo") sem nova tentativa. Um corpo parado é cortado antes pela inatividade, então o tempo real fica no máximo em teto + inatividade;
- **disco**: antes de cada download o gerador compara o espaço livre do diretório do pedido com `DOSSIE_ESPACO_MINIMO_MB`; abaixo dele o documento falha com "espaço em disco insuficiente", sem chamar a API;
- **404**, **5xx**, tempo limite, conexão recusada e disco insuficiente falham **só aquele documento** ("arquivo não encontrado no repositório", "repositório de arquivos indisponível", "tempo limite excedido ao obter o arquivo", "tempo total máximo excedido ao obter o arquivo", "espaço em disco insuficiente"); o pedido termina a obtenção dos demais e fica em `PENDENTE_DECISAO` (RN09, S2.2);
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

| Item | Valor |
|---|---|
| Endpoint usado pelo SIGA | `.../documentos/pessoa-licenciamento/{idCorporativo}` |
| Base | **NÃO CONFIRMADO** (`SCORP_API_URL`) |
| Autenticação | **NÃO CONFIRMADO**. Testar com o mesmo token da API do Licenciamento. |
| Retorno | Lista com `chave`, `nomeArquivo`, `descricao`, `dataInicio`, `dataFim` |
| Pessoas consultadas | Interessado, representantes legais e responsáveis técnicos do requerimento (`PESSOA.ID_CORPORATIVO`) |

Os arquivos retornados pelo SCORP são obtidos pelo mesmo endpoint `/api/arquivo/{chave}`. **NÃO CONFIRMADO**: verificar se a chave do SCORP é válida nesse endpoint.

Falha no SCORP não impede a listagem. Os documentos de origem `SCORP` aparecem como indisponíveis, com aviso.

## LibreOffice — conversor de documentos (`integration.libreoffice`, S3)

Não é um sistema de rede: é o LibreOffice headless instalado na imagem da `api`, controlado pelo JODConverter 4.4.9 (módulo local, sem o starter do Spring). Converte textos (DOC, DOCX, ODT, RTF), HTML e planilhas para PDF pela porta `ConversorDeDocumentos` (de `documento.application.port`), usada pelo dossiê e pela visualização com os mesmos parâmetros. Imagens não passam por aqui (PDFBox, bloco 4 da S3). Os documentos são de terceiros e tratados como **hostis**.

**Processos**

- Pool de `CONVERSAO_PROCESSOS` instâncias do `soffice`, em portas livres do loopback (`127.0.0.1`), sobe na **primeira conversão**: instâncias da aplicação que nunca convertem (como os contextos de teste) não abrem processo.
- Cada instância tem **perfil próprio e descartável**, cópia do perfil-modelo seguro, dentro do diretório de trabalho da aplicação (`<java.io.tmpdir>/vistas-conversor-<id>`), apagado ao parar. Na imagem, `java.io.tmpdir` é `/tmp` e o usuário é `vistas` (sem root), com `HOME` gravável.
- Reinício a cada `CONVERSAO_TAREFAS_POR_PROCESSO` tarefas. Tarefa acima de `CONVERSAO_TIMEOUT_SEGUNDOS` falha com "tempo limite" e o processo é encerrado à força e reiniciado; espera na fila por um processo livre acima do mesmo tempo (no mínimo 30 s, para cobrir o reinício de um processo) também é "tempo limite".
- **Sem órfãos:** ao parar (inclusive ao desligar a aplicação), os processos que restarem são encerrados à força. O diretório de trabalho guarda o processo dono (pid e instante de início); na subida do pool, os processos e diretórios de uma execução anterior cujo dono morreu são encerrados e apagados.
- Processo morto no meio de uma tarefa: a tarefa falha e o pool sobe outro processo; a próxima funciona.
- **Conexão perdida não é documento corrompido (bloco 8):** depois de um tempo limite o JODConverter reinicia o processo duas vezes (pelo tempo limite e pela conexão perdida), e a tarefa seguinte era cancelada nessa reinicialização ("Task was cancelled") ou caía no processo que ela derruba, falhando como "corrompido" um documento bom (reproduzido em 2 de 3 rodadas; é a causa provável do erro intermitente visto no bloco 5). Quando a falha vem do processo (tarefa cancelada pelo JODConverter, `DisposedException`, `com.sun.star.io.IOException`, "EOF reached"), a tarefa é repetida **uma vez**; documento que o LibreOffice não abre não é repetido, e o cancelamento pedido pelo Vistas continua "interrompida".

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
