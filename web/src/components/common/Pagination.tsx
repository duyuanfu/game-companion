import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

export function Pagination({
  current,
  pages,
  total,
  onChange,
  className
}: {
  current: number;
  pages: number;
  total?: number;
  onChange: (page: number) => void;
  className?: string;
}) {
  if (!pages || pages <= 1) return null;

  const start = Math.max(1, Math.min(current - 2, pages - 4));
  const end = Math.min(pages, start + 4);
  const numbers: number[] = [];
  for (let i = start; i <= end; i += 1) numbers.push(i);

  return (
    <div className={`flex flex-wrap items-center justify-between gap-3 pt-4 ${className || ""}`}>
      <p className="text-xs text-muted-foreground">
        第 {current} / {pages} 页{typeof total === "number" ? ` · 共 ${total} 条` : ""}
      </p>
      <div className="ml-auto flex items-center gap-1">
        <Button
          variant="outline"
          size="icon"
          className="h-9 w-9"
          aria-label="上一页"
          disabled={current <= 1}
          onClick={() => onChange(current - 1)}
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <div className="hidden items-center gap-1 sm:flex">
          {numbers.map((page) => (
            <Button
              key={page}
              variant={page === current ? "default" : "outline"}
              size="sm"
              onClick={() => onChange(page)}
            >
              {page}
            </Button>
          ))}
        </div>
        <span className="min-w-[3.5rem] text-center text-xs text-muted-foreground sm:hidden">
          {current} / {pages}
        </span>
        <Button
          variant="outline"
          size="icon"
          className="h-9 w-9"
          aria-label="下一页"
          disabled={current >= pages}
          onClick={() => onChange(current + 1)}
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
