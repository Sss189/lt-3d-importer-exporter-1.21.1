# 1.21.1 移植验证

## 0.8.0-alpha.3：转换末段卡顿修复（2026-10-17）

- 用户反馈椅子转换停留 94%。检查发现旧版最后的 `LittleGroup.combine(true)` 和序列化在游戏主线程执行。
- 读取现场线程栈时，本次任务已完成；日志确认用户当时仍运行 alpha.1，并生成 `imports/model-15460457675728134895/model.struct`。因此不把这份线程栈当作合并阻塞时的现场证据。
- 导入现在在同一后台任务内完成采样、三轴同色长方体合并、原生蓝图编码和写文件，不再进行主线程全量 LT 合并。
- 合并只连接同色且其余两轴范围完全相同的相邻盒子；不补洞、不重叠。界面增加阶段提示，合并/编码循环检查取消。
- 13 项单元测试通过，包括随机有孔多色体素的逐格恢复、25 万同色小格合并为一个盒子、取消和 alpha.2 的白膜/材质回归。
- 使用实际打包 alpha.3，联合 LT pre233、CreativeCore 2.13.50、Sodium 0.8.13-beta.2、Iris 1.8.14-beta.1 启动独立客户端。在主菜单完成真实椅子后台转换，再由原生 `LittleGroup.load` 读取并展开，逐格比对全部坐标和颜色一致。
- 椅子参数：最长边 4、网格 16、间距 0.25；14,675 小格 → 1,282 盒子。后台任务从开始至完成的日志均在 23:58:41 这一秒内，不包含游戏启动时间；不将此样例结果推广为所有规模的耗时承诺。
- 原生编码另核对网格、负坐标边界和半透明颜色；原有导出几何、UV、染色、偏移、子结构和玻璃断言仍通过。
- 本轮未操作用户世界；LT 原生导入预览、放置及大场景渲染仍需世界内验收。
- 打包测试日志保留在 `build/alpha3-packaged-smoke.log`；测试 JAR 移回 `build/packaged-test-mods`，避免源代码开发时重复加载。
- alpha.3 已安装到整合包，alpha.2 保留为 `.jar.disabled`；用户须完全重启游戏后加载新版。

```text
048a1836e72baf290ace5981818169e0b2ae222e8ecf11fa9fb7242dec35b048  lt3dimporter-neoforge-1.21.1-0.8.0-alpha.3.jar
LT3D_IMPORT_RESULT 14675 voxels -> 1282 boxes
LT3D_BACKGROUND_BLUEPRINT_PASS voxels=14675 boxes=1282
LT3D_SMOKE_PASS
```

## 0.8.0-alpha.2：缺失材质自动白膜（2026-10-17）

- 离线 Gradle build 成功，10 项转换测试通过，0 失败、0 错误。
- 新增覆盖：仅几何/usemtl 无材质库、引用的 MTL 缺失、部分材质名未定义、贴图缺失且原材质完全透明，以及显式覆盖贴图路径错误仍提示失败。
- 原有贴图相对路径、漫反射颜色及透明度回归仍通过。
- 实际椅子（13,506 面）以最长边 4 方块、网格 16、间距 0.25 转换：纯几何 OBJ 与保留悬空 MTL 引用的 OBJ 均生成同样的 14,675 个不透明白色小格。
- 生成 `examples/chair-white.obj`，无材质文件依赖；原始带材质椅子保持原样。
- 新 JAR 已放入整合包 mods，旧 alpha.1 改为 `.jar.disabled` 备份；重启游戏后加载新版。
- 本轮没有运行游戏世界内的放置和渲染验收。

```text
1cfd3d6ec56b2619758b08545cc3688ac114398d4202a58c888b5c4a3babd127  lt3dimporter-neoforge-1.21.1-0.8.0-alpha.2.jar
WHITE_CHAIR_PASS voxels=14675; both material-free and missing-MTL OBJ
```

以下为 alpha.1 的历史验证记录。

