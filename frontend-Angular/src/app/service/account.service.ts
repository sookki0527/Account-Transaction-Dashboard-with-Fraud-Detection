import { Injectable } from '@angular/core';
import {BehaviorSubject, Observable} from "rxjs";
import {Account} from "../model/account.model";
import {HttpClient} from "@angular/common/http";
@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private totalBalanceSubject = new BehaviorSubject<number>(0);
  totalBalance$ = this.totalBalanceSubject.asObservable();
  constructor(private http: HttpClient) { }
  private url = "http://localhost:8081/accounts";
  getAccounts(userId: String): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.url}/${userId}`)
  }

  //reduce()를 사용해서 전체 잔고(total)를 계산
  // 예를 들어 accounts = [{balance: 100}, {balance: 300}]이면 → total = 400
  updateTotalBalance(accounts: Account[]){
      const total = accounts.reduce((sum, acc) => sum + acc.balance, 0);
      this.totalBalanceSubject.next(total);
  }


}
