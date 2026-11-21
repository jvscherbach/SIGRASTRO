import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { TrilhaService } from '../../shared/trilha.service';
import { DocumentosApiService } from '../documentos/documentos-api.service';
import { DetalheApiService } from './detalhe-api.service';
import { DetalheComponent } from './detalhe.component';
import { Objeto, Prioridade, Requerimento, TarefaAtual } from './detalhe.models';

const SEM_OBJETO: Objeto = { objetivos: [], finalidadesAtividades: [], tiposObjetivo: [], atividadeLicenciamento: null };
const NORMAL: Prioridade = { codigo: 'NORMAL', descricao: 'Atendimento normal', criterios: [] };

function requerimento(sobre: Partial<Requerimento> = {}): Requerimento {
  return {
    id: 101,
    identificacao: { rotulo: 'Processo', numero: '2825/2025', protocolado: true },
    numeroRequerimento: '3331/2025',
    numeroProcesso: '2825/2025',
    tipoRequerimento: null,
    prioridade: NORMAL,
    situacao: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
    abertura: '2025-03-31T10:12:00',
    atualizacao: '2025-04-29T09:14:00',
    interessado: { nome: 'Associação Vale Verde', cpfCnpj: '12345678000190' },
    tipoProcesso: 'Uso de Recursos Hídricos',
    objeto: SEM_OBJETO,
    tarefaAtual: { estado: 'SEM_TAREFA', nome: null, desde: null, fluxo: null },
    ...sobre,
  };
}

