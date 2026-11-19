import { useEffect, useState } from "react";
import { resolveFileUrl } from "@/api/file";
import { cn } from "@/lib/utils";

const DEFAULT_AVATAR = "/default-avatar.svg";

export function UserAvatar({
  src,
  name,
  className
}: {
  src?: string;
  name?: string;
  className?: string;
}) {
  const url = resolveFileUrl(src);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [url]);

  return (
    <img
      src={url && !failed ? url : DEFAULT_AVATAR}
      alt={name || "默认头像"}
      className={cn("h-10 w-10 shrink-0 rounded-full bg-muted object-cover", className)}
      onError={() => setFailed(true)}
    />
  );
}
