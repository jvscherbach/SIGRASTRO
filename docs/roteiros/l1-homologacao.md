# Roteiro de homologação da L1 (login consolidado)

Roteiro para o responsável executar **depois** de subir o sistema com o `.env.local` do ambiente. Nenhum cenário usa dado real: o CPF/CNPJ do Operador vem de `RASTRO_OPERADOR_CPF_CNPJ` (fictício no ambiente de teste). As senhas são as que **você** define no `.env.local`; este roteiro não traz nenhuma.

**Pré-requisitos**

- `.env.local` com `RASTRO_SENHA_OPERADOR`, `RASTRO_SENHA_ANALISTA` e `RASTRO_SENHA_ADMIN` preenchidas, cada uma com pelo menos 12 caracteres e **diferentes entre si**.
- Subida com os três arquivos do Compose (`compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml` quando a verificação exigir o ambiente vazio). Nunca `docker compose down -v`.
- Nunca abra nem imprima o `.env.local` para conferir os valores; confira pelo comportamento.

**Resultado:** preencha com `Não executado`, `OK` ou `Falhou (descreva sem dado real)`. Nunca registre senha, token ou hash.

| # | Cenário | Passos | Esperado | Resultado |
|---|---|---|---|---|
| 1 | Entrar com `operador` | Digite `operador` e a senha de `RASTRO_SENHA_OPERADOR` | Entra; o menu é o do Operador (Consulta, Dossiês gerados, Auditoria › Documentos) | Não executado |
| 2 | Entrar com `analista` | Digite `analista` e a senha de `RASTRO_SENHA_ANALISTA` | Cai na caixa de tarefas; não vê Configurações | Não executado |
| 3 | Entrar com `admin` | Digite `admin` e a senha de `RASTRO_SENHA_ADMIN` | Entra com o menu completo, inclusive Auditoria › Acessos e Configurações | Não executado |
| 4 | Senha errada | Digite `operador` com uma senha errada | "Usuário ou senha inválidos."; o campo de senha é apagado | Não executado |
| 5 | Usuário inexistente | Digite um login que não existe com uma senha qualquer | A **mesma** mensagem do cenário 4 | Não executado |
| 6 | Bloqueio | Erre a senha 3 vezes seguidas para `operador`, depois digite a senha certa | 429 com o tempo restante em minutos; a senha certa também é recusada | Não executado |
| 7 | Liberação | Espere 15 minutos depois do bloqueio e entre com a senha certa | Entra normalmente | Não executado |
| 8 | Trocar a própria senha | Em **Minha conta**, informe a senha atual, uma nova (12 a 200 caracteres, diferente da atual e sem o login) e a confirmação | Mensagem de sucesso; a sessão atual continua; em outro navegador, a sessão antiga do mesmo usuário cai | Não executado |
| 9 | Senha atual errada na troca | Em **Minha conta**, informe uma senha atual errada | Erro sem detalhe; conta o limite de tentativas | Não executado |
| 10 | Administrador redefine a senha de outro | Entre como `admin`, em **Configurações › Usuários** use "Redefinir senha" no `analista` | Senha alterada; a sessão aberta do `analista` cai na próxima chamada | Não executado |
| 11 | Criar um Gestor | Como `admin`, inclua um usuário com perfil Gestor e senha inicial válida | O Gestor entra e vê o Painel do Gestor e os documentos sigilosos | Não executado |
| 12 | Painel do Gestor | Entre como o Gestor criado no cenário 11 | Painel em `/painel` abre; `operador` e `analista` não veem o item no menu e, se digitarem `/painel`, são levados ao destino do perfil | Não executado |
| 13 | Operador não redefine senha alheia | Como `operador`, tente `PATCH` de senha de outro usuário (pela API, com a sessão do Operador) | 403 `PERFIL_SEM_PERMISSAO` | Não executado |
| 14 | Sem variável, sem usuário | Suba com `RASTRO_SENHA_ANALISTA` vazia | O `analista` não é criado; o aviso aparece no log só com o **nome** da variável | Não executado |
| 15 | Senhas repetidas | Suba com duas senhas iguais (mesmo só com diferença de maiúsculas) | Nenhum dos usuários envolvidos é criado; o aviso lista só os nomes das variáveis | Não executado |
| 16 | Reinício volta ao que as variáveis dizem | Troque a senha do `operador` pela tela, reinicie o serviço e entre com a senha da variável | Entra com a senha da variável (a troca pela tela não sobrevive ao reinício; R90) | Não executado |
| 17 | Sem administrador válido | Suba sem `RASTRO_SENHA_ADMIN` válida | `GET /api/estado` informa `semAdministrador`; o aviso aparece em destaque no log | Não executado |
| 18 | Nenhuma senha no navegador | Em qualquer cenário de login, depois do erro, confira o campo de senha e a página | A senha digitada não aparece na página nem em atributo do DOM | Não executado |

**Observações**

- Depois do bloqueio do cenário 6, o contador fica em memória: reiniciar o serviço zera o bloqueio (R93).
- O roteiro não cobre persistência em banco, login institucional, troca obrigatória no primeiro acesso, recuperação por e-mail, MFA, expiração por inatividade nem destravar antes dos 15 minutos: tudo isso é da H1.
