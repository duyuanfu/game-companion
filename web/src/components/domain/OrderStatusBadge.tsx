import { Badge } from "@/components/ui/badge";
import { getOrderStatus } from "@/lib/orderStatus";
import { cn } from "@/lib/utils";

export function OrderStatusBadge({ status, className }: { status?: number | null; className?: string }) {
  const meta = getOrderStatus(status);
  return <Badge className={cn(meta.className, className)}>{meta.label}</Badge>;
}
