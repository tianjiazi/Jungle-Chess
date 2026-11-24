# 林间斗兽棋 1.13

PICO OS 6 / Spatial SDK 6.2.8 原生 FullSpace 3D 人机斗兽棋。
采用用户确认的经典 **7列×9行** 棋盘，红方为玩家、蓝方为 AI。

1.13更新：主页底部「棋盘设置」旁新增「天空沉浸度」入口。原生滑杆支持0–100%，
并提供全透、半沉浸、全沉浸快捷按钮；首次默认100%，选择保存到本机。
0%仅隐藏天空以显示周围VST，50%混合天空与VST，100%显示完整虚拟天空。
棋盘、棋子、树、地面装饰、胜利烟花和交互保持原样。主页仍为640×608dp，
天空设置页和棋盘设置页均为640×368dp，无滚动。
使用Mixed Stage与独立天空材质实现；通过应用内控件调节，系统沉浸度旋钮不控制此项。
实机确认Progressive Stage在0%会连同棋盘和森林模型一起隐藏，因此未使用该模式。

1.12更新：在难度设置下面依次增加原生「背景音乐」「棋子音效」开关，
首次使用默认全部开启，切换即时生效，选择保存到本机并在重新进入时恢复。
音乐与叫声独立控制，两个都关闭时释放音频焦点。主页改为640×608dp，
棋盘设置页仍为640×368dp；八种叫声沿用1.11真实动物录音。

1.11更新：八种棋子的叫声全部替换为对应动物的真实录音节选，长度0.42–1.44秒，
保持原始音高与音色；仅裁剪、单声道转换、重采样、统一音量和切口淡化。
原始录音文件保留在 `art/recordings`；作者、录音说明及许可见
`docs/真实动物音频来源与许可.md`，同时随APK附带 `ANIMAL_AUDIO_LICENSES.txt`。
猫、狗音频按CC BY-SA 3.0发布，豹声按CC BY 3.0发布，其余为CC0或公有领域录音。
合成叫声不再作为当前棋子音效；重建脚本也已改为真实录音裁剪。

1.10历史更新：首次增加八种棋子选择叫声；其合成卡通音频已在1.11替换。
成功点选或抓起红方棋子时播放；同动物300ms内重复选择去重，
切换棋子立即停止前一段叫声。蓝方、AI回合、终局及hover不触发。
后台停止音效，退出释放SoundPool，保留原背景音乐。

1.9更新：周围24棵树混合圆冠、尖顶针叶、细长树冠及舒展阔叶四种样式，
随机布局、高低错落。随机种子固定，场景布局在每次运行时保持稳定。
进入棋局自动播放原创轻快背景曲《林间散步》，约69秒无缝循环。
后台暂停、返回继续，退出释放播放器；音乐遵循系统音频焦点及媒体音量。

1.8更新：结果弹窗增加「退出游戏」，关闭场景、面板及应用任务。
蓝方棋子不再支持hover、点击、抓取或移动；吃子通过目标格完成。
森林地面增加苔藓石头、成片草花及兔子、抱橡果的松鼠，保留棋桌及玩家观察空间。

1.7.1更新：退出斗兽棋场景时同步关闭信息面板，监听Stage停止显示及销毁事件；
后台不允许规则、结果或延迟任务重新打开面板。再次进入场景正常打开一个面板。

1.7更新：一局结束自动显示原生结果弹窗，列出结果与结束原因，提供「新开一局」。
胜方仍在棋盘上的棋子以不同频率和高度持续跳动；玩家获胜时天空播放9秒彩色烟花。
最后落子先完成动画再开始庆祝；和棋只显示结果。重开立即停止庆祝、恢复棋子初始位置，保留难度和棋盘摆放。
结果弹窗可以使用系统手势关闭以观看庆祝；面板被关闭时终局会自动恢复面板并提示结果。

1.6更新：简单、困难共用局势评分与negamax/alpha-beta搜索，简单搜索2层，困难迭代加深至最多6层。
一层为任一方的一步；两层覆盖AI走子与玩家下一步应对。两档均考虑胜负、子力、兽穴压力、陷阱、受攻击棋子和行动力。
先完成基础2层搜索，困难后续搜索以1100ms为软预算；只使用完整搜索轮次的结果，已证明获胜时可提前结束。
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
3. 也可点选红方棋子，再点选目标格落子；吃子时点击蓝方棋子所在的格子。
4. AI 回合禁止玩家走子。简单难度预测2层；困难难度预测最多6层，受时间预算限制。两档都判断局势与对方反击。
   一局结束后在结果弹窗点击「新开一局」重新开始，或点击「退出游戏」完全关闭场景与面板。
