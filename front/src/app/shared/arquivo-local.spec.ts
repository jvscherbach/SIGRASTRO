import { baixarBlob, baixarDireto, nomeDoContentDisposition } from './arquivo-local';

describe('nomeDoContentDisposition', () => {
  it('lê o filename* codificado (RFC 5987)', () => {
    expect(nomeDoContentDisposition("attachment; filename*=UTF-8''dossie-processo-2825-2025.pdf")).toBe('dossie-processo-2825-2025.pdf');
    expect(nomeDoContentDisposition("inline; filename*=UTF-8''dossi%C3%AA.pdf")).toBe('dossiê.pdf');
  });

  it('lê o filename entre aspas e sem aspas', () => {
    expect(nomeDoContentDisposition('attachment; filename="dossie-requerimento-3331-2025.pdf"')).toBe('dossie-requerimento-3331-2025.pdf');
    expect(nomeDoContentDisposition('attachment; filename=dossie.pdf')).toBe('dossie.pdf');
  });

  it('prefere o filename* quando há os dois', () => {
    expect(nomeDoContentDisposition(`attachment; filename="velho.pdf"; filename*=UTF-8''novo.pdf`)).toBe('novo.pdf');
  });

  it('sem cabeçalho ou sem nome devolve nulo', () => {
    expect(nomeDoContentDisposition(null)).toBeNull();
    expect(nomeDoContentDisposition('')).toBeNull();
    expect(nomeDoContentDisposition('attachment')).toBeNull();
  });
});

describe('baixarBlob', () => {
  afterEach(() => vi.restoreAllMocks());

  it('baixa por URL de objeto com o nome dado, sem deixar âncora no documento, e revoga a URL', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:http://localhost/x');
    URL.revokeObjectURL = vi.fn();
    const cliques: { download: string; href: string; noDocumento: boolean }[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      cliques.push({ download: this.download, href: this.href, noDocumento: document.body.contains(this) });
    });

    baixarBlob(new Blob(['x']), 'dossie.pdf');

    expect(cliques).toEqual([{ download: 'dossie.pdf', href: 'blob:http://localhost/x', noDocumento: true }]);
    expect(document.querySelector('a[download]')).toBeNull();
    await new Promise((resolver) => setTimeout(resolver, 5));
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/x');
  });
});

describe('baixarDireto', () => {
  afterEach(() => vi.restoreAllMocks());

  it('deixa o navegador baixar pela URL do ticket, sem Blob e sem deixar âncora no documento', () => {
    URL.createObjectURL = vi.fn();
    const cliques: { href: string; rel: string }[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      cliques.push({ href: this.getAttribute('href')!, rel: this.rel });
    });

    baixarDireto('/api/dossies/abc/arquivo?disposicao=attachment&ticket=t1');

    expect(cliques).toEqual([{ href: '/api/dossies/abc/arquivo?disposicao=attachment&ticket=t1', rel: 'noopener noreferrer' }]);
    expect(URL.createObjectURL).not.toHaveBeenCalled();
    expect(document.querySelector('a[download]')).toBeNull();
  });
});
