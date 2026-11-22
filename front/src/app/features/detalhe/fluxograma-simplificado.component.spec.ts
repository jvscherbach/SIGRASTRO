import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FluxogramaSimplificadoComponent } from './fluxograma-simplificado.component';
import { EtapaDaPassagem, PassagemDaTramitacao, TramitacaoDoRequerimento } from './tramitacao.models';

const dia = (n: number): string => `2026-03-${String(n).padStart(2, '0')}T09:00:00`;
const etapa = (id: string, rotulo: string, tipo: EtapaDaPassagem['tipo'] = 'TAREFA_HUMANA'): EtapaDaPassagem => ({ id, rotulo, tipo });
const CADASTRO = etapa('cadastro_do_requerimento', 'Cadastro do requerimento');
const CONFERENCIA = etapa('conferencia_dos_documentos', 'Conferência dos documentos');
const TAXA = etapa('emissao_da_taxa', 'Emissão da taxa', 'AUTOMATICA');
const PAGAMENTO = etapa('aguardando_pagamento', 'Aguardando pagamento', 'ESPERA');
const CORRECAO = etapa('correcao_do_requerimento', 'Correção do requerimento');

function p(e: EtapaDaPassagem, entrada: number, saida: number | null, sobre: Partial<PassagemDaTramitacao> = {}): PassagemDaTramitacao {
  return {
    etapa: e,
    ciclo: 1,
    ramo: 'PRINCIPAL',
    entrada: dia(entrada),
    saida: saida === null ? null : dia(saida),
    duracaoSegundos: saida === null ? null : (saida - entrada) * 86400,
    atual: saida === null,
    emParalelo: false,
    processoConcluido: false,
    motivoDaVolta: null,
    responsaveis: [],
    participantes: [],
    atos: [],
    ...sobre,
  };
}

function t(passagens: PassagemDaTramitacao[], sobre: Partial<TramitacaoDoRequerimento> = {}): TramitacaoDoRequerimento {
  return {
    idRequerimento: 8001,
    numeroRequerimento: '8001/2026',
    numeroProcesso: null,
    situacaoLicenciamento: null,
    modelo: 'Outorga de recursos hídricos',
    estado: 'ATIVO',
    aproximado: false,
    truncado: false,
    passagens,
    complementacoes: [],
    final: null,
    avisos: [],
    planoB: null,
    ...sobre,
  };
}

