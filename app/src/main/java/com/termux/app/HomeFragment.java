package com.termux.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 简化 UI Fragment（ChatGPT 风格）。
 *
 * 完成检测策略（优先级依次）：
 *   1. 检测 Claude Code "> " 提示符重新出现 → 立即结束（最可靠）
 *   2. 连续 20 次 poll（10 秒）无新内容 → 超时兜底
 *
 * 过滤策略（尽量少过滤，保留 Claude 实际回复）：
 *   - 去 ANSI/VT 转义序列
 *   - 去 bash 提示符（user@host:path#）
 *   - 去含 Unicode 制表符（U+2500-U+257F）的行 → Claude Code TUI 边框，非内容
 *   - 去超长纯 ASCII 边框线（>= 20 chars 且仅含 -=+| ）→ TUI 分隔线
 *     短的 --- 或 | 保留，它们可能是 markdown HR / 表格
 *   - 去已知 TUI 状态行（"esc to interrupt" 等）
 *   - 去 Braille 旋转器行（⠋ Thinking... 等 Claude Code 思考动画）
 *   - 去 "> " 输入提示符（已单独用于完成检测）
 */
public class HomeFragment extends Fragment {

    private static final int POLL_MS = 500;
    /**
     * 足够大，使 getRecentTerminalLines 实际上返回完整的 transcript（无滑动窗口）。
     * 若窗口滑动，baseline 会超出新窗口的行数 → extractDelta 永远返回 "" → "…" 永远卡住。
     * 设为 10000 后，任何实际 Claude 会话都不会超过这个数，baseline 变成绝对位置，不再有此问题。
     */
    private static final int MAX_LINES = 10000;
    /**
     * 兜底超时：60 × 500ms = 30 秒有实际内容后无新增时强制结束。
     * 仅在 delta 非空时计数（纯思考阶段 delta 为空，计数器不增长，避免过早超时）。
     * 正常情况下 "> " 提示符检测会先触发，无需等满 30 秒。
     */
    private static final int STABLE_THRESHOLD = 60;

    /**
     * 综合 ANSI/VT 转义序列正则，四个分支：
     *
     *  1. CSI：ESC [ {参数字节 0x20-0x3F}* {最终字节 0x40-0x7E}
     *     参数字节范围包含 ?: ; 0-9 等，覆盖 DEC 私有模式（ESC[?2026h 等）。
     *     旧模式 \\[[;\\d]*[A-Za-z] 不含 ?，导致 ESC[?2026h 残留为乱文。
     *
     *  2. OSC：ESC ] ... BEL 或 ESC \
     *
     *  3. DCS/SOS/PM/APC：ESC [PX^_] ... ST
     *
     *  4. 兜底：ESC + 任意单字符（2 字符转义序列）
     */
    private static final Pattern ANSI = Pattern.compile(
        "\u001B\\[[ -?]*[@-~]" +                       // 1. CSI（含 ?-前缀私有模式）
        "|\u001B\\].*?(?:\u0007|\u001B\\\\)" +          // 2. OSC
        "|\u001B[PX^_].*?(?:\u0007|\u001B\\\\)" +       // 3. DCS/SOS/PM/APC
        "|\u001B.",                                      // 4. 其余双字符序列
        Pattern.DOTALL
    );

    /** bash 提示符行：root@ubuntu:~# */
    private static final Pattern BASH_PROMPT = Pattern.compile(
        "^[a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+:.*[#$]\\s*$");

    /**
     * 含 Unicode 制表符的行 → 纯 TUI 边框字符，绝对不是 Claude 内容。
     * 用 find() 而非 matches()，只要行内有一个就过滤。
     */
    private static final Pattern HAS_BOX_CHAR = Pattern.compile("[\\u2500-\\u257F]");

    /**
     * 纯 ASCII 边框字符行（仅含 - = + | ）。
     * 只在长度 >= 20 时过滤，避免误删 markdown 的 --- HR 或 | 列分隔。
     */
    private static final Pattern ASCII_SEP = Pattern.compile("^[-=+|]+$");

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mPoller;

    private RecyclerView mRecycler;
    private ChatAdapter mAdapter;
    private final List<ChatMessage> mMessages = new ArrayList<>();

    private TextView mStatusText;
    private EditText mInputEdit;

