import { useEffect, useState } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { Loader2, LockKeyhole, Phone, UserRound } from "lucide-react";
import { toast } from "sonner";
import { useSession } from "@/session/SessionProvider";
import { PERSONAS } from "@/session/store";
import { personaHome, safeRedirect } from "@/session/routeUtils";
import { ApiError } from "@/api/client";
import { errorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FullPageLoading } from "@/components/common/Feedback";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

type Mode = "login" | "register";

export function LoginPage() {
  const {
    session,
    user,
    loading,
    login,
    loginWithPassword,
    registerWithPassword,
    busyPersona
  } = useSession();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [mode, setMode] = useState<Mode>("login");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [nickname, setNickname] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [devDialog, setDevDialog] = useState(false);

  const redirect = params.get("redirect");
  const expired = params.get("expired");

  useEffect(() => {
    if (expired) {
      toast.error("登录已失效，请重新登录");
    }
  }, [expired]);

  if (loading) {
    return <FullPageLoading label="正在恢复登录状态..." />;
  }

  if (session && user) {
    return (
      <Navigate
        to={user.role === 9 ? "/admin" : safeRedirect(redirect, personaHome(undefined, user.role))}
        replace
      />
    );
  }

  const validate = (): string | null => {
    if (!/^1\d{10}$/.test(phone.trim())) return "请输入 11 位手机号";
    if (password.length < 8) return "密码至少 8 位";
    if (mode === "register" && password !== confirmPassword) return "两次输入的密码不一致";
    if (mode === "register" && nickname.trim().length > 20) return "昵称不能超过 20 个字符";
    return null;
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const message = validate();
    if (message) {
      setError(message);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const next =
        mode === "login"
          ? await loginWithPassword(phone.trim(), password)
          : await registerWithPassword({
              phone: phone.trim(),
              password,
              nickname: nickname.trim() || undefined
            });
      // 管理员登录后直接进入运营后台，不受客户端页面 redirect 参数影响
      navigate(next.role === 9 ? "/admin" : safeRedirect(redirect, personaHome(undefined, next.role)), {
        replace: true
      });
    } catch (submitError) {
      const text = errorMessage(submitError, mode === "login" ? "登录失败" : "注册失败");
      setError(text);
      toast.error(text);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDevLogin = async (persona: (typeof PERSONAS)[number]["id"]) => {
    setError(null);
    try {
      const next = await login(persona);
      navigate(next.role === 9 ? "/admin" : safeRedirect(redirect, personaHome(persona, next.role)), {
        replace: true
      });
    } catch (loginError) {
      if (loginError instanceof ApiError && loginError.code === 403) {
        setDevDialog(true);
        setError(errorMessage(loginError, "后端拒绝角色切换"));
      } else {
        const text = errorMessage(loginError, "开发登录失败");
        setError(text);
        toast.error(text);
      }
    }
  };

  return (
    <div className="min-h-dvh bg-[radial-gradient(circle_at_top_left,_rgba(15,163,127,0.12),_transparent_45%),radial-gradient(circle_at_bottom_right,_rgba(255,183,150,0.14),_transparent_40%)]">
      <div className="mx-auto flex min-h-dvh w-full max-w-5xl flex-col px-4 pb-8 pt-5 sm:pt-10">
        <header className="flex items-center gap-2.5">
          <img
            src="/logo.png"
            alt="PP电竞鼠"
            className="h-11 w-11 rounded-full object-cover ring-1 ring-border/70"
          />
          <div className="leading-tight">
            <p className="text-lg font-semibold tracking-tight">PP电竞鼠</p>
            <p className="text-[10px] tracking-wide text-muted-foreground">PP ESPORTS · WEB</p>
          </div>
        </header>

        <div className="flex flex-1 flex-col justify-center gap-6 py-6 lg:grid lg:grid-cols-[1fr_420px] lg:items-center lg:gap-16">
          <section className="hidden lg:block">
            <h1 className="text-4xl font-semibold leading-tight tracking-tight xl:text-5xl">
              找到你的
              <br />
              开黑队友
            </h1>
            <p className="mt-4 max-w-md text-base leading-relaxed text-muted-foreground">
              浏览陪练、先聊后下单、平台内完成支付。手机浏览器直接使用，无需下载。
            </p>
            <div className="mt-8 flex items-center gap-6 text-sm text-muted-foreground">
              <span>真实陪练</span>
              <span className="h-1 w-1 rounded-full bg-border" />
              <span>平台担保</span>
              <span className="h-1 w-1 rounded-full bg-border" />
              <span>超时自动退款</span>
            </div>
          </section>

          <Card className="w-full border-border/70 shadow-lg shadow-primary/5">
            <CardContent className="p-4 sm:p-6">
              <div className="mb-5 flex rounded-lg border bg-muted/40 p-1">
                {[
                  { id: "login" as const, label: "登录" },
                  { id: "register" as const, label: "注册" }
                ].map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => {
                      setMode(item.id);
                      setError(null);
                    }}
                    className={cn(
                      "flex-1 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                      mode === item.id
                        ? "bg-background text-foreground shadow-sm"
                        : "text-muted-foreground"
                    )}
                  >
                    {item.label}
                  </button>
                ))}
              </div>

              <div className="mb-5">
                <h2 className="text-lg font-semibold">
                  {mode === "login" ? "登录 PP电竞鼠" : "创建账号"}
                </h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  {mode === "login"
                    ? "手机号与密码登录，管理员也从这里进入。"
                    : "注册后即可下单、咨询，成为陪练需另行申请。"}
                </p>
              </div>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="phone">手机号</Label>
                  <div className="relative">
                    <Phone className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="phone"
                      className="pl-9"
                      inputMode="numeric"
                      maxLength={11}
                      placeholder="请输入 11 位手机号"
                      value={phone}
                      onChange={(event) => setPhone(event.target.value.replace(/\D/g, ""))}
                      autoComplete="tel"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="password">密码</Label>
                  <div className="relative">
                    <LockKeyhole className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="password"
                      className="pl-9"
                      type="password"
                      placeholder="至少 8 位"
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      autoComplete={mode === "login" ? "current-password" : "new-password"}
                    />
                  </div>
                </div>

                {mode === "register" ? (
                  <>
                    <div className="space-y-2">
                      <Label htmlFor="confirmPassword">确认密码</Label>
                      <Input
                        id="confirmPassword"
                        type="password"
                        placeholder="再次输入密码"
                        value={confirmPassword}
                        onChange={(event) => setConfirmPassword(event.target.value)}
                        autoComplete="new-password"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="nickname">昵称（选填）</Label>
                      <div className="relative">
                        <UserRound className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                        <Input
                          id="nickname"
                          className="pl-9"
                          maxLength={20}
                          placeholder="不填将自动生成"
                          value={nickname}
                          onChange={(event) => setNickname(event.target.value)}
                        />
                      </div>
                    </div>
                  </>
                ) : null}

                {error ? (
                  <p className="rounded-lg bg-destructive/5 px-3 py-2 text-xs text-destructive">{error}</p>
                ) : null}

                <Button type="submit" className="w-full" size="lg" disabled={submitting}>
                  {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                  {mode === "login" ? "登录" : "注册并登录"}
                </Button>
              </form>

              {import.meta.env.DEV ? (
                <div className="mt-5 border-t pt-4">
                  <p className="mb-2 text-[11px] text-muted-foreground">
                    开发快捷登录（仅本地生效）
                  </p>
                  <div className="grid grid-cols-3 gap-2">
                    {PERSONAS.map((persona) => (
                      <Button
                        key={persona.id}
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={busyPersona !== null || submitting}
                        onClick={() => void handleDevLogin(persona.id)}
                      >
                        {busyPersona === persona.id ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : null}
                        {persona.label}
                      </Button>
                    ))}
                  </div>
                </div>
              ) : null}

              <p className="pt-4 text-[11px] leading-relaxed text-muted-foreground">
                继续即表示同意平台服务协议与隐私政策。
              </p>
            </CardContent>
          </Card>
        </div>
      </div>

      <Dialog open={devDialog} onOpenChange={setDevDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>需要开启开发工具</DialogTitle>
            <DialogDescription>
              后端拒绝了开发态角色切换。请在服务端配置中开启开发工具后重启后端，再重新登录。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2 rounded-lg bg-muted/60 p-3 text-xs text-muted-foreground">
            <p>
              在 <code className="rounded bg-background px-1">application-dev.yml</code> 中设置
            </p>
            <pre className="overflow-x-auto rounded bg-background p-2 text-[11px] text-foreground">
              {"platform:\n  dev-tools-enabled: true\nauth:\n  mock-login-enabled: true"}
            </pre>
            <p>修改后需要重启 Spring Boot 服务。</p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDevDialog(false)}>
              我知道了
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
