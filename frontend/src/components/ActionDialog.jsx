import React from "react";
import { createPortal } from "react-dom";
import { motion } from "motion/react";
import { AlertCircle, CheckCircle2, X } from "lucide-react";
import "../mockexam/actionDialog.css";

export default function ActionDialog({
  title,
  message,
  confirmText = "确定",
  cancelText = "取消",
  hideCancel = false,
  tone = "default",
  onConfirm,
  onCancel,
}) {
  const icon =
    tone === "success" ? <CheckCircle2 size={22} strokeWidth={2.2} /> : <AlertCircle size={22} strokeWidth={2.2} />;

  const dialog = (
    <motion.div
      className="action-dialog-backdrop"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onClick={onCancel}
    >
      <motion.div
        className={`action-dialog-card tone-${tone}`}
        initial={{ opacity: 0, y: 18, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 18, scale: 0.98 }}
        transition={{ duration: 0.18 }}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="action-dialog-head">
          <div className="action-dialog-head-copy">
            <div className={`action-dialog-icon tone-${tone}`}>{icon}</div>
            <div>
              <h3>{title}</h3>
              {message ? <p>{message}</p> : null}
            </div>
          </div>

          <button type="button" className="action-dialog-close" onClick={onCancel} aria-label="关闭弹窗">
            <X size={18} />
          </button>
        </div>

        <div className="action-dialog-actions">
          {!hideCancel ? (
            <button type="button" className="action-dialog-button secondary" onClick={onCancel}>
              {cancelText}
            </button>
          ) : null}
          <button
            type="button"
            className={`action-dialog-button primary tone-${tone}`}
            onClick={onConfirm}
          >
            {confirmText}
          </button>
        </div>
      </motion.div>
    </motion.div>
  );

  if (typeof document === "undefined") {
    return dialog;
  }

  return createPortal(dialog, document.body);
}
