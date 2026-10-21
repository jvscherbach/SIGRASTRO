import { Routes } from '@angular/router';

import { autenticadoGuard, perfilGuard, visitanteGuard } from './core/guards';
import { ConsultaComponent } from './features/consulta/consulta.component';
import { DetalheComponent } from './features/detalhe/detalhe.component';
import { EmConstrucaoComponent } from './features/em-construcao/em-construcao.component';
import { LoginComponent } from './features/login/login.component';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [visitanteGuard], title: 'Entrar · SIGA Vistas' },
  {
    path: '',
    component: ShellComponent,
    canActivate: [autenticadoGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'consulta' },
      {
        path: 'consulta',
        component: ConsultaComponent,
        title: 'Consulta · SIGA Vistas',
        data: { crumb: ['Consulta'] },
      },
      {
        path: 'consulta/requerimentos/:id',
        component: DetalheComponent,
        title: 'Requerimento · SIGA Vistas',
        data: { crumb: ['Consulta'] },
      },
      {
        path: 'dossies',
        component: EmConstrucaoComponent,
        title: 'Dossiês gerados · SIGA Vistas',
        data: {
          crumb: ['Dossiês gerados'],
          titulo: 'Dossiês gerados',
          descricao: 'Acompanhe os dossiês em geração, pré-visualize e baixe o PDF antes que ele expire.',
          sprint: 'Sprint 4',
        },
      },
      {
        path: 'auditoria/documentos',
        component: EmConstrucaoComponent,
        title: 'Auditoria de documentos · SIGA Vistas',
        data: {
          crumb: ['Auditoria', 'Documentos'],
          titulo: 'Auditoria de documentos',
          descricao: 'Consultas, visualizações, dossiês, falhas e downloads de documentos.',
          sprint: 'Sprint 5',
        },
      },
      {
        path: 'auditoria/acessos',
        component: EmConstrucaoComponent,
        canActivate: [perfilGuard],
        title: 'Auditoria de acessos · SIGA Vistas',
        data: {
          perfis: ['ADMIN'],
          crumb: ['Auditoria', 'Acessos'],
          titulo: 'Auditoria de acessos',
          descricao: 'Entradas, trocas de perfil e alterações de usuários e de configuração.',
          sprint: 'Sprint 5',
        },
      },
      {
        path: 'configuracoes',
        component: EmConstrucaoComponent,
        canActivate: [perfilGuard],
        title: 'Configurações · SIGA Vistas',
        data: {
          perfis: ['ADMIN'],
          crumb: ['Configurações'],
          titulo: 'Configurações',
          descricao: 'Usuários e perfis, parâmetros do dossiê, conversão, conexões e retenção da auditoria.',
          sprint: 'Sprint 5',
        },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
