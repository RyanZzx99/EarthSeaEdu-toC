<script setup>
import { ref } from 'vue';
import {
  convertScore,
  formatConvertedScore,
  scoreLabels,
  scoreRanges
} from '../utils/scoreConverter';

// Preserve the original initial selection so the Vue page opens
// with the same default state as the legacy static page.
const fromTest = ref('toefl');
const toTest = ref('ielts');
const scoreInput = ref('');
const resultMessage = ref('');
const resultValue = ref('');
const hasEstimatedResult = ref(false);

function handleConvert() {
  const rawValue = Number.parseFloat(scoreInput.value);

  // Keep validation in the click handler so the interaction stays
  // identical to the original page instead of recalculating on input.
  if (Number.isNaN(rawValue)) {
    resultMessage.value = '请输入有效的分数。';
    resultValue.value = '';
    hasEstimatedResult.value = false;
    return;
  }

  if (fromTest.value === toTest.value) {
    resultMessage.value = `Same test selected - result: ${rawValue}`;
    resultValue.value = '';
    hasEstimatedResult.value = false;
    return;
  }

  const [min, max] = scoreRanges[fromTest.value] ?? [Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY];

  if (rawValue < min || rawValue > max) {
    resultMessage.value = `输入值超出 ${fromTest.value.toUpperCase()} 的有效范围 (${min}-${max})。`;
    resultValue.value = '';
    hasEstimatedResult.value = false;
    return;
  }

  const converted = convertScore(rawValue, fromTest.value, toTest.value);
  const formatted = formatConvertedScore(converted, toTest.value);

  if (formatted === null) {
    resultMessage.value = '无法换算该分数。';
    resultValue.value = '';
    hasEstimatedResult.value = false;
    return;
  }

  // Split label/value storage lets the template keep the target
  // score bolded just like the original innerHTML version did.
  resultMessage.value = `Estimated ${scoreLabels[toTest.value]}:`;
  resultValue.value = `${formatted}`;
  hasEstimatedResult.value = true;
}
</script>

<template>
  <div class="converter" aria-labelledby="converter-heading">
    <h4 id="converter-heading">Score Converter — 语言类标化考试分数换算器</h4>
    <p class="note">提示：换算为近似值，仅供参考。使用官方机构发布的换算表以获取权威结果。</p>

    <div class="row">
      <label for="fromTest">From</label>
      <select id="fromTest" v-model="fromTest">
        <option value="toefl">TOEFL iBT</option>
        <option value="ielts">IELTS</option>
        <option value="pte">PTE Academic</option>
        <option value="duolingo">Duolingo English Test</option>
        <option value="languagecert">LanguageCert Academic</option>
      </select>
      <span class="row-gap" />
      <label for="toTest">To</label>
      <select id="toTest" v-model="toTest">
        <option value="ielts">IELTS</option>
        <option value="toefl">TOEFL iBT</option>
        <option value="pte">PTE Academic</option>
        <option value="duolingo">Duolingo English Test</option>
        <option value="languagecert">LanguageCert Academic</option>
      </select>
    </div>

    <div class="row">
      <label for="scoreInput">Score</label>
      <input
        id="scoreInput"
        v-model="scoreInput"
        type="number"
        min="0"
        step="0.5"
        placeholder="Enter score"
      />
      <button id="convertBtn" class="btn" type="button" @click="handleConvert">Convert</button>
    </div>

    <div id="result" class="result" aria-live="polite">
      <template v-if="hasEstimatedResult">
        {{ resultMessage }} <strong>{{ resultValue }}</strong>（近似）
      </template>
      <template v-else>
        {{ resultMessage }}
      </template>
    </div>
  </div>
</template>
