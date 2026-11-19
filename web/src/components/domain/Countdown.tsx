import { useEffect, useRef, useState } from "react";
import { Clock } from "lucide-react";
import { formatDuration } from "@/lib/datetime";
import { cn } from "@/lib/utils";

export function Countdown({
  seconds,
  onExpire,
  className,
  prefix
}: {
  seconds?: number | null;
  onExpire?: () => void;
  className?: string;
  prefix?: string;
}) {
  const [remaining, setRemaining] = useState(seconds ?? 0);
  const initialRef = useRef(seconds ?? 0);
  const onExpireRef = useRef(onExpire);
  onExpireRef.current = onExpire;

  useEffect(() => {
    const next = seconds ?? 0;
    initialRef.current = next;
    setRemaining(next);
  }, [seconds]);

  useEffect(() => {
    if (remaining <= 0) return;
    const timer = window.setTimeout(() => {
      setRemaining((value) => Math.max(0, value - 1));
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [remaining]);

  useEffect(() => {
    if (remaining === 0 && initialRef.current > 0) {
      initialRef.current = 0;
      onExpireRef.current?.();
    }
  }, [remaining]);

  if (!seconds && seconds !== 0) return null;

  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 text-xs font-medium",
        remaining <= 60 ? "text-red-600" : "text-muted-foreground",
        className
      )}
    >
      <Clock className="h-3.5 w-3.5" />
      {prefix ? <span>{prefix}</span> : null}
      <span className="tabular-nums">{formatDuration(remaining)}</span>
    </span>
  );
}
