import { useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { CalendarDays, Phone, ShieldCheck, UserRound, Wallet, Wrench } from "lucide-react";
import { useSession } from "@/session/SessionProvider";
import { updateProfile, bindPhone } from "@/api/auth";
import { formatDate } from "@/lib/datetime";
import { showError } from "@/lib/errors";
import { ROLE_LABEL } from "@/lib/orderStatus";
import { PageHeader } from "@/components/common/Feedback";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { UserAvatar } from "@/components/domain/UserAvatar";
import { ImageUploader } from "@/components/domain/FileUploader";

export function MePage() {
  const { user, refreshUser } = useSession();
  const [editOpen, setEditOpen] = useState(false);
  const [phoneOpen, setPhoneOpen] = useState(false);
  const [nickname, setNickname] = useState(user?.nickname ?? "");
  const [avatarUrl, setAvatarUrl] = useState<string | undefined>(user?.avatarUrl);
  const [phone, setPhone] = useState("");
  const [saving, setSaving] = useState(false);

  if (!user) return null;

  const openEdit = () => {
    setNickname(user.nickname);
    setAvatarUrl(user.avatarUrl);
    setEditOpen(true);
  };

  const saveProfile = async () => {
    if (!nickname.trim()) {
      toast.error("昵称不能为空");
      return;
    }
    if (nickname.trim().length > 20) {
      toast.error("昵称不能超过 20 个字符");
      return;
    }
    setSaving(true);
    try {
      await updateProfile({ nickname: nickname.trim(), avatarUrl });
      await refreshUser();
      toast.success("资料已更新");
      setEditOpen(false);
    } catch (error) {
      showError(error);
    } finally {
      setSaving(false);
    }
  };

  const savePhone = async () => {
    const value = phone.trim();
    if (!/^1\d{10}$/.test(value)) {
      toast.error("请输入 11 位手机号");
      return;
    }
    setSaving(true);
    try {
      await bindPhone(value);
      await refreshUser();
      toast.success("手机号已绑定");
      setPhoneOpen(false);
      setPhone("");
    } catch (error) {
      showError(error);
    } finally {
      setSaving(false);
    }
  };

  const maskedPhone = user.phone ? user.phone.replace(/^(\d{3})\d{4}(\d{4})$/, "$1****$2") : "未绑定";

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="个人中心" />

      <Card className="mb-5">
        <CardContent className="flex flex-col items-center gap-4 p-5 text-center sm:flex-row sm:items-center sm:p-6 sm:text-left">
          <UserAvatar src={user.avatarUrl} name={user.nickname} className="h-16 w-16 text-xl sm:h-20 sm:w-20 sm:text-2xl" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
              <h2 className="text-xl font-semibold">{user.nickname}</h2>
              <Badge variant="secondary">{ROLE_LABEL[user.role] || "用户"}</Badge>
            </div>
            <div className="mt-2 flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs text-muted-foreground sm:justify-start">
              <span className="inline-flex items-center gap-1">
                <Phone className="h-3 w-3" />
                {maskedPhone}
              </span>
              <span className="inline-flex items-center gap-1">
                <CalendarDays className="h-3 w-3" />
                注册于 {formatDate(user.createTime)}
              </span>
            </div>
          </div>
          <div className="flex w-full flex-wrap gap-2 sm:w-auto">
            <Button variant="outline" size="sm" className="h-10 flex-1 sm:h-9 sm:flex-none" onClick={openEdit}>
              <UserRound className="h-4 w-4" />
              编辑资料
            </Button>
            <Button variant="outline" size="sm" className="h-10 flex-1 sm:h-9 sm:flex-none" onClick={() => setPhoneOpen(true)}>
              <Phone className="h-4 w-4" />
              绑定手机
            </Button>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Link to="/me/wallet">
          <Card className="h-full transition-shadow hover:shadow-md">
            <CardContent className="flex items-center gap-3 p-5">
              <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-100 text-amber-600">
                <Wallet className="h-5 w-5" />
              </span>
              <div>
                <p className="font-medium">我的钱包</p>
                <p className="text-xs text-muted-foreground">收益、流水与提现记录</p>
              </div>
            </CardContent>
          </Card>
        </Link>

        {user.role === 1 ? (
          <Link to="/companion/apply">
            <Card className="h-full transition-shadow hover:shadow-md">
              <CardContent className="flex items-center gap-3 p-5">
                <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100 text-emerald-600">
                  <Wrench className="h-5 w-5" />
                </span>
                <div>
                  <p className="font-medium">申请成为陪练</p>
                  <p className="text-xs text-muted-foreground">提交多个游戏资质与定价</p>
                </div>
              </CardContent>
            </Card>
          </Link>
        ) : (
          <Link to="/companion/workbench">
            <Card className="h-full transition-shadow hover:shadow-md">
              <CardContent className="flex items-center gap-3 p-5">
                <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-100 text-indigo-600">
                  <Wrench className="h-5 w-5" />
                </span>
                <div>
                  <p className="font-medium">陪练工作台</p>
                  <p className="text-xs text-muted-foreground">在线开关、接单与收益</p>
                </div>
              </CardContent>
            </Card>
          </Link>
        )}

        {user.role === 9 ? (
          <Link to="/admin">
            <Card className="h-full transition-shadow hover:shadow-md">
              <CardContent className="flex items-center gap-3 p-5">
                <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-red-100 text-red-600">
                  <ShieldCheck className="h-5 w-5" />
                </span>
                <div>
                  <p className="font-medium">运营后台</p>
                  <p className="text-xs text-muted-foreground">审核、提现、看板与配置</p>
                </div>
              </CardContent>
            </Card>
          </Link>
        ) : null}
      </div>

      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑资料</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>头像</Label>
              <ImageUploader value={avatarUrl} onChange={setAvatarUrl} label="上传新头像" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="nickname">昵称</Label>
              <Input
                id="nickname"
                maxLength={20}
                value={nickname}
                onChange={(event) => setNickname(event.target.value)}
                placeholder="1~20 个字符"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)}>取消</Button>
            <Button disabled={saving} onClick={() => void saveProfile()}>
              {saving ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={phoneOpen} onOpenChange={setPhoneOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>绑定手机号</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="phone">手机号</Label>
            <Input
              id="phone"
              inputMode="numeric"
              maxLength={11}
              value={phone}
              onChange={(event) => setPhone(event.target.value.replace(/\D/g, ""))}
              placeholder="请输入 11 位手机号"
            />
            <p className="text-xs text-muted-foreground">{user.phone ? "当前：" + maskedPhone : "绑定后用于接单提醒与平台联系。"}</p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPhoneOpen(false)}>取消</Button>
            <Button disabled={saving} onClick={() => void savePhone()}>
              {saving ? "绑定中..." : "确认绑定"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
