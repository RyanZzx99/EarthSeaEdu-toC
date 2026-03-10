<script setup>
import { computed, onMounted, ref } from 'vue';

// Keep region labels centralized so the template remains simple and
// the displayed wording stays consistent with the original feature.
const regionMap = {
  USA: '美国',
  Canada: '加拿大',
  HKMO: '中国香港/澳门',
  UK: '英国',
  AUNZ: '澳新',
  Singapore: '新加坡',
  CN: '中国大陆',
  China: '中国大陆',
  Joint: '中外合作'
};

const regionFilter = ref('all');
const qsFilter = ref('all');
const schoolSearch = ref('');
const schools = ref([]);
const loadError = ref('');
const failedLogoIds = ref(new Set());

function getQsRange(value) {
  if (value === 'all') {
    return [Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY];
  }

  return value.split('-').map(Number);
}

function getSchoolInitials(name) {
  return name
    .split(' ')
    .map((word) => word[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

function markLogoAsFailed(id) {
  failedLogoIds.value = new Set([...failedLogoIds.value, id]);
}

function canShowLogo(school) {
  return Boolean(school.logo) && !failedLogoIds.value.has(school.id);
}

const visibleSchools = computed(() => {
  const [minQs, maxQs] = getQsRange(qsFilter.value);
  const searchKeyword = schoolSearch.value.trim().toLowerCase();
  const noFilters =
    regionFilter.value === 'all' &&
    qsFilter.value === 'all' &&
    searchKeyword === '';

  // The legacy page shows only the top 10 schools when no filters
  // or search term are provided. Preserve that exact default behavior.
  let baseList = noFilters
    ? schools.value.slice().sort((left, right) => left.qs - right.qs).slice(0, 10)
    : schools.value.filter((school) => {
        const regionMatches =
          regionFilter.value === 'all' ||
          school.region === regionFilter.value ||
          (regionFilter.value === 'CN' && (school.region === 'China' || school.region === 'CN'));

        const qsMatches =
          (school.qs >= minQs && school.qs <= maxQs) ||
          (minQs === Number.NEGATIVE_INFINITY && maxQs === Number.POSITIVE_INFINITY);

        return regionMatches && qsMatches;
      });

  if (searchKeyword === '') {
    return baseList;
  }

  return baseList.filter((school) =>
    `${school.name_en} ${school.name_cn}`.toLowerCase().includes(searchKeyword)
  );
});

onMounted(async () => {
  try {
    const response = await fetch('/data/schools.json');

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    schools.value = await response.json();
  } catch (error) {
    console.error('Failed to load schools.json', error);
    loadError.value = '学校数据未找到，请将 data/schools.json 放在项目中。';
  }
});
</script>

<template>
  <div class="ranking-tool" aria-labelledby="ranking-heading">
    <h4 id="ranking-heading">大学排名查询</h4>
    <p class="note">根据地区和 QS 排名区间筛选学校。美国学校额外显示 US News 排名。</p>

    <div class="filters">
      <label for="regionFilter">地区：</label>
      <select id="regionFilter" v-model="regionFilter">
        <option value="all">全部</option>
        <option value="USA">美国</option>
        <option value="Canada">加拿大</option>
        <option value="HKMO">中国香港/澳门</option>
        <option value="UK">英国</option>
        <option value="AUNZ">澳新</option>
        <option value="Singapore">新加坡</option>
        <option value="CN">中国大陆</option>
      </select>

      <label for="qsFilter">QS 排名：</label>
      <select id="qsFilter" v-model="qsFilter">
        <option value="all">全部</option>
        <option value="1-20">QS1-20</option>
        <option value="21-50">QS21-50</option>
        <option value="50-100">QS50-100</option>
        <option value="100-200">QS100-200</option>
      </select>

      <input
        id="schoolSearch"
        v-model="schoolSearch"
        type="search"
        placeholder="按校名搜索（中文/英文）"
      />

      <button id="applyFilter" class="btn" type="button">筛选</button>
    </div>

    <div id="schoolsList" class="schools-list" aria-live="polite">
      <p v-if="loadError" class="note">{{ loadError }}</p>
      <p v-else-if="visibleSchools.length === 0" class="note">未找到学校。</p>

      <div v-for="school in visibleSchools" :key="school.id" class="school-card">
        <div class="logo-wrap">
          <img
            v-if="canShowLogo(school)"
            class="logo-img"
            :src="school.logo"
            :alt="`${school.name_en} logo`"
            @error="markLogoAsFailed(school.id)"
          />
          <div class="logo-fallback" aria-hidden="true">
            {{ getSchoolInitials(school.name_en) }}
          </div>
        </div>

        <div class="school-info">
          <div class="school-name">
            <div v-if="school.name_cn" class="name-cn">{{ school.name_cn }}</div>
            <div class="name-en">{{ school.name_en }}</div>
          </div>
          <div class="meta"><strong>地区:</strong> {{ regionMap[school.region] || school.region }}</div>
          <div class="meta"><strong>QS:</strong> {{ school.qs }}</div>
          <div v-if="school.region === 'USA'" class="meta">
            <strong>US News:</strong> {{ school.usnews || 'N/A' }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
