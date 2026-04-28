import React from "react";
import { CheckCircle2, Circle } from "lucide-react";
import {
  MOCK_EXAM_ALL_CONTENT,
  MOCK_EXAM_CATEGORY_ACT,
  MOCK_EXAM_CATEGORY_ALEVEL,
  MOCK_EXAM_CATEGORY_IELTS,
  getExamCategoryLabel,
  getPaperContentLabel,
} from "../mockexam/pageHelpers";

function FilterRadioRow({ active, children, onClick }) {
  return (
    <button
      type="button"
      className={`mockexam-radio-row${active ? " active" : ""}`}
      onClick={onClick}
    >
      <span className="mockexam-check-icon">
        {active ? <CheckCircle2 size={16} /> : <Circle size={16} />}
      </span>
      <span>{children}</span>
    </button>
  );
}

export default function MockExamExamFilterSections({
  examCategory,
  examCategoryOptions,
  examContent,
  contentOptions,
  onExamCategoryChange,
  onExamContentChange,
  contentTitle = "科目",
  allContentLabel = "全部",
  allowAllContent = true,
}) {
  const categories = Array.isArray(examCategoryOptions) && examCategoryOptions.length
    ? examCategoryOptions
    : [MOCK_EXAM_CATEGORY_IELTS, MOCK_EXAM_CATEGORY_ALEVEL, MOCK_EXAM_CATEGORY_ACT];
  const contents = Array.isArray(contentOptions) ? contentOptions : [];

  return (
    <>
      <section className="mockexam-filter-section">
        <h3>考试类型</h3>
        {categories.map((category) => (
          <FilterRadioRow
            key={category}
            active={examCategory === category}
            onClick={() => onExamCategoryChange(category)}
          >
            {getExamCategoryLabel(category)}
          </FilterRadioRow>
        ))}
      </section>

      <section className="mockexam-filter-section">
        <h3>{contentTitle}</h3>
        {allowAllContent ? (
          <FilterRadioRow
            active={examContent === MOCK_EXAM_ALL_CONTENT}
            onClick={() => onExamContentChange(MOCK_EXAM_ALL_CONTENT)}
          >
            {allContentLabel}
          </FilterRadioRow>
        ) : null}
        {contents.map((content) => (
          <FilterRadioRow
            key={content}
            active={examContent === content}
            onClick={() => onExamContentChange(content)}
          >
            {getPaperContentLabel(content)}
          </FilterRadioRow>
        ))}
      </section>
    </>
  );
}
