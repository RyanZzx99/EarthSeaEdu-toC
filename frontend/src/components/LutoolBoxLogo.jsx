import React from "react";

export function LutoolBoxLogo({
  size = 40,
  variant = "light",
  showText = true,
  showTagline = true,
}) {
  const isLight = variant === "light";
  const textColor = isLight ? "rgba(255,255,255,0.95)" : "#111827";
  const tagColor = isLight ? "rgba(255,255,255,0.45)" : "#6b7280";

  return (
    <div style={{ display: "flex", alignItems: "center", gap: size * 0.3 }}>
      <LutoolBoxMark size={size} variant={variant} />
      {showText ? (
        <div style={{ display: "flex", flexDirection: "column", lineHeight: 1 }}>
          <span
            style={{
              color: textColor,
              fontSize: size * 0.45,
              fontWeight: 700,
              letterSpacing: "0.01em",
              lineHeight: 1.25,
            }}
          >
            录途
          </span>
          {showTagline ? (
            <span
              style={{
                color: tagColor,
                fontSize: size * 0.27,
                fontWeight: 500,
                letterSpacing: "0.12em",
                lineHeight: 1.3,
                textTransform: "uppercase",
              }}
            >
              LutoolBox
            </span>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

export function LutoolBoxMark({ size = 40, variant = "light" }) {
  const isLight = variant === "light";

  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: size * 0.24,
        backgroundColor: isLight ? "rgba(255,255,255,0.12)" : "rgba(255,255,255,0.95)",
        border: `1.5px solid ${isLight ? "rgba(255,255,255,0.2)" : "rgba(79,142,247,0.15)"}`,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: size * 0.08,
        flexShrink: 0,
        boxShadow: isLight ? "0 2px 8px rgba(0,0,0,0.05)" : "0 2px 12px rgba(79,142,247,0.12)",
        transition: "all 0.3s ease",
      }}
    >
      {/* 中文注释：直接复用设计稿插画资源作为登录页品牌标识。 */}
      <img
        src="/assets/lutoolbox-login-illustration.png"
        alt="LutoolBox Logo"
        style={{
          width: "100%",
          height: "100%",
          objectFit: "contain",
          filter: isLight ? "invert(1) brightness(1.1)" : "none",
        }}
      />
    </div>
  );
}
