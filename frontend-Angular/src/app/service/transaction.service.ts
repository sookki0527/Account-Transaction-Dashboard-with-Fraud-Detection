import { Injectable } from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";

import {Transaction} from "../model/transaction.model"
import {Deposit} from "../model/deposit.model"
@Injectable({
  providedIn: 'root'
})
export class TransactionService  {

  constructor(private http: HttpClient) { }
  private url = "http://localhost:8082/transactions";

  getTransations(userId: String): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(`${this.url}/${userId}`)
  }

  sendTransaction(tx: Transaction): Observable<any> {
    return this.http.post(`${this.url}/transfer`, tx);
  }

  sendDeposit(tx: Deposit): Observable<any> {
    console.log("Sending deposit request: ", tx);
    return this.http.post(`${this.url}/transfer/deposit`, tx);
  }

  sendWithdraw(tx: Deposit): Observable<any> {
    return this.http.post(`${this.url}/transfer/withdraw`, tx);
  }

}
