# -*- coding: utf-8 -*-
"""用 matplotlib 把压测结果画成矢量图(SVG + PDF)+ 高清 PNG。
数据来源: results/matrix_async_100.json, matrix_async_2000.json, matrix_sync_2000.json
输出目录: D:\\software_work\\diagrams
"""
import json, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

# ---------- 中文字体 ----------
def setup_font():
    candidates = [
        r"C:\Windows\Fonts\msyh.ttc",      # 微软雅黑
        r"C:\Windows\Fonts\msyhbd.ttc",
        r"C:\Windows\Fonts\simhei.ttf",    # 黑体
    ]
    fam = None
    for p in candidates:
        if os.path.exists(p):
            font_manager.fontManager.addfont(p)
            fam = font_manager.FontProperties(fname=p).get_name()
            break
    if fam:
        plt.rcParams["font.sans-serif"] = [fam]
    plt.rcParams["axes.unicode_minus"] = False
setup_font()

BRAND = "#a4233a"; BRAND2 = "#8c1c30"
AMBER = "#e8973a"; GREEN = "#2e9e6b"; GREY = "#9aa0aa"; INK = "#1d1d1f"
OUTDIR = r"D:\software_work\diagrams"
RES = os.path.join(os.path.dirname(__file__), "results")

def load(name):
    p = os.path.join(RES, name)
    return json.load(open(p, encoding="utf-8")) if os.path.exists(p) else None

def save(fig, base):
    os.makedirs(OUTDIR, exist_ok=True)
    for ext in ("svg", "pdf", "png"):
        fig.savefig(os.path.join(OUTDIR, base + "." + ext),
                    bbox_inches="tight", dpi=200 if ext == "png" else None,
                    transparent=False)
    plt.close(fig)
    print("saved", base, "(svg/pdf/png)")

def style_ax(ax):
    ax.grid(axis="y", color="#e7e7ec", linewidth=1)
    ax.set_axisbelow(True)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("left", "bottom"):
        ax.spines[s].set_color("#c9ccd2")

# ===== 图 09：并发-延迟曲线 + 不超卖 =====
def chart_latency(rows):
    xs = [r["n"] for r in rows]
    avg = [r["avg"] for r in rows]; p95 = [r["p95"] for r in rows]; p99 = [r["p99"] for r in rows]
    fig, ax = plt.subplots(figsize=(8.2, 4.6))
    ax.plot(xs, avg, "-o", color=GREEN, lw=2.4, ms=7, label="平均响应")
    ax.plot(xs, p95, "-o", color=AMBER, lw=2.4, ms=7, label="P95")
    ax.plot(xs, p99, "-o", color=BRAND, lw=2.4, ms=7, label="P99")
    for x, y in zip(xs, p99):
        ax.annotate(f"{y}", (x, y), textcoords="offset points", xytext=(0, 9),
                    ha="center", fontsize=9, color=BRAND)
    style_ax(ax)
    ax.set_xlabel("并发抢课请求数 (人)"); ax.set_ylabel("响应时间 (ms)")
    ax.set_title("不同并发下的响应时间（容量=100，每档 3 遍取中位）", fontsize=13, color=INK, pad=12)
    ax.set_xticks(xs); ax.legend(frameon=False, loc="upper left")
    ax.margins(x=0.06); ax.set_ylim(bottom=0)
    # 副标题：不超卖
    ax.text(0.99, 0.04, "每一档最终落库 = 容量 100，超卖恒为 0",
            transform=ax.transAxes, ha="right", va="bottom", fontsize=10,
            color=GREEN, bbox=dict(boxstyle="round,pad=0.4", fc="#eafaf1", ec=GREEN, lw=1))
    save(fig, "09_压测延迟与不超卖")

