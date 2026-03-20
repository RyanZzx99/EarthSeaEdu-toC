import React, { useEffect, useMemo, useState } from "react";

const regionMap = {
  USA: "美国",
  Canada: "加拿大",
  HKMO: "中国香港/澳门",
  UK: "英国",
  AUNZ: "澳新",
  Singapore: "新加坡",
  CN: "中国大陆",
  China: "中国大陆",
  Joint: "中外合作",
};

function getQsRange(value) {
  if (value === "all") {
    return [Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY];
  }

  return value.split("-").map(Number);
}

function getSchoolInitials(name) {
  return name
    .split(" ")
    .map((word) => word[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

export default function RankingTool() {
  const [regionFilter, setRegionFilter] = useState("all");
  const [qsFilter, setQsFilter] = useState("all");
  const [schoolSearch, setSchoolSearch] = useState("");
  const [schools, setSchools] = useState([]);
  const [loadError, setLoadError] = useState("");
  const [failedLogoIds, setFailedLogoIds] = useState(new Set());

  useEffect(() => {
    let cancelled = false;

    async function loadSchools() {
      try {
        const response = await fetch("/data/schools.json");

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();

        if (!cancelled) {
          setSchools(data);
        }
      } catch (error) {
        console.error("Failed to load schools.json", error);

        if (!cancelled) {
          setLoadError("学校数据未找到，请确认 /public/data/schools.json 已存在。");
        }
      }
    }

    loadSchools();

    return () => {
      cancelled = true;
    };
  }, []);

  const visibleSchools = useMemo(() => {
    const [minQs, maxQs] = getQsRange(qsFilter);
    const searchKeyword = schoolSearch.trim().toLowerCase();
    const noFilters = regionFilter === "all" && qsFilter === "all" && searchKeyword === "";

    const baseList = noFilters
      ? schools.slice().sort((left, right) => left.qs - right.qs).slice(0, 10)
      : schools.filter((school) => {
          const regionMatches =
            regionFilter === "all" ||
            school.region === regionFilter ||
            (regionFilter === "CN" && (school.region === "China" || school.region === "CN"));

          const qsMatches =
            (school.qs >= minQs && school.qs <= maxQs) ||
            (minQs === Number.NEGATIVE_INFINITY && maxQs === Number.POSITIVE_INFINITY);

          return regionMatches && qsMatches;
        });

    if (searchKeyword === "") {
      return baseList;
    }

    return baseList.filter((school) =>
      `${school.name_en} ${school.name_cn}`.toLowerCase().includes(searchKeyword)
    );
  }, [qsFilter, regionFilter, schoolSearch, schools]);

  function canShowLogo(school) {
    return Boolean(school.logo) && !failedLogoIds.has(school.id);
  }

  function markLogoAsFailed(id) {
    // 中文注释：图片加载失败后只屏蔽当前学校 logo，继续展示首字母占位
    setFailedLogoIds((previous) => new Set([...previous, id]));
  }

  return (
    <div className="ranking-tool" aria-labelledby="ranking-heading">
      <h4 id="ranking-heading">大学排名查询</h4>
      <p className="note">根据地区和 QS 排名区间筛选学校。美国学校额外显示 US News 排名。</p>

      <div className="filters">
        <label htmlFor="regionFilter">地区：</label>
        <select id="regionFilter" value={regionFilter} onChange={(event) => setRegionFilter(event.target.value)}>
          <option value="all">全部</option>
          <option value="USA">美国</option>
          <option value="Canada">加拿大</option>
          <option value="HKMO">中国香港/澳门</option>
          <option value="UK">英国</option>
          <option value="AUNZ">澳新</option>
          <option value="Singapore">新加坡</option>
          <option value="CN">中国大陆</option>
        </select>

        <label htmlFor="qsFilter">QS 排名：</label>
        <select id="qsFilter" value={qsFilter} onChange={(event) => setQsFilter(event.target.value)}>
          <option value="all">全部</option>
          <option value="1-20">QS1-20</option>
          <option value="21-50">QS21-50</option>
          <option value="50-100">QS50-100</option>
          <option value="100-200">QS100-200</option>
        </select>

        <input
          id="schoolSearch"
          value={schoolSearch}
          onChange={(event) => setSchoolSearch(event.target.value)}
          type="search"
          placeholder="按校名搜索（中文/英文）"
        />

        <button id="applyFilter" className="btn" type="button">
          筛选
        </button>
      </div>

      <div id="schoolsList" className="schools-list" aria-live="polite">
        {loadError ? <p className="note">{loadError}</p> : null}
        {!loadError && visibleSchools.length === 0 ? <p className="note">未找到学校。</p> : null}

        {visibleSchools.map((school) => (
          <div key={school.id} className="school-card">
            <div className="logo-wrap">
              {canShowLogo(school) ? (
                <img
                  className="logo-img"
                  src={school.logo}
                  alt={`${school.name_en} logo`}
                  onError={() => markLogoAsFailed(school.id)}
                />
              ) : null}
              <div className="logo-fallback" aria-hidden="true">
                {getSchoolInitials(school.name_en)}
              </div>
            </div>

            <div className="school-info">
              <div className="school-name">
                {school.name_cn ? <div className="name-cn">{school.name_cn}</div> : null}
                <div className="name-en">{school.name_en}</div>
              </div>
              <div className="meta">
                <strong>地区:</strong> {regionMap[school.region] || school.region}
              </div>
              <div className="meta">
                <strong>QS:</strong> {school.qs}
              </div>
              {school.region === "USA" ? (
                <div className="meta">
                  <strong>US News:</strong> {school.usnews || "N/A"}
                </div>
              ) : null}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
