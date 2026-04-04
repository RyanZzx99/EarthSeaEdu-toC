const QUICK_PRACTICE_STORAGE_PREFIX = "mockexam_quick_practice_";

function buildStorageKey(storageId) {
  return `${QUICK_PRACTICE_STORAGE_PREFIX}${storageId}`;
}

export function createQuickPracticeStorageId() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function saveQuickPracticePayload(storageId, payload) {
  window.localStorage.setItem(buildStorageKey(storageId), JSON.stringify(payload));
}

export function loadQuickPracticePayload(storageId) {
  const raw = window.localStorage.getItem(buildStorageKey(storageId));
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw);
  } catch (_error) {
    return null;
  }
}

export function removeQuickPracticePayload(storageId) {
  window.localStorage.removeItem(buildStorageKey(storageId));
}
