import {Component, OnInit} from '@angular/core';
import {Observable} from "rxjs";
import {AccountService} from "../service/account.service" ;
import {Account} from "../model/account.model";
import {Router} from "@angular/router"
import {NotificationService} from "../service/notification.service";
import {Notification} from "../model/notification.model";
import {AiNotificationService} from "../service/ai-notification.service";
@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit{
  userName = localStorage.getItem('userId') || '';
  isAdmin = localStorage.getItem('isAdmin') === 'true';
  total$: Observable<number> | undefined;
  accounts: Account[] = [];

  latestNotification: Notification | null = null;
  aiNotification: Notification | null = null;
  constructor(private accountService :AccountService, private router: Router, private notificationService: NotificationService,
              private aiNotificationService: AiNotificationService) {
  }

  ngOnInit() {

    this.total$ = this.accountService.totalBalance$;
    this.accountService.getAccounts(this.userName).subscribe(data => {
      this.accounts = data;
      this.accountService.updateTotalBalance(data);
    });
    this.notificationService.getNotificationStream().subscribe(notif =>{
      if(notif){
        this.latestNotification = notif;
        setTimeout(()=>{
          this.latestNotification = null;
        }, 5000);
      }
    })
    this.aiNotificationService.getNotificationStream().subscribe(notif =>{
        if(notif){
          this.aiNotification = notif;
          setTimeout(()=>{
            this.aiNotification = null;
          }, 5000);
        }

    })

  }

  viewAccounts() {
    this.router.navigate(['/accounts']);
  }


  makeTransfer() {
    // navigate to /transfer
    this.router.navigate(['/transfer']);
  }
}
