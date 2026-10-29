# 02 — Requisitos

## Requisitos funcionais

### Consulta

| ID | Requisito |
|---|---|
| RF01 | Consultar por tipo (Requerimento ou Processo) e número no formato `N/AAAA`. |
| RF02 | Consultar por CPF/CNPJ do interessado e/ou por nome do interessado (busca parcial), retornando uma lista de resultados para escolha. |
| RF03 | A consulta por número de requerimento é exata (1 resultado). A consulta por número de processo trata o resultado como 1:N: o SIGA não impede que o mesmo `NUMERO_PROCESSO` apareça em mais de um requerimento. Com 1 resultado, abre o detalhe; com mais de 1, lista todos para o usuário escolher. A chave é sempre o requerimento. |
| RF04 | Exibir os dados do requerimento: prioridade de atendimento, tipo de requerimento, nº do processo (ou do requerimento, se não protocolado), situação, data de abertura, interessado, CPF/CNPJ, tipo de processo, última atualização, objetivos, finalidades e atividades. |
| RF05 | Exibir a tarefa atual do fluxo (nome da tarefa, data de criação e responsável) ou indicar que não há tarefa ativa. |

### Documentos

| ID | Requisito |
|---|---|
| RF06 | Listar todos os documentos do requerimento (a chave é o requerimento, D31), das seis origens da Vista do SIGA (S2.1) e dos documentos pessoais do SCORP (S4, "Pessoal"; falha do SCORP vira aviso e não impede a lista), com os totais (total, vigentes, sem efeito, não PDF, tipo não informado, indisponíveis, sigilosos). A chave de um documento é origem + id. |
| RF07 | Para cada documento, exibir: identificador, nome, origem, data e sigilo. A situação (vigente, substituído, indisponível) não é exibida como coluna, só nos totais; documentos indisponíveis ficam bloqueados para seleção, com indicação no nome do arquivo. |
| RF08 | Permitir ordenar a lista por documento, origem e data, e filtrar por texto e origem. |
| RF09 | Visualizar qualquer documento em modal. Documentos não-PDF são exibidos já convertidos, quando houver conversão. |
| RF10 | Selecionar documentos individualmente e em massa. Todos os selecionáveis vêm selecionados (RN13). |

### Dossiê

| ID | Requisito |
|---|---|
| RF11 | Gerar um PDF único com os documentos selecionados em ordem cronológica. |
| RF12 | O dossiê tem capa com os dados do requerimento ou processo e sumário com marcadores (bookmarks) para cada documento. |
| RF13 | Converter para PDF planilhas (XLS, XLSX, ODS), documentos de texto (DOC, DOCX, ODT, RTF, HTML) e imagens (PNG, JPG, JPEG). O tipo é confirmado pelo conteúdo do arquivo (assinatura), não só pela extensão (S3). O que foi convertido fica identificado no dossiê. A chave `CONVERSAO_HABILITADA` (padrão ligada) volta ao comportamento só-PDF da S2.2. |
| RF14 | Se documentos selecionados não puderem ser obtidos ou convertidos, a geração coleta todas as falhas, nenhum arquivo é entregue e o andamento lista cada documento com o motivo. O usuário decide entre tentar de novo só os documentos com problema ou removê-los e gerar; a decisão não baixa de novo o que já foi obtido (S2.2) nem converte de novo o que já foi convertido (S3); falha de conversão é falha do documento. |
| RF15 | Geometrias (SHP, KML, KMZ) são listadas à parte e baixadas por "Baixar geometrias" (RN10), num ZIP com todas as vigentes que o perfil acessa. Arquivos compactados são desconsiderados (RN11). |
| RF16 | Acompanhar o andamento da geração (aguardando, obtendo, aguardando decisão, montando, concluído, cancelado, expirado) com progresso por documento que não volta a zero após a decisão (S2.2). |
| RF17 | Pré-visualizar o dossiê gerado antes de baixar. |
| RF18 | Baixar o dossiê com o nome `dossie-processo-<numero>-<ano>.pdf` quando o requerimento estiver protocolado, ou `dossie-requerimento-<numero>-<ano>.pdf` quando não estiver. |

### Acesso (simulado)