describe('FluxogramaSimplificadoComponent', () => {
  function criar(tramitacao: TramitacaoDoRequerimento): { fixture: ComponentFixture<FluxogramaSimplificadoComponent>; raiz: HTMLElement } {
    TestBed.configureTestingModule({ imports: [FluxogramaSimplificadoComponent] });
    const fixture = TestBed.createComponent(FluxogramaSimplificadoComponent);
    fixture.componentRef.setInput('tramitacao', tramitacao);
    fixture.detectChanges();
    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  const FELIZ = t(
    [p(CADASTRO, 2, 3), p(CONFERENCIA, 3, 4), p(TAXA, 4, 4), p(PAGAMENTO, 4, null)],
    {},
  );
  const COM_FINAL = t([p(CADASTRO, 2, 3)], { estado: 'ENCERRADO', final: { rotulo: 'Expirado no cadastro', tipo: 'FINAL', encerradoEm: dia(12) } });

  it('uma linha de caixas em lista semântica, com a etapa atual destacada e o estado em texto e ícone', () => {
    const { raiz } = criar(FELIZ);
    const caixas = Array.from(raiz.querySelectorAll('.caixa'));

    expect(caixas.map((c) => c.querySelector('.caixa-rotulo')!.textContent)).toEqual([
      'Cadastro do requerimento',
      'Conferência dos documentos',
      'Emissão da taxa',
      'Aguardando pagamento',
    ]);
    expect(caixas[0].classList.contains('concluida')).toBe(true);
    expect(caixas[0].querySelector('.caixa-estado')!.textContent).toContain('Concluída');
    expect(caixas[3].classList.contains('aguardando')).toBe(true);
    expect(caixas[3].querySelector('.caixa-estado')!.textContent).toContain('Aguardando');
    expect(caixas[3].querySelector('.icone-estado')!.getAttribute('aria-hidden')).toBe('true');
    expect(raiz.querySelector('ol.fluxo-linha')).not.toBeNull();
    expect(raiz.querySelector('[role="group"]')!.getAttribute('aria-label')).toContain('4 etapas percorridas');
  });

  it('a data de entrada fica sob o rótulo', () => {
    const { raiz } = criar(FELIZ);

    expect(raiz.querySelectorAll('.caixa')[1].querySelector('.caixa-data')!.textContent).toBe('03/03/2026');
  });

  it('cada caixa é focável e liga a dica (tooltip) por aria-describedby', () => {
    const { raiz } = criar(FELIZ);

    for (const caixa of Array.from(raiz.querySelectorAll('.caixa'))) {
      expect(caixa.getAttribute('tabindex')).toBe('0');
      const dica = raiz.querySelector('#' + caixa.getAttribute('aria-describedby'))!;
      expect(dica.getAttribute('role')).toBe('tooltip');
      expect(dica.textContent).toContain('Entrada:');
    }
  });

  it('propriedade: nenhum losango (gateway é caixa normal) e as setas são decorativas', () => {
    const { raiz } = criar(FELIZ);

    expect(raiz.querySelector('polygon')).toBeNull();
    expect(raiz.innerHTML).not.toMatch(/rotate\(45|losango|diamond/i);
    expect(raiz.querySelectorAll('svg.seta').length).toBe(3);
    for (const seta of Array.from(raiz.querySelectorAll('svg.seta'))) {
      expect(seta.getAttribute('aria-hidden')).toBe('true');
    }
  });

  it('final: bola preta com o rótulo escrito em cima, e a bola com texto alternativo', () => {
    const { raiz } = criar(COM_FINAL);
    const final = raiz.querySelector('li.final')!;
    const filhos = Array.from(final.children).map((c) => c.tagName.toLowerCase() + '.' + (c.getAttribute('class') ?? '').split(' ')[0]);

    expect(final.querySelector('.final-rotulo')!.textContent).toBe('Expirado no cadastro');
    expect(filhos.indexOf('span.final-rotulo')).toBeLessThan(filhos.indexOf('svg.bola'));
    const bola = final.querySelector('svg.bola')!;
    expect(bola.getAttribute('role')).toBe('img');
    expect(bola.getAttribute('aria-label')).toBe('Final: Expirado no cadastro');
    expect(bola.querySelector('circle')!.getAttribute('fill')).toBe('#000');
    expect(final.textContent).toContain('12/03/2026');
  });

  it('sem final (em andamento) não há bola', () => {
    expect(criar(FELIZ).raiz.querySelector('svg.bola')).toBeNull();
  });

  it('a correção aparece numa faixa identificada, com a lista própria', () => {
    const { raiz } = criar(t([p(CADASTRO, 2, 3), p(CORRECAO, 3, 5, { ramo: 'CORRECAO' }), p(CADASTRO, 5, null, { ciclo: 2 })]));
    const faixa = raiz.querySelector('li.faixa')!;

    expect(faixa.querySelector('.faixa-rotulo')!.textContent).toBe('Correção do requerimento');
    expect(faixa.querySelectorAll('.caixa')).toHaveLength(1);
    expect(raiz.querySelectorAll('.caixa')).toHaveLength(3);
    expect(Array.from(raiz.querySelectorAll('.caixa-passagem')).map((e) => e.textContent)).toEqual(['2ª passagem']);
  });

  it('não renderiza nada para plano B, não suportado e indisponível', () => {
    for (const estado of ['SEM_INSTANCIA', 'NAO_SUPORTADO', 'INDISPONIVEL'] as const) {
      TestBed.resetTestingModule();
      expect(criar(t([], { estado })).raiz.querySelector('.fluxo')).toBeNull();
    }
  });

  it('nenhum id técnico nem CPF no DOM', () => {
    const texto = criar(COM_FINAL).raiz.textContent!;

    expect(texto).not.toMatch(/task|gtw|gwt|Event_|Activity_|procSurh|procOutorga/);
    expect(texto).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
  });
});
