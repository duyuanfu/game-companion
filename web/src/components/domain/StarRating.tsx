import { Star } from "lucide-react";
import { cn } from "@/lib/utils";

export function StarRating({
  value,
  onChange,
  size = "md",
  className
}: {
  value: number;
  onChange?: (value: number) => void;
  size?: "sm" | "md";
  className?: string;
}) {
  const iconClass = size === "sm" ? "h-3.5 w-3.5" : "h-5 w-5";
  return (
    <div className={cn("flex items-center gap-0.5", className)}>
      {[1, 2, 3, 4, 5].map((star) => {
        const active = star <= value;
        if (!onChange) {
          return (
            <Star
              key={star}
              className={cn(iconClass, active ? "fill-amber-400 text-amber-400" : "text-zinc-300")}
            />
          );
        }
        return (
          <button
            key={star}
            type="button"
            aria-label={`${star} 星`}
            className="rounded p-0.5 transition-transform hover:scale-110 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            onClick={() => onChange(star)}
          >
            <Star
              className={cn(iconClass, active ? "fill-amber-400 text-amber-400" : "text-zinc-300")}
            />
          </button>
        );
      })}
    </div>
  );
}