| ID | Requisito |
|---|---|
| RF19 | Login simulado com usuários fixos de três perfis: `OPERADOR`, `OPERADOR_SIGILO` (Gestor) e `ADMIN` (Administrador). Permissões em [12 — Configurações](12-configuracoes.md). **S4:** cada perfil testado em todos os endpoints (`MatrizDeAcessoTest`, docs/07 "Matriz de acesso por endpoint"). |
| RF20 | Documentos sigilosos aparecem na lista para todos os perfis, mas só Gestor e Administrador podem visualizá-los, selecioná-los e incluí-los no dossiê. Para o Operador o documento vem **mascarado** (título "Documento sigiloso", sem nome de arquivo, extensão nem tipo) na lista, no PDF (sumário e "Documentos não incluídos") e em toda resposta; no modal e no `POST /dossies`, 403 `DOCUMENTO_SIGILOSO`. Vale também para os documentos pessoais com `SCORP_SIGILO_PADRAO=true`. Geometria sigilosa não aparece para o Operador (só a contagem de omitidas) nem vai no ZIP. **Aplicado e testado no back-end (D36, S4: `PoliticaDeAcesso`, `MatrizDeAcessoTest`).** |
| RF30 | O Operador só consulta, visualiza, gera e baixa dossiê de processos e requerimentos vinculados a ele (RN07). Busca por número de processo ou requerimento sem vínculo retorna "não tem você como interessado"; buscas por interessado só listam os vinculados. **S4:** também em geometrias, documentos pessoais (o SCORP não é chamado para requerimento alheio) e "Dossiês gerados" (só os próprios pedidos, sem "quem gerou"); pedido de outro usuário não existe para ele (404). Testado em todos os endpoints (`MatrizDeAcessoTest`). |

### Auditoria

| ID | Requisito |
|---|---|
| RF21 | Registrar eventos de documentos (consulta, visualização, dossiê gerado, download) e de acessos (entrada, troca de perfil, alteração de usuário, alteração de configuração). Ver [11 — Auditoria](11-auditoria.md). |
| RF22 | Marcar os eventos que envolvem documento sigiloso. |
| RF23 | Auditoria de documentos: Gestor e Administrador veem todos os eventos; o Operador vê os eventos (de qualquer usuário) sobre processos e requerimentos vinculados a ele, com o nome de documentos sigilosos mascarado. Auditoria de acessos: só Administrador; para os demais perfis, o item **não aparece no menu** (RN08). Filtros por período, evento, usuário e texto; "somente sigilosos" para Gestor e Administrador. |
| RF24 | Exibir o detalhe do evento (lista de documentos do dossiê ou alterações antes/depois) e exportar os eventos filtrados em CSV. |

### Configurações

| ID | Requisito |
|---|---|
| RF25 | Tela de configurações acessível apenas ao perfil `ADMIN`. |
| RF26 | Gerenciar usuários: incluir, alterar perfil e ativar/desativar. Cada alteração gera evento na auditoria de acessos. |
| RF27 | Parametrizar dossiê (expiração, gerações simultâneas, sumário, marcadores, numeração, origens incluídas), conversão (modo das planilhas, orientação, abas ocultas, tempo limite), tempo limite de download e retenção da auditoria. |
| RF28 | Alterações de parâmetros ficam pendentes até "Salvar alterações"; o salvamento gera um único evento com a lista de mudanças (antes e depois). |
| RF29 | Exibir o estado das conexões (banco do SIGA, arquivos do Licenciamento, documentos pessoais, taxas) com teste de conexão. Credenciais não são exibidas nem editadas pela tela. |

## Requisitos não funcionais

| ID | Requisito |
|---|---|
| RNF01 | Somente leitura no banco do SIGA. O serviço não grava em nenhuma tabela do SIGA. |
| RNF02 | Geração assíncrona: o pedido retorna imediatamente e o processamento ocorre em segundo plano. |
| RNF03 | Arquivos processados em disco temporário, nunca acumulados integralmente em memória. |
| RNF04 | Timeout por documento na obtenção e na conversão (inatividade e teto total, com novas tentativas automáticas; docs/05). Nenhum dossiê é entregue com documento faltando sem decisão explícita do usuário (RN09); os temporários são descartados ao concluir, cancelar ou expirar. |
| RNF05 | O dossiê não é armazenado de forma permanente. Arquivos temporários são apagados após o download ou após expiração (padrão: 30 minutos). |
| RNF06 | Páginas mantêm tamanho e orientação originais. Não se força A4. |
| RNF07 | Credenciais (token da API, conexão com o banco) ficam só em variáveis de ambiente (`.env` fora do controle de versão). |
| RNF08 | Execução local via Docker Compose. |
| RNF09 | O front-end nunca recebe a chave do arquivo no repositório de arquivos. Documentos são referenciados por `origem + id`, e o back-end resolve a chave. |
| RNF10 | Regras de perfil e sigilo são aplicadas no back-end, não apenas na interface. **S4:** uma única `PoliticaDeAcesso` decide (D60; `ArchitectureTest` proíbe decidir fora dela); a matriz endpoint × perfil × interessado × sigilo × dono é provada por MockMvc em todos os endpoints, e um endpoint novo sem linha na matriz do docs/07 faz o teste falhar. |
| RNF11 | Eventos de auditoria são somente inclusão: a aplicação não altera nem exclui registros. |

