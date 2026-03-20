import { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence } from "motion/react";
import { LutoolBoxLogo } from "./LutoolBoxLogo";

type LoginTab = "password" | "sms" | "wechat";

// Floating particle component
function Particle({ x, y, size, duration, delay }: { x: number; y: number; size: number; duration: number; delay: number }) {
  return (
    <motion.div
      className="absolute rounded-full pointer-events-none"
      style={{
        left: `${x}%`,
        top: `${y}%`,
        width: size,
        height: size,
        background: "rgba(255,255,255,0.12)",
      }}
      animate={{
        y: [0, -30, 0],
        x: [0, 10, -10, 0],
        opacity: [0.3, 0.7, 0.3],
        scale: [1, 1.2, 1],
      }}
      transition={{
        duration,
        delay,
        repeat: Infinity,
        ease: "easeInOut",
      }}
    />
  );
}

// Animated stat counter
function AnimatedStat({ target, label, delay }: { target: string; label: string; delay: number }) {
  return (
    <motion.div
      className="flex flex-col gap-1"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.5, ease: "easeOut" }}
    >
      <span style={{ fontSize: "22px", fontWeight: 700, color: "rgba(255,255,255,0.95)" }}>
        {target}
      </span>
      <span style={{ fontSize: "13px", color: "rgba(255,255,255,0.5)" }}>
        {label}
      </span>
    </motion.div>
  );
}

// Animated input field with focus highlight
function AnimatedInput({
  children,
  focused,
}: {
  children: React.ReactNode;
  focused: boolean;
}) {
  return (
    <motion.div
      className="flex items-center rounded-lg px-4 py-3 gap-3"
      animate={{
        borderColor: focused ? "#2c4a8a" : "#e5e7eb",
        boxShadow: focused ? "0 0 0 3px rgba(44,74,138,0.1)" : "0 0 0 0px rgba(44,74,138,0)",
      }}
      transition={{ duration: 0.2 }}
      style={{
        border: "1.5px solid",
        borderColor: focused ? "#2c4a8a" : "#e5e7eb",
        background: "#fafafa",
      }}
    >
      {children}
    </motion.div>
  );
}

// QR scanner line animation
function ScannerLine() {
  return (
    <motion.div
      className="absolute left-2 right-2 h-0.5 rounded-full"
      style={{
        background: "linear-gradient(90deg, transparent, #07C160, transparent)",
        boxShadow: "0 0 8px #07C160",
      }}
      animate={{ top: ["8%", "90%", "8%"] }}
      transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut" }}
    />
  );
}

const particles = [
  { x: 10, y: 15, size: 6, duration: 5, delay: 0 },
  { x: 85, y: 20, size: 10, duration: 7, delay: 1 },
  { x: 30, y: 60, size: 4, duration: 6, delay: 0.5 },
  { x: 70, y: 75, size: 8, duration: 8, delay: 2 },
  { x: 50, y: 35, size: 5, duration: 5.5, delay: 1.5 },
  { x: 20, y: 85, size: 7, duration: 9, delay: 0.8 },
  { x: 90, y: 55, size: 4, duration: 6.5, delay: 3 },
  { x: 60, y: 10, size: 9, duration: 7.5, delay: 0.3 },
  { x: 40, y: 90, size: 5, duration: 5, delay: 2.5 },
  { x: 75, y: 45, size: 6, duration: 8.5, delay: 1.2 },
];

