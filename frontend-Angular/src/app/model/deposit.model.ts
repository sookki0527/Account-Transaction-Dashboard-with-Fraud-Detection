export interface Deposit {
  userId: string;
  accountId: number;
  amount: number;
  date: Date;
  type: 'DEPOSIT' | 'WITHDRAW';
}
