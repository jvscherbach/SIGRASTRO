import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';

import { DetalheApiService } from './detalhe-api.service';
import { OndeEstaAgoraComponent } from './onde-esta-agora.component';
import { PosicaoDoRequerimento } from './posicao.models';

/** Posição fictícia no formato da API (S7, docs/07), com os campos de Administrador misturados para provar que a tela os ignora. */
function posicao(sobre: Partial<PosicaoDoRequerimento> = {}): PosicaoDoRequerimento {
  return {
    idRequerimento: 7101,
    numeroRequerimento: '7101/2026',
    numeroProcesso: '7900/2022',
    situacaoLicenciamento: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
    modelo: 'Outorga de recursos hídricos',
    estado: 'ATIVO',
    etapaAtual: {
      id: 'analise_do_processo',
      rotulo: 'Análise do processo',
      tipo: 'TAREFA_HUMANA',
      desde: '2026-03-08T09:30:00',
      desdeUltimaEntrada: true,
      responsavel: { tipo: 'VARIOS', papel: 'Analista', area: null, nome: null, texto: 'Analista' },
      idTecnico: 'taskAnalisarRequerimento',
    } as PosicaoDoRequerimento['etapaAtual'],
    emParalelo: [
      {
        id: 'emissao_do_ato',
        rotulo: 'Emissão do ato',
        tipo: 'EMISSAO',
        desde: '2026-03-09T09:30:00',
        desdeUltimaEntrada: false,
        responsavel: { tipo: 'AREA', papel: 'Equipe', area: 'Emissão de títulos', nome: null, texto: 'Emissão de títulos' },
        idTecnico: 'taskEmitirPortarias',
        processoFilho: 'procSurhEmissaoPortaria',
      } as PosicaoDoRequerimento['emParalelo'][number],
      { id: null, rotulo: 'Etapa não mapeada', tipo: null, desde: null, desdeUltimaEntrada: false, responsavel: null },
    ],
    complementacao: { quantidade: 1, desde: '2026-03-06T09:30:00' },
    multiInstancia: {
      concluidos: 1,
      ativas: 2,
      total: 3,
      participantes: [
        { nome: 'Servidor Exemplo 01', papel: 'Analista', concluiu: true },
        { nome: 'Servidor Exemplo 02', papel: 'Analista', concluiu: false },
        { nome: null, papel: 'Responsável não identificado', concluiu: false },
      ],
      maisParticipantes: 0,
    },
    avisos: [{ codigo: 'ETAPA_NAO_MAPEADA', texto: 'Há uma etapa do fluxo que o Rastro ainda não conhece.', gravidade: 'INFO' }],
    final: null,
    ...sobre,
  };
}