# ===== 图 10：吞吐 vs 并发 =====
def chart_throughput_scale(rows):
    xs = [r["n"] for r in rows]; tps = [r["throughput_rps"] for r in rows]
    fig, ax = plt.subplots(figsize=(8.2, 4.6))
    bars = ax.bar([str(x) for x in xs], tps, color=BRAND, width=0.6)
    for b, v in zip(bars, tps):
        ax.annotate(f"{v}", (b.get_x()+b.get_width()/2, v), textcoords="offset points",
                    xytext=(0, 5), ha="center", fontsize=10, color=INK)
    style_ax(ax)
    ax.set_xlabel("并发抢课请求数 (人)"); ax.set_ylabel("吞吐 (req/s)")
    ax.set_title("吞吐随并发变化（容量=100，异步模式）", fontsize=13, color=INK, pad=12)
    ax.set_ylim(top=max(tps)*1.18)
    save(fig, "10_压测吞吐随并发")

# ===== 图 08：同步 vs 异步 对比 =====
def chart_sync_vs_async(async_rows, sync_rows):
    import numpy as np
    ns = sorted(set(r["n"] for r in async_rows) & set(r["n"] for r in sync_rows))
    a = {r["n"]: r for r in async_rows}; s = {r["n"]: r for r in sync_rows}
    x = np.arange(len(ns)); w = 0.36
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10.4, 4.6))
    # 左：吞吐
    ax1.bar(x - w/2, [a[n]["throughput_rps"] for n in ns], w, color=BRAND, label="异步(Kafka)")
    ax1.bar(x + w/2, [s[n]["throughput_rps"] for n in ns], w, color=GREY, label="同步(写库)")
    for i, n in enumerate(ns):
        ax1.annotate(f'{a[n]["throughput_rps"]}', (x[i]-w/2, a[n]["throughput_rps"]), textcoords="offset points", xytext=(0,4), ha="center", fontsize=9, color=INK)
        ax1.annotate(f'{s[n]["throughput_rps"]}', (x[i]+w/2, s[n]["throughput_rps"]), textcoords="offset points", xytext=(0,4), ha="center", fontsize=9, color=INK)
    style_ax(ax1); ax1.set_xticks(x); ax1.set_xticklabels([str(n) for n in ns])
    ax1.set_xlabel("并发抢课请求数 (人)"); ax1.set_ylabel("吞吐 (req/s)")
    ax1.set_title("吞吐对比", fontsize=12, color=INK); ax1.legend(frameon=False)
    # 右：P95
    ax2.bar(x - w/2, [a[n]["p95"] for n in ns], w, color=BRAND, label="异步(Kafka)")
    ax2.bar(x + w/2, [s[n]["p95"] for n in ns], w, color=GREY, label="同步(写库)")
    for i, n in enumerate(ns):
        ax2.annotate(f'{a[n]["p95"]}', (x[i]-w/2, a[n]["p95"]), textcoords="offset points", xytext=(0,4), ha="center", fontsize=9, color=INK)
        ax2.annotate(f'{s[n]["p95"]}', (x[i]+w/2, s[n]["p95"]), textcoords="offset points", xytext=(0,4), ha="center", fontsize=9, color=INK)
    style_ax(ax2); ax2.set_xticks(x); ax2.set_xticklabels([str(n) for n in ns])
    ax2.set_xlabel("并发抢课请求数 (人)"); ax2.set_ylabel("P95 (ms)")
    ax2.set_title("P95 延迟对比", fontsize=12, color=INK); ax2.legend(frameon=False)
    fig.suptitle("两种落库策略实测对比（容量=2000，开放瞬间全量并发，2000 winner 全部落库）", fontsize=12.5, color=INK, y=1.02)
    save(fig, "08_压测吞吐对比")

