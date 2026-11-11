# 教程工作流（内部说明）

这个目录是系列教程的唯一母版。公众号与 CSDN 的所有内容都从这里产出，**不要在平台后台直接改字**。

## 目录约定

```
docs/tutorial/
  README.md              # 本文件：工作流
  series-plan.md         # 选题池、进度、tag 映射
  _template/index.md     # 单篇骨架
  01-slug/
    index.md             # 母版，唯一事实来源（公众号底稿）
    csdn.md              # CSDN 发布稿：母版超集，额外含配图与代码走读
    wechat.html          # 公众号成品（内联样式，全选复制）
    assets/
      fig-01-*.png       # 正文配图（CSDN 用）
      cover-900x383.png  # 公众号主封面（2.35:1）
      cover-500x500.png  # 公众号分享图（1:1）
      cover-csdn-1280x720.png  # CSDN 封面（16:9）
      make_cover.py      # 封面合成脚本，改文案即可复用
      make_figs.py       # 配图脚本（拓扑、链路、分层）
  _bonus-slug/           # 加餐篇，不占正式序号，结构同上
```

## 封面

每篇三张：公众号主封面 `900×383`（2.35:1）、分享图 `500×500`（1:1）、CSDN 封面 `1280×720`（16:9）。CSDN 的前端按 1280×720 硬校验，比例不符会被拦；缩略图按 240×135 展示，标题必须撑满左侧，小图状态下仍可辨认。

底图用生成模型出，中文标题用脚本叠加——生成模型直接写字容易糊。合成脚本在 `01-project-overview/assets/make_cover.py`，复用方式：复制脚本、改顶部常量（`SERIES` / `TITLE_CN` / `TITLE_SUB` / `TECH` / `META`）、换同目录底图文件名，跑 `python make_cover.py`。字体走 `C:/Windows/Fonts/msyhbd.ttc`（微软雅黑 Bold），文字自动带 2px 深色描边，压到亮背景上也清晰。

注意：底图右下角带平台水印，脚本的裁切区域已避开；换底图后需重新确认水印不在取景范围。

## 配图

CSDN 版每篇配 2–3 张结构图，白底浅色、和正文同色系，全部由 `assets/make_figs.py` 用 Pillow 画出来——不用 mermaid（CSDN 不渲染），也不截图别人的图。

| 图 | 内容 | 用在哪 |
|---|---|---|
| `fig-01-layers.png` | 模块分层与依赖方向 | 讲模块职责的那一节 |
| `fig-02-request-path.png` | 一次请求的检查顺序 | 讲主链路的那一节 |
| `fig-03-topology.png` | 部署拓扑 | 讲部署的那一节 |

复用方式：复制 `make_figs.py`，改各函数里的小标题与 `box()` / 表格行文本，跑 `python make_figs.py`。三条约束——图文数字对齐（图 1 对应 `fig-01`）、图里的事实必须能在代码里核对、每张图下方一句灰色图注说明"看哪里"。

CSDN 编辑器不会因为粘贴 markdown 就上传本地图，发布时按 `![...](assets/fig-0N-*.png)` 的位置逐张替换为上传后的图。

## 一篇的产出流程

1. 从 `_template/index.md` 复制出 `NN-slug/index.md`，填 front matter。
2. 写完母版，`series-plan.md` 状态改 `drafting` → `review`。
3. 转公众号：用 [doocs/md](https://github.com/doocs/md) 粘贴 `index.md`，选固定主题，代码高亮选浅色，导出 HTML。
4. 把导出的 HTML 存成 `wechat.html`。**图片必须换成微信素材库地址**（或转码前在 md 里粘贴上传），否则粘贴到公众号会丢图。
5. 公众号后台全选粘贴 → 手机预览 → 发布。CSDN 粘 `csdn.md`，标题、摘要、标签、专栏、封面按 `series-plan.md` 第 5 节的字段表填；正文里的 `assets/fig-*.png` 逐张上传替换。
6. 打 tag：`git tag lesson-NN && git push origin lesson-NN`，回填 `series-plan.md`。

## 写之前先过的三道判断

1. **是不是一个坑**？如果这一篇的核心不是"这样做会出错"，而是一个功能介绍，它就偏离了系列定位，退回选题池。
2. **代码在仓库里存在吗**？每篇必须指向一段真实代码 + 一个可 checkout 的 tag；写不出来的，说明还没做，先别写。
3. **读者能照着复现吗**？给命令、给配置、给报错原文。只给结论的文章不发。

## 文风约束

全系列统一，两个平台一致：

| 项 | 要求 |
|---|---|
| 人称 | 不用第一人称（我 / 我们），不用第二人称（你 / 大家）。用无主语句式：`该实现可以运行，但存在三个问题` |
| 口述感 | 禁止「聊聊」「踩过的坑」「说实话」「不得不说」「其实」「显然」 |
| 主观描述 | 不写感受与评价，只写现象、机制、取值、代价 |
| 数据 | 默认值、上限、耗时、条数一律写具体数字 |
| 主体 | 机制与代码占 70% 以上，结论不超过 30% |

## 排版纪律

- 标题层级不超过 3 级，公众号只认 `h1/h2/h3` 且样式被平台覆盖，靠**加粗**和**分隔线**做层次。
- 段落 2–4 行，手机阅读不出现大段文字墙。
- 代码块单行不超过 60 字符，超了拆行或改伪代码。
- **表格每一列都要显式给 `width`**。公众号在 `table-layout:fixed` 下会用首行内容决定无宽度列的列宽，首行是 `JDK | 21` 时第二列被按 "21" 定宽，后面 `MyBatis-Plus 3.5.17` 就被压成一列单字。纯键值表（技术栈、参数清单）直接写成 `标签 + 值` 的段落列表，不依赖表格。
- **禁用 mermaid**。CSDN 不渲染 mermaid，发布后正文会变成三段：源码代码块、`图表加载中…`、一串无排版的节点清单。拓扑与时序改用表格；确实需要形状时用带 `text` 标记的代码块画字符图。
- 每张图配一句图注（公众号没有图注语法，用灰色小字段落代替）。

## 校验

```bash
# 母版里不应残留待办标记
grep -rn "TODO\|TBD\|FIXME" docs/tutorial/

# 人称呼检查：除引用外不应出现第一/第二人称
grep -rn "我们\|你们\|大家\|\b我\b" docs/tutorial/*/index.md

# 口述语检查
grep -rn "聊聊\|说说\|说实话\|不得不说\|其实" docs/tutorial/*/index.md

# 公众号 HTML 不应引用外链样式与本地图片
grep -n "<link\|src=\"\./\|src=\"assets" docs/tutorial/*/wechat.html

# 不允许出现 mermaid
grep -rn "mermaid" docs/tutorial/*/index.md docs/tutorial/*/csdn.md
```
