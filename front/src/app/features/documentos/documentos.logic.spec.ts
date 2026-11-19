import {
  ORDEM_PADRAO,
  chaveDe,
  classeDaExtensao,
  filtrar,
  nomeExibido,
  normalizar,
  ordenar,
  referenciaDe,
  rotuloDoBloqueio,
  selecaoPadrao,
  selecionavel,
  seraConvertido,
  semConversao,
  visualizavel,
} from './documentos.logic';
import { documento } from './documentos.fixtures';
import { DocumentoItem } from './documentos.models';

describe('documentos.logic', () => {
  const lista: DocumentoItem[] = [
    documento({ id: '10', titulo: 'Relatório técnico', nomeArquivo: 'relatorio.pdf', data: '2025-04-03T10:00:00' }),
    documento({ id: '9', titulo: 'Ofício', nomeArquivo: 'oficio.pdf', origem: 'JUNTADA', origemDescricao: 'Juntada', data: '2025-04-01T10:00:00' }),
    documento({ id: '3', titulo: 'Sem data', nomeArquivo: 'sd.pdf', data: null }),
    documento({
      id: '4',
      titulo: 'Cálculo',
      nomeArquivo: 'calculo.xlsx',
      tipoArquivo: 'PLANILHA',
      extensao: 'xlsx',
      bloqueio: 'NAO_PDF',
      selecionavelPorPadrao: false,
      data: '2025-04-02T10:00:00',
    }),
  ];

  describe('chaves', () => {
    it('chave e referência usam origem e id (texto), nunca a chave do arquivo', () => {
      const d = documento({ origem: 'JUNTADA', id: '900118' });

      expect(chaveDe(d)).toBe('JUNTADA:900118');
      expect(referenciaDe(d)).toEqual({ origem: 'JUNTADA', id: '900118' });
    });
  });

  describe('busca', () => {
    it('ignora acento e maiúsculas e olha título, id e nome do arquivo', () => {
      expect(filtrar(lista, 'RELATORIO', '').map((d) => d.id)).toEqual(['10']);
      expect(filtrar(lista, 'oficio', '').map((d) => d.id)).toEqual(['9']);
      expect(filtrar(lista, 'calculo.xlsx', '').map((d) => d.id)).toEqual(['4']);
      expect(filtrar(lista, '10', '').map((d) => d.id)).toEqual(['10']);
    });

    it('busca vazia ou só com espaços devolve tudo', () => {
      expect(filtrar(lista, '   ', '').length).toBe(4);
    });

    it('sem resultado devolve lista vazia', () => {
      expect(filtrar(lista, 'inexistente', '')).toEqual([]);
    });

    it('documento sigiloso mascarado não casa pelo título real', () => {
      const mascarado = documento({ id: '77', titulo: 'Documento sigiloso', nomeArquivo: null, sigiloso: true, acessivel: false });

      expect(filtrar([mascarado], 'laudo', '')).toEqual([]);
      expect(filtrar([mascarado], '77', '').length).toBe(1);
    });
  });

  describe('filtro de origem', () => {
    it('mostra só a origem escolhida e combina com a busca', () => {
      expect(filtrar(lista, '', 'JUNTADA').map((d) => d.id)).toEqual(['9']);
      expect(filtrar(lista, 'relatorio', 'JUNTADA')).toEqual([]);
    });
  });

  describe('ordenação', () => {
    it('padrão: data crescente, sem data ao final', () => {
      expect(ordenar(lista, ORDEM_PADRAO).map((d) => d.id)).toEqual(['9', '4', '10', '3']);
    });

    it('empate de data segue o servidor: origem (ordem do docs/04), depois id numérico; sem data por origem também', () => {
      const t = '2025-04-02T14:00:00';
      const empate = [
        documento({ id: '10', origem: 'IMOVEL', data: t }),
        documento({ id: '10', origem: 'DOCUMENTO_PROCESSO', data: t }),
        documento({ id: '3', origem: 'COMPLEMENTACAO', data: null }),
        documento({ id: '9', origem: 'DOCUMENTO_PROCESSO', data: t }),
        documento({ id: '4', origem: 'DOCUMENTO_PROCESSO', data: null }),
        documento({ id: '1', origem: 'DOCUMENTO_REQUERIMENTO', data: '2025-04-02T13:59:59' }),
      ];

      expect(ordenar(empate, ORDEM_PADRAO).map(chaveDe)).toEqual([
        'DOCUMENTO_REQUERIMENTO:1',
        'DOCUMENTO_PROCESSO:9',
        'DOCUMENTO_PROCESSO:10',
        'IMOVEL:10',
        'DOCUMENTO_PROCESSO:4',
        'COMPLEMENTACAO:3',
      ]);
    });

    it('data decrescente também deixa o sem data ao final', () => {
      expect(ordenar(lista, { campo: 'data', direcao: -1 }).map((d) => d.id)).toEqual(['10', '4', '9', '3']);
    });

    it('por documento, sem considerar acento (Cálculo antes de Ofício)', () => {
      expect(ordenar(lista, { campo: 'titulo', direcao: 1 }).map((d) => d.titulo)).toEqual([
        'Cálculo',
        'Ofício',
        'Relatório técnico',
        'Sem data',
      ]);
    });

    it('por origem, com desempate pelo id numérico (9 antes de 10)', () => {
      const ids = ordenar(lista, { campo: 'origem', direcao: 1 }).map((d) => d.id);

      // "Documento do processo" (3, 4, 10) vem antes de "Juntada" (9); dentro da origem, o id numérico: 3 < 4 < 10
      expect(ids).toEqual(['3', '4', '10', '9']);
    });

    it('não altera a lista original', () => {
      const copia = [...lista];

      ordenar(lista, { campo: 'titulo', direcao: -1 });

      expect(lista).toEqual(copia);
    });
  });

  describe('seleção', () => {
    it('só o que a API marca como selecionável por padrão entra na seleção inicial', () => {
      const sel = selecaoPadrao([
        ...lista,
        documento({ id: '5', situacao: 'SUBSTITUIDO', }),
        documento({ id: '6', situacao: 'INDISPONIVEL', bloqueio: 'INDISPONIVEL', selecionavelPorPadrao: false }),
        documento({ id: '7', sigiloso: true, acessivel: false, bloqueio: 'SIGILOSO', selecionavelPorPadrao: false }),
      ]);

      expect(Array.from(sel).sort()).toEqual(['DOCUMENTO_PROCESSO:10', 'DOCUMENTO_PROCESSO:3', 'DOCUMENTO_PROCESSO:5', 'JUNTADA:9']);
    });

    it('bloqueado não é selecionável; substituído em PDF é (manualmente)', () => {
      expect(selecionavel(documento({ bloqueio: 'NAO_PDF' }))).toBe(false);
      expect(selecionavel(documento({ bloqueio: 'INDISPONIVEL' }))).toBe(false);
      expect(selecionavel(documento({ bloqueio: 'SIGILOSO' }))).toBe(false);
      expect(selecionavel(documento({ situacao: 'SUBSTITUIDO', selecionavelPorPadrao: false }))).toBe(true);
    });
  });

  describe('apresentação', () => {
    it('rótulos dos bloqueios', () => {
      expect(rotuloDoBloqueio(documento({ bloqueio: 'INDISPONIVEL' }))).toBe('arquivo indisponível');
      expect(rotuloDoBloqueio(documento({ bloqueio: 'NAO_PDF' }))).toBe('conversão indisponível');
      expect(rotuloDoBloqueio(documento({ bloqueio: 'SIGILOSO' }))).toBe('documento sigiloso');
      expect(rotuloDoBloqueio(documento())).toBeNull();
    });

    it('classe do selo pela categoria do arquivo; mascarado fica neutro', () => {
      expect(classeDaExtensao(documento())).toBe('pdf');
      expect(classeDaExtensao(documento({ tipoArquivo: 'PLANILHA' }))).toBe('xls');
      expect(classeDaExtensao(documento({ tipoArquivo: 'TEXTO' }))).toBe('doc');
      expect(classeDaExtensao(documento({ tipoArquivo: 'IMAGEM' }))).toBe('img');
      expect(classeDaExtensao(documento({ tipoArquivo: null }))).toBe('off');
    });

    it('convertido: planilha, texto e imagem sem bloqueio; txt e conversão desligada ficam como sem conversão', () => {
      expect(seraConvertido(documento({ tipoArquivo: 'PLANILHA', extensao: 'xlsx' }))).toBe(true);
      expect(seraConvertido(documento({ tipoArquivo: 'TEXTO' }))).toBe(true);
      expect(seraConvertido(documento({ tipoArquivo: 'IMAGEM' }))).toBe(true);
      expect(seraConvertido(documento())).toBe(false);
      expect(seraConvertido(documento({ tipoArquivo: 'PLANILHA', bloqueio: 'NAO_PDF' }))).toBe(false);
      expect(semConversao(documento({ tipoArquivo: 'PLANILHA', bloqueio: 'NAO_PDF' }))).toBe(true);
      expect(semConversao(documento({ tipoArquivo: 'PLANILHA' }))).toBe(false);
    });

    it('visualizar: tudo menos o indisponível (o sigiloso abre só com o motivo)', () => {
      expect(visualizavel(documento())).toBe(true);
      expect(visualizavel(documento({ bloqueio: 'SIGILOSO' }))).toBe(true);
      expect(visualizavel(documento({ bloqueio: 'NAO_PDF' }))).toBe(true);
      expect(visualizavel(documento({ bloqueio: 'INDISPONIVEL' }))).toBe(false);
    });

    it('normalizar tira acento, maiúsculas e espaços das pontas', () => {
      expect(normalizar('  AÇÃO Média ')).toBe('acao media');
      expect(normalizar(null)).toBe('');
    });
  });

  describe('nome do arquivo × tipo (S6)', () => {
    it('a coluna do nome mostra o nome do arquivo; sem ele, o título; sem ambos, o rótulo', () => {
      expect(nomeExibido({ nomeArquivo: 'Planilha de vazão.v2.xlsx', titulo: 'Teste de bombeamento.' }, 'Documento 1')).toBe(
        'Planilha de vazão.v2.xlsx',
      );
      expect(nomeExibido({ nomeArquivo: null, titulo: 'Documento sigiloso' }, 'Documento 1')).toBe('Documento sigiloso');
      expect(nomeExibido({ nomeArquivo: null, titulo: null }, 'Documento 1')).toBe('Documento 1');
    });

    it('ordena pelo nome do arquivo, não pelo título (tipo)', () => {
      const itens = [
        documento({ id: '1', titulo: 'Alfa', nomeArquivo: 'zeta.pdf' }),
        documento({ id: '2', titulo: 'Zeta', nomeArquivo: 'alfa.pdf' }),
      ];
      expect(ordenar(itens, { campo: 'titulo', direcao: 1 }).map((d) => d.id)).toEqual(['2', '1']);
    });

    it('o filtro de busca continua achando pelo tipo e pelo nome do arquivo', () => {
      const itens = [documento({ id: '1', titulo: 'Teste de bombeamento.', nomeArquivo: 'laudo-final.xlsx' })];
      expect(filtrar(itens, 'bombeamento', '')).toHaveLength(1);
      expect(filtrar(itens, 'laudo', '')).toHaveLength(1);
    });
  });
});
