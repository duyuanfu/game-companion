const MONEY_PATTERN = /^\d{1,6}(\.\d{1,2})?$/;

/**
 * 金额输入规范化：只保留数字和一个小数点，小数最多两位，整数最多六位。
 * 例：12.345 -> 12.34；abc12.3 -> 12.3
 */
export function sanitizeMoneyInput(value: string, maxIntegerDigits = 6): string {
  const cleaned = value.replace(/[^\d.]/g, "");
  const [integerPart, ...decimalParts] = cleaned.split(".");
  const integer = integerPart.slice(0, maxIntegerDigits);
  if (decimalParts.length === 0) {
    return integer;
  }
  const decimals = decimalParts.join("").slice(0, 2);
  return `${integer}.${decimals}`;
}

export function isValidMoney(value: string, max = 100000): boolean {
  if (!MONEY_PATTERN.test(value)) {
    return false;
  }
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0 && amount <= max;
}
