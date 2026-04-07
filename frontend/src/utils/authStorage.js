const ACCESS_TOKEN_KEY = "access_token";

function clearAccessTokenStorage() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
}

function notifyAuthTokenChanged() {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(new Event("auth-token-changed"));
}

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || sessionStorage.getItem(ACCESS_TOKEN_KEY) || "";
}

export function setAccessToken(token, remember = false) {
  clearAccessTokenStorage();

  if (!token) {
    notifyAuthTokenChanged();
    return;
  }

  if (remember) {
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
    notifyAuthTokenChanged();
    return;
  }

  sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
  notifyAuthTokenChanged();
}

export function clearAccessToken() {
  clearAccessTokenStorage();
  notifyAuthTokenChanged();
}
