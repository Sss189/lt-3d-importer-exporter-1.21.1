# LT 3D Importer & Exporter - Minecraft 1.21.1

当前版本：**0.8.0-alpha.3**（alpha）。

**[下载可安装 JAR](https://github.com/Sss189/lt-3d-importer-exporter-1.21.1/releases/tag/v0.8.0-alpha.3)** | **[全部版本](https://github.com/Sss189/lt-3d-importer-exporter-1.21.1/releases)** | **[简要使用说明 PDF](docs/quick-start.pdf)**

F8 或 /lt3d：OBJ 转 LT 蓝图、主手已保存蓝图导出 OBJ/MTL/PNG。缺少材质自动白模；采样、合并与编码在后台执行。

将 Release 中的 JAR 放入客户端 `mods` 并重启游戏。兼容基准：Minecraft 1.21.1、NeoForge 21.1.256、Java 21、LT pre233、CreativeCore 2.13.50。

源码构建：设置 Java 21 的 `JAVA_HOME`，把 `libs/README.md` 中的外部依赖放入 `libs`，执行 `gradlew.bat build`（Windows）或 `./gradlew build`。首次构建需要联网下载 Gradle/Maven 依赖。

构建验证与游戏内验收范围见 `docs/VALIDATION.md`。旧记录中的 `LT_WORKSPACE` / `GAME_DIR` 是本地路径占位；测试用第三方材质包、光影及游戏模型不包含在仓库中。

---

# LT 3D Importer & Exporter — 1.21.1 移植

Timardo 项目的非官方移植，基于上游 `1.20.1` 分支提交 `926607ff86f9411630fdf83f3b4612ef5f7121bd`。
面向 Minecraft **1.21.1 / NeoForge 21.1.256 / LittleTiles pre233 / CreativeCore 2.13.50 / Java 21**。
这是开发中的 alpha，实际整合包的操作与视觉验收仍需进行。

## 使用

把构建的 JAR 放到客户端 `mods`，与上面的 LT、CreativeCore 一起使用。
本附属只在客户端加载，不注册新的方块或服务器网络包。多人导入使用服务器已有的 LT 导入功能及其权限。

进入世界后按 **F8**，或输入 **`/lt3d`** 打开工具。按键可在控制设置中修改。

### OBJ 导入

1. 输入 OBJ 文件绝对路径，也可以把文件拖进界面。MTL、贴图按文件声明的相对路径组织；可在第二行指定覆盖贴图。
2. 设置最长边（单位为游戏中的大方块）、每方块的网格数、采样间距和基础方块。默认使用 LT 的干净可染色方块。
3. 点击“OBJ 转 LT 蓝图”。结果保存到游戏目录的 `lt3d/imports/model-*/model.struct`。
4. 点击“打开 LT 导入并复制蓝图”，在 LT 界面点击“粘贴”和“导入”，然后取出蓝图。创造模式可使用空槽，其他模式需按 LT 的规则提供蓝图。

模型最长边会缩放到设置的尺寸，最小坐标移到原点。三角形表面被采样成小方块，再按 UV 采样纹理颜色。
可先用项目 `examples/cube.obj` 测试，它带三个基础颜色的 MTL，不需要外部贴图。
使用无材质 OBJ 时无需 MTL 或 PNG；第三方游戏椅子素材不随本仓库分发。
建议先设最长边 4、网格 16、采样间距 0.25；覆盖贴图路径留空。
使用无材质 OBJ 时无需 MTL 或 PNG；第三方游戏椅子素材不随本仓库分发。
建议先设最长边 4、网格 16、采样间距 0.25；覆盖贴图路径留空。
模型内部不自动填充。相邻同色小格由导入器在后台做无损长方体合并，再编码为 LT 原生蓝图。导入不是保留任意模型 UV 的无损转换。
默认采样间距 0.25，允许 0.05–1；数值越小越密，耗时越多。网格必须是 LT 当前配置允许的网格。
MTL 的 `Kd`、`d` 和 `map_Kd` 支持基础颜色与透明度；法线、高光、骨骼、材质节点不导入。

从 `0.8.0-alpha.2` 起，材质不再是导入的必要条件：没有 MTL、引用的 MTL 缺失、未定义的材质名，或材质引用的贴图缺失时，相应部分自动导入为不透明白膜。
有完整材质的部分仍按原来的颜色和贴图导入。使用白膜时“覆盖贴图路径”留空；手动指定了不存在的覆盖贴图仍会提示输入错误。

### LT 导出

1. 使用 LT 蓝图保存目标结构，并把已保存的蓝图拿在**主手**。
2. 按 F8，点击“导出主手蓝图为 OBJ”。
3. 输出位于游戏目录的 `lt3d/exports/blueprint-*/`，包含 `model.obj`、`model.mtl`、`textures/*.png`。
4. 在 Blender 等软件导入 `model.obj`，保持 MTL 与纹理相对目录不变。

一个 OBJ 单位对应一个 Minecraft 大方块。导出通过 LT 实际 tile → CreativeCore 裁切 BakedQuad 获取几何和 UV，
从当前游戏已加载的贴图读取像素，烘焙 LT 染色与可取得的基础方块染色。不会走 LT 的大型物品简化预览。
子结构和透明 tile 均遍历；导出的是静态几何。OBJ 写出法线，并去重顶点/UV。

## 当前限制

- 蓝图没有实际世界坐标和邻居上下文，**不保证 CTM/repeat/生物群系颜色与放在世界中的结果一致**。
- 导出不包含光影、PBR 法线/高光、发光叠加、门动画或机械逻辑。动画贴图取声明的首帧。
- 遍历真实 tile 几何；结构专用渲染器附加的文字/特效/动态物体不保证导出。
- 暂未做全面遮挡面剔除，大型模型文件可能较大。
- 第一版只支持 OBJ；三角化依赖 javagl，复杂凹多边形建议在 Blender 中先三角化。
- 文件与转换限额：OBJ 32 MiB、输入 20 万面/100 万顶点、三角化后 40 万面、导入 25 万小方块、采样工作量 5000 万步。
  导出最多 25 万 render boxes、150 万面、2048 材质、累计 6400 万纹理像素。
- 可取消转换。取消或写出失败可能留下本次创建的部分输出目录；不会覆盖已有输出或模型素材。
- 从 `0.8.0-alpha.3` 起，采样、同色合并、蓝图编码和文件写出都在后台执行，界面显示当前阶段并支持取消；不再在游戏主线程调用最终的 LT 全量合并。
- LT 原生导入界面的预览、蓝图放置和复杂场景渲染仍可能较慢，需要按模型规模分别测试。

## 构建与验证

```powershell
.\tools\build.ps1
.\tools\build.ps1 -Smoke
```

`-Smoke` 启动独立开发客户端，在主菜单自动检查导入、蓝图数据组件、SNBT 往返，以及加载模型的导出、UV、染色、
跨方块偏移、子结构和玻璃面；完成后自动退出。不使用整合包存档。
普通构建运行转换测试；具体结果记录在 `docs/VALIDATION.md`。

输出 JAR：`build/libs/lt3dimporter-neoforge-1.21.1-0.8.0-alpha.3.jar`。
LT 与 CreativeCore 放在 `libs` 作为开发依赖，不打进 JAR。OBJ 库 0.4.0 随 JAR 内置。
缓存位于父目录 `.gradle-user`。其他机器需调整 `gradle.properties` 中本机 Java 21 路径。

## 来源

上游：https://github.com/Red-and-White-Fox/LT-3D-Importer-Exporter
保留上游 MIT 许可证；具体复用与第三方库说明见 `LICENSE` 和 `THIRD-PARTY-NOTICES.md`。
