# v0.1.43 · 画面右侧耳鳍参考基准

用户2026-10-10要求将AI伴侣已经定版的右耳鳍位置同步回菜菜参考项目。范围为Caicai-Maid-Live2D-Accessory-Lab；本轮不修改AI伴侣项目。当前IMPLEMENTED / CI PENDING / TRUE DEVICE PENDING。

基准：AI Companion v0.42.89+333，源码a3a5b15051294b1ffa792bb4c06d1d9b9fc16e9f，参数来自用户+332真机诊断；画面上显示9.4°，实际保存精度9.45°。

| 固定参数 | 数值 | 含义 |
| --- | --- | --- |
| X | -0.075 | 向画面左侧移动当前未调整耳宽的7.5% |
| Y | +0.04 | 向上移动当前未调整耳宽的4%，按屏幕宽高比换算 |
| rotation | +9.45° | 以当前耳网格包围盒中心为轴，在屏幕像素空间逆时针旋转 |

## 实现与应用顺序

CaicaiRightEarAdjustment.java及CaicaiEarRotation.java从伴侣定版逐字复制，不重新解释成OverlayCalibration的旧绝对clip偏移/rotation。两边OverlayCalibration.java本身完全相同，原耳校准继续作为基线，不能把上述增量再写入其中，否则会重复应用。

原耳投影与校准 → 女仆头部跟随 → 耳间距约束与共同位移 → 右耳固定增量 → 右耳全部网格统一绘制。每帧投影重建，增量仅在右耳绘制通道应用一次；不改顶点、不逐网格变形、不逐帧累计。

一并同步伴侣+331的像素比例换算，作用于既有左右耳微调及耳对旋转；这保证原基础旋转与新增9.45°均为屏幕上的真实角度，且不引入旋转剪切。舞台缩放/拖动与原先左右摆动、眨眼耳抖、呆毛和尾巴跟随继续由原逻辑负责。菜菜使用自身投影的surfaceWidth/surfaceHeight；伴侣使用其sceneHeight，这只是宿主投影指标不同，几何公式相同。

固定增量不存入偏好。原素材ZIP和.moc3无需更新或重新导入，加载/重置均使用相同基准。诊断增加right_ear_default_x/y/rotation_degrees、单位与参考版本；旧ear_pair_constraint/geometry_comparison测量仍描述固定增量之前的耳对约束层。

## 可核对的源文件

- CaicaiRightEarAdjustment.java SHA-256：83cdbe58c7bcf1961869ae8b466a27a8b7bc87b771f0fa95874ba4cb11488921。
- CaicaiEarRotation.java SHA-256：ccb90901a41094adcde91941c129a4662073c5fc67fc989c4dbbace1fc3b400c。
- OverlayCalibration.java SHA-256：1e619e9baa0c6e8913785db735fd1fbfe1e670c15b88bbe1105b7ea4397e9a2c。

后续移植需保持这三份基准及应用顺序，按接收宿主的实际投影宽高传入指标，不重复烘焙位移。

## 验证

Java几何回归检查多个横竖屏比例/耳大小、位移方向、旋转中心和像素长度守恒，并确认9.45°逆时针角度与无效输入安全回退。另有Android完整编译。仅CI通过不能冒充真机视觉验收；观察需使用相同模型、姿势和舞台缩放比较两应用。
