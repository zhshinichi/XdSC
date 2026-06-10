# -*- coding: utf-8 -*-
"""事件驱动架构风格图：事件源(Event Source) → 事件通道(Event Channel) → 事件处理器(Event Processor)。
强调 EDA 的解耦特性：生产者不感知消费者、处理器可独立增删；Kafka 作为事件通道实现异步削峰。
输出: D:\\software_work\\diagrams\\13_事件驱动架构.{svg,pdf,png}
与 plot_bench.py 同一套字体/配色管线。
"""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib import font_manager


def setup_font():
    candidates = [
        r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\msyhbd.ttc",
        r"C:\Windows\Fonts\simhei.ttf",
    ]
    for p in candidates:
        if os.path.exists(p):
            font_manager.fontManager.addfont(p)
            plt.rcParams["font.sans-serif"] = [font_manager.FontProperties(fname=p).get_name()]
            break
    plt.rcParams["axes.unicode_minus"] = False


setup_font()

BRAND = "#a4233a"; BRAND2 = "#8c1c30"
AMBER = "#e8973a"; GREEN = "#2e9e6b"; BLUE = "#2a6fdb"
GREY = "#9aa0aa"; INK = "#1d1d1f"; MUTE = "#6b7280"
OUTDIR = r"D:\software_work\diagrams"


def box(ax, x, y, w, h, fc, ec, title, subtitle=None, tcolor="white",
        sub_tcolor=None, fontsize=12, dashed=False, alpha=1.0):
    style = "round,pad=0.02,rounding_size=0.14"
    p = FancyBboxPatch((x, y), w, h, boxstyle=style, linewidth=1.8,
                       facecolor=fc, edgecolor=ec, alpha=alpha,
                       linestyle="--" if dashed else "-")
    ax.add_patch(p)
    cx = x + w / 2
    if subtitle:
        ax.text(cx, y + h * 0.62, title, ha="center", va="center",
                fontsize=fontsize, color=tcolor, fontweight="bold")
        ax.text(cx, y + h * 0.30, subtitle, ha="center", va="center",
                fontsize=fontsize - 3, color=sub_tcolor or tcolor)
    else:
        ax.text(cx, y + h / 2, title, ha="center", va="center",
                fontsize=fontsize, color=tcolor, fontweight="bold")
    return p


def arrow(ax, x0, y0, x1, y1, color, lw=2.2, dashed=False, label=None,
          lcolor=None, rad=0.0, ls_off=(0, 0)):
    a = FancyArrowPatch((x0, y0), (x1, y1),
                        arrowstyle="-|>", mutation_scale=18, linewidth=lw,
                        color=color, connectionstyle=f"arc3,rad={rad}",
                        linestyle="--" if dashed else "-",
                        shrinkA=2, shrinkB=2, zorder=5)
    ax.add_patch(a)
    if label:
        mx, my = (x0 + x1) / 2 + ls_off[0], (y0 + y1) / 2 + ls_off[1]
        ax.text(mx, my, label, ha="center", va="center", fontsize=8.5,
                color=lcolor or color,
                bbox=dict(boxstyle="round,pad=0.18", fc="white", ec="none", alpha=0.92))


def column_header(ax, x, txt, color):
    ax.text(x, 8.55, txt, ha="center", va="center", fontsize=13.5,
            color=color, fontweight="bold")


fig, ax = plt.subplots(figsize=(12.6, 7.4))
ax.set_xlim(0, 13); ax.set_ylim(0, 9); ax.axis("off")

ax.text(0.15, 8.7, "事件驱动架构风格 · 事件源 → 事件通道 → 事件处理器",
        ha="left", va="bottom", fontsize=16, color=INK, fontweight="bold")

# ===== 三大分区底色带 =====
for bx, bw, bc in [(0.3, 3.5, "#fbf1f2"), (4.35, 4.0, "#fff7ec"), (8.95, 3.75, "#eef4fd")]:
    ax.add_patch(FancyBboxPatch((bx, 0.55), bw, 7.7,
                 boxstyle="round,pad=0.02,rounding_size=0.2",
                 facecolor=bc, edgecolor="none", zorder=0))

column_header(ax, 2.05, "事件源 / 生产者", BRAND)
column_header(ax, 6.35, "事件通道", AMBER)
column_header(ax, 10.8, "事件处理器 / 消费者", BLUE)
ax.text(2.05, 8.18, "Event Source", ha="center", fontsize=9, color=MUTE, style="italic")
ax.text(6.35, 8.18, "Event Channel", ha="center", fontsize=9, color=MUTE, style="italic")
ax.text(10.8, 8.18, "Event Processor", ha="center", fontsize=9, color=MUTE, style="italic")

# ===== 事件源 =====
box(ax, 0.6, 5.7, 2.9, 1.35, BRAND, BRAND2,
    "抢课服务", "EnrollmentService · 选课成功事件", fontsize=12.5, sub_tcolor="#f3d6da")