export function LoginPage() {
  const [activeTab, setActiveTab] = useState<LoginTab>("password");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [smsPhone, setSmsPhone] = useState("");
  const [code, setCode] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [agreed, setAgreed] = useState(false);
  const [qrScanned, setQrScanned] = useState(false);
  const [hoveredPanel, setHoveredPanel] = useState<"left" | "right" | null>(null);

  // Focus states
  const [phoneFocused, setPhoneFocused] = useState(false);
  const [passFocused, setPassFocused] = useState(false);
  const [smsPhoneFocused, setSmsPhoneFocused] = useState(false);
  const [codeFocused, setCodeFocused] = useState(false);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const handleSendCode = () => {
    if (smsPhone.length === 11 && countdown === 0) {
      setCountdown(60);
    }
  };

  const tabs: { key: LoginTab; label: string }[] = [
    { key: "password", label: "密码登录" },
    { key: "sms", label: "验证码登录" },
    { key: "wechat", label: "微信扫码" },
  ];

  const formVariants = {
    initial: { opacity: 0, x: 16 },
    animate: { opacity: 1, x: 0 },
    exit: { opacity: 0, x: -16 },
  };

  return (
    <div className="min-h-screen w-full flex">
      {/* ── Left Panel ── */}
      <motion.div
        className="hidden lg:flex relative flex-col justify-between p-12 overflow-hidden"
        style={{
          background: "linear-gradient(135deg, #0a1220 0%, #0f1a2e 50%, #121826 100%)",
        }}
        animate={{
          width: hoveredPanel === "left" ? "58%" : hoveredPanel === "right" ? "42%" : "52%",
        }}
        transition={{ type: "spring", stiffness: 300, damping: 30 }}
        onMouseEnter={() => setHoveredPanel("left")}
        onMouseLeave={() => setHoveredPanel(null)}
      >
        {/* Animated background gradient orb */}
        <motion.div
          className="absolute rounded-full pointer-events-none"
          style={{
            width: 500,
            height: 500,
            background: "radial-gradient(circle, rgba(79,142,247,0.18) 0%, transparent 70%)",
            top: "10%",
            left: "20%",
          }}
          animate={{ scale: [1, 1.15, 1], x: [0, 30, 0], y: [0, -20, 0] }}
          transition={{ duration: 10, repeat: Infinity, ease: "easeInOut" }}
        />
        <motion.div
          className="absolute rounded-full pointer-events-none"
          style={{
            width: 300,
            height: 300,
            background: "radial-gradient(circle, rgba(56,201,176,0.12) 0%, transparent 70%)",
            bottom: "15%",
            right: "10%",
          }}
          animate={{ scale: [1, 1.2, 1], x: [0, -20, 0], y: [0, 25, 0] }}
          transition={{ duration: 8, repeat: Infinity, ease: "easeInOut", delay: 2 }}
        />

        {/* Background Image Overlay */}
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage: `url(https://images.unsplash.com/photo-1547817651-7fb0cc360536?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx1bml2ZXJzaXR5JTIwY2FtcHVzJTIwc3R1ZGVudHMlMjBzdHVkeWluZ3xlbnwxfHx8fDE3NzM5NzU3Njh8MA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral)`,
            backgroundSize: "cover",
            backgroundPosition: "center",
          }}
        />

        {/* Floating particles */}
        {particles.map((p, i) => (
          <Particle key={i} {...p} />
        ))}

        {/* Decorative circles */}
        <div className="absolute top-[-80px] right-[-80px] w-64 h-64 rounded-full opacity-10" style={{ background: "rgba(255,255,255,0.15)" }} />
        <div className="absolute bottom-[-60px] left-[-60px] w-48 h-48 rounded-full opacity-10" style={{ background: "rgba(255,255,255,0.1)" }} />

        {/* Top: Logo text */}
        <motion.div
          className="relative z-10"
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.1, ease: "easeOut" }}
        >
          <h2 style={{ fontSize: "24px", fontWeight: 700, color: "rgba(255,255,255,0.95)", letterSpacing: "0.02em" }}>
            录途 LutoolBox
          </h2>
        </motion.div>

        {/* Center Content */}
        <div className="relative z-10 flex flex-col gap-8 items-center justify-center flex-1">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.2, ease: "easeOut" }}
          >
            <h1 className="text-white mb-4" style={{ fontSize: "48px", fontWeight: 700, lineHeight: 1.3, letterSpacing: "-0.01em" }}>
              欢迎使用录途<br />你的留学申请工具包
            </h1>
            <p style={{ fontSize: "20px", color: "rgba(255,255,255,0.75)", lineHeight: 1.7, maxWidth: "480px" }}>
              备考、选校、申请、查分，一站式搞定。
            </p>
          </motion.div>
        </div>

        {/* Bottom */}
        <motion.div
          className="relative z-10"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6, delay: 1 }}
        >
          <p style={{ fontSize: "13px", color: "rgba(255,255,255,0.35)" }}>
            © 2026 录途 LutoolBox
          </p>
        </motion.div>
      </motion.div>

      {/* ── Right Panel ── */}
      <motion.div
        className="flex-1 flex flex-col items-center justify-center px-6 py-12 bg-white min-h-screen"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.5 }}
        onMouseEnter={() => setHoveredPanel("right")}
        onMouseLeave={() => setHoveredPanel(null)}
      >
        <motion.div
          className="w-full max-w-[380px]"
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.15, ease: "easeOut" }}
        >
          {/* Header */}
          <div className="mb-8">
            <h2 style={{ fontSize: "26px", fontWeight: 700, color: "#111827", marginBottom: "6px" }}>
              欢迎回来
            </h2>
            <p style={{ fontSize: "14px", color: "#6b7280" }}>
              登录您的学习账户，继续探索之旅
            </p>
          </div>

          {/* Tabs */}
          <div
            className="flex rounded-lg mb-7 p-1 relative"
            style={{ background: "#f3f4f6" }}
          >
            {tabs.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className="flex-1 py-2 rounded-md relative z-10"
                style={{
                  fontSize: "13px",
                  fontWeight: activeTab === tab.key ? 600 : 400,
                  color: activeTab === tab.key ? "#1a2744" : "#6b7280",
                  background: "transparent",
                  border: "none",
                  cursor: "pointer",
                  transition: "color 0.2s",
                }}
              >
                {activeTab === tab.key && (
                  <motion.div
                    layoutId="tab-indicator"
                    className="absolute inset-0 rounded-md"
                    style={{ background: "#ffffff", boxShadow: "0 1px 3px rgba(0,0,0,0.1)" }}
                    transition={{ type: "spring", stiffness: 380, damping: 32 }}
                  />
                )}
                <span className="relative z-10">{tab.label}</span>
              </button>
            ))}
          </div>

          {/* Form panels */}
          <AnimatePresence mode="wait">
            {/* ── Password Login ── */}
            {activeTab === "password" && (
              <motion.div
                key="password"
                variants={formVariants}
                initial="initial"
                animate="animate"
                exit="exit"
                transition={{ duration: 0.22, ease: "easeInOut" }}
                className="flex flex-col gap-4"
              >
                <div className="flex flex-col gap-1.5">
                  <label style={{ fontSize: "13px", fontWeight: 500, color: "#374151" }}>手机号</label>
                  <AnimatedInput focused={phoneFocused}>
                    <span style={{ fontSize: "14px", color: "#9ca3af", borderRight: "1px solid #e5e7eb", paddingRight: "10px" }}>+86</span>
                    <input
                      type="tel"
                      placeholder="请输入手机号"
                      maxLength={11}
                      value={phone}
                      onChange={(e) => setPhone(e.target.value.replace(/\D/g, ""))}
                      onFocus={() => setPhoneFocused(true)}
                      onBlur={() => setPhoneFocused(false)}
                      className="flex-1 outline-none bg-transparent"
                      style={{ fontSize: "14px", color: "#111827" }}
                    />
                  </AnimatedInput>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label style={{ fontSize: "13px", fontWeight: 500, color: "#374151" }}>密码</label>
                  <AnimatedInput focused={passFocused}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                    <input
                      type={showPassword ? "text" : "password"}
                      placeholder="请输入密码"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      onFocus={() => setPassFocused(true)}
                      onBlur={() => setPassFocused(false)}
                      className="flex-1 outline-none bg-transparent"
                      style={{ fontSize: "14px", color: "#111827" }}
                    />
                    <motion.button
                      onClick={() => setShowPassword(!showPassword)}
                      whileTap={{ scale: 0.85 }}
                      style={{ background: "none", border: "none", cursor: "pointer", padding: 0, color: "#9ca3af" }}
                    >
                      <AnimatePresence mode="wait">
                        {showPassword ? (
                          <motion.svg key="hide" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
                            initial={{ opacity: 0, rotate: -10 }} animate={{ opacity: 1, rotate: 0 }} exit={{ opacity: 0, rotate: 10 }} transition={{ duration: 0.15 }}>
                            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                            <line x1="1" y1="1" x2="23" y2="23"/>
                          </motion.svg>
                        ) : (
                          <motion.svg key="show" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
                            initial={{ opacity: 0, rotate: 10 }} animate={{ opacity: 1, rotate: 0 }} exit={{ opacity: 0, rotate: -10 }} transition={{ duration: 0.15 }}>
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                          </motion.svg>
                        )}
                      </AnimatePresence>
                    </motion.button>
                  </AnimatedInput>
                </div>

                <div className="flex items-center justify-between">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={agreed}
                      onChange={(e) => setAgreed(e.target.checked)}
                      className="rounded"
                      style={{ accentColor: "#1a2744", width: "15px", height: "15px" }}
                    />
                    <span style={{ fontSize: "12px", color: "#6b7280" }}>记住我</span>
                  </label>
                  <motion.button
                    whileHover={{ color: "#1a2744" }}
                    style={{ fontSize: "12px", color: "#2c4a8a", background: "none", border: "none", cursor: "pointer" }}
                  >
                    忘记密码？
                  </motion.button>
                </div>

                <motion.button
                  className="w-full py-3 rounded-lg mt-1"
                  style={{
                    background: "linear-gradient(135deg, #1a2744 0%, #2c4a8a 100%)",
                    color: "white",
                    border: "none",
                    cursor: phone.length === 11 && password.length >= 6 ? "pointer" : "not-allowed",
                    fontSize: "15px",
                    fontWeight: 600,
                    letterSpacing: "0.02em",
                    opacity: phone.length === 11 && password.length >= 6 ? 1 : 0.55,
                  }}
                  whileHover={phone.length === 11 && password.length >= 6 ? { scale: 1.015, boxShadow: "0 6px 20px rgba(26,39,68,0.35)" } : {}}
                  whileTap={phone.length === 11 && password.length >= 6 ? { scale: 0.98 } : {}}
                  transition={{ type: "spring", stiffness: 400, damping: 20 }}
                >
                  登录
                </motion.button>
              </motion.div>
            )}

            {/* ── SMS Login ── */}
            {activeTab === "sms" && (
              <motion.div
                key="sms"
                variants={formVariants}
                initial="initial"
                animate="animate"
                exit="exit"
                transition={{ duration: 0.22, ease: "easeInOut" }}
                className="flex flex-col gap-4"
              >
                <div className="flex flex-col gap-1.5">
                  <label style={{ fontSize: "13px", fontWeight: 500, color: "#374151" }}>手机号</label>
                  <AnimatedInput focused={smsPhoneFocused}>
                    <span style={{ fontSize: "14px", color: "#9ca3af", borderRight: "1px solid #e5e7eb", paddingRight: "10px" }}>+86</span>
                    <input
                      type="tel"
                      placeholder="请输入手机号"
                      maxLength={11}
                      value={smsPhone}
                      onChange={(e) => setSmsPhone(e.target.value.replace(/\D/g, ""))}
                      onFocus={() => setSmsPhoneFocused(true)}
                      onBlur={() => setSmsPhoneFocused(false)}
                      className="flex-1 outline-none bg-transparent"
                      style={{ fontSize: "14px", color: "#111827" }}
                    />
                  </AnimatedInput>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label style={{ fontSize: "13px", fontWeight: 500, color: "#374151" }}>验证码</label>
                  <div className="flex gap-3">
                    <AnimatedInput focused={codeFocused}>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <rect x="2" y="4" width="20" height="16" rx="2"/>
                        <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
                      </svg>
                      <input
                        type="text"
                        placeholder="请输入验证码"
                        maxLength={6}
                        value={code}
                        onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                        onFocus={() => setCodeFocused(true)}
                        onBlur={() => setCodeFocused(false)}
                        className="flex-1 outline-none bg-transparent"
                        style={{ fontSize: "14px", color: "#111827" }}
                      />
                    </AnimatedInput>
                    <motion.button
                      onClick={handleSendCode}
                      className="rounded-lg px-4 whitespace-nowrap"
                      style={{
                        fontSize: "13px",
                        fontWeight: 500,
                        border: "1.5px solid",
                        borderColor: countdown > 0 || smsPhone.length !== 11 ? "#e5e7eb" : "#2c4a8a",
                        color: countdown > 0 || smsPhone.length !== 11 ? "#9ca3af" : "#2c4a8a",
                        background: "white",
                        cursor: countdown > 0 || smsPhone.length !== 11 ? "not-allowed" : "pointer",
                        minWidth: "100px",
                      }}
                      whileHover={countdown === 0 && smsPhone.length === 11 ? { background: "#f0f4ff" } : {}}
                      whileTap={countdown === 0 && smsPhone.length === 11 ? { scale: 0.96 } : {}}
                      transition={{ duration: 0.15 }}
                    >
                      {countdown > 0 ? `${countdown}s 后重发` : "获取验证码"}
                    </motion.button>
                  </div>
                </div>

                <p style={{ fontSize: "12px", color: "#9ca3af", marginTop: "-4px" }}>
                  未注册的手机号验证后将自动创建账号
                </p>

                <motion.button
                  className="w-full py-3 rounded-lg mt-2"
                  style={{
                    background: "linear-gradient(135deg, #1a2744 0%, #2c4a8a 100%)",
                    color: "white",
                    border: "none",
                    cursor: smsPhone.length === 11 && code.length === 6 ? "pointer" : "not-allowed",
                    fontSize: "15px",
                    fontWeight: 600,
                    letterSpacing: "0.02em",
                    opacity: smsPhone.length === 11 && code.length === 6 ? 1 : 0.55,
                  }}
                  whileHover={smsPhone.length === 11 && code.length === 6 ? { scale: 1.015, boxShadow: "0 6px 20px rgba(26,39,68,0.35)" } : {}}
                  whileTap={smsPhone.length === 11 && code.length === 6 ? { scale: 0.98 } : {}}
                  transition={{ type: "spring", stiffness: 400, damping: 20 }}
                >
                  登录 / 注册
                </motion.button>
              </motion.div>
            )}

            {/* ── WeChat QR ── */}
            {activeTab === "wechat" && (
              <motion.div
                key="wechat"
                variants={formVariants}
                initial="initial"
                animate="animate"
                exit="exit"
                transition={{ duration: 0.22, ease: "easeInOut" }}
                className="flex flex-col items-center gap-5"
              >
                <motion.div
                  className="relative p-5 rounded-2xl overflow-hidden"
                  style={{ border: "1.5px solid #e5e7eb", background: "#fafafa" }}
                  whileHover={{ boxShadow: "0 8px 24px rgba(0,0,0,0.08)" }}
                  transition={{ duration: 0.2 }}
                >
                  {/* QR Code */}
                  <div className="w-[180px] h-[180px] relative">
                    <svg viewBox="0 0 180 180" width="180" height="180" xmlns="http://www.w3.org/2000/svg">
                      <rect width="180" height="180" fill="white"/>
                      <rect x="10" y="10" width="50" height="50" rx="4" fill="#111827"/>
                      <rect x="18" y="18" width="34" height="34" rx="2" fill="white"/>
                      <rect x="24" y="24" width="22" height="22" rx="1" fill="#111827"/>
                      <rect x="120" y="10" width="50" height="50" rx="4" fill="#111827"/>
                      <rect x="128" y="18" width="34" height="34" rx="2" fill="white"/>
                      <rect x="134" y="24" width="22" height="22" rx="1" fill="#111827"/>
                      <rect x="10" y="120" width="50" height="50" rx="4" fill="#111827"/>
                      <rect x="18" y="128" width="34" height="34" rx="2" fill="white"/>
                      <rect x="24" y="134" width="22" height="22" rx="1" fill="#111827"/>
                      {[
                        [70,10],[80,10],[90,10],[100,10],[110,10],
                        [70,20],[90,20],[100,20],
                        [80,30],[100,30],[110,30],
                        [70,40],[80,40],[90,40],[110,40],
                        [70,50],[90,50],[100,50],
                        [10,70],[30,70],[50,70],[70,70],[90,70],[110,70],[130,70],[150,70],[170,70],
                        [10,80],[50,80],[70,80],[110,80],[130,80],[170,80],
                        [10,90],[20,90],[40,90],[60,90],[90,90],[110,90],[140,90],[160,90],
                        [10,100],[30,100],[50,100],[70,100],[100,100],[120,100],[150,100],
                        [20,110],[40,110],[60,110],[80,110],[100,110],[130,110],[160,110],[170,110],
                        [70,120],[80,120],[100,120],[120,120],[140,120],[160,120],
                        [70,130],[90,130],[110,130],[130,130],[150,130],[170,130],
                        [80,140],[100,140],[110,140],[140,140],[160,140],
                        [70,150],[90,150],[110,150],[120,150],[150,150],[170,150],
                        [80,160],[100,160],[130,160],[140,160],[160,160],
                        [70,170],[90,170],[100,170],[120,170],[150,170],
                      ].map(([x, y], i) => (
                        <rect key={i} x={x} y={y} width="8" height="8" fill="#111827"/>
                      ))}
                    </svg>

                    {/* Scanner line (only when not scanned) */}
                    {!qrScanned && <ScannerLine />}

                    {/* WeChat icon */}
                    <motion.div
                      className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-10 h-10 rounded-lg flex items-center justify-center"
                      style={{ background: "#07C160" }}
                      animate={{ scale: [1, 1.08, 1] }}
                      transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
                    >
                      <svg width="22" height="22" viewBox="0 0 24 24" fill="white">
                        <path d="M8.4 4C4.8 4 2 6.4 2 9.4c0 1.7.9 3.2 2.3 4.2l-.6 1.9 2.1-1.1c.7.2 1.4.3 2.2.3h.4c-.1-.4-.2-.8-.2-1.2 0-2.7 2.6-4.9 5.8-4.9h.2C13.6 6 11.2 4 8.4 4zM6 8.5c-.4 0-.8-.3-.8-.8s.3-.8.8-.8.8.3.8.8-.4.8-.8.8zm4.7 0c-.4 0-.8-.3-.8-.8s.3-.8.8-.8.8.3.8.8-.4.8-.8.8z"/>
                        <path d="M22 13.8c0-2.6-2.5-4.8-5.5-4.8s-5.5 2.1-5.5 4.8c0 2.6 2.5 4.8 5.5 4.8.7 0 1.4-.1 2-.3l1.8.9-.5-1.6c1.3-1 2.2-2.3 2.2-3.8zm-7.3-.5c-.4 0-.7-.3-.7-.7s.3-.7.7-.7.7.3.7.7-.3.7-.7.7zm3.6 0c-.4 0-.7-.3-.7-.7s.3-.7.7-.7.7.3.7.7-.3.7-.7.7z"/>
                      </svg>
                    </motion.div>

                    {/* Scanned overlay */}
                    <AnimatePresence>
                      {qrScanned && (
                        <motion.div
                          className="absolute inset-0 flex flex-col items-center justify-center rounded-lg"
                          style={{ background: "rgba(255,255,255,0.96)" }}
                          initial={{ opacity: 0, scale: 0.9 }}
                          animate={{ opacity: 1, scale: 1 }}
                          exit={{ opacity: 0, scale: 0.9 }}
                          transition={{ duration: 0.25 }}
                        >
                          <motion.div
                            className="w-12 h-12 rounded-full flex items-center justify-center mb-2"
                            style={{ background: "#07C160" }}
                            initial={{ scale: 0 }}
                            animate={{ scale: 1 }}
                            transition={{ type: "spring", stiffness: 400, damping: 18, delay: 0.1 }}
                          >
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                              <polyline points="20 6 9 17 4 12"/>
                            </svg>
                          </motion.div>
                          <p style={{ fontSize: "13px", color: "#374151" }}>扫描成功</p>
                          <p style={{ fontSize: "12px", color: "#9ca3af" }}>请在手机端确认</p>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </div>
                </motion.div>

                <div className="text-center">
                  <p style={{ fontSize: "14px", color: "#374151", marginBottom: "4px" }}>
                    使用 <span style={{ color: "#07C160", fontWeight: 600 }}>微信</span> 扫描二维码登录
                  </p>
                  <p style={{ fontSize: "12px", color: "#9ca3af" }}>
                    打开微信 → 扫一扫 → 扫描上方二维码
                  </p>
                </div>

                <motion.button
                  onClick={() => setQrScanned(!qrScanned)}
                  className="px-6 py-2 rounded-lg"
                  style={{
                    fontSize: "13px",
                    color: "#07C160",
                    border: "1.5px solid #07C160",
                    background: "white",
                    cursor: "pointer",
                  }}
                  whileHover={{ background: "#f0fdf4" }}
                  whileTap={{ scale: 0.96 }}
                  transition={{ duration: 0.15 }}
                >
                  {qrScanned ? "重置二维码" : "模拟扫码（演示）"}
                </motion.button>

                <div className="flex items-center gap-3 w-full">
                  <div className="flex-1 h-px" style={{ background: "#e5e7eb" }} />
                  <span style={{ fontSize: "12px", color: "#9ca3af" }}>或选择其他方式</span>
                  <div className="flex-1 h-px" style={{ background: "#e5e7eb" }} />
                </div>

                <div className="flex gap-3 w-full">
                  {[
                    { label: "密码登录", tab: "password" as LoginTab },
                    { label: "验证码登录", tab: "sms" as LoginTab },
                  ].map(({ label, tab }) => (
                    <motion.button
                      key={tab}
                      onClick={() => setActiveTab(tab)}
                      className="flex-1 py-2.5 rounded-lg"
                      style={{ fontSize: "13px", color: "#374151", border: "1.5px solid #e5e7eb", background: "white", cursor: "pointer" }}
                      whileHover={{ borderColor: "#2c4a8a", color: "#2c4a8a" }}
                      whileTap={{ scale: 0.97 }}
                      transition={{ duration: 0.15 }}
                    >
                      {label}
                    </motion.button>
                  ))}
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Agreement */}
          <AnimatePresence>
            {activeTab !== "wechat" && (
              <motion.p
                className="mt-5 text-center"
                style={{ fontSize: "12px", color: "#9ca3af" }}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2 }}
              >
                登录即表示同意{" "}
                <motion.button whileHover={{ color: "#1a2744" }} style={{ color: "#2c4a8a", background: "none", border: "none", cursor: "pointer", fontSize: "12px" }}>
                  用户议
                </motion.button>
                {" "}与{" "}
                <motion.button whileHover={{ color: "#1a2744" }} style={{ color: "#2c4a8a", background: "none", border: "none", cursor: "pointer", fontSize: "12px" }}>
                  隐私政策
                </motion.button>
              </motion.p>
            )}
          </AnimatePresence>

          {/* Register link */}
          <div className="mt-6 pt-6 text-center" style={{ borderTop: "1px solid #f3f4f6" }}>
            <p style={{ fontSize: "13px", color: "#6b7280" }}>
              还没有账号？{" "}
              <motion.button
                whileHover={{ color: "#2c4a8a" }}
                style={{ color: "#1a2744", fontWeight: 600, background: "none", border: "none", cursor: "pointer", fontSize: "13px" }}
                transition={{ duration: 0.15 }}
              >
                免费注册
              </motion.button>
            </p>
          </div>
        </motion.div>
      </motion.div>
    </div>
  );
}