    // ── 状态机 ──────────────────────────────────────────────────────────────
    private boolean mWaitingResponse   = false;
    private int     mBaselineLineCount = 0;
    private String  mLastDelta         = "";
    private int     mStableCount       = 0;

    // =========================================================================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRecycler = view.findViewById(R.id.chat_recycler);
        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        lm.setStackFromEnd(true);
        mRecycler.setLayoutManager(lm);
        mAdapter = new ChatAdapter(mMessages);
        mRecycler.setAdapter(mAdapter);

        mStatusText = view.findViewById(R.id.home_status_text);
        mInputEdit  = view.findViewById(R.id.home_input_edit);

        MaterialButton btnSend       = view.findViewById(R.id.home_send_btn);
        MaterialButton btnEnter      = view.findViewById(R.id.btn_enter);
        MaterialButton btnStart      = view.findViewById(R.id.btn_start_claude);
        MaterialButton btnStop       = view.findViewById(R.id.btn_stop_claude);
        MaterialButton btnRestart    = view.findViewById(R.id.btn_restart_claude);
        MaterialButton btnNewSession = view.findViewById(R.id.btn_new_session);

        btnSend.setOnClickListener(v -> sendOrConfirm());
        btnEnter.setOnClickListener(v -> terminal("\r"));
        btnStart.setOnClickListener(v -> terminal("claude\r"));
        btnStop.setOnClickListener(v -> terminal("\003"));
        btnRestart.setOnClickListener(v -> {
            terminal("\003");
            mHandler.postDelayed(() -> terminal("claude\r"), 1000);
        });
        btnNewSession.setOnClickListener(v -> {
            TermuxActivity a = act();
            if (a != null) a.addNewSessionFromHome();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        startPolling();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPolling();
    }

    // =========================================================================
    // 发送
    // =========================================================================

    private void sendOrConfirm() {
        String text = mInputEdit.getText().toString().trim();

        if (text.isEmpty()) {
            terminal("\r");
            return;
        }

        TermuxActivity a = act();

        // ① 先记录 baseline（发送之前！），避免把发送前的内容算进回复
        if (a != null) {
            mBaselineLineCount = lineCount(filter(a.getRecentTerminalLines(MAX_LINES)));
        }

        // ② 用户气泡
        mAdapter.addMessage(ChatMessage.user(text));
        scrollToBottom();

        // ③ 发文本 + \r（80ms 间隔，防 readline 丢字符）
        terminal(text);
        mHandler.postDelayed(() -> terminal("\r"), 80);
        mInputEdit.setText("");

        // ④ 占位 ASSISTANT 气泡
        mAdapter.addMessage(ChatMessage.assistant("…"));
        scrollToBottom();

        mWaitingResponse = true;
        mStableCount     = 0;
        mLastDelta       = "";
    }

    private void terminal(String text) {
        TermuxActivity a = act();
        if (a != null) a.sendTerminalInput(text);
    }

    // =========================================================================
    // 轮询
    // =========================================================================

    private void startPolling() {
        mPoller = new Runnable() {
            @Override
            public void run() {
                poll();
                mHandler.postDelayed(this, POLL_MS);
            }
        };
        mHandler.post(mPoller);
    }

    private void stopPolling() {
        if (mPoller != null) {
            mHandler.removeCallbacks(mPoller);
            mPoller = null;
        }
    }

    private void poll() {
        TermuxActivity a = act();
        if (a == null) return;

        boolean active = a.hasActiveSession();
        mStatusText.setText(active ? "● 会话活跃" : "● 等待中");
        mStatusText.setTextColor(active ? 0xFF2E7D32 : 0xFF888888);

        if (!mWaitingResponse) return;

        String raw      = a.getRecentTerminalLines(MAX_LINES);
        String filtered = filter(raw);
        String delta    = extractDelta(filtered);
        // 检测 Claude Code "> " 提示符：仅在已有新内容时才触发，
        // 避免发送后、Claude 开始处理前的空窗期误判。
        boolean claudeDone = !mLastDelta.isEmpty() && isClaudeDone(raw);

        if (!delta.equals(mLastDelta)) {
            // 有新内容：重置稳定计数，实时更新气泡（流式显示）
            mStableCount = 0;
            mLastDelta   = delta;
            if (!delta.isEmpty()) {
                mAdapter.updateLastAssistant(delta);
                scrollToBottom();
            }
        } else if (!delta.isEmpty()) {
            // 仅在 delta 非空时才计数：纯思考阶段所有噪声均被过滤，delta 为空；
            // 若此时也计数，10 秒后超时会过早结束等待，导致实际回复被漏掉。
            mStableCount++;
        }

        // 终止条件：检测到 "> " 提示符（Claude 已就绪）或超时兜底
        if (claudeDone || mStableCount >= STABLE_THRESHOLD) {
            if (delta.isEmpty()) dropPlaceholder();
            else mAdapter.updateLastAssistant(delta);
            mWaitingResponse = false;
            mStableCount     = 0;
        }
    }

    // =========================================================================
    // Claude 完成检测
    // =========================================================================

    /**
     * 检测 Claude Code 是否已完成回复，显示出 "> " 输入提示符。
     *
     * 从末尾往前扫描（跳过空行），遇到第一个非空行：
     *   - 是 ">" 或 "> " → Claude 正在等待下一条输入 → 返回 true
     *   - 其他内容       → 仍在输出            → 返回 false
     *
     * 注意：此方法在 raw 字符串上操作（ANSI 去除后），不依赖 filter() 的去噪逻辑。
     */
    private boolean isClaudeDone(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        String s = ANSI.matcher(raw).replaceAll("").replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = s.split("\n", -1);
        for (int i = lines.length - 1; i >= 0; i--) {
            String t = lines[i].trim();
            if (t.isEmpty()) continue;
            // "> " 或 ">" 且后面没有实际内容 → Claude Code 等待输入提示符
            return t.equals(">") || t.equals("> ");
        }
        return false;
    }

    // =========================================================================
    // 过滤 + 提取
    // =========================================================================

    /**
     * 过滤终端文本，尽量保留 Claude 实际回复，去掉 TUI 装饰。
     */
    private String filter(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        // 1. 去 ANSI/VT 转义序列（含 ?-前缀 DEC 私有模式）
        String s = ANSI.matcher(raw).replaceAll("");
        // 2. 处理 \r：\r\n → \n（标准 Windows 换行），孤立 \r → \n（PTY 用 CR 覆写当前行，
        //    语义等价于换行；若直接丢弃，"2.蓝色\r3.绿色" 会被拼成同一行）
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        // 3. 去残余控制字符（0x00-0x08, 0x0B-0x0C, 0x0E-0x1F, 0x7F）
        //    保留 \n（0x0A）和 \t（0x09）
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            // 剥离 Claude Code 的 UI 前缀符号：
            //   ● (U+25CF) = 当前条目/回复指示符  → 内容在其后
            //   ✦ (U+2726) = 状态指示符           → 后面是状态文本（会被 isNoise 过滤）
            //   ❯ (U+276F) = 选择列表当前项指针
            //   ⊛ (U+229B) = 选中项指示符（circled asterisk）
            //   ◉ (U+25C9) = 已选项（fisheye）
            //   ○ (U+25CB) = 未选项（white circle）
            //   · (U+00B7) = TUI 中点 bullet（动画帧 "·elizin" 等）
            //   ⁂ (U+2042) = asterism / TUI 装饰符
            //   * (U+002A) = 动画帧前缀（"*utng" 等）→ 剥离后 "utng"(4 字符) 被短行规则过滤
            while (t.length() > 0 && (
                    t.charAt(0) == '\u25CF' ||  // ●
                    t.charAt(0) == '\u2726' ||  // ✦
                    t.charAt(0) == '\u276F' ||  // ❯
                    t.charAt(0) == '\u229B' ||  // ⊛
                    t.charAt(0) == '\u25C9' ||  // ◉
                    t.charAt(0) == '\u25CB' ||  // ○
                    t.charAt(0) == '\u00B7' ||  // ·
                    t.charAt(0) == '\u2042' ||  // ⁂
                    t.charAt(0) == '*'          // *
            )) {
                t = t.substring(1).trim();
            }
            if (isNoise(t)) continue;
            sb.append(t).append("\n");
        }
        return sb.toString();   // 不 trim 末尾，保持行数稳定
    }

