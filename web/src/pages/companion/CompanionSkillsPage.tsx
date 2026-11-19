import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Gamepad2, Loader2, Pencil, Plus, Power } from "lucide-react";
import { toast } from "sonner";
import {
  createCompanionSkill,
  getMyCompanionSkills,
  updateCompanionSkill,
  type CompanionSkillPayload
} from "@/api/companion";
import type { CompanionSkillVO } from "@/types/api";
import { getGameCategories } from "@/api/game";
import { keys } from "@/lib/keys";
import { isValidMoney, sanitizeMoneyInput } from "@/lib/moneyInput";
import { useSession } from "@/session/SessionProvider";
import { showError } from "@/lib/errors";
import { PageHeader, EmptyState, ErrorState, LoadingSkeleton } from "@/components/common/Feedback";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Money } from "@/components/domain/Money";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { cn } from "@/lib/utils";

export function CompanionSkillsPage() {
  const { user } = useSession();
  const queryClient = useQueryClient();

  const skillsQuery = useQuery({
    queryKey: keys.mySkills,
    queryFn: getMyCompanionSkills
  });
  const gamesQuery = useQuery({
    queryKey: keys.games,
    queryFn: getGameCategories,
    staleTime: 5 * 60_000
  });

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<CompanionSkillVO | null>(null);
  const [gameName, setGameName] = useState("");
  const [rankName, setRankName] = useState("");
  const [priceYuan, setPriceYuan] = useState("30");
  const [unit, setUnit] = useState("小时");
  const [status, setStatus] = useState(1);
  const [saving, setSaving] = useState(false);
  const [togglingId, setTogglingId] = useState<number | null>(null);

  useEffect(() => {
    if (dialogOpen && !editing && !gameName && gamesQuery.data?.length) {
      setGameName(gamesQuery.data[0].name);
    }
  }, [dialogOpen, editing, gameName, gamesQuery.data]);

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: keys.mySkills }),
      queryClient.invalidateQueries({ queryKey: keys.myStatus }),
      queryClient.invalidateQueries({ queryKey: ["companions"] }),
      user
        ? queryClient.invalidateQueries({ queryKey: keys.companionSkills(user.id) })
        : Promise.resolve()
    ]);
  };

  const openCreate = () => {
    setEditing(null);
    setGameName(gamesQuery.data?.[0]?.name || "");
    setRankName("");
    setPriceYuan("30");
    setUnit("小时");
    setStatus(1);
    setDialogOpen(true);
  };

  const openEdit = (skill: CompanionSkillVO) => {
    setEditing(skill);
    setGameName(skill.gameName || "");
    setRankName(skill.rankName || "");
    setPriceYuan(String(skill.priceYuan ?? skill.price / 100));
    setUnit(skill.unit || "小时");
    setStatus(skill.status === 0 ? 0 : 1);
    setDialogOpen(true);
  };

  const save = async () => {
    const yuan = Number(priceYuan);
    if (!gameName.trim()) {
      toast.error("请选择游戏");
      return;
    }
    if (!rankName.trim()) {
      toast.error("请填写当前段位");
      return;
    }
    if (!isValidMoney(priceYuan)) {
      toast.error("单价需为 0.01–100000 元，最多两位小数");
      return;
    }

    const payload: CompanionSkillPayload = {
      gameName: gameName.trim(),
      rankName: rankName.trim(),
      price: Math.round(yuan * 100),
      unit,
      status
    };

    setSaving(true);
    try {
      if (editing) {
        await updateCompanionSkill(editing.id, payload);
        toast.success("技能已更新");
      } else {
        await createCompanionSkill(payload);
        toast.success("技能已添加");
      }
      await invalidate();
      setDialogOpen(false);
    } catch (error) {
      showError(error, editing ? "更新技能失败" : "添加技能失败");
    } finally {
      setSaving(false);
    }
  };

  const toggleStatus = async (skill: CompanionSkillVO) => {
    setTogglingId(skill.id);
    try {
      await updateCompanionSkill(skill.id, {
        gameName: skill.gameName,
        rankName: skill.rankName || "未填写段位",
        price: skill.price,
        unit: skill.unit,
        status: skill.status === 1 ? 0 : 1,
        rankImg: skill.rankImg
      });
      toast.success(skill.status === 1 ? "技能已下架" : "技能已上架");
      await invalidate();
    } catch (error) {
      showError(error, "更新技能状态失败");
    } finally {
      setTogglingId(null);
    }
  };

  const skills = skillsQuery.data ?? [];

  return (
    <div className="mx-auto w-full max-w-3xl">
      <Link
        to="/companion/workbench"
        className="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        返回工作台
      </Link>

      <PageHeader
        title="游戏技能"
        description="价格和上下架可自助修改；新增游戏或修改段位会重新进入审核。"
        actions={
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4" />
            新增技能
          </Button>
        }
      />

      {skillsQuery.isLoading ? (
        <LoadingSkeleton rows={3} />
      ) : skillsQuery.isError ? (
        <ErrorState
          message={(skillsQuery.error as Error)?.message}
          onRetry={() => void skillsQuery.refetch()}
        />
      ) : skills.length === 0 ? (
        <EmptyState
          icon={<Gamepad2 className="h-5 w-5" />}
          title="还没有游戏技能"
          description="添加至少一个技能后，客户才能预约你。"
          action={
            <Button onClick={openCreate}>
              <Plus className="h-4 w-4" />
              新增技能
            </Button>
          }
        />
      ) : (
        <div className="space-y-3">
          {skills.map((skill) => (
            <Card key={skill.id}>
              <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex min-w-0 items-start gap-3">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Gamepad2 className="h-5 w-5" />
                  </span>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium">{skill.gameName}</p>
                      <Badge
                        variant="secondary"
                        className={cn(
                          "font-normal",
                          skill.status === 1
                            ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                            : "text-muted-foreground"
                        )}
                      >
                        {skill.status === 1 ? "上架中" : "已下架"}
                      </Badge>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {skill.rankName || "未填写段位"} ·{" "}
                      <Money fen={skill.price} className="text-xs font-medium text-foreground" /> /{" "}
                      {skill.unit || "小时"}
                    </p>
                  </div>
                </div>

                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-11 flex-1 sm:h-9 sm:flex-none"
                    onClick={() => openEdit(skill)}
                  >
                    <Pencil className="h-3.5 w-3.5" />
                    编辑
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-11 flex-1 sm:h-9 sm:flex-none"
                    disabled={togglingId === skill.id}
                    onClick={() => void toggleStatus(skill)}
                  >
                    {togglingId === skill.id ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Power className="h-3.5 w-3.5" />
                    )}
                    {skill.status === 1 ? "下架" : "上架"}
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? "编辑技能" : "新增技能"}</DialogTitle>
            <DialogDescription>修改后立即生效，已产生的历史订单不受影响。</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="skill-game">游戏名称</Label>
              <Select
                id="skill-game"
                value={gameName}
                disabled={gamesQuery.isLoading}
                onChange={(event) => setGameName(event.target.value)}
              >
                <option value="">请选择游戏</option>
                {gameName &&
                !(gamesQuery.data ?? []).some((game) => game.name === gameName) ? (
                  <option value={gameName}>{gameName}（当前）</option>
                ) : null}
                {(gamesQuery.data ?? []).map((game) => (
                  <option key={game.id} value={game.name}>
                    {game.name}
                  </option>
                ))}
              </Select>
              {gamesQuery.isError ? (
                <p className="text-xs text-destructive">游戏列表加载失败，请稍后重试</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="skill-rank">当前段位</Label>
              <Input
                id="skill-rank"
                placeholder="如：荣耀王者"
                value={rankName}
                maxLength={30}
                onChange={(event) => setRankName(event.target.value)}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="skill-price">单价（元）</Label>
                <Input
                  id="skill-price"
                  type="text"
                  inputMode="decimal"
                  maxLength={9}
                  placeholder="如：30.00"
                  value={priceYuan}
                  onChange={(event) => setPriceYuan(sanitizeMoneyInput(event.target.value))}
                />
                <p className="text-[11px] text-muted-foreground">0.01–100000 元，最多两位小数</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="skill-unit">计价单位</Label>
                <Select id="skill-unit" value={unit} onChange={(event) => setUnit(event.target.value)}>
                  <option value="小时">小时</option>
                  <option value="局">局</option>
                </Select>
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="skill-status">上架状态</Label>
              <Select
                id="skill-status"
                value={status}
                onChange={(event) => setStatus(Number(event.target.value))}
              >
                <option value={1}>上架中</option>
                <option value={0}>已下架</option>
              </Select>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button disabled={saving} onClick={() => void save()}>
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
