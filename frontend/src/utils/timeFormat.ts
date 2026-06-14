const DIVIDER_THRESHOLD_MS = 5 * 60 * 1000;

export function toTimestamp(value: number | string): number {
  if (typeof value === "number") return value;
  return new Date(value).getTime();
}

export function formatDividerTime(value: number | string): string {
  const date = new Date(toTimestamp(value));
  const now = new Date();
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();

  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");

  if (sameDay) {
    return `${hours}:${minutes}`;
  }

  const month = date.getMonth() + 1;
  const day = date.getDate();
  return `${month}-${day} ${hours}:${minutes}`;
}

export function shouldShowTimeDivider(
  previousTimestamp: number | string | null,
  currentTimestamp: number | string,
): boolean {
  if (previousTimestamp == null) return true;
  const diff = toTimestamp(currentTimestamp) - toTimestamp(previousTimestamp);
  return diff > DIVIDER_THRESHOLD_MS;
}

export function formatHistoryDate(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const dayDiff = Math.floor((startOfToday.getTime() - startOfDate.getTime()) / 86_400_000);

  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  const time = `${hours}:${minutes}`;

  if (dayDiff === 0) return `今天 ${time}`;
  if (dayDiff === 1) return `昨天 ${time}`;
  if (dayDiff < 7) return `${dayDiff} 天前`;

  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  if (date.getFullYear() === now.getFullYear()) {
    return `${month}-${day} ${time}`;
  }
  return `${date.getFullYear()}-${month}-${day}`;
}
