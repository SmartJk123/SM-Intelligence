export interface NotificationFilters { timeline: string; category: string; transaction: string; important: string; bank: string; unread: boolean; }
export interface NotificationItem { id: string; title: string; message: string; target: string; read: boolean; date: string; category: string; transaction: string; important: string; bank: string; }
export function matchesNotification(item: NotificationItem, f: NotificationFilters, today: string): boolean {
  if (f.unread && item.read || f.category && item.category.toLowerCase() !== f.category.toLowerCase() || f.transaction && item.transaction !== f.transaction || f.important && item.important !== f.important || f.bank && item.bank !== f.bank) return false;
  if (!f.timeline) return true;
  if (!item.date) return false;
  const day = new Date(today + 'T00:00:00Z');
  const offset = (n: number) => new Date(day.getTime() + n * 86400000).toISOString().slice(0,10);
  if (f.timeline === 'today') return item.date === today;
  if (f.timeline === 'yesterday') return item.date === offset(-1);
  const from = f.timeline === 'week' ? offset(-((day.getUTCDay()+6)%7)) : f.timeline === 'month' ? today.slice(0,8)+'01' : offset(-29);
  return item.date >= from && item.date <= today;
}