## Regras de negócio

| ID | Regra |
|---|---|
| RN01 | **Vigente**: registro sem data de fim (`DATA_FIM IS NULL`) e com chave diferente de `"0"`. **Substituído**: registro com `DATA_FIM` preenchida. **Indisponível**: chave `"0"` ou arquivo inexistente. |
| RN02 | Documentos de doença grave (`PESSOA_DOCUMENTO_DOENCA_GRAVE`) nunca são listados. |
| RN03 | Data para ordem cronológica: a data da própria origem (S2.1; a data de assinatura deixou de ser usada, a Vista não a usa). Documentos sem data vão ao final. Empate desempata por origem e id. **Reforçada na S2.2:** a ordem do dossiê é sempre a do servidor, decidida no `POST /dossies` (`DATA_DOC` crescente pelo timestamp completo; sem data ao final; empate pela ordem das origens do docs/04 e depois pelo `ID` numérico); a ordem, o filtro e os cliques na tabela não a alteram. A data é lida sem deslocar o dia (relógio de Cuiabá, qualquer que seja o fuso da JVM ou do navegador). Detalhes em docs/06. |
| RN04 | O requerimento vira processo após o pagamento da taxa e a conclusão do protocolo. Quando há processo, a identificação exibida (topo da tela, atalhos, resultados, capa e nome do dossiê) é sempre o número do processo; o número do requerimento só aparece enquanto ele não foi protocolado. |
| RN05 | Documentos presentes em mais de uma tabela com a mesma chave aparecem uma única vez. |
| RN06 | A sinalização de sigilo considera todas as variações de coluna (`SIGILOSO`, `SIGILO`, `IND_SIGILOSO`). Na dúvida, o documento é tratado como sigiloso. |
| RN07 | Vínculo: o processo é do interessado. O Operador só acessa processos e requerimentos em que o CPF/CNPJ do seu usuário é o do interessado (`PESSOA.CPF_CNPJ` de `REQUERIMENTO.ID_PESSOA`). Gestor e Administrador acessam todos. |
| RN08 | Itens de menu de funções que o perfil não pode usar não são exibidos (decidido pelas `permissoes` de `/auth/me`), e a URL dessas telas é bloqueada no front (aviso e volta à Consulta) e validada de novo no back-end. Desde a S4 não há cadeado de demonstração. |
| RN09 | Nada incompleto sem decisão explícita (reescrita na S2.2): a geração percorre todos os documentos e coleta as falhas; com falha, o pedido fica em `PENDENTE_DECISAO` e nada é entregue. A decisão — tentar de novo só os documentos com problema, ou removê-los e montar com os já obtidos — não reinicia nada. Credencial recusada (401/403) para a obtenção na hora, preservando o que já foi obtido. Detalhes em docs/06. (Até a S2.1: "completo ou nenhum", a primeira falha interrompia e descartava tudo.) |
| RN10 | Geometrias (SHP, KML, KMZ) não entram no dossiê nem na seleção: ficam numa seção própria do detalhe, com download à parte ("Baixar geometrias", ZIP), registrado na auditoria (S5). O ZIP é **tudo ou nada**, como a RN09: se algum arquivo falhar, nenhum ZIP é entregue e o erro lista os arquivos com problema. Geometria sigilosa não é entregue ao Operador: some da lista e do ZIP, e só a **contagem** de omitidas aparece. |
| RN11 | Arquivos compactados (ZIP, RAR, 7Z) são desconsiderados: não aparecem na lista nem entram no dossiê. |
| RN12 | Responsável pela tarefa atual: só o **nome** do servidor é exibido, e só para Gestor e Administrador. O Operador não recebe o campo (a API o omite). O CPF do servidor nunca sai na API nem na tela. Sem responsável: "Não atribuída"; sem nome encontrado ou valor em outro formato: "Responsável não identificado". |
| RN13 | Seleção total (S2.2): ao abrir o requerimento, todo documento **selecionável** vem selecionado: vigente ou sem efeito (`DATA_FIM` preenchida), de qualquer tipo, inclusive tipo não informado (`DESCONHECIDO`). Com a conversão ligada (S3), planilhas, textos e imagens de formato convertível (RF13) também são selecionáveis e vêm selecionados. Não são selecionáveis: indisponíveis (chave nula, vazia ou `"0"`), não PDF por extensão conhecida que não é convertível (por exemplo `txt`) ou, com `CONVERSAO_HABILITADA=false` ou conversor indisponível, qualquer não PDF ("conversão indisponível"), e sigilosos para o Operador. "Seleção padrão" volta a marcar todos os selecionáveis. |
