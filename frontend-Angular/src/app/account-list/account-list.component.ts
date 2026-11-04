import {Component, OnInit} from '@angular/core';
import {Account} from "../model/account.model";
import {NotificationService} from "../service/notification.service";
import {AccountService} from "../service/account.service";
import {Notification} from "../model/notification.model";
import {Router} from "@angular/router";
@Component({
  selector: 'app-account-list',
  templateUrl: './account-list.component.html',
  styleUrls: ['./account-list.component.css']
})
export class AccountListComponent implements OnInit{


  accounts: Account[] = [];
  latestNotification: Notification | null = null;
  constructor(
    private accountService: AccountService,
    private notificationService: NotificationService,
    private router: Router
  ) {
  }
  ngOnInit(){
      const userId = this.getUserId();
      if (!userId) {
        return;
      }
      this.accountService.getAccounts(userId).subscribe(data =>{
        this.accounts = data;
      })
    this.notificationService.getNotificationStream().subscribe(notif =>{
      if(notif){
        this.latestNotification = notif;
        console.log('🔔 Notification:', notif);
        setTimeout(()=>{
          this.latestNotification = null;
        }, 5000);
      }
    })
    }


  selectedAccountId: number | null = null;

  toggleForm(accountId: number) {
    if (this.selectedAccountId === accountId) {
      this.selectedAccountId = null;  // 같은 걸 다시 누르면 닫기
    } else {
      this.selectedAccountId = accountId;
    }
  }
  refreshAccounts() {
    const userId = this.getUserId();
    if (!userId) {
      return;
    }
    this.accountService.getAccounts(userId).subscribe(accounts => {
      this.accounts = accounts;
    });
  }

  private getUserId(): string | null {
    const userId = localStorage.getItem('userId');
    if (!userId) {
      void this.router.navigate(['/auth']);
      return null;
    }
    return userId;
  }

  protected readonly String = String;
}
