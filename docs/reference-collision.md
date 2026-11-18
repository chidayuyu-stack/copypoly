# 碰撞参考核对

核对日期：2026-11-16。参考为用户本机的 Copycats+ 3.0.11、Sable 2.0.6，仅用于核对行为、API 和调用位置；发布包不包含这些参考 jar。

## 伪装斜坡

`copycats:copycat_slope` 的 `CopycatSlopeBlock.getShape()` 使用 `CCShapes.SLOPE`。其定义是 8 级、每级 2 像素的 AABB。`OutlinedVoxelShape` 保留实体的离散体素网格，并通过 `forAllEdges()` 输出单独定义的 9 条边，其中两条为斜边。斜面轮廓和阶梯实体碰撞是两套表示。

CopyPoly 对任意凸包使用相同的分离原则。`PolyOutlineShape` 共享玩家碰撞的体素网格，只重写边线遍历，沿每个面的有序边界输出一次真实棱边；不生成面的三角剖分线，也不画离散体素的台阶线。碰撞查询独立返回原 AABB 集合。

## Sable

核对了 Sable 2.0.6 内嵌的 Rapier jar。`RapierVoxelColliderBakery` 以 `BlockState` 记忆化碰撞，默认通过 `PhysicsColliderBlockGetter` 在 `BlockPos.ZERO` 查询形状。它不能区分同一方块状态、不同方块实体中的顶点。

因此仅实现 `BlockSubLevelCollisionShape` 不足以支持 CopyPoly；该接口没有位置参数。CopyPoly 在 `RapierPhysicsPipeline.handleChunkSectionAddition()` 和 `handleBlockChange()` 中，对自己的方块改为读取真实位置的方块实体，其他方块保留 Sable 原来的处理。

这两条管线同时处理世界地形和 Sable 的物理结构地块。编辑方块实体没有产生方块状态变化，因此形状提交和载入还需显式通知 `SubLevelPhysicsSystem.handleBlockChange()`，并唤醒附近物体。每个位置交替使用两个原生碰撞句柄，使 Rapier 确实替换体素；删除、区段卸载后将句柄回收到同物理材质的池中，避免每次编辑都创建新句柄。不会修改 Sable 共享的方块状态缓存。

可选 mixin 在 Sable 不存在时跳过，不在普通方块类的签名中引用可选 API。适配范围是 Sable 动态物理结构和地形，不扩展 Create 的 KinematicContraption 管线。

## 验证范围

- 普通游戏测试：斜坡只输出 9 条边，其中两条为真正斜边；实体仍有 8 级阶梯，轮廓包装没有改变碰撞网格；编辑后边线缓存更新。
- Sable 原生接触测试：不同位置、相同方块状态的 4/16 与 12/16 高度分别产生正确落点；连续编辑、删除重放、区段重载后碰撞保持正确。
- Sable 真实结构测试：通过 `SubLevelAssemblyHelper.assembleBlocks()` 组装带顶点数据的多面体，将其翻转落向 6/16 高的自定义地形。先直接测量组装时保存的 4/16 高形状，再改成 12/16、2/16 高度，最后从 NBT 读入 8/16 高度；每次测量真实表面接触位置。使用翻转的薄体以区分默认立方体与实际形状。
- 客户端检查：在 Sable 开启时创建独立世界，瞄准斜坡，核对客户端边线/碰撞数量并截图。

Rapier 接触场景固定在原点附近，避免 GameTest 随机数百万格远坐标引入的原生刚体精度误差。测试源码属于 gametest 源集，不进入发布 jar。

最终服务端结果：26 项单元测试通过；不加载 Sable 时 11 项游戏测试通过；加载 Sable 2.0.6 时 13 项游戏测试通过，两次服务端均正常保存并退出。

客户端在有、无 Sable 两种环境中的轮廓截图与断言均通过。自动客户端退出时，两种环境都在 `ChunkMap.processUnloads()` 循环；另建只放原版石头、完全不放置或编辑 CopyPoly 方块的 Sable 对照世界，也复现相同退出问题，因此不能将该环境的客户端保存退出算作通过。本次没有修改原版或 Sable 的区块卸载实现。相关探针均在截图完成后单独结束进程，没有操作用户的游戏进程或 mods 目录。
