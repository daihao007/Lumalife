import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import type { Role } from "../types";

export default function Login({ onLogin, onRegister }: { onLogin: (phone: string, password: string) => void; onRegister: (phone: string, password: string, nickname: string, role: Role) => void }) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [nickname, setNickname] = useState("新同学");
  const [role, setRole] = useState<Role>("USER");
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  function switchMode(nextMode: "login" | "register") {
    setMode(nextMode);
    setError("");
    if (nextMode === "login") {
      setPhone("");
      setPassword("");
      setConfirmPassword("");
      return;
    }
    setPhone("");
    setPassword("");
    setConfirmPassword("");
    setNickname(role === "MERCHANT_ADMIN" ? "新店主" : "新同学");
  }

  function switchRole(nextRole: Role) {
    setRole(nextRole);
    setPhone("");
    setNickname(nextRole === "MERCHANT_ADMIN" ? "新店主" : "新同学");
  }

  function passwordStrength(value: string) {
    let score = 0;
    if (value.length >= 6) score += 1;
    if (value.length >= 10) score += 1;
    if (/[a-zA-Z]/.test(value) && /\d/.test(value)) score += 1;
    if (/[^a-zA-Z0-9]/.test(value)) score += 1;
    if (score <= 1) return "弱";
    if (score <= 3) return "中";
    return "强";
  }

  function submit() {
    setError("");
    if (mode === "login") {
      onLogin(phone, password);
      return;
    }
    if (password.length < 6) {
      setError("请输入至少6位的密码");
      return;
    }
    if (password !== confirmPassword) {
      setError("请输入一致的密码");
      return;
    }
    onRegister(phone, password, nickname, role);
  }

  return <div className="panel login">
    <div className="segmented">
      <button data-testid="login-mode" type="button" className={mode === "login" ? "active" : ""} onClick={() => switchMode("login")}>登录</button>
      <button data-testid="register-mode" type="button" className={mode === "register" ? "active" : ""} onClick={() => switchMode("register")}>注册</button>
    </div>
    <h2>{mode === "login" ? "演示登录" : "账号注册"}</h2>
    <input data-testid="auth-phone" value={phone} onChange={e => setPhone(e.target.value)} placeholder="请输入用户名" />
    <div className="password-row">
      <input data-testid="auth-password" value={password} onChange={e => setPassword(e.target.value)} type={showPassword ? "text" : "password"} placeholder="请输入密码" />
      <button type="button" title={showPassword ? "隐藏密码" : "显示密码"} aria-label={showPassword ? "隐藏密码" : "显示密码"} onClick={() => setShowPassword(value => !value)}>
        {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
        {showPassword ? "隐藏密码" : "显示密码"}
      </button>
    </div>
    {mode === "register" && <>
      <input data-testid="auth-confirm-password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} type={showPassword ? "text" : "password"} placeholder="请确认密码" />
      <div className={`strength strength-${passwordStrength(password)}`}>
        <span>密码强度</span><b>{passwordStrength(password)}</b><i />
      </div>
      <input data-testid="auth-nickname" value={nickname} onChange={e => setNickname(e.target.value)} placeholder="请输入昵称" />
      <div className="segmented role-segmented">
        <button type="button" className={role === "USER" ? "active" : ""} onClick={() => switchRole("USER")}>用户账号</button>
        <button type="button" className={role === "MERCHANT_ADMIN" ? "active" : ""} onClick={() => switchRole("MERCHANT_ADMIN")}>商家账号</button>
      </div>
    </>}
    {error && <p className="form-error">{error}</p>}
    <button data-testid="auth-submit" className="primary" onClick={submit}>
      {mode === "login" ? "进入系统" : "创建账号"}
    </button>
  </div>;
}