describe('DetalheComponent', () => {
  let api: { obter: ReturnType<typeof vi.fn>; posicao: ReturnType<typeof vi.fn> };
  let documentosApi: { listar: ReturnType<typeof vi.fn> };

  function criar(id = '101'): { fixture: ComponentFixture<DetalheComponent>; raiz: HTMLElement } {
    TestBed.configureTestingModule({
      imports: [DetalheComponent],
      providers: [
        provideRouter([]),
        { provide: DetalheApiService, useValue: api },
        { provide: DocumentosApiService, useValue: documentosApi },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => id } } } },
        { provide: AuthService, useValue: { usuario: signal({ login: 'u.demo' }) } },
      ],
    });
    const fixture = TestBed.createComponent(DetalheComponent);
    fixture.detectChanges();
    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  const texto = (raiz: HTMLElement) => raiz.textContent!.replace(/\s+/g, ' ');

  beforeEach(() => {
    localStorage.clear();
    // A posição (S7) carrega sozinha; aqui fica pendente, e os estados dela são provados em onde-esta-agora.component.spec.
    api = { obter: vi.fn(), posicao: vi.fn(() => NEVER) };
    documentosApi = { listar: vi.fn(() => NEVER) };
  });

  afterEach(() => localStorage.clear());

  describe('"Onde está agora" (S7)', () => {
    it('fica acima do objeto e dos documentos e pede a posição do mesmo requerimento', () => {
      api.obter.mockReturnValue(of(requerimento()));
      const { raiz } = criar();
      const onde = raiz.querySelector('app-folha-sobreposta app-onde-esta-agora')!;
      const objeto = raiz.querySelector('app-folha-sobreposta [aria-label="Objeto do requerimento"]')!;
      const documentos = raiz.querySelector('app-folha-sobreposta app-documentos-secao')!;

      expect(onde.compareDocumentPosition(objeto) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(onde.compareDocumentPosition(documentos) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(api.posicao).toHaveBeenCalledWith(101);
    });

    it('falha da posição não derruba o detalhe', () => {
      api.obter.mockReturnValue(of(requerimento()));
      api.posicao.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      const { raiz } = criar();

      expect(raiz.querySelector('h1')!.textContent).toBe('2825/2025');
      expect(texto(raiz)).toContain('Objeto do requerimento');
      expect(texto(raiz)).toContain('Tentar novamente');
    });
  });

  describe('identificação (RN04)', () => {
    it('com processo, o número do processo é o título e o do requerimento aparece à parte', () => {
      api.obter.mockReturnValue(of(requerimento()));
      const { raiz } = criar();

      expect(raiz.querySelector('h1')!.textContent).toBe('2825/2025');
      expect(raiz.querySelector('.eyebrow')!.textContent).toBe('Processo');
      expect(texto(raiz)).toContain('Nº do requerimento3331/2025');
      expect(texto(raiz)).not.toContain('Não protocolado');
    });

    it('sem processo, mostra o requerimento com a marca "Não protocolado"', () => {
      api.obter.mockReturnValue(
        of(requerimento({ numeroProcesso: null, identificacao: { rotulo: 'Requerimento', numero: '1187/2026', protocolado: false } })),
      );
      const { raiz } = criar();

      expect(raiz.querySelector('h1')!.textContent).toBe('1187/2026');
      expect(raiz.querySelector('.eyebrow')!.textContent).toBe('Requerimento');
      expect(texto(raiz)).toContain('Não protocolado');
      expect(texto(raiz)).not.toContain('Nº do requerimento');
    });

    it('completa a trilha do topo e registra nos recentes', () => {
      api.obter.mockReturnValue(of(requerimento()));
      const { fixture } = criar();

      expect(TestBed.inject(TrilhaService).complemento()).toEqual(['Consulta', 'Processo 2825/2025']);
      expect(JSON.parse(localStorage.getItem('rastro.recentes.u.demo')!)).toEqual([
        { id: 101, rotulo: 'Processo 2825/2025', numeroRequerimento: '3331/2025', situacao: 'Em análise' },
      ]);
      fixture.destroy();
      expect(TestBed.inject(TrilhaService).complemento()).toBeNull();
    });
  });

  describe('dados do requerimento', () => {
    it('mostra interessado, CPF/CNPJ formatado, situação, tipo e datas', () => {
      api.obter.mockReturnValue(of(requerimento({ tipoRequerimento: 'Emissão' })));
      const { raiz } = criar();
      const t = texto(raiz);

      expect(t).toContain('Associação Vale Verde');
      expect(t).toContain('12.345.678/0001-90');
      expect(t).toContain('Em análise');
      expect(t).toContain('Uso de Recursos Hídricos');
      expect(t).toContain('31/03/2025');
      expect(t).toContain('29/04/2025');
      expect(t).toContain('Processo · Emissão');
      expect(t).toContain('Atendimento normal');
    });

    it('campos não informados aparecem com traço, sem número bruto de prioridade', () => {
      api.obter.mockReturnValue(of(requerimento({ interessado: null, atualizacao: null, tipoProcesso: null })));
      const { raiz } = criar();
      const t = texto(raiz);

      expect(t).toContain('Nome—');
      expect(t).toContain('CPF/CNPJ—');
      expect(t).toContain('Última atualização—');
      expect(t).toContain('Tipo do requerimento—');
      expect(raiz.querySelector('.tag.blue')).toBeNull();
    });

    it('mostra o objeto do requerimento em cartões (detalhes no spec do componente)', () => {
      api.obter.mockReturnValue(
        of(
          requerimento({
            objeto: {
              objetivos: ['Consumo humano', 'Irrigação'],
              finalidadesAtividades: [{ finalidade: 'Abastecimento público', atividade: 'Captação' }],
              tiposObjetivo: [
                { tipo: 'SUBTERRANEA', tipoDescricao: 'Subterrânea', objetivo: 'Consumo humano' },
                { tipo: 'SUPERFICIAL', tipoDescricao: 'Superficial', objetivo: 'Irrigação' },
              ],
              atividadeLicenciamento: null,
            },
          }),
        ),
      );
      const { raiz } = criar();

      expect(Array.from(raiz.querySelectorAll('.objeto-card')).map((c) => c.getAttribute('aria-label'))).toEqual([
        'Objetivos',
        'Finalidade e atividade',
      ]);
      expect(texto(raiz)).toContain('SubterrâneaConsumo humano');
    });

    it('sem nenhum dado de objeto: um aviso único, sem cartões com traço', () => {
      api.obter.mockReturnValue(of(requerimento()));
      const { raiz } = criar();

      expect(raiz.querySelectorAll('.objeto-card').length).toBe(0);
      expect(texto(raiz)).toContain('Sem objeto cadastrado para este requerimento');
    });
  });

  describe('prioridade de atendimento', () => {
    it('normal: chip cinza "Atendimento normal" e sem critérios', () => {
      api.obter.mockReturnValue(of(requerimento()));
      const { raiz } = criar();

      const chip = raiz.querySelector('.tag.prioridade')!;
      expect(chip.textContent!.trim()).toBe('Atendimento normal');
      expect(chip.classList).toContain('gray');
      expect(texto(raiz)).not.toContain('Critérios de prioridade');
    });

    it('prioritário: chip azul com os critérios na dica e em lista', () => {
      api.obter.mockReturnValue(
        of(
          requerimento({
            prioridade: {
              codigo: 'PRIORITARIO',
              descricao: 'Atendimento prioritário',
              criterios: [
                { codigo: 2, descricao: 'Pessoa com idade igual ou superior a 80 anos' },
                { codigo: 5, descricao: 'Pessoa com doença grave' },
              ],
            },
          }),
        ),
      );
      const { raiz } = criar();

      const chip = raiz.querySelector('.tag.prioridade')!;
      expect(chip.textContent!.trim()).toBe('Atendimento prioritário');
      expect(chip.classList).toContain('blue');
      expect(chip.getAttribute('title')).toBe('Pessoa com idade igual ou superior a 80 anos; Pessoa com doença grave');
      expect(Array.from(raiz.querySelectorAll('.criterios li')).map((li) => li.textContent)).toEqual([
        'Pessoa com idade igual ou superior a 80 anos',
        'Pessoa com doença grave',
      ]);
    });

    it('a seção de documentos pede a lista do requerimento aberto', () => {
      api.obter.mockReturnValue(of(requerimento({ id: 101 })));
      const { raiz } = criar();

      expect(raiz.querySelector('app-documentos-secao')).not.toBeNull();
      expect(documentosApi.listar).toHaveBeenCalledWith(101);
      expect(texto(raiz)).not.toContain('Sprint 2');
    });
  });

  describe('tarefa atual', () => {
    const tarefa = (sobre: Partial<TarefaAtual>): TarefaAtual => ({ estado: 'ATIVA', nome: null, desde: null, fluxo: null, ...sobre });

    it('ativa com responsável atribuído: nome, desde quando e o nome do responsável', () => {
      api.obter.mockReturnValue(
        of(
          requerimento({
            tarefaAtual: tarefa({
              nome: 'Analisar requerimento',
              desde: '2025-04-29T09:14:00',
              responsavel: { situacao: 'ATRIBUIDO', nome: 'Maria Nova de Souza' },
            }),
          }),
        ),
      );
      const t = texto(criar().raiz);

      expect(t).toContain('Analisar requerimento');
      expect(t).toContain('29/04/2025 09:14');
      expect(t).toContain('ResponsávelMaria Nova de Souza');
    });

    it('responsável não atribuído e não identificado', () => {
      api.obter.mockReturnValue(of(requerimento({ tarefaAtual: tarefa({ nome: 'Analisar', responsavel: { situacao: 'NAO_ATRIBUIDA' } }) })));
      expect(texto(criar().raiz)).toContain('ResponsávelNão atribuída');

      TestBed.resetTestingModule();
      api.obter.mockReturnValue(of(requerimento({ tarefaAtual: tarefa({ nome: 'Analisar', responsavel: { situacao: 'NAO_IDENTIFICADO' } }) })));
      expect(texto(criar().raiz)).toContain('ResponsávelResponsável não identificado');
    });

    it('sem o campo na resposta (Operador), a linha "Responsável" não é renderizada', () => {
      api.obter.mockReturnValue(of(requerimento({ tarefaAtual: tarefa({ nome: 'Analisar', desde: '2025-04-29T09:14:00' }) })));
      const { raiz } = criar();

      expect(raiz.querySelector('.kv.responsavel')).toBeNull();
      expect(texto(raiz)).not.toContain('Responsável');
      expect(texto(raiz)).toContain('Analisar');
    });

    it('sem tarefa ativa', () => {
      api.obter.mockReturnValue(of(requerimento()));

      expect(texto(criar().raiz)).toContain('Sem tarefa ativa');
    });

    it('indisponível não esconde o restante do detalhe', () => {
      api.obter.mockReturnValue(of(requerimento({ tarefaAtual: tarefa({ estado: 'INDISPONIVEL' }) })));
      const t = texto(criar().raiz);

      expect(t).toContain('Tarefa indisponível no momento');
      expect(t).toContain('Associação Vale Verde');
    });

    it('a interface nunca cita o motor de fluxo', () => {
      api.obter.mockReturnValue(of(requerimento()));

      expect(texto(criar().raiz).toLowerCase()).not.toContain('camunda');
    });
  });

  describe('erros', () => {
    function falha(status: number, corpo: object) {
      api.obter.mockReturnValue(throwError(() => new HttpErrorResponse({ status, error: corpo })));
      return criar().raiz;
    }

    it('403 NAO_INTERESSADO', () => {
      const t = texto(falha(403, { errorCode: 'NAO_INTERESSADO' }));

      expect(t).toContain('Seu perfil consulta apenas processos e requerimentos em que você é o interessado.');
      expect(t).toContain('Voltar para a consulta');
    });

    it('404 NAO_ENCONTRADO', () => {
      expect(texto(falha(404, { errorCode: 'NAO_ENCONTRADO' }))).toContain('Requerimento não encontrado.');
    });

    it('503 SIGA_INDISPONIVEL', () => {
      expect(texto(falha(503, { errorCode: 'SIGA_INDISPONIVEL' }))).toContain('O banco do SIGA está indisponível no momento.');
    });

    it('id inválido na URL nem chama a API', () => {
      const { raiz } = criar('abc');

      expect(api.obter).not.toHaveBeenCalled();
      expect(texto(raiz)).toContain('Requerimento não encontrado.');
    });
  });

  it('"Voltar para a consulta" volta à lista', () => {
    api.obter.mockReturnValue(of(requerimento()));
    const { fixture, raiz } = criar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    raiz.querySelector<HTMLButtonElement>('.backlink')!.click();
    fixture.detectChanges();

    expect(navegar).toHaveBeenCalledWith(['/consulta']);
  });
});
