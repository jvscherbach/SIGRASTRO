# S5: geração em segundo plano, auditoria e configurações

> **Forma de trabalho:** a sprint é dividida em **12 blocos**, cada um terminando em um **ponto de commit**. Duas sessões do Claude Code (**OPUS** e **SONNET**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

## Contexto

A S5 entrega a **gestão** do serviço (`docs/14`, seção S5), ainda **em memória** (D09): rastreabilidade das consultas, visualizações, dossiês e acessos, e a administração de usuários e parâmetros. Mais um ajuste vindo da homologação da S4:

1. **Geração em segundo plano (bloco 1).** Hoje, clicar fora da gaveta de geração durante a geração fecha a gaveta e **para o acompanhamento**; se o pedido ainda está sendo enviado, a requisição é cancelada e a geração nem nasce. O usuário precisa poder fechar a gaveta e a geração **continuar**, com acompanhamento e aviso.
2. **Auditoria** (RF21 a RF24, `docs/11`): os eventos de documentos e de acessos, as telas "Documentos" e "Acessos", detalhe, filtros e exportação CSV, com a divisão por perfil.
3. **Configurações** (RF25 a RF28, `docs/12`): usuários e perfis, parâmetros do dossiê, da conversão e da visualização, conexões com teste e retenção da auditoria, tudo restrito ao Administrador, com "Salvar alterações" gerando **um único evento** com o antes e o depois.

O dossiê e os dados do serviço são usados em **processos jurídicos**: a auditoria não pode ser alterada pela aplicação, não pode vazar documento sigiloso nem credencial, e a ausência dela não pode quebrar a geração.

Leia antes de começar: `docs/02` (RF16, RF21 a RF28, RN04, RN07, RN08, RNF05, RNF09, RNF10), `docs/07` (matriz de acesso por endpoint), `docs/08` (telas 5 e 6), `docs/09`, `docs/10`, `docs/11`, `docs/12`, `docs/13`, `docs/14` e `docs/progresso/s4.md`.

## Decisões já tomadas (registre em `docs/09`)

- **Em memória.** Auditoria e configurações **somem ao reiniciar o serviço** (os parâmetros voltam aos valores das variáveis de ambiente e os usuários aos fixos). O registro permanente é do schema próprio da versão definitiva (P14); registre o risco em `docs/10` e **avise na tela** (faixa fixa: "Neste protótipo, os registros ficam na memória e somem ao reiniciar o serviço").
- **Auditoria é somente inclusão** (`docs/11`): nenhuma operação altera ou apaga evento; só a **retenção** os descarta (e o teto de memória `AUDITORIA_MAX_EVENTOS`, padrão 50.000, descartando os mais antigos).
- **Falha de auditoria nunca impede a operação:** registrar evento não pode lançar exceção para o chamador (erro vai para o log, sem dado sensível). Em memória isso é raro, mas a porta nasce assim.
- **Retrato do momento:** o evento guarda cópia do usuário, dos números e dos documentos (título, arquivo, sigilo, ordem). **Nunca** grava a chave do arquivo, token, cookie, CPF/CNPJ completo de ninguém nem o caminho de arquivos do servidor.
- **Operador** (RN07): vê os eventos, **de qualquer usuário**, sobre requerimentos em que é o interessado; documento sigiloso aparece como "Documento sigiloso", sem nome de arquivo, e o filtro "somente sigilosos" não é oferecido. **Auditoria de acessos e Configurações: só Administrador**; para os demais o item não aparece (RN08) e o endpoint responde 403 `PERFIL_SEM_PERMISSAO`.
- **Parâmetros em tempo de execução:** cada parâmetro tem uma classe de aplicação: **imediato** (vale já na próxima leitura), **próxima geração** (vale para pedidos criados depois; os em andamento não mudam) ou **exige reinício** (aparece com o aviso e só vale depois de reiniciar; fica registrado no evento). Credenciais **não** são editáveis nem exibidas (`docs/12`, "Conexões").
- **Proteções da administração:** não é possível desativar nem rebaixar o **último Administrador ativo**, nem desativar o **próprio** usuário.
- **CSV:** células que começam com `=`, `+`, `-`, `@`, tabulação ou retorno recebem um apóstrofo na frente (injeção de fórmula no Excel).
- **Falha na própria auditoria ao exportar ou consultar:** erro normal (`ProblemDetail`), sem efeito sobre as demais telas.

## Regras desta sprint

- **Você não executa nada contra o SIGA real, o SCORP real nem a API de arquivos real.** Testes com dados e arquivos **fictícios e sintéticos** (nenhum CPF, CNPJ, nome ou número de processo real em teste, doc ou repositório).
- Nunca abra, imprima ou copie o `.env.local`.
- **Verificação com Docker:** sempre com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`; confirme dentro do container que as variáveis externas (`SIGA_DB_*`, `LICENCIAMENTO_API_*`, `SCORP_*`) estão vazias (só "vazia" ou "preenchida"). Nunca `docker compose down -v`.
- **Nada pago** (regra permanente do `CLAUDE.md`): bibliotecas novas só gratuitas e de código aberto, com licença registrada em `docs/13`. Prefira o que o projeto já tem.
- A chave do arquivo, tokens e cookies **nunca** saem em resposta, log, erro ou evento de auditoria (RNF09).
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.

## Protocolo das duas sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS` ou `SESSÃO: SONNET`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/s5.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/s5.md` (crie no bloco 1, com a tabela dos 12 blocos); a marcação entra no mesmo commit;
   - rode `git diff --stat` e responda:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET>): <título>
Testes: <rodaram / falharam / pulados>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto: o que muda e por quê>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET>.
```

5. **Pare.** Mesma sessão no próximo bloco: espere "continuar". Outra sessão: a atual termina ali.

Tipos de commit permitidos: `feat`, `fix`, `refactor`, `test`, `docs`, `perf`, `chore`, `ci`. A mensagem **não** começa com espaço.

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Front: a geração continua em segundo plano ao fechar a gaveta | **SONNET** | Estado de tela, com requisitos e testes fechados |
| 2 | Módulo `auditoria`: domínio, porta `RegistrarEvento` e repositório em memória | **OPUS** | Base da rastreabilidade: imutabilidade, retrato, sem vazamento |
| 3 | Eventos de documentos: consulta, visualização, dossiê, falha, download e geometrias | **OPUS** | Instrumenta fluxos sensíveis sem alterar o comportamento |
| 4 | Consulta da auditoria por perfil, detalhe e CSV | **OPUS** | Acesso por vínculo, mascaramento de sigilo, injeção de fórmula |
| 5 | Front: auditoria de documentos | **SONNET** | Interface a partir de contrato pronto |
| 6 | Eventos de acessos e front da auditoria de acessos | **SONNET** | Reuso da tela e instrumentação do login |
| 7 | Configurações no back-end: usuários e perfis | **OPUS** | Administração de acesso, regras de proteção |
| 8 | Configurações no back-end: parâmetros em tempo de execução e conexões | **OPUS** | Alteração de comportamento em produção, credenciais, evento único |
| 9 | Front: configurações, usuários e perfis | **SONNET** | Interface |
| 10 | Front: configurações, parâmetros, conexões e retenção | **SONNET** | Interface com alterações pendentes |
| 11 | Matriz de acesso estendida e varredura de vazamento da S5 | **OPUS** | Prova final das regras de segurança |
| 12 | Roteiro de homologação e fechamento | **SONNET** | Documentação |

---

## Bloco 1 (SONNET): a geração continua em segundo plano

**Commit sugerido:** `fix(front): geração do dossiê continua e é acompanhada ao fechar a gaveta`

**Problema (causa):** `GavetaDossieComponent` é destruída quando a tela a fecha (clique no fundo, Esc ou "Fechar") e, ao ser destruída, `encerrar()` cancela o acompanhamento e **qualquer requisição em voo**, inclusive o `POST /dossies` ainda em envio. O servidor continua gerando um pedido que já existe, mas a tela perde o acompanhamento; com o `POST` ainda em envio, o pedido nem é criado.

**Requisitos:**

- **Serviço de acompanhamento** (singleton, em `features/documentos`, por exemplo `AcompanhamentoDeDossieService`), dono do pedido ativo, do envio do `POST`, do polling (mesmo intervalo crescente de hoje) e do estado. A gaveta passa a ser **só a janela** que mostra esse estado. **Fechar a gaveta não cancela o `POST` nem o polling.** Só **Cancelar geração** (botão) chama `DELETE`. Sair da aplicação (logout) ou trocar de usuário encerra o acompanhamento.
- **Clique fora da gaveta durante a geração** (e Esc, e "Fechar") **minimiza**, não encerra. Enquanto houver pedido em andamento, o fundo **não** confirma nada: só minimiza.
- **Indicador na barra do topo** (visível em todas as telas enquanto houver pedido ativo): "Gerando dossiê… 12 de 40" com barra pequena; ao clicar, **reabre a gaveta** no mesmo pedido. Estados: obtendo, montando, **precisa da sua decisão** (destaque), **concluído** ("Dossiê pronto") e erro; concluído e decisão ficam visíveis até o usuário abrir ou dispensar. **Sem alterar o quadro do topo** (altura igual com e sem indicador; 0 px).
- **Aviso de término:** ao concluir ou parar à espera de decisão com a gaveta fechada, um aviso na tela (toast) com a ação "Abrir". Respeite o idioma e o padrão dos avisos existentes.
- **Um pedido ativo por vez no acompanhamento:** com pedido ativo, **Gerar dossiê** em outro requerimento mostra aviso "Já existe uma geração em andamento" e oferece abrir a gaveta atual (o servidor aceita várias gerações; a tela acompanha uma).
- A tela **Dossiês gerados** (S4) continua funcionando e não duplica o polling: o serviço expõe o pedido ativo e a tela existente só o reaproveita para o modo "Decidir".
- Sem vazamento: sem timer, subscription ou URL de objeto sobrando ao concluir, cancelar ou sair.
- Docs: `docs/08` (gaveta, indicador e aviso), `docs/progresso/s5.md` (crie com a tabela dos 12 blocos).
- Testes (Vitest e e2e com API simulada): fechar a gaveta durante o **envio** do `POST` (o pedido é criado); fechar durante `OBTENDO` (o polling continua e o indicador avança); clique no fundo e Esc minimizam; reabrir pelo indicador; conclusão e decisão com a gaveta fechada mostram aviso e indicador; **Cancelar geração** continua cancelando; segundo "Gerar dossiê" com pedido ativo; logout encerra e limpa timers; quadro do topo com 0 px com e sem indicador; celular sem rolagem horizontal.

## Bloco 2 (OPUS): módulo `auditoria`

**Commit sugerido:** `feat(auditoria): módulo de auditoria com registro de eventos em memória`

- Novo módulo `br.gov.mt.sema.vistas.auditoria` (domain, application, api, infrastructure), seguindo os padrões de `docs/13` e as regras do ArchUnit: os demais módulos dependem **só da porta** `RegistrarEvento` (aplicação do módulo `auditoria`); `auditoria` não depende de `consulta`, `documento` nem `dossie` (os dados chegam no evento).
- **Domínio:** `EventoDeAuditoria` imutável (id sequencial, data e hora com fuso de Cuiabá, grupo `DOCUMENTOS`/`ACESSOS`, tipo conforme `docs/11` — `CONSULTA`, `VISUALIZACAO`, `DOSSIE`, `FALHA`, `DOWNLOAD`, `GEOMETRIAS`, `LOGIN`, `USUARIO`, `CONFIG` —, retrato do usuário (login, nome, perfil), `idRequerimento`, número do processo e do requerimento, **vínculo do interessado** (dígitos de CPF/CNPJ usados só para o filtro do Operador, **nunca expostos nem exportados**), `sigiloso`, arquivo, id do pedido de dossiê, origem (IP de `X-Forwarded-For` tratado ou do remoto), detalhe, `correlationId`), `DocumentoDoEvento` (origem, id, título, nome do arquivo, sigilo, ordem), `AlteracaoDoEvento` (item, valor anterior, valor novo). Validação na construção (campos obrigatórios por tipo, tamanhos máximos, detalhe aparado, caracteres de controle removidos).
- **Porta `RegistrarEvento`** (`registrar(NovoEvento)`): nunca lança para o chamador (captura, registra no log **sem** dado do evento e segue). **Porta `ConsultarEventos`** para o bloco 4.
- **Repositório em memória** (thread-safe, só **inclusão**; a interface não oferece alterar nem apagar): índice por grupo e data; teto `AUDITORIA_MAX_EVENTOS` (50.000; descarta os mais antigos); **retenção** por grupo (`AUDITORIA_RETENCAO_DOCUMENTOS_ANOS`, padrão 5; `AUDITORIA_RETENCAO_ACESSOS_ANOS`, padrão 1; faixas 1, 2, 5 e 10) com limpeza agendada junto das limpezas existentes. Os parâmetros entram em `ParametrosDaAuditoria` (porta) e são ligados em `config` por variável de ambiente, como os demais (`docs/12`, `.env.local.example`).
- Sem chave de arquivo, token, cookie ou caminho em **nenhum** campo (teste que tenta e verifica a recusa ou a limpeza).
- Docs: `docs/03` (módulo), `docs/09` (decisões), `docs/10` (R de memória e de perda ao reiniciar), `docs/11` (situação real), `docs/12` (variáveis), `docs/13` (`padroes/modulos-e-dependencias.md`), `docs/progresso/s5.md`.
- Testes: domínio (validação, imutabilidade, limpeza de controle), repositório (inclusão concorrente, ordem, teto, retenção com relógio controlado, nenhuma operação de alteração ou exclusão exposta), `RegistrarEvento` que não lança com repositório falhando, e `ArchitectureTest` com as regras novas.

## Bloco 3 (OPUS): eventos de documentos

**Commit sugerido:** `feat(auditoria): registro dos eventos de consulta, visualização, dossiê, download e geometrias`

Instrumente com `RegistrarEvento`, **sem mudar status, `errorCode` nem corpo de nenhuma resposta existente** e sem registrar acesso **negado** como se fosse bem-sucedido:

| Evento | Onde | Quando | Detalhe |
|---|---|---|---|
| `CONSULTA` | abertura do detalhe de requerimento/processo | acesso concedido | número consultado |
| `VISUALIZACAO` | `GET .../conteudo` | conteúdo entregue | documento (origem, id, título, nome do arquivo, sigilo) |
| `DOSSIE` | fim da geração com sucesso | PDF montado | quantidade, quantos sigilosos, lista completa na ordem, nome do arquivo, id do pedido |
| `FALHA` | cada tentativa com falha em documento (e falha na montagem) | por documento | documento e motivo (texto do motivo, sem caminho nem chave) |
| `DOWNLOAD` | entrega do PDF do dossiê (`attachment` e ticket) | download concluído | nome do arquivo, id do pedido |
| `GEOMETRIAS` | `GET .../geometrias/arquivo` | ZIP entregue | quantidade de arquivos, quantas sigilosas, nome do ZIP |

- O **interessado** do requerimento vai no evento (dígitos, só para o filtro do Operador) e o **sigilo** de cada documento é o da lista no momento. A lista de documentos do `DOSSIE` fica como estava no momento da geração; o PDF não é guardado (D08).
- **Pré-visualização** do dossiê não gera `DOWNLOAD`. **Acesso negado** (403/404) não gera evento de sucesso (se quiser registrar tentativa negada de sigiloso, **não** neste bloco: registre a ideia em `docs/10`).
- Substitua os comentários `// S5: auditoria` deixados na S4 pelas chamadas reais.
- Atenção a **janelas de corrida**: o evento `DOSSIE` e o `FALHA` vêm da thread de geração (sem usuário na requisição): carregue o retrato do usuário **no pedido** (login, nome, perfil) em vez de depender do contexto da requisição; e confira que o `DOWNLOAD` por ticket (sem sessão) usa o usuário que pediu o ticket.
- Docs: `docs/07` (eventos por endpoint), `docs/11`, `docs/progresso/s5.md`.
- Testes (`MockMvc` e testes de caso de uso com repositório real em memória): cada evento sai **uma vez** no momento certo e com os campos esperados; acesso negado não registra; falha do registro não muda a resposta; download por ticket; geração com falha e decisão (um `FALHA` por tentativa com falha, `DOSSIE` só no sucesso); o corpo e os cabeçalhos de todas as respostas continuam **idênticos** aos da S4 (os testes existentes seguem verdes); varredura dos eventos atrás de chave de arquivo, token, cookie e caminho.

## Bloco 4 (OPUS): consulta da auditoria por perfil, detalhe e CSV

**Commit sugerido:** `feat(auditoria): consulta por perfil, detalhe e exportação CSV`

- Endpoints (`docs/07`, atualize a matriz de acesso): `GET /auditoria/documentos`, `GET /auditoria/acessos`, `GET /auditoria/{grupo}/{id}` (detalhe) e `GET /auditoria/{grupo}/exportacao` (CSV). Filtros: período (`hoje`, `7d` padrão, `30d`, `todo`), texto livre, tipo de evento, usuário, `somenteSigilosos`; ordem do mais recente para o mais antigo; paginação (`pagina`, `tamanho`, máx. 100); **indicadores do período** (documentos: dossiês gerados, eventos com sigilosos, usuários distintos; acessos: entradas, alterações, usuários distintos) no mesmo `GET`.
- **Regras por perfil, na política de acesso** (`PoliticaDeAcesso`, módulo `acesso`; **não** decida acesso fora dela): Gestor e Administrador veem **todos** os eventos de documentos; o **Operador** vê só os eventos cujo vínculo do interessado é o dele (RN07), **de qualquer usuário**; **acessos: só Administrador** (403 `PERFIL_SEM_PERMISSAO` para os demais).
- **Mascaramento para o Operador** (e para qualquer perfil sem sigilo): nome de documento sigiloso vira "Documento sigiloso", sem nome de arquivo nem extensão, **em todos os campos** (lista, detalhe, CSV, texto livre e detalhe de falha). O filtro `somenteSigilosos` é recusado (400) para o Operador; a **busca por texto** do Operador não pode revelar documento sigiloso por correspondência (a busca não considera o título nem o arquivo de documento sigiloso).
- **Detalhe** (gaveta no front): todos os campos permitidos, origem da requisição, e para `VISUALIZACAO`/`DOSSIE` a lista de documentos com o sigilo de cada um; para `USUARIO`/`CONFIG` a tabela antes e depois. **Nunca** devolve o vínculo do interessado (CPF/CNPJ) nem a chave do arquivo.
- **CSV:** `;` como separador, UTF-8 com BOM, cabeçalho em português, **mesmo filtro e mesmas regras** da lista, proteção contra injeção de fórmula (apóstrofo antes de `=`, `+`, `-`, `@`, tabulação e retorno), aspas duplicadas, sem quebra de linha dentro da célula, `Content-Disposition` com nome seguro (`auditoria-documentos-AAAAMMDD.csv`), `Cache-Control: no-store`, **limite de linhas** (`AUDITORIA_CSV_MAX_LINHAS`, padrão 50.000) com aviso no cabeçalho `X-Vistas-Truncado` quando cortar. Streaming, sem montar o arquivo todo em memória.
- Docs: `docs/07`, `docs/11`, `docs/12`, `.env.local.example`, `docs/09`, `docs/progresso/s5.md`.
- Testes (`MockMvc`): matriz de perfis em cada endpoint (Operador do interessado e de outro, Gestor, Administrador; evento sigiloso e comum); mascaramento em lista, detalhe e CSV; busca do Operador que **não** encontra documento sigiloso; 400 do filtro; período, texto, tipo, usuário, ordem, paginação e indicadores com relógio controlado; CSV com célula `=1+1`, `@x`, aspas, `;`, acentos, BOM e truncamento; ausência de CPF/CNPJ, chave, token e caminho em corpo e cabeçalhos.

## Bloco 5 (SONNET): front da auditoria de documentos

**Commit sugerido:** `feat(front): tela de auditoria de documentos`

- Rota **Auditoria › Documentos** (`docs/08`, tela 5, `docs/11`, "Telas"), para todos os perfis; o item de menu e a guarda de rota seguem as `permissoes`; a tela de **Acessos** continua "em construção" até o bloco 6.
- Seletor de período (hoje, últimos 7 dias padrão, 30 dias, todo o período); **indicadores**; filtros (texto, tipo de evento, usuário, "somente sigilosos" — **ausente** para o Operador); lista com data e hora (com o código do evento), usuário e perfil, evento, processo ou requerimento, detalhe e sigilo, **destaque lateral** nos eventos sigilosos; paginação; estado vazio e erro com "Tentar novamente".
- **Gaveta de detalhe:** todos os campos, origem da requisição e a lista de documentos com o sigilo de cada um; fecha com Esc e clique fora, sem vazar subscription.
- **Exportar CSV** (`Blob`, token no cabeçalho, nunca na URL; nome do `Content-Disposition`; aviso quando `X-Vistas-Truncado`).
- **Faixa fixa** "Neste protótipo, os registros ficam na memória e somem ao reiniciar o serviço".
- Operador: o texto da tela deixa claro que os eventos são os dos **seus** processos (de qualquer usuário), sem expor outro dado.
- Docs: `docs/08` (tela 5).
- Testes (Vitest e e2e com API simulada): cada perfil (Operador sem "somente sigilosos", com "Documento sigiloso" mascarado; Gestor e Administrador completos), período, filtros, paginação, detalhe (documentos e sigilo), CSV por `Blob` com `Authorization` e sem token na URL, truncamento, vazio e erro, quadro do topo com 0 px e celular sem rolagem horizontal.

## Bloco 6 (SONNET): eventos e tela de acessos

**Commit sugerido:** `feat(auditoria): eventos de acesso e tela de auditoria de acessos`

- **Back-end (pequeno, só instrumentação):** `LOGIN` na entrada (login bem-sucedido; falha de login **não** vira `LOGIN`) e `PERFIL` na troca de usuário/perfil simulado, com retrato do usuário e origem; `USUARIO` e `CONFIG` ficam para os blocos 7 e 8 (deixe os tipos prontos). Nenhuma mudança de contrato do login.
- **Front:** rota **Auditoria › Acessos**, **somente Administrador** (menu e guarda pelas `permissoes`; digitar a URL volta à Consulta sem chamada), mesma tela do bloco 5 **reaproveitando o componente**: sem coluna de processo nem filtro de sigilo, indicadores (entradas, alterações, usuários), detalhe com a tabela antes e depois (para `USUARIO` e `CONFIG`), CSV, mesma faixa de memória.
- Docs: `docs/08` (tela 5, "Acessos"), `docs/11`.
- Testes: back (`LOGIN` e `PERFIL` uma vez por ação; login recusado não registra; resposta do login idêntica à da S4), front (Vitest e e2e: só Administrador vê o item e abre a tela; Operador e Gestor bloqueados na URL sem chamada; reaproveitamento sem regressão da tela de documentos; CSV; quadro do topo com 0 px).

## Bloco 7 (OPUS): configurações, usuários e perfis

**Commit sugerido:** `feat(configuracao): gestão de usuários e perfis em memória com evento de auditoria`

- Novo módulo `br.gov.mt.sema.vistas.configuracao` (mesmas regras de módulo). **Usuários e perfis** sobre o repositório em memória do módulo `acesso` (porta), sem criar autenticação nova: `GET /configuracoes/usuarios`, `POST /configuracoes/usuarios` (inclusão), `PATCH /configuracoes/usuarios/{id}` (perfil e situação). **Só Administrador** (403 `PERFIL_SEM_PERMISSAO` para os demais), validado na política.
- Regras (`docs/12`): login único e normalizado; **CPF/CNPJ obrigatório e válido para o Operador** (dígitos verificadores; guardado só como dígitos, devolvido **mascarado** `***.456.789-**`, nunca completo); perfil dentre `OPERADOR`, `OPERADOR_SIGILO`, `ADMIN`; usuário **inativo não entra** (login recusado com a mensagem existente de credencial inválida, sem revelar que está inativo); último acesso atualizado no login; histórico de auditoria preservado. **Não é possível** desativar nem rebaixar o **último Administrador ativo** nem **desativar o próprio usuário** (409 com `errorCode` próprio e mensagem em português).
- **Sessões abertas:** ao desativar ou mudar o perfil de um usuário, as sessões dele **passam a valer com o novo perfil** (ou deixam de valer, se inativo) **na próxima requisição**, sem precisar sair; teste isso.
- Cada alteração gera evento `USUARIO` (grupo `ACESSOS`) **por alteração**, com antes e depois (perfil e situação; inclusão registra "incluído" e os valores novos, **sem CPF/CNPJ**).
- `// S5: persistência` (nota curta) onde a versão definitiva trocará a implementação.
- Docs: `docs/07` (endpoints e matriz de acesso), `docs/12`, `docs/11`, `docs/09`, `docs/10`, `docs/progresso/s5.md`.
- Testes: política (Administrador sim; Operador e Gestor 403), inclusão válida e inválida (login repetido, CPF/CNPJ inválido, Operador sem documento), alteração com evento correto, último Administrador, próprio usuário, usuário inativo sem login, sessão aberta com perfil novo, CPF/CNPJ **nunca** completo em corpo, cabeçalho ou evento, e a **matriz de acesso** (`MatrizDeAcessoTest`) estendida com as rotas novas (teste de cobertura obriga).

## Bloco 8 (OPUS): configurações, parâmetros em tempo de execução e conexões

**Commit sugerido:** `feat(configuracao): parâmetros em tempo de execução, conexões e evento único de alteração`

- **Parâmetros editáveis** (`docs/12`): Dossiê (expiração 5 a 240 min, gerações simultâneas 1 a 8, sumário, marcadores, numeração, origens), Conversão (as chaves e valores de `docs/12`, "Conversão", com as faixas ali definidas), Visualização (cache, teto, conversões e fila), tempo limite de download (10 a 300 s) e retenção da auditoria (1, 2, 5 ou 10 anos por grupo). Cada parâmetro declara **classe de aplicação** (imediato, próxima geração, exige reinício) e **faixa**; valor fora da faixa é 400 `PARAMETRO_INVALIDO` com o nome do parâmetro.
- **Implementação:** um **repositório de sobreposições em memória** sobre os valores das variáveis de ambiente (as portas `ParametrosDoDossie`, `ParametrosDaConversao`, `ParametrosDaVisualizacao`, `ParametrosDaAuditoria` e as de download passam a ler a sobreposição quando existir; **o contrato das portas não muda**). Valor do ambiente continua sendo o padrão e a fonte ao reiniciar. Pedido de dossiê já em andamento **não muda** de comportamento no meio.
- **Endpoints** (só Administrador): `GET /configuracoes/parametros` (valor atual, padrão do ambiente, faixa, classe de aplicação, se está sobreposto), `PUT /configuracoes/parametros` (**corpo com todas as alterações pendentes**, aplicadas **atomicamente**: ou todas ou nenhuma; valida tudo antes de aplicar) e `DELETE /configuracoes/parametros/{nome}` (volta ao padrão do ambiente). **Um único evento `CONFIG`** por salvamento com a lista antes e depois; sem alteração real, **sem evento**.
- **Conexões** (`GET /configuracoes/conexoes` e `POST /configuracoes/conexoes/{nome}/teste`): estado e teste de **banco do SIGA** (consulta trivial somente leitura), **API de arquivos** (chamada leve de verificação; mostra **horas até expirar o token** quando o token for decodificável: leia **apenas** a data de expiração, sem validar assinatura, sem logar nem devolver o token), **SCORP** (chamada leve) e **taxas** (desativada). Timeout curto no teste, limite de **1 teste simultâneo por conexão**, resultado só com estado, tempo de resposta e mensagem **sem** URL completa com credencial, host interno nem detalhe de exceção. As credenciais **nunca** são exibidas nem editadas.
- **Proteções:** o teste de conexão não é mecanismo de SSRF (a URL vem **só** da configuração do servidor, nunca do pedido); parâmetros desconhecidos recusados; **`SCORP_SIGILO_PADRAO`** e as chaves de credencial **não** estão entre os editáveis (decisão da área, P30).
- Docs: `docs/07` (endpoints e matriz), `docs/12` (tabela de parâmetros com a classe de aplicação), `docs/11`, `docs/09`, `docs/10`, `docs/progresso/s5.md`.
- Testes: validação de faixas, atomicidade (um inválido, nenhum aplicado e nenhum evento), cada classe de aplicação (imediato vale já; próxima geração não muda pedido em andamento; exige reinício aparece como pendente), `DELETE` volta ao padrão, **evento único** com antes e depois, nenhum evento sem mudança, conexões (sucesso, falha e tempo limite com servidores locais de teste; horas até expirar com token de teste; **varredura** atrás de token, cookie, URL com credencial e caminho), só Administrador e a matriz estendida.

## Bloco 9 (SONNET): front de configurações, usuários e perfis

**Commit sugerido:** `feat(front): tela de configurações com a aba de usuários e perfis`

- Rota **Configurações** (somente Administrador; `permissoes`) com **abas** (`docs/08`, tela 6, `docs/12`, "Abas"): **Usuários e perfis** agora; as demais abas ficam como "em construção" até o bloco 10 (abas existentes e navegáveis por teclado, foco visível).
- **Usuários e perfis:** lista (nome, login, perfil, situação, último acesso, CPF/CNPJ mascarado só como a API enviar); alterar **perfil** e **situação** direto na lista, gravando na hora com confirmação curta e tratando os erros das proteções (último Administrador, próprio usuário) com a mensagem do servidor; **inclusão por gaveta lateral** (nome, login, CPF/CNPJ, perfil) com validação (login único; CPF/CNPJ obrigatório para Operador, com máscara e dígitos verificadores); inativo aparece esmaecido.
- **Faixa fixa** de memória (como na auditoria): usuários incluídos ou alterados somem ao reiniciar o serviço.
- Docs: `docs/08` (tela 6, Usuários).
- Testes (Vitest e e2e com API simulada): lista e estados, alteração de perfil e situação com sucesso e com cada erro de proteção, inclusão válida e inválida, CPF/CNPJ mascarado, bloqueio de Operador e Gestor na URL sem chamada, quadro do topo com 0 px, celular sem rolagem horizontal.

## Bloco 10 (SONNET): front de configurações, parâmetros, conexões e retenção

**Commit sugerido:** `feat(front): abas de dossiê, conversão, visualização, conexões e auditoria nas configurações`

- Abas **Dossiê**, **Conversão** (inclui a escolha do modo das planilhas, `LARGURA` ou `PAGINA_POR_ABA`), **Visualização**, **Conexões** e **Auditoria** (retenção por grupo) com os campos, faixas, padrão do ambiente e a **classe de aplicação** de cada um (etiquetas "Vale já", "Vale na próxima geração", "Exige reinício").
- **Alterações pendentes** (`docs/02`, RF28): editar não grava; barra fixa com o número de alterações, **Salvar alterações** (um `PUT` com todas) e **Descartar**; saída da tela com pendência pede confirmação; erro de validação do servidor marca o campo e mantém as pendências; sucesso mostra o resumo do que mudou e o que **exige reinício**. Botão "Restaurar padrão" por parâmetro (`DELETE`).
- **Conexões:** estado e **Testar conexão** por item (carregando, sucesso com tempo de resposta, erro com a mensagem do servidor), **horas até expirar o token** da API de arquivos quando vier, aviso claro quando o token está vencido ("Capture um novo token, atualize o arquivo do token e clique em Tentar novamente"); **nenhuma credencial** exibida ou editável (o texto da aba diz isso).
- A regra de ordem cronológica aparece como **somente leitura** ("fixa, RN03").
- Docs: `docs/08` (tela 6).
- Testes (Vitest e e2e com API simulada): edição com pendência, salvar com sucesso, erro de faixa, descartar, saída com pendência, restaurar padrão, etiquetas de classe de aplicação, conexões (teste com sucesso, falha e token vencido), nenhuma credencial na tela, e a rota de Configurações abrindo só para o Administrador; quadro do topo com 0 px e celular sem rolagem horizontal.

## Bloco 11 (OPUS): matriz de acesso estendida e varredura de vazamento

**Commit sugerido:** `test(acesso): matriz estendida e varredura de vazamento da S5`

- **`MatrizDeAcessoTest`** estendida com **todos** os endpoints novos (`/auditoria/**`, `/configuracoes/**`) × perfil × vínculo × sigilo, nas mesmas regras de cobertura (endpoint novo sem linha na matriz do `docs/07` quebra o teste). Inclui: Operador vendo **só** eventos dos seus requerimentos (de qualquer usuário), nunca de outro interessado; Administrador único em acessos e configurações; usuário desativado ou rebaixado perdendo o acesso na próxima requisição.
- **Mascaramento da auditoria:** para o Operador, o texto do documento sigiloso **não aparece** em nenhuma resposta da auditoria (lista, detalhe, CSV, busca, indicadores).
- **Varredura:** percorra corpo e cabeçalhos de **todas** as respostas de auditoria e configuração atrás de: chave de arquivo, valores de token e cookie, URL e identificador do SCORP, caminho temporário, CPF/CNPJ completo (de usuário e de interessado), host interno e segredo de conexão.
- **CSV:** teste de injeção de fórmula ponta a ponta (evento com título `=HYPERLINK(...)`), cabeçalho e BOM, truncamento.
- **Imutabilidade:** nenhuma rota nem método da aplicação altera ou apaga evento (varredura dos mapeamentos e da interface do repositório).
- **Auditoria que falha não derruba:** com o repositório de auditoria forçado a falhar, consulta, visualização, geração e download continuam com os mesmos status e corpos.
- Corrija o que a matriz achar (e registre em `docs/10`). Atualize `docs/12` (matriz) e `docs/02` (RF21 a RF28) para o que está implementado.
- Docs: `docs/10`, `docs/progresso/s5.md`.

## Bloco 12 (SONNET): roteiro de homologação e fechamento

**Commit sugerido:** `docs(homologacao): roteiro da S5 e registro das decisões`

- `docs/homologacao/S5-roteiro-homologacao.md`, para o **responsável** executar, no formato dos anteriores (comando ou ação exata, resultado esperado, `[ ] OK / [ ] Falhou: observação`):
  1. **Geração em segundo plano:** com um requerimento de muitos documentos (por exemplo, o de ~100 documentos), gerar o dossiê, **clicar fora da gaveta**, navegar por outras telas, ver o indicador avançar, ser avisado ao concluir, reabrir pelo indicador e baixar; repetir clicando fora **no instante do envio**; forçar uma falha e ver o aviso de decisão com a gaveta fechada.
  2. **Auditoria de documentos:** consultar, visualizar, gerar, baixar e baixar geometrias com cada perfil, e conferir os eventos, o destaque dos sigilosos, o detalhe, os filtros e o CSV aberto no Excel (acentos, separador, célula começando com `=`).
  3. **Divisão por perfil:** como Operador do interessado, ver eventos de outro usuário sobre o seu processo e **nenhum** de outro interessado; conferir "Documento sigiloso" mascarado; conferir que Gestor e Administrador veem tudo; conferir que **Acessos** só aparece para o Administrador.
  4. **Usuários e perfis:** incluir, mudar perfil e situação, tentar desativar o último Administrador e o próprio usuário, e entrar com um usuário recém-incluído e com um desativado; conferir os eventos de `USUARIO`.
  5. **Parâmetros:** alterar expiração e modo das planilhas, salvar e conferir o evento `CONFIG` **único** com antes e depois; conferir a classe de aplicação (um que exige reinício); restaurar o padrão; comparar `LARGURA` e `PAGINA_POR_ABA` na pior planilha (fecha a **D58** da S3, se ainda aberta).
  6. **Conexões:** testar banco do SIGA, API de arquivos (horas até expirar) e SCORP; nenhuma credencial na tela nem na rede (aba Rede).
  7. **Reinício do serviço:** reiniciar o container e confirmar que auditoria e alterações somem (comportamento documentado, não falha).
  8. Conferências finais: logs sem token ou chave; `.env.local` ignorado; nada de dado real no repositório.
- `docs/14`: registre a S5 e o que cada bloco entregou, com a tabela "Validação da S5" (pendente); `docs/09`: decisões (geração em segundo plano, auditoria e configuração em memória, retrato do evento, classes de aplicação dos parâmetros, proteções da administração, CSV); `docs/10`: riscos e pendências (perda de auditoria ao reiniciar, teto de memória, parâmetros que exigem reinício, tentativa negada de sigiloso não registrada); coerência final entre `docs/02`, `07`, `08`, `11`, `12` e `13`; `README.md` e `CLAUDE.md` (estado da S5).
- **Verificação final completa:** `./mvnw -B clean verify`, `ArchitectureTest`, `MatrizDeAcessoTest`, front (build, Vitest, e2e), Compose com os três arquivos e variáveis vazias dentro do container, YAMLs válidos, `git diff --check` limpo, varredura do diff atrás de credencial, URL institucional e dado real.

---

## Critérios de aceite (ao final do bloco 12)

1. Clicar fora da gaveta durante a geração **não** interrompe nem perde o acompanhamento: o indicador mostra o andamento, avisa ao concluir ou quando precisa de decisão e reabre a gaveta; só **Cancelar geração** cancela.
2. Os eventos de documentos e de acessos são registrados uma vez, no momento certo, **sem alterar** nenhuma resposta existente; a falha da auditoria não derruba a operação.
3. Auditoria por perfil conforme RF23: Operador só vê o dos seus processos, com sigiloso mascarado em **todos** os campos e na busca; **Acessos** só o Administrador.
4. Detalhe e CSV com as mesmas regras, com proteção contra injeção de fórmula; nunca CPF/CNPJ completo, chave de arquivo, token ou caminho.
5. Usuários e perfis geridos pelo Administrador, com as proteções (último Administrador, próprio usuário) e efeito imediato nas sessões abertas; **um evento por alteração**.
6. Parâmetros editáveis pelo Administrador, validados por faixa, aplicados de forma atômica, com a classe de aplicação visível, e **um único evento `CONFIG`** por salvamento; conexões testáveis sem exibir credencial.
7. `MatrizDeAcessoTest` cobre os endpoints novos e a varredura de vazamento passa; auditoria imutável pela aplicação.
8. Tudo gratuito, sem credencial ou dado real no repositório; avisos de memória na interface.
9. Cada bloco foi executado pela sessão atribuída e marcado em `docs/progresso/s5.md`.
10. Nenhum comando git além de `git diff`.
