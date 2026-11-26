import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../shared/toast.service';
import { ConfiguracoesApiService } from './configuracoes-api.service';
import { ConfiguracoesComponent } from './configuracoes.component';
import { UsuarioConfigurado } from './configuracoes.models';

function usuario(sobre: Partial<UsuarioConfigurado> = {}): UsuarioConfigurado {
  return {
    id: 'operador',
    nome: 'Operador',
    login: 'operador',
    perfil: 'OPERADOR',
    perfilRotulo: 'Operador',
    ativo: true,
    ultimoAcesso: '2026-10-21T14:00:00Z',
    cpfCnpj: '***.456.789-**',
    ...sobre,
  };
}

const ADMIN = usuario({ id: 'admin', nome: 'Administrador', login: 'admin', perfil: 'ADMIN', perfilRotulo: 'Administrador', cpfCnpj: null });

function problema(status: number, errorCode: string, detail?: string, campos?: { campo: string; mensagem: string }[]) {
  return new HttpErrorResponse({ status, error: { status, errorCode, detail, campos } });
}

describe('ConfiguracoesComponent', () => {
  let api: Record<'usuarios' | 'incluirUsuario' | 'alterarUsuario' | 'parametros' | 'conexoes', ReturnType<typeof vi.fn>>;
  let fixture: ComponentFixture<ConfiguracoesComponent>;
  let raiz: HTMLElement;
  let toast: ToastService;

  function abrir(): void {
    TestBed.configureTestingModule({
      imports: [ConfiguracoesComponent],
      providers: [
        { provide: ConfiguracoesApiService, useValue: api },
        { provide: AuthService, useValue: { usuario: signal({ login: 'admin' }).asReadonly() } },
      ],
    });
    toast = TestBed.inject(ToastService);
    fixture = TestBed.createComponent(ConfiguracoesComponent);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  }

  const texto = () => raiz.textContent!.replace(/\s+/g, ' ');
  const linha = (login: string) => raiz.querySelector<HTMLElement>(`tr[data-login="${login}"]`)!;
  const botao = (rotulo: string, dentro: ParentNode = raiz) =>
    Array.from(dentro.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes(rotulo));
  const escolher = (select: HTMLSelectElement, valor: string) => {
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const gaveta = () => document.body.querySelector('app-novo-usuario') as HTMLElement | null;
  const digitar = (campo: HTMLInputElement, valor: string) => {
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  beforeEach(() => {
    api = {
      usuarios: vi.fn(() => of({ conteudo: [ADMIN, usuario()], pagina: 0, tamanho: 2, totalElementos: 2, totalPaginas: 1 })),
      incluirUsuario: vi.fn(),
      alterarUsuario: vi.fn(),
      parametros: vi.fn(() => of({ parametros: [] })),
      conexoes: vi.fn(() => of({ conexoes: [] })),
    };
  });

  afterEach(() => {
    fixture.destroy();
    vi.restoreAllMocks();
  });

  describe('abas', () => {
    it('sete abas (Rastro desde a S7), a de usuários ativa, com faixa de memória', () => {
      abrir();

      const abas = Array.from(raiz.querySelectorAll('[role="tab"]'));
      expect(abas.map((a) => a.textContent!.trim())).toEqual([
        'Usuários e perfis', 'Dossiê', 'Conversão', 'Visualização', 'Rastro', 'Conexões', 'Auditoria',
      ]);
      expect(abas[0].getAttribute('aria-selected')).toBe('true');
      expect(texto()).toContain('Neste protótipo, as alterações ficam na memória e somem ao reiniciar o serviço.');
      (abas[1] as HTMLElement).click();
      fixture.detectChanges();
      expect(texto()).toContain('Editar não grava');
      expect(texto()).not.toContain('em construção');
    });

    it('aba Rastro (S7): os parâmetros do grupo RASTRO e a nota de CPF, nome e credenciais', () => {
      api.parametros.mockReturnValue(
        of({
          parametros: [
            {
              nome: 'rastro.posicoesSimultaneas', rotulo: 'Posições consultadas ao mesmo tempo (lista da busca)', grupo: 'RASTRO',
              tipo: 'INTEIRO', valor: '4', padrao: '4', minimo: 1, maximo: 20, opcoes: [], classeDeAplicacao: 'IMEDIATO',
              sobreposto: false, pendenteDeReinicio: false,
            },
            {
              nome: 'dossie.ttlMinutos', rotulo: 'Expiração do dossiê (min)', grupo: 'DOSSIE', tipo: 'INTEIRO', valor: '30',
              padrao: '30', minimo: 5, maximo: 240, opcoes: [], classeDeAplicacao: 'PROXIMA_GERACAO', sobreposto: false,
              pendenteDeReinicio: false,
            },
          ],
        }),
      );
      abrir();
      (raiz.querySelector('#aba-rastro') as HTMLElement).click();
      fixture.detectChanges();

      expect(texto()).toContain('Posições consultadas ao mesmo tempo (lista da busca)');
      expect(texto()).not.toContain('Expiração do dossiê');
      expect(texto()).toContain('O CPF do responsável nunca aparece');
      expect(texto()).toContain('Credenciais do banco não ficam aqui');
    });

    it('setas, Home e End navegam e movem o foco; só a aba ativa está na ordem de tabulação', () => {
      abrir();
      const lista = raiz.querySelector('[role="tablist"]')!;
      const tecla = (key: string) => {
        lista.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        fixture.detectChanges();
      };

      tecla('ArrowRight');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Dossiê');
      expect(raiz.querySelector('#aba-dossie')!.getAttribute('tabindex')).toBe('0');
      expect(raiz.querySelector('#aba-usuarios')!.getAttribute('tabindex')).toBe('-1');
      tecla('End');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Auditoria');
      tecla('ArrowRight');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Usuários e perfis');
      tecla('ArrowLeft');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Auditoria');
      tecla('Home');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Usuários e perfis');
    });
  });

  describe('lista de usuários', () => {
    it('mostra nome, login, CPF/CNPJ só como a API mandou, perfil, situação e último acesso', () => {
      abrir();

      expect(raiz.querySelectorAll('tbody tr')).toHaveLength(2);
      const op = linha('operador');
      expect(op.textContent).toContain('***.456.789-**');
      expect(op.querySelector<HTMLSelectElement>('.cfg-perfil')!.value).toBe('OPERADOR');
      expect(op.querySelector<HTMLSelectElement>('.cfg-situacao')!.value).toBe('ativo');
      expect(op.textContent).toContain('21/10/2026');
      expect(linha('admin').textContent).toContain('você');
      expect(linha('admin').textContent).toContain('—');
    });

    it('inativo aparece esmaecido', () => {
      api.usuarios.mockReturnValue(of({ conteudo: [ADMIN, usuario({ ativo: false })], pagina: 0, tamanho: 2, totalElementos: 2, totalPaginas: 1 }));
      abrir();

      expect(linha('operador').classList.contains('inativo')).toBe(true);
      expect(linha('admin').classList.contains('inativo')).toBe(false);
    });

    it('erro de carga com "Tentar novamente"', () => {
      api.usuarios.mockReturnValueOnce(throwError(() => problema(503, 'ERRO_INTERNO')));
      abrir();
      expect(raiz.querySelector('.cfg-erro')).not.toBeNull();

      botao('Tentar novamente')!.click();
      fixture.detectChanges();

      expect(raiz.querySelectorAll('tbody tr')).toHaveLength(2);
    });

    it('mudar o perfil pede confirmação, grava com PATCH só do perfil e avisa', () => {
      api.alterarUsuario.mockReturnValue(of(usuario({ perfil: 'OPERADOR_SIGILO', perfilRotulo: 'Gestor' })));
      abrir();

      escolher(linha('operador').querySelector('.cfg-perfil')!, 'OPERADOR_SIGILO');
      expect(api.alterarUsuario).not.toHaveBeenCalled();
      expect(texto()).toContain('Mudar o perfil de Operador para Gestor?');
      botao('Confirmar')!.click();
      fixture.detectChanges();

      expect(api.alterarUsuario).toHaveBeenCalledWith('operador', { perfil: 'OPERADOR_SIGILO' });
      expect(linha('operador').querySelector<HTMLSelectElement>('.cfg-perfil')!.value).toBe('OPERADOR_SIGILO');
      expect(raiz.querySelector('.cfg-confirmacao')).toBeNull();
      expect(toast.toasts().some((t) => t.texto.includes('atualizado'))).toBe(true);
    });

    it('cancelar a confirmação volta o controle ao valor anterior e não grava', () => {
      abrir();
      const select = linha('operador').querySelector<HTMLSelectElement>('.cfg-perfil')!;

      escolher(select, 'ADMIN');
      botao('Cancelar')!.click();
      fixture.detectChanges();

      expect(select.value).toBe('OPERADOR');
      expect(api.alterarUsuario).not.toHaveBeenCalled();
      expect(raiz.querySelector('.cfg-confirmacao')).toBeNull();
    });

    it('desativar grava só a situação, com a pergunta que avisa que o usuário deixa de entrar', () => {
      api.alterarUsuario.mockReturnValue(of(usuario({ ativo: false })));
      abrir();

      escolher(linha('operador').querySelector('.cfg-situacao')!, 'inativo');
      expect(texto()).toContain('Desativar Operador? Ele deixa de entrar no sistema.');
      botao('Confirmar')!.click();
      fixture.detectChanges();

      expect(api.alterarUsuario).toHaveBeenCalledWith('operador', { ativo: false });
      expect(linha('operador').classList.contains('inativo')).toBe(true);
    });

    for (const [codigo, mensagem] of [
      ['ULTIMO_ADMINISTRADOR', 'Não é possível desativar nem rebaixar o último Administrador ativo.'],
      ['PROPRIO_USUARIO', 'Não é possível desativar o próprio usuário.'],
    ]) {
      it(`${codigo}: mostra a mensagem do servidor na linha e volta ao valor anterior`, () => {
        api.alterarUsuario.mockReturnValue(throwError(() => problema(409, codigo, mensagem)));
        abrir();
        const select = linha('admin').querySelector<HTMLSelectElement>('.cfg-situacao')!;

        escolher(select, 'inativo');
        botao('Confirmar')!.click();
        fixture.detectChanges();

        expect(raiz.querySelector('.cfg-erro-linha')!.textContent).toContain(mensagem);
        expect(select.value).toBe('ativo');
        expect(raiz.querySelector('.cfg-confirmacao')).toBeNull();
      });
    }

    it('a confirmação trava os controles enquanto grava e não duplica o PATCH', () => {
      const resposta = new Subject<UsuarioConfigurado>();
      api.alterarUsuario.mockReturnValue(resposta);
      abrir();

      escolher(linha('operador').querySelector('.cfg-perfil')!, 'ADMIN');
      botao('Confirmar')!.click();
      fixture.detectChanges();
      botao('Gravando')!.click();

      expect(api.alterarUsuario).toHaveBeenCalledTimes(1);
      expect(linha('operador').querySelector<HTMLSelectElement>('.cfg-perfil')!.disabled).toBe(true);
    });

    it('destruir a tela com a gravação em andamento não deixa inscrição pendurada', () => {
      const resposta = new Subject<UsuarioConfigurado>();
      api.alterarUsuario.mockReturnValue(resposta);
      abrir();
      escolher(linha('operador').querySelector('.cfg-perfil')!, 'ADMIN');
      botao('Confirmar')!.click();

      fixture.destroy();

      expect(resposta.observed).toBe(false);
    });
  });

  describe('redefinição de senha', () => {
    const SENHA = 'senha-de-teste-123';

    it('o botão abre o campo de senha; sem exibir senha salva; envia só a senha e limpa depois', () => {
      api.alterarUsuario.mockReturnValue(of(usuario()));
      abrir();
      botao('Redefinir senha', linha('operador'))!.click();
      fixture.detectChanges();

      const campo = raiz.querySelector<HTMLInputElement>('.cfg-senha-nova')!;
      expect(campo.type).toBe('password');
      expect(campo.value).toBe('');
      digitar(campo, SENHA);
      botao('Redefinir', raiz.querySelector('.cfg-redefinicao')!)!.click();
      fixture.detectChanges();

      expect(api.alterarUsuario).toHaveBeenCalledWith('operador', { senha: SENHA });
      expect(raiz.querySelector('.cfg-redefinicao')).toBeNull();
      expect(texto()).not.toContain(SENHA);
      expect(toast.toasts().some((t) => t.texto.includes('redefinida'))).toBe(true);
    });

    it('senha curta não chega ao servidor; recusa do servidor aparece no campo e limpa o valor', () => {
      api.alterarUsuario.mockReturnValue(throwError(() => problema(400, 'PARAMETRO_INVALIDO', undefined, [{ campo: 'senha', mensagem: 'A senha não pode conter o login.' }])));
      abrir();
      botao('Redefinir senha', linha('operador'))!.click();
      fixture.detectChanges();
      digitar(raiz.querySelector<HTMLInputElement>('.cfg-senha-nova')!, 'curta');
      botao('Redefinir', raiz.querySelector('.cfg-redefinicao')!)!.click();
      fixture.detectChanges();
      expect(api.alterarUsuario).not.toHaveBeenCalled();
      expect(raiz.querySelector('.cfg-redefinicao .cfg-campo-erro')!.textContent).toContain('pelo menos 12 caracteres');

      digitar(raiz.querySelector<HTMLInputElement>('.cfg-senha-nova')!, 'operador-senha-1234');
      botao('Redefinir', raiz.querySelector('.cfg-redefinicao')!)!.click();
      fixture.detectChanges();
      expect(raiz.querySelector('.cfg-redefinicao .cfg-campo-erro')!.textContent).toContain('A senha não pode conter o login.');
      expect(raiz.querySelector('.cfg-redefinicao')).not.toBeNull();
    });

    it('cancelar fecha o campo sem chamar a API', () => {
      abrir();
      botao('Redefinir senha', linha('operador'))!.click();
      fixture.detectChanges();
      botao('Cancelar', raiz.querySelector('.cfg-redefinicao')!)!.click();
      fixture.detectChanges();
      expect(raiz.querySelector('.cfg-redefinicao')).toBeNull();
      expect(api.alterarUsuario).not.toHaveBeenCalled();
    });
  });

  describe('inclusão por gaveta', () => {
    const SENHA = 'senha-de-teste-123';
    const preencher = (nome: string, login: string, perfil: string, cpf: string, senha = SENHA) => {
      const g = gaveta()!;
      digitar(g.querySelector<HTMLInputElement>('#nu-nome')!, nome);
      digitar(g.querySelector<HTMLInputElement>('#nu-login')!, login);
      escolher(g.querySelector<HTMLSelectElement>('#nu-perfil')!, perfil);
      digitar(g.querySelector<HTMLInputElement>('#nu-cpf')!, cpf);
      digitar(g.querySelector<HTMLInputElement>('#nu-senha')!, senha);
    };

    it('abre a gaveta e fecha com Esc e clique fora, sem chamar a API', () => {
      abrir();

      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();
      expect(gaveta()).not.toBeNull();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();
      expect(gaveta()).toBeNull();

      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();
      gaveta()!.querySelector<HTMLElement>('.scrim')!.click();
      fixture.detectChanges();
      expect(gaveta()).toBeNull();
      expect(api.incluirUsuario).not.toHaveBeenCalled();
    });

    it('inclusão válida: envia o CPF só em dígitos, fecha, ordena a lista e avisa', () => {
      api.incluirUsuario.mockReturnValue(of(usuario({ id: 'ana.teste', nome: 'Ana de Teste', login: 'ana.teste', cpfCnpj: '***.444.777-**' })));
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();

      preencher('Ana de Teste', 'Ana.Teste', 'OPERADOR', '111.444.777-35');
      gaveta()!.querySelector<HTMLInputElement>('#nu-cpf')!.value;
      botao('Incluir usuário', gaveta()!)!.click();
      fixture.detectChanges();

      expect(api.incluirUsuario).toHaveBeenCalledWith({ nome: 'Ana de Teste', login: 'ana.teste', cpfCnpj: '11144477735', perfil: 'OPERADOR', senha: SENHA });
      expect(gaveta()).toBeNull();
      expect(Array.from(raiz.querySelectorAll('tbody tr td:first-child b')).map((b) => b.textContent)).toEqual([
        'Administrador', 'Ana de Teste', 'Operador',
      ]);
      expect(raiz.textContent).not.toContain('11144477735');
      expect(toast.toasts().some((t) => t.texto.includes('incluído'))).toBe(true);
    });

    it('máscara de CPF/CNPJ na digitação', () => {
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();

      digitar(gaveta()!.querySelector<HTMLInputElement>('#nu-cpf')!, '11144477735');

      expect(gaveta()!.querySelector<HTMLInputElement>('#nu-cpf')!.value).toBe('111.444.777-35');
    });

    it('validações: nome, login (formato e único) e CPF/CNPJ obrigatório para Operador e com dígitos verificadores', () => {
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();

      botao('Incluir usuário', gaveta()!)!.click();
      fixture.detectChanges();
      let erros = Array.from(gaveta()!.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim());
      expect(erros).toEqual(['Informe o nome.', 'Informe o login.', 'O CPF/CNPJ é obrigatório para o Operador.', 'Informe a senha inicial.']);

      preencher('Fulano', 'a b', 'OPERADOR', '111.444.777-36');
      erros = Array.from(gaveta()!.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim());
      expect(erros).toEqual(['Use de 3 a 60 caracteres: letras, números, ponto, hífen ou sublinhado.', 'CPF/CNPJ inválido.']);

      preencher('Fulano', 'ADMIN', 'ADMIN', '');
      erros = Array.from(gaveta()!.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim());
      expect(erros).toEqual(['Já existe um usuário com este login.']);
      expect(api.incluirUsuario).not.toHaveBeenCalled();
    });

    it('senha inicial: mínimo de 12 caracteres, sem o login, e some dos campos depois do envio', () => {
      api.incluirUsuario.mockReturnValue(of(usuario({ id: 'ana.teste', nome: 'Ana de Teste', login: 'ana.teste' })));
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();

      preencher('Ana de Teste', 'ana.teste', 'ADMIN', '', 'curta');
      botao('Incluir usuário', gaveta()!)!.click();
      fixture.detectChanges();
      let erros = Array.from(gaveta()!.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim());
      expect(erros).toEqual(['A senha precisa ter pelo menos 12 caracteres.']);

      digitar(gaveta()!.querySelector<HTMLInputElement>('#nu-senha')!, 'xx-ana.teste-xx');
      erros = Array.from(gaveta()!.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim());
      expect(erros).toEqual(['A senha não pode conter o login.']);
      expect(api.incluirUsuario).not.toHaveBeenCalled();
      expect(gaveta()!.querySelector('#nu-senha')!.getAttribute('type')).toBe('password');
      expect(texto()).not.toContain('xx-ana.teste-xx');
    });

    it('CPF/CNPJ é opcional fora do Operador', () => {
      api.incluirUsuario.mockReturnValue(of(usuario({ id: 'gestor.novo', login: 'gestor.novo' })));
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();

      preencher('Gestor Novo', 'gestor.novo', 'OPERADOR_SIGILO', '');
      botao('Incluir usuário', gaveta()!)!.click();

      expect(api.incluirUsuario).toHaveBeenCalledWith(expect.objectContaining({ perfil: 'OPERADOR_SIGILO', cpfCnpj: '' }));
    });

    it('recusa do servidor: 409 de login repetido na própria gaveta; 400 por campo marca o campo', () => {
      api.incluirUsuario
        .mockReturnValueOnce(throwError(() => problema(409, 'LOGIN_JA_EXISTE', 'Já existe um usuário com este login.')))
        .mockReturnValueOnce(throwError(() => problema(400, 'PARAMETRO_INVALIDO', undefined, [{ campo: 'cpfCnpj', mensagem: 'CPF/CNPJ inválido.' }])));
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();
      preencher('Ana de Teste', 'ana.teste', 'ADMIN', '');

      botao('Incluir usuário', gaveta()!)!.click();
      fixture.detectChanges();
      expect(gaveta()!.querySelector('.cfg-erro-geral')!.textContent).toContain('Já existe um usuário com este login.');

      // a senha digitada é apagada depois de cada envio recusado: é preciso digitá-la de novo
      expect(gaveta()!.querySelector<HTMLInputElement>('#nu-senha')!.value).toBe('');
      digitar(gaveta()!.querySelector<HTMLInputElement>('#nu-senha')!, SENHA);
      botao('Incluir usuário', gaveta()!)!.click();
      fixture.detectChanges();
      expect(gaveta()!.querySelector('.cfg-campo-erro')!.textContent).toContain('CPF/CNPJ inválido.');
      expect(gaveta()).not.toBeNull();
    });

    it('destruir a tela com a inclusão em andamento cancela o envio e remove a gaveta do corpo', () => {
      const envio = new Subject<UsuarioConfigurado>();
      api.incluirUsuario.mockReturnValue(envio);
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();
      preencher('Ana de Teste', 'ana.teste', 'ADMIN', '');
      botao('Incluir usuário', gaveta()!)!.click();
      expect(envio.observed).toBe(true);

      fixture.destroy();

      expect(envio.observed).toBe(false);
      expect(gaveta()).toBeNull();
    });
  });
});
