import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { ConfiguracoesApiService } from './configuracoes-api.service';
import { ConfiguracoesComponent } from './configuracoes.component';
import { Conexao, Parametro, TesteDeConexao } from './configuracoes.models';
import { MENSAGEM_DE_SAIDA, pendenciasGuard } from './pendencias.guard';

function parametro(sobre: Partial<Parametro>): Parametro {
  return {
    nome: 'x',
    rotulo: 'X',
    grupo: 'DOSSIE',
    tipo: 'INTEIRO',
    valor: '30',
    padrao: '30',
    minimo: 5,
    maximo: 240,
    opcoes: null,
    classeDeAplicacao: 'PROXIMA_GERACAO',
    sobreposto: false,
    pendenteDeReinicio: false,
    ...sobre,
  };
}

const TTL = parametro({ nome: 'dossie.ttlMinutos', rotulo: 'Expiração do dossiê (min)' });
const THREADS = parametro({ nome: 'dossie.threads', rotulo: 'Gerações simultâneas', valor: '2', padrao: '2', minimo: 1, maximo: 8, classeDeAplicacao: 'EXIGE_REINICIO' });
const SUMARIO = parametro({ nome: 'dossie.sumario', rotulo: 'Sumário', tipo: 'BOOLEANO', valor: 'true', padrao: 'true', minimo: null, maximo: null });
const ORIGEM = parametro({ nome: 'dossie.origem.JUNTADA', rotulo: 'Origem: Juntadas', tipo: 'BOOLEANO', valor: 'true', padrao: 'true', minimo: null, maximo: null, classeDeAplicacao: 'IMEDIATO' });
const MODO = parametro({ nome: 'conversao.planilhaModo', rotulo: 'Planilhas', grupo: 'CONVERSAO', tipo: 'OPCAO', valor: 'LARGURA', padrao: 'LARGURA', minimo: null, maximo: null, opcoes: ['LARGURA', 'PAGINA_POR_ABA'] });
const VIS_TTL = parametro({ nome: 'visualizacao.ttlMinutos', rotulo: 'Vida do cache (min)', grupo: 'VISUALIZACAO', valor: '10', padrao: '10', minimo: 1, maximo: 240, classeDeAplicacao: 'IMEDIATO' });
const RETENCAO = parametro({ nome: 'auditoria.retencaoDocumentosAnos', rotulo: 'Retenção: documentos', grupo: 'AUDITORIA', tipo: 'OPCAO', valor: '5', padrao: '5', minimo: null, maximo: null, opcoes: ['1', '2', '5', '10'], classeDeAplicacao: 'IMEDIATO' });
const DOWNLOAD = parametro({ nome: 'conexoes.downloadTimeoutSegundos', rotulo: 'Tempo limite de download (s)', grupo: 'CONEXOES', valor: '120', padrao: '120', minimo: 10, maximo: 300 });
const TODOS = [TTL, THREADS, SUMARIO, ORIGEM, MODO, VIS_TTL, RETENCAO, DOWNLOAD];
const RASTRO_PAINEL: Parametro[] = [
  parametro({ nome: 'rastro.painelAno', rotulo: 'Painel: ano considerado (0 = ano corrente)', grupo: 'RASTRO', tipo: 'INTEIRO', valor: '0', padrao: '0', minimo: 0, maximo: 2100, classeDeAplicacao: 'IMEDIATO' }),
  parametro({ nome: 'rastro.painelAtualizacaoMinutos', rotulo: 'Painel: atualização automática (min)', grupo: 'RASTRO', valor: '30', padrao: '30', minimo: 5, maximo: 1440, classeDeAplicacao: 'IMEDIATO' }),
  parametro({ nome: 'rastro.painelLimiteRequerimentos', rotulo: 'Painel: teto de requerimentos por atualização', grupo: 'RASTRO', valor: '3000', padrao: '3000', minimo: 100, maximo: 20000, classeDeAplicacao: 'IMEDIATO' }),
  parametro({ nome: 'rastro.painelTimeoutSegundos', rotulo: 'Painel: tempo limite da atualização (s)', grupo: 'RASTRO', valor: '60', padrao: '60', minimo: 10, maximo: 300, classeDeAplicacao: 'IMEDIATO' }),
  parametro({ nome: 'rastro.painelIntervaloMinimoManualSegundos', rotulo: 'Painel: intervalo mínimo da atualização manual (s)', grupo: 'RASTRO', valor: '120', padrao: '120', minimo: 30, maximo: 3600, classeDeAplicacao: 'IMEDIATO' }),
];

