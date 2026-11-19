import { Link } from "react-router-dom";
import { ShieldAlert, Compass } from "lucide-react";
import { Button } from "@/components/ui/button";

export function ForbiddenPage() {
  return (
    <div className="flex min-h-[70vh] flex-col items-center justify-center gap-4 px-4 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-destructive/10 text-destructive">
        <ShieldAlert className="h-7 w-7" />
      </div>
      <div>
        <h1 className="text-2xl font-semibold">没有访问权限</h1>
        <p className="mt-1 text-sm text-muted-foreground">当前身份无法访问该页面，请切换到对应角色后重试。</p>
      </div>
      <div className="flex gap-2">
        <Button asChild>
          <Link to="/companions">返回找陪练</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/login">切换身份</Link>
        </Button>
      </div>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="flex min-h-[70vh] flex-col items-center justify-center gap-4 px-4 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-muted text-muted-foreground">
        <Compass className="h-7 w-7" />
      </div>
      <div>
        <h1 className="text-2xl font-semibold">页面不存在</h1>
        <p className="mt-1 text-sm text-muted-foreground">链接可能已失效，或你输入了一个不存在的地址。</p>
      </div>
      <Button asChild>
        <Link to="/companions">返回找陪练</Link>
      </Button>
    </div>
  );
}
