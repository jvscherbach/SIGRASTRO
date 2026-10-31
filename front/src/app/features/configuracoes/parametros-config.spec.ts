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

const CONEXOES: Conexao[] = [
  { nome: 'siga', rotulo: 'Banco do SIGA', estado: 'CONFIGURADA', horasAteExpirar: null, tokenVencido: false },
  { nome: 'arquivos', rotulo: 'API de arquivos', estado: 'CONFIGURADA', horasAteExpirar: 36, tokenVencido: false },
  { nome: 'taxas', rotulo: 'Taxas', estado: 'DESATIVADA', horasAteExpirar: null, tokenVencido: false },
];

function teste(sobre: Partial<TesteDeConexao>): TesteDeConexao {
  return { nome: 'siga', sucesso: true, tempoMs: 42, mensagem: 'Conexão funcionando.', horasAteExpirar: null, tokenVencido: false, ...sobre };
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
      conexoes: vi.fn(() => of({ conexoes: CONEXOES })),
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
      expect(raiz.querySelector('[data-conexao="siga"] .cfg-teste.ok')!.textContent).toContain('87 ms');
    });

    it('teste com falha mostra a mensagem do servidor e o botão vira "Tentar novamente"', () => {
      api.testarConexao.mockReturnValue(of(teste({ nome: 'siga', sucesso: false, mensagem: 'Sem resposta no tempo limite.' })));
      abrir();
      aba('Conexões');
      const linha = raiz.querySelector('[data-conexao="siga"]')!;

      botao('Testar conexão', linha)!.click();
      fixture.detectChanges();

      expect(linha.querySelector('.cfg-teste.falha')!.textContent).toContain('Sem resposta no tempo limite.');
      expect(botao('Tentar novamente', linha)).toBeDefined();
    });

    it('token vencido: etiqueta e orientação para capturar um novo token', () => {
      api.testarConexao.mockReturnValue(
        of(teste({ nome: 'arquivos', sucesso: false, mensagem: 'A credencial foi recusada.', horasAteExpirar: 0, tokenVencido: true })),
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
