# 11 — Auditoria

Registro de quem consultou, visualizou, gerou e baixou dossiês, com destaque para os eventos que envolvem documentos sigilosos.

## Situação no protótipo

- **Módulo `auditoria` (S5, bloco 2):** eventos imutáveis num repositório em memória do serviço (D09, D65), somente inclusão. **Somem ao reiniciar o serviço**; o registro permanente é da versão definitiva (P14, R50). Os eventos de documentos são registrados desde o bloco 3 (D66; momento de cada um em [07](07-api.md#auditoria)); a consulta por perfil, o detalhe e o CSV estão em [07](07-api.md#auditoria) desde o bloco 4 (D67); os eventos de acessos e as telas chegam nos blocos seguintes da S5 (`docs/progresso/s5.md`).
- Os demais módulos dependem só de `RegistrarEvento`, que **nunca lança**: falha de auditoria vai para o log, só com a classe da exceção, e não impede a operação. Evento inválido é descartado do mesmo jeito.
- Só dois mecanismos descartam eventos: a **retenção** por grupo (limpeza agendada, de hora em hora, porta `RetencaoDeEventos`, separada do repositório) e o **teto** `AUDITORIA_MAX_EVENTOS` (saem os mais antigos, somando os dois grupos).
- **Retrato e limpeza:** todo texto passa por `TextoDoEvento`: tira controle, apara, corta no tamanho máximo (corta em vez de recusar, para não perder o evento) e troca por `[removido]` o que parecer token, cookie, URL, caminho, chave opaca ou CPF/CNPJ completo. Nome de arquivo perde as pastas. O evento não tem campo para chave, token, cookie nem caminho (`ArchitectureTest`).
- **Vínculo do interessado:** os dígitos ficam em `VinculoDoInteressado`, sem leitura pública: só saem para a regra do filtro do Operador e não aparecem em `toString`. Valor que não é CPF nem CNPJ vira "sem vínculo", que nenhum Operador enxerga.
- **Origem da requisição:** último endereço válido do `X-Forwarded-For` (o que o nginx acrescenta; o resto foi escrito pelo cliente) ou o endereço remoto.
- A auditoria é dividida em duas telas:
  - **Documentos:** Gestor e Administrador veem todos os eventos. O Operador vê os eventos, de qualquer usuário, sobre processos e requerimentos vinculados a ele (RN07); nomes de documentos sigilosos aparecem como "Documento sigiloso" e o filtro "somente sigilosos" não é oferecido.
  - **Acessos:** somente Administrador.
- **Regra:** para quem não é Administrador, o item "Acessos" não aparece no menu (RN08). O cadeado de demonstração do protótipo saiu na S4.

## Eventos registrados

### Auditoria de documentos

| Código | Rótulo na tela | Quando ocorre | Detalhe registrado |
|---|---|---|---|
| `CONSULTA` | Consulta | Abertura do detalhe de um processo ou requerimento | Número consultado |
| `VISUALIZACAO` | Visualização | Abertura de um documento no modal | Documento visualizado e sigilo |
| `DOSSIE` | Dossiê gerado | Fim da geração com sucesso | Quantidade de documentos, quantos sigilosos, lista completa e nome do arquivo |
| `FALHA` | Falha na geração | Geração com falha em documento (desde a S2.2, o pedido fica aguardando decisão; cada tentativa com falha gera um evento) ou falha na montagem (evento sem documento) | Documento e motivo |
| `DOWNLOAD` | Download | Download completo do PDF do dossiê (a pré-visualização não conta) | Nome do arquivo e pedido |
| `GEOMETRIAS` | Geometrias baixadas | Download completo do ZIP das geometrias (RN10; previsto desde a S4, registrado a partir da S5) | Quantidade de arquivos, quantas sigilosas e nome do ZIP |

O evento `DOSSIE` é registrado quando a geração termina com sucesso; uma tentativa interrompida gera `FALHA`.

### Auditoria de acessos

| Código | Rótulo na tela | Quando ocorre | Detalhe registrado |
|---|---|---|---|
| `LOGIN` | Acesso | Entrada no sistema (login bem-sucedido; login recusado não registra) | — |
| `PERFIL` | Troca de perfil | Troca do perfil simulado (só no protótipo): um login bem-sucedido feito com uma sessão aberta de **outro perfil** registra `LOGIN` e `PERFIL`, em nome de quem entrou | Perfil novo ("Perfil novo: Administrador") |
| `USUARIO` | Usuário alterado | Inclusão de usuário, mudança de perfil ou de situação (um evento por gravação; sem mudança real, nenhum) | Login do alvo em `detalhe` e antes e depois, sem CPF/CNPJ |
| `CONFIG` | Configuração alterada | Salvamento de parâmetros | Lista de mudanças (antes e depois) |

**Implementação (S5, bloco 6):** o módulo `acesso` publica `EntradaRealizada` (Spring `ApplicationEvent`, só em login bem-sucedido) e `auditoria.application.EventosDeAcesso` registra `LOGIN` e `PERFIL` por `RegistrarEvento`; assim `acesso` não depende da auditoria (ArchUnit) e uma falha ao registrar nunca derruba o login. O contrato do login não mudou (o `Authorization` opcional do pedido só informa o perfil anterior). `USUARIO` e `CONFIG` entram nos blocos 7 e 8.

Eventos com documento sigiloso são marcados (`sigiloso = true`) e destacados na lista.

Regras:

- Eventos são **somente inclusão**. Não há alteração nem exclusão pela aplicação.
- O alvo segue a regra de identificação da interface: número do processo quando houver; número do requerimento quando não protocolado (RN04).
- O registro guarda a lista de documentos do dossiê como estava no momento da geração. O dossiê em si não é armazenado (D08).

## Telas

As duas telas seguem o mesmo padrão. Na de acessos não há coluna de processo nem filtro de sigilo, e o detalhe mostra a tabela de alterações (antes e depois).

- **Período:** hoje, últimos 7 dias (padrão), últimos 30 dias e todo o período.
- **Indicadores do período:** documentos — dossiês gerados, eventos com sigilosos e usuários; acessos — entradas, alterações e usuários.
- **Filtros:** busca por texto (usuário, número, documento, arquivo ou código do evento), tipo de evento, usuário e "somente sigilosos".
- **Lista:** data e hora com o código do evento, usuário e perfil, evento, processo ou requerimento, detalhe e sigilo. Ordem do mais recente para o mais antigo.
- **Detalhe do evento (gaveta lateral):** todos os campos, a origem da requisição e, para visualização e dossiê, a lista de documentos com o sigilo de cada um.
- **Exportar CSV:** exporta os eventos filtrados (separador `;`, UTF-8 com BOM, para abrir direto no Excel).

## Modelo lógico para a versão definitiva

Schema próprio do serviço (ver D09), em Oracle (D21): desenvolvimento em Oracle 23 Free, compatível com 19c. O DDL segue [`padroes/`](padroes/).

**AUDITORIA_EVENTO**

| Coluna | Descrição |
|---|---|
| `ID_AUDITORIA_EVENTO` | Identificador (identity) |
| `DATA_HORA` | Momento do evento, com fuso |
| `LOGIN_USUARIO` / `NOME_USUARIO` | Retrato do usuário no momento do evento (sem FK para `USUARIO`) |
| `PERFIL` | Perfil no momento do evento |
| `GRUPO` | `DOCUMENTOS` ou `ACESSOS` |
| `TIPO_EVENTO` | `CONSULTA`, `VISUALIZACAO`, `DOSSIE`, `FALHA`, `DOWNLOAD`, `GEOMETRIAS`, `LOGIN`, `USUARIO`, `CONFIG` |
| `ID_REQUERIMENTO` | Requerimento do SIGA (referência lógica, sem FK entre bancos) |
| `NUMERO_PROCESSO` / `NUMERO_REQUERIMENTO` | Números no momento do evento |
| `SIGILOSO` | Evento envolve documento sigiloso |
| `ARQUIVO` | Nome do PDF gerado (dossiê e download) |
| `ID_DOSSIE_PEDIDO` | Pedido de geração relacionado (download ligado à geração) |
| `ORIGEM_IP` | Endereço de origem da requisição |
| `DETALHE` | Texto complementar |
| `CORRELATION_ID` | Correlação da requisição (cruza log e auditoria) |

**AUDITORIA_EVENTO_ALTERACAO** (eventos `USUARIO` e `CONFIG`)

| Coluna | Descrição |
|---|---|
| `ID_AUDITORIA_EVENTO` | FK para `AUDITORIA_EVENTO` |
| `ITEM` | Parâmetro ou atributo alterado |
| `VALOR_ANTERIOR` / `VALOR_NOVO` | Valores formatados |

**AUDITORIA_EVENTO_DOCUMENTO**

| Coluna | Descrição |
|---|---|
| `ID_AUDITORIA_EVENTO` | FK para `AUDITORIA_EVENTO` |
| `ORIGEM` / `ID_DOCUMENTO` | Identificação do documento no SIGA (`origem + id`, D10) |
| `TITULO` / `NOME_ARQUIVO` | Cópia no momento do evento |
| `SIGILOSO` | Sigilo no momento do evento |
| `ORDEM` | Posição no dossiê |

Recomendações:

- Usuário da aplicação só com `INSERT` e `SELECT` nessas tabelas, sem `UPDATE` nem `DELETE`; expurgo por usuário próprio (ver [`padroes/seguranca-privilegios.md`](padroes/seguranca-privilegios.md)).
- Colunas de usuário, números e documentos são **retratos** do momento do evento (ver [`padroes/normalizacao.md`](padroes/normalizacao.md)).
- Índices conforme [`padroes/indexacao.md`](padroes/indexacao.md): `(GRUPO, DATA_HORA)`, `(ID_REQUERIMENTO, DATA_HORA)`, `(LOGIN_USUARIO, DATA_HORA)`.
- Retenção separada por grupo (parâmetro em Configurações) e expurgo por rotina agendada; particionamento por período a avaliar conforme volume.
- A chave do arquivo no repositório não é gravada na auditoria.

## Decisões

- Não há integração com o SIGA: o evento `VISTAS_AO_PROCESSO` não é gravado lá (D14).
- Divisão por perfil (D13).

## Pontos em aberto

- Prazos de retenção definitivos de cada grupo (o protótipo usa 5 anos para documentos e 1 ano para acessos como valores de exemplo).
