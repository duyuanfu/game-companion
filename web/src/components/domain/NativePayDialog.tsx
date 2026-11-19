import { useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { QRCodeSVG } from "qrcode.react";
import { Loader2 } from "lucide-react";
import { getOrderDetail } from "@/api/order";
import { keys } from "@/lib/keys";
import { Money } from "@/components/domain/Money";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

export function NativePayDialog({
  open,
  onOpenChange,
  orderSn,
  codeUrl,
  amountFen,
  onPaid
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orderSn: string;
  codeUrl?: string;
  amountFen?: number;
  onPaid: () => void;
}) {
  const paidRef = useRef(false);

  const orderQuery = useQuery({
    queryKey: keys.order(orderSn),
    queryFn: () => getOrderDetail(orderSn),
    enabled: open && Boolean(orderSn),
    refetchInterval: 2_000,
    refetchIntervalInBackground: false
  });

  useEffect(() => {
    paidRef.current = false;
  }, [orderSn, open]);

  useEffect(() => {
    const status = orderQuery.data?.orderStatus;
    if (!open || paidRef.current) return;
    if (typeof status === "number" && status !== 0) {
      paidRef.current = true;
      onPaid();
    }
  }, [open, orderQuery.data?.orderStatus, onPaid]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>微信扫码支付</DialogTitle>
          <DialogDescription>请使用微信「扫一扫」扫描二维码完成支付。</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col items-center gap-3">
          {codeUrl ? (
            <div className="w-full max-w-[220px] rounded-xl border bg-white p-3">
              <QRCodeSVG value={codeUrl} size={220} className="h-auto w-full" />
            </div>
          ) : (
            <p className="py-10 text-sm text-destructive">二维码生成失败，请重新发起支付</p>
          )}
          <div className="text-center text-sm">
            <p className="text-muted-foreground">
              订单号：<span className="font-mono text-foreground">{orderSn}</span>
            </p>
            {typeof amountFen === "number" ? (
              <p className="mt-1">
                应付金额：<Money fen={amountFen} className="text-lg font-semibold text-primary" />
              </p>
            ) : null}
          </div>
          <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            正在等待支付结果，支付成功后会自动跳转
          </p>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            稍后支付
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
