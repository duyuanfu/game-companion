/**
 * 金额工具。
 *
 * 后端字段单位并不完全统一：
 * - CompanionItem.price 是「元」
 * - CompanionSkillVO.price / 订单 / 钱包 / 提现 / 工作台 都是「分」
 * 所有转换必须走这里的函数，禁止在组件里散落 /100。
 */

export function fenToYuan(fen?: number | null): number {
  if (typeof fen !== "number" || Number.isNaN(fen)) return 0;
  return fen / 100;
}

export function yuanToFen(yuan: number | string): number {
  const value = typeof yuan === "string" ? Number(yuan) : yuan;
  if (!Number.isFinite(value)) return 0;
  return Math.round(value * 100);
}

const formatter = new Intl.NumberFormat("zh-CN", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

export function formatYuan(yuan?: number | null, withSymbol = false): string {
  const value = typeof yuan === "number" && Number.isFinite(yuan) ? yuan : 0;
  const text = formatter.format(value);
  return withSymbol ? `¥${text}` : text;
}

export function formatFen(fen?: number | null, withSymbol = false): string {
  return formatYuan(fenToYuan(fen), withSymbol);
}

/** 用于输入框回填，避免出现 25.00 这种不适合编辑的格式 */
export function fenToYuanInput(fen?: number | null): string {
  const value = fenToYuan(fen);
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}
