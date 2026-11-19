# Inventário da renomeação SIGA Vistas → Rastro (R1, bloco 1)

> Levantamento feito em **2026-11-19**, sobre os arquivos versionados e os novos ainda sem commit (`git grep --untracked`, sem diferenciar caixa). O `.env.local` **não foi aberto** e `amostras-locais/` é ignorado pelo Git. Nenhum arquivo de código foi alterado.

## Resumo

| Classe | Significado | Linhas |
|---|---|---:|
| A | visível ao usuário ou nome do produto em documento vivo | 95 |
| B | técnica (pacote, artefato, propriedade, chave de armazenamento, cabeçalho, caminho) | 2007 |
| C | histórico (`docs/prompts/`, `docs/progresso/`, roteiros em `docs/homologacao/`): não muda | 123 |
| D | nome do SIGA: a "Vista do Processo" (singular) do SIGA Licenciamento e as telas dele não contêm o nome buscado e ficam fora da lista; nada a trocar | 0 |
| E | "vistas" com outro sentido (palavra comum) | 8 |
| **Total** | | **2233** |

Nesta lista, os trechos trazem aspas simples no lugar de crases (para não quebrar a tabela). Variantes sem nenhuma ocorrência: sigavistas e siga_vistas. Os arquivos front/package.json e front/angular.json, nginx e configurações de teste do front **não** carregam o nome antigo. Das 2007 linhas B, **1481** são só `package`/`import` do pacote Java (resumidas na seção 2); as demais estão listadas uma a uma.

### Riscos da Fase 2 (blocos 5 a 7)

1. **Pacote Java (`br.gov.mt.sema.vistas`).** Cerca de 1.500 linhas de `package`/`import` em 375 arquivos. Mover os arquivos (não recriar) para preservar o histórico. O `ArchitectureTest` e as regras ArchUnit citam o pacote por texto e precisam do novo nome junto. O `@SpringBootApplication` define a raiz de varredura; `VistasApplication` e `VistasException` mudam de nome.
2. **Propriedades `vistas.*`** (dezenas, em `@Value`, `@ConditionalOnProperty`, `application*.yml`, no catálogo de parâmetros da tela de Configurações e em ~30 testes). Exigem **alias**: lê os dois prefixos, o antigo vence, com aviso de depreciação sem valor. O `CatalogoDeParametros` mostra o nome da propriedade na tela e o `ParametrosHttpTest` confere que ele não vaza.
3. **Variáveis de ambiente `VISTAS_*`** (`VISTAS_DEMO_SENHA`, `VISTAS_DOSSIE_DIRETORIO`, `VISTAS_DB_*`) estão no `.env.local` do responsável, que não abrimos. **Não renomear**; no máximo aceitar a nova como alias.
4. **Chaves de armazenamento do navegador** `vistas.token` (sessionStorage) e `vistas.recentes.<login>` (localStorage). Trocar sem fallback **desloga** e apaga os "recentes". Fallback por uma versão: ler a antiga, migrar e apagar.
5. **Cabeçalhos `X-Vistas-Truncado` e `X-Vistas-Convertido-De`** fazem parte do **contrato HTTP** (back, front, e2e e OpenAPI). Renomear é mudança de contrato: **decisão do responsável**. Sugestão: manter (não é visível ao usuário) ou emitir os dois por uma versão.
6. **Volume do Docker `dossies-tmp` e caminho `/var/tmp/vistas`**, mais o usuário/grupo `vistas` no `Dockerfile`. O volume foi criado com dono `vistas`; trocar o usuário sem cuidado repete o erro de permissão já descrito no README. Nunca `docker compose down -v`. O nome do **projeto** Compose (`name: siga-vistas` em `compose.yaml`) **prefixa os volumes e a rede**: mudar o `name:` cria volume novo e vazio e deixa o antigo órfão.
7. **Imagens e CI.** O `ci.yml` marca as imagens `siga-vistas-api:ci` e `siga-vistas-front:ci`; o selo do README aponta para `jvscherbach/siga-vistas`. Imagens em cache continuam com o nome antigo até serem reconstruídas. Nenhum segredo com o nome antigo foi achado nos workflows (conferir também as configurações do repositório, que não estão no código).
8. **Cookie da sessão.** Só aparece como `sessao_vistas` em teste (`VarreduraDaAuditoriaHttpTest`); o nome real do cookie de sessão do SIGA é configurável e não leva o nome do projeto.
9. **Skill `vistas-banco`** (`.claude/skills/vistas-banco/`) e seu espelho `docs/padroes/`: o nome da skill é citado no `CLAUDE.md`. Renomear a pasta muda como a skill é invocada. Decisão do responsável.
10. **Schema próprio da versão definitiva** (`VISTAS`, `APP_VISTAS`, `ROLE_APP_VISTAS`, datasource `vistas`, `VISTAS_DB_*`): só existe em documentação (não há tabela nem migration). Decidir o nome **antes** da versão definitiva; não faz parte desta sprint.
11. **Senha de demonstração `vistas-demo`**: padrão do perfil `local`, citada no README, no login e no `application-local.yml`. Trocar quebra quem usa o clique rápido e o roteiro de homologação. Decisão do responsável.
12. **Arquivo do protótipo** `docs/prototipo/siga-vistas-prototipo.html`: citado por `CLAUDE.md`, README, skills e `tokens.css`. Renomear o arquivo exige atualizar todas essas referências.
13. **Imagem da marca.** O topo e o login usam `assets/img/logo-sema.png`. A decisão sobre a logo da SEMA no produto/capa é da S6, fora da R1.

## 1. Classes A, D e E e demais linhas B (uma a uma)

Documentos de classe C e as cópias em `.claude/skills/` aparecem só nas seções 3 e 4.