const CONEXOES: Conexao[] = [
  { nome: 'siga', rotulo: 'Banco do SIGA', estado: 'CONFIGURADA', horasAteExpirar: null, tokenVencido: false },
  { nome: 'arquivos', rotulo: 'API de arquivos', estado: 'CONFIGURADA', horasAteExpirar: 36, tokenVencido: false },
  { nome: 'taxas', rotulo: 'Taxas', estado: 'DESATIVADA', horasAteExpirar: null, tokenVencido: false },
];

function teste(sobre: Partial<TesteDeConexao>): TesteDeConexao {
  return {
    nome: 'siga',
    sucesso: true,
    classificacao: 'OK',
    tempoMs: 42,
    tempoLimiteSegundos: 15,
    mensagem: 'Conexão funcionando.',
    horasAteExpirar: null,
    tokenVencido: false,
    ...sobre,
  };
}

function problema(status: number, errorCode: string, campos?: { campo: string; mensagem: string }[]) {
  return new HttpErrorResponse({ status, error: { status, errorCode, campos } });
}

describe('ConfiguracoesComponent: parâmetros, conexões e retenção', () => {
  let api: Record<'usuarios' | 'incluirUsuario' | 'alterarUsuario' | 'parametros' | 'salvarParametros' | 'restaurarParametro' | 'conexoes' | 'testarConexao', ReturnType<typeof vi.fn>>;
  let fixture: ComponentFixture<ConfiguracoesComponent>;
  let raiz: HTMLElement;

  function abrir(): void {
    TestBed.configureTestingModule({
      imports: [ConfiguracoesComponent],
      providers: [
        { provide: ConfiguracoesApiService, useValue: api },
        { provide: AuthService, useValue: { usuario: signal({ login: 'admin.demo' }).asReadonly() } },
      ],
    });
    fixture = TestBed.createComponent(ConfiguracoesComponent);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  }

  const texto = () => raiz.textContent!.replace(/\s+/g, ' ');
  const aba = (nome: string) => {
    Array.from(raiz.querySelectorAll<HTMLElement>('[role="tab"]')).find((a) => a.textContent!.trim() === nome)!.click();
    fixture.detectChanges();
  };
  const campo = (nome: string) => raiz.querySelector<HTMLElement>(`[data-param="${nome}"]`)!;
  const botao = (rotulo: string, dentro: ParentNode = raiz) =>
    Array.from(dentro.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes(rotulo));
  const numero = (nome: string, valor: string) => {
    const entrada = campo(nome).querySelector<HTMLInputElement>('input[type="number"]')!;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const marcar = (nome: string, ligado: boolean) => {
    const caixa = campo(nome).querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    caixa.checked = ligado;
    caixa.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  beforeEach(() => {
    api = {
      usuarios: vi.fn(() => of({ conteudo: [], pagina: 0, tamanho: 0, totalElementos: 0, totalPaginas: 0 })),
      incluirUsuario: vi.fn(),
      alterarUsuario: vi.fn(),
      parametros: vi.fn(() => of({ parametros: TODOS })),
      salvarParametros: vi.fn(),
      restaurarParametro: vi.fn(),
      conexoes: vi.fn(() => of({ conexoes: CONEXOES, tempoLimiteDoTesteSegundos: 15 })),
      testarConexao: vi.fn(),
    };
  });

  afterEach(() => {
    fixture.destroy();
    vi.restoreAllMocks();
  });

  describe('campos e etiquetas', () => {
    it('cada aba mostra os parâmetros do seu grupo, com faixa, padrão do ambiente e etiqueta da classe de aplicação', () => {
      abrir();
      aba('Dossiê');

      expect(campo('dossie.ttlMinutos').textContent).toContain('Expiração do dossiê (min)');
      expect(campo('dossie.ttlMinutos').textContent).toContain('Faixa: 5 a 240');
      expect(campo('dossie.ttlMinutos').textContent).toContain('Padrão do ambiente: 30');
      expect(campo('dossie.ttlMinutos').querySelector('.tag')!.textContent!.trim()).toBe('Vale na próxima geração');
      expect(campo('dossie.threads').querySelector('.tag')!.textContent!.trim()).toBe('Exige reinício');
      expect(campo('dossie.threads').textContent).toContain('só vale de fato pela variável de ambiente');
      expect(campo('dossie.origem.JUNTADA').querySelector('.tag')!.textContent!.trim()).toBe('Vale já');
      expect(campo('dossie.sumario').textContent).toContain('Padrão do ambiente: ligado');
      expect(raiz.querySelector('[data-param="conversao.planilhaModo"]')).toBeNull();
    });

    it('Rastro: os cinco campos do painel aparecem com faixa, padrão e "Vale já", e o texto de apoio explica o 0 do ano', () => {
      api.parametros.mockReturnValue(of({ parametros: [...TODOS, ...RASTRO_PAINEL] }));
      abrir();
      aba('Rastro');

      expect(campo('rastro.painelAno').textContent).toContain('Painel: ano considerado (0 = ano corrente)');
      expect(campo('rastro.painelAno').textContent).toContain('Faixa: 0 a 2100');
      expect(campo('rastro.painelAno').textContent).toContain('Padrão do ambiente: 0');
      expect(campo('rastro.painelAtualizacaoMinutos').textContent).toContain('Faixa: 5 a 1440');
      expect(campo('rastro.painelLimiteRequerimentos').textContent).toContain('Faixa: 100 a 20000');
      expect(campo('rastro.painelTimeoutSegundos').textContent).toContain('Faixa: 10 a 300');
      expect(campo('rastro.painelIntervaloMinimoManualSegundos').textContent).toContain('Faixa: 30 a 3600');
      expect(campo('rastro.painelAno').querySelector('.tag')!.textContent!.trim()).toBe('Vale já');
      expect(raiz.querySelector('.cfg-apoio-painel')!.textContent).toContain('0 é o ano corrente (fuso de Cuiabá)');
      expect(raiz.querySelector('.cfg-apoio-painel')!.textContent).not.toMatch(/senha|token|SIGA_DB|https?:\/\//i);
    });

    it('a ordem cronológica aparece como somente leitura (fixa, RN03), sem campo', () => {
      abrir();
      aba('Dossiê');

      expect(raiz.querySelector('.cfg-fixo')!.textContent).toContain('fixa (RN03)');
      expect(raiz.querySelector('.cfg-fixo input')).toBeNull();
      expect(texto()).toContain('Origens dos documentos');
    });

    it('Conversão: modo das planilhas em botões de opção com rótulos em português', () => {
      abrir();
      aba('Conversão');

      const opcoes = Array.from(campo('conversao.planilhaModo').querySelectorAll<HTMLInputElement>('input[type="radio"]'));
      expect(opcoes.map((o) => o.value)).toEqual(['LARGURA', 'PAGINA_POR_ABA']);
      expect(opcoes[0].checked).toBe(true);
      expect(campo('conversao.planilhaModo').textContent).toContain('Ajustar à largura');
      expect(campo('conversao.planilhaModo').textContent).toContain('Uma página por aba');
    });

    it('Auditoria: retenção por grupo em lista 1, 2, 5 ou 10 anos', () => {
      abrir();
      aba('Auditoria');

      const lista = campo('auditoria.retencaoDocumentosAnos').querySelector<HTMLSelectElement>('select')!;
      expect(Array.from(lista.options).map((o) => o.textContent!.trim())).toEqual(['1 ano', '2 anos', '5 anos', '10 anos']);
      expect(lista.value).toBe('5');
    });

    it('parâmetro sobreposto e pendente de reinício trazem etiquetas e "Restaurar padrão"', () => {
      api.parametros.mockReturnValue(of({ parametros: [TTL, { ...THREADS, valor: '4', sobreposto: true, pendenteDeReinicio: true }] }));
      abrir();
      aba('Dossiê');

      expect(campo('dossie.threads').textContent).toContain('Pendente de reinício');
      expect(campo('dossie.threads').textContent).toContain('Alterado nesta sessão');
      expect(botao('Restaurar padrão', campo('dossie.threads'))).toBeDefined();
      expect(botao('Restaurar padrão', campo('dossie.ttlMinutos'))).toBeUndefined();
    });

    it('erro ao carregar com "Tentar novamente"', () => {
      api.parametros.mockReturnValueOnce(throwError(() => problema(503, 'ERRO_INTERNO')));
      abrir();
      aba('Dossiê');
      expect(raiz.querySelector('.cfg-erro')).not.toBeNull();

      botao('Tentar novamente')!.click();
      fixture.detectChanges();

      expect(campo('dossie.ttlMinutos')).not.toBeNull();
    });
  });

  describe('alterações pendentes', () => {
    it('editar não grava: aparece a barra com a contagem e as pendências sobrevivem à troca de aba', () => {
      abrir();
      aba('Dossiê');
      expect(raiz.querySelector('.cfg-barra')).toBeNull();

      numero('dossie.ttlMinutos', '45');
      marcar('dossie.sumario', false);
      aba('Visualização');
      numero('visualizacao.ttlMinutos', '20');

      expect(api.salvarParametros).not.toHaveBeenCalled();
      expect(raiz.querySelector('.cfg-barra b')!.textContent).toBe('3');
      expect(raiz.querySelector('.cfg-barra')!.textContent).toContain('alterações pendentes');
      aba('Dossiê');
      expect(campo('dossie.ttlMinutos').querySelector<HTMLInputElement>('input')!.value).toBe('45');
      expect(campo('dossie.ttlMinutos').textContent).toContain('Alteração pendente');
    });

    it('voltar ao valor vigente tira a pendência', () => {
      abrir();
      aba('Dossiê');

      numero('dossie.ttlMinutos', '45');
      expect(raiz.querySelector('.cfg-barra')).not.toBeNull();
      numero('dossie.ttlMinutos', '30');

      expect(raiz.querySelector('.cfg-barra')).toBeNull();
    });

    it('Descartar volta tudo ao vigente sem chamar o servidor', () => {
      abrir();
      aba('Dossiê');
      numero('dossie.ttlMinutos', '45');

      botao('Descartar')!.click();
      fixture.detectChanges();

      expect(api.salvarParametros).not.toHaveBeenCalled();
      expect(raiz.querySelector('.cfg-barra')).toBeNull();
      expect(campo('dossie.ttlMinutos').querySelector<HTMLInputElement>('input')!.value).toBe('30');
    });

    it('Salvar envia um único PUT com todas as pendências tipadas e mostra o resumo, com o que exige reinício', () => {
      api.salvarParametros.mockReturnValue(
        of({ parametros: TODOS.map((p) => (p.nome === 'dossie.ttlMinutos' ? { ...p, valor: '45', sobreposto: true } : p.nome === 'dossie.threads' ? { ...p, pendenteDeReinicio: true } : p)) }),
      );
      abrir();
      aba('Dossiê');
      numero('dossie.ttlMinutos', '45');
      numero('dossie.threads', '4');
      marcar('dossie.sumario', false);

      botao('Salvar alterações')!.click();
      fixture.detectChanges();

      expect(api.salvarParametros).toHaveBeenCalledTimes(1);
      expect(api.salvarParametros).toHaveBeenCalledWith({ 'dossie.ttlMinutos': 45, 'dossie.threads': 4, 'dossie.sumario': false });
      expect(raiz.querySelector('.cfg-barra')).toBeNull();
      const resumo = raiz.querySelector('.cfg-resumo')!.textContent!.replace(/\s+/g, ' ');
      expect(resumo).toContain('3 alterações salvas');
      expect(resumo).toContain('Expiração do dossiê (min): 30 → 45');
      expect(resumo).toContain('Sumário: ligado → desligado');
      expect(resumo).toContain('Exigem reinício: Gerações simultâneas');
      expect(resumo).toContain('variável de ambiente');
      expect(campo('dossie.threads').textContent).toContain('Pendente de reinício');
    });

    it('opção de texto vai como texto', () => {
      api.salvarParametros.mockReturnValue(of({ parametros: TODOS }));
      abrir();
      aba('Conversão');

      const radios = campo('conversao.planilhaModo').querySelectorAll<HTMLInputElement>('input[type="radio"]');
      radios[1].checked = true;
      radios[1].dispatchEvent(new Event('change'));
      fixture.detectChanges();
      botao('Salvar alterações')!.click();

      expect(api.salvarParametros).toHaveBeenCalledWith({ 'conversao.planilhaModo': 'PAGINA_POR_ABA' });
    });

    it('erro de faixa do servidor marca o campo e mantém todas as pendências', () => {
      api.salvarParametros.mockReturnValue(
        throwError(() => problema(400, 'PARAMETRO_INVALIDO', [{ campo: 'dossie.ttlMinutos', mensagem: 'Deve ficar entre 5 e 240.' }])),
      );
      abrir();
      aba('Dossiê');
      numero('dossie.ttlMinutos', '999');
      marcar('dossie.sumario', false);

      botao('Salvar alterações')!.click();
      fixture.detectChanges();

      expect(campo('dossie.ttlMinutos').querySelector('.cfg-campo-erro')!.textContent).toContain('Deve ficar entre 5 e 240.');
      expect(campo('dossie.ttlMinutos').querySelector('input')!.getAttribute('aria-invalid')).toBe('true');
      expect(raiz.querySelector('.cfg-barra b')!.textContent).toBe('2');
      expect(raiz.querySelector('.cfg-resumo')).toBeNull();
      // corrigir o campo limpa o erro dele
      numero('dossie.ttlMinutos', '60');
      expect(campo('dossie.ttlMinutos').querySelector('.cfg-campo-erro')).toBeNull();
    });

    it('falha geral ao salvar (sem campo) mostra a mensagem e não perde as pendências', () => {
      api.salvarParametros.mockReturnValue(throwError(() => problema(503, 'ERRO_INTERNO')));
      abrir();
      aba('Dossiê');
      numero('dossie.ttlMinutos', '45');

      botao('Salvar alterações')!.click();
      fixture.detectChanges();

      expect(raiz.querySelector('.cfg-erro-salvar')).not.toBeNull();
      expect(raiz.querySelector('.cfg-barra b')!.textContent).toBe('1');
    });

    it('trava os campos e não duplica o PUT enquanto salva', () => {
      const resposta = new Subject<{ parametros: Parametro[] }>();
      api.salvarParametros.mockReturnValue(resposta);
      abrir();
      aba('Dossiê');
      numero('dossie.ttlMinutos', '45');

      botao('Salvar alterações')!.click();
      fixture.detectChanges();
      botao('Salvando')!.click();

      expect(api.salvarParametros).toHaveBeenCalledTimes(1);
      expect(campo('dossie.ttlMinutos').querySelector<HTMLInputElement>('input')!.disabled).toBe(true);
    });

    it('Restaurar padrão chama o DELETE, atualiza a lista e tira a pendência do parâmetro', () => {
      api.parametros.mockReturnValue(of({ parametros: [{ ...TTL, valor: '45', sobreposto: true }, SUMARIO] }));
      api.restaurarParametro.mockReturnValue(of({ parametros: [TTL, SUMARIO] }));
      abrir();
      aba('Dossiê');
      numero('dossie.ttlMinutos', '60');

      botao('Restaurar padrão')!.click();
      fixture.detectChanges();

      expect(api.restaurarParametro).toHaveBeenCalledWith('dossie.ttlMinutos');
      expect(campo('dossie.ttlMinutos').querySelector<HTMLInputElement>('input')!.value).toBe('30');
      expect(raiz.querySelector('.cfg-barra')).toBeNull();
      expect(botao('Restaurar padrão')).toBeUndefined();
    });

    it('destruir a tela com o salvamento em andamento não deixa inscrição pendurada', () => {
      const resposta = new Subject<{ parametros: Parametro[] }>();
      api.salvarParametros.mockReturnValue(resposta);
      abrir();
      aba('Dossiê');
      numero('dossie.ttlMinutos', '45');
      botao('Salvar alterações')!.click();

      fixture.destroy();

      expect(resposta.observed).toBe(false);
    });
  });

  describe('saída com pendência', () => {
    it('o guard pede confirmação com pendência e deixa sair sem ela', () => {
      abrir();
      aba('Dossiê');
      const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(false);

      expect(pendenciasGuard(fixture.componentInstance, null as never, null as never, null as never)).toBe(true);
      expect(confirmar).not.toHaveBeenCalled();
      numero('dossie.ttlMinutos', '45');
      expect(pendenciasGuard(fixture.componentInstance, null as never, null as never, null as never)).toBe(false);
      expect(confirmar).toHaveBeenCalledWith(MENSAGEM_DE_SAIDA);
      confirmar.mockReturnValue(true);
      expect(pendenciasGuard(fixture.componentInstance, null as never, null as never, null as never)).toBe(true);
    });

    it('recarregar ou fechar a janela com pendência aciona o aviso do navegador', () => {
      abrir();
      aba('Dossiê');
      const evento = new Event('beforeunload', { cancelable: true });

      window.dispatchEvent(evento);
      expect(evento.defaultPrevented).toBe(false);
      numero('dossie.ttlMinutos', '45');
      const comPendencia = new Event('beforeunload', { cancelable: true });
      window.dispatchEvent(comPendencia);

      expect(comPendencia.defaultPrevented).toBe(true);
    });
  });

  describe('conexões', () => {
    it('lista as conexões com estado e as horas até expirar o token; nenhuma credencial na tela', () => {
      abrir();
      aba('Conexões');

      expect(raiz.querySelectorAll('.cfg-conexao')).toHaveLength(3);
      expect(raiz.querySelector('[data-conexao="arquivos"] .cfg-horas')!.textContent).toContain('36 h');
      expect(raiz.querySelector('[data-conexao="taxas"]')!.textContent).toContain('Desativada');
      expect(botao('Testar conexão', raiz.querySelector('[data-conexao="taxas"]')!)!.disabled).toBe(true);
      expect(texto()).toContain('não as exibe nem as edita');
      expect(raiz.querySelector('input[type="password"], input[name*="token" i], input[name*="senha" i]')).toBeNull();
      expect(campo('conexoes.downloadTimeoutSegundos').textContent).toContain('Tempo limite de download (s)');
    });

    it('teste com sucesso mostra o tempo de resposta', () => {
      api.testarConexao.mockReturnValue(of(teste({ tempoMs: 87 })));
      abrir();
      aba('Conexões');

      botao('Testar conexão', raiz.querySelector('[data-conexao="siga"]')!)!.click();
      fixture.detectChanges();

      expect(api.testarConexao).toHaveBeenCalledWith('siga');
      const resultado = raiz.querySelector('[data-conexao="siga"] .cfg-teste.ok')!.textContent!;
      expect(resultado).toContain('OK');
      expect(resultado).toContain('87 ms (limite 15 s)');
    });

    it('mostra o tempo limite do teste antes de testar', () => {
      abrir();
      aba('Conexões');

      expect(raiz.querySelector('.cfg-limite-teste')!.textContent).toContain('Tempo limite de cada chamada: 15 s');
      expect(raiz.querySelector('.cfg-limite-teste')!.textContent).toContain('mesmo endereço, cliente e credencial');
    });

    it('alcançável com ressalva (ex.: 400 do SCORP para identificador fictício) não é falha: texto, código e explicação', () => {
      api.testarConexao.mockReturnValue(
        of(
          teste({
            nome: 'arquivos',
            classificacao: 'ALCANCAVEL_COM_RESSALVA',
            codigoHttp: 404,
            tempoMs: 120,
            mensagem: 'Serviço alcançável: respondeu HTTP 404 à chamada de teste, que usa uma chave fictícia.',
          }),
        ),
      );
      abrir();
      aba('Conexões');
      const linha = raiz.querySelector('[data-conexao="arquivos"]')!;

      botao('Testar conexão', linha)!.click();
      fixture.detectChanges();

      const resultado = linha.querySelector('[data-classificacao="ALCANCAVEL_COM_RESSALVA"]')!;
      expect(resultado.classList).toContain('ressalva');
      expect(resultado.classList).not.toContain('falha');
      expect(resultado.getAttribute('role')).toBe('status');
      expect(resultado.textContent).toContain('Alcançável com ressalva');
      expect(resultado.textContent).toContain('HTTP 404');
      expect(resultado.textContent).toContain('chave fictícia');
    });

    it('falha de autenticação aparece com texto e orienta renovar o token', () => {
      api.testarConexao.mockReturnValue(
        of(
          teste({
            nome: 'arquivos',
            sucesso: false,
            classificacao: 'FALHA_DE_AUTENTICACAO',
            codigoHttp: 401,
            mensagem: 'O serviço recusou o token (HTTP 401). Renove o token: capture um novo, atualize o arquivo e teste de novo.',
          }),
        ),
      );
      abrir();
      aba('Conexões');
      const linha = raiz.querySelector('[data-conexao="arquivos"]')!;

      botao('Testar conexão', linha)!.click();
      fixture.detectChanges();

      const resultado = linha.querySelector('.cfg-teste.falha')!;
      expect(resultado.textContent).toContain('Falha de autenticação');
      expect(resultado.textContent).toContain('Renove o token');
      expect(resultado.getAttribute('role')).toBe('alert');
    });

    it('teste com falha mostra a mensagem do servidor e o botão vira "Tentar novamente"', () => {
      api.testarConexao.mockReturnValue(
        of(teste({ nome: 'siga', sucesso: false, classificacao: 'FALHA', mensagem: 'Sem resposta no tempo limite.' })),
      );
      abrir();
      aba('Conexões');
      const linha = raiz.querySelector('[data-conexao="siga"]')!;

      botao('Testar conexão', linha)!.click();
      fixture.detectChanges();

      expect(linha.querySelector('.cfg-teste.falha')!.textContent).toContain('Sem resposta no tempo limite.');
      expect(linha.querySelector('.cfg-teste.falha')!.textContent).toContain('Falha');
      expect(botao('Tentar novamente', linha)).toBeDefined();
    });

    it('token vencido: etiqueta e orientação para capturar um novo token', () => {
      api.testarConexao.mockReturnValue(
        of(
          teste({
            nome: 'arquivos',
            sucesso: false,
            classificacao: 'FALHA_DE_AUTENTICACAO',
            mensagem: 'A credencial foi recusada.',
            horasAteExpirar: 0,
            tokenVencido: true,
          }),
        ),
      );
      abrir();
      aba('Conexões');
      const linha = raiz.querySelector('[data-conexao="arquivos"]')!;

      botao('Testar conexão', linha)!.click();
      fixture.detectChanges();

      expect(linha.querySelector('.cfg-vencido')!.textContent).toContain('Token vencido');
      expect(linha.querySelector('.cfg-token-vencido')!.textContent).toContain(
        'Capture um novo token, atualize o arquivo do token e clique em Tentar novamente.',
      );
      expect(linha.querySelector('.cfg-horas')).toBeNull();
    });

    it('teste já em andamento no servidor (409) aparece na linha', () => {
      api.testarConexao.mockReturnValue(throwError(() => problema(409, 'TESTE_DE_CONEXAO_EM_ANDAMENTO')));
      abrir();
      aba('Conexões');
      const linha = raiz.querySelector('[data-conexao="siga"]')!;

      botao('Testar conexão', linha)!.click();
      fixture.detectChanges();

      expect(linha.querySelector('.cfg-teste.falha')).not.toBeNull();
    });

    it('desabilita o botão enquanto testa e destruir a tela cancela o teste', () => {
      const resposta = new Subject<TesteDeConexao>();
      api.testarConexao.mockReturnValue(resposta);
      abrir();
      aba('Conexões');
      const linha = raiz.querySelector('[data-conexao="siga"]')!;

      botao('Testar conexão', linha)!.click();
      fixture.detectChanges();
      expect(botao('Testando', linha)!.disabled).toBe(true);
      fixture.destroy();

      expect(resposta.observed).toBe(false);
    });

    it('erro ao carregar as conexões com "Tentar novamente"', () => {
      api.conexoes.mockReturnValueOnce(throwError(() => problema(503, 'ERRO_INTERNO')));
      abrir();
      aba('Conexões');
      expect(raiz.querySelector('.cfg-erro')).not.toBeNull();

      botao('Tentar novamente')!.click();
      fixture.detectChanges();

      expect(raiz.querySelectorAll('.cfg-conexao')).toHaveLength(3);
    });
  });
});
