package com.caca.copypoly.client;

import com.caca.copypoly.*;
import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.geometry.MeshOpenings;
import com.caca.copypoly.geometry.VertexEditing;
import com.caca.copypoly.api.PolyShape;
import com.caca.copypoly.api.PolyShapeCodec;
import com.caca.copypoly.api.VertexType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.joml.Matrix4f;
import java.util.*;

public class PolyScreen extends Screen implements MenuAccess<PolyMenu> {
    private final PolyMenu menu;
    private int leftPos,topPos,imageWidth,imageHeight;
    private final List<String[]> rows=new ArrayList<>();
    private int page, visibleRows, tableWidth, previewX, previewWidth;
    private Button apply;
    private Polyhedron preview;
    private PolyShape document;
    private PolyShape.Compilation compiled;
    private final ShapeHistory history=new ShapeHistory();
    private EditBox coordinateSession;
    private boolean dragCheckpoint;
    private List<BakedQuad> previewQuads=List.of();
    private BlockState material;
    private boolean dirty, valid=true, waiting, dragging;
    private long changedAt;
    private Component error=Component.empty();
    private double yaw=.65,pitch=.45,zoom=1;
    private final PolyGizmo gizmo=new PolyGizmo();
    private final WorldEditorCamera camera=new WorldEditorCamera();
    private final VertexMarquee marquee=new VertexMarquee();
    private boolean panning,panelDragging,cameraMouseCaptured;
    private boolean rightPending,linkDragging,popupOpen;
    private double rightX,rightY,linkX,linkY;
    private int linkSource=-1;
    private boolean linkControl,axesVisible=true,advanced;
    private Polyhedron.Point contextPoint;
    private int popupX,popupY;
    private final List<Button> contextButtons=new ArrayList<>();
    private int previousCursorMode=GLFW.GLFW_CURSOR_NORMAL,panelX=8,panelY=8;
    private double pointerX,pointerY;
    private boolean worldMode=true, minimized, looking, conflict;
    private EditorWorldProjection worldView;
    private PolyBlockEntity previewTarget;
    private final List<EditBox[]> fields=new ArrayList<>();
    private final List<Integer> fieldIndices=new ArrayList<>();
    private boolean facePicking;
    private final Set<Integer> selectedFaces=new LinkedHashSet<>();
    private PolyShape.Connection selectedConnection;
    private boolean showOpenings;
    private long openingsUntil;
    private List<Polyhedron.Triangle> openingFaces=List.of();
    private List<Polyhedron.Point> openingPoints=List.of();
    private boolean updatingFields;
    public PolyScreen(PolyMenu menu,Inventory inv,Component title) {
        super(title);this.menu=menu;preview=menu.initial;
        document=menu.document;compiled=document.compile();
        setRows(document.vertices().stream().map(PolyShape.Vertex::position).toList());
    }
    @Override public PolyMenu getMenu(){return menu;}
    @Override public boolean isPauseScreen(){return false;}
    private void setRows(List<Polyhedron.Point> points) {
        replaceRows(points);
        page=0;gizmo.clearSelection();
    }
    private void replaceRows(List<Polyhedron.Point> points) {
        rows.clear();for(var p:points)rows.add(new String[]{VertexEditing.format(p.x()),VertexEditing.format(p.y()),VertexEditing.format(p.z())});
    }
    @Override protected void init() {
        imageWidth=worldMode?Math.min(210,width-12):Math.min(520,width-12);imageHeight=Math.min(390,height-16);
        super.init();
        leftPos=(width-imageWidth)/2;topPos=(height-imageHeight)/2;
        if(worldMode)clampPanel();
        tableWidth=worldMode?imageWidth-28:Math.max(140,imageWidth/2-12);previewX=leftPos+tableWidth+16;previewWidth=imageWidth-tableWidth-28;
        visibleRows=Math.max(1,(imageHeight-292)/22);
        material=minecraft.level.getBlockEntity(menu.pos) instanceof PolyBlockEntity be?be.getMaterial():AllBlocks.COPYCAT_BASE.getDefaultState();
        if(minecraft.level.getBlockEntity(menu.pos) instanceof PolyBlockEntity be)previewTarget=be;
        stopMouseAction();marquee.cancel();camera.clear();if(worldMode)camera.start();else camera.stop();
        worldView=null;
        gizmo.release();looking=false;rebuild();updatePreview();
    }
    private Component tr(String key,Object...args) {return Component.translatable("screen.copypoly."+key,args);}
    private Button button(String key,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(tr(key),b->action.run()).bounds(x,y,w,20).build());
    }
    private void rebuild() {
        setFocused(null);coordinateSession=null;clearWidgets();fields.clear();fieldIndices.clear();contextButtons.clear();popupOpen=false;
        if(worldMode) {
            button("reset_view",leftPos+imageWidth-96,topPos+3,67,camera::resetView);
            addRenderableWidget(Button.builder(Component.literal(minimized?"+":"−"),b->{minimized=!minimized;gizmo.release();clampPanel();rebuild();})
                .bounds(leftPos+imageWidth-25,topPos+3,20,18).build());
            if(minimized)return;
        }
        int selectorWidth=(tableWidth-4)/2;
        if(advanced) {
            var vertexMode=button("select_vertices",leftPos+12,topPos+28,selectorWidth,()->selectionMode(false));
            var faceMode=button("select_faces",leftPos+16+selectorWidth,topPos+28,selectorWidth,()->selectionMode(true));
            vertexMode.active=facePicking;faceMode.active=!facePicking;
        }
        List<Integer> selected=!selectedFaces.isEmpty() && !facePicking?List.of():gizmo.selectedIndices().stream().filter(i->i<rows.size()).toList();
        int maxPage=Math.max(0,(selected.size()-1)/visibleRows);page=Math.min(page,maxPage);
        int fieldWidth=(tableWidth-44)/3;
        for(int line=0;line<visibleRows;line++) {
            int offset=page*visibleRows+line;if(offset>=selected.size())break;int index=selected.get(offset);
            EditBox[] rowFields=new EditBox[3];fields.add(rowFields);fieldIndices.add(index);
            addRenderableWidget(Button.builder(Component.literal(""+(index+1)),b->{gizmo.select(index,hasControlDown());rebuild();})
                .bounds(leftPos+5,topPos+65+line*22,19,20).build());
            for(int axis=0;axis<3;axis++) {
                final int a=axis;
                EditBox box=new EditBox(font,leftPos+26+axis*(fieldWidth+2),topPos+66+line*22,fieldWidth,18,
                    Component.literal((index+1)+" "+"XYZ".charAt(axis)));
                box.setMaxLength(5);box.setValue(rows.get(index)[axis]);
                box.setResponder(value->{if(!updatingFields){if(coordinateSession!=box){remember();coordinateSession=box;}rows.get(index)[a]=value;gizmo.selected=index;syncDocument();markDirty();}});
                rowFields[axis]=addRenderableWidget(box);
            }
            addRenderableWidget(Button.builder(Component.literal("×"),b->deleteVertices(List.of(document.vertices().get(index).id())))
                .bounds(leftPos+tableWidth-10,topPos+65+line*22,18,20).build());
        }
        int navY=topPos+67+visibleRows*22;
        if(selected.size()>visibleRows) {
            Button prev=addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuild();}).bounds(leftPos+tableWidth-48,navY,24,20).build());prev.active=page>0;
            Button next=addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuild();}).bounds(leftPos+tableWidth-20,navY,24,20).build());next.active=page<maxPage;
        }
        if(advanced) {
            button("create_face",leftPos+12,topPos+imageHeight-154,selectorWidth,this::createFace).active=gizmo.selectedCount()>=3 && !waiting && !conflict;
            button("delete_faces",leftPos+16+selectorWidth,topPos+imageHeight-154,selectorWidth,this::deleteFaces).active=!waiting && !conflict;
            button("advanced_close",leftPos+12,topPos+imageHeight-130,selectorWidth,()->{advanced=false;selectionMode(false);});
            button("clear_faces",leftPos+16+selectorWidth,topPos+imageHeight-130,selectorWidth,this::clearFaces).setTooltip(Tooltip.create(tr("clear_faces_help")));
        }else {
            button("create_face",leftPos+12,topPos+imageHeight-154,selectorWidth,this::createFace).active=selectedFaces.isEmpty() && gizmo.selectedCount()>=3 && !waiting && !conflict;
            button("undo",leftPos+16+selectorWidth,topPos+imageHeight-154,selectorWidth,this::undo);
            button("advanced",leftPos+12,topPos+imageHeight-130,tableWidth,()->{advanced=true;rebuild();});
        }
        int transformWidth=(tableWidth-6)/3;
        for(int axis=0;axis<3;axis++) {
            final int a=axis;
            Button mirror=addRenderableWidget(Button.builder(tr("mirror_axis",""+"XYZ".charAt(axis)),b->transform(true,a))
                .bounds(leftPos+12+axis*(transformWidth+2),topPos+imageHeight-106,transformWidth,20).build());
            mirror.setTooltip(Tooltip.create(tr("mirror_help",""+"XYZ".charAt(axis))));
            Button rotate=addRenderableWidget(Button.builder(tr("rotate_axis",""+"XYZ".charAt(axis)),b->transform(false,a))
                .bounds(leftPos+12+axis*(transformWidth+2),topPos+imageHeight-82,transformWidth,20).build());
            rotate.setTooltip(Tooltip.create(tr("rotate_help",""+"XYZ".charAt(axis))));
            mirror.active=rotate.active=!waiting && !conflict;
        }
        int y=topPos+imageHeight-57;
        int w=(tableWidth-6)/3;
        button("copy",leftPos+12,y,w,this::copyWholeShape)
            .setTooltip(Tooltip.create(tr("copy_all_help")));
        button("paste",leftPos+14+w,y,w,this::pasteWholeShape).setTooltip(Tooltip.create(tr("paste_replace_help")));
        button("reset",leftPos+16+2*w,y,w,()->{remember();loadDocument(PolyShape.fromGeometry(Polyhedron.CUBE),List.of());});
        int footerWidth=(imageWidth-24)/3;
        apply=button("apply",worldMode?leftPos+12+footerWidth:leftPos+imageWidth-140,topPos+imageHeight-28,worldMode?footerWidth:60,()->{
            validate();if(waiting || conflict)return;
            if(!valid){showOpenings=true;openingsUntil=net.minecraft.Util.getMillis()+5000;updateOpenings();return;}
            clearOpenings();
            waiting=true;apply.active=false;error=tr("saving");
            PacketDistributor.sendToServer(new PolyPackets.Apply(menu.containerId,menu.revision,document));
        });
        button("cancel",worldMode?leftPos+16+2*footerWidth:leftPos+imageWidth-74,topPos+imageHeight-28,worldMode?footerWidth:62,this::onClose);
        button(worldMode?"table":"world",leftPos+8,topPos+imageHeight-28,worldMode?footerWidth:88,()->{
            worldMode=!worldMode;minimized=false;init();
        });
        apply.active=!waiting && !conflict;
    }
    private String text() {return String.join("\n",rows.stream().map(r->String.join(", ",r)).toList());}
    private List<Integer> selectedIds() {
        return gizmo.selectedIndices().stream().filter(i->i<document.vertices().size()).map(i->document.vertices().get(i).id()).toList();
    }
    private List<Integer> orderedIds(){return gizmo.selectionOrder().stream().filter(i->i<document.vertices().size()).map(i->document.vertices().get(i).id()).toList();}
    private void selectionMode(boolean faces){if(faces)advanced=true;facePicking=faces;selectedFaces.clear();selectedConnection=null;gizmo.clearSelection();stopMouseAction();marquee.cancel();rebuild();}
    private void createFace() {
        if(waiting || conflict)return;
        try {
            syncDocument();var selected=orderedIds();
            var changed=document.editableMesh().toBuilder().faceAutomatically(selected).build();changed.draftSurface();
            if(changed.equals(document))return;remember();loadDocument(changed,selected);
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void deleteFaces() {
        if(waiting || conflict)return;
        try {
            syncDocument();var surface=document.surfaceTriangles();var selected=new LinkedHashSet<>(selectedFaces);
            if(selected.isEmpty()){var ids=new HashSet<>(selectedIds());for(int i=0;i<surface.size();i++)if(Arrays.stream(surface.get(i).indices()).allMatch(ids::contains))selected.add(i);}
            if(selected.isEmpty()){error=tr("error.no_faces");return;}
            var changed=document.editableMesh().toBuilder().removeFaces(selected).build();remember();loadDocument(changed,List.of());
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void clearFaces() {
        if(waiting || conflict)return;
        try{var changed=document.toBuilder().meshFaces(List.of()).build();remember();loadDocument(changed,List.of());}
        catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void remember(){history.remember(document,orderedIds(),selectedFaces,axesVisible,facePicking,selectedConnection);}
    private void syncDocument() {
        try {
            var points=Polyhedron.parse(text());if(points.size()!=document.vertices().size())return;
            Map<Integer,Polyhedron.Point> positions=new LinkedHashMap<>();for(int i=0;i<points.size();i++)positions.put(document.vertices().get(i).id(),points.get(i));
            document=document.withPositions(positions);
        }catch(IllegalArgumentException invalid){/* Keep invalid coordinate text editable until it is corrected. */}
    }
    private void loadDocument(PolyShape shape,List<Integer> selection) {
        stopMouseAction();marquee.cancel();camera.clear();selectedFaces.clear();selectedConnection=null;document=shape;
        replaceRows(shape.vertices().stream().map(PolyShape.Vertex::position).toList());
        gizmo.replaceSelection(selection.stream().map(id->java.util.stream.IntStream.range(0,shape.vertices().size()).filter(i->shape.vertices().get(i).id()==id).findFirst().orElse(-1)).filter(i->i>=0).toList());
        page=0;markDirty();validate();rebuild();
    }
    private void deleteVertices(List<Integer> ids) {
        if(ids.isEmpty() || conflict || waiting)return;
        try {
            var builder=document.toBuilder();for(int id:ids)builder.removeVertex(id);var changed=builder.build();
            remember();loadDocument(changed,List.of());
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void undo() {
        if(conflict || waiting)return;var state=history.undo();if(state==null)return;
        loadDocument(state.shape(),state.selection());selectedFaces.addAll(state.faces());selectedConnection=state.connection();axesVisible=state.axes();facePicking=state.faceMode();if(facePicking)advanced=true;rebuild();
    }
    private void deleteConnection() {
        if(selectedConnection==null || waiting || conflict)return;
        try {
            var changed=document.toBuilder().disconnect(selectedConnection.source(),selectedConnection.target()).build();
            remember();loadDocument(changed,List.of());axesVisible=false;
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void createVertex(VertexType type,Polyhedron.Point point) {
        try {
            var builder=document.toBuilder();int id=builder.addVertex(type,point);var changed=builder.build();remember();loadDocument(changed,List.of(id));axesVisible=false;
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void connectVertices(int sourceIndex,int targetIndex) {
        if(conflict || waiting || sourceIndex<0 || targetIndex<0 || sourceIndex==targetIndex)return;
        int source=document.vertices().get(sourceIndex).id(),target=document.vertices().get(targetIndex).id();
        try {
            var builder=document.toBuilder();
            boolean removing=document.targets(source).contains(target);
            if(removing)builder.disconnect(source,target);else builder.connect(source,target);
            var changed=builder.build();
            if(!removing && document.lineMesh() && document.connections().stream().anyMatch(c->!document.pendingConnections().contains(c) && changed.pendingConnections().contains(c))) {
                error=tr("error.line_crossing");return;
            }
            remember();loadDocument(changed,List.of(source));axesVisible=false;
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void copyWholeShape() {
        try{syncDocument();if(valid || document.manualMesh() || document.lineMesh())minecraft.keyboardHandler.setClipboard(PolyShapeCodec.toText(document));}
        catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void pasteWholeShape() {
        try {
            var shape=PolyShapeCodec.fromText(minecraft.keyboardHandler.getClipboard());remember();loadDocument(shape,List.of());
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void copySelectedVertices() {
        try {
            syncDocument();minecraft.keyboardHandler.setClipboard(PolyShapeCodec.copyVertices(document,selectedIds()));
            error=Component.empty();
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    private void pasteVertices() {
        if(conflict){error=tr("error.changed");return;}
        try {
            String clipboard=minecraft.keyboardHandler.getClipboard();
            var copied=PolyShapeCodec.readVertices(clipboard);
            if(rows.size()+copied.vertices().size()>Polyhedron.MAX_POINTS)throw new IllegalArgumentException("paste_limit");
            var builder=document.toBuilder();Map<Integer,Integer> added=new LinkedHashMap<>();
            for(var v:copied.vertices())added.put(v.id(),builder.addVertex(v.type(),v.position()));
            for(var link:copied.connections())builder.connect(added.get(link.source()),added.get(link.target()));
            var shape=builder.build();remember();loadDocument(shape,List.copyOf(added.values()));
        }catch(IllegalArgumentException ex){error=tr("error."+("count".equals(ex.getMessage())?"paste_limit":ex.getMessage()));}
    }
    private void transform(boolean mirror,int axis) {
        if(waiting || conflict)return;
        try {
            validate();if(!valid && !document.manualMesh())return;
            var changed=document.transformPositions(p->mirror?VertexEditing.mirror(p,axis):VertexEditing.rotate(p,axis,hasShiftDown()?-1:1));
            if(!document.manualMesh())changed.compile();var selection=selectedIds();remember();loadDocument(changed,selection);
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
    }
    void updateMovement(net.minecraft.client.player.Input input) {
        WorldEditorCamera.suppressPlayerInput(input);
    }
    boolean worldCameraActive() {return worldMode && camera.active();}
    private void markDirty() {dirty=true;changedAt=System.currentTimeMillis();}
    private void validate() {
        dirty=false;
        try {
            var parsed=Polyhedron.parse(text());if(parsed.size()!=document.vertices().size())throw new IllegalArgumentException("format");
            syncDocument();compiled=document.compile();preview=compiled.geometry();valid=true;error=Component.empty();updatePreview();
        }
        catch(IllegalArgumentException ex){
            valid=false;error=tr("error."+ex.getMessage());
            if(document.manualMesh() || document.lineMesh())try {
                preview=document.draftSurface();compiled=new PolyShape.Compilation(preview,Set.of(),document.lineMesh()?document.pendingVertices():Map.of());updatePreview();
                Map<Long,Integer> edges=new HashMap<>();for(var t:document.baseFaces()){int[] p=t.indices();for(int i=0;i<3;i++){int a=p[i],b=p[(i+1)%3];long e=(long)Math.min(a,b)<<32|Math.max(a,b)&0xffffffffL;edges.merge(e,1,Integer::sum);}}
                long open=edges.values().stream().filter(n->n==1).count();
                error=open>0 || edges.isEmpty()?tr("draft_open",open):tr("draft_invalid");
            }catch(IllegalArgumentException ignored){/* Retain coordinates and show the validation error until repaired. */}
        }
        if(conflict)error=tr("error.changed");
        expireOpenings();
        if(valid)clearOpenings();else if(showOpenings)updateOpenings();
        if(apply!=null)apply.active=!waiting && !conflict;
    }
    private void clearOpenings(){showOpenings=false;openingsUntil=0;openingFaces=List.of();openingPoints=List.of();}
    private void expireOpenings() {
        if(showOpenings && net.minecraft.Util.getMillis()>=openingsUntil) {
            clearOpenings();if(error.equals(tr("openings_marked")))error=Component.empty();
        }
    }
    private void updateOpenings() {
        try {
            var vertices=document.vertices();Map<Integer,Integer> index=new HashMap<>();for(int i=0;i<vertices.size();i++)index.put(vertices.get(i).id(),i);
            openingPoints=vertices.stream().map(PolyShape.Vertex::position).toList();
            var faces=document.surfaceTriangles().stream().map(t->new Polyhedron.Triangle(index.get(t.a()),index.get(t.b()),index.get(t.c()))).toList();
            openingFaces=MeshOpenings.previewCaps(openingPoints,faces);
            if(faces.isEmpty() && openingPoints.size()>=4)try {
                var hull=Polyhedron.of(openingPoints);var indices=hull.points().stream().map(openingPoints::indexOf).toList();
                openingFaces=hull.triangles().stream().map(t->new Polyhedron.Triangle(indices.get(t.a()),indices.get(t.b()),indices.get(t.c()))).toList();
            }catch(IllegalArgumentException empty){/* Flat point sets have no enclosing volume to highlight. */}
            if(!openingFaces.isEmpty())error=tr("openings_marked");
        }catch(IllegalArgumentException ex){openingFaces=List.of();}
    }
    private void updatePreview() {
        if(minecraft.getBlockRenderer().getBlockModel(Copypoly.POLY.get().defaultBlockState()) instanceof PolyModel model)
            previewQuads=model.preview(preview,material);
        if(previewTarget!=null)previewTarget.setClientPreview(worldMode && !conflict?preview:null);
    }
    public void result(String message) {
        waiting=false;
        if(message.isEmpty()){onClose();return;}
        error=tr("error."+message);if(apply!=null)apply.active=!conflict;
    }
    @Override public void tick() {
        super.tick();
        expireOpenings();
        if(minecraft.player==null || !minecraft.player.isAlive() || minecraft.player.isRemoved() || minecraft.player.containerMenu!=menu){onClose();return;}
        if(!minecraft.isWindowActive()){stopMouseAction();marquee.cancel();}
        if(worldMode) {
            if(!camera.active()){onClose();return;}
            camera.tick(!waiting && !(getFocused() instanceof EditBox) && !gizmo.dragging() && !marquee.pending() && !panelDragging && !popupOpen && !rightPending && linkSource<0 && inCanvas(pointerX,pointerY));
        }
        if(dirty && (gizmo.dragging() || System.currentTimeMillis()-changedAt>=160))validate();
        if(!menu.stillValid(minecraft.player)){onClose();return;}
        if(previewTarget!=null && previewTarget.revision()!=menu.revision && !waiting) {
            conflict=true;gizmo.release();error=tr("error.changed");if(apply!=null)apply.active=false;previewTarget.setClientPreview(null);
        }
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial) {
        if(!worldMode)g.fillGradient(0,0,width,height,0xb0101820,0xd0101820);
        renderBg(g,partial,mx,my);
    }
    private void renderBg(GuiGraphics g,float partial,int mx,int my) {
        pointerX=mx;pointerY=my;
        if(worldMode){drawWorld(g);drawMarquee(g);}
        int panelHeight=worldMode && minimized?25:imageHeight;
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+panelHeight,0xf218252b);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+25,0xff273c43);
        g.drawString(font,worldMode?tr("world_title"):title,leftPos+12,topPos+9,0xffe3f2ec,false);
        if(worldMode && minimized)return;
        if(!advanced)g.drawWordWrap(font,tr("gesture_hint"),leftPos+12,topPos+29,tableWidth,0xffb7d9ec);
        int fieldWidth=(tableWidth-44)/3;
        for(int a=0;a<3;a++)g.drawString(font,""+"XYZ".charAt(a),leftPos+26+a*(fieldWidth+2),topPos+54,0xff94b5ad,false);
        for(int line=0;line<fieldIndices.size();line++) {
            int y=topPos+65+line*22;
            g.fill(leftPos+3,y-1,leftPos+tableWidth+9,y,0xff86e7ff);
        }
        if(gizmo.selectedCount()==0 || !selectedFaces.isEmpty() && !facePicking)g.drawWordWrap(font,tr(selectedConnection!=null?"line_selected_help":!selectedFaces.isEmpty()?"face_selected_help":facePicking?"empty_face_selection":"empty_selection"),leftPos+12,topPos+70,tableWidth-12,0xffa6b4b9);
        if(gizmo.selected>=0 && gizmo.selected<document.vertices().size()) {
            var v=document.vertices().get(gizmo.selected);
            if(v.type()==VertexType.CUSTOM && !document.manualMesh() && !document.lineMesh()) {
                var reason=compiled.pending().get(v.id());
                Component status=tr(reason==null?"custom.active":"custom."+reason.name().toLowerCase(Locale.ROOT),document.targets(v.id()).size());
                g.enableScissor(leftPos+8,topPos+imageHeight-186,leftPos+tableWidth+8,topPos+imageHeight-157);
                g.drawWordWrap(font,status,leftPos+10,topPos+imageHeight-185,tableWidth-4,0xffd6adff);
                g.disableScissor();
            }
        }
        if(document.manualMesh()) {
            g.enableScissor(leftPos+8,topPos+imageHeight-186,leftPos+tableWidth+8,topPos+imageHeight-157);
            g.drawWordWrap(font,tr("mesh_status",document.baseFaces().size(),selectedFaces.size()),leftPos+10,topPos+imageHeight-185,tableWidth-4,0xffb7d9ec);g.disableScissor();
        }
        if(document.lineMesh()) {
            g.enableScissor(leftPos+8,topPos+imageHeight-186,leftPos+tableWidth+8,topPos+imageHeight-157);
            g.drawWordWrap(font,tr("line_status",document.pendingConnections().size()),leftPos+10,topPos+imageHeight-185,tableWidth-4,0xffcfb7e8);g.disableScissor();
        }
        if(!worldMode) {
            g.fill(previewX,topPos+30,previewX+previewWidth,topPos+imageHeight-64,0xff101c22);
            drawPreview(g);
            g.drawString(font,tr("count",rows.size(),Polyhedron.MAX_POINTS),previewX+5,topPos+35,0xffa6c9bd,false);
            g.drawWordWrap(font,tr("drag"),previewX+5,topPos+imageHeight-91,previewWidth-10,0xffa6b4b9);
            g.drawString(font,tr("step",hasControlDown()?"0.1":"1"),previewX+5,topPos+imageHeight-55,0xffa6b4b9,false);
        }
        if(!error.getString().isEmpty()) {
            g.enableScissor(leftPos+8,topPos+imageHeight-216,leftPos+tableWidth+8,topPos+imageHeight-187);
            g.drawWordWrap(font,error,leftPos+10,topPos+imageHeight-215,tableWidth-4,waiting?0xffaacfc1:0xffffa599);
            g.disableScissor();
        }
    }
    private Vec3 rotate(Vec3 p) {
        double x=Math.cos(yaw)*p.x+Math.sin(yaw)*p.z,z=-Math.sin(yaw)*p.x+Math.cos(yaw)*p.z;
        return new Vec3(x,Math.cos(pitch)*p.y-Math.sin(pitch)*z,Math.sin(pitch)*p.y+Math.cos(pitch)*z);
    }
    private Vec3 project(Vec3 p) {
        Vec3 r=rotate(p.subtract(.5,.5,.5));double scale=Math.min(previewWidth-30,imageHeight-125)*.42*zoom;
        return new Vec3(previewX+previewWidth*.5+r.x*scale,topPos+30+(imageHeight-94)*.5-r.y*scale,r.z);
    }
    private record PreviewQuad(BakedQuad source,Vec3[] points,double depth) {}
    private void drawPreview(GuiGraphics g) {
        g.enableScissor(previewX,topPos+47,previewX+previewWidth,topPos+imageHeight-104);
        List<PreviewQuad> sorted=new ArrayList<>();
        for(BakedQuad q:previewQuads) {
            int[] data=q.getVertices();int stride=data.length/4;Vec3[] p=new Vec3[4];double depth=0;
            for(int i=0;i<4;i++){p[i]=project(new Vec3(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])));depth+=p[i].z;}
            // Screen Y is inverted; clockwise screen triangles face the viewer.
            if(preview.closed() && (p[1].x-p[0].x)*(p[2].y-p[0].y)-(p[1].y-p[0].y)*(p[2].x-p[0].x)>=0)continue;
            sorted.add(new PreviewQuad(q,p,depth/4));
        }
        sorted.sort(Comparator.comparingDouble(PreviewQuad::depth));
        g.flush();
        for(PreviewQuad quad:sorted) {
            int tint=quad.source.isTinted()?minecraft.getBlockColors().getColor(material,minecraft.level,menu.pos,quad.source.getTintIndex()):0xffffff;
            float shade=.68f+.32f*Math.max(0,(float)rotate(Vec3.atLowerCornerOf(quad.source.getDirection().getNormal())).z);
            int color=0xff000000 | ((int)(((tint>>16)&255)*shade)<<16) | ((int)(((tint>>8)&255)*shade)<<8) | (int)((tint&255)*shade);
            VertexConsumer v=g.bufferSource().getBuffer(RenderType.text(TextureAtlas.LOCATION_BLOCKS));
            int[] data=quad.source.getVertices();int stride=data.length/4;
            for(int i=0;i<4;i++)v.addVertex(g.pose().last().pose(),(float)quad.points[i].x,(float)quad.points[i].y,0)
                .setColor(color).setUv(Float.intBitsToFloat(data[i*stride+4]),Float.intBitsToFloat(data[i*stride+5])).setLight(LightTexture.FULL_BRIGHT);
        }
        g.flush();
        Set<Long> edges=new HashSet<>();
        Set<Long> boundary=new HashSet<>();for(var edge:preview.edges())boundary.add(edgeKey(edge.a(),edge.b()));
        for(var face:preview.faces()) {
            Vec3 n=new Vec3(face.plane().x(),face.plane().y(),face.plane().z());if(rotate(n).z<=0)continue;
            for(int i=0;i<face.vertices().size();i++) {
                var a=face.vertices().get(i);var b=face.vertices().get((i+1)%face.vertices().size());
                if(!boundary.contains(edgeKey(a,b)) || !edges.add(edgeKey(a,b)))continue;
                line(g,project(new Vec3(a.x()/16.,a.y()/16.,a.z()/16.)),project(new Vec3(b.x()/16.,b.y()/16.,b.z()/16.)),0xff93d3bc);
            }
        }
        drawOpeningFaces(g);drawEditingPoints(g);
        drawMarquee(g);
        g.disableScissor();
    }
    private static void line(GuiGraphics g,Vec3 a,Vec3 b,int color) {
        PolyGizmo.line(g,a,b,color,1);
    }
    private static long edgeKey(Polyhedron.Point a,Polyhedron.Point b) {
        return (long)Math.min(a.packed(),b.packed())<<32 | Math.max(a.packed(),b.packed())&0xffffffffL;
    }
    private List<Polyhedron.Point> draftPoints() {
        try{return Polyhedron.parse(text());}catch(IllegalArgumentException ex){return List.of();}
    }
    public void captureWorldView(Matrix4f view,Matrix4f projection,Vec3 camera,float partialTick) {
        worldView=new EditorWorldProjection(view,projection,camera,width,height,
                EditorBlockTransform.capture(minecraft.level,menu.pos,partialTick));
    }
    private Vec3 editProject(Vec3 local) {
        if(!worldMode){Vec3 p=project(local);return new Vec3(p.x,p.y,-p.z);}
        return worldView==null?null:worldView.project(local);
    }
    private PolyGizmo.AxisCoordinate axisMapper() {
        if(!worldMode)return (x,y,origin,axis)->{
            Vec3 a=project(origin),b=project(origin.add(PolyGizmo.axis(axis).scale(1/16.0)));
            double dx=b.x-a.x,dy=b.y-a.y;
            return ((x-a.x)*dx+(y-a.y)*dy)/(dx*dx+dy*dy);
        };
        // Read the latest frame while dragging: a structure can move or rotate
        // between mouse events, so the click-time world transform must not be frozen.
        return (x,y,origin,axis)->worldView==null?Double.NaN:worldView.axisCoordinate(x,y,origin,axis);
    }
    private void drawWorld(GuiGraphics g) {
        if(!conflict) {
            drawOpeningFaces(g);
            for(var edge:preview.edges()) {
                var first=edge.a();var second=edge.b();
                Vec3 a=editProject(PolyGizmo.position(first));
                Vec3 b=editProject(PolyGizmo.position(second));
                line(g,a,b,0xffa4d4c3);
            }
            drawEditingPoints(g);
        }
        Component status=tr("world_help");
        int hudLeft=!minimized && leftPos<width/2?leftPos+imageWidth+10:10;
        int hudRight=!minimized && leftPos>=width/2?leftPos-10:width-10;
        int helpWidth=Math.max(60,hudRight-hudLeft);
        int lines=font.split(status,helpWidth).size(),y=height-52-lines*10;
        g.fill(hudLeft-5,y-4,hudLeft+helpWidth+5,height-46,0xb0101c22);
        g.drawWordWrap(font,status,hudLeft,y,helpWidth,0xffd3e7e2);
        Component info=tr("count",rows.size(),Polyhedron.MAX_POINTS).copy().append("  ·  ").append(tr("step",hasControlDown()?"0.1":"1"));
        if(gizmo.selectedCount()>0)info=info.copy().append("  ·  ").append(tr("selection",gizmo.selectedCount()));
        if(gizmo.selected>=0 && gizmo.selected<rows.size())info=info.copy().append("  #"+(gizmo.selected+1)+"  "+String.join(", ",rows.get(gizmo.selected)));
        int infoLines=font.split(info,helpWidth).size();
        g.drawWordWrap(font,info,hudLeft,y-8-infoLines*10,helpWidth,0xffffdc8b);
    }
    private void drawEditingPoints(GuiGraphics g) {
        List<Polyhedron.Triangle> surface;
        try{surface=document.surfaceTriangles();}catch(IllegalArgumentException ex){surface=List.of();}
        if(facePicking || !selectedFaces.isEmpty() || document.manualMesh()) {
            Map<Long,Integer> edges=new HashMap<>();
            for(int i=0;i<surface.size();i++) {
                var t=surface.get(i);int[] ids=t.indices();
                Vec3[] p=Arrays.stream(ids).mapToObj(id->editProject(PolyGizmo.position(document.vertex(id).position()))).toArray(Vec3[]::new);
                if(selectedFaces.contains(i) && Arrays.stream(p).allMatch(Objects::nonNull))SmoothOverlay.triangle(g,p[0].x,p[0].y,p[1].x,p[1].y,p[2].x,p[2].y,0x666acbff);
                for(int j=0;j<3;j++){int a=ids[j],b=ids[(j+1)%3];long e=(long)Math.min(a,b)<<32|Math.max(a,b)&0xffffffffL;edges.merge(e,1,Integer::sum);}
                if(facePicking)for(int j=0;j<3;j++)SmoothOverlay.line(g,p[j],p[(j+1)%3],0x779fc9d8,SmoothOverlay.pixel());
            }
            for(var e:edges.entrySet())if(e.getValue()!=2) {
                var a=document.vertex((int)(e.getKey()>>32));var b=document.vertex(e.getKey().intValue());
                line(g,editProject(PolyGizmo.position(a.position())),editProject(PolyGizmo.position(b.position())),e.getValue()==1?0xffffb866:0xffff6868);
            }
        }
        for(var link:document.connections()) {
            Vec3 a=editProject(PolyGizmo.position(document.vertex(link.source()).position()));
            Vec3 b=editProject(PolyGizmo.position(document.vertex(link.target()).position()));
            boolean pending=document.lineMesh()?document.pendingConnections().contains(link):compiled.pending().containsKey(link.source());
            boolean selected=link.equals(selectedConnection);
            if(selected)SmoothOverlay.line(g,a,b,0xff17212a,9*SmoothOverlay.pixel());
            if(pending && a!=null && b!=null) {
                double length=Math.hypot(b.x-a.x,b.y-a.y);int segments=Math.min(512,(int)Math.ceil(length/(6*SmoothOverlay.pixel())));
                for(int i=0;i<segments;i+=2)SmoothOverlay.line(g,a.lerp(b,(double)i/segments),a.lerp(b,(double)Math.min(i+1,segments)/segments),selected?0xffffec86:0xffb59cca,(selected?6:2)*SmoothOverlay.pixel());
            }else SmoothOverlay.line(g,a,b,selected?0xffffec86:0xffcf96ff,(selected?6:3)*SmoothOverlay.pixel());
        }
        if(linkDragging && linkSource>=0 && linkSource<rows.size()) {
            Vec3 a=editProject(PolyGizmo.position(document.vertices().get(linkSource).position()));
            int target=PolyGizmo.pickVertex(pointerX,pointerY,draftPoints(),this::editProject,linkSource);
            Vec3 b=target<0?new Vec3(pointerX,pointerY,0):editProject(PolyGizmo.position(document.vertices().get(target).position()));
            line(g,a,b,0xffe3c3ff);
        }
        if(linkSource>=0 && linkSource<document.vertices().size()) {
            Vec3 p=editProject(PolyGizmo.position(document.vertices().get(linkSource).position()));
            if(p!=null)SmoothOverlay.circle(g,p.x,p.y,11*SmoothOverlay.pixel(),0xffe3c3ff);
        }
        gizmo.draw(g,font,draftPoints(),this::editProject,
            i->document.vertices().get(i).type()==VertexType.CUSTOM?0xffcf96ff:0xffffdc8b,
            i->compiled.pending().containsKey(document.vertices().get(i).id()),handlesVisible());
    }
    private boolean handlesVisible(){return axesVisible && linkSource<0 && !linkDragging && !marquee.active();}
    private void drawOpeningFaces(GuiGraphics g) {
        if(!showOpenings || conflict)return;
        for(var t:openingFaces) {
            Vec3 a=editProject(PolyGizmo.position(openingPoints.get(t.a()))),b=editProject(PolyGizmo.position(openingPoints.get(t.b()))),c=editProject(PolyGizmo.position(openingPoints.get(t.c())));
            if(a==null || b==null || c==null)continue;
            SmoothOverlay.triangle(g,a.x,a.y,b.x,b.y,c.x,c.y,0x88ff4343);
            for(var e:new Vec3[][]{{a,b},{b,c},{c,a}})SmoothOverlay.line(g,e[0],e[1],0xddff6868,2*SmoothOverlay.pixel());
        }
    }
    private EditorWorldProjection.Ray editRay(double x,double y) {
        EditorWorldProjection.Ray ray;
        if(worldMode){if(worldView==null)return null;ray=worldView.ray(x,y);}
        else {
            double scale=Math.min(previewWidth-30,imageHeight-125)*.42*zoom;
            Vec3 origin=new Vec3((x-previewX-previewWidth*.5)/scale,-(y-topPos-30-(imageHeight-94)*.5)/scale,10);
            ray=new EditorWorldProjection.Ray(unrotate(origin).add(.5,.5,.5),unrotate(new Vec3(0,0,-1)));
        }
        return ray;
    }
    private Polyhedron.Point surfaceHit(double x,double y) {
        var ray=editRay(x,y);if(ray==null)return null;
        var point=EditorSurfacePicking.hit(preview,ray.origin(),ray.direction(),hasControlDown());
        // An empty/open mesh can still add vertices inside the target block's bounds.
        return point==null && document.manualMesh()?EditorSurfacePicking.hit(Polyhedron.CUBE,ray.origin(),ray.direction(),hasControlDown()):point;
    }
    private boolean pickFace(double x,double y,boolean multiple) {
        var ray=editRay(x,y);if(ray==null)return false;
        selectedConnection=null;
        try {
            var triangles=document.surfaceTriangles();
            List<PolyShape.Vertex> vertices=document.vertices();Map<Integer,Integer> index=new HashMap<>();for(int i=0;i<vertices.size();i++)index.put(vertices.get(i).id(),i);
            var indexed=triangles.stream().map(t->new Polyhedron.Triangle(index.get(t.a()),index.get(t.b()),index.get(t.c()))).toList();
            var mesh=Polyhedron.surface(vertices.stream().map(PolyShape.Vertex::position).toList(),indexed);
            var hit=EditorSurfacePicking.triangle(mesh,ray.origin(),ray.direction());
            if(!multiple)selectedFaces.clear();
            if(hit!=null) {
                Set<Integer> patch=new LinkedHashSet<>();patch.add(indexed.indexOf(mesh.triangles().get(hit.index())));
                if(!hasShiftDown()) {
                    var plane=mesh.faces().get(hit.index()).plane();boolean progress=true;
                    while(progress){progress=false;for(int i=0;i<mesh.triangles().size();i++) {
                        final int candidate=indexed.indexOf(mesh.triangles().get(i));
                        if(!patch.contains(candidate) && mesh.faces().get(i).plane().equals(plane)
                                && patch.stream().anyMatch(j->shareUncutEdge(triangles.get(candidate),triangles.get(j)))){patch.add(candidate);progress=true;}
                    }}
                }
                if(multiple && selectedFaces.containsAll(patch))selectedFaces.removeAll(patch);else selectedFaces.addAll(patch);
            }
            Set<Integer> ids=new LinkedHashSet<>();for(int i:selectedFaces)for(int id:triangles.get(i).indices())ids.add(index.get(id));
            if(facePicking)gizmo.replaceSelection(ids);else gizmo.clearSelection();page=0;rebuild();return hit!=null;
        }catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());return false;}
    }
    private boolean shareUncutEdge(Polyhedron.Triangle a,Polyhedron.Triangle b) {
        int[] shared=Arrays.stream(a.indices()).filter(b::uses).toArray();return shared.length==2 && !document.isDrawnEdge(shared[0],shared[1]);
    }
    private Vec3 unrotate(Vec3 p) {
        double y=Math.cos(pitch)*p.y+Math.sin(pitch)*p.z,z=-Math.sin(pitch)*p.y+Math.cos(pitch)*p.z;
        return new Vec3(Math.cos(yaw)*p.x-Math.sin(yaw)*z,y,Math.sin(yaw)*p.x+Math.cos(yaw)*z);
    }
    private void closePopup() {
        for(var b:contextButtons)removeWidget(b);contextButtons.clear();popupOpen=false;setFocused(null);
    }
    private void openPopup(double x,double y,Polyhedron.Point point) {
        closePopup();camera.clear();popupOpen=true;
        int w=Math.max(130,Math.max(font.width(tr("add_custom")),font.width(tr("add_normal")))+16);
        popupX=Math.clamp((int)x,0,Math.max(0,width-w));popupY=Math.clamp((int)y,0,Math.max(0,height-44));
        for(int i=0;i<2;i++) {
            VertexType type=i==0?VertexType.CUSTOM:VertexType.NORMAL;
            Button b=button(i==0?"add_custom":"add_normal",popupX,popupY+i*22,w,()->{closePopup();createVertex(type,point);});
            b.active=rows.size()<Polyhedron.MAX_POINTS;contextButtons.add(b);
        }
    }
    private boolean inCanvas(double x,double y) {
        if(worldMode)return !(x>=leftPos && x<leftPos+imageWidth && y>=topPos && y<topPos+(minimized?25:imageHeight));
        return x>=previewX && x<previewX+previewWidth && y>=topPos+47 && y<topPos+imageHeight-96;
    }
    private void clampPanel() {
        panelX=Math.clamp(panelX,0,Math.max(0,width-imageWidth));
        panelY=Math.clamp(panelY,0,Math.max(0,height-(minimized?25:imageHeight)));
        leftPos=panelX;topPos=panelY;
    }
    private void stopMouseAction() {
        panning=false;looking=false;dragging=false;panelDragging=false;
        gizmo.release();
        rightPending=false;linkSource=-1;linkDragging=false;linkControl=false;dragCheckpoint=false;
        if(cameraMouseCaptured && minecraft!=null) {
            GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(),GLFW.GLFW_CURSOR,previousCursorMode);
            cameraMouseCaptured=false;
        }
    }
    private void captureCameraMouse() {
        if(cameraMouseCaptured)return;
        long window=minecraft.getWindow().getWindow();
        previousCursorMode=GLFW.glfwGetInputMode(window,GLFW.GLFW_CURSOR);
        GLFW.glfwSetInputMode(window,GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_DISABLED);cameraMouseCaptured=true;
    }
    private void updateMarquee() {
        if(marquee.active()) {
            boolean wasFace=!selectedFaces.isEmpty() || selectedConnection!=null;selectedFaces.clear();selectedConnection=null;
            var selected=marquee.find(draftPoints(),this::editProject,p->inCanvas(p.x,p.y));
            if(wasFace || !new HashSet<>(gizmo.selectedIndices()).equals(new HashSet<>(selected))){gizmo.replaceSelection(selected);rebuild();}
        }
    }
    private void drawMarquee(GuiGraphics g) {
        if(!marquee.active())return;
        double x0=marquee.minX(),y0=marquee.minY(),x1=marquee.maxX(),y1=marquee.maxY();
        g.fill((int)x0,(int)y0,(int)Math.ceil(x1),(int)Math.ceil(y1),0x286acbff);
        Vec3 a=new Vec3(x0,y0,0),b=new Vec3(x1,y0,0),c=new Vec3(x1,y1,0),d=new Vec3(x0,y1,0);
        for(Vec3[] edge:new Vec3[][]{{a,b},{b,c},{c,d},{d,a}})SmoothOverlay.line(g,edge[0],edge[1],0xff86d9ff,2*SmoothOverlay.pixel());
    }
    @Override public void mouseMoved(double x,double y){pointerX=x;pointerY=y;super.mouseMoved(x,y);}
    @Override public boolean mouseClicked(double x,double y,int button) {
        pointerX=x;pointerY=y;
        if(waiting)return true;
        if(popupOpen) {
            boolean over=contextButtons.stream().anyMatch(b->b.isMouseOver(x,y));
            if(over){for(var b:List.copyOf(contextButtons))if(b.mouseClicked(x,y,button))break;return true;}
            closePopup();return true;
        }
        if(inCanvas(x,y)) {
            setFocused(null);coordinateSession=null;
            boolean control=hasControlDown();
            if(button==0 && facePicking && !conflict) {
                if(handlesVisible() && PolyGizmo.pickVertex(x,y,draftPoints(),this::editProject,-1)<0 && gizmo.clickAxes(x,y,draftPoints(),this::editProject,axisMapper()))return true;
                pickFace(x,y,control);axesVisible=true;return true;
            }
            var connection=button==0 && !control && !conflict?EditorLinePicking.pick(document,x,y,this::editProject,Math.max(3,6*SmoothOverlay.pixel())):null;
            if(button==0 && !conflict) {
                // Near a wire, prioritize the visible point marker rather than its larger convenience hit area.
                int vertex=PolyGizmo.pickVertex(x,y,draftPoints(),this::editProject,-1,connection==null?8:10*SmoothOverlay.pixel());
                if(vertex>=0) {
                    linkSource=vertex;linkX=x;linkY=y;linkControl=control;linkDragging=false;axesVisible=false;
                    if(control)marquee.begin(x,y);return true;
                }
            }
            boolean picked=button==0 && !conflict && handlesVisible() && gizmo.clickAxes(x,y,draftPoints(),this::editProject,axisMapper());
            if(button==0 && control && !gizmo.dragging())marquee.begin(x,y);
            if(picked) {
                if(!gizmo.dragging()) {
                    page=0;rebuild();
                }
                return true;
            }
            if(button==0 && marquee.pending())return true;
            if(connection!=null) {
                selectedConnection=connection;selectedFaces.clear();gizmo.clearSelection();axesVisible=false;page=0;rebuild();return true;
            }
            if(button==1){rightPending=true;rightX=x;rightY=y;contextPoint=surfaceHit(x,y);return true;}
            if(button==0 && !control && !conflict && pickFace(x,y,false)){axesVisible=false;return true;}
            if(button==0){panning=worldMode;dragging=!worldMode;if(worldMode)captureCameraMouse();return true;}
            return true;
        }
        if(super.mouseClicked(x,y,button))return true;
        if(worldMode && button==0 && y>=topPos && y<topPos+25){panelDragging=true;return true;}
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        pointerX=x;pointerY=y;
        if(button==0 && gizmo.dragging()) {
            boolean changed=false;
            for(var moved:gizmo.dragSelection(x,y,hasControlDown()).entrySet()) {
                int index=moved.getKey();var p=moved.getValue();
                if(index<0 || index>=rows.size())continue;
                String[] row={VertexEditing.format(p.x()),VertexEditing.format(p.y()),VertexEditing.format(p.z())};
                if(!Arrays.equals(rows.get(index),row)){
                    if(!dragCheckpoint){remember();dragCheckpoint=true;}
                    rows.set(index,row);changed=true;
                }
            }
            if(changed){syncDocument();markDirty();syncFields();}
            return true;
        }
        if(button==0 && marquee.pending()){marquee.drag(x,y,3*SmoothOverlay.pixel());if(marquee.active()){linkSource=-1;linkDragging=false;axesVisible=false;}updateMarquee();return true;}
        if(button==0 && linkSource>=0) {
            if(Math.hypot(x-linkX,y-linkY)>3*SmoothOverlay.pixel())linkDragging=true;
            return true;
        }
        if(button==0 && panelDragging){panelX+=(int)Math.round(dx);panelY+=(int)Math.round(dy);clampPanel();rebuild();return true;}
        if(button==0 && panning){camera.pan(dx,dy);return true;}
        if(button==1 && rightPending && Math.hypot(x-rightX,y-rightY)>3*SmoothOverlay.pixel()) {
            rightPending=false;looking=worldMode;dragging=!worldMode;
            if(worldMode)captureCameraMouse();
        }
        if(looking){camera.look(dx,dy);return true;}
        if(dragging){yaw+=dx*.015;pitch=Math.max(-1.4,Math.min(1.4,pitch+dy*.015));return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    private void syncFields() {
        updatingFields=true;
        try{for(int line=0;line<fields.size();line++)for(int axis=0;axis<3;axis++)
            fields.get(line)[axis].setValue(rows.get(fieldIndices.get(line))[axis]);}
        finally{updatingFields=false;}
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        boolean consumed=gizmo.dragging() || dragging || looking || panning || panelDragging || marquee.pending() || rightPending || linkSource>=0;
        if(button==1 && rightPending) {
            Polyhedron.Point point=contextPoint;stopMouseAction();
            if(point!=null && !conflict)openPopup(x,y,point);
            return true;
        }
        if(button==0 && marquee.pending()) {
            marquee.drag(x,y,3*SmoothOverlay.pixel());updateMarquee();
            if(marquee.active()){marquee.cancel();stopMouseAction();axesVisible=true;rebuild();return true;}
            marquee.cancel();
        }
        if(button==0 && linkSource>=0) {
            int source=linkSource;boolean control=linkControl;
            boolean dragged=linkDragging || Math.hypot(x-linkX,y-linkY)>3*SmoothOverlay.pixel();
            int target=dragged?PolyGizmo.pickVertex(x,y,draftPoints(),this::editProject,source):-1;
            stopMouseAction();
            if(dragged){axesVisible=false;if(target>=0)connectVertices(source,target);}
            else{if(!selectedFaces.isEmpty() || selectedConnection!=null){selectedFaces.clear();selectedConnection=null;gizmo.clearSelection();}gizmo.select(source,control);axesVisible=true;page=0;rebuild();}
            return true;
        }
        if(button==0){gizmo.release();if(dirty)validate();}
        stopMouseAction();
        return consumed || super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(popupOpen)return true;
        if(gizmo.dragging())return true;
        if(inCanvas(x,y)) {
            if(worldMode)camera.dolly(vertical);else zoom=Math.max(.5,Math.min(1.5,zoom+vertical*.08));
            return true;
        }
        page=Math.max(0,Math.min(Math.max(0,(gizmo.selectedCount()-1)/visibleRows),page-(int)Math.signum(vertical)));rebuild();return true;
    }
    @Override public void removed() {
        stopMouseAction();marquee.cancel();camera.stop();gizmo.release();if(previewTarget!=null)previewTarget.setClientPreview(null);
        if(minecraft.player!=null) {
            menu.removed(minecraft.player);
            if(minecraft.player.containerMenu==menu) {
                minecraft.player.connection.send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(menu.containerId));
                minecraft.player.containerMenu=minecraft.player.inventoryMenu;
            }
        }
        super.removed();
    }
    @Override public void onClose() {
        if(minecraft.player!=null && minecraft.player.containerMenu==menu)minecraft.player.closeContainer();
        else super.onClose();
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==GLFW.GLFW_KEY_ESCAPE){if(popupOpen)closePopup();else onClose();return true;}
        if(waiting)return true;
        if(((mods&GLFW.GLFW_MOD_CONTROL)!=0 || hasControlDown()) && key==GLFW.GLFW_KEY_Z){undo();return true;}
        if(getFocused() instanceof EditBox box && box.keyPressed(key,scan,mods))return true;
        // Inventory hotkey must not close an editor while a text field is focused.
        if(getFocused() instanceof EditBox)return false;
        if(key==GLFW.GLFW_KEY_1){selectionMode(false);return true;}
        if(key==GLFW.GLFW_KEY_2){selectionMode(true);return true;}
        if(key==GLFW.GLFW_KEY_F){createFace();return true;}
        if(key==GLFW.GLFW_KEY_DELETE){if(selectedConnection!=null)deleteConnection();else if(facePicking || !selectedFaces.isEmpty() || (mods&GLFW.GLFW_MOD_SHIFT)!=0 || hasShiftDown())deleteFaces();else deleteVertices(selectedIds());return true;}
        if((mods&GLFW.GLFW_MOD_CONTROL)!=0 || hasControlDown()) {
            if(key==GLFW.GLFW_KEY_A){
                selectedConnection=null;
                if(facePicking || !selectedFaces.isEmpty()){selectedFaces.clear();for(int i=0;i<document.surfaceTriangles().size();i++)selectedFaces.add(i);}
                gizmo.replaceSelection(java.util.stream.IntStream.range(0,rows.size()).boxed().toList());rebuild();return true;
            }
            if(key==GLFW.GLFW_KEY_C){copySelectedVertices();return true;}
            if(key==GLFW.GLFW_KEY_V){pasteVertices();return true;}
        }
        if(worldMode && camera.key(key,scan,true))return true;
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean keyReleased(int key,int scan,int mods) {
        boolean handled=camera.key(key,scan,false);
        return handled || super.keyReleased(key,scan,mods);
    }
    @Override public boolean charTyped(char character,int modifiers) {
        return waiting || super.charTyped(character,modifiers);
    }
}