验证日期：2026-10-09。版本：`0.8.0-alpha.1`，非官方 alpha 移植。

## 环境与构建

- Minecraft 1.21.1、NeoForge 21.1.256、Java 21。
- LittleTiles 1.6.0-pre233、CreativeCore 2.13.50。
- 打包客户端联合加载 Sodium 0.8.13-beta.2、Iris 1.8.14-beta.1 和 Little Textures 0.1.0-alpha.1。
- `build -Ppackaged` 成功，记录见 `build/package-build.log`。
- JAR 包含 NeoForge 元数据、内嵌 `de.javagl:obj:0.4.0`、MIT LICENSE 和第三方说明；版本占位符已展开。
- `dist` 与 `build/libs` 的 JAR SHA256 一致：

```text
55F85D7B2F1516F9F0CCC92B42E3C52CF8753A3EE1B7D54FADA837B85507D4DB
```

## 转换单元测试

5 项测试全部通过，0 失败、0 错误。结果见 `build/test-results/test/TEST-net.timardo.lt3dimporter.ConversionTest.xml`。

- 负坐标原点规范化和薄三角形采样。
- MTL 相对贴图路径、漫反射颜色和透明度。
- 缺文件、采样限额和取消。
- 负 UV 重复与缺少 UV 时的颜色回退。
- 导出 OBJ 索引、法线、PNG 和独立输出目录。

## 独立开发客户端与正式 JAR

`runClient -Psmoke -Ppackaged` 成功，使用 `run/mods` 中的正式 JAR，关闭源代码模组加载。
正式 JAR 与其内嵌 OBJ 库均成功加载。日志见 `build/package-smoke.log` 和 `run/logs/latest.log`。

客户端在主菜单自动检查以下路径，没有进入或修改存档：

- OBJ → LittleGroup → SNBT → LT 蓝图数据组件往返。
- 实际异步导入任务完整执行，生成可读取的 `.struct`。
- 染色石头、半块木板、子结构玻璃的实际裁切 BakedQuad → OBJ / MTL / PNG。
- 跨方块偏移和比例：X 范围 0–1.5，Y 最大值 1.5。
- sprite 上边界 UV 保持 1；贴图像素通道与染色正确。
- 导出 OBJ 再解析，面数匹配。
- 中文工具界面截图可读，无标签/按钮重叠或截断；见 `run/screenshots/lt3d-tools-smoke.png`。

最终成功标记（北京时间 19:45:59）：

```text
LT3D_SMOKE_PASS import -> LT SNBT/blueprint; actual asynchronous import pipeline; baked export -> OBJ/MTL/PNG; UV, tint, offsets, child/translucent boxes; tool screen screenshot
```

## 尚需实际游戏验收

- 世界内 F8、主手蓝图导出，以及 LT 原生导入界面的粘贴、导入和取出蓝图交互。
- 用户整合包中的复杂模型、大型结构及特殊结构渲染器。
- CTM/repeat/生物群系颜色的视觉效果。联合加载时没有启用 CTM 资源规则。
- 具体光影效果。联合加载 Iris 不等于已验收光影，更不代表能导出光影或 PBR。

蓝图导出缺少世界位置和邻居；本版本不承诺导出结果与世界中连接纹理一致。
门/机械动画、发光叠加和特殊文字/特效不在本版保证范围内。详细限制见 README。

## 开发目录与重复加载

测试后的正式 JAR 已从 `run/mods` 移到 `build/packaged-test-mods/`，避免普通 `runClient` 同时加载源代码和同 modid 的 JAR。
以后复验打包版本时，把 `build/libs` 中本模组 JAR 复制到 `run/mods`，运行 `runClient -Psmoke -Ppackaged`；
结束后将该 JAR 移回独立保存目录。普通开发运行无需执行这一步。

原 Little Textures 项目已移到 `../little-textures/`，离线构建成功，见其 `build/relocation-check.log`。
本次目录整理和测试未改动用户整合包或存档。
