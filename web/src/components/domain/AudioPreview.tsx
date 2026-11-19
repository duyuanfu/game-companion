import { Volume2, VolumeX } from "lucide-react";
import { resolveFileUrl } from "@/api/file";
import { cn } from "@/lib/utils";

export function AudioPreview({
  url,
  duration,
  className
}: {
  url?: string;
  duration?: number;
  className?: string;
}) {
  const src = resolveFileUrl(url);
  if (!src) {
    return (
      <span className={cn("inline-flex items-center gap-1 text-xs text-muted-foreground", className)}>
        <VolumeX className="h-3.5 w-3.5" />
        暂未录制原声
      </span>
    );
  }

  return (
    <div className={cn("flex items-center gap-2", className)}>
      <Volume2 className="h-4 w-4 shrink-0 text-primary" />
      <audio controls preload="none" src={src} className="h-9 w-full max-w-[260px]" />
      {typeof duration === "number" && duration > 0 ? (
        <span className="shrink-0 text-xs text-muted-foreground">{duration}s</span>
      ) : null}
    </div>
  );
}
