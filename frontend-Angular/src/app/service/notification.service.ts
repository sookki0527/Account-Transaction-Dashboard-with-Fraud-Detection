import { Injectable } from '@angular/core';
import { Client, IMessage, Stomp } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {BehaviorSubject} from "rxjs";
import {Notification} from "../model/notification.model";

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private stompClient: Client;

  private notifications$ = new BehaviorSubject<Notification | null>(null);

  constructor() {
    this.stompClient = new Client({
      brokerURL: undefined, // use SockJS
      webSocketFactory: () => new SockJS('http://localhost:8083/ws'), // Spring에서 `/ws`로 매핑된 엔드포인트
      reconnectDelay: 5000
    });

   this.stompClient.onConnect = frame => {
  console.log('Connected!!', frame);

  this.stompClient.subscribe(
    '/topic/notifications',
    (message: IMessage) => {
      console.log('📩 수신 원문:', message.body);

      try {
        const notif: Notification = JSON.parse(message.body);

        console.log('🔔 Notification:', notif);
        this.notifications$.next(notif);
      } catch (error) {
        console.error('알림 처리 실패:', error);
      }
    }
  );

  console.log('구독 요청 보냄: /topic/notifications');
};

    this.stompClient.activate();
  }

  getNotificationStream() {
    return this.notifications$.asObservable();
  }


  sendTransferMessage(event: { amount: number }) {
    this.stompClient.publish({
      destination: '/app/transfer',
      body: JSON.stringify(event)
    });
  }
}
