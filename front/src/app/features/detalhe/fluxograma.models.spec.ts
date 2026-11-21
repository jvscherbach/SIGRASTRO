import { montarFluxograma, rotuloCurtoDoFinal } from './fluxograma.models';
import {
  AtoDaPassagem,
  EtapaDaPassagem,
  PassagemDaTramitacao,
  ResponsavelDaPassagem,
  TramitacaoDoRequerimento,
} from './tramitacao.models';

const dia = (n: number): string => `2026-03-${String(n).padStart(2, '0')}T09:00:00`;
const etapa = (id: string, rotulo: string, tipo: EtapaDaPassagem['tipo'] = 'TAREFA_HUMANA'): EtapaDaPassagem => ({ id, rotulo, tipo });
const CADASTRO = etapa('cadastro_do_requerimento', 'Cadastro do requerimento');
const CONFERENCIA = etapa('conferencia_dos_documentos', 'Conferência dos documentos');
const TAXA = etapa('emissao_da_taxa', 'Emissão da taxa', 'AUTOMATICA');
const PAGAMENTO = etapa('aguardando_pagamento', 'Aguardando pagamento', 'ESPERA');
const ISENCAO = etapa('isencao_confirmada', 'Isenção confirmada', 'AUTOMATICA');
const ANALISE = etapa('analise_do_processo', 'Análise do processo');
const EMISSAO = etapa('emissao_do_ato', 'Emissão do ato', 'EMISSAO');
const CORRECAO = etapa('correcao_do_requerimento', 'Correção do requerimento');
const VERIFICACAO = etapa('verificacao_da_revisao_do_processo', 'Verificação da revisão do processo');

function pessoa(papel: string, nome: string | null, estado: ResponsavelDaPassagem['estado'] = 'CONCLUIU'): ResponsavelDaPassagem {
  return { tipo: 'SERVIDOR', papel, nome, inicio: dia(3), fim: estado === 'PENDENTE' ? null : dia(4), estado };
}

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

const tres = [pessoa('Analista', 'Servidor Exemplo 05'), pessoa('Analista', 'Servidor Exemplo 06'), pessoa('Analista', null)];
const rotulos = (f: ReturnType<typeof montarFluxograma>) =>
  f.itens.map((i) => (i.tipo === 'caixa' ? i.caixa.rotulo : `[${i.caixas.map((c) => c.rotulo).join(' | ')}]`));

