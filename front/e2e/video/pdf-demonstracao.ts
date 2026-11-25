/**
 * V1, bloco 2: gera por script, sem biblioteca, um PDF pequeno e fictício (4 páginas, texto de exemplo e uma tabela
 * que imita uma planilha convertida) para o gerador e o visualizador do dossiê da demonstração. Nenhum documento real.
 */
const ESCAPAR = (texto: string): string => texto.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');

interface Linha {
  x: number;
  y: number;
  tamanho: number;
  texto: string;
  negrito?: boolean;
}

function texto(linhas: Linha[]): string {
  return linhas
    .map((l) => `BT /${l.negrito ? 'F2' : 'F1'} ${l.tamanho} Tf ${l.x} ${l.y} Td (${ESCAPAR(l.texto)}) Tj ET`)
    .join('\n');
}

const TABELA: string[][] = [
  ['Mês', 'Captação (m3)', 'Limite (m3)', 'Situação'],
  ['Janeiro', '1.200', '1.500', 'Dentro do limite'],
  ['Fevereiro', '1.350', '1.500', 'Dentro do limite'],
  ['Março', '1.480', '1.500', 'Dentro do limite'],
  ['Abril', '1.100', '1.500', 'Dentro do limite'],
  ['Maio', '950', '1.500', 'Dentro do limite'],
];

function paginaDaTabela(): string {
  const esquerda = 60;
  const larguras = [120, 130, 110, 140];
  const topo = 700;
  const altura = 28;
  const partes: string[] = [];
  const linhas: Linha[] = [
    { x: 60, y: 760, tamanho: 16, texto: 'Planilha de acompanhamento (exemplo)', negrito: true },
    { x: 60, y: 738, tamanho: 10, texto: 'Planilha convertida para PDF. Dados fictícios, sem relação com processo real.' },
  ];
  const larguraTotal = larguras.reduce((a, b) => a + b, 0);
  for (let i = 0; i <= TABELA.length; i++) {
    const y = topo - i * altura + 8;
    partes.push(`${esquerda} ${y} m ${esquerda + larguraTotal} ${y} l S`);
  }
  let x = esquerda;
  for (let c = 0; c <= larguras.length; c++) {
    partes.push(`${x} ${topo + 8} m ${x} ${topo + 8 - TABELA.length * altura} l S`);
    x += larguras[c] ?? 0;
  }
  TABELA.forEach((linha, r) => {
    let cx = esquerda + 6;
    linha.forEach((celula, c) => {
      linhas.push({ x: cx, y: topo - r * altura - 6, tamanho: 11, texto: celula, negrito: r === 0 });
      cx += larguras[c] ?? 0;
    });
  });
  return `0.5 w\n${partes.join('\n')}\n${texto(linhas)}`;
}

function paginaDeTexto(titulo: string, paragrafos: string[]): string {
  const linhas: Linha[] = [{ x: 60, y: 760, tamanho: 16, texto: titulo, negrito: true }];
  paragrafos.forEach((p, i) => linhas.push({ x: 60, y: 720 - i * 22, tamanho: 11, texto: p }));
  linhas.push({ x: 60, y: 40, tamanho: 9, texto: 'Documento de demonstração. Dados fictícios, nenhum processo real.' });
  return texto(linhas);
}

/** Devolve os bytes de um PDF válido (xref com deslocamentos calculados), com 4 páginas A4. */
export function gerarPdfDemonstracao(): Buffer {
  const conteudos = [
    paginaDeTexto('Requerimento (exemplo)', [
      'Interessado: Associação Residencial Vale Verde (nome fictício).',
      'Objeto: captação de água para abastecimento de uma área de exemplo.',
      'Local: Fazenda Modelo, município fictício.',
      'Este texto existe apenas para demonstrar o dossiê do SIGRASTRO.',
    ]),
    paginaDeTexto('Parecer técnico (exemplo)', [
      'O analista de exemplo verificou os documentos apresentados.',
      'A vazão solicitada é compatível com a disponibilidade informada.',
      'Conclusão fictícia: favorável, com condicionantes de exemplo.',
      'Texto sem valor jurídico, criado para a demonstração.',
    ]),
    paginaDaTabela(),
    paginaDeTexto('Ofício (exemplo)', [
      'Prezados, encaminhamos o resultado da análise de exemplo.',
      'Permanecemos à disposição para esclarecimentos fictícios.',
      'Atenciosamente, Equipe de Exemplo.',
    ]),
  ];

  const objetos: string[] = [];
  const n = conteudos.length;
  // 1 catálogo, 2 páginas, 3 e 4 fontes, depois (página, conteúdo) por página
  objetos[1] = '<< /Type /Catalog /Pages 2 0 R >>';
  const ids = conteudos.map((_, i) => 5 + i * 2);
  objetos[2] = `<< /Type /Pages /Kids [${ids.map((id) => `${id} 0 R`).join(' ')}] /Count ${n} >>`;
  objetos[3] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>';
  objetos[4] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>';
  conteudos.forEach((c, i) => {
    const idPagina = 5 + i * 2;
    const idConteudo = idPagina + 1;
    objetos[idPagina] =
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${idConteudo} 0 R >>`;
    const bytes = Buffer.from(c, 'latin1');
    objetos[idConteudo] = `<< /Length ${bytes.length} >>\nstream\n${c}\nendstream`;
  });

  const partes: Buffer[] = [Buffer.from('%PDF-1.4\n', 'latin1')];
  const deslocamentos: number[] = [];
  let posicao = partes[0].length;
  for (let id = 1; id < objetos.length; id++) {
    const bruto = Buffer.from(`${id} 0 obj\n${objetos[id]}\nendobj\n`, 'latin1');
    deslocamentos[id] = posicao;
    partes.push(bruto);
    posicao += bruto.length;
  }
  const total = objetos.length;
  let xref = `xref\n0 ${total}\n0000000000 65535 f \n`;
  for (let id = 1; id < total; id++) xref += `${String(deslocamentos[id]).padStart(10, '0')} 00000 n \n`;
  xref += `trailer\n<< /Size ${total} /Root 1 0 R >>\nstartxref\n${posicao}\n%%EOF\n`;
  partes.push(Buffer.from(xref, 'latin1'));
  return Buffer.concat(partes);
}

export const PAGINAS_DO_PDF_DE_DEMONSTRACAO = 4;
