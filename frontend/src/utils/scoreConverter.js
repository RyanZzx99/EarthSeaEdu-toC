// 使用一组锚点分数做近似换算。
// 这里保留了原页面的换算策略，避免迁移到 Vue 后出现结果变化。
export const anchors = [
  { toefl: 120, ielts: 9.0, pte: 90, duolingo: 160, languagecert: 120 },
  { toefl: 115, ielts: 8.5, pte: 86, duolingo: 155, languagecert: 115 },
  { toefl: 110, ielts: 8.0, pte: 84, duolingo: 150, languagecert: 110 },
  { toefl: 100, ielts: 7.0, pte: 73, duolingo: 140, languagecert: 100 },
  { toefl: 90, ielts: 6.5, pte: 65, duolingo: 130, languagecert: 90 },
  { toefl: 80, ielts: 6.0, pte: 58, duolingo: 120, languagecert: 80 },
  { toefl: 70, ielts: 5.5, pte: 50, duolingo: 110, languagecert: 70 },
  { toefl: 60, ielts: 5.0, pte: 42, duolingo: 100, languagecert: 60 },
  { toefl: 50, ielts: 4.5, pte: 35, duolingo: 85, languagecert: 50 },
  { toefl: 40, ielts: 4.0, pte: 30, duolingo: 70, languagecert: 40 },
  { toefl: 0, ielts: 0.0, pte: 10, duolingo: 10, languagecert: 0 }
];

export const scoreRanges = {
  toefl: [0, 120],
  ielts: [0, 9],
  pte: [10, 90],
  duolingo: [10, 160],
  languagecert: [0, 120]
};

export const scoreLabels = {
  toefl: 'TOEFL iBT',
  ielts: 'IELTS',
  pte: 'PTE Academic',
  duolingo: 'Duolingo',
  languagecert: 'LanguageCert Academic'
};

export function convertScore(value, fromKey, toKey) {
  const sorted = anchors.slice().sort((left, right) => right[fromKey] - left[fromKey]);
  const max = sorted[0][fromKey];
  const min = sorted[sorted.length - 1][fromKey];

  if (value >= max) {
    return sorted[0][toKey];
  }

  if (value <= min) {
    return sorted[sorted.length - 1][toKey];
  }

  for (let index = 0; index < sorted.length - 1; index += 1) {
    const current = sorted[index];
    const next = sorted[index + 1];

    if (value <= current[fromKey] && value >= next[fromKey]) {
      const ratio = (value - next[fromKey]) / (current[fromKey] - next[fromKey]);
      return next[toKey] + ratio * (current[toKey] - next[toKey]);
    }
  }

  return null;
}

export function formatConvertedScore(rawValue, toKey) {
  if (rawValue === null || rawValue === undefined || Number.isNaN(rawValue)) {
    return null;
  }

  if (toKey === 'ielts') {
    return Math.round(rawValue * 2) / 2;
  }

  return Math.round(rawValue);
}