describe('montarFluxograma', () => {
  it('HC01: só o que passou, com a taxa e o pagamento, a análise em uma caixa e a verificação como caixa própria', () => {
    const f = montarFluxograma(
      t(
        [
          p(CADASTRO, 2, 3),
          p(CONFERENCIA, 3, 4),
          p(TAXA, 4, 4),
          p(PAGAMENTO, 4, 7),
          p(ANALISE, 7, 9, { participantes: tres }),
          p(VERIFICACAO, 9, null, { processoConcluido: true }),
        ],
        { final: { rotulo: 'Concluído', tipo: 'CONCLUIDO_NA_VERIFICACAO', encerradoEm: dia(9) } },
      ),
    );

    expect(rotulos(f)).toEqual([
      'Cadastro do requerimento',
      'Conferência dos documentos',
      'Emissão da taxa',
      'Aguardando pagamento',
      'Análise do processo',
      'Verificação da revisão do processo',
    ]);
    const analise = f.itens[4];
    expect(analise.tipo === 'caixa' && analise.caixa.detalhe).toBe('3 analistas');
    const verificacao = f.itens[5];
    expect(verificacao.tipo === 'caixa' && verificacao.caixa.concluido).toBe(true);
    expect(verificacao.tipo === 'caixa' && verificacao.caixa.estado).toBe('atual');
    // sem evento de fim, sem bola preta
    expect(f.final).toBeNull();
    expect(f.resumo).toContain('6 etapas percorridas');
    expect(f.resumo).toContain('etapa atual: Verificação da revisão do processo');
  });

  it('HC02: isento mostra "Isenção confirmada" no lugar da taxa e do pagamento', () => {
    const f = montarFluxograma(t([p(CADASTRO, 2, 3), p(CONFERENCIA, 3, 4), p(ISENCAO, 4, 4), p(ANALISE, 5, null, { participantes: tres })]));

    expect(rotulos(f)).toEqual(['Cadastro do requerimento', 'Conferência dos documentos', 'Isenção confirmada', 'Análise do processo']);
  });

  it('HC03: a volta é uma nova caixa na linha, com "2ª passagem" (nunca seta de retorno)', () => {
    const f = montarFluxograma(t([p(CADASTRO, 2, 3), p(CONFERENCIA, 3, 4), p(CADASTRO, 4, 5, { ciclo: 2 }), p(CONFERENCIA, 5, null, { ciclo: 2 })]));
    const caixas = f.itens.map((i) => (i.tipo === 'caixa' ? i.caixa : null));

    expect(rotulos(f)).toEqual(['Cadastro do requerimento', 'Conferência dos documentos', 'Cadastro do requerimento', 'Conferência dos documentos']);
    expect(caixas.map((c) => c?.passagem)).toEqual([null, null, '2ª passagem', '2ª passagem']);
  });

  it('HC07: a correção fica numa faixa própria, com a taxa e o pagamento dela, e a análise volta na linha', () => {
    const f = montarFluxograma(
      t([
        p(ANALISE, 2, 5, { participantes: tres }),
        p(CORRECAO, 5, 7, { ramo: 'CORRECAO' }),
        p(etapa('emissao_da_taxa_correcao', 'Emissão da taxa (correção)', 'AUTOMATICA'), 7, 7, { ramo: 'CORRECAO' }),
        p(etapa('aguardando_pagamento_correcao', 'Aguardando pagamento (correção)', 'ESPERA'), 7, 9, { ramo: 'CORRECAO' }),
        p(ANALISE, 9, null, { ciclo: 2, participantes: tres }),
      ]),
    );

    expect(rotulos(f)).toEqual([
      'Análise do processo',
      '[Correção do requerimento | Emissão da taxa (correção) | Aguardando pagamento (correção)]',
      'Análise do processo',
    ]);
    const faixa = f.itens[1];
    expect(faixa.tipo === 'faixa' && faixa.rotulo).toBe('Correção do requerimento');
  });

  it('HC12: a emissão é uma caixa só e lista só os atos que ocorreram', () => {
    const atos: AtoDaPassagem[] = [
      { tipo: 'AUTORIZACAO', rotulo: 'Autorização', inicio: dia(3), fim: dia(4), estado: 'EMITIDO', ocorreu: true },
      { tipo: 'PORTARIA', rotulo: 'Portaria', inicio: dia(3), fim: dia(4), estado: 'EMITIDO', ocorreu: true },
      { tipo: 'OUTROS_ATOS', rotulo: 'Outros atos', inicio: dia(3), fim: dia(4), estado: 'NAO_CONCLUIDO', ocorreu: false },
    ];
    const f = montarFluxograma(t([p(EMISSAO, 3, 4, { atos })]));
    const caixa = f.itens[0];

    expect(f.itens).toHaveLength(1);
    expect(caixa.tipo === 'caixa' && caixa.caixa.detalhe).toBe('Autorização, Portaria');
  });

  it('multi-instância ativa mostra "x de y concluídos"; a esperando pagamento é "Aguardando"', () => {
    const f = montarFluxograma(
      t([p(ANALISE, 2, null, { participantes: [pessoa('Analista', 'Servidor Exemplo 05'), pessoa('Analista', null, 'PENDENTE'), pessoa('Analista', null, 'PENDENTE')] }), p(PAGAMENTO, 3, null)]),
    );
    const analise = f.itens[0];
    const espera = f.itens[1];

    expect(analise.tipo === 'caixa' && analise.caixa.detalhe).toBe('1 de 3 concluídos');
    expect(analise.tipo === 'caixa' && analise.caixa.estado).toBe('atual');
    expect(espera.tipo === 'caixa' && espera.caixa.estado).toBe('aguardando');
    expect(espera.tipo === 'caixa' && espera.caixa.textoDoEstado).toBe('Aguardando');
  });

  it('a dica (tooltip) traz entrada, saída, duração e com quem, sem CPF', () => {
    const f = montarFluxograma(t([p(CONFERENCIA, 3, 5, { responsaveis: [pessoa('Conferidor', 'Servidor Exemplo 02')] })]));
    const caixa = f.itens[0];

    expect(caixa.tipo === 'caixa' && caixa.caixa.dica).toEqual([
      'Entrada: 03/03/2026 09:00',
      'Saída: 05/03/2026 09:00',
      'Duração: 2 dias',
      'Com quem: Servidor Exemplo 02 · Conferidor',
    ]);
  });

  it('etapa não mapeada entra (aconteceu); automática de ruído não entra', () => {
    const f = montarFluxograma(
      t([p({ id: null, rotulo: 'Etapa não mapeada', tipo: null }, 2, 3), p(etapa('conclusao', 'Conclusão', 'AUTOMATICA'), 3, 3), p(CADASTRO, 3, null)]),
    );

    expect(rotulos(f)).toEqual(['Etapa não mapeada', 'Cadastro do requerimento']);
  });

  describe('final (bola preta)', () => {
    it('só com evento de fim; o rótulo vem do final, na forma curta', () => {
      const f = montarFluxograma(t([p(CADASTRO, 2, 3)], { estado: 'ENCERRADO', final: { rotulo: 'Cancelado pelo cadastrante', tipo: 'FINAL', encerradoEm: dia(12) } }));

      expect(f.final).toEqual({ rotulo: 'Cancelado pelo cadastrante', data: '12/03/2026' });
      expect(f.resumo).toContain('final: Cancelado pelo cadastrante');
    });

    it('rótulos curtos dos finais do Hídrico e do LAC', () => {
      for (const r of [
        'Concluído',
        'Cancelado pelo cadastrante',
        'Expirado no cadastro',
        'Expirado na correção',
        'Cancelado por falta de pagamento',
        'Encerrado por taxa não paga no prazo',
      ]) {
        expect(rotuloCurtoDoFinal(r)).toBe(r);
      }
      expect(rotuloCurtoDoFinal('Arquivado (termo não enviado no prazo)')).toBe('Arquivado');
    });
  });

  describe('sem caminho', () => {
    it('plano B, não suportado e indisponível não geram fluxograma', () => {
      expect(montarFluxograma(t([], { estado: 'SEM_INSTANCIA', aproximado: true, planoB: { selo: 's', semDetalhamento: true, etapas: [], marcos: [], situacao: null, eventosIgnorados: 0, eventosTruncados: false } })).itens).toEqual([]);
      expect(montarFluxograma(t([], { estado: 'NAO_SUPORTADO' })).itens).toEqual([]);
      expect(montarFluxograma(t([], { estado: 'INDISPONIVEL' })).itens).toEqual([]);
    });

    it('propriedade: nunca há mais caixas que passagens (nenhuma etapa futura)', () => {
      const passagens = [p(CADASTRO, 2, 3), p(CONFERENCIA, 3, 4), p(TAXA, 4, 4), p(PAGAMENTO, 4, null)];
      const f = montarFluxograma(t(passagens));
      const total = f.itens.reduce((n, i) => n + (i.tipo === 'caixa' ? 1 : i.caixas.length), 0);

      expect(total).toBeLessThanOrEqual(passagens.length);
      expect(f.resumo).not.toContain('Análise');
    });
  });
});
