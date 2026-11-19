const pad = (value: number) => String(value).padStart(2, "0");

const DATE_TIME_RE = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/;

export interface ParsedDate {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

export function parseServerDate(value?: string | null): ParsedDate | null {
  if (!value) return null;
  const match = DATE_TIME_RE.exec(value);
  if (!match) return null;
  return {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
    hour: Number(match[4]),
    minute: Number(match[5]),
    second: Number(match[6] ?? 0)
  };
}

export function formatDateTime(value?: string | null): string {
  const parsed = parseServerDate(value);
  if (!parsed) return "-";
  return `${parsed.year}-${pad(parsed.month)}-${pad(parsed.day)} ${pad(parsed.hour)}:${pad(parsed.minute)}`;
}

export function formatDate(value?: string | null): string {
  const parsed = parseServerDate(value);
  if (!parsed) return "-";
  return `${parsed.year}-${pad(parsed.month)}-${pad(parsed.day)}`;
}

export function formatTime(value?: string | null): string {
  const parsed = parseServerDate(value);
  if (!parsed) return "";
  return `${pad(parsed.hour)}:${pad(parsed.minute)}`;
}

/** 仅用于生成 Date 对象做本地倒计时，避免浏览器按 UTC 解析造成时区偏移 */
export function toLocalDate(value?: string | null): Date | null {
  const parsed = parseServerDate(value);
  if (!parsed) return null;
  return new Date(parsed.year, parsed.month - 1, parsed.day, parsed.hour, parsed.minute, parsed.second);
}

/** 消息列表使用：今天显示时间，昨天显示“昨天”，更早显示日期 */
export function formatRelativeDay(value?: string | null): string {
  const parsed = parseServerDate(value);
  if (!parsed) return "";
  const target = new Date(parsed.year, parsed.month - 1, parsed.day);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diffDays = Math.round((today.getTime() - target.getTime()) / 86_400_000);
  if (diffDays === 0) return `${pad(parsed.hour)}:${pad(parsed.minute)}`;
  if (diffDays === 1) return "昨天";
  if (diffDays < 7) return `${diffDays} 天前`;
  return `${parsed.month}/${parsed.day}`;
}

export function formatDuration(seconds?: number | null): string {
  if (typeof seconds !== "number" || seconds <= 0) return "00:00";
  const total = Math.floor(seconds);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const rest = total % 60;
  if (hours > 0) return `${pad(hours)}:${pad(minutes)}:${pad(rest)}`;
  return `${pad(minutes)}:${pad(rest)}`;
}

export function formatWaitMinutes(minutes?: number | null): string {
  if (typeof minutes !== "number" || minutes < 0) return "-";
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`;
}