| Arquivo | Linha | Trecho | Classe | Tipo | Ação |
|---|---:|---|---|---|---|
| .claude/skills/vistas-banco/SKILL.md | 6 | # Contexto arquitetural do SIGA Vistas | A | nome do produto | Bloco 4: trocar por Rastro |
| .claude/skills/vistas-banco/SKILL.md | 8 | SIGA Vistas é um serviço externo ao SIGA Licenciamento que consulta requerimentos e processos, lista os docume… | A | nome do produto | Bloco 4: trocar por Rastro |
| .claude/skills/vistas-banco/SKILL.md | 76 | - 'references/tipos-dados.md' — tipos por natureza do dado, com exemplos do Vistas. | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| api/pom.xml | 18 | <description>SIGA Vistas - API de consulta e geração de dossiê</description> | A | nome do produto | Bloco 2/3: trocar por Rastro |
| api/src/main/java/br/gov/mt/sema/vistas/config/OpenApiConfig.java | 45 | .title("SIGA Vistas API") | A | nome do produto | Bloco 2/3: trocar por Rastro |
| api/src/main/java/br/gov/mt/sema/vistas/dossie/infrastructure/pdf/MontagemPdfBox.java | 199 | y = texto(c, "Vistas do Processo", negrito, 10f, x, y, 18f); | A | rótulo do módulo | Bloco 2: DECISÃO (rótulo do módulo; manter "Vistas") |
| api/src/main/java/br/gov/mt/sema/vistas/integration/libreoffice/ConversorLibreOffice.java | 253 | * pedido pelo Vistas (interrupção da thread) não chega aqui: vira {@code INTERROMPIDA} antes. | A | nome do produto/módulo em prosa | Bloco 2/3: trocar por Rastro |
| api/src/test/resources/sql/siga/02-dados.sql | 13 | -- 101: protocolado (tem processo). 102 e 103: não protocolados. 104: situação que o Vistas não conhece. | A | nome do produto/módulo em prosa | Bloco 2/3: trocar por Rastro |
| CLAUDE.md | 21 | SIGA Vistas (nome provisório) é um serviço externo ao SIGA Licenciamento que gera o **dossiê em PDF** de um re… | A | nome do produto | Bloco 4: trocar por Rastro |
| CLAUDE.md | 90 | O SIGA Vistas é um projeto pessoal, em fase de protótipo, sem orçamento e sem vínculo institucional por enquan… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 10 | …istórico do que foi protocolado (o SIGA Vistas **não lê**; ver [P01](#número-de-processo-requerimentonumero_pr… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 83 | …ntos com a data em "Sem efeito"; o SIGA Vistas também os lista.… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 84 | …ante legal e responsável técnico o SIGA Vistas usa a coluna real 'SIGILO', **mesmo que a Vista do SIGA a ignor… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 89 | …pré-seleciona nada; desde a S2.2 o SIGA Vistas seleciona **todo documento selecionável** (sem bloqueio: vigent… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 169 | …_COLUMNS'. O usuário de leitura do SIGA Vistas tem 'SELECT' em todas as tabelas abaixo. A tela atual foi compa… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 176 | …A', 'SUPERFICIAL', 'BARRAMENTO'. O SIGA Vistas exibe "Subterrânea", "Superficial" e "Barramento"; valor descon… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 178 | …A busca pela API da Arrecadação; o SIGA Vistas lê a tabela diretamente (mesmo texto, conferido no processo 602… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 186 | …em 'REQUERIMENTO_LICENCIAMENTO'. O SIGA Vistas consulta os dois blocos e mostra o que tiver dados; sem dados, … | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 214 | - Visibilidade (regra do SIGA Vistas; o SIGA não tem regra própria): o nome só aparece para **Gestor** ('OPERA… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 219 | …nsferência sobre título emitido. O SIGA Vistas **não a consulta** e não filtra por origem: só a 'REQUERIMENTO'… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 221 | - Regra do SIGA Vistas: a busca por número de processo trata o resultado como **1:N**. Com 1 resultado, abre o… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 228 | ### Tabelas do SIGA lidas pelo SIGA Vistas (para o grant do usuário de leitura) | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/04-modelo-de-dados.md | 252 | … lista "Tabelas do SIGA lidas pelo SIGA Vistas" (seção P01), mais 'SIGA2_CAMUNDA.ACT_RU_EXECUTION', 'ACT_RU_TA… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/05-integracoes.md | 103 | … repetido, e o cancelamento pedido pelo Vistas continua "interrompida".… | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/08-telas.md | 80 | …sulta". O topo da tela mostra a trilha 'Vistas do Processo / Consulta / Processo N/AAAA'.… | A | rótulo do módulo (trilha da tela) | Bloco 4: acompanhar a decisão do bloco 2 |
| docs/08-telas.md | 91 | …desão sem objetivos cadastrados; o SIGA Vistas não mostra (regra de origem ainda não levantada).… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/09-decisoes.md | 164 | **Decisão:** o SIGA Vistas lê a descrição direto da tabela 'SIGA2_ARRECADACAO.ATIVIDADE' (por 'FINALIDADE_ATIV… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/09-decisoes.md | 239 | **Decisão:** o SIGA Vistas usa a coluna real 'SIGILO' nas duas origens, com a normalização tolerante e "na dúv… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/09-decisoes.md | 248 | ## D45 — Pré-seleção é conveniência do SIGA Vistas | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/09-decisoes.md | 250 | … a Vista não pré-seleciona nada; o SIGA Vistas mantém a pré-seleção de **PDF vigente, sem bloqueio**. **Substi… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/09-decisoes.md | 255 | …ecisão:** a Vista não deduplica; o SIGA Vistas sim (RN05), para não repetir páginas no dossiê. Precedência: a … | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/09-decisoes.md | 294 | **Contexto:** o SIGA Vistas é um projeto pessoal, em protótipo, sem orçamento. O repositório pode ser privado … | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/09-decisoes.md | 364 | …cumentos entre a Vista do SIGA e o SIGA Vistas (P21: 29 contra 35 no '445/2022', 92 contra 104 num processo de… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 8 | …'. **Status (S1.1): resolvida.** O SIGA Vistas usa 'REQUERIMENTO.NUMERO_PROCESSO' e **não consulta** a tabela … | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 22 | …trante), no mesmo 'ID_PROCESSO'. O SIGA Vistas lista os dois (1:N) \| Comunicar à equipe do SIGA \|… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 25 | …os cadastrados (ex.: 3040/2022); o SIGA Vistas mostra "—". A regra de origem (no código do SIGA, aparentemente… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 26 | …ntos que a Vista do SIGA lista e o SIGA Vistas não** (processo '445/2022': 35 na Vista, 29 no Vistas; os 6 que… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 55 | …sugerir índice à equipe do SIGA (o SIGA Vistas não cria objeto no SIGA) \|… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 58 | …edir em homologação (R09, R14) \| O SIGA Vistas não cria objeto no SIGA: sugerir o índice à equipe do SIGA \|… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 63 | …o quer provar que tem solução) \| O SIGA Vistas não depende dessas colunas: data nula vai ao final e RL e RT sã… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 64 | …role de sigilo fica só na tela \| O SIGA Vistas aplica RN06 e RN07 por conta própria, mas o token da API de arq… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 65 | … não vai para o PDF, sem aviso \| O SIGA Vistas lista o documento como 'INDISPONIVEL' (RN01), não o seleciona e… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/10-pendencias-e-riscos.md | 66 | …úblico \| Divergência consciente: o SIGA Vistas usa a coluna real 'SIGILO' nas tabelas de RL e RT (D43). **SCOR… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/13-padroes-tecnicos.md | 72 | - ['padroes/tipos-dados.md'](padroes/tipos-dados.md) — tipos por natureza do dado, com exemplos do Vistas. | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/14-plano-de-sprints.md | 75 | \| Processo \| Situação no SIGA \| Resultado no SIGA Vistas \| Bate? \| | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/14-plano-de-sprints.md | 112 | …bjetivo:** o SIGA mostra "LAC" e o SIGA Vistas mostra "—" (ver P20); o grupo da atividade aparecia junto da at… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/14-plano-de-sprints.md | 152 | … na Vista do SIGA? \| Documentos (SIGA × Vistas) \| Dossiê gerado? \| Páginas \| Tempo \| Bate? \|… | A | nome do produto (cabeçalho de tabela "SIGA × Vistas") | Bloco 4: trocar por Rastro |
| docs/14-plano-de-sprints.md | 354 | …Comparativo: Vista atual do SIGA × SIGA Vistas para os mesmos processos (gera/não gera, planilhas incluídas, d… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/casos-de-teste.md | 3 | … e processos usados para validar o SIGA Vistas (P09). **Preenchida pelo responsável**: o desenvolvimento não t… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/casos-de-teste.md | 15 | …GA \| Tem planilha? \| Documentos (SIGA × Vistas) \| Resultado do dossiê \| Tempo \| Páginas \| Tamanho \| Observação… | A | nome do produto (cabeçalho de tabela "SIGA × Vistas") | Bloco 4: trocar por Rastro |
| docs/casos-de-teste.md | 21 | … (bloqueio)'. **Documentos:** "SIGA N × Vistas N". Um caso vale como prova do critério (1) quando falha hoje n… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/padroes/connection-pooling.md | 35 | …GA atende o próprio SIGA em produção. O Vistas é um consumidor secundário e não pode competir por sessões.… | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/padroes/documentacao.md | 6 | …_EVENTO IS 'Evento de auditoria do SIGA Vistas (somente inclusão). Grupos DOCUMENTOS e ACESSOS.';… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/padroes/http-erros-openapi.md | 10 | ### Códigos de erro do Vistas | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/padroes/indexacao.md | 34 | …SIGA não são nossos. Se uma consulta do Vistas ficar lenta, levar o plano de execução ao DBA do SIGA; nunca cr… | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/padroes/mer.md | 5 | Entidades do Vistas: Usuário, Perfil, Parâmetro, Pedido de dossiê, Documento do pedido, Evento de auditoria, D… | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/padroes/mer.md | 15 | - Relação entre módulos do Vistas também é lógica quando cruza módulos (ex.: 'AUDITORIA_EVENTO.LOGIN_USUARIO' … | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/padroes/modulos-e-dependencias.md | 35 | ## Contratos internos usados no Vistas | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/padroes/nomenclatura.md | 3 | Vale para o **schema próprio** do SIGA Vistas. O banco do SIGA tem nomes próprios que não alteramos; nas consu… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/padroes/nomenclatura.md | 29 | \| Objeto \| Padrão \| Exemplo no Vistas \| | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/padroes/normalizacao.md | 11 | ## Desnormalização deliberada do Vistas: o "retrato" na auditoria | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/padroes/tipos-dados.md | 5 | \| Natureza \| Tipo \| Exemplo no Vistas \| | A | nome do produto/módulo em prosa | Bloco 4: trocar por Rastro |
| docs/prototipo/siga-vistas-prototipo.html | 6 | <title>SIGA Vistas · Protótipo</title> | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/prototipo/siga-vistas-prototipo.html | 471 | …btu33AAAAAASUVORK5CYII=" alt=""><b>SIGA VISTAS</b></a>… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/prototipo/siga-vistas-prototipo.html | 495 | <footer class="page-footer"><span>SIGA Vistas · Dossiê de requerimentos e processos do SIGA Licenciamento</spa… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/prototipo/siga-vistas-prototipo.html | 671 | …crumbs').innerHTML=ui.view==='detalhe'?'Vistas do Processo / Consulta / <strong>${e(crumbs.detalhe)}</strong>'… | A | rótulo do módulo (protótipo) | Bloco 4: decidir (referência visual) |
| docs/prototipo/siga-vistas-prototipo.html | 685 | …"${$('.brand img').src}" alt=""><b>SIGA VISTAS</b></a>… | A | nome do produto | Bloco 4: trocar por Rastro |
| docs/prototipo/siga-vistas-prototipo.html | 698 | …s="dash-headline"><span class="eyebrow">Vistas do Processo</span><h1 style="margin-top:6px">Localize o requeri… | A | rótulo do módulo (protótipo) | Bloco 4: decidir (referência visual) |
| docs/README.md | 1 | # Vistas do Processo — Protótipo | A | título do índice (rótulo do módulo) | Bloco 4: trocar por Rastro (índice) e manter "Vistas" como módulo |
| front/src/app/app.routes.ts | 12 | { path: 'login', component: LoginComponent, canActivate: [visitanteGuard], title: 'Entrar · SIGA Vistas' }, | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/app.routes.ts | 22 | title: 'Consulta · SIGA Vistas', | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/app.routes.ts | 28 | title: 'Requerimento · SIGA Vistas', | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/app.routes.ts | 34 | title: 'Dossiês gerados · SIGA Vistas', | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/app.routes.ts | 40 | title: 'Auditoria de documentos · SIGA Vistas', | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/app.routes.ts | 47 | title: 'Auditoria de acessos · SIGA Vistas', | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/app.routes.ts | 56 | title: 'Configurações · SIGA Vistas', | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/features/login/login.component.html | 4 | <a class="brand" href="/login"><img src="assets/img/logo-sema.png" alt="" /><b>SIGA VISTAS</b></a> | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/layout/shell.component.html | 5 | <a class="brand" routerLink="/consulta"><img src="assets/img/logo-sema.png" alt="" /><b>SIGA VISTAS</b></a> | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/layout/shell.component.html | 51 | Vistas do Processo | A | rótulo do módulo | Bloco 2: DECISÃO (rótulo do módulo; manter "Vistas") |
| front/src/app/layout/shell.component.html | 94 | <span>SIGA Vistas · Dossiê de requerimentos e processos do SIGA Licenciamento</span> | A | nome do produto | Bloco 2/3: trocar por Rastro |
| front/src/app/shared/quadro-topo.component.ts | 32 | readonly sobretitulo = input('Vistas do Processo'); | A | rótulo do módulo | Bloco 2: DECISÃO (rótulo do módulo; manter "Vistas") |
| front/src/index.html | 5 | <title>SIGA Vistas</title> | A | nome do produto | Bloco 2/3: trocar por Rastro |
| README.md | 1 | # SIGA Vistas | A | nome do produto | Bloco 4: trocar por Rastro |
| README.md | 7 | O **SIGA Vistas** (nome provisório) é um serviço externo ao SIGA Licenciamento. Ele consulta requerimentos e p… | A | nome do produto | Bloco 4: trocar por Rastro |
| .claude/skills/vistas-banco/SKILL.md | 2 | name: vistas-banco | B | skill | Bloco 6: decisão (skill e nome do schema próprio) |
| .claude/skills/vistas-banco/SKILL.md | 3 | … arquitetural e padrão de banco do SIGA Vistas (serviço de dossiê da Vista do Processo; Spring Boot 3/Java 21 … | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| .claude/skills/vistas-banco/SKILL.md | 32 | Pacote raiz 'br.gov.mt.sema.vistas' (nome do projeto provisório). | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| .env.local.example | 7 | # VISTAS_DEMO_SENHA= | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| .env.local.example | 11 | # VISTAS_DOSSIE_DIRETORIO=/var/tmp/vistas | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| .env.local.example | 100 | # LICENCIAMENTO_API_TOKEN_FILE=/run/secrets-vistas/token-arquivos | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| .env.local.example | 109 | # SCORP_API_TOKEN_FILE=/run/secrets-vistas/token-scorp | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| .env.local.example | 119 | # VISTAS_DB_URL=jdbc:oracle:thin:@//host:1521/servico | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| .env.local.example | 120 | # VISTAS_DB_USER=usuario_app | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| .env.local.example | 121 | # VISTAS_DB_PASSWORD=senha | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| .env.local.example | 122 | # VISTAS_DB_POOL_MAX=3 | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| .github/workflows/ci.yml | 210 | tags: siga-vistas-api:ci | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| .github/workflows/ci.yml | 218 | tags: siga-vistas-front:ci | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| api/Dockerfile | 17 | …DejaVu. O conversor roda como o usuário vistas, com o… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/Dockerfile | 18 | # perfil do LibreOffice no diretório temporário e HOME gravável (/home/vistas) para o cache de fontes. | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/Dockerfile | 30 | && groupadd --system vistas \ | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/Dockerfile | 31 | && useradd --system --gid vistas --create-home --home-dir /home/vistas vistas \ | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/Dockerfile | 32 | && mkdir -p /var/tmp/vistas \ | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/Dockerfile | 33 | && chown vistas:vistas /var/tmp/vistas | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/Dockerfile | 36 | COPY --from=build /build/target/siga-vistas-api.jar app.jar | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| api/Dockerfile | 38 | USER vistas | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/pom.xml | 15 | <artifactId>siga-vistas-api</artifactId> | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| api/pom.xml | 17 | <name>siga-vistas-api</name> | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| api/pom.xml | 109 | <finalName>siga-vistas-api</finalName> | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| api/src/main/java/br/gov/mt/sema/vistas/acesso/application/AdministrarUsuarios.java | 64 | throw new VistasException(ErrorCode.LOGIN_JA_EXISTE, "Já existe um usuário com este login."); | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/acesso/application/AdministrarUsuarios.java | 82 | throw new VistasException(ErrorCode.PROPRIO_USUARIO, "Não é possível desativar o próprio usuário."); | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/acesso/application/AdministrarUsuarios.java | 91 | throw new VistasException(ErrorCode.ULTIMO_ADMINISTRADOR, | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/acesso/infrastructure/memoria/RepositorioSessoesEmMemoria.java | 27 | RepositorioSessoesEmMemoria(Clock relogio, @Value("${vistas.sessao.duracao:PT8H}") Duration duracao) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/acesso/infrastructure/memoria/RepositorioUsuariosEmMemoria.java | 20 | … do protótipo, todos com a mesma senha (vistas.demo.senha). Os incluídos ou alterados somem ao reiniciar: o… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/acesso/infrastructure/memoria/RepositorioUsuariosEmMemoria.java | 36 | RepositorioUsuariosEmMemoria(@Value("${vistas.demo.senha:}") String senhaDemo, Clock relogio) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/auditoria/api/AuditoriaController.java | 87 | + "AUDITORIA_CSV_MAX_LINHAS corta e avisa no cabeçalho X-Vistas-Truncado.") | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/main/java/br/gov/mt/sema/vistas/auditoria/api/AuditoriaController.java | 106 | resposta.setHeader("X-Vistas-Truncado", "true"); | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/main/java/br/gov/mt/sema/vistas/auditoria/infrastructure/agendamento/LimpezaDaAuditoria.java | 12 | @ConditionalOnProperty(name = "vistas.agendamento.enabled", havingValue = "true", matchIfMissing = true) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/auditoria/infrastructure/agendamento/LimpezaDaAuditoria.java | 23 | @Scheduled(fixedDelayString = "${vistas.agendamento.limpeza-orfaos:PT1H}", initialDelayString = "${vistas.agen… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/AgendamentoConfig.java | 10 | @ConditionalOnProperty(name = "vistas.agendamento.enabled", havingValue = "true", matchIfMissing = true) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/OpenApiConfig.java | 30 | OpenAPI vistasOpenApi() { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/config/OrigensLigadasVigentes.java | 10 | /** Padrão em {@code vistas.documentos.origens.<origem>}, com a sobreposição da tela por cima. */ | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/OrigensLigadasVigentes.java | 24 | …lean doAmbiente = ambiente.getProperty("vistas.documentos.origens." + origem.propriedade(), Boolean.class, tru… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaAuditoriaDoAmbiente.java | 28 | @Value("${vistas.auditoria.max-eventos:50000}") int maximoDeEventos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaAuditoriaDoAmbiente.java | 29 | @Value("${vistas.auditoria.retencao-documentos-anos:5}") int retencaoDocumentosAnos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaAuditoriaDoAmbiente.java | 30 | @Value("${vistas.auditoria.retencao-acessos-anos:1}") int retencaoAcessosAnos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaAuditoriaDoAmbiente.java | 31 | @Value("${vistas.auditoria.csv-max-linhas:50000}") int maximoDeLinhasDoCsv, ParametrosEmVigor vigentes) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 28 | @Value("${vistas.conversao.habilitada:true}") boolean habilitada, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 29 | @Value("${vistas.conversao.timeout-segundos:300}") int timeoutSegundos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 30 | @Value("${vistas.conversao.processos:2}") int processos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 31 | @Value("${vistas.conversao.tarefas-por-processo:50}") int tarefasPorProcesso, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 32 | @Value("${vistas.conversao.planilha-modo:LARGURA}") String planilhaModo, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 33 | @Value("${vistas.conversao.abas-ocultas:false}") boolean abasOcultas, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 34 | @Value("${vistas.conversao.imagem-max-megapixels:100}") int imagemMaxMegapixels, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 35 | @Value("${vistas.conversao.planilha-escala-minima:40}") int planilhaEscalaMinima, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 36 | @Value("${vistas.conversao.paginas-maximas:2000}") int paginasMaximas, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaConversaoDoAmbiente.java | 37 | @Value("${vistas.conversao.saida-max-mb:500}") int saidaMaxMb, ParametrosEmVigor vigentes) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDasGeometriasDoAmbiente.java | 9 | …ente (protótipo). Temporários em {@code VISTAS_DOSSIE_DIRETORIO/geometrias}. */… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDasGeometriasDoAmbiente.java | 21 | @Value("${vistas.geometrias.max-mb:500}") long maximoMb, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDasGeometriasDoAmbiente.java | 22 | @Value("${vistas.geometrias.max-arquivos:200}") int maximoDeArquivos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDasGeometriasDoAmbiente.java | 23 | @Value("${vistas.dossie.espaco-minimo-mb:2048}") long espacoMinimoMb, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDasGeometriasDoAmbiente.java | 24 | @Value("${vistas.dossie.diretorio:/var/tmp/vistas}") String diretorioDoDossie) { | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaVisualizacaoDoAmbiente.java | 11 | …nte (protótipo). O cache fica em {@code VISTAS_DOSSIE_DIRETORIO/visualizacao}. */… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaVisualizacaoDoAmbiente.java | 25 | @Value("${vistas.visualizacao.ttl-minutos:10}") int ttlMinutos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaVisualizacaoDoAmbiente.java | 26 | @Value("${vistas.visualizacao.cache-max-mb:500}") long cacheMaxMb, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaVisualizacaoDoAmbiente.java | 27 | @Value("${vistas.visualizacao.conversoes-simultaneas:2}") int conversoesSimultaneas, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaVisualizacaoDoAmbiente.java | 28 | @Value("${vistas.visualizacao.fila-de-conversoes:4}") int filaDeConversoes, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDaVisualizacaoDoAmbiente.java | 29 | @Value("${vistas.dossie.diretorio:/var/tmp/vistas}") String diretorioDoDossie, ParametrosEmVigor vigentes) { | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 29 | @Value("${vistas.dossie.ttl-minutos:30}") int ttlMinutos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 30 | @Value("${vistas.dossie.threads:2}") int threads, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 31 | @Value("${vistas.dossie.download-timeout-segundos:120}") int downloadTimeoutSegundos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 32 | @Value("${vistas.dossie.download-total-maximo-segundos:900}") int downloadTotalMaximoSegundos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 33 | @Value("${vistas.dossie.espaco-minimo-mb:2048}") long espacoMinimoMb, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 34 | @Value("${vistas.dossie.blob-max-mb:150}") long blobMaxMb, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 35 | @Value("${vistas.dossie.historico-horas:24}") int historicoHoras, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 36 | @Value("${vistas.dossie.sumario:true}") boolean sumario, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 37 | @Value("${vistas.dossie.marcadores:true}") boolean marcadores, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 38 | @Value("${vistas.dossie.numeracao:true}") boolean numeracao, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDoDossieDoAmbiente.java | 39 | @Value("${vistas.dossie.diretorio:/var/tmp/vistas}") String diretorio, ParametrosEmVigor vigentes) { | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/main/java/br/gov/mt/sema/vistas/config/ParametrosDosDocumentosPessoaisDoAmbiente.java | 13 | …sDocumentosPessoaisDoAmbiente(@Value("${vistas.scorp.sigilo-padrao:false}") boolean sigiloPadrao) {… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/application/GerirConexoes.java | 76 | throw new VistasException(ErrorCode.TESTE_DE_CONEXAO_EM_ANDAMENTO, "Já há um teste desta conexão em andamento.… | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 51 | …, "Expiração do dossiê (min)", DOSSIE, "vistas.dossie.ttl-minutos", 30, 5, 240, PROXIMA_GERACAO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 52 | inteiro(DOSSIE_THREADS, "Gerações simultâneas", DOSSIE, "vistas.dossie.threads", 2, 1, 8, EXIGE_REINICIO), | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 53 | booleano(DOSSIE_SUMARIO, "Sumário", DOSSIE, "vistas.dossie.sumario", true, PROXIMA_GERACAO), | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 54 | booleano(DOSSIE_MARCADORES, "Marcadores no PDF", DOSSIE, "vistas.dossie.marcadores", true, PROXIMA_GERACAO), | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 55 | booleano(DOSSIE_NUMERACAO, "Numeração de páginas", DOSSIE, "vistas.dossie.numeracao", true, PROXIMA_GERACAO), | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 63 | …DA, "Conversão habilitada", CONVERSAO, "vistas.conversao.habilitada", true, PROXIMA_GERACAO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 64 | opcao(CONVERSAO_PLANILHA_MODO, "Planilhas", CONVERSAO, "vistas.conversao.planilha-modo", "LARGURA", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 66 | …AS, "Incluir abas ocultas", CONVERSAO, "vistas.conversao.abas-ocultas", false, PROXIMA_GERACAO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 67 | …o limite de conversão (s)", CONVERSAO, "vistas.conversao.timeout-segundos", 300, 10, 1800, EXIGE_REINICIO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 68 | … "Instâncias do conversor", CONVERSAO, "vistas.conversao.processos", 2, 1, 8, EXIGE_REINICIO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 69 | …tância antes de reiniciar", CONVERSAO, "vistas.conversao.tarefas-por-processo", 50, 1, 500, EXIGE_REINICIO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 70 | …gem: máximo de megapixels", CONVERSAO, "vistas.conversao.imagem-max-megapixels", 100, 1, 500, PROXIMA_GERACAO)… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 71 | …anilha: escala mínima (%)", CONVERSAO, "vistas.conversao.planilha-escala-minima", 40, 10, 100, PROXIMA_GERACAO… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 72 | … páginas de uma conversão", CONVERSAO, "vistas.conversao.paginas-maximas", 2000, 1, 10000, PROXIMA_GERACAO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 73 | …PDF de uma conversão (MB)", CONVERSAO, "vistas.conversao.saida-max-mb", 500, 1, 2000, PROXIMA_GERACAO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 74 | …de vida do cache (min)", VISUALIZACAO, "vistas.visualizacao.ttl-minutos", 10, 1, 240, IMEDIATO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 75 | …e espaço do cache (MB)", VISUALIZACAO, "vistas.visualizacao.cache-max-mb", 500, 10, 10000, IMEDIATO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 76 | …s do modal em paralelo", VISUALIZACAO, "vistas.visualizacao.conversoes-simultaneas", 2, 1, 8, EXIGE_REINICIO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 77 | …sões do modal à espera", VISUALIZACAO, "vistas.visualizacao.fila-de-conversoes", 4, 0, 32, EXIGE_REINICIO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 78 | …mpo limite de download (s)", CONEXOES, "vistas.dossie.download-timeout-segundos", 120, 10, 300, PROXIMA_GERACA… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 79 | …oria de documentos (anos)", AUDITORIA, "vistas.auditoria.retencao-documentos-anos", "5", RETENCOES, IMEDIATO),… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 80 | …ditoria de acessos (anos)", AUDITORIA, "vistas.auditoria.retencao-acessos-anos", "1", RETENCOES, IMEDIATO));… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/configuracao/domain/CatalogoDeParametros.java | 86 | return booleano(PREFIXO_ORIGEM + chave, rotulo, DOSSIE, "vistas.documentos.origens." + chave, true, IMEDIATO); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/documento/api/DocumentoController.java | 79 | …Code = "200", description = "Binário. X-Vistas-Convertido-De traz o formato de origem quando o PDF veio de uma… | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/main/java/br/gov/mt/sema/vistas/documento/api/DocumentoController.java | 110 | resposta.header("X-Vistas-Convertido-De", conteudo.convertidoDe()); | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/main/java/br/gov/mt/sema/vistas/documento/application/BaixarGeometrias.java | 96 | throw new VistasException(ErrorCode.GEOMETRIAS_GRANDES_DEMAIS, "Este requerimento tem " | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/documento/application/BaixarGeometrias.java | 125 | throw new VistasException(ErrorCode.GEOMETRIAS_GRANDES_DEMAIS, "As geometrias deste requerimento " | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/documento/application/FalhaNoConteudoException.java | 7 | public class FalhaNoConteudoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/documento/application/GeometriasIncompletasException.java | 12 | public class GeometriasIncompletasException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/documento/application/port/ArquivosDoDocumento.java | 13 | * @throws br.gov.mt.sema.vistas.shared.application.exception.CredencialArquivosRecusadaException token recusad… | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/main/java/br/gov/mt/sema/vistas/documento/application/port/ParametrosDasGeometrias.java | 20 | … apagada ao fim (subdiretório de {@code VISTAS_DOSSIE_DIRETORIO}). */… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/java/br/gov/mt/sema/vistas/documento/application/port/ParametrosDaVisualizacao.java | 23 | /** Diretório do cache (subdiretório de {@code VISTAS_DOSSIE_DIRETORIO}). */ | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/java/br/gov/mt/sema/vistas/documento/domain/OrigemDocumento.java | 30 | /** Chave da propriedade {@code vistas.documentos.origens.<chave>} que liga ou desliga a origem. */ | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/documento/infrastructure/agendamento/LimpezaDasGeometrias.java | 23 | @ConditionalOnProperty(name = "vistas.agendamento.enabled", havingValue = "true", matchIfMissing = true) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/documento/infrastructure/agendamento/LimpezaDasGeometrias.java | 42 | @Scheduled(fixedDelayString = "${vistas.agendamento.limpeza-orfaos:PT1H}", initialDelayString = "${vistas.agen… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/documento/infrastructure/agendamento/LimpezaDaVisualizacao.java | 17 | @ConditionalOnProperty(name = "vistas.agendamento.enabled", havingValue = "true", matchIfMissing = true) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/documento/infrastructure/agendamento/LimpezaDaVisualizacao.java | 33 | @Scheduled(fixedDelayString = "${vistas.agendamento.limpeza-dossie:PT5M}", initialDelayString = "${vistas.agen… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/dossie/application/ArquivosTemporarios.java | 22 | …ório próprio do pedido dentro de {@code VISTAS_DOSSIE_DIRETORIO}: documentos obtidos e o PDF final. */… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/java/br/gov/mt/sema/vistas/dossie/application/LimparDossies.java | 83 | // Só mexe no que o próprio dossiê cria (pasta com nome de UUID): VISTAS_DOSSIE_DIRETORIO apontado para uma | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/java/br/gov/mt/sema/vistas/dossie/infrastructure/agendamento/LimpezaAgendada.java | 17 | @ConditionalOnProperty(name = "vistas.agendamento.enabled", havingValue = "true", matchIfMissing = true) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/dossie/infrastructure/agendamento/LimpezaAgendada.java | 34 | @Scheduled(fixedDelayString = "${vistas.agendamento.limpeza-dossie:PT5M}", initialDelayString = "${vistas.agen… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/dossie/infrastructure/agendamento/LimpezaAgendada.java | 39 | @Scheduled(fixedDelayString = "${vistas.agendamento.limpeza-orfaos:PT1H}", initialDelayString = "${vistas.agen… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/libreoffice/ConversorLibreOffice.java | 55 | * desta aplicação ({@code <tmp>/vistas-conversor-<id>}). Cada instância é reiniciada a cada | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/libreoffice/ConversorLibreOffice.java | 120 | …metrosDaConversao parametros, @Value("${vistas.conversao.office-home:}") String officeHome,… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/libreoffice/FiltroDePlanilha.java | 288 | String nome = "VistasAba" + (indice + 1); | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/integration/libreoffice/FiltroDePlanilha.java | 290 | nome = "VistasAba" + (indice + 1) + "_" + sufixo; | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/integration/libreoffice/ProcessosDoConversor.java | 26 | static final String PREFIXO = "vistas-conversor-"; | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosCondicoes.java | 9 | static final String CONFIGURADO = "!'${vistas.arquivos.url:}'.isBlank()"; | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosCondicoes.java | 10 | static final String NAO_CONFIGURADO = "'${vistas.arquivos.url:}'.isBlank()"; | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosDoLicenciamentoHttp.java | 77 | ArquivosDoLicenciamentoHttp(@Value("${vistas.arquivos.url}") String url, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosDoLicenciamentoHttp.java | 78 | @Value("${vistas.arquivos.token:}") String token, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosDoLicenciamentoHttp.java | 79 | @Value("${vistas.arquivos.token-file:}") String arquivoDoToken, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosDoLicenciamentoHttp.java | 80 | @Value("${vistas.arquivos.auth-mode:bearer}") String modo, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosDoLicenciamentoHttp.java | 81 | @Value("${vistas.arquivos.cookie-name:}") String nomeDoCookie, ParametrosDoDossie parametros) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/VerificadorDaApiDeArquivos.java | 39 | VerificadorDaApiDeArquivos(@Value("${vistas.arquivos.url:}") String url, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/VerificadorDaApiDeArquivos.java | 40 | @Value("${vistas.arquivos.token:}") String token, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/VerificadorDaApiDeArquivos.java | 41 | @Value("${vistas.arquivos.token-file:}") String arquivoDoToken, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/VerificadorDaApiDeArquivos.java | 42 | @Value("${vistas.arquivos.auth-mode:bearer}") String modo, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/licenciamento/VerificadorDaApiDeArquivos.java | 43 | @Value("${vistas.arquivos.cookie-name:}") String nomeDoCookie, Clock relogio) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 53 | …sem {@code SCORP_API_URL} ou com {@code vistas.documentos.origens.scorp=false}: nenhuma chamada.</li>… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 91 | DocumentosDoScorpHttp(@Value("${vistas.scorp.url:}") String url, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 93 | @Value("${vistas.scorp.token-file:}") String arquivoDoToken, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 94 | @Value("${vistas.arquivos.token-file:}") String arquivoDoTokenDoLicenciamento, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 95 | @Value("${vistas.arquivos.token:}") String token, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 96 | @Value("${vistas.scorp.auth-mode:}") String modo, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 97 | @Value("${vistas.arquivos.auth-mode:bearer}") String modoDoLicenciamento, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 98 | @Value("${vistas.scorp.cookie-name:}") String nomeDoCookie, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 99 | @Value("${vistas.arquivos.cookie-name:}") String nomeDoCookieDoLicenciamento, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 100 | @Value("${vistas.scorp.timeout-segundos:15}") int timeoutSegundos, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttp.java | 101 | @Value("${vistas.scorp.chamadas-simultaneas:4}") int chamadasSimultaneas, ObjectMapper json) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/VerificadorDoScorp.java | 37 | VerificadorDoScorp(@Value("${vistas.scorp.url:}") String url, OrigensLigadas origens, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/VerificadorDoScorp.java | 38 | @Value("${vistas.scorp.token-file:}") String arquivoDoToken, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/VerificadorDoScorp.java | 39 | @Value("${vistas.arquivos.token-file:}") String arquivoDoTokenDoLicenciamento, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/VerificadorDoScorp.java | 40 | @Value("${vistas.arquivos.token:}") String token, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/VerificadorDoScorp.java | 41 | @Value("${vistas.scorp.auth-mode:}") String modo, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/VerificadorDoScorp.java | 42 | @Value("${vistas.arquivos.auth-mode:bearer}") String modoDoLicenciamento, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/VerificadorDoScorp.java | 43 | @Value("${vistas.scorp.cookie-name:}") String nomeDoCookie, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/scorp/VerificadorDoScorp.java | 44 | @Value("${vistas.arquivos.cookie-name:}") String nomeDoCookieDoLicenciamento) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/DocumentosDoSigaJdbc.java | 22 | …desligar uma origem (propriedade {@code vistas.documentos.origens.<origem>}). Uma origem desligada não é consu… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/SigaCondicoes.java | 9 | static final String CONFIGURADO = "!'${vistas.datasource.siga.url:}'.isBlank()"; | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/SigaCondicoes.java | 10 | static final String NAO_CONFIGURADO = "'${vistas.datasource.siga.url:}'.isBlank()"; | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/SigaDataSourceConfig.java | 23 | @Value("${vistas.datasource.siga.url}") String url, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/SigaDataSourceConfig.java | 24 | @Value("${vistas.datasource.siga.username:}") String usuario, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/SigaDataSourceConfig.java | 25 | @Value("${vistas.datasource.siga.password:}") String senha, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/SigaDataSourceConfig.java | 26 | @Value("${vistas.datasource.siga.hikari.maximum-pool-size:2}") int poolMaximo, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/SigaDataSourceConfig.java | 27 | @Value("${vistas.datasource.siga.hikari.connection-timeout:5000}") long timeoutConexaoMs, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/SigaDataSourceConfig.java | 28 | @Value("${vistas.datasource.siga.hikari.max-lifetime:1500000}") long vidaMaximaMs, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/integration/siga/SigaDataSourceConfig.java | 29 | @Value("${vistas.datasource.siga.hikari.read-timeout:30000}") long timeoutLeituraMs) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/java/br/gov/mt/sema/vistas/shared/api/error/GlobalExceptionHandler.java | 38 | @ExceptionHandler(VistasException.class) | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/api/error/GlobalExceptionHandler.java | 39 | ResponseEntity<Object> aoFalharNegocio(VistasException ex, HttpServletRequest request) { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/CredencialArquivosRecusadaException.java | 6 | public class CredencialArquivosRecusadaException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/DocumentoSigilosoException.java | 6 | public class DocumentoSigilosoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/DossieExpiradoException.java | 6 | public class DossieExpiradoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/DossieSemArquivoException.java | 6 | public class DossieSemArquivoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/DossieSemDecisaoPendenteException.java | 6 | public class DossieSemDecisaoPendenteException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/NaoAutenticadoException.java | 5 | public class NaoAutenticadoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/NaoInteressadoException.java | 6 | public class NaoInteressadoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/ParametroInvalidoException.java | 5 | public class ParametroInvalidoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/PerfilSemPermissaoException.java | 5 | public class PerfilSemPermissaoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/RecursoNaoEncontradoException.java | 5 | public class RecursoNaoEncontradoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/SigaIndisponivelException.java | 6 | public class SigaIndisponivelException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/TicketInvalidoException.java | 6 | public class TicketInvalidoException extends VistasException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/VistasException.java | 6 | public class VistasException extends RuntimeException { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/shared/application/exception/VistasException.java | 10 | public VistasException(ErrorCode errorCode, String mensagemSegura) { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/VistasApplication.java | 7 | public class VistasApplication { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/java/br/gov/mt/sema/vistas/VistasApplication.java | 10 | SpringApplication.run(VistasApplication.class, args); | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/main/resources/application-local.yml | 7 | vistas: | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/resources/application-local.yml | 9 | senha: ${VISTAS_DEMO_SENHA:vistas-demo} | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/resources/application.yml | 3 | name: siga-vistas-api | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| api/src/main/resources/application.yml | 5 | # Os datasources são configurados pelo projeto (siga agora; vistas na versão definitiva). | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| api/src/main/resources/application.yml | 45 | vistas: | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/main/resources/application.yml | 59 | senha: ${VISTAS_DEMO_SENHA:} | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/resources/application.yml | 63 | diretorio: ${VISTAS_DOSSIE_DIRETORIO:/var/tmp/vistas} | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/resources/application.yml | 91 | …ização (S3): cache da versão em PDF (em VISTAS_DOSSIE_DIRETORIO/visualizacao) e fila de conversões.… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/main/resources/application.yml | 97 | …as em ZIP (S4): limites; temporários em VISTAS_DOSSIE_DIRETORIO/geometrias, com o espaço mínimo do dossiê.… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| api/src/test/java/br/gov/mt/sema/vistas/acesso/application/AdministrarUsuariosTest.java | 60 | .isInstanceOfSatisfying(VistasException.class, | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/acesso/application/PoliticaDeAcessoTest.java | 179 | assertThatThrownBy(acao::run).isInstanceOfSatisfying(VistasException.class, | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/acesso/MatrizDeAcessoTest.java | 78 | @TestPropertySource(properties = {"vistas.scorp.sigilo-padrao=true", "vistas.conversao.habilitada=false"}) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/acesso/MatrizDeAcessoTest.java | 135 | registro.add("vistas.scorp.url", SCORP::url); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/ArchitectureTest.java | 30 | @AnalyzeClasses(packages = "br.gov.mt.sema.vistas", importOptions = ImportOption.DoNotIncludeTests.class) | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/ArchitectureTest.java | 33 | private static final String RAIZ = "br.gov.mt.sema.vistas."; | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/api/ConsultaDaAuditoriaHttpTest.java | 43 | @TestPropertySource(properties = "vistas.auditoria.csv-max-linhas=5") | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/api/ConsultaDaAuditoriaHttpTest.java | 393 | assertThat(resposta.getHeader("X-Vistas-Truncado")).isNull(); | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/api/ConsultaDaAuditoriaHttpTest.java | 423 | assertThat(truncado.getHeader("X-Vistas-Truncado")).isEqualTo("true"); | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/api/ConsultaDaAuditoriaHttpTest.java | 425 | assertThat(doOperador.getHeader("X-Vistas-Truncado")).isNull(); | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/api/ConsultaDaAuditoriaHttpTest.java | 446 | assertThat(resposta.getHeader("X-Vistas-Truncado")).isEqualTo("true"); | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/domain/EventoDeAuditoriaTest.java | 139 | … + " Bearer abc123 " + jwt + " /var/tmp/vistas/pedido/a.pdf"… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/domain/EventoDeAuditoriaTest.java | 144 | .requerimento(1L, sujo, sujo).arquivo("/var/tmp/vistas/" + chave + "/dossie.pdf").detalhe(sujo) | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/domain/EventoDeAuditoriaTest.java | 145 | .documentos(List.of(new DocumentoDoEvento("Requerimento", "10", sujo, "/var/tmp/vistas/a.pdf", false, 1))) | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/EventosDeDocumentosHttpTest.java | 180 | doThrow(new FalhaNoDocumentoException("recusado em /var/tmp/vistas/pedido/a.pdf", 1)) | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/VarreduraDaAuditoriaHttpTest.java | 56 | private static final String NOME_DO_COOKIE = "sessao_vistas"; | B | cookie | Bloco 5/6: decisão (cookie da sessão; só aparece em teste) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/VarreduraDaAuditoriaHttpTest.java | 74 | registro.add("vistas.scorp.url", SCORP::url); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/VarreduraDaAuditoriaHttpTest.java | 75 | registro.add("vistas.arquivos.url", () -> "http://" + HOST_INTERNO + ":8443/arquivos"); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/VarreduraDaAuditoriaHttpTest.java | 76 | registro.add("vistas.arquivos.auth-mode", () -> "cookie"); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/auditoria/VarreduraDaAuditoriaHttpTest.java | 77 | registro.add("vistas.arquivos.cookie-name", () -> NOME_DO_COOKIE); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/configuracao/application/GerirConexoesTest.java | 137 | assertThatThrownBy(() -> conexoes.testar("falsa")).isInstanceOfSatisfying(VistasException.class, | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/configuracao/application/ParametrosEmVigorTest.java | 27 | }, new MockEnvironment().withProperty("vistas.dossie.ttl-minutos", "45")); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/configuracao/ParametrosHttpTest.java | 130 | …ntain("sigilo-padrao", "sigiloPadrao", "vistas.arquivos", "datasource", diretorioTemporario().toString());… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/configuracao/ParametrosHttpTest.java | 163 | …r (String nome : List.of("nao.existe", "vistas.scorp.sigilo-padrao", "SCORP_SIGILO_PADRAO", "LICENCIAMENTO_API… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/configuracao/ParametrosHttpTest.java | 164 | "vistas.arquivos.url")) { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/consulta/api/ConsultaHttpTest.java | 55 | @SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url="}) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/consulta/api/SigaForaDoArHttpTest.java | 60 | @SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url="}) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/consulta/api/SigaForaDoArHttpTest.java | 71 | "vistas.demo.senha=senha-de-teste", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/consulta/api/SigaForaDoArHttpTest.java | 72 | "vistas.datasource.siga.url=jdbc:oracle:thin:@//127.0.0.1:1/INEXISTENTE", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/consulta/api/SigaForaDoArHttpTest.java | 73 | "vistas.datasource.siga.username=x", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/consulta/api/SigaForaDoArHttpTest.java | 74 | "vistas.datasource.siga.password=x", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/consulta/api/SigaForaDoArHttpTest.java | 75 | "vistas.datasource.siga.hikari.connection-timeout=1000"}) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/ContratoHttpTest.java | 24 | @SpringBootTest(properties = "vistas.demo.senha=senha-de-teste") | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/ConteudoHttpTest.java | 64 | @TestPropertySource(properties = {"vistas.visualizacao.conversoes-simultaneas=1", "vistas.visualizacao.fila-de… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/ConteudoHttpTest.java | 183 | .andExpect(header().doesNotExist("X-Vistas-Convertido-De")) | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/ConteudoHttpTest.java | 196 | .andExpect(header().string("X-Vistas-Convertido-De", "XLSX")) | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/ConteudoHttpTest.java | 203 | .andExpect(header().string("X-Vistas-Convertido-De", "DOCX")); | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/ConteudoHttpTest.java | 206 | eq(new br.gov.mt.sema.vistas.documento.domain.TipoDetectado( | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/ConteudoHttpTest.java | 207 | br.gov.mt.sema.vistas.documento.domain.TipoReal.PLANILHA, "xlsx")), any(Path.class), | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/ConteudoHttpTest.java | 217 | .andExpect(header().string("X-Vistas-Convertido-De", "PNG")) | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/GeometriasHttpTest.java | 70 | @SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url=", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/GeometriasHttpTest.java | 71 | "vistas.arquivos.token=TOKEN-GEOMETRIAS-DE-TESTE", "vistas.dossie.espaco-minimo-mb=1", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/GeometriasHttpTest.java | 72 | "vistas.dossie.download-timeout-segundos=1", "vistas.dossie.download-total-maximo-segundos=2", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/GeometriasHttpTest.java | 73 | "vistas.geometrias.max-mb=1", "vistas.geometrias.max-arquivos=6", "vistas.agendamento.enabled=false"}) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/GeometriasHttpTest.java | 92 | DIRETORIO = Files.createTempDirectory("vistas-geometrias-teste"); | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/GeometriasHttpTest.java | 100 | registro.add("vistas.arquivos.url", SERVIDOR::url); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/GeometriasHttpTest.java | 101 | registro.add("vistas.dossie.diretorio", DIRETORIO::toString); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/api/ScorpHttpTest.java | 67 | registro.add("vistas.scorp.url", SCORP::url); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/documento/application/CasosDeUsoDocumentoTest.java | 72 | br.gov.mt.sema.vistas.testsupport.ScorpDeTeste.desligado()); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/api/DossieHttpTest.java | 43 | @org.springframework.test.context.TestPropertySource(properties = "vistas.conversao.habilitada=false") | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/api/DossieHttpTest.java | 299 | …da.<Path>getArgument(1), br.gov.mt.sema.vistas.testsupport.PdfsDeTeste.bytes(1, "novo"));… | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/api/DossiesGeradosHttpTest.java | 25 | @org.springframework.test.context.TestPropertySource(properties = "vistas.conversao.habilitada=false") | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/api/DownloadGrandeEmStreamingTest.java | 49 | mock(br.gov.mt.sema.vistas.dossie.application.ListarDossiesGerados.class)); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/api/DownloadGrandeEmStreamingTest.java | 80 | mock(br.gov.mt.sema.vistas.dossie.application.ListarDossiesGerados.class)); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/api/SistemasExternosForaDoArHttpTest.java | 63 | @SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url=", "vistas.arqui… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/api/SistemasExternosForaDoArHttpTest.java | 64 | "vistas.dossie.diretorio=${java.io.tmpdir}/vistas-teste-externos", "vistas.dossie.espaco-minimo-mb=0"}) | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/api/SistemasExternosForaDoArHttpTest.java | 83 | @SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url=", "vistas.arqui… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/api/SistemasExternosForaDoArHttpTest.java | 84 | "vistas.arquivos.token=", "vistas.dossie.diretorio=${java.io.tmpdir}/vistas-teste-externos", "vistas.dossie.es… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/application/CasosDeUsoDossieTest.java | 99 | throw new br.gov.mt.sema.vistas.shared.application.exception.NaoAutenticadoException("sem sessão"); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/application/CasosDeUsoDossieTest.java | 275 | .containsOnly(br.gov.mt.sema.vistas.dossie.domain.SituacaoItem.OK); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/application/CasosDeUsoDossieTest.java | 345 | .containsKey(br.gov.mt.sema.vistas.dossie.domain.TicketDeDownload.resumo(emitido.ticket())); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/application/CasosDeUsoDossieTest.java | 1002 | .isInstanceOf(br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException.class); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/application/CasosDeUsoDossieTest.java | 1004 | .isInstanceOf(br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException.class); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/application/CasosDeUsoDossieTest.java | 1006 | .isInstanceOf(br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException.class); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/application/GeradorDeDossieTest.java | 283 | public int montar(br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa capa, java.util.List<DocumentoParaMontagem> … | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/application/GeradorDeDossieTest.java | 301 | public int montar(br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa capa, java.util.List<DocumentoParaMontagem> … | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/infrastructure/agendamento/AgendamentoDaLimpezaTest.java | 15 | @SpringBootTest(properties = {"vistas.demo.senha=x", "vistas.datasource.siga.url=", "vistas.arquivos.url=", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/infrastructure/agendamento/AgendamentoDaLimpezaTest.java | 16 | "vistas.dossie.diretorio=${java.io.tmpdir}/vistas-teste-agendamento"}) | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/infrastructure/agendamento/AgendamentoDaLimpezaTest.java | 29 | @SpringBootTest(properties = {"vistas.demo.senha=x", "vistas.datasource.siga.url=", "vistas.arquivos.url=", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/dossie/infrastructure/agendamento/AgendamentoDaLimpezaTest.java | 30 | "vistas.agendamento.enabled=false", "vistas.dossie.diretorio=${java.io.tmpdir}/vistas-teste-agendamento"}) | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/libreoffice/ConversorLibreOfficeTest.java | 200 | br.gov.mt.sema.vistas.testsupport.PdfsDeTeste.bytes(1, "Linha 1930")); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/integration/libreoffice/DesempenhoDaConversaoTest.java | 67 | @SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url=", "vistas.arqui… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/libreoffice/DesempenhoDaConversaoTest.java | 341 | for (Path t : temporarios.filter(p -> p.getFileName().toString().startsWith("vistas-conversor-")).toList()) { | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/libreoffice/NumeracaoComPlanilhaConvertidaTest.java | 36 | @SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url=", "vistas.arqui… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/libreoffice/ProcessosDoConversorTest.java | 86 | br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.class, | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosDoLicenciamentoHttpTest.java | 54 | ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.vistas")).addAppender(log); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosDoLicenciamentoHttpTest.java | 60 | ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.vistas")).detachAppender(log); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosDoLicenciamentoHttpTest.java | 488 | .withPropertyValues("vistas.arquivos.url=" + url(), "vistas.arquivos.token=" + TOKEN, | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/licenciamento/ArquivosDoLicenciamentoHttpTest.java | 489 | "vistas.arquivos.token-file=" + arquivo) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttpTest.java | 65 | ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.vistas")).addAppender(log); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/integration/scorp/DocumentosDoScorpHttpTest.java | 71 | ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.vistas")).detachAppender(log); | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/integration/scorp/ScorpFumacaTest.java | 35 | @SpringBootTest(properties = "vistas.agendamento.enabled=false") | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/ConexaoDoSigaLiberadaTest.java | 95 | br.gov.mt.sema.vistas.testsupport.ScorpDeTeste.desligado()), politica, | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/ConexaoDoSigaLiberadaTest.java | 98 | Map<String, Integer> conexoesAtivasVistasNoDownload = new ConcurrentHashMap<>(); | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/ConexaoDoSigaLiberadaTest.java | 101 | conexoesAtivasVistasNoDownload.put(chave, dataSource.getHikariPoolMXBean().getActiveConnections()); | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/ConexaoDoSigaLiberadaTest.java | 107 | conexoesAtivasVistasNoDownload.put("validacao-" + arquivo.getFileName(), dataSource.getHikariPoolMXBean().getA… | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/ConexaoDoSigaLiberadaTest.java | 111 | conexoesAtivasVistasNoDownload.put("montagem", dataSource.getHikariPoolMXBean().getActiveConnections()); | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/ConexaoDoSigaLiberadaTest.java | 216 | assertThat(conexoesAtivasVistasNoDownload).isNotEmpty().containsKeys("montagem", "K-DP-1", "K-DR-13", "K-CO-21… | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/ConexaoDoSigaLiberadaTest.java | 217 | assertThat(conexoesAtivasVistasNoDownload.values()).as("conexões ativas do SIGA durante download, validação e … | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/RequerimentosDoSigaJdbcTest.java | 119 | void situacaoQueOVistasNaoConheceViraDesconhecidaComOTextoCru() { | B | nome de classe/bean | Bloco 5: renomear classe |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/SigaConfiguracaoTest.java | 46 | contexto.withPropertyValues("vistas.datasource.siga.url=").run(ctx -> { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/SigaConfiguracaoTest.java | 50 | contexto.withPropertyValues("vistas.datasource.siga.url=   ").run(ctx -> | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/SigaConfiguracaoTest.java | 57 | "vistas.datasource.siga.url=jdbc:oracle:thin:@//127.0.0.1:1/INEXISTENTE", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/SigaConfiguracaoTest.java | 58 | "vistas.datasource.siga.username=leitura", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/SigaConfiguracaoTest.java | 59 | "vistas.datasource.siga.password=nao-usada").run(ctx -> { | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/integration/siga/SigaOracleTeste.java | 26 | static final String LEITURA = "VISTAS_LEITURA_SIGA"; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/java/br/gov/mt/sema/vistas/testsupport/BaseHttpDeDocumentos.java | 49 | @SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url=", | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/testsupport/BaseHttpDeDocumentos.java | 50 | "vistas.arquivos.url=", "vistas.arquivos.token=TOKEN-ARQUIVOS-DE-TESTE", "vistas.dossie.espaco-minimo-mb=0"}) | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/java/br/gov/mt/sema/vistas/testsupport/BaseHttpDeDocumentos.java | 65 | DIRETORIO = Files.createTempDirectory("vistas-dossies-teste"); | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| api/src/test/java/br/gov/mt/sema/vistas/testsupport/BaseHttpDeDocumentos.java | 78 | registro.add("vistas.dossie.diretorio", DIRETORIO::toString); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| api/src/test/resources/sql/siga/01-recorte.sql | 132 | CREATE USER VISTAS_LEITURA_SIGA IDENTIFIED BY "leitura_teste"; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 133 | GRANT CREATE SESSION TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 134 | GRANT SELECT ON SIGA2_LICENCIAMENTO.REQUERIMENTO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 135 | GRANT SELECT ON SIGA2_LICENCIAMENTO.PESSOA TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 136 | GRANT SELECT ON SIGA2_LICENCIAMENTO.TIPO_PROCESSO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 137 | GRANT SELECT ON SIGA2_CAMUNDA.ACT_RU_EXECUTION TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 138 | GRANT SELECT ON SIGA2_CAMUNDA.ACT_RU_TASK TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 139 | GRANT SELECT ON SIGA2_CAMUNDA.ACT_RE_PROCDEF TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 140 | GRANT SELECT ON SIGA2_LICENCIAMENTO.TIPO_REQUERIMENTO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 141 | GRANT SELECT ON SIGA2_LICENCIAMENTO.PRIORIDADE_PROCESSO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 142 | GRANT SELECT ON SIGA2_LICENCIAMENTO.PRIORIDADE_PROCESSO_CRITERIO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 143 | GRANT SELECT ON SIGA2_LICENCIAMENTO.REQUERIMENTO_LICENCIAMENTO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 144 | GRANT SELECT ON SIGA2_LICENCIAMENTO.OBJETIVO_LICENCIAMENTO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 145 | GRANT SELECT ON SIGA2_LICENCIAMENTO.SERVIDOR TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 146 | GRANT SELECT ON SIGA2_OUTORGA.REQUERIMENTO_OUTORGA TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 147 | GRANT SELECT ON SIGA2_OUTORGA.OBJETIVO_OUTORGA TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 148 | GRANT SELECT ON SIGA2_OUTORGA.OBJETIVO_REQUERIMENTO_OUTORGA TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 149 | GRANT SELECT ON SIGA2_OUTORGA.FINALIDADE TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 150 | GRANT SELECT ON SIGA2_OUTORGA.FINALIDADE_ATIVIDADE TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 151 | GRANT SELECT ON SIGA2_OUTORGA.REQUERIMENTO_FINALIDADE_ATIVIDADE TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/01-recorte.sql | 152 | GRANT SELECT ON SIGA2_ARRECADACAO.ATIVIDADE TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 98 | GRANT SELECT ON SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 99 | GRANT SELECT ON SIGA2_LICENCIAMENTO.DOCUMENTO_REQUERIMENTO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 100 | GRANT SELECT ON SIGA2_LICENCIAMENTO.COMPLEMENTACAO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 101 | GRANT SELECT ON SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 102 | GRANT SELECT ON SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM_ARQUIVO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 103 | GRANT SELECT ON SIGA2_LICENCIAMENTO.IMOVEL TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 104 | GRANT SELECT ON SIGA2_LICENCIAMENTO.DOCUMENTO_IMOVEL TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 105 | GRANT SELECT ON SIGA2_LICENCIAMENTO.REQUERIMENTO_REPRESENTANTE_LEGAL TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 106 | GRANT SELECT ON SIGA2_LICENCIAMENTO.REQ_REP_LEGAL_DOCUMENTO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 107 | GRANT SELECT ON SIGA2_LICENCIAMENTO.REQUERIMENTO_RESPONSAVEL_TECNICO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| api/src/test/resources/sql/siga/04-documentos-s2.sql | 108 | GRANT SELECT ON SIGA2_LICENCIAMENTO.REQ_RESP_TECNICO_DOCUMENTO TO VISTAS_LEITURA_SIGA; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| CLAUDE.md | 10 | …LICENCIAMENTO_API_*', 'SCORP_API_URL', 'VISTAS_DB_*'); 'environment' tem precedência sobre 'env_file'. Nunca s… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| CLAUDE.md | 23 | … tem a documentação ('docs/'), a skill 'vistas-banco' ('.claude/skills/') e o código: 'api/' (Spring Boot, Jav… | B | skill | Bloco 6: decisão (skill e nome do schema próprio) |
| CLAUDE.md | 25 | **Carregue a skill 'vistas-banco' antes de qualquer trabalho de código ou banco.** Ela contém os padrões detal… | B | skill | Bloco 6: decisão (skill e nome do schema próprio) |
| CLAUDE.md | 48 | …modular por domínio sob 'br.gov.mt.sema.vistas', fiscalizado por ArchUnit.… | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| CLAUDE.md | 49 | …eferência visual é 'docs/prototipo/siga-vistas-prototipo.html'.… | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| CLAUDE.md | 86 | - **'docs/padroes/' e '.claude/skills/vistas-banco/references/' têm o mesmo conteúdo.** Ao alterar um, altere … | B | skill | Bloco 6: decisão (skill e nome do schema próprio) |
| compose.local.yaml | 12 | - dossies-tmp:/var/tmp/vistas | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| compose.local.yaml | 14 | …CENCIAMENTO_API_TOKEN_FILE=/run/secrets-vistas/token-arquivos.… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| compose.local.yaml | 16 | # - ./.secrets:/run/secrets-vistas:ro | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| compose.verificacao.yaml | 20 | VISTAS_DB_URL: "" | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| compose.verificacao.yaml | 21 | VISTAS_DB_USER: "" | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| compose.verificacao.yaml | 22 | VISTAS_DB_PASSWORD: "" | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| compose.yaml | 1 | name: siga-vistas | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| docs/02-requisitos.md | 53 | …o contra fórmula e aviso de corte em 'X-Vistas-Truncado'. Nenhuma rota altera ou apaga evento. \|… | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| docs/03-arquitetura.md | 31 | …or domínio, pacote raiz 'br.gov.mt.sema.vistas', fiscalizado por ArchUnit \|… | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| docs/03-arquitetura.md | 86 | \| 'SIGA_DB_USER' / 'SIGA_DB_PASSWORD' \| Usuário somente leitura ('VISTAS_LEITURA_SIGA') \| | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| docs/03-arquitetura.md | 89 | \| 'VISTAS_DEMO_SENHA' \| Senha única dos usuários de demonstração (padrão 'vistas-demo' só no perfil 'local') \| | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/03-arquitetura.md | 90 | \| 'VISTAS_DB_POOL_MAX' \| Tamanho máximo do pool do schema próprio (padrão 3 na fase inicial) \| | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/03-arquitetura.md | 92 | \| 'VISTAS_DB_URL' / 'VISTAS_DB_USER' / 'VISTAS_DB_PASSWORD' \| Schema próprio (somente na versão definitiva) \| | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/03-arquitetura.md | 106 | - 'VISTAS_LEITURA_SIGA': no banco do SIGA, apenas 'CREATE SESSION' e 'SELECT' nas tabelas listadas em [04 — Mo… | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| docs/03-arquitetura.md | 107 | - 'VISTAS' (owner) e 'APP_VISTAS' (aplicação): schema próprio, só na versão definitiva. | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/04-modelo-de-dados.md | 241 | ## Privilégios do usuário de leitura ('VISTAS_LEITURA_SIGA') | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| docs/05-integracoes.md | 66 | …ontada somente leitura em '/run/secrets-vistas' por uma linha comentada do 'compose.local.yaml', e o '.env.loc… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| docs/05-integracoes.md | 99 | …rabalho da aplicação ('<java.io.tmpdir>/vistas-conversor-<id>'), apagado ao parar. Na imagem, 'java.io.tmpdir'… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| docs/06-geracao-do-dossie.md | 159 | …ados ficam no **diretório do pedido** ('VISTAS_DOSSIE_DIRETORIO/<id>/', um arquivo por posição no pedido) até … | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/06-geracao-do-dossie.md | 176 | …dossiê, numa pasta temporária própria ('VISTAS_DOSSIE_DIRETORIO/geometrias'), e só monta o ZIP se nenhuma falh… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/07-api.md | 13 | Usuários de demonstração (senha única 'vistas-demo' no perfil 'local'; configurável por 'VISTAS_DEMO_SENHA'): … | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/07-api.md | 195 | …, para uma pasta temporária própria em 'VISTAS_DOSSIE_DIRETORIO/geometrias', com a checagem de 'DOSSIE_ESPACO_… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/07-api.md | 210 | … e JPG pelo PDFBox), com o cabeçalho 'X-Vistas-Convertido-De' (ex.: 'XLSX'). Tipo sem conversão (compactado, n… | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| docs/07-api.md | 212 | - **Cache** em 'VISTAS_DOSSIE_DIRETORIO/visualizacao', uma pasta por '(origem, id)' com o original e o PDF: va… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/07-api.md | 400 | …' corta nos mais recentes e responde 'X-Vistas-Truncado: true'.… | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| docs/08-telas.md | 3 | O protótipo navegável ('prototipo/siga-vistas-prototipo.html') é a referência visual: tema claro, paleta verde… | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| docs/08-telas.md | 141 | …DF a partir de <formato>" (cabeçalho 'X-Vistas-Convertido-De'), permitindo conferir a conversão antes de gerar… | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| docs/08-telas.md | 200 | …e nome do 'Content-Disposition'; com 'X-Vistas-Truncado: true' mostra o aviso de que o arquivo foi cortado.… | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| docs/09-decisoes.md | 65 | …o não grava nada no SIGA, nem o evento 'VISTAS_AO_PROCESSO'. Toda a auditoria fica no schema próprio.… | B | nome de evento descartado (D14) | Não muda (citado como decisão) |
| docs/09-decisoes.md | 111 | …y) e, na versão definitiva, datasource 'vistas' (schema próprio, JPA, Flyway, 'ddl-auto=validate'). No protóti… | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/09-decisoes.md | 186 | …**. Cada origem pode ser desligada por 'vistas.documentos.origens.<origem>'; origem desligada não é consultada… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/09-decisoes.md | 217 | …paga pastas com nome de UUID dentro de 'VISTAS_DOSSIE_DIRETORIO', para nunca tocar em arquivos que não são do … | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/09-decisoes.md | 280 | …imagem da 'api' já roda como o usuário 'vistas'.… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| docs/09-decisoes.md | 444 | …ntent-Disposition', com aviso quando 'X-Vistas-Truncado'. A proteção contra injeção de fórmula é do servidor (… | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| docs/10-pendencias-e-riscos.md | 77 | …até 'VISUALIZACAO_TTL_MINUTOS' (10) em 'VISTAS_DOSSIE_DIRETORIO/visualizacao' \| Quem lê o disco do container l… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/11-auditoria.md | 114 | - Não há integração com o SIGA: o evento 'VISTAS_AO_PROCESSO' não é gravado lá (D14). | B | nome de evento descartado (D14) | Não muda (citado como decisão) |
| docs/12-configuracoes.md | 49 | …entram com a senha única do protótipo ('VISTAS_DEMO_SENHA'); não há autenticação nova (D09).… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/12-configuracoes.md | 65 | …; 24; em memória, some num reinício) e 'VISTAS_DOSSIE_DIRETORIO'. Detalhes de tempo e novas tentativas em docs… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/12-configuracoes.md | 69 | …ga ao fim). As origens são ligadas por 'vistas.documentos.origens.<origem>' ('true'/'false'; 'EXTRATO_PUBLICAC… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/12-configuracoes.md | 102 | …A origem também pode ser desligada por 'vistas.documentos.origens.scorp=false'. Detalhes em docs/05.… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/12-configuracoes.md | 104 | …tempos de download do dossiê e a pasta 'VISTAS_DOSSIE_DIRETORIO/geometrias' (cada download apaga a sua; o pico… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/12-configuracoes.md | 106 | …_FILA_CONVERSOES' (4). O cache fica em 'VISTAS_DOSSIE_DIRETORIO/visualizacao'. A conversão usa os parâmetros d… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/12-configuracoes.md | 120 | …imite do CSV, com aviso no cabeçalho 'X-Vistas-Truncado'). Valor fora da faixa cai no padrão, com aviso no log… | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| docs/12-configuracoes.md | 131 | …'dossie.origem.<origem>' (7 origens) \| 'vistas.documentos.origens.<origem>' \| ligado ou desligado \| imediato \|… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/13-padroes-tecnicos.md | 3 | SIGA Vistas é um serviço externo ao SIGA Licenciamento que consulta requerimentos e processos, lista os docume… | B | skill | Bloco 6: decisão (skill e nome do schema próprio) |
| docs/13-padroes-tecnicos.md | 27 | Pacote raiz 'br.gov.mt.sema.vistas' (nome do projeto provisório). | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| docs/13-padroes-tecnicos.md | 159 | …icação. Em Packages (perfil) abra 'siga-vistas/api' e 'siga-vistas/front' → Package settings: confira *Manage … | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| docs/14-plano-de-sprints.md | 16 | \| 1 \| Usuário 'VISTAS_LEITURA_SIGA' com 'SELECT' nas tabelas do [04](04-modelo-de-dados.md) \| P10 \| S1 \| | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| docs/14-plano-de-sprints.md | 43 | - 'api': Spring Boot 3 / Java 21, 'VistasApplication', 'shared' (ProblemDetail, 'errorCode', filtro de correla… | B | nome de classe/bean | Bloco 5: renomear classe |
| docs/casos-de-teste.md | 35 | …Tamanho do PDF \| Maior uso de '/var/tmp/vistas' \| Download por ticket? \| Observação \|… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| docs/casos-de-teste.md | 84 | …Limite do container \| Pico de '/var/tmp/vistas' \| 'soffice' vivos depois do 'down' \|… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| docs/padroes/agendamento.md | 15 | …a grupo, com usuário de banco próprio ('VISTAS_EXPURGO') \|… | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/agendamento.md | 24 | …s e horários vêm de configuração (ex.: 'vistas.agendamento.limpeza-dossie=PT5M'), nunca fixos no código.… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/padroes/ambientes-e-docker.md | 17 | …_AUTH_MODE', 'SCORP_API_COOKIE_NAME' e 'VISTAS_DB_*'. A chave 'environment' tem precedência sobre o 'env_file'… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/padroes/ambientes-e-docker.md | 31 | \| 'VISTAS_DEMO_SENHA' \| Senha única dos usuários de demonstração; o padrão ('vistas-demo') só existe no perfil… | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/padroes/ambientes-e-docker.md | 32 | \| 'VISTAS_DOSSIE_DIRETORIO' \| Diretório temporário dos dossiês (padrão '/var/tmp/vistas') \| | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/padroes/ambientes-e-docker.md | 33 | \| 'VISTAS_DB_POOL_MAX' \| Tamanho máximo do pool do schema próprio (padrão 3 na fase inicial) \| | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/padroes/ambientes-e-docker.md | 35 | \| 'VISTAS_DB_URL', 'VISTAS_DB_USER', 'VISTAS_DB_PASSWORD' \| Schema próprio (definitivo) \| | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/padroes/ambientes-e-docker.md | 43 | \| 'vistas.documentos.origens.<origem>' \| 'true'/'false' por origem de documento (as seis origens da Vista liga… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/padroes/connection-pooling.md | 8 | vistas: | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/padroes/connection-pooling.md | 24 | vistas:   # somente na versão definitiva | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/connection-pooling.md | 25 | url: ${VISTAS_DB_URL} | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/padroes/connection-pooling.md | 26 | username: ${VISTAS_DB_USER} | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/padroes/connection-pooling.md | 27 | password: ${VISTAS_DB_PASSWORD} | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/padroes/connection-pooling.md | 29 | pool-name: vistas | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/padroes/connection-pooling.md | 30 | maximum-pool-size: ${VISTAS_DB_POOL_MAX:3} | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| docs/padroes/estrutura-de-pastas.md | 8 | siga-vistas/ | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| docs/padroes/estrutura-de-pastas.md | 9 | ├── .claude/skills/vistas-banco/   # skill com estes padrões (SKILL.md + references/) | B | skill | Bloco 6: decisão (skill e nome do schema próprio) |
| docs/padroes/estrutura-de-pastas.md | 20 | 'docs/padroes/' e '.claude/skills/vistas-banco/references/' têm o mesmo conteúdo: alterar um exige alterar o o… | B | skill | Bloco 6: decisão (skill e nome do schema próprio) |
| docs/padroes/estrutura-de-pastas.md | 31 | │   ├── java/br/gov/mt/sema/vistas/ | B | pacote (caminho de pastas) | Bloco 5: pacote br.gov.mt.sema.rastro |
| docs/padroes/estrutura-de-pastas.md | 32 | │   │   ├── VistasApplication.java | B | nome de classe/bean | Bloco 5: renomear classe |
| docs/padroes/estrutura-de-pastas.md | 80 | ├── java/br/gov/mt/sema/vistas/ | B | pacote (caminho de pastas) | Bloco 5: pacote br.gov.mt.sema.rastro |
| docs/padroes/estrutura-de-pastas.md | 122 | - Referência visual: 'docs/prototipo/siga-vistas-prototipo.html'. | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| docs/padroes/integracao-externa.md | 11 | …em filtrar vigência; origem desligada ('vistas.documentos.origens.<origem>') não é consultada; as origens são … | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/padroes/modulos-e-dependencias.md | 7 | … entrada/saída ficam em 'br.gov.mt.sema.vistas.<modulo>.api.dto'. O controller valida (Bean Validation), tradu… | B | pacote | Bloco 5: pacote br.gov.mt.sema.rastro |
| docs/padroes/nomenclatura.md | 25 | - Schema próprio: 'VISTAS' (nome provisório, acompanha o nome do projeto). Usuários: 'VISTAS' (owner), 'APP_VI… | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/persistencia-e-migrations.md | 8 | \| 'vistas' (definitivo) \| schema próprio Oracle: usuários, perfis, parâmetros, auditoria, fila \| Spring Data J… | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/persistencia-e-migrations.md | 11 | …us próprios datasources ('siga' agora; 'vistas' na versão definitiva).… | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/persistencia-e-migrations.md | 12 | - Os beans do SIGA só existem com 'vistas.datasource.siga.url' **preenchida e não vazia** ('@ConditionalOnExpr… | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| docs/padroes/persistencia-e-migrations.md | 13 | - Flyway é ligado **apenas** ao datasource 'vistas'. Nenhuma configuração de Flyway aponta para o SIGA. | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/persistencia-e-migrations.md | 17 | - Não há datasource 'vistas'. Os módulos 'acesso', 'auditoria', 'configuracao' e 'dossie' usam **adapters em m… | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/persistencia-e-migrations.md | 37 | …ED' (padrão 'true' quando o datasource 'vistas' existir). Não desabilitar antes de existir processo substituto… | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/README.md | 3 | … Estes arquivos são os mesmos da skill 'vistas-banco' usada no Claude Code; ao alterar um, altere o outro.… | B | skill | Bloco 6: decisão (skill e nome do schema próprio) |
| docs/padroes/seguranca-privilegios.md | 7 | \| 'VISTAS_LEITURA_SIGA' \| banco do SIGA \| 'CREATE SESSION' + 'SELECT' só nas tabelas (ou views 'VW_') listadas… | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| docs/padroes/seguranca-privilegios.md | 8 | \| 'VISTAS' (owner) \| schema próprio \| dono dos objetos; usado só pelo Flyway \| DBA da instância \| | B | schema/datasource próprio (versão definitiva) | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 9 | \| 'APP_VISTAS' \| schema próprio \| DML mínimo por tabela (abaixo), via role \| DBA da instância \| | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 16 | CREATE ROLE ROLE_APP_VISTAS; | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 17 | GRANT SELECT, INSERT ON VISTAS.AUDITORIA_EVENTO TO ROLE_APP_VISTAS; | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 18 | GRANT SELECT, INSERT ON VISTAS.AUDITORIA_EVENTO_DOCUMENTO TO ROLE_APP_VISTAS; | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 19 | GRANT SELECT, INSERT ON VISTAS.AUDITORIA_EVENTO_ALTERACAO TO ROLE_APP_VISTAS; | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 20 | GRANT SELECT, INSERT, UPDATE ON VISTAS.USUARIO TO ROLE_APP_VISTAS; | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 21 | GRANT SELECT, INSERT, UPDATE ON VISTAS.PARAMETRO TO ROLE_APP_VISTAS; | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 22 | GRANT SELECT, INSERT, UPDATE, DELETE ON VISTAS.DOSSIE_PEDIDO TO ROLE_APP_VISTAS; | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 23 | GRANT SELECT, INSERT, DELETE ON VISTAS.DOSSIE_PEDIDO_DOCUMENTO TO ROLE_APP_VISTAS; | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 24 | GRANT ROLE_APP_VISTAS TO APP_VISTAS; | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 28 | …: rotina agendada com usuário próprio ('VISTAS_EXPURGO') com 'DELETE' só nas tabelas de auditoria e só por dat… | B | schema/usuário do banco próprio | Fora desta sprint: nome do schema próprio da versão definitiva; decidir antes da definitiva |
| docs/padroes/seguranca-privilegios.md | 41 | WHERE GRANTEE IN ('APP_VISTAS','ROLE_APP_VISTAS','VISTAS_LEITURA_SIGA') ORDER BY 1, 3; | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| docs/padroes/seguranca-privilegios.md | 42 | SELECT * FROM DBA_SYS_PRIVS WHERE GRANTEE IN ('APP_VISTAS','VISTAS_LEITURA_SIGA'); | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| front/e2e/api-simulada.ts | 96 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/auditoria-acessos.e2e.ts | 38 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/auditoria-documentos.e2e.ts | 38 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/auditoria-documentos.e2e.ts | 66 | ...(c.truncar ? { 'X-Vistas-Truncado': 'true' } : {}), | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| front/e2e/configuracoes-parametros.e2e.ts | 59 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/configuracoes-usuarios.e2e.ts | 44 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/documentos-dossie.e2e.ts | 52 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/documentos-dossie.e2e.ts | 192 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/documentos-dossie.e2e.ts | 302 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/documentos-dossie.e2e.ts | 396 | …: 200, corpo: PDF_MINIMO, headers: { 'X-Vistas-Convertido-De': 'XLSX' }, atrasoMs: 700 }));… | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| front/e2e/dossies-gerados.e2e.ts | 56 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/geometrias.e2e.ts | 61 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/geracao-segundo-plano.e2e.ts | 50 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/largura-celular.e2e.ts | 59 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/e2e/pessoal.e2e.ts | 72 | await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste')); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/core/auth.service.spec.ts | 46 | expect(sessionStorage.getItem('vistas.token')).toBe('abc'); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/core/auth.service.spec.ts | 58 | sessionStorage.setItem('vistas.token', 'abc'); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/core/auth.service.spec.ts | 69 | sessionStorage.setItem('vistas.token', 'velho'); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/core/auth.service.spec.ts | 81 | sessionStorage.setItem('vistas.token', 'abc'); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/core/auth.service.ts | 7 | const CHAVE_TOKEN = 'vistas.token'; | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/features/auditoria/auditoria-api.service.spec.ts | 75 | 'X-Vistas-Truncado': 'true', | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| front/src/app/features/auditoria/auditoria-api.service.ts | 65 | truncado: resposta.headers.get('X-Vistas-Truncado') === 'true', | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| front/src/app/features/consulta/consulta.component.spec.ts | 426 | localStorage.setItem('vistas.recentes.u.demo', JSON.stringify([{ id: 7, rotulo: 'Processo 2825/2025' }])); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/features/consulta/consulta.component.spec.ts | 438 | 'vistas.recentes.u.demo', | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/features/detalhe/detalhe.component.spec.ts | 94 | expect(JSON.parse(localStorage.getItem('vistas.recentes.u.demo')!)).toEqual([ | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/features/documentos/documentos-api.service.spec.ts | 164 | headers: { 'X-Vistas-Convertido-De': 'XLSX', 'Content-Disposition': 'inline; filename="calculo.pdf"' }, | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| front/src/app/features/documentos/documentos-api.service.ts | 91 | convertidoDe: evento.headers.get('X-Vistas-Convertido-De'), | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| front/src/app/features/documentos/documentos-secao.component.ts | 81 | vistas.set(d.origem, d.origem === 'SCORP' ? 'Pessoal' : d.origemDescricao); | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| front/src/app/features/documentos/documentos.models.ts | 173 | /** Formato de origem (cabeçalho X-Vistas-Convertido-De, ex.: XLSX), só quando houve conversão. */ | B | cabeçalho HTTP | Bloco 5/6: DECISÃO do responsável (cabeçalho é contrato HTTP) |
| front/src/app/features/login/login.component.ts | 12 | /** Senha de demonstração do perfil local (vistas.demo.senha). Só existe para o clique rápido do protótipo. */ | B | propriedade de configuração | Bloco 5: renomear para rastro.* com alias (antigo vence) |
| front/src/app/features/login/login.component.ts | 13 | const SENHA_DEMO = 'vistas-demo'; | B | senha de demonstração | DECISÃO do responsável (senha padrão de demonstração) |
| front/src/app/features/login/login.component.ts | 83 | ? 'A senha de demonstração foi alterada no servidor. Informe a senha configurada em VISTAS_DEMO_SENHA.' | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| front/src/app/shared/recentes.service.spec.ts | 44 | expect(localStorage.getItem('vistas.recentes.operador.demo')).not.toContain('interessado'); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/shared/recentes.service.spec.ts | 83 | localStorage.setItem('vistas.recentes.operador.demo', '{nao-e-json'); | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/shared/recentes.service.spec.ts | 86 | localStorage.setItem('vistas.recentes.operador.demo', JSON.stringify([{ id: 'x' }, { id: 3, rotulo: 'C' }, 7])… | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/shared/recentes.service.spec.ts | 111 | 'vistas.recentes.operador.demo', | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/shared/recentes.service.spec.ts | 130 | 'vistas.recentes.operador.demo', | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/app/shared/recentes.service.ts | 75 | return login ? 'vistas.recentes.${login}' : null; | B | chave de armazenamento | Bloco 6: nova chave + fallback da antiga |
| front/src/styles/tokens.css | 1 | /* Tokens do protótipo (docs/prototipo/siga-vistas-prototipo.html). Tema claro apenas. */ | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| README.md | 3 | …CI](https://github.com/jvscherbach/siga-vistas/actions/workflows/ci.yml/badge.svg)](https://github.com/jvscher… | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| README.md | 88 | cd siga-vistas | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| README.md | 131 | Procure a linha 'Started VistasApplication'. | B | nome de classe/bean | Bloco 5: renomear classe |
| README.md | 147 | …sa processos em que é o interessado. \| 'vistas-demo' \|… | B | senha de demonstração | DECISÃO do responsável (senha padrão de demonstração) |
| README.md | 148 | …processos e os documentos sigilosos. \| 'vistas-demo' \|… | B | senha de demonstração | DECISÃO do responsável (senha padrão de demonstração) |
| README.md | 149 | \| 'admin.demo' \| Administrador \| Tudo: inclui Auditoria › Acessos e Configurações. \| 'vistas-demo' \| | B | senha de demonstração | DECISÃO do responsável (senha padrão de demonstração) |
| README.md | 151 | …no perfil 'local'. Para trocar, defina 'VISTAS_DEMO_SENHA' no '.env.local' e rode o passo 3 de novo (o clique … | B | variável de ambiente | NÃO renomear (decisão); nova variável só como alias |
| README.md | 182 | …a ao DBA do SIGA o usuário de leitura ('VISTAS_LEITURA_SIGA'), a URL do Oracle e a liberação de rede entre o s… | B | usuário do banco (nome sugerido ao DBA) | Decisão do responsável (pedido ao DBA) |
| README.md | 223 | 2. Em 'compose.local.yaml', descomente a linha '- ./.secrets:/run/secrets-vistas:ro' do serviço 'api'. | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| README.md | 224 | …CENCIAMENTO_API_TOKEN_FILE=/run/secrets-vistas/token-arquivos'. O caminho é o de **dentro do container**.… | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| README.md | 282 | …s). A API subiu quando aparece 'Started VistasApplication' e 'Tomcat started on port 8081'. Confira em outro t… | B | nome de classe/bean | Bloco 5: renomear classe |
| README.md | 306 | …, 'gestor.demo' ou 'admin.demo' (senha 'vistas-demo', igual ao Tutorial 1). Para parar, use 'Ctrl + C' em cada… | B | senha de demonstração | DECISÃO do responsável (senha padrão de demonstração) |
| README.md | 353 | \| **Erro de permissão em '/var/tmp/vistas'** (volume 'dossies-tmp') \| O volume foi criado com dono diferente. … | B | caminho/usuário/diretório | Bloco 6: decisão do responsável (caminho/usuário/volume) |
| README.md | 376 | - [Protótipo navegável](docs/prototipo/siga-vistas-prototipo.html) (abra no navegador; é a referência visual) | B | artefato/repositório/arquivo | Bloco 5/6: renomear artefato/imagem/repo |
| api/src/main/java/br/gov/mt/sema/vistas/documento/domain/CatalogoDeDocumentos.java | 24 | Set<String> chavesVistas = new HashSet<>(); | E | variável (chaves da Vista do SIGA) | Não muda (palavra comum) |
| api/src/main/java/br/gov/mt/sema/vistas/documento/domain/CatalogoDeDocumentos.java | 30 | if (documento.temChaveValida() && !chavesVistas.add(documento.chaveDoArquivo())) { | E | variável (chaves da Vista do SIGA) | Não muda (palavra comum) |
| docs/padroes/modulos-e-dependencias.md | 58 | Dependências previstas entre módulos de negócio: 'documento' → 'consulta', 'acesso'; 'dossie' → 'documento', '… | E | falso positivo ('previstas') | Não muda (palavra comum) |
| docs/padroes/persistencia-e-migrations.md | 39 | ### Tabelas previstas (ver 'docs/11-auditoria.md' e 'docs/12-configuracoes.md') | E | falso positivo ('previstas') | Não muda (palavra comum) |
| front/src/app/features/documentos/documentos-secao.component.ts | 78 | const vistas = new Map<string, string>(); | E | variável/palavra comum | Não muda (palavra comum) |
| front/src/app/features/documentos/documentos-secao.component.ts | 83 | return Array.from(vistas, ([codigo, descricao]) => ({ codigo, descricao })).sort((a, b) => a.descricao.localeC… | E | variável/palavra comum | Não muda (palavra comum) |

## 2. Pacote Java: `package` e `import` (classe B, bloco 5)

| Origem | Módulo | Arquivos | Linhas |
|---|---|---:|---:|
| main | (raiz) | 1 | 1 |
| main | acesso | 28 | 75 |
| main | auditoria | 33 | 89 |
| main | config | 11 | 33 |
| main | configuracao | 18 | 71 |
| main | consulta | 36 | 91 |
| main | documento | 48 | 143 |
| main | dossie | 50 | 188 |
| main | integration | 38 | 122 |
| main | shared | 19 | 35 |
| test | (raiz) | 2 | 12 |
| test | acesso | 7 | 35 |
| test | auditoria | 10 | 66 |
| test | config | 1 | 4 |
| test | configuracao | 5 | 35 |
| test | consulta | 7 | 45 |
| test | documento | 11 | 89 |
| test | dossie | 13 | 157 |
| test | integration | 31 | 167 |
| test | shared | 1 | 1 |
| test | testsupport | 5 | 22 |

## 3. Documentos históricos (classe C, não mudam)

| Arquivo | Ocorrências |
|---|---:|
| docs/homologacao/S2-roteiro-homologacao.md | 31 |
| docs/homologacao/S3-roteiro-homologacao.md | 3 |
| docs/homologacao/S4-roteiro-homologacao.md | 5 |
| docs/homologacao/S5-roteiro-homologacao.md | 1 |
| docs/progresso/ci-github-actions.md | 1 |
| docs/progresso/ci-sem-custo.md | 1 |
| docs/progresso/s3.md | 5 |
| docs/progresso/s4.md | 2 |
| docs/progresso/s5.md | 2 |
| docs/prompts/CI-github-actions.md | 1 |
| docs/prompts/CI-sem-custo.md | 2 |
| docs/prompts/S1-consulta.md | 9 |
| docs/prompts/S1.1-ajustes-consulta.md | 17 |
| docs/prompts/S2-documentos-e-dossie.md | 12 |
| docs/prompts/S2.1-ajuste-colunas.md | 4 |
| docs/prompts/S2.2-retomada-e-selecao.md | 1 |
| docs/prompts/S3-conversao-e-visualizacao.md | 2 |
| docs/prompts/S4-regras-de-acesso-e-complementos.md | 2 |
| docs/prompts/S5-auditoria-e-configuracoes.md | 4 |
| docs/prompts/S7-tramitacao-onde-esta-e-com-quem.md | 1 |
| docs/prompts/prompt-sprint-0.md | 17 |

## 4. Espelho da skill `vistas-banco` (`.claude/skills/vistas-banco/references/`)

Mesmo conteúdo de `docs/padroes/` (regra do `CLAUDE.md`): as linhas estão detalhadas na seção 1, pelos arquivos de `docs/padroes/`. A classificação é a mesma e a ação, idêntica nos dois lados. `SKILL.md` e demais arquivos da skill:

| Arquivo | Ocorrências |
|---|---:|
| .claude/skills/vistas-banco/references/agendamento.md | 2 |
| .claude/skills/vistas-banco/references/ambientes-e-docker.md | 6 |
| .claude/skills/vistas-banco/references/connection-pooling.md | 8 |
| .claude/skills/vistas-banco/references/documentacao.md | 1 |
| .claude/skills/vistas-banco/references/estrutura-de-pastas.md | 7 |
| .claude/skills/vistas-banco/references/http-erros-openapi.md | 1 |
| .claude/skills/vistas-banco/references/indexacao.md | 1 |
| .claude/skills/vistas-banco/references/integracao-externa.md | 1 |
| .claude/skills/vistas-banco/references/mer.md | 2 |
| .claude/skills/vistas-banco/references/modulos-e-dependencias.md | 3 |
| .claude/skills/vistas-banco/references/nomenclatura.md | 3 |
| .claude/skills/vistas-banco/references/normalizacao.md | 1 |
| .claude/skills/vistas-banco/references/persistencia-e-migrations.md | 7 |
| .claude/skills/vistas-banco/references/seguranca-privilegios.md | 15 |
| .claude/skills/vistas-banco/references/tipos-dados.md | 1 |

## Resultado final (bloco 7, após a Fase 2)

Varredura feita em **2026-11-19** com `grep` sem diferenciar caixa, por `siga[ _-]?vistas`, `sigavistas` e `siga-vistas`, excluindo `docs/prompts/`, `docs/progresso/`, `docs/homologacao/` e este inventário. Restaram **18 arquivos**, todos em uma das categorias abaixo. Nenhum é texto visível ao usuário do produto.

| Categoria | Arquivos e linhas | Motivo de manter |
|---|---|---|
| Nota histórica (classe C) | `CLAUDE.md:21`, `docs/01:7`, `docs/03:3`, `docs/README.md:5`, `README.md:3` | Dizem "antes SIGA Vistas" de propósito |
| Decisões antigas (classe C) | `docs/09` linhas 164, 239, 248, 250, 255, 294, 364, 453 | Texto de decisão da época; o D73 (linha 453) registra a origem |
| Cabeçalhos de tabela de homologação | `docs/14:87`, `docs/casos-de-teste.md:3,21` | Dados do roteiro do responsável; a `casos-de-teste` é preenchida por ele |
| Nomes de arquivo e pasta | `docs/prototipo/siga-vistas-prototipo.html`, `front/src/styles/tokens.css:1`, `docs/08:3`, `README.md:378`, `CLAUDE.md:49`, `docs/padroes/estrutura-de-pastas.md:8,122` e cópia em `references/`, `.claude/skills/.../references/estrutura-de-pastas.md` | Arquivo do protótipo (classe D, referência visual); o nome da pasta do repositório é trocado pelo responsável (D73) |
| Texto de teste que **verifica a ausência** do nome | `api/src/test/.../config/OpenApiConfigTest.java:17`, `.../ValidadorEMontadorPdfBoxTest.java:165` | São as asserções de que o nome antigo não aparece no título nem nos metadados |
| Volume e migração do Docker | `compose.local.yaml:24,26`, `README.md:355`, `docs/13` (seção "Renomeação do Rastro" e GHCR) | O volume `siga-vistas_dossies-tmp` foi mantido de propósito para não perder dados (D75); as instruções citam o nome antigo |
| Descrição da skill | `.claude/skills/vistas-banco/SKILL.md:3` | Frontmatter que define o gatilho da skill; o nome `vistas-banco` foi mantido (decisão do responsável) |
| Decisões de texto (dupla) | `docs/09-decisoes.md` e `docs/14` já listados | Idem acima |

### Aliases documentados (ainda ativos, remover depois)

- Propriedades `vistas.*` → `rastro.*` (a antiga vence, aviso de depreciação sem valor): `config.AliasDasPropriedadesAntigas`, D74, R62 em `docs/10`.
- Chaves do navegador `vistas.token` e `vistas.recentes.<login>` → `rastro.*` (migração e apagamento da antiga): `front/src/app/core/armazenamento.ts`, D75.

### Mantidos por contrato, decisão ou risco (não são nome do produto)

- Cabeçalhos `X-Vistas-Truncado` e `X-Vistas-Convertido-De` (contrato HTTP com o front; D74).
- Variáveis de ambiente `VISTAS_*` (não renomeadas; o `.env.local` do responsável não é aberto).
- Usuário `vistas` e diretório `/var/tmp/vistas` no `Dockerfile` (ligados ao volume).
- Schema próprio `VISTAS` / `APP_VISTAS` (versão definitiva, sem tabela ainda).
- Senha de demonstração `vistas-demo` (padrão do perfil `local`).
- Rótulo "Vistas do Processo" e módulo "Vistas" (rótulo do módulo, D73).

### Pendências para o responsável (não decididas aqui)

1. Renomear o repositório e a pasta local `siga-vistas` para `rastro` (lista em `docs/13`).
2. Migrar o volume `siga-vistas_dossies-tmp` (passos em `docs/13`), se quiser remover o nome antigo.
3. Remover os aliases quando o prazo combinado passar (R62).
4. Pasta `siga-vistas/` citada em `docs/padroes/estrutura-de-pastas.md` e na cópia da skill: atualizar junto com a pasta.
5. Ajuste de texto na `docs/padroes/nomenclatura.md` ("VISTAS (nome provisório, acompanha o nome do projeto)"): o nome do projeto já é Rastro; o schema segue `VISTAS` até a versão definitiva.
