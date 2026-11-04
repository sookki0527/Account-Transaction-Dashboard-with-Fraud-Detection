import {Component, Input, Output, EventEmitter} from '@angular/core';
import {Account} from "../model/account.model";
import {Deposit} from "../model/deposit.model";
import {TransactionService} from "../service/transaction.service";

@Component({
  selector: 'app-account-summary',
  templateUrl: './account-summary.component.html',
  styleUrls: ['./account-summary.component.css']
})
export class AccountSummaryComponent {
  @Input() account !: Account;

  @Output() transactionCompleted = new EventEmitter<void>();
  @Output() requestTransferForm = new EventEmitter<number>();
  amountToAdd: number = 0;
  amountToSubtract: number = 0;
  transactionInProgress = false;
  transactionError = '';

  constructor(private transactionService: TransactionService) {
  }

  increaseBalance() {
    this.submitBalanceChange(this.amountToAdd, 'DEPOSIT');
  }

  decreaseBalance() {
    this.submitBalanceChange(this.amountToSubtract, 'WITHDRAW');
  }

  private submitBalanceChange(amount: number, type: Deposit['type']) {
    if (!Number.isFinite(amount) || amount <= 0) {
      this.transactionError = '금액은 0보다 커야 합니다.';
      return;
    }

    if (type === 'WITHDRAW' && amount > this.account.balance) {
      this.transactionError = '잔액이 부족합니다.';
      return;
    }

    const transaction: Deposit = {
      userId: this.account.userId,
      accountId: this.account.accountId,
      amount,
      date: new Date(),
      type
    };

    this.transactionError = '';
    this.transactionInProgress = true;
    const request = type === 'DEPOSIT'
      ? this.transactionService.sendDeposit(transaction)
      : this.transactionService.sendWithdraw(transaction);

    request.subscribe({
      next: () => {
        this.transactionInProgress = false;
        this.amountToAdd = 0;
        this.amountToSubtract = 0;
        this.transactionCompleted.emit();
      },
      error: () => {
        this.transactionInProgress = false;
        this.transactionError = type === 'DEPOSIT'
          ? '입금에 실패했습니다. 다시 시도해 주세요.'
          : '출금에 실패했습니다. 다시 시도해 주세요.';
      }
    });
  }

  onTransferClick() {
    this.requestTransferForm.emit(this.account.accountId);
  }

}
