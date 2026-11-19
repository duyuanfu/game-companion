import { useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Controller, useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowLeft, Loader2, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { applyCompanion, getMyCompanionStatus } from "@/api/companion";
import { getGameCategories } from "@/api/game";
import { keys } from "@/lib/keys";
import { showError } from "@/lib/errors";
import { sanitizeMoneyInput } from "@/lib/moneyInput";
import { PageHeader, FullPageLoading } from "@/components/common/Feedback";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { AudioUploader, ImageUploader } from "@/components/domain/FileUploader";
import { Badge } from "@/components/ui/badge";

const schema = z.object({
  realName: z.string().trim().min(1, "请输入真实姓名"),
  phone: z.string().trim().regex(/^1\d{10}$/, "请输入 11 位手机号"),
  bio: z.string().max(200, "简介不能超过 200 字").optional(),
  voiceUrl: z.string().optional(),
  skills: z
    .array(
      z.object({
        gameName: z.string().min(1, "请选择游戏"),
        rankName: z.string().trim().min(1, "请输入当前段位"),
        price: z.coerce
          .number()
          .min(0.01, "单价至少 0.01 元")
          .max(100000, "单价不能超过 100000 元")
          .refine(
            (value) => Math.abs(value * 100 - Math.round(value * 100)) < 1e-9,
            "单价最多两位小数"
          ),
        unit: z.string().min(1),
        rankImg: z.string().optional()
      })
    )
    .min(1, "至少添加一个游戏技能")
});

type FormValues = z.infer<typeof schema>;

export function CompanionApplyPage() {
  const navigate = useNavigate();
  const statusQuery = useQuery({ queryKey: keys.myStatus, queryFn: getMyCompanionStatus });
  const gamesQuery = useQuery({ queryKey: keys.games, queryFn: getGameCategories, staleTime: 5 * 60_000 });

  const {
    register,
    control,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting }
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      realName: "",
      phone: "",
      bio: "",
      voiceUrl: "",
      skills: [{ gameName: "", rankName: "", price: 25, unit: "小时", rankImg: "" }]
    }
  });

  const { fields, append, remove } = useFieldArray({ control, name: "skills" });
  const voiceUrl = watch("voiceUrl");

  useEffect(() => {
    if (statusQuery.data?.realName) {
      setValue("realName", statusQuery.data.realName);
    }
  }, [statusQuery.data?.realName, setValue]);

  const onSubmit = async (values: FormValues) => {
    try {
      await applyCompanion({
        realName: values.realName.trim(),
        phone: values.phone.trim(),
        bio: values.bio?.trim() || undefined,
        voiceUrl: values.voiceUrl || undefined,
        voiceDuration: 0,
        skills: values.skills.map((skill) => ({
          gameName: skill.gameName,
          rankName: skill.rankName.trim(),
          price: Math.round(skill.price * 100),
          priceUnit: "FEN",
          unit: skill.unit,
          rankImg: skill.rankImg || undefined
        }))
      });
      toast.success("入驻申请已提交，请等待平台审核");
      navigate("/companion/status");
    } catch (error) {
      showError(error, "提交失败");
    }
  };

  if (statusQuery.isLoading) return <FullPageLoading label="正在检查入驻状态..." />;

  if (statusQuery.data?.hasProfile) {
    return (
      <div className="mx-auto max-w-2xl">
        <PageHeader title="入驻申请" description="你已提交过陪练入驻资料。" />
        <Card>
          <CardContent className="space-y-3 p-6">
            <Badge variant="secondary">
              {statusQuery.data.auditStatus === 0
                ? "审核中"
                : statusQuery.data.auditStatus === 1
                  ? "已通过"
                  : "已驳回"}
            </Badge>
            <p className="text-sm text-muted-foreground">
              {statusQuery.data.auditStatus === 2
                ? "当前资料未通过审核，可前往状态页查看原因并重新提交。"
                : "如需查看审核进度与接单资格，请前往入驻状态页。"}
            </p>
            <div className="flex gap-2">
              <Button asChild>
                <Link to="/companion/status">查看入驻状态</Link>
              </Button>
              {statusQuery.data.auditStatus === 1 ? (
                <Button asChild variant="outline">
                  <Link to="/companion/workbench">进入工作台</Link>
                </Button>
              ) : null}
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  const gameNames = (gamesQuery.data ?? []).map((game) => game.name).filter(Boolean);

  return (
    <div className="mx-auto max-w-3xl">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        返回
      </button>

      <PageHeader
        title="申请成为陪练"
        description="提交真实姓名、手机号与至少一个游戏技能。原声试听为选填加分项。"
      />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        <Card>
          <CardHeader>
            <CardTitle>基础资料</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="realName">真实姓名</Label>
              <Input id="realName" placeholder="用于平台实名备案" {...register("realName")} />
              {errors.realName ? <p className="text-xs text-destructive">{errors.realName.message}</p> : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="phone">联系手机</Label>
              <Input id="phone" inputMode="numeric" maxLength={11} placeholder="11 位手机号" {...register("phone")} />
              {errors.phone ? <p className="text-xs text-destructive">{errors.phone.message}</p> : null}
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="bio">个人简介（选填）</Label>
              <Textarea id="bio" placeholder="擅长位置、开黑风格、常用英雄..." {...register("bio")} />
              {errors.bio ? <p className="text-xs text-destructive">{errors.bio.message}</p> : null}
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label>原声试听（选填）</Label>
              <AudioUploader
                value={voiceUrl || undefined}
                onChange={(url) => setValue("voiceUrl", url || "")}
                label="上传原声介绍"
                hint="真实录音更容易获得客户信任；未录制不影响提交"
              />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>游戏技能</CardTitle>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => append({ gameName: "", rankName: "", price: 25, unit: "小时", rankImg: "" })}
            >
              <Plus className="h-4 w-4" />
              添加游戏
            </Button>
          </CardHeader>
          <CardContent className="space-y-4">
            {fields.map((field, index) => (
              <div key={field.id} className="space-y-3 rounded-xl border p-4">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-medium">技能 {index + 1}</p>
                  {fields.length > 1 ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="text-destructive"
                      onClick={() => remove(index)}
                    >
                      <Trash2 className="h-4 w-4" />
                      删除
                    </Button>
                  ) : null}
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label>游戏名称</Label>
                    <Select {...register(`skills.${index}.gameName`)} defaultValue="">
                      <option value="">请选择游戏</option>
                      {gameNames.map((name) => (
                        <option key={name} value={name}>
                          {name}
                        </option>
                      ))}
                    </Select>
                    {errors.skills?.[index]?.gameName ? (
                      <p className="text-xs text-destructive">{errors.skills[index]?.gameName?.message}</p>
                    ) : null}
                  </div>
                  <div className="space-y-2">
                    <Label>当前段位</Label>
                    <Input placeholder="如：荣耀王者 30 星" {...register(`skills.${index}.rankName`)} />
                    {errors.skills?.[index]?.rankName ? (
                      <p className="text-xs text-destructive">{errors.skills[index]?.rankName?.message}</p>
                    ) : null}
                  </div>
                  <div className="space-y-2">
                    <Label>单价（元）</Label>
                    <Controller
                      control={control}
                      name={`skills.${index}.price`}
                      render={({ field }) => (
                        <Input
                          type="text"
                          inputMode="decimal"
                          maxLength={9}
                          placeholder="如：25.00"
                          value={field.value === undefined || field.value === null ? "" : String(field.value)}
                          onChange={(event) => {
                            const raw = sanitizeMoneyInput(event.target.value);
                            field.onChange(raw === "" ? "" : Number(raw));
                          }}
                          onBlur={field.onBlur}
                        />
                      )}
                    />
                    <p className="text-[11px] text-muted-foreground">0.01–100000 元，最多两位小数</p>
                    {errors.skills?.[index]?.price ? (
                      <p className="text-xs text-destructive">{errors.skills[index]?.price?.message}</p>
                    ) : null}
                  </div>
                  <div className="space-y-2">
                    <Label>计价单位</Label>
                    <Select {...register(`skills.${index}.unit`)}>
                      <option value="小时">小时</option>
                      <option value="局">局</option>
                    </Select>
                  </div>
                  <div className="space-y-2 sm:col-span-2">
                    <Label>段位截图（建议上传）</Label>
                    <ImageUploader
                      value={watch(`skills.${index}.rankImg`)}
                      onChange={(url) => setValue(`skills.${index}.rankImg`, url || "")}
                      label="上传段位认证截图"
                    />
                  </div>
                </div>
              </div>
            ))}
            {errors.skills?.message ? (
              <p className="text-xs text-destructive">{errors.skills.message}</p>
            ) : null}
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => navigate(-1)}>
            取消
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            提交入驻申请
          </Button>
        </div>
      </form>
    </div>
  );
}