5. 标题下先显示回合状态，再依次为新对局、规则、难度切换、背景音乐、棋子音效。
   底部打开「天空沉浸度」，拖动滑杆或选择「全透」「半沉浸」「全沉浸」；关闭设置保留棋局。
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
| `app/src/main/java/com/pico/junglechess/game/JungleAI.kt` | 两档搜索深度、minimax/alpha-beta、共享局势评估 |
| `app/src/main/java/com/pico/junglechess/game/GameViewModel.kt` | 回合、难度、重开、异步AI及过期任务取消 |
| `app/src/main/java/com/pico/junglechess/content/JungleScene.kt` | 模型加载、碰撞、拖拽、格子吸附、动画、高亮、资源释放 |
| `app/src/main/java/com/pico/junglechess/content/HomeStage.kt` | FullSpace场景、手势接入、面板恢复入口 |
| `app/src/main/java/com/pico/junglechess/content/ControlWindow.kt` | Spatial UI原生控件及窗口级Sheet/AlertDialog |
| `app/src/main/java/com/pico/junglechess/content/PanelSession.kt` | Stage与面板共享棋局，窗口关闭不重置游戏 |
| `app/src/main/AndroidManifest.xml` | FullSpace `stage.style=1`，保持前景显示、允许透明天空显示VST |
| `app/src/main/assets` | 16枚动物GLB、棋盘、混合林、地面装饰、天空及背景音乐；HDR仅保留作制作参考，运行时不加载 |
| `app/src/main/java/com/pico/junglechess/content/BackgroundMusic.kt` | Stage背景音乐、异步流式准备、循环、音频焦点与退出释放 |
| `app/src/main/java/com/pico/junglechess/content/SkySettings.kt` | 天空沉浸度StateFlow及本机偏好保存 |
| `app/src/test` | 规则与AI战术、搜索深度、预算和取消验证 |
| `app/src/main/java/com/pico/junglechess/content/VictoryMotion.kt` | 胜方棋子跳动参数与烟花错峰时间表 |
| `app/src/main/java/com/pico/junglechess/game/MatchResult.kt` | 玩家胜利、AI胜利和和棋结果文案 |
| `app/src/androidTest` | 设备测试，含终局后重开、Stage关闭、后台退出及结果按钮完整退出；本轮执行范围见验证记录 |
| `art` | 原创模型生成、离线预览、模型面数清单、真实场景语义回归脚本 |
| `docs` | 规则调研、交互约定和验收记录 |

## 资源重建

模型由程序化曲面、网格和顶点色构成，统一为鲜艳的圆润动物雕塑风格。
象鼻、狮鬃、虎纹、豹斑、狼耳、狗耳和鼠尾等均为真实几何结构。
光照烘焙在glTF的线性COLOR_0中，使用KHR_materials_unlit，预览时须正确转换回sRGB。

```sh
python3 -m venv .art-venv
.art-venv/bin/pip install numpy trimesh pillow pygltflib scipy matplotlib soundfile
.art-venv/bin/python art/generate_assets.py
.art-venv/bin/python art/generate_forest_details.py
.art-venv/bin/python art/generate_mixed_forest.py
.art-venv/bin/python art/compose_woodland_music.py
.art-venv/bin/python art/generate_animal_calls.py
.art-venv/bin/python art/render_preview.py
```

预览脚本默认使用 macOS 华文黑体，其他系统需调整 `FONT`。
棋盘、棋子和林地装饰预览从实际几何离线渲染，**不是头显截图**。
地面装饰独立生成，使用 `art/forest_details_manifest.json` 记录面数与文件大小。
混合林与原创音乐参数分别记录于 `mixed_forest_manifest.json` 和 `woodland_music_manifest.json`。
音乐使用32kHz双声道PCM WAV，随APK离线播放；音频文件不压缩以支持assets文件描述符流式读取。
棋子叫声使用32kHz单声道PCM WAV，由真实录音节选制作，通过SoundPool播放。

## 实现边界

本版本为离线单人人机对弈；当前棋局驻留 ViewModel，不提供进程终止后的存档恢复。
手势由 PICO 原生 Spatial 手势识别接入，最终手部跟踪体验、空间画面和持续帧率需要佩戴头显验收。
具体验证结果见 `docs/验证记录.md`。
