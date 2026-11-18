# CopyPoly 1.9.0 API

包名 `com.caca.copypoly.api`，服务器与无 Sable 环境均可使用。开发引用发布 jar，例如 `compileOnly files('libs/copypoly-1.9.0.jar')`，源码包供 IDE 关联。

## 顶点与无向连线

`PolyShape` 是不可变文档，顶点使用稳定整数 ID（不是数组下标）；删除其他点不改变 ID。普通点和自定义点均可作为连接的任意一端。

```java
var b = PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();
int a = b.addNormalVertex(4, 16, 4);
int c = b.addCustomVertex(12, 16, 4);
int d = b.addNormalVertex(12, 16, 12);
int e = b.addCustomVertex(4, 16, 12);
b.connect(a, c).connect(c, d).connect(d, e).connect(e, a);
b.connect(2, a).connect(6, c).connect(7, d).connect(3, e);
PolyShape flat = b.build();
PolyShape dent = flat.withPositions(Map.of(
    a, flat.vertex(a).position().withAxis(1, 8),
    c, flat.vertex(c).position().withAxis(1, 8),
    d, flat.vertex(d).position().withAxis(1, 8),
    e, flat.vertex(e).position().withAxis(1, 8)));
// 所属服务器主线程；目标区块已加载且 pos 处是正常放置的 CopyPoly 方块。
CopypolyApi.setShape(level, pos, dent);
```

坐标以像素为单位，0～16，至多一位小数；最多 64 个顶点，ID 为 0～1,000,000，新 ID 递增。

- `addVertex(VertexType, Point)` / `addNormalVertex(x,y,z)` / `addCustomVertex(x,y,z)` 返回 ID。
- `moveVertex(id, point)` 或 `(id,x,y,z)`；`withPositions(Map<ID,Point>)` 批量修改。
- `connect(a,b)` 无向、去重，`connect(b,a)` 不会重复；`disconnect(a,b)` 同样无向。
- `targets(id)` 返回相邻点；`isDrawnEdge(a,b)` 查询实际用户分界边，不包括隐式三角化对角线。
- `removeVertex(id)` 清除该点及其连接。连线模型从参考表面重新编译，实现删除恢复；逐面模型删除邻接面，保留开口供补面。
- `transformPositions(...)` 同时变换实际坐标与参考坐标，镜像会处理面方向。

第一次连接保留基础三角表面和参考坐标，之后在参考表面的共面区域内插入点并恢复约束边。半截枝杈不会改变表面；闭合内圈和连接原边界的路径会自动形成有效分区。变形后仍使用参考嵌入，避免重新凸包或重新选最近面。

`lineMesh()` 表示连线文档。`pendingConnections()` 给出尚未参与分割的边，`pendingVertices()` 给出原因（如 NEEDS_CONNECTIONS、INVALID_BOUNDARY）。交叉线需显式交点；不会自动创建隐藏点。不同基础面之间无合法切分区域的连接保持待完成。`compile()` 返回实际封闭几何与活动自定义点状态；基础表面或实际变形非法时抛 IllegalArgumentException。

## 直接管理面

`editableMesh()` 固定当前可见几何并保留分界边，随后可删除或创建面，普通点与自定义点均可使用。

```java
var edit = dent.editableMesh().toBuilder();
edit.removeFaces(List.of(triangleIndex));
edit.faceAutomatically(List.of(a, d, c, e)); // 无需选点顺序，优先匹配现有开口
PolyShape draft = edit.build();
var displayOnly = draft.draftSurface();     // 允许开口，用于编辑预览；不提供实体碰撞
var solid = draft.compile().geometry();    // 必须封闭、有体积、无自交
```

- `surfaceTriangles()` 是稳定点 ID 的三角面列表，`removeFaces` 的参数是该列表的三角面下标。
- `faceAutomatically(Collection<ID>)`：至少三个不共线顶点；优先沿现有开口排列，否则自动投影排序；非平面多点会拆成三角面。
- `face(List<ID>)`：较底层的平面多边形接口，调用方传边界顺序，支持凹多边形。
- `triangle(a,b,c)` / `baseFaces(List<Triangle>)`：兼容基础面构造接口。
- `meshFaces(List<Triangle>)`：明确设置全部表面，可为空，用于从头构建草稿；`useConvexBase()` 恢复普通点凸包。
- `manualMesh()` 表示逐面文档。面边界记录为连接供选面使用；新建连线会再次以该表面作为参考。

验证按封闭三角表面执行，支持贯穿孔、环形、内部空腔、多个分离闭合实体。不会要求形状填满 AABB。内部的 `Polyhedron.orientedMesh(points,faces)` 可统一绕序，并根据壳体嵌套深度识别空腔。上限 1344 个三角面；不接受自交、重合面、非流形边、零体积。

## 缺口诊断与应用

`geometry.MeshOpenings.boundaryLoops(faces)` 找开边轮廓；`previewCaps(points,faces)` 返回仅供显示的缺面三角形，支持共面嵌套边界，避免把环状开口的中心孔标成缺面。这些参数使用点数组下标；稳定 ID 文档需先自行映射。

闭合 O 形没有开边，不会被当成缺面。显示用补片不会自动写入模型，服务器依旧只接收完整表面。实际碰撞采用最多 64 个盒的体素近似，和三角表面精度不同。

`CopypolyApi.getShape(BlockGetter,pos)` 返回不可变编辑文档。`setShape(ServerLevel,pos,shape)` 必须在所属服务器主线程，目标区块需已加载且为 CopyPoly 方块；它不会放置方块或加载区块。成功后更新存档、同步、邻居及可选 Sable 碰撞。调用方负责自己的权限检查。

## 序列化与兼容

`packed()` / `PolyShape.fromPacked(int[])` 保存类型、稳定 ID、基础面、参考坐标和连接，输入有明确数量限制。当前连线/逐面文档写版本 3；旧版本 1（有向自定义点）和 2（逐面）仍可读取。旧有向文档原样编译；新增连接时转换为无向分割。

`PolyShapeCodec.toText` / `fromText` 导出导入整个 `CopyPolyGraph 1` 文档，内部自带整数版本；兼容旧坐标及 `CopyPolyMesh 1`。允许导出未完成的编辑草稿，但不能应用未封闭几何。

`copyVertices(shape, IDs)` / `readVertices(text)` 用于选区，保留点类型及内部连接，ID 重新分配；跨选区连接丢弃。只有普通坐标且没有内部连接时使用旧纯坐标格式。合并到其他文档时，需用新点 ID 重映射连接。

请保存 PolyShape 文档以保留切面和删除恢复信息；只保存派生 Polyhedron 会丢失参考表面。
