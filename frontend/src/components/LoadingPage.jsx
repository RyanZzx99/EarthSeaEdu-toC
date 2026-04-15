import React from "react";
import { createPortal } from "react-dom";
import { motion } from "motion/react";
import { GraduationCap } from "lucide-react";

export function LoadingPage({
  message = "\u52a0\u8f7d\u4e2d...",
  submessage = "\u8bf7\u7a0d\u5019\uff0c\u6b63\u5728\u4e3a\u60a8\u51c6\u5907\u5185\u5bb9",
}) {
  return (
    <div
      style={{
        minHeight: "100vh",
        width: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background:
          "linear-gradient(135deg, #f8fafc 0%, #eff6ff 50%, #eef2ff 100%)",
      }}
    >
      <div
        style={{
          textAlign: "center",
          padding: "0 24px",
        }}
      >
        <motion.div
          style={{
            width: "96px",
            height: "96px",
            margin: "0 auto 32px",
            borderRadius: "16px",
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            background: "linear-gradient(135deg, #1a2744 0%, #2c4a8a 100%)",
            boxShadow: "0 24px 48px rgba(15, 23, 42, 0.22)",
          }}
          animate={{
            scale: [1, 1.1, 1],
            rotate: [0, 5, -5, 0],
          }}
          transition={{
            duration: 2,
            repeat: Infinity,
            ease: "easeInOut",
          }}
        >
          <GraduationCap className="w-12 h-12 text-white" />
        </motion.div>

        <motion.h2
          style={{
            margin: "0 0 12px",
            color: "#1a2744",
            fontSize: "32px",
            fontWeight: 700,
            lineHeight: 1.2,
          }}
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
        >
          {message}
        </motion.h2>

        <motion.p
          style={{
            margin: "0 0 32px",
            color: "#4b5563",
            fontSize: "16px",
            lineHeight: 1.6,
          }}
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.3 }}
        >
          {submessage}
        </motion.p>

        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "8px",
          }}
        >
          {[0, 1, 2].map((index) => (
            <motion.div
              key={index}
              style={{
                width: "12px",
                height: "12px",
                borderRadius: "9999px",
                background: "linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)",
              }}
              animate={{
                y: [0, -12, 0],
                opacity: [0.5, 1, 0.5],
              }}
              transition={{
                duration: 1,
                repeat: Infinity,
                delay: index * 0.2,
                ease: "easeInOut",
              }}
            />
          ))}
        </div>

        <motion.div
          style={{
            width: "256px",
            height: "4px",
            margin: "32px auto 0",
            borderRadius: "9999px",
            overflow: "hidden",
            background: "#e5e7eb",
          }}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.5 }}
        >
          <motion.div
            style={{
              height: "100%",
              borderRadius: "9999px",
              background: "linear-gradient(90deg, #4f46e5 0%, #6366f1 100%)",
            }}
            animate={{
              x: ["-100%", "100%"],
            }}
            transition={{
              duration: 1.5,
              repeat: Infinity,
              ease: "linear",
            }}
          />
        </motion.div>
      </div>
    </div>
  );
}

export function LoadingOverlay({
  message = "\u5904\u7406\u4e2d...",
  submessage,
}) {
  const overlay = (
    <motion.div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 9999,
        background: "rgba(15, 23, 42, 0.2)",
        backdropFilter: "blur(4px)",
        WebkitBackdropFilter: "blur(4px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "16px",
      }}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <motion.div
        style={{
          width: "100%",
          maxWidth: "448px",
          margin: "0 16px",
          padding: "48px",
          background: "#ffffff",
          borderRadius: "16px",
          boxShadow: "0 24px 48px rgba(15, 23, 42, 0.22)",
        }}
        initial={{ scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        exit={{ scale: 0.9, opacity: 0 }}
        transition={{ type: "spring", stiffness: 300, damping: 30 }}
      >
        <motion.div
          style={{
            width: "64px",
            height: "64px",
            margin: "0 auto 24px",
            borderRadius: "9999px",
            border: "4px solid #e5e7eb",
            borderTopColor: "#4f46e5",
            boxSizing: "border-box",
          }}
          animate={{ rotate: 360 }}
          transition={{
            duration: 1,
            repeat: Infinity,
            ease: "linear",
          }}
        />

        <h3
          style={{
            margin: "0 0 8px",
            textAlign: "center",
            color: "#111827",
            fontSize: "20px",
            fontWeight: 700,
            lineHeight: 1.3,
          }}
        >
          {message}
        </h3>
        {submessage ? (
          <p
            style={{
              margin: 0,
              textAlign: "center",
              color: "#4b5563",
              fontSize: "14px",
              lineHeight: 1.6,
            }}
          >
            {submessage}
          </p>
        ) : null}
      </motion.div>
    </motion.div>
  );

  if (typeof document === "undefined") {
    return overlay;
  }

  return createPortal(overlay, document.body);
}

export function InlineLoading({
  message = "\u52a0\u8f7d\u4e2d",
  size = "md",
}) {
  const sizeMap = {
    sm: { size: 16, borderWidth: 2 },
    md: { size: 24, borderWidth: 2 },
    lg: { size: 32, borderWidth: 3 },
  };
  const currentSize = sizeMap[size] || sizeMap.md;

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: "12px",
      }}
    >
      <motion.div
        style={{
          width: `${currentSize.size}px`,
          height: `${currentSize.size}px`,
          borderRadius: "9999px",
          borderStyle: "solid",
          borderWidth: `${currentSize.borderWidth}px`,
          borderColor: "#e5e7eb",
          borderTopColor: "#4f46e5",
          boxSizing: "border-box",
        }}
        animate={{ rotate: 360 }}
        transition={{
          duration: 1,
          repeat: Infinity,
          ease: "linear",
        }}
      />
      <span
        style={{
          color: "#4b5563",
          fontSize: "14px",
          lineHeight: 1.4,
        }}
      >
        {message}
      </span>
    </div>
  );
}
