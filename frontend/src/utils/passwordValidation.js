export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 24;
export const BCRYPT_PASSWORD_MAX_BYTES = 72;

export function getUtf8ByteLength(value) {
  return new TextEncoder().encode(value).length;
}

export function validatePasswordRule(value) {
  if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
    return "密码长度需为 8-24 位";
  }

  if (/\s/.test(value)) {
    return "密码不能包含空格";
  }

  const hasLetter = /[A-Za-z]/.test(value);
  const hasDigit = /\d/.test(value);
  const hasSpecial = /[^A-Za-z0-9]/.test(value);
  const categoryCount = [hasLetter, hasDigit, hasSpecial].filter(Boolean).length;

  if (categoryCount < 2) {
    return "密码至少需要包含字母、数字、特殊字符中的 2 种";
  }

  if (getUtf8ByteLength(value) > BCRYPT_PASSWORD_MAX_BYTES) {
    return "密码字节长度不能超过 72 bytes";
  }

  return "";
}
