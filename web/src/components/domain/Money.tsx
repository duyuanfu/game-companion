import { formatFen, formatYuan } from "@/lib/money";
import { cn } from "@/lib/utils";

export function Money({
  fen,
  yuan,
  className,
  symbol = true
}: {
  /** 分 */
  fen?: number | null;
  /** 元（仅用于 CompanionItem.price 这类本来就以元为单位的字段） */
  yuan?: number | null;
  className?: string;
  symbol?: boolean;
}) {
  const text = typeof yuan === "number" ? formatYuan(yuan, symbol) : formatFen(fen, symbol);
  return <span className={cn("tabular-nums", className)}>{text}</span>;
}
