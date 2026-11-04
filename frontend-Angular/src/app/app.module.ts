import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { NavbarComponent } from './navbar/navbar.component';
import { HttpClientModule } from '@angular/common/http';
import {DashboardComponent} from "./dashboard/dashboard.component";
import { AccountSummaryComponent } from './account-summary/account-summary.component';
import { AccountListComponent } from './account-list/account-list.component';

import { TransactionListComponent } from './transaction-list/transaction-list.component';
import { TransactionFormComponent } from './transaction-form/transaction-form.component';
import {FormsModule} from "@angular/forms";
import {AuthComponent} from "./auth/auth.component";

@NgModule({
  declarations: [
    AppComponent,
    NavbarComponent,

    DashboardComponent,
     AccountSummaryComponent,
     AccountListComponent,
     TransactionListComponent,
     TransactionFormComponent,
     AuthComponent
  ],
    imports: [
        BrowserModule,
        AppRoutingModule,
        HttpClientModule,
        FormsModule
    ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
