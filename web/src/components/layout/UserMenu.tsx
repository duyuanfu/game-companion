import { useNavigate, Link } from "react-router-dom";
import { LogOut, ShieldCheck, UserRound, Wallet, Wrench, Briefcase, ClipboardList } from "lucide-react";
import { toast } from "sonner";
import { useSession } from "@/session/SessionProvider";
import { PERSONAS } from "@/session/store";
import { personaHome } from "@/session/routeUtils";
import { ApiError } from "@/api/client";
import { errorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { UserAvatar } from "@/components/domain/UserAvatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { ROLE_LABEL } from "@/lib/orderStatus";

export function UserMenu() {
  const { user, login, logout, busyPersona } = useSession();
  const navigate = useNavigate();

  if (!user) {
    return (
      <Button asChild variant="outline" size="sm">
        <Link to="/login">登录</Link>
      </Button>
    );
  }

  const handleSwitch = async (persona: (typeof PERSONAS)[number]["id"]) => {
    try {
      const next = await login(persona);
      navigate(personaHome(persona, next.role));
      toast.success(`已切换为${PERSONAS.find((item) => item.id === persona)?.label}身份`);
    } catch (error) {
      if (error instanceof ApiError && error.code === 403) {
        toast.error("切换失败：后端未开启开发工具，请设置 platform.dev-tools-enabled=true 后重启");
      } else {
        toast.error(errorMessage(error, "身份切换失败"));
      }
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="flex items-center gap-2 rounded-full border border-transparent p-1 pr-2 transition-colors hover:border-border hover:bg-muted/60 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <UserAvatar src={user.avatarUrl} name={user.nickname} className="h-8 w-8" />
          <span className="hidden max-w-[120px] truncate text-sm font-medium sm:block">
            {user.nickname}
          </span>
          <Badge variant="secondary" className="hidden sm:inline-flex">
            {ROLE_LABEL[user.role] || "用户"}
          </Badge>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="py-2">
          <p className="truncate text-sm font-medium text-foreground">{user.nickname}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">ID: {user.id}</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to="/me">
            <UserRound className="h-4 w-4" />
            个人中心
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/me/wallet">
            <Wallet className="h-4 w-4" />
            我的钱包
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/orders">
            <ClipboardList className="h-4 w-4" />
            我的订单
          </Link>
        </DropdownMenuItem>
        {user.role === 2 || user.role === 9 ? (
          <DropdownMenuItem asChild>
            <Link to="/companion/workbench">
              <Briefcase className="h-4 w-4" />
              陪练工作台
            </Link>
          </DropdownMenuItem>
        ) : null}
        {user.role === 1 ? (
          <DropdownMenuItem asChild>
            <Link to="/companion/apply">
              <Wrench className="h-4 w-4" />
              申请成为陪练
            </Link>
          </DropdownMenuItem>
        ) : null}
        {user.role === 9 ? (
          <DropdownMenuItem asChild>
            <Link to="/admin">
              <ShieldCheck className="h-4 w-4" />
              管理后台
            </Link>
          </DropdownMenuItem>
        ) : null}

        {import.meta.env.DEV ? (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuLabel>开发身份切换</DropdownMenuLabel>
            {PERSONAS.map((persona) => (
              <DropdownMenuItem
                key={persona.id}
                disabled={busyPersona !== null}
                onClick={() => void handleSwitch(persona.id)}
              >
                <span className="flex-1">{persona.label}</span>
                <span className="text-[10px] text-muted-foreground">{persona.code}</span>
              </DropdownMenuItem>
            ))}
          </>
        ) : null}

        <DropdownMenuSeparator />
        <DropdownMenuItem
          onClick={() => {
            logout();
            navigate("/login");
          }}
          className="text-destructive focus:text-destructive"
        >
          <LogOut className="h-4 w-4" />
          退出登录
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