    private boolean isNoise(String t) {
        if (t.isEmpty()) return true;

        // bash 提示符（user@host:path# 或 user@host:path$）
        if (BASH_PROMPT.matcher(t).matches()) return true;

        // 含 Unicode 制表符 → Claude Code TUI 边框，绝不是内容
        if (HAS_BOX_CHAR.matcher(t).find()) return true;

        // 超长纯 ASCII 边框线（>= 20 chars）→ TUI 分隔线
        if (t.length() >= 20 && ASCII_SEP.matcher(t).matches()) return true;

        // Braille 旋转器（U+2800-U+28FF）→ Claude Code "⠋ Thinking..." 动画
        if (!t.isEmpty() && t.charAt(0) >= '\u2800' && t.charAt(0) <= '\u28FF') return true;

        String lower = t.toLowerCase();

        // "esc to interrupt"：匹配有空格和无空格（esctointerrupt）两种形式
        if (lower.replaceAll("\\s", "").contains("esctointerrupt")) return true;
        if (lower.contains("escape to interrupt")) return true;

        // Claude Code 特有的状态/工具消息
        if (lower.contains("fiddle-faddling")) return true;   // 思考动画文字
        if (lower.equals("for shortcuts"))     return true;   // 快捷键提示

        // "(thinking)" 标签 → 思考过程动画帧（可能夹杂在动画字符中间）
        // 例：(thinking)、g(thinking)、·n(thinking)、i…(thinking) 等
        if (lower.contains("(thinking)")) return true;

        // 单词 + "..." → Claude Code 思考动画词（Ionizing...、Cooking...、Analyzing... 等）
        // 条件：不含空格（单词），以 ... 结尾 → 思考状态行，非内容
        if (!t.contains(" ") && t.endsWith("...")) return true;

        // Tip 提示行（└ Tip: ... ）
        if (t.startsWith("\u2514")) return true;   // └ (U+2514)
        if (lower.contains("tip:"))  return true;

        // Claude Code 输入提示符（已单独用于完成检测，不展示给用户）
        if (t.equals(">") || t.equals("> ")) return true;

        // 交互式选择菜单导航提示行（"Enter to select · ↑/↓ to navigate · Esc to cancel"）
        // 去空格后包含 "toselect" 或 "tonavigate" → 纯导航提示，不是内容
        if (lower.replaceAll("\\s", "").contains("toselect")) return true;
        if (lower.replaceAll("\\s", "").contains("tonavigate")) return true;

        // TUI 动画帧残骸：≤ 4 字符且不含空格，且不含数字或中文
        // Claude Code 进度动画用 cursor-up + overwrite，去 ANSI 后每帧成孤立短行
        // "di", "*dn", "Fid", "lg", "n", "*g…" 等均符合此条件
        // 保留含数字（选项编号）或中文的短串，避免误删选项内容
        if (t.length() <= 4 && !t.contains(" ") && !t.matches(".*[\\d\\u4E00-\\u9FFF].*")) return true;

        return false;
    }