box(ax, 0.6, 3.85, 2.9, 1.35, BRAND, BRAND2,
    "退课服务", "退课 · 名额释放事件", fontsize=12.5, sub_tcolor="#f3d6da")
# 解耦说明
ax.text(2.05, 2.9, "生产者只「发事件」\n不感知谁来消费", ha="center", va="center",
        fontsize=9.5, color=BRAND2,
        bbox=dict(boxstyle="round,pad=0.3", fc="white", ec=BRAND, lw=1.1))

# ===== 事件通道：Kafka Topic =====
box(ax, 4.55, 3.4, 3.6, 2.3, "#fdebd0", AMBER,
    "", None)
ax.text(6.35, 5.35, "Kafka Topic", ha="center", fontsize=12.5, color="#9a5b12", fontweight="bold")
ax.text(6.35, 4.98, "enroll-events", ha="center", fontsize=11, color="#9a5b12")
# 分区
for i, px in enumerate([4.75, 5.7, 6.65]):
    ax.add_patch(FancyBboxPatch((px, 3.62), 0.82, 0.95,
                 boxstyle="round,pad=0.02,rounding_size=0.06",
                 facecolor="white", edgecolor=AMBER, linewidth=1.3))
    ax.text(px + 0.41, 4.32, f"P{i}", ha="center", fontsize=9, color="#9a5b12", fontweight="bold")
    for j in range(3):
        ax.add_patch(plt.Rectangle((px + 0.1 + j * 0.22, 3.74), 0.17, 0.34,
                     facecolor=AMBER, edgecolor="none", alpha=0.55))
ax.text(6.35, 3.28, "按 courseId 分区 · 分区内有序", ha="center", fontsize=8.8, color="#9a5b12")
# 通道特性注释
ax.text(6.35, 2.55, "异步削峰 · 缓冲解耦 · 可重放\nat-least-once 投递",
        ha="center", va="center", fontsize=9.5, color="#9a5b12",
        bbox=dict(boxstyle="round,pad=0.3", fc="white", ec=AMBER, lw=1.1))

# ===== 事件处理器 =====
box(ax, 9.2, 6.0, 3.3, 1.25, BLUE, "#1c4fae",
    "落库处理器（已实现）", "EnrollEventConsumer → MySQL", fontsize=11.5, sub_tcolor="#d4e2fb")
box(ax, 9.2, 4.45, 3.3, 1.1, "#dbe7fb", BLUE,
    "选课统计处理器", "可独立增加", tcolor="#1c4fae", sub_tcolor=BLUE,
    fontsize=11, dashed=True)
box(ax, 9.2, 3.15, 3.3, 1.1, "#dbe7fb", BLUE,
    "通知 / 对账处理器", "可独立增加", tcolor="#1c4fae", sub_tcolor=BLUE,
    fontsize=11, dashed=True)
ax.text(10.8, 2.25, "新增处理器无需改动生产者\n→ 可扩展、可修改性强", ha="center", va="center",
        fontsize=9.5, color="#1c4fae",
        bbox=dict(boxstyle="round,pad=0.3", fc="white", ec=BLUE, lw=1.1))

# ===== 箭头：事件源 → 通道 =====
arrow(ax, 3.5, 6.35, 4.55, 5.4, BRAND, label="publish", lcolor=BRAND2, rad=-0.12)
arrow(ax, 3.5, 4.5, 4.55, 4.15, BRAND, label="publish", lcolor=BRAND2, rad=0.12)

# ===== 箭头：通道 → 处理器（消费者组并行消费） =====
arrow(ax, 8.15, 4.9, 9.2, 6.55, BLUE, label="subscribe", lcolor="#1c4fae", rad=0.12)
arrow(ax, 8.15, 4.4, 9.2, 5.0, BLUE, dashed=True, rad=0.05)
arrow(ax, 8.15, 3.95, 9.2, 3.7, BLUE, dashed=True, rad=-0.1)
ax.text(8.62, 5.85, "消费者组\n并行消费", ha="center", va="center", fontsize=8.5,
        color="#1c4fae", bbox=dict(boxstyle="round,pad=0.18", fc="white", ec="none", alpha=0.9))

# ===== 底部一句话点题 =====
ax.text(6.5, 0.95, "核心特性：事件源与处理器通过事件通道完全解耦——"
                   "生产者立即返回（削峰），处理器异步消费、可独立增删（隐式调用）",
        ha="center", va="center", fontsize=10.5, color=INK,
        bbox=dict(boxstyle="round,pad=0.4", fc="#f5f5f7", ec="#d9dce2", lw=1.2))

os.makedirs(OUTDIR, exist_ok=True)
for ext in ("svg", "pdf", "png"):
    fig.savefig(os.path.join(OUTDIR, "13_事件驱动架构." + ext),
                bbox_inches="tight", dpi=200 if ext == "png" else None, transparent=False)
plt.close(fig)
print("saved 13_事件驱动架构 (svg/pdf/png)")
