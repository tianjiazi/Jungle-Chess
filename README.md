# 林间斗兽棋 1.5

PICO OS 6 / Spatial SDK 6.2.8 原生 FullSpace 3D 人机斗兽棋。
采用用户确认的经典 **7列×9行** 棋盘，红方为玩家、蓝方为 AI。

1.5更新：规则页和棋盘设置页左上角按钮统一为「关闭」，关闭当前页后回到主操作面板，保留棋局。
规则使用两页原生Sheet，支持上一页、下一页。

保留1.4更新：标题图标改为圆形棋子式外观；回合提示位于标题下方最前面，
「新对局」「规则」竖向排列。移除两页的收起按钮与底部提示，统一使用系统关闭控件。
主页收窄为640×480dp，棋盘设置页为640×368dp，切换页面时由原生窗口调整高度。
难度控件下方为「棋盘设置」入口。
棋盘设置为独立页面，十字布局的上/下按钮升降，左/右按钮旋转，中心按钮复位。
操作面板打开时隐藏空间中的打开按钮，收起或关闭后恢复入口；页面切换保留同一棋局。
保留原生可移动WindowContainer、应用图标与大标题、难度SegmentControl、
规则Sheet、新对局AlertDialog。通过系统拖拽栏调整面板位置。

保留1.1更新：全量19个模型重生为鲜艳配色；烘焙柔光、正确的线性顶点色及不透明单面渲染，
移除运行时PBR光照/阴影开销；缓存合法落点并仅更新变化的高亮与棋子语义状态。
保留1.0.1的高亮材质显式持有与统一释放修复。

## 体验

1. 在头显中打开「林间斗兽棋」，进入全沉浸森林棋桌。
2. 捏合抓起红方棋子，移动到绿色合法落点上方，靠近桌面松手；非法落点自动返回原位。
3. 也可点选红方棋子，再点选格子或可吃的蓝方棋子落子。
4. AI 回合禁止玩家走子。简单难度随机选择合法走法；困难难度使用迭代加深 minimax。
5. 标题下先显示回合状态，再依次为新对局、规则、难度切换和棋盘设置。
6. 捏合系统拖拽栏移动面板。使用底部系统关闭控件关闭面板；点击棋盘上方「打开操作面板」恢复，已打开时入口隐藏。
7. 可自然转头、走动环绕观察。棋桌初始高0.74m，每格0.16m。

金色圈为选中棋子；绿色圈为合法落点；蓝色圈记录上一着；红色圈表示当前非法候选落点。
棋子以红蓝底座和项圈区分阵营，金色圆点表示等级。

## 环境与构建

- 用 PICO CLI 0.7.0 的 Spatial `stage` 模板创建。
- Android arm64-v8a，min/target/compile SDK 35；PICO Spatial SDK 6.2.8。
- 本机验证使用JDK 21；Java/Kotlin目标为11，兼容平台JDK 17构建要求。Android SDK 35、Gradle 8.13、AGP 8.13.2；首次构建需联网访问配置的公开 Maven 仓库。
- 将工程放入已信任的开发目录，在本机 `local.properties` 设置 Android `sdk.dir`。
- 源码包不包含本机 SDK 路径、构建缓存、签名私钥。

```sh
./gradlew testDebugUnitTest assembleDebug
./gradlew assembleRelease
./gradlew connectedDebugAndroidTest
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --device <device-id>
pico-cli app launch com.pico.junglechess --activity .platform.LaunchActivity --device <device-id>
```

交付 APK 是 **debug 签名体验构建**，便于当前设备安装测试；未配置商店发布签名。
Vibe Lab投稿使用完整Gradle源码工程；release不配置签名，由平台统一签名。平台签名与本机debug签名不同，已装体验版的设备可能需要先卸载再安装平台版本。

## 源码导航

| 路径 | 职责 |
|---|---|
| `app/src/main/java/com/pico/junglechess/game/Jungle.kt` | 不可变棋局、地形、走法、吃子、终局 |
| `app/src/main/java/com/pico/junglechess/game/JungleAI.kt` | 随机策略、minimax/alpha-beta、局势评估 |
| `app/src/main/java/com/pico/junglechess/game/GameViewModel.kt` | 回合、难度、重开、异步AI及过期任务取消 |
| `app/src/main/java/com/pico/junglechess/content/JungleScene.kt` | 模型加载、碰撞、拖拽、格子吸附、动画、高亮、资源释放 |
| `app/src/main/java/com/pico/junglechess/content/HomeStage.kt` | FullSpace场景、手势接入、面板恢复入口 |
| `app/src/main/java/com/pico/junglechess/content/ControlWindow.kt` | Spatial UI原生控件及窗口级Sheet/AlertDialog |
| `app/src/main/java/com/pico/junglechess/content/PanelSession.kt` | Stage与面板共享棋局，窗口关闭不重置游戏 |
| `app/src/main/AndroidManifest.xml` | FullSpace `stage.style=3`、全沉浸配置 |
| `app/src/main/assets` | 16枚动物GLB、棋盘、森林、天空；HDR仅保留作制作参考，运行时不加载 |
| `app/src/test` | 规则与AI的17项主机测试 |
| `app/src/androidTest` | 5项设备集成测试，含60局规则AI压力及连续异步回合 |
| `art` | 原创模型生成、离线预览、模型面数清单、真实场景语义回归脚本 |
| `docs` | 规则调研、交互约定和验收记录 |

## 资源重建

模型由程序化曲面、网格和顶点色构成，统一为鲜艳的圆润动物雕塑风格。
象鼻、狮鬃、虎纹、豹斑、狼耳、狗耳和鼠尾等均为真实几何结构。
光照烘焙在glTF的线性COLOR_0中，使用KHR_materials_unlit，预览时须正确转换回sRGB。

```sh
python3 -m venv .art-venv
.art-venv/bin/pip install numpy trimesh pillow pygltflib scipy matplotlib
.art-venv/bin/python art/generate_assets.py
.art-venv/bin/python art/render_preview.py
```

预览脚本默认使用 macOS 华文黑体，其他系统需调整 `FONT`。
两张预览图从交付GLB实际几何离线渲染，**不是头显截图**。

## 实现边界

本版本为离线单人人机对弈；当前棋局驻留 ViewModel，不提供进程终止后的存档恢复。
手势由 PICO 原生 Spatial 手势识别接入，最终手部跟踪体验、空间画面和持续帧率需要佩戴头显验收。
具体验证结果见 `docs/验证记录.md`。