    /**
     * 取 baseline 行之后的所有新行，拼接为回复文本。
     */
    private String extractDelta(String filtered) {
        if (filtered.isEmpty()) return "";
        String[] lines = filtered.split("\n", -1);
        if (lines.length <= mBaselineLineCount) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = mBaselineLineCount; i < lines.length; i++) {
            String t = lines[i].trim();
            if (!t.isEmpty()) sb.append(t).append("\n");
        }
        return sb.toString().trim();
    }

    private int lineCount(String filtered) {
        if (filtered.isEmpty()) return 0;
        return filtered.split("\n", -1).length;
    }

    // =========================================================================
    // 辅助
    // =========================================================================

    private void scrollToBottom() {
        mRecycler.post(() -> {
            int last = mAdapter.getItemCount() - 1;
            if (last >= 0) mRecycler.smoothScrollToPosition(last);
        });
    }

    private void dropPlaceholder() {
        if (!mMessages.isEmpty()) {
            ChatMessage last = mMessages.get(mMessages.size() - 1);
            if (last.type == ChatMessage.Type.ASSISTANT
                    && (last.content.equals("…") || last.content.trim().isEmpty())) {
                int idx = mMessages.size() - 1;
                mMessages.remove(idx);
                mAdapter.notifyItemRemoved(idx);
            }
        }
    }

    @Nullable
    private TermuxActivity act() {
        return (getActivity() instanceof TermuxActivity)
            ? (TermuxActivity) getActivity() : null;
    }
}
