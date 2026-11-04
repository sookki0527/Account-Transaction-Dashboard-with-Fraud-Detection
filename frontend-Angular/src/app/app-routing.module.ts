import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {AccountListComponent} from "./account-list/account-list.component"
import {DashboardComponent} from "./dashboard/dashboard.component"
import {TransactionListComponent} from "./transaction-list/transaction-list.component";
import {AuthComponent} from "./auth/auth.component";
import {AuthGuard} from "./service/auth.guard";
const routes: Routes = [
  { path: '', redirectTo: '/auth', pathMatch: 'full' },
  { path: 'auth', component: AuthComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [AuthGuard] },
  { path: 'accounts', component: AccountListComponent, canActivate: [AuthGuard] },
  { path: 'transfer', component: TransactionListComponent, canActivate: [AuthGuard] }
  // 다른 경로들...
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
