import { Routes } from '@angular/router';

import { autenticadoGuard, permissaoGuard, visitanteGuard } from './core/guards';
import { pendenciasGuard } from './features/configuracoes/pendencias.guard';
import { ConsultaComponent } from './features/consulta/consulta.component';
import { DetalheComponent } from './features/detalhe/detalhe.component';
import { DossiesGeradosComponent } from './features/dossies/dossies-gerados.component';
import { LoginComponent } from './features/login/login.component';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [visitanteGuard], title: 'Entrar · Rastro' },
  {
    path: '',
    component: ShellComponent,
    canActivate: [autenticadoGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'consulta' },
      {
        path: 'consulta',
        component: ConsultaComponent,
        title: 'Consulta · Rastro',
        data: { crumb: ['Consulta'] },
      },
      {
        path: 'consulta/requerimentos/:id',
        component: DetalheComponent,
        title: 'Requerimento · Rastro',
        data: { crumb: ['Consulta'] },
      },
      {
        path: 'dossies',
        component: DossiesGeradosComponent,
        title: 'Dossiês gerados · Rastro',
        data: { crumb: ['Dossiês gerados'] },
      },
      {
        path: 'auditoria/documentos',
        loadComponent: () => import('./features/auditoria/auditoria.component').then((m) => m.AuditoriaComponent),
        title: 'Auditoria de documentos · Rastro',
        data: { crumb: ['Auditoria', 'Documentos'], grupo: 'documentos' },
      },
      {
        path: 'auditoria/acessos',
        loadComponent: () => import('./features/auditoria/auditoria.component').then((m) => m.AuditoriaComponent),
        canActivate: [permissaoGuard],
        title: 'Auditoria de acessos · Rastro',
        data: { permissao: 'verAuditoriaAcessos', crumb: ['Auditoria', 'Acessos'], grupo: 'acessos' },
      },
      {
        path: 'configuracoes',
        loadComponent: () =>
          import('./features/configuracoes/configuracoes.component').then((m) => m.ConfiguracoesComponent),
        canActivate: [permissaoGuard],
        canDeactivate: [pendenciasGuard],
        title: 'Configurações · Rastro',
        data: { permissao: 'acessarConfiguracoes', crumb: ['Configurações'] },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