# ===== 图 11：不超卖（发起 vs 成功落库）=====
def chart_oversell(rows):
    import numpy as np
    xs = [r["n"] for r in rows]
    issued = xs
    success = [r["finalEnrolled"] for r in rows]
    x = np.arange(len(xs)); w = 0.4
    fig, ax = plt.subplots(figsize=(8.2, 4.6))
    b1 = ax.bar(x - w/2, issued, w, color=GREY, label="发起抢课请求")
    b2 = ax.bar(x + w/2, success, w, color=BRAND, label="成功落库")
    for i in range(len(xs)):
        ax.annotate(f"{issued[i]}", (x[i]-w/2, issued[i]), textcoords="offset points", xytext=(0,4), ha="center", fontsize=9, color=INK)
        ax.annotate(f"{success[i]}", (x[i]+w/2, success[i]), textcoords="offset points", xytext=(0,4), ha="center", fontsize=9, color=BRAND)
    ax.axhline(100, color=GREEN, ls="--", lw=1.5)
    ax.text(len(xs)-0.5, 130, "容量上限 = 100", color=GREEN, ha="right", fontsize=10)
    style_ax(ax); ax.set_xticks(x); ax.set_xticklabels([str(n) for n in xs])
    ax.set_xlabel("并发抢课请求数 (人)"); ax.set_ylabel("人数")
    ax.set_title("不超卖验证：无论多少人并发抢，成功落库恒为容量 100", fontsize=13, color=INK, pad=12)
    ax.legend(frameon=False, loc="upper left")
    save(fig, "11_压测不超卖验证")

# ===== 图 12：DB 成为瓶颈时 async vs sync（模拟 40ms 远程写）=====
def chart_db_bottleneck(a, s):
    ra = a[0]; rs = s[0]
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10.0, 4.6))
    # 左：吞吐（接受请求速率）
    bars = ax1.bar(["异步\n(Kafka)", "同步\n(写库)"], [ra["throughput_rps"], rs["throughput_rps"]], color=[BRAND, GREY], width=0.55)
    for b, v in zip(bars, [ra["throughput_rps"], rs["throughput_rps"]]):
        ax1.annotate(f"{v}", (b.get_x()+b.get_width()/2, v), textcoords="offset points", xytext=(0,5), ha="center", fontsize=12, color=INK, fontweight="bold")
    style_ax(ax1); ax1.set_ylabel("吞吐 / 受理速率 (req/s)")
    ax1.set_title("受理吞吐", fontsize=12, color=INK)
    mult = ra["throughput_rps"]/rs["throughput_rps"] if rs["throughput_rps"] else 0
    ax1.text(0.74, 0.42, f"异步\n≈ {mult:.1f}×\n同步", transform=ax1.transAxes, ha="center", va="center", fontsize=13, color=BRAND, fontweight="bold")
    # 右：平均受理延迟
    bars2 = ax2.bar(["异步\n(Kafka)", "同步\n(写库)"], [ra["avg"], rs["avg"]], color=[BRAND, GREY], width=0.55)
    for b, v in zip(bars2, [ra["avg"], rs["avg"]]):
        ax2.annotate(f"{v}", (b.get_x()+b.get_width()/2, v), textcoords="offset points", xytext=(0,5), ha="center", fontsize=12, color=INK, fontweight="bold")
    style_ax(ax2); ax2.set_ylabel("平均受理延迟 (ms)")
    ax2.set_title("平均受理延迟", fontsize=12, color=INK)
    fig.suptitle("当 DB 成为瓶颈时（模拟单次写 40ms 远程库，2000 并发全部落库）异步显著占优", fontsize=12.5, color=INK, y=1.02)
    save(fig, "12_压测_DB瓶颈下异步优势")

if __name__ == "__main__":
    aL = load("matrix_asyncLat_2000.json"); sL = load("matrix_syncLat_2000.json")
    if aL and sL:
        chart_db_bottleneck(aL, sL)
    m100 = load("matrix_async_100.json")
    if m100:
        chart_latency(m100)
        chart_throughput_scale(m100)
        chart_oversell(m100)
    a2k = load("matrix_async_2000.json"); s2k = load("matrix_sync_2000.json")
    if a2k and s2k:
        chart_sync_vs_async(a2k, s2k)
    else:
        print("跳过同步vs异步图（缺 matrix_async_2000.json 或 matrix_sync_2000.json）")
