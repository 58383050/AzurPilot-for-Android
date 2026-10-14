# 界面图标 / UI icons

## 中文

### 布局与颜色

应用设置、配置行和主导航使用 `AppSymbol` 定义语义图标。设置行通过现有 Material 3
`SegmentedListItem` 的 `leadingContent` 插槽放置 24 dp 图标，沿用组件的间距、圆角和
文字样式。普通图标采用 `onSurfaceVariant`，启用状态采用 `primary`；按钮图标继承
按钮内容色。Miuix 模式保留原有组件，并使用相同的语义与尺寸。

操作按钮使用 `AppActionLabel`，尺寸与间距采用 Material 3 的 `ButtonDefaults`。独立图标按钮
由调用方提供无障碍名称，与相邻文字共同出现的图标不重复朗读。

### 动效与资源

动态图标来自 [useAnimations](https://github.com/useAnimations/react-useanimations)，固定到
提交 `88b845d9a4330b95bc7f456a17f32ce54157b32f`。35 个新增 JSON 资源存放于
`app/app/src/main/res/raw/icon_*.json`，合计约 96 KiB；MIT 许可保留在应用开源许可清单中。
资源保持上游原样，通过固定绘制缩放抵消 32 单位画布的留白，使可见线条对齐 24 dp。

图标由现有 Lottie Compose 渲染，不添加网络请求或新的播放依赖。首次显示保持静态；
按钮点击、设置值变化及导航选择按资源自身时长播放，保留原有曲线。只有明确
的忙碌状态循环播放。页面离开前台或系统关闭动画时显示静态图形，停止播放。系统动画
设置变化立即生效，快速切换状态从当前进度继续，剩余时长按进度距离计算。
系统时长缩放由 Compose 应用一次；循环也使用资源自身时长。

新设置应选择与功能相符的 `AppSymbol`，避免仅因可用而复用不相关图形。业务运行状态、
按钮可用性及错误提示仍由原来的状态源控制。

## English

### Layout and color

App settings, configuration rows and main navigation use semantic icons defined by `AppSymbol`.
Preference rows place 24 dp icons in the existing Material 3 `SegmentedListItem` leading slot,
retaining its spacing, shapes and typography. Regular icons use `onSurfaceVariant`; enabled states
use `primary`. Button icons inherit the button content color. Miuix mode retains its existing
components with the same semantics and dimensions.

Action buttons use `AppActionLabel`, with icon dimensions and spacing from Material 3 `ButtonDefaults`. Standalone
icon buttons supply accessibility labels; icons beside text are decorative to avoid repeated speech.

### Motion and assets

Animated icons come from [useAnimations](https://github.com/useAnimations/react-useanimations), pinned
to commit `88b845d9a4330b95bc7f456a17f32ce54157b32f`. The 35 new JSON assets are bundled under
`app/app/src/main/res/raw/icon_*.json`, totaling about 96 KiB. Their MIT license remains in the app's
open-source license catalog. Assets are unchanged; a fixed draw scale compensates for padding in
the 32-unit canvas, aligning the visible strokes to 24 dp.

The existing Lottie Compose renderer handles playback without network requests or new playback
dependencies. Icons initially remain static. Button taps, preference changes and navigation
selection use each asset's native duration and curves. Only explicit busy
states loop. Background pages and disabled system animations show static glyphs and stop playback.
System animation changes apply immediately; rapid state changes continue from current progress,
with remaining duration proportional to the remaining distance. Compose applies system duration
scaling once, and loops also use the asset's native duration.

New preferences should select a relevant `AppSymbol` instead of reusing an unrelated available
glyph. Existing state sources still control business activity, button availability and errors.