describe('OndeEstaAgoraComponent', () => {
  let api: { posicao: ReturnType<typeof vi.fn>; parametrosDeExibicao: ReturnType<typeof vi.fn> };

  function criar(modo: 'completo' | 'resumido' | 'celula' = 'completo'): {
    fixture: ComponentFixture<OndeEstaAgoraComponent>;
    raiz: HTMLElement;
  } {
    TestBed.configureTestingModule({
      imports: [OndeEstaAgoraComponent],
      providers: [{ provide: DetalheApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(OndeEstaAgoraComponent);
    fixture.componentRef.setInput('idRequerimento', 7101);
    fixture.componentRef.setInput('modo', modo);
    fixture.detectChanges();
    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  const texto = (raiz: HTMLElement) => raiz.textContent!.replace(/\s+/g, ' ');

  /** Nenhum id técnico nem CPF no texto da tela, mesmo com os campos de Administrador na resposta. */
  function semIdTecnicoNemCpf(raiz: HTMLElement): void {
    const t = raiz.textContent!;
    expect(t).not.toMatch(/task|gtw|gwt|Event_|Activity_|procSurh|procOutorga/);
    expect(t).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}/);
    expect(t).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-11T13:30:00Z')); // 09:30 em Cuiabá (UTC-4)
    api = { posicao: vi.fn(), parametrosDeExibicao: vi.fn(() => NEVER) };
  });

  afterEach(() => vi.useRealTimers());

  it('carregando: esqueleto acessível com a altura reservada', () => {
    api.posicao.mockReturnValue(NEVER);
    const { raiz } = criar();

    expect(raiz.querySelector('[role="status"]')!.textContent).toContain('Carregando a posição');
    expect(raiz.querySelector('section')!.getAttribute('aria-busy')).toBe('true');
    expect(raiz.querySelectorAll('.osso')).toHaveLength(3);
    expect(raiz.querySelector('.onde')).not.toBeNull(); // min-height no .onde em todos os estados
  });

  it('ativo: etapa, selo de tipo, desde quando com a data completa, com quem, x de y e participantes', () => {
    api.posicao.mockReturnValue(of(posicao()));
    const { raiz } = criar();
    const t = texto(raiz);

    expect(raiz.querySelector('.onde-etapa')!.textContent).toBe('Análise do processo');
    expect(t).toContain('Tarefa');
    expect(t).toContain('Outorga de recursos hídricos');
    const tempo = raiz.querySelector('time')!;
    expect(tempo.textContent).toBe('há 3 dias');
    expect(tempo.getAttribute('title')).toBe('08/03/2026 09:30');
    expect(t).toContain('(em 08/03/2026 09:30)'); // texto acessível
    expect(t).toContain('desde a última entrada nesta etapa');
    expect(t).toMatch(/Com quem\s*Analista/);
    expect(t).toContain('1 de 3 concluídos');
    const participantes = [...raiz.querySelectorAll('.onde-participantes li')].map((li) => li.textContent!.replace(/\s+/g, ' ').trim());
    expect(participantes).toEqual([
      'Servidor Exemplo 01: Concluiu',
      'Servidor Exemplo 02: Pendente',
      'Responsável não identificado: Pendente',
    ]);
    semIdTecnicoNemCpf(raiz);
  });

  it('+N em paralelo abre e fecha pelo botão (teclado) e a complementação vem à parte', () => {
    api.posicao.mockReturnValue(of(posicao()));
    const { fixture, raiz } = criar();
    const botao = raiz.querySelector<HTMLButtonElement>('.onde-paralelo')!;

    expect(botao.tagName).toBe('BUTTON'); // focável e acionável por Enter/Espaço
    expect(botao.textContent!.trim()).toBe('+2 em paralelo');
    expect(botao.getAttribute('aria-expanded')).toBe('false');
    expect(raiz.querySelector('[id^="onde-paralelos-"]')).toBeNull();
    expect(texto(raiz)).toContain('Complementação em andamento');

    botao.click();
    fixture.detectChanges();
    expect(botao.getAttribute('aria-expanded')).toBe('true');
    const lista = raiz.querySelector('[id^="onde-paralelos-"]')!;
    expect(botao.getAttribute('aria-controls')).toBe(lista.id);
    const itens = [...raiz.querySelectorAll('[id^="onde-paralelos-"] li')].map((li) => li.textContent!.replace(/\s+/g, ' '));
    expect(itens[0]).toContain('Emissão do ato');
    expect(itens[0]).toContain('Emissão de títulos');
    expect(itens[1]).toContain('Etapa não mapeada');
    expect(itens.join(' ')).not.toContain('Complementação'); // não entra no "+N"
    semIdTecnicoNemCpf(raiz);

    botao.click();
    fixture.detectChanges();
    expect(raiz.querySelector('[id^="onde-paralelos-"]')).toBeNull();
  });

  it('multi-instância com ciclo desconhecido mostra só as pendentes, sem inventar o "y", e "e mais N"', () => {
    api.posicao.mockReturnValue(
      of(
        posicao({
          multiInstancia: {
            concluidos: null,
            ativas: 2,
            total: null,
            participantes: [{ nome: 'Servidor Exemplo 03', papel: 'Analista', concluiu: false }],
            maisParticipantes: 4,
          },
        }),
      ),
    );
    const { raiz } = criar();
    const t = texto(raiz);

    expect(t).toContain('2 pendentes');
    expect(t).not.toMatch(/\d+ de \d+ concluídos/);
    expect(t).toContain('e mais 4');
  });

  it('desde desconhecido não inventa data', () => {
    api.posicao.mockReturnValue(
      of(posicao({ etapaAtual: { ...posicao().etapaAtual!, desde: null }, multiInstancia: null, emParalelo: [] })),
    );
    const { raiz } = criar();

    expect(raiz.querySelector('.onde-linhas time')).toBeNull();
    expect(texto(raiz)).toContain('Data não informada');
  });

  it('avisos: texto e ícone, com a gravidade dita ao leitor de tela (não só cor)', () => {
    api.posicao.mockReturnValue(
      of(
        posicao({
          avisos: [
            { codigo: 'EM_SUPORTE', texto: 'Em atendimento de suporte técnico.', gravidade: 'INFO' },
            { codigo: 'FLUXO_ENCERRADO_SEM_STATUS', texto: 'O fluxo foi encerrado por taxa não paga no prazo.', gravidade: 'ATENCAO' },
          ],
        }),
      ),
    );
    const { raiz } = criar();
    const avisos = raiz.querySelectorAll('.onde-avisos li');

    expect(avisos).toHaveLength(2);
    expect(avisos[0].className).toBe('info');
    expect(avisos[0].textContent).toContain('Informação: Em atendimento de suporte técnico.');
    expect(avisos[0].querySelector('app-icone')).not.toBeNull();
    expect(avisos[1].className).toBe('atencao');
    expect(avisos[1].textContent).toContain('Atenção: O fluxo foi encerrado por taxa não paga no prazo.');
  });

  it('encerrado: o final com selo e a data', () => {
    api.posicao.mockReturnValue(
      of(
        posicao({
          estado: 'ENCERRADO',
          etapaAtual: null,
          emParalelo: [],
          complementacao: null,
          multiInstancia: null,
          avisos: [],
          final: { rotulo: 'Cancelado pelo cadastrante', encerradoEm: '2026-03-08T09:30:00' },
        }),
      ),
    );
    const { raiz } = criar();
    const t = texto(raiz);

    expect(raiz.querySelector('.onde-etapa')!.textContent).toBe('Cancelado pelo cadastrante');
    expect(t).toContain('Final');
    expect(t).toContain('Encerrado em 08/03/2026 09:30');
    expect(raiz.querySelector('.onde-paralelo')).toBeNull();
  });

  it('P38: verificação da revisão é "Concluído" (arquivamento), não etapa em andamento, e sem selo "Final"', () => {
    api.posicao.mockReturnValue(
      of(
        posicao({
          estado: 'CONCLUIDO',
          etapaAtual: null,
          emParalelo: [],
          complementacao: null,
          multiInstancia: null,
          avisos: [{ codigo: 'CONCLUIDO_ANTES_DA_VERIFICACAO', texto: 'Processo concluído; a verificação da revisão é só arquivamento.', gravidade: 'INFO' }],
          final: { rotulo: 'Concluído', encerradoEm: '2026-03-08T09:30:00' },
        }),
      ),
    );
    const { raiz } = criar();
    const t = texto(raiz);

    expect(raiz.querySelector('.onde-etapa')!.textContent).toBe('Concluído');
    expect(t).toContain('Processo concluído');
    expect(t).toContain('Concluído desde 08/03/2026 09:30');
    expect(t).not.toContain('Final');
    expect(t).not.toContain('Verificação da revisão');
    expect(t).toContain('Informação: Processo concluído; a verificação da revisão é só arquivamento.');
  });

  it('P38: o resumo do processo e da busca também mostra "Concluído" como estado encerrado', () => {
    api.posicao.mockReturnValue(of(posicao({ estado: 'CONCLUIDO', etapaAtual: null, emParalelo: [], multiInstancia: null, avisos: [], final: { rotulo: 'Concluído', encerradoEm: null } })));
    const { raiz } = criar('celula');
    const ponto = raiz.querySelector('.ponto')!;

    expect(ponto.classList.contains('encerrado')).toBe(true);
    expect(ponto.getAttribute('aria-label')).toBe('Estado: Concluído');
    expect(texto(raiz)).toContain('Concluído');
  });

  it('sem instância: histórico detalhado indisponível', () => {
    api.posicao.mockReturnValue(of(posicao({ estado: 'SEM_INSTANCIA', etapaAtual: null, emParalelo: [], multiInstancia: null, avisos: [] })));
    const { raiz } = criar();

    expect(texto(raiz)).toContain('Histórico detalhado indisponível para este requerimento.');
    expect(raiz.querySelector('svg[data-diagrama]')).toBeNull();
  });

  it('não suportado', () => {
    api.posicao.mockReturnValue(
      of(posicao({ estado: 'NAO_SUPORTADO', modelo: null, etapaAtual: null, emParalelo: [], multiInstancia: null, avisos: [] })),
    );
    const { raiz } = criar();

    expect(texto(raiz)).toContain('Fluxo ainda não suportado pelo Rastro.');
  });

  it('indisponível (200): mensagem neutra e "Tentar novamente" que lê de novo', () => {
    api.posicao.mockReturnValueOnce(
      of(posicao({ estado: 'INDISPONIVEL', etapaAtual: null, emParalelo: [], multiInstancia: null, complementacao: null, avisos: [] })),
    );
    api.posicao.mockReturnValueOnce(of(posicao()));
    const { fixture, raiz } = criar();

    expect(texto(raiz)).toContain('Não foi possível consultar o andamento agora. O restante da página não foi afetado.');
    const botao = [...raiz.querySelectorAll('button')].find((b) => b.textContent!.includes('Tentar novamente'))!;
    botao.click();
    fixture.detectChanges();

    expect(api.posicao).toHaveBeenCalledTimes(2);
    expect(raiz.querySelector('.onde-etapa')!.textContent).toBe('Análise do processo');
  });

  it('erro HTTP fica no próprio bloco, com "Tentar novamente"', () => {
    api.posicao.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503, error: { errorCode: 'SIGA_INDISPONIVEL' } })),
    );
    const { raiz } = criar();

    expect(raiz.querySelector('[role="alert"]')!.textContent!.length).toBeGreaterThan(0);
    expect(texto(raiz)).toContain('Tentar novamente');
    semIdTecnicoNemCpf(raiz);
  });

  describe('modo resumido (processo com vários requerimentos, S7 bloco 9)', () => {
    it('uma linha com ponto de estado acessível, etapa, com quem, selo e "+N"; expande para o bloco completo', () => {
      api.posicao.mockReturnValue(of(posicao()));
      const { fixture, raiz } = criar('resumido');

      expect(raiz.querySelector('section')).toBeNull(); // sem o cabeçalho do bloco completo
      const ponto = raiz.querySelector('.ponto')!;
      expect(ponto.classList.contains('ativo')).toBe(true);
      expect(ponto.getAttribute('role')).toBe('img');
      expect(ponto.getAttribute('aria-label')).toBe('Estado: Em andamento');
      expect(texto(raiz)).toContain('Análise do processo · Analista');
      expect(texto(raiz)).toContain('Tarefa');
      expect(texto(raiz)).toContain('+2 em paralelo');
      expect(raiz.querySelector('.onde-participantes')).toBeNull();

      const abrir = [...raiz.querySelectorAll('button')].find((b) => b.textContent!.includes('Ver posição completa'))!;
      expect(abrir.getAttribute('aria-expanded')).toBe('false');
      abrir.click();
      fixture.detectChanges();

      expect(abrir.getAttribute('aria-expanded')).toBe('true');
      expect(abrir.textContent!.trim()).toBe('Ver menos');
      const corpo = raiz.querySelector(`#${abrir.getAttribute('aria-controls')}`)!;
      expect(corpo.textContent).toContain('1 de 3 concluídos');
      expect(corpo.querySelectorAll('.onde-participantes li')).toHaveLength(3);
      semIdTecnicoNemCpf(raiz);
    });

    it('ponto de estado por situação: atenção, encerrado, neutro e erro', () => {
      const casos: [Partial<PosicaoDoRequerimento>, string, string][] = [
        [{ avisos: [{ codigo: 'X', texto: 'Aviso.', gravidade: 'ATENCAO' }] }, 'ponto atencao', 'Estado: Em andamento, com aviso'],
        [{ estado: 'ENCERRADO', final: { rotulo: 'Concluído', encerradoEm: null } }, 'ponto encerrado', 'Estado: Encerrado'],
        [{ estado: 'SEM_INSTANCIA' }, 'ponto neutro', 'Estado: Sem histórico detalhado'],
        [{ estado: 'NAO_SUPORTADO' }, 'ponto neutro', 'Estado: Não suportado'],
        [{ estado: 'INDISPONIVEL' }, 'ponto erro', 'Estado: Indisponível'],
      ];
      for (const [sobre, classe, rotulo] of casos) {
        TestBed.resetTestingModule();
        api.posicao.mockReturnValue(of(posicao(sobre)));
        const { raiz } = criar('resumido');
        const ponto = raiz.querySelector('.ponto')!;
        expect([...ponto.classList].sort()).toEqual(classe.split(' ').sort());
        expect(ponto.getAttribute('aria-label')).toBe(rotulo);
      }
    });
  });

  describe('modo célula (coluna "Etapa atual" da busca)', () => {
    it('só a linha, sem botão de expandir', () => {
      api.posicao.mockReturnValue(of(posicao({ estado: 'ENCERRADO', final: { rotulo: 'Concluído', encerradoEm: null } })));
      const { raiz } = criar('celula');

      expect(texto(raiz)).toContain('Concluído');
      expect(raiz.querySelector('button')).toBeNull();
    });

    it('erro: "não foi possível" e um botão de ícone para consultar de novo', () => {
      api.posicao.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
      api.posicao.mockReturnValueOnce(of(posicao()));
      const { fixture, raiz } = criar('celula');

      expect(texto(raiz)).toContain('Não foi possível consultar');
      expect(raiz.querySelector('.ponto')!.getAttribute('aria-label')).toBe('Estado: Indisponível');
      const botao = raiz.querySelector<HTMLButtonElement>('button[aria-label="Consultar a etapa de novo"]')!;
      botao.click();
      fixture.detectChanges();

      expect(texto(raiz)).toContain('Análise do processo');
    });
  });
});
