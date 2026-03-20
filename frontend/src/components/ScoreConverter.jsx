import React, { useState } from "react";
import {
  convertScore,
  formatConvertedScore,
  scoreLabels,
  scoreRanges,
} from "../utils/scoreConverter";

export default function ScoreConverter() {
  const [fromTest, setFromTest] = useState("toefl");
  const [toTest, setToTest] = useState("ielts");
  const [scoreInput, setScoreInput] = useState("");
  const [resultMessage, setResultMessage] = useState("");
  const [resultValue, setResultValue] = useState("");
  const [hasEstimatedResult, setHasEstimatedResult] = useState(false);

  function handleConvert() {
    const rawValue = Number.parseFloat(scoreInput);

    // 中文注释：保留原有点击后校验的交互，不在输入阶段实时打断用户
    if (Number.isNaN(rawValue)) {
      setResultMessage("请输入有效的分数。");
      setResultValue("");
      setHasEstimatedResult(false);
      return;
    }

    if (fromTest === toTest) {
      setResultMessage(`Same test selected - result: ${rawValue}`);
      setResultValue("");
      setHasEstimatedResult(false);
      return;
    }

    const [min, max] = scoreRanges[fromTest] ?? [Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY];

    if (rawValue < min || rawValue > max) {
      setResultMessage(`输入值超出 ${fromTest.toUpperCase()} 的有效范围 (${min}-${max})。`);
      setResultValue("");
      setHasEstimatedResult(false);
      return;
    }

    const converted = convertScore(rawValue, fromTest, toTest);
    const formatted = formatConvertedScore(converted, toTest);

    if (formatted === null) {
      setResultMessage("无法换算该分数。");
      setResultValue("");
      setHasEstimatedResult(false);
      return;
    }

    setResultMessage(`Estimated ${scoreLabels[toTest]}:`);
    setResultValue(`${formatted}`);
    setHasEstimatedResult(true);
  }

  return (
    <div className="converter" aria-labelledby="converter-heading">
      <h4 id="converter-heading">Score Converter - 语言类标准化考试分数换算器</h4>
      <p className="note">提示：换算为近似值，仅供参考。使用官方机构发布的换算表以获取权威结果。</p>

      <div className="row">
        <label htmlFor="fromTest">From</label>
        <select id="fromTest" value={fromTest} onChange={(event) => setFromTest(event.target.value)}>
          <option value="toefl">TOEFL iBT</option>
          <option value="ielts">IELTS</option>
          <option value="pte">PTE Academic</option>
          <option value="duolingo">Duolingo English Test</option>
          <option value="languagecert">LanguageCert Academic</option>
        </select>
        <span className="row-gap" />
        <label htmlFor="toTest">To</label>
        <select id="toTest" value={toTest} onChange={(event) => setToTest(event.target.value)}>
          <option value="ielts">IELTS</option>
          <option value="toefl">TOEFL iBT</option>
          <option value="pte">PTE Academic</option>
          <option value="duolingo">Duolingo English Test</option>
          <option value="languagecert">LanguageCert Academic</option>
        </select>
      </div>

      <div className="row">
        <label htmlFor="scoreInput">Score</label>
        <input
          id="scoreInput"
          value={scoreInput}
          onChange={(event) => setScoreInput(event.target.value)}
          type="number"
          min="0"
          step="0.5"
          placeholder="Enter score"
        />
        <button id="convertBtn" className="btn" type="button" onClick={handleConvert}>
          Convert
        </button>
      </div>

      <div id="result" className="result" aria-live="polite">
        {hasEstimatedResult ? (
          <>
            {resultMessage} <strong>{resultValue}</strong>（近似）
          </>
        ) : (
          resultMessage
        )}
      </div>
    </div>
  );
}